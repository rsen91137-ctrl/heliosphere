package com.heliosphere.core.env;

import com.heliosphere.core.celestial.CelestialBody;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Physical environment at a position: gravity, air, pressure and temperature. */
public final class Environment {
    /** Oxygen bubbles from distributors, per dimension: position -> radius. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> BUBBLES = new ConcurrentHashMap<>();

    /** Set by the rocketry module: true while an entity sits in a pressurised cabin. */
    public static java.util.function.Predicate<net.minecraft.world.entity.Entity> sealedCabin = e -> false;

    private Environment() {}

    /** Gravity multiplier relative to Earth for entities in this level. */
    public static double gravity(Level level) {
        if (Planets.isSpace(level)) return 0.04;
        CelestialBody b = Planets.bodyOf(level);
        // tiny moons keep a playable minimum so a jump does not throw you into orbit
        return b == null ? 1.0 : Math.max(0.03, b.surfaceGravity);
    }

    public static boolean hasAtmosphere(Level level) {
        CelestialBody b = Planets.bodyOf(level);
        return b == null || b.pressureBar > 0.001;
    }

    public static double pressureBar(Level level) {
        if (Planets.isSpace(level)) return 0;
        CelestialBody b = Planets.bodyOf(level);
        return b == null ? 1.0 : b.pressureBar;
    }

    public static boolean breathable(Level level, BlockPos pos) {
        if (Planets.isSpace(level)) return insideBubble(level, pos);
        CelestialBody b = Planets.bodyOf(level);
        if (b == null || b.breathable) return true;
        if (b.gasGiant) return insideBubble(level, pos);
        return insideBubble(level, pos);
    }

    /** 0 at night, 1 at noon, from the dimension's sun angle. */
    public static double daylight(Level level, BlockPos pos) {
        if (Planets.isSpace(level)) return 1;
        float angle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos);
        double elev = Math.cos(Math.toRadians(angle));
        return Math.max(0, Math.min(1, elev * 1.6 + 0.35));
    }

    public static double temperatureC(Level level, BlockPos pos) {
        if (Planets.isSpace(level)) return -100;
        CelestialBody b = Planets.bodyOf(level);
        if (b == null) return 20;
        double d = daylight(level, pos);
        double t = b.tempNightC + (b.tempDayC - b.tempNightC) * d;
        // Underground rock moderates the swing toward the mean
        if (!level.canSeeSky(pos.above()) && b.pressureBar < 0.1) t = t * 0.35 + (b.tempDayC + b.tempNightC) * 0.5 * 0.65;
        if (insideBubble(level, pos)) t = 20;
        return t;
    }

    public static void addBubble(Level level, BlockPos pos, int radius) {
        BUBBLES.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.immutable(), radius);
    }

    public static void removeBubble(Level level, BlockPos pos) {
        Map<BlockPos, Integer> m = BUBBLES.get(level.dimension());
        if (m != null) m.remove(pos);
    }

    public static boolean insideBubble(Level level, BlockPos pos) {
        Map<BlockPos, Integer> m = BUBBLES.get(level.dimension());
        if (m == null || m.isEmpty()) return false;
        for (Map.Entry<BlockPos, Integer> e : m.entrySet()) {
            int r = e.getValue();
            if (e.getKey().distSqr(pos) <= (double) r * r) return true;
        }
        return false;
    }

    public static Set<BlockPos> bubbles(Level level) {
        Map<BlockPos, Integer> m = BUBBLES.get(level.dimension());
        return m == null ? Set.of() : m.keySet();
    }
}
