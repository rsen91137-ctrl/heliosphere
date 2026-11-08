package com.heliosphere.core.env;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.world.PlanetGeometry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Which celestial body each dimension represents. */
public final class Planets {
    public static final ResourceKey<Level> MOON = dim("moon");
    public static final ResourceKey<Level> MARS = dim("mars");
    public static final ResourceKey<Level> SPACE = dim("space");

    private static final Map<ResourceKey<Level>, CelestialBody> BY_DIM = new LinkedHashMap<>();
    private static final Map<String, ResourceKey<Level>> BY_BODY = new LinkedHashMap<>();
    /** Set by the sphere-world module: true when the overworld is the finite, spherical Earth. */
    private static Predicate<Level> earthIsSphere = l -> false;

    static {
        link(Level.OVERWORLD, SolarSystem.EARTH);
        link(MOON, SolarSystem.MOON);
        link(MARS, SolarSystem.MARS);
        for (CelestialBody b : SolarSystem.all()) {
            if (b.kind == CelestialBody.Kind.STAR || BY_BODY.containsKey(b.id)) continue;
            link(dim(b.id), b);
        }
    }

    private Planets() {}

    public static ResourceKey<Level> dim(String path) { return ResourceKey.create(Registries.DIMENSION, Heliosphere.id(path)); }

    public static void link(ResourceKey<Level> dim, CelestialBody body) { BY_DIM.put(dim, body); BY_BODY.put(body.id, dim); }

    public static void setEarthSphereCheck(Predicate<Level> p) { earthIsSphere = p; }

    /** The body a level stands on, or null for nether/end/space. */
    public static CelestialBody bodyOf(Level level) { return BY_DIM.get(level.dimension()); }

    public static ResourceKey<Level> dimensionOf(CelestialBody body) { return BY_BODY.get(body.id); }

    public static boolean isSpace(Level level) { return level.dimension() == SPACE; }

    public static boolean isHeliosphereDimension(Level level) {
        return level.dimension() == SPACE || (level.dimension() != Level.OVERWORLD && BY_DIM.containsKey(level.dimension()));
    }

    /** Surface geometry if this level wraps around a sphere, else null. */
    public static PlanetGeometry geometry(Level level) {
        CelestialBody b = bodyOf(level);
        if (b == null) return null;
        if (b == SolarSystem.EARTH && !earthIsSphere.test(level)) return null;
        return PlanetGeometry.of(b);
    }

    public static boolean isGasGiant(Level level) { CelestialBody b = bodyOf(level); return b != null && b.gasGiant; }

    public static Iterable<CelestialBody> landable() { return () -> BY_DIM.values().iterator(); }
}
