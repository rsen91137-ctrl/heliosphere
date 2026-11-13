package com.heliosphere.hostiles.arsenal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Block damage from machine weapons. Up to level 4 they break ordinary blocks; from level 5 they also cut
 * through obsidian, crying obsidian and ancient debris. Bedrock and other unbreakable blocks always survive.
 */
public final class MachineBlast {
    private MachineBlast() {}

    public static final int BREAKS_OBSIDIAN = 5;

    /**
     * A missile crater: every block within {@code radius} (a ragged sphere) whose blast resistance is at most
     * {@code limit} goes. Big craters drop little, so a heavy warhead does not bury the area in items.
     */
    public static void crater(ServerLevel level, Vec3 at, double radius, float limit) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = (int) Math.ceil(radius + 1);
        float drop = radius > 7 ? 0.03f : radius > 4 ? 0.1f : 0.25f;
        long seed = Double.doubleToLongBits(at.x * 31 + at.z);
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);              // a little flatter than a ball
            double edge = radius * (0.88 + 0.24 * hash(seed, dx, dy, dz));
            if (d > edge) continue;
            p.set(Math.floor(at.x) + dx, Math.floor(at.y) + dy, Math.floor(at.z) + dz);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0) continue;
            if (s.getBlock().getExplosionResistance() > limit) continue;
            if (level.random.nextFloat() < drop) level.destroyBlock(p, true);
            else level.setBlock(p, Blocks.AIR.defaultBlockState(), d > edge - 1.5 ? 3 : 2);
        }
    }

    private static double hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static void breakBlocks(ServerLevel level, Vec3 at, float radius, int tier) {
        float limit = tier >= BREAKS_OBSIDIAN ? 1300f : 100f;            // obsidian & ancient debris: 1200
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > radius + level.random.nextFloat() * 0.6 - 0.3) continue;
            p.set(Math.floor(at.x) + dx, Math.floor(at.y) + dy, Math.floor(at.z) + dz);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0) continue;
            if (s.getBlock().getExplosionResistance() > limit) continue;
            level.destroyBlock(p, level.random.nextFloat() < 0.25f);
        }
    }
}
