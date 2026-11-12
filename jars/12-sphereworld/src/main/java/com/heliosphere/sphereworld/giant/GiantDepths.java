package com.heliosphere.sphereworld.giant;

import com.heliosphere.core.Reg;
import com.heliosphere.sphereworld.CloudBlock;
import com.heliosphere.sphereworld.TerrainBlocks;
import com.heliosphere.sphereworld.terrain.Mat;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.MapColor;

/**
 * The deep interiors of the gas and ice giants. Their worlds now run from Y -2032 to 2031: a thin haze of the
 * highest clouds near Y 1100, the released cloud deck and aerostat platforms around Y 250-340, then deeper
 * cloud decks of other chemistry, a hot glowing haze, and at the bottom an ocean: liquid metallic hydrogen in
 * Jupiter and Saturn, a hot ionic water-ammonia ocean in Uranus and Neptune. From the top clouds to the ocean
 * is more than 3000 blocks, and it gets hotter all the way down (see GiantClimate).
 */
public final class GiantDepths implements ModInitializer {
    public static final int MIN_Y = -2032, HEIGHT = 4064;
    public static final int OCEAN_TOP = -1990;

    public static Block WATER_CLOUD, DEEP_HAZE, METALLIC_HYDROGEN, IONIC_OCEAN, HYDROGEN_SULFIDE_CLOUD;

    /** One cloud deck: centre height, half thickness, coverage 0..1 and horizontal scale of its cells. */
    record Deck(int y, int half, float cover, int scale, BlockState block) {}

    @Override
    public void onInitialize() {
        Reg.tab("planets");
        WATER_CLOUD = Reg.blockWithItem("water_cloud", CloudBlock::new, Reg.soil(MapColor.SNOW, 0.2f).noCollision().noOcclusion());
        HYDROGEN_SULFIDE_CLOUD = Reg.blockWithItem("hydrogen_sulfide_cloud", CloudBlock::new, Reg.soil(MapColor.COLOR_LIGHT_GREEN, 0.2f).noCollision().noOcclusion());
        DEEP_HAZE = Reg.blockWithItem("deep_haze", CloudBlock::new, Reg.soil(MapColor.COLOR_ORANGE, 0.2f).noCollision().noOcclusion().lightLevel(s -> 7));
        METALLIC_HYDROGEN = Reg.blockWithItem("metallic_hydrogen", p -> new GiantOceanBlock(p, 0xC8D2E0, 9000),
                Reg.metal(MapColor.METAL, 100f).noCollision().noLootTable().lightLevel(s -> 15));
        IONIC_OCEAN = Reg.blockWithItem("ionic_ocean", p -> new GiantOceanBlock(p, 0x3A7CFF, 5000),
                Reg.soil(MapColor.COLOR_BLUE, 100f).noCollision().noLootTable().lightLevel(s -> 12));
        GiantClimate.init();
    }

    static boolean ice(String planet) { return planet.equals("uranus") || planet.equals("neptune"); }

    private static Deck[] decks(String planet) {
        BlockState ammonia = TerrainBlocks.state(Mat.AMMONIA_CLOUD), nh4sh = TerrainBlocks.state(Mat.NH4SH_CLOUD),
                methane = TerrainBlocks.state(Mat.METHANE_CLOUD), water = WATER_CLOUD.defaultBlockState(),
                h2s = HYDROGEN_SULFIDE_CLOUD.defaultBlockState(), haze = DEEP_HAZE.defaultBlockState();
        return switch (planet) {
            case "jupiter" -> new Deck[]{new Deck(1120, 10, 0.32f, 40, ammonia), new Deck(-120, 16, 0.45f, 34, nh4sh),
                    new Deck(-620, 18, 0.55f, 30, water), new Deck(-1350, 10, 0.30f, 46, haze), new Deck(-1820, 8, 0.38f, 52, haze)};
            case "saturn" -> new Deck[]{new Deck(1150, 14, 0.40f, 48, ammonia), new Deck(-260, 18, 0.45f, 40, nh4sh),
                    new Deck(-900, 20, 0.55f, 36, water), new Deck(-1500, 12, 0.32f, 52, haze)};
            default -> new Deck[]{new Deck(1080, 12, 0.30f, 44, methane), new Deck(-80, 16, 0.42f, 36, h2s),
                    new Deck(-700, 16, 0.45f, 34, nh4sh), new Deck(-1250, 18, 0.50f, 30, water), new Deck(-1700, 10, 0.34f, 48, haze)};
        };
    }

    /** Adds the decks and the ocean to a freshly filled gas giant chunk. */
    public static void fill(String planet, ChunkAccess chunk) {
        if (chunk.getMinY() > MIN_Y + 16) return;                    // an old, short world
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long seed = planet.hashCode() * 0x9E3779B97F4A7C15L;
        for (Deck d : decks(planet)) {
            long s = seed ^ d.y * 0x632BE59BD9B4E019L;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                // bands: coverage changes with latitude-like z, cells give the cloud puffs
                double v = noise(s, x / (double) d.scale, z / (d.scale * 0.6)) * 0.65 + noise(s + 7, x / (d.scale * 0.3), z / (d.scale * 0.25)) * 0.35;
                double band = 0.5 + 0.5 * Math.sin(z / 90.0 + (s & 7));
                double c = v + (band - 0.5) * 0.25;
                if (c < 1 - d.cover) continue;
                double k = (c - (1 - d.cover)) / d.cover;                    // 0 at the edge of a puff, 1 inside
                int half = (int) Math.round(d.half * (0.35 + 0.65 * k));
                int mid = d.y + (int) Math.round((noise(s + 13, x / 20.0, z / 20.0) - 0.5) * d.half);
                for (int y = mid - half; y <= mid + half; y++) chunk.setBlockState(p.set(x, y, z), d.block, 0);
            }
        }
        BlockState ocean = (ice(planet) ? IONIC_OCEAN : METALLIC_HYDROGEN).defaultBlockState();
        BlockState floor = Blocks.BEDROCK.defaultBlockState();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = x0 + dx, z = z0 + dz;
            int top = OCEAN_TOP + (int) Math.round((noise(seed + 99, x / 14.0, z / 14.0) - 0.5) * 3);   // a gently heaving surface
            chunk.setBlockState(p.set(x, MIN_Y, z), floor, 0);
            for (int y = MIN_Y + 1; y <= top; y++) chunk.setBlockState(p.set(x, y, z), ocean, 0);
        }
    }

    /** Smooth value noise, 0..1. */
    static double noise(long seed, double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = hash(seed, xi, zi), b = hash(seed, xi + 1, zi), c = hash(seed, xi, zi + 1), d = hash(seed, xi + 1, zi + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    private static double hash(long seed, int x, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
