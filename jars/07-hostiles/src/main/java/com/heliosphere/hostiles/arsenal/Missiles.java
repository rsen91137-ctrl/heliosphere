package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.tech.TechMob;
import com.heliosphere.planets.tech.TechProjectile;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Guided missiles. A machine's missile blasts a hole where it lands, deeper for better machines (level 5 and up
 * get through obsidian and ancient debris). A missile with no target (a player's fire-and-forget shot) looks
 * ahead with its seeker each tick and turns onto the first enemy it sees.
 */
public final class Missiles {
    private Missiles() {}

    /** Fields of a round, exposed by TechProjectileMixin. */
    public interface Round {
        UUID heliosphere$owner();
        UUID heliosphere$target();
        void heliosphere$target(UUID id);
    }

    private static final ThreadLocal<TechProjectile> CURRENT = new ThreadLocal<>();
    public static final double SEEKER_RANGE = 48, SEEKER_COS = Math.cos(Math.toRadians(30));

    public static void begin(TechProjectile p) {
        CURRENT.set(p);
        if (p.mode() == 3 && p.level() instanceof ServerLevel level && ((Round) (Object) p).heliosphere$target() == null) seek(level, p);
    }

    public static void end() { CURRENT.remove(); }

    /** A machine fires a guided missile of its technology level (see MissileSpec.machine and MachineWarheads). */
    public static void fire(ServerLevel level, TechMob mob, LivingEntity target, Vec3 from, Vec3 dir, float damage) {
        int tier = mob.role() != null ? EnemyRoster.tier(mob.role().id()) : 3;
        com.heliosphere.planets.defense.sam.GuidedMissile.launch(level, com.heliosphere.planets.defense.sam.MissileSpec.machine(tier), mob,
                com.heliosphere.planets.defense.sam.GuidedMissile.Side.MACHINE, target, from, dir, damage, tier);
        level.playSound(null, from.x, from.y, from.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, net.minecraft.sounds.SoundSource.HOSTILE, 1.6f, 0.6f);
    }

    static void init() {
        com.heliosphere.planets.defense.sam.GuidedMissile.BLAST_HOOKS.add((level, missile, at) -> {
            if (missile.side() != com.heliosphere.planets.defense.sam.GuidedMissile.Side.MACHINE) return;
            int tier = com.heliosphere.planets.defense.sam.MachineWarheads.clamp(missile.tier());
            MachineBlast.crater(level, at, com.heliosphere.planets.defense.sam.MachineWarheads.crater(tier),
                    com.heliosphere.planets.defense.sam.MachineWarheads.resistanceLimit(tier));
        });
        CombatVfx.LISTENERS.add((level, kind, color, start, end, strength) -> {
            TechProjectile p = CURRENT.get();
            if (p == null || kind != CombatVfx.RING || p.mode() != 3) return;
            UUID owner = ((Round) (Object) p).heliosphere$owner();
            Entity shooter = owner == null ? null : level.getEntity(owner);
            if (shooter instanceof Player) return;                        // the player's launcher does not dig
            int tier = shooter instanceof TechMob m && m.role() != null ? EnemyRoster.tier(m.role().id()) : 3;
            MachineBlast.breakBlocks(level, start, 1.8f + tier * 0.25f, tier);
        });
    }

    /** The seeker head: the nearest enemy within 30 degrees of the nose and 48 blocks. */
    private static void seek(ServerLevel level, TechProjectile p) {
        Vec3 v = p.getDeltaMovement();
        if (v.lengthSqr() < 1e-4) return;
        Vec3 nose = v.normalize(), at = p.position();
        UUID owner = ((Round) (Object) p).heliosphere$owner();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SEEKER_RANGE))) {
            if (!(m instanceof Enemy) || !m.isAlive() || m.getUUID().equals(owner)) continue;
            Vec3 to = m.getBoundingBox().getCenter().subtract(at);
            double d = to.length();
            if (d > SEEKER_RANGE || to.normalize().dot(nose) < SEEKER_COS) continue;
            if (d < bestD) { bestD = d; best = m; }
        }
        if (best != null) ((Round) (Object) p).heliosphere$target(best.getUUID());
    }
}
