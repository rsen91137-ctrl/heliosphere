package com.heliosphere.rocketry.launch;

import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.entity.RocketStructure;
import com.heliosphere.rocketry.part.PartBlockEntity;
import com.heliosphere.rocketry.part.RocketPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds the launch pad next to a console and the rocket assembled on it. */
public final class RocketScanner {
    public static final int MAX_PAD = 121, MAX_PARTS = 4096;

    public record Scan(BlockPos origin, List<BlockPos> pad, List<BlockPos> parts, RocketStructure structure) {
        public boolean valid() { return !pad.isEmpty(); }
    }

    private RocketScanner() {}

    public static Scan scan(Level level, BlockPos console) {
        BlockPos start = null;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(console.relative(d)).is(Rocketry.LAUNCH_PAD)) { start = console.relative(d); break; }
        }
        if (start == null) return new Scan(console, List.of(), List.of(), RocketStructure.EMPTY);
        // pad: horizontally connected pad blocks at the same height
        List<BlockPos> pad = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && pad.size() < MAX_PAD) {
            BlockPos p = q.poll();
            pad.add(p);
            // 8-connected: diagonal neighbours count, so a console pad touching a corner still joins the pad
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) { int dy = 0;
                if (dx == 0 && dz == 0) continue;
                BlockPos n = p.offset(dx, dy, dz);
                if (seen.add(n) && level.getBlockState(n).is(Rocketry.LAUNCH_PAD)) q.add(n);
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : pad) { minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX()); minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ()); }
        BlockPos origin = new BlockPos(Math.floorDiv(minX + maxX, 2), start.getY() + 1, Math.floorDiv(minZ + maxZ, 2));
        // rocket: every part block connected to the pad's top surface
        List<BlockPos> parts = new ArrayList<>();
        seen.clear();
        q.clear();
        for (BlockPos p : pad) {
            BlockPos a = p.above();
            if (isPart(level, a) && seen.add(a)) q.add(a);
        }
        while (!q.isEmpty() && parts.size() < MAX_PARTS) {
            BlockPos p = q.poll();
            parts.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getY() >= origin.getY() && seen.add(n) && isPart(level, n)) q.add(n);
            }
        }
        List<RocketStructure.Part> list = new ArrayList<>();
        for (BlockPos p : parts) {
            RocketPartBlock b = (RocketPartBlock) level.getBlockState(p).getBlock();
            float fill = 0, tol = 0.85f;
            boolean tested = false;
            if (level.getBlockEntity(p) instanceof PartBlockEntity be) { fill = be.fill(); tol = be.tolerance(); tested = be.tested(); }
            list.add(new RocketStructure.Part(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ(),
                    Block.getId(level.getBlockState(p)), b.specId(), fill, tol, tested));
        }
        return new Scan(origin, pad, parts, new RocketStructure(list));
    }

    private static boolean isPart(Level level, BlockPos p) { return level.getBlockState(p).getBlock() instanceof RocketPartBlock; }
}
