package com.heliosphere.sphereworld.gen;

import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.world.PlanetGeometry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * Marks the overworld as the finite spherical Earth (1/100 scale) and wraps the vanilla biome source.
 * Along the antimeridian it forces a deep ocean strait and near the poles an ice sheet, so the places
 * where the map folds back on itself look continuous.
 */
public class EarthBiomeSource extends BiomeSource {
    public static final MapCodec<EarthBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("base").forGetter(s -> s.base)
    ).apply(i, i.stable(EarthBiomeSource::new)));

    public static final PlanetGeometry GEO = PlanetGeometry.of(SolarSystem.EARTH);
    /** Half-width (blocks) of the ocean strait centred on the antimeridian. */
    public static final int SEAM_BAND = 640;
    /** Latitude above which the polar ice sheet begins. */
    public static final double POLAR_LAT = 78.0;

    private final BiomeSource base;
    private Holder<Biome> ocean, ice;

    public EarthBiomeSource(BiomeSource base) {
        super();
        this.base = base;
    }

    public BiomeSource base() { return base; }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() { return base.possibleBiomes().stream(); }

    private Holder<Biome> find(ResourceKey<Biome> key) {
        for (Holder<Biome> h : base.possibleBiomes()) if (h.is(key)) return h;
        return null;
    }

    /** 0 inland, 1 fully inside the antimeridian strait. */
    public static double seamFactor(double x) {
        double edge = GEO.halfWidth() - Math.abs(wrapX(x));
        return 1 - Math.max(0, Math.min(1, (edge - SEAM_BAND * 0.5) / (SEAM_BAND * 0.5)));
    }

    public static double polarFactor(double z) {
        double[] ll = GEO.latLon(0, z);
        return Math.max(0, Math.min(1, (Math.abs(ll[0]) - POLAR_LAT) / 3.0));
    }

    private static double wrapX(double x) {
        double c = GEO.circumference, h = c / 2;
        return ((x + h) % c + c) % c - h;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        double bx = qx << 2, bz = qz << 2;
        if (ocean == null) { ocean = find(Biomes.DEEP_OCEAN); ice = find(Biomes.SNOWY_PLAINS); }
        if (polarFactor(bz) > 0.5 && ice != null) return ice;
        if (seamFactor(bx) > 0.5 && ocean != null) return ocean;
        return base.getNoiseBiome(qx, qy, qz, sampler);
    }
}
