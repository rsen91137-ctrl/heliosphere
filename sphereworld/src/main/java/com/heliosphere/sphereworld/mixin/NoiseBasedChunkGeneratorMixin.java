package com.heliosphere.sphereworld.mixin;

import com.heliosphere.sphereworld.gen.EarthBiomeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * On the spherical Earth, reshapes freshly generated terrain near the antimeridian into an ocean strait
 * and near the poles into a flat ice sheet, so wrapping around the map is seamless.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void heliosphere$shapeEarth(Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk,
                                        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!(((ChunkGenerator) (Object) this).getBiomeSource() instanceof EarthBiomeSource)) return;
        ChunkPos cp = chunk.getPos();
        double seamMax = Math.max(EarthBiomeSource.seamFactor(cp.getMinBlockX()), EarthBiomeSource.seamFactor(cp.getMaxBlockX()));
        double polarMax = Math.max(EarthBiomeSource.polarFactor(cp.getMinBlockZ()), EarthBiomeSource.polarFactor(cp.getMaxBlockZ()));
        if (seamMax <= 0 && polarMax <= 0) return;
        cir.setReturnValue(cir.getReturnValue().thenApply(c -> { reshape(c); return c; }));
    }

    private static void reshape(ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        BlockState packed = Blocks.PACKED_ICE.defaultBlockState(), snow = Blocks.SNOW_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int sea = 63, floor = 28, plateau = 96;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
            double s = EarthBiomeSource.seamFactor(x), p = EarthBiomeSource.polarFactor(z);
            if (s <= 0 && p <= 0) continue;
            int top = topSolid(chunk, m, x, z);
            if (p > 0) {
                int target = (int) Math.round(top + (plateau - top) * smooth(p));
                for (int y = chunk.getMinY() + 5; y <= Math.max(top, target) + 1; y++) {
                    m.set(x, y, z);
                    BlockState st;
                    if (y > target) st = air;
                    else if (y > target - 4) st = p > 0.6 ? packed : chunk.getBlockState(m).isAir() ? stone : chunk.getBlockState(m);
                    else if (chunk.getBlockState(m).isAir() || !chunk.getBlockState(m).getFluidState().isEmpty()) st = stone;
                    else continue;
                    chunk.setBlockState(m, st);
                }
                if (p > 0.6) chunk.setBlockState(m.set(x, target + 1, z), snow);
            } else {
                int target = (int) Math.round(top + (floor - top) * smooth(s));
                if (target >= top && top >= sea) continue;
                for (int y = Math.min(target, top) - 2; y <= Math.max(top, sea) + 1; y++) {
                    m.set(x, y, z);
                    if (y <= target) { if (chunk.getBlockState(m).isAir()) chunk.setBlockState(m, stone); }
                    else chunk.setBlockState(m, y <= sea ? water : air);
                }
            }
        }
    }

    private static int topSolid(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int z) {
        for (int y = chunk.getMaxY(); y > chunk.getMinY(); y--) {
            BlockState s = chunk.getBlockState(m.set(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty()) return y;
        }
        return chunk.getMinY();
    }

    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
}
