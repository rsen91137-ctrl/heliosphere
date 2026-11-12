package com.heliosphere.planets.weapon.guided;

import com.heliosphere.planets.tech.TechProjectile;
import com.heliosphere.planets.weapon.GunItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The guided missile launcher. Aim down the sights at an enemy for a second and it locks (a tone and a message);
 * the missile then hunts that target. Fired without a lock, the missile flies on and its seeker turns onto the
 * first enemy that comes into its view (see Missiles in the hostiles module).
 */
public final class SmartLauncher {
    private SmartLauncher() {}

    private static final double RANGE = 160, CONE = Math.cos(Math.toRadians(5)), KEEP = Math.cos(Math.toRadians(30));
    private static final int LOCK_TICKS = 20, HOLD_TICKS = 100;

    private static final class Lock { UUID candidate; int ticks; UUID locked; long lostAt; }
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    public static void init() { ServerTickEvents.END_WORLD_TICK.register(SmartLauncher::tick); }

    private static boolean holding(ServerPlayer p) {
        return p.getMainHandItem().getItem() instanceof GunItem g && "smart_launcher".equals(BuiltInRegistries.ITEM.getKey(g).getPath());
    }

    private static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (!holding(p)) { LOCKS.remove(p.getUUID()); continue; }
            Lock lock = LOCKS.computeIfAbsent(p.getUUID(), k -> new Lock());
            LivingEntity locked = lock.locked == null ? null : level.getEntity(lock.locked) instanceof LivingEntity le ? le : null;
            if (locked != null) {
                Vec3 to = locked.getBoundingBox().getCenter().subtract(p.getEyePosition());
                boolean inView = locked.isAlive() && to.length() < RANGE * 1.25 && to.normalize().dot(p.getLookAngle()) > KEEP;
                if (!inView && now - lock.lostAt > HOLD_TICKS) { lock.locked = null; p.displayClientMessage(Component.translatable("message.heliosphere.lock_lost"), true); }
                else if (inView) lock.lostAt = now;
            }
            if (!GunItem.isAiming(p)) { lock.candidate = null; lock.ticks = 0; continue; }
            Mob aim = underSight(level, p);
            if (aim == null) { lock.candidate = null; lock.ticks = 0; continue; }
            if (aim.getUUID().equals(lock.candidate)) lock.ticks++;
            else { lock.candidate = aim.getUUID(); lock.ticks = 0; }
            if (lock.ticks == LOCK_TICKS && !aim.getUUID().equals(lock.locked)) {
                lock.locked = aim.getUUID();
                lock.lostAt = now;
                p.displayClientMessage(Component.translatable("message.heliosphere.lock_on", aim.getDisplayName()), true);
                level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 2.0f);
            } else if (lock.ticks > 0 && lock.ticks < LOCK_TICKS && lock.ticks % 5 == 0) {
                level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
            }
        }
    }

    /** The enemy nearest the line of sight within 5 degrees and 160 blocks, with nothing in the way. */
    private static Mob underSight(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Mob best = null;
        double bestDot = CONE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RANGE))) {
            if (!(m instanceof Enemy) || !m.isAlive()) continue;
            Vec3 to = m.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > RANGE) continue;
            double dot = to.normalize().dot(look);
            if (dot <= bestDot) continue;
            if (level.clip(new ClipContext(eye, m.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            bestDot = dot;
            best = m;
        }
        return best;
    }

    /** Fires one missile, at the locked target if there is one. */
    public static void launch(ServerLevel level, ServerPlayer p, float damage) {
        Lock lock = LOCKS.get(p.getUUID());
        LivingEntity target = lock == null || lock.locked == null ? null : level.getEntity(lock.locked) instanceof LivingEntity le && le.isAlive() ? le : null;
        Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(1.2)).add(0, -0.2, 0);
        TechProjectile.launch(level, p, target, from, from.add(p.getLookAngle().scale(10)).add(0, 1.2, 0), 3, damage);
        level.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.4f, 0.7f);
        if (target == null) p.displayClientMessage(Component.translatable("message.heliosphere.seeker_search"), true);
    }
}
