package com.necromancermod;

import com.necromancermod.entity.NecromancerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NecromancerMod.MODID)
public class ForgeEvents {

    private static boolean isOtherUndead(Entity e) {
        return e instanceof Mob mob && !(e instanceof Player) && !(e instanceof NecromancerEntity)
                && mob.getMobType() == MobType.UNDEAD;
    }

    /** Sipy minionu i ostatnich undead mobu letí skrz Necromancera (neublizi mu a neodrazi se). */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof NecromancerEntity) {
            Entity owner = event.getProjectile().getOwner();
            if (owner != null && (owner.getTags().contains(NecromancerEntity.MINION_TAG) || isOtherUndead(owner))) {
                event.setCanceled(true);
            }
        }
    }

    /** Undead moby si Necromancera nikdy nezvoli jako cil a on nezvoli je. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        Entity attacker = event.getEntity();
        Entity newTarget = event.getNewTarget();
        if (newTarget == null) return;
        if (newTarget instanceof NecromancerEntity && isOtherUndead(attacker)) {
            event.setCanceled(true);
        } else if (attacker instanceof NecromancerEntity && isOtherUndead(newTarget)) {
            event.setCanceled(true);
        }
    }
}
