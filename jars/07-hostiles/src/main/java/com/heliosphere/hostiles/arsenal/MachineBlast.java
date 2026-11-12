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
