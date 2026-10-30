package com.necromancermod.entity;

import java.util.EnumSet;

import com.necromancermod.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

/**
 * Necromancer z Minecraft Dungeons.
 *
 * Chovani:
 *  - kdyz se hrac priblizi na dosah blizkeho boje, drzi odstup (KeepDistanceGoal),
 *  - v bezpecne vzdalenosti vyvolava zombie / skeletony (vanilla moby, s brnenim podle obtiznosti),
 *    ale jen kdyz ma kolem sebe malo minionu,
 *  - jinak strili modre energeticke koule; kdyz ho hrac zahnal do kouta, strili skoro porad.
 */
public class NecromancerEntity extends Monster {

    /** Velikost moba oproti puvodnimu modelu (model, hitbox i misto vystrelu). */
    public static final float SCALE = 1.18F;

    public static final String MINION_TAG = "necromancer_minion";

    public static final int STATE_NONE = 0;
    public static final int STATE_SHOOT = 1;
    public static final int STATE_SUMMON = 2;

    /** Delky animaci v tickach (projectile 1.54 s, summon 1.83 s). */
    private static final int SHOOT_TOTAL_TICKS = 31;
    /** Tick animace, ve kterem koule vyleti (hul se v tu chvili mrsti dopredu; mensi cislo = drive). */
    private static final int SHOOT_FIRE_TICK = 10;
    /** Pozice krystalu na holi pri vystrelu (souradnice modelu v blocich pred skalovanim). */
    private static final double ORB_RIGHT = 0.71D;
    private static final double ORB_UP = 2.0D;
    private static final double ORB_FORWARD = 1.2D;
    private static final int SUMMON_TOTAL_TICKS = 38;
    private static final int SUMMON_SPAWN_TICK = 20;

    private static final int MAX_MINIONS = 6;
    private static final int RESUMMON_BELOW = 3;
    private static final double FLEE_DISTANCE = 4.5D;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(NecromancerEntity.class, EntityDataSerializers.INT);

    /** Pouze klient: tickCount v okamziku, kdy se zmenil stav animace. */
    public int animStartTick;

    private int shootCooldown = 30;
    private int summonCooldown = 5;
    /** Smich se ozve jen obcas: po vystrelu/vyvolani je pauza minimalne ~25 s. */
    private int laughCooldown = 300;

