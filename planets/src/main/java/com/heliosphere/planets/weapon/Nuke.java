package com.heliosphere.planets.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tactical nuclear detonation: a crater of overlapping blasts, a flash, a mushroom cloud and fallout. */
public final class Nuke {
    private Nuke() {}

    public static void detonate(ServerLevel l, Entity src, Vec3 c) {
        l.explode(src, c.x, c.y, c.z, 16f, Level.ExplosionInteraction.TNT);
        for (int k = 0; k < 10; k++) {
            double a = k * Math.PI * 2 / 10;
            l.explode(src, c.x + Math.cos(a) * 11, c.y - 2, c.z + Math.sin(a) * 11, 10f, Level.ExplosionInteraction.TNT);
        }
        for (Entity e : l.getEntities(src, new AABB(c, c).inflate(64), en -> en instanceof LivingEntity)) {
            double d = e.position().distanceTo(c);
            if (d < 64) e.hurtServer(l, l.damageSources().explosion(src, src), (float) (80 * (1 - d / 64)));
            if (e instanceof LivingEntity le && d < 48) le.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 1));
        }
        for (ServerPlayer p : l.players()) {
            double d = p.position().distanceTo(c);
            if (d < 200) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, (int) (60 * (1 - d / 200)) + 20, 0));
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
            }
        }
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 2, c.z, 12, 6, 3, 6, 0);
        for (int h = 0; h < 40; h += 2) {
            double r = h < 28 ? 2 + h * 0.08 : 6 + (h - 28) * 0.9;
            l.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + h, c.z, 30, r, 0.8, r, 0.02);
            l.sendParticles(ParticleTypes.FLAME, c.x, c.y + h * 0.5, c.z, 12, r * 0.6, 0.5, r * 0.6, 0.02);
        }
        l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 30f, 0.35f);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 30f, 0.4f);
    }
}
