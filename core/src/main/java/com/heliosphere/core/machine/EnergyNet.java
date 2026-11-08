package com.heliosphere.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pushes energy from a source to adjacent consumers and along connected power cables. */
public final class EnergyNet {
    private static final int MAX_NODES = 1024;

    private EnergyNet() {}

    public static void push(Level level, BlockPos origin, EnergyNode source, long maxAmount) {
        long available = Math.min(source.energy(), maxAmount);
        if (available <= 0) return;
        List<EnergyNode> sinks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        for (Direction d : Direction.values()) queue.add(origin.relative(d));
        boolean sourceIsBattery = source.canReceive();
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || !level.isLoaded(p)) continue;
            if (level.getBlockState(p).getBlock() instanceof CableBlock) {
                for (Direction d : Direction.values()) queue.add(p.relative(d));
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof EnergyNode n && n != source && n.canReceive() && n.energy() < n.capacity()) {
                // batteries don't feed other batteries, so storage doesn't slosh back and forth
                if (sourceIsBattery && n.canExtract()) continue;
                sinks.add(n);
            }
        }
        if (sinks.isEmpty()) return;
        // Consumers first, batteries last
        sinks.sort((a, b) -> Boolean.compare(a.canExtract(), b.canExtract()));
        long share = Math.max(1, available / sinks.size());
        long given = 0;
        for (EnergyNode n : sinks) {
            if (given >= available) break;
            given += n.receive(Math.min(share * 2, available - given));
        }
        source.setEnergy(source.energy() - given);
    }
}
