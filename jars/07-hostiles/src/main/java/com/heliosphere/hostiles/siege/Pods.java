package com.heliosphere.hostiles.siege;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Drop pods: machines dropped from the sky leave a burning trail and land with a thump. */
final class Pods {
    private Pods() {}

    private static final List<Entity> FALLING = new ArrayList<>();
    private static boolean hooked;

    static void add(Entity e) {
        if (!hooked) { hooked = true; ServerTickEvents.END_SERVER_TICK.register(s -> tick()); }
        FALLING.add(e);
    }

    private static void tick() {
        for (Iterator<Entity> it = FALLING.iterator(); it.hasNext(); ) {
            Entity e = it.next();
            if (!e.isAlive() || !(e.level() instanceof ServerLevel l)) { it.remove(); continue; }
            if (e.onGround() || e.isInWater()) {
                l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.3, e.getZ(), 2, 0.6, 0.1, 0.6, 0);
                l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.3, e.getZ(), 14, 1.2, 0.2, 1.2, 0.02);
                l.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 1.3f);
                it.remove();
                continue;
            }
            l.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 0.2, e.getZ(), 4, 0.25, 0.1, 0.25, 0.01);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.6, e.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
        }
    }
}
