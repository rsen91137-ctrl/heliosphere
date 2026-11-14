package com.heliosphere.industry.habitat;

import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.Planets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Earth's animals (and villagers) need air like the player does: on a world without breathable air they suffocate
 * slowly (2 damage every 2 seconds) unless they are inside a habitat field or an oxygen bubble. The worlds' own
 * creatures and the monsters are not affected.
 */
public final class LifeSupport {
    private LifeSupport() {}

    public static void init() { ServerTickEvents.END_SERVER_TICK.register(LifeSupport::tick); }

    /** A creature from Earth that breathes: vanilla, not a monster. */
    public static boolean needsAir(Entity e) {
        return e instanceof Mob && !(e instanceof Enemy) && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("minecraft");
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 7) return;
        for (ServerLevel level : server.getAllLevels()) {
            if (!Planets.isHeliosphereDimension(level)) continue;
            var body = Planets.bodyOf(level);
            if (body != null && body.breathable && !Planets.isSpace(level)) continue;
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || !needsAir(e)) continue;
                if (Environment.breathable(level, BlockPos.containing(e.getEyePosition()))) continue;
                le.hurtServer(level, level.damageSources().drown(), 2f);
            }
        }
    }
}
