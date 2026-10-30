package com.necromancermod.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Nahrazuje AvoidEntityGoal + KeepRangeGoal.
 *
 * Necromancer si drzi odstup od cile, ale NEUTIKA daleko:
 *  - blizko (< TOO_CLOSE)      -> kratky ustupovy krok (cca RETREAT_STEP bloku), pak zase stoji/strili
 *  - v pasmu (TOO_CLOSE..IDEAL_MAX) -> stoji, obcas se kratce posune do strany (strafe)
 *  - daleko / bez videni       -> priblizi se k cily
 * Goal ma jen flag MOVE, takze OrbShootGoal (LOOK) bezi soucasne a strili i za pohybu.
 */
public class KeepDistanceGoal extends Goal {

    // ---- ladici konstanty (v blocich) ----
    private static final double TOO_CLOSE    = 6.0D;  // bliz nez tohle -> ustup
    private static final double IDEAL_MAX    = 11.0D; // dal nez tohle -> priblizit
    private static final double RETREAT_STEP = 5.0D;  // delka jednoho ustupu
    private static final double STRAFE_STEP  = 3.0D;  // delka jednoho kroku do strany

    private final PathfinderMob mob;
    private int repathTicks;
    private int strafeTicks;
    private int strafeDir = 1;

    public KeepDistanceGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.repathTicks = 0;
        this.strafeTicks = 20 + this.mob.getRandom().nextInt(20);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        if (--this.repathTicks > 0) {
            return;
        }
        this.repathTicks = 5 + this.mob.getRandom().nextInt(4);

        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001D) {
            dist = 0.001D;
        }
        double nx = dx / dist; // jednotkovy vektor k cili
        double nz = dz / dist;

        boolean sees = this.mob.hasLineOfSight(target);

        if (!sees || dist > IDEAL_MAX) {
            // priblizit
            this.mob.getNavigation().moveTo(target, dist > 16.0D ? 1.1D : 1.0D);
        } else if (dist < TOO_CLOSE) {
            // kratky ustup pryc od cile, mirne bokem aby nenarazil rovnou do zdi
            double ax = -nx + (-nz * this.strafeDir) * 0.5D;
            double az = -nz + (nx * this.strafeDir) * 0.5D;
            double len = Math.sqrt(ax * ax + az * az);
            ax /= len;
            az /= len;
            boolean ok = this.mob.getNavigation().moveTo(
                    this.mob.getX() + ax * RETREAT_STEP,
                    this.mob.getY(),
                    this.mob.getZ() + az * RETREAT_STEP,
                    1.25D);
            if (!ok) {
                // zablokovano -> zkus druhou stranu
                this.strafeDir = -this.strafeDir;
                this.mob.getNavigation().moveTo(
                        this.mob.getX() + (-nz * this.strafeDir) * STRAFE_STEP,
                        this.mob.getY(),
                        this.mob.getZ() + (nx * this.strafeDir) * STRAFE_STEP,
                        1.25D);
            }
        } else {
            // idealni pasmo: stoji a strili, obcas kratky strafe
            this.strafeTicks -= 6;
            if (this.strafeTicks <= 0) {
                this.strafeTicks = 30 + this.mob.getRandom().nextInt(40);
                this.strafeDir = this.mob.getRandom().nextBoolean() ? 1 : -1;
                if (this.mob.getRandom().nextInt(3) != 0) {
                    this.mob.getNavigation().moveTo(
                            this.mob.getX() + (-nz * this.strafeDir) * STRAFE_STEP,
                            this.mob.getY(),
                            this.mob.getZ() + (nx * this.strafeDir) * STRAFE_STEP,
                            0.9D);
                }
            }
        }
    }
}
