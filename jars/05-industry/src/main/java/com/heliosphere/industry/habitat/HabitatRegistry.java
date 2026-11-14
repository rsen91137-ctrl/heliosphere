package com.heliosphere.industry.habitat;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The sealed spaces of the habitat field emitters that are running, per level (server and client each keep their
 * own): inside them the air is breathable, the temperature 20 °C and the gravity that of Earth.
 */
public final class HabitatRegistry {
    private HabitatRegistry() {}

    /** The columns inside a ring (packed as x,0,z), from minY to maxY. */
    public record Region(LongOpenHashSet cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(BlockPos p) {
            return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ
                    && cells.contains(BlockPos.asLong(p.getX(), 0, p.getZ()));
        }
    }

    private static final Map<Level, Map<BlockPos, Region>> REGIONS = new WeakHashMap<>();

    public static synchronized void put(Level level, BlockPos emitter, Region r) {
        REGIONS.computeIfAbsent(level, l -> new HashMap<>()).put(emitter.immutable(), r);
    }

    public static synchronized void remove(Level level, BlockPos emitter) {
        Map<BlockPos, Region> m = REGIONS.get(level);
        if (m != null) m.remove(emitter);
    }

    public static synchronized boolean contains(Level level, BlockPos pos) {
        if (level == null) return false;
        Map<BlockPos, Region> m = REGIONS.get(level);
        if (m == null || m.isEmpty()) return false;
        for (Region r : m.values()) if (r.contains(pos)) return true;
        return false;
    }

    /** Whether any part of the entity's body is in a habitat (its feet or its eyes). */
    public static boolean contains(Entity e) {
        return contains(e.level(), e.blockPosition()) || contains(e.level(), BlockPos.containing(e.getEyePosition()));
    }

    // ---- the entity whose fall is being worked out (Environment.gravity has only the level) ----

    private record Asking(Entity entity, long tick) {}
    /** On the client, the player looking at the HUD (set by HabitatClient). */
    public static java.util.function.Supplier<Entity> viewer = () -> null;
    private static final ThreadLocal<Asking> GRAVITY_OF = new ThreadLocal<>();

    public static void gravityOf(Entity e) { GRAVITY_OF.set(new Asking(e, e.level().getGameTime())); }

    /** Whether the gravity just asked for is that of an entity standing in a habitat; good for one question in this tick. */
    public static boolean earthGravity(Level level) {
        Asking a = GRAVITY_OF.get();
        if (a == null) return level.isClientSide() && viewer.get() != null && viewer.get().level() == level && contains(viewer.get());   // the HUD: as where the player stands
        GRAVITY_OF.remove();
        Entity e = a.entity();
        return !e.isRemoved() && e.level() == level && level.getGameTime() == a.tick() && contains(e);
    }
}