    public NecromancerEntity(EntityType<? extends NecromancerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 40;
        // vode se vyhyba (nechodi do ni ani za cilem)
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 8.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 125.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    // ------------------------------------------------------------------ data / stav animace

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, STATE_NONE);
    }

    public int getAnimState() {
        return this.entityData.get(DATA_STATE);
    }

    public void setAnimState(int state) {
        this.entityData.set(DATA_STATE, state);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key)) {
            this.animStartTick = this.tickCount;
        }
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // utek pred hracem v dosahu blizkeho boje
        // vyvolavani ma prednost pred utekem: kdyz nema zadne miniony, vyvola je i kdyz je hrac blizko
        this.goalSelector.addGoal(1, new SummonGoal(this));
        this.goalSelector.addGoal(2, new KeepDistanceGoal(this));
        this.goalSelector.addGoal(2, new OrbShootGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // stejne jako vanilla zombie / skeleton utoci i na zelezneho golema
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.shootCooldown > 0) this.shootCooldown--;
        if (this.summonCooldown > 0) this.summonCooldown--;
        if (this.laughCooldown > 0) {
            this.laughCooldown--;
        } else if (this.getTarget() != null && this.random.nextInt(250) == 0) {
            this.playSound(ModSounds.LAUGH.get(), 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
            this.laughCooldown = 500 + this.random.nextInt(500);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData spawnData, CompoundTag tag) {
        // prirozene spawnuty mob se ma normalne despawnovat, ostatni (vejce, prikaz...) zustavaji
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            this.setPersistenceRequired();
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // vlastni miniony a vlastni koule mu neublizuji
        Entity attacker = source.getEntity();
        if (attacker != null && attacker.getTags().contains(MINION_TAG)) return false;
        if (source.getDirectEntity() instanceof NecromancerOrb) return false;
        // ostatni undead moby mu neublizi (a tim se na nej ani nenastvou)
        if (attacker instanceof Mob && !(attacker instanceof Player) && ((Mob) attacker).getMobType() == MobType.UNDEAD) return false;
        return super.hurt(source, amount);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    // ------------------------------------------------------------------ zvuky (vanilla skeleton)

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SKELETON_STEP, 0.15F, 1.0F);
    }

    // ------------------------------------------------------------------ utoky

    /** Hrac je tak blizko, ze se Necromancer povazuje za zahnaneho do kouta. */
    private boolean isCornered() {
        LivingEntity target = this.getTarget();
        return target != null && this.distanceToSqr(target) < 5.0D * 5.0D;
    }

    private void shootOrb(LivingEntity target) {
        if (this.level().isClientSide) return;

        // orb launches from the staff crystal (right hand) and turns with the body
        double yaw = Math.toRadians(this.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // dopredu
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // doprava
        double s = SCALE;
        double sx = this.getX() + (fx * ORB_FORWARD + rx * ORB_RIGHT) * s;
        double sy = this.getY() + ORB_UP * s;
        double sz = this.getZ() + (fz * ORB_FORWARD + rz * ORB_RIGHT) * s;
        double dx = target.getX() - sx;
        double dy = target.getY(0.6D) - sy;
        double dz = target.getZ() - sz;

        NecromancerOrb orb = new NecromancerOrb(this.level(), this);
        orb.setPos(sx, sy, sz);
        float inaccuracy = 14.0F - (float) this.level().getDifficulty().getId() * 4.0F;
        orb.shoot(dx, dy, dz, 1.2F, inaccuracy);
        this.level().addFreshEntity(orb);
        this.playSound(ModSounds.ORB_CAST.get(), 1.0F, 1.0F);
    }

    private int countMinions() {
        return this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(28.0D),
                m -> m.isAlive() && m.getTags().contains(MINION_TAG)).size();
    }

    private void summonMinions() {
        if (!(this.level() instanceof ServerLevel server)) return;

        int toSpawn = Math.min(2 + this.random.nextInt(3), MAX_MINIONS - countMinions());
        if (toSpawn <= 0) return;

        this.playSound(ModSounds.MINION_SUMMONED.get(), 1.2F, 1.0F);

        LivingEntity target = this.getTarget();
        for (int i = 0; i < toSpawn; i++) {
            EntityType<? extends Mob> type = this.random.nextFloat() < 0.7F ? EntityType.ZOMBIE : EntityType.SKELETON;
            Mob minion = type.create(server);
            if (minion == null) continue;

            BlockPos pos = findSpawnPos();
            minion.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.random.nextFloat() * 360.0F, 0.0F);
            minion.addTag(MINION_TAG);
            minion.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
            // zadni baby zombie (a ani kuraci jezdci)
            if (minion instanceof Zombie zombie && zombie.isBaby()) {
                Entity vehicle = zombie.getVehicle();
                zombie.stopRiding();
                if (vehicle != null) vehicle.discard();
                zombie.setBaby(false);
            }
            equipArmor(minion);
            if (target != null) minion.setTarget(target);
            server.addFreshEntityWithPassengers(minion);

            server.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                    12, 0.25D, 0.5D, 0.25D, 0.03D);
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D,
                    8, 0.3D, 0.05D, 0.3D, 0.02D);
        }
    }

    /** Najde volne misto 2-4 bloky od Necromancera. */
    private BlockPos findSpawnPos() {
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = this.random.nextInt(9) - 4;
            int dz = this.random.nextInt(9) - 4;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos base = this.blockPosition().offset(dx, 0, dz);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos p = base.offset(0, dy, 0);
                BlockPos below = p.below();
                boolean solidFloor = this.level().getBlockState(below).isFaceSturdy(this.level(), below, Direction.UP);
                boolean freeBody = this.level().getBlockState(p).getCollisionShape(this.level(), p).isEmpty();
                boolean freeHead = this.level().getBlockState(p.above()).getCollisionShape(this.level(), p.above()).isEmpty();
                if (solidFloor && freeBody && freeHead) return p;
            }
        }
        return this.blockPosition();
    }

    /**
     * Miniony maji nahodne brneni: zadne, castecne (1-3 kusy) nebo cele.
     * Typ (leather / chainmail / iron) se vybira podle obtiznosti.
     */
    private void equipArmor(Mob mob) {
        int difficulty = this.level().getDifficulty().getId();

        // kolik kusu: 25 % zadne, 35 % cast, 40 % cele
        float amountRoll = this.random.nextFloat();
        if (amountRoll < 0.25F) return;

        // typ brneni podle obtiznosti
        float roll = this.random.nextFloat();
        Item[] set;
        if (difficulty >= 3) {
            set = roll < 0.5F ? ironArmor() : (roll < 0.8F ? chainArmor() : leatherArmor());
        } else if (difficulty == 2) {
            set = roll < 0.3F ? ironArmor() : (roll < 0.7F ? chainArmor() : leatherArmor());
        } else {
            set = roll < 0.1F ? ironArmor() : (roll < 0.4F ? chainArmor() : leatherArmor());
        }

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        boolean full = amountRoll >= 0.6F;
        for (int i = 0; i < slots.length; i++) {
            if (full || this.random.nextFloat() < 0.5F) {
                mob.setItemSlot(slots[i], new ItemStack(set[i]));
            }
        }
    }

    private static Item[] leatherArmor() {
        return new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
    }

    private static Item[] chainArmor() {
        return new Item[]{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
    }

    private static Item[] ironArmor() {
        return new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    }


    // ------------------------------------------------------------------ goaly

    /** Kouzli energetickou kouli (animace "projectile"). Funguje i za behu (utek + strelba). */
    private static class OrbShootGoal extends Goal {
        private final NecromancerEntity mob;
        private int castTick;

        OrbShootGoal(NecromancerEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null
                    && target.isAlive()
                    && this.mob.shootCooldown <= 0
                    && this.mob.getAnimState() == STATE_NONE
                    && this.mob.distanceToSqr(target) <= 26.0D * 26.0D
                    && this.mob.hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive() && this.castTick < SHOOT_TOTAL_TICKS;
        }

        @Override
        public void start() {
            this.castTick = 0;
            this.mob.setAnimState(STATE_SHOOT);
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);
            }
            this.castTick++;
            if (this.castTick == SHOOT_FIRE_TICK && target != null) {
                this.mob.shootOrb(target);
            }
        }

        @Override
        public void stop() {
            this.mob.setAnimState(STATE_NONE);
            // zahnany do kouta = spamuje koule
            this.mob.shootCooldown = this.mob.isCornered() ? 4 : 8;
            this.castTick = 0;
        }
    }

    /** Vyvola zombie / skeletony (animace "summon"), kdyz je hrac v bezpecne vzdalenosti a miniony chybi. */
    private static class SummonGoal extends Goal {
        private final NecromancerEntity mob;
        private int tick;

        SummonGoal(NecromancerEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null
                    && target.isAlive()
                    && this.mob.getAnimState() == STATE_NONE
                    && this.canSummonNow(target);
        }

        /**
         * Bez minionu vyvolava rychle a klidne i kdyz je hrac blizko;
         * kdyz nejake ma (ale malo), vyvola dalsi jen v bezpecne vzdalenosti a po cooldownu.
         */
        private boolean canSummonNow(LivingEntity target) {
            int minions = this.mob.countMinions();
            if (minions == 0) {
                return this.mob.summonCooldown <= 60;
            }
            return minions < RESUMMON_BELOW
                    && this.mob.summonCooldown <= 0
                    && this.mob.distanceToSqr(target) > 6.0D * 6.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return this.tick < SUMMON_TOTAL_TICKS;
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            this.tick = 0;
            this.mob.setAnimState(STATE_SUMMON);
            this.mob.getNavigation().stop();
            this.mob.playSound(ModSounds.SUMMON_CAST.get(), 1.2F, 1.0F);
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            this.tick++;
            if (this.tick == SUMMON_SPAWN_TICK) {
                this.mob.summonMinions();
            }
        }

        @Override
        public void stop() {
            this.mob.setAnimState(STATE_NONE);
            this.mob.summonCooldown = 140 + this.mob.getRandom().nextInt(60);
            if (this.mob.countMinions() == 0) this.mob.summonCooldown = 100;
            this.tick = 0;
        }
    }

}
