package com.heliosphere.sphereworld.ores;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Every ore found on Earth also generates on the other rocky worlds, as veins in their own rock: all the vanilla ores
 * and all of Heliosphere's overworld ores. Depths are below the local surface (Earth's sea level counted as the
 * surface for the mod ores' height ranges); below 64 blocks the deepslate variant is used. A bit sparser than on
 * Earth, on top of each world's own deposits.
 */
public final class EarthOres {
    private EarthOres() {}

    /** One kind of vein: block near the surface / deep, veins per chunk (fractions are a chance), size, depth range. */
    private record Vein(String block, String deep, float perChunk, int size, int minDepth, int maxDepth) {}

    private static final Vein[] VEINS = {
            // vanilla
            new Vein("minecraft:coal_ore", "minecraft:deepslate_coal_ore", 10, 12, 3, 90),
            new Vein("minecraft:iron_ore", "minecraft:deepslate_iron_ore", 9, 9, 4, 130),
            new Vein("minecraft:copper_ore", "minecraft:deepslate_copper_ore", 7, 10, 4, 90),
            new Vein("minecraft:gold_ore", "minecraft:deepslate_gold_ore", 3, 8, 30, 140),
            new Vein("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", 4, 7, 50, 160),
            new Vein("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore", 2, 6, 30, 140),
            new Vein("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", 2, 6, 70, 200),
            new Vein("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", 1, 3, 10, 110),
            // Heliosphere's overworld ores (counts and depths from their placed features)
            new Vein("heliosphere:oil_sand", "heliosphere:oil_sand", 2, 16, 3, 40),
            new Vein("heliosphere:apatite_ore", "heliosphere:deepslate_apatite_ore", 2, 7, 3, 80),
            new Vein("heliosphere:bauxite_ore", "heliosphere:deepslate_bauxite_ore", 4, 12, 3, 40),
            new Vein("heliosphere:beryl_ore", "heliosphere:deepslate_beryl_ore", 1, 4, 16, 96),
            new Vein("heliosphere:cassiterite_ore", "heliosphere:deepslate_cassiterite_ore", 3, 8, 3, 80),
            new Vein("heliosphere:chromite_ore", "heliosphere:deepslate_chromite_ore", 2.5f, 7, 16, 96),
            new Vein("heliosphere:cobaltite_ore", "heliosphere:deepslate_cobaltite_ore", 1.5f, 5, 48, 112),
            new Vein("heliosphere:fluorite_ore", "heliosphere:deepslate_fluorite_ore", 2.5f, 8, 3, 80),
            new Vein("heliosphere:galena_ore", "heliosphere:deepslate_galena_ore", 3, 8, 16, 96),
            new Vein("heliosphere:graphite_ore", "heliosphere:deepslate_graphite_ore", 2.5f, 8, 3, 96),
            new Vein("heliosphere:halite_ore", "heliosphere:deepslate_halite_ore", 2, 12, 3, 64),
            new Vein("heliosphere:ilmenite_ore", "heliosphere:deepslate_ilmenite_ore", 3, 8, 16, 112),
            new Vein("heliosphere:molybdenite_ore", "heliosphere:deepslate_molybdenite_ore", 1.5f, 6, 48, 112),
            new Vein("heliosphere:monazite_ore", "heliosphere:deepslate_monazite_ore", 1.5f, 5, 40, 112),
            new Vein("heliosphere:pentlandite_ore", "heliosphere:deepslate_pentlandite_ore", 3, 8, 16, 112),
            new Vein("heliosphere:pyrochlore_ore", "heliosphere:deepslate_pyrochlore_ore", 1.5f, 5, 48, 128),
            new Vein("heliosphere:sperrylite_ore", "heliosphere:deepslate_sperrylite_ore", 0.5f, 3, 96, 128),
            new Vein("heliosphere:sphalerite_ore", "heliosphere:deepslate_sphalerite_ore", 2.5f, 8, 16, 96),
            new Vein("heliosphere:spodumene_ore", "heliosphere:deepslate_spodumene_ore", 2.5f, 6, 3, 80),
            new Vein("heliosphere:sulfur_ore", "heliosphere:deepslate_sulfur_ore", 2.5f, 8, 32, 128),
            new Vein("heliosphere:titanomagnetite_ore", "heliosphere:deepslate_titanomagnetite_ore", 2, 7, 16, 96),
            new Vein("heliosphere:uraninite_ore", "heliosphere:deepslate_uraninite_ore", 1, 4, 80, 128),
            new Vein("heliosphere:wolframite_ore", "heliosphere:deepslate_wolframite_ore", 1.5f, 6, 64, 128),
    };

    private record Resolved(Vein v, BlockState shallow, BlockState deep) {}
    private static volatile List<Resolved> resolved;

    private static List<Resolved> veins() {
        List<Resolved> r = resolved;
        if (r != null) return r;
        r = new ArrayList<>();
        for (Vein v : VEINS) {
            Block a = block(v.block), b = block(v.deep);
            if (a == null) continue;
            r.add(new Resolved(v, a.defaultBlockState(), (b == null ? a : b).defaultBlockState()));
        }
        resolved = r;
        return r;
    }

    private static Block block(String id) {
        var key = Identifier.parse(id);
        return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key) : null;
    }

    /** Places the Earth ores in a freshly filled planet chunk; {@code tops} are the surface heights per column (lx*16+lz). */
    public static void place(ChunkAccess chunk, int[] tops, RandomSource rand) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int floor = chunk.getMinY() + 5;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Resolved o : veins()) {
            Vein v = o.v;
            int n = (int) v.perChunk + (rand.nextFloat() < v.perChunk - (int) v.perChunk ? 1 : 0);
            for (int k = 0; k < n; k++) {
                int x = rand.nextInt(16), z = rand.nextInt(16);
                int top = tops[x * 16 + z];
                int depth = v.minDepth + rand.nextInt(Math.max(1, v.maxDepth - v.minDepth));
                int y = top - depth;
                if (y <= floor) continue;
                BlockState ore = depth > 64 ? o.deep : o.shallow;
                int size = Math.max(2, v.size / 2 + rand.nextInt(v.size / 2 + 1));
                for (int i = 0; i < size; i++) {
                    if (y > floor && y < top) {
                        at.set(x0 + x, y, z0 + z);
                        BlockState here = chunk.getBlockState(at);
                        if (!here.isAir() && here.getFluidState().isEmpty() && !here.is(Blocks.BEDROCK) && here.isSolid())
                            chunk.setBlockState(at, ore);
                    }
                    switch (rand.nextInt(3)) {
                        case 0 -> x = Mth.clamp(x + rand.nextInt(3) - 1, 0, 15);
                        case 1 -> z = Mth.clamp(z + rand.nextInt(3) - 1, 0, 15);
                        default -> y += rand.nextInt(3) - 1;
                    }
                }
            }
        }
    }
}
