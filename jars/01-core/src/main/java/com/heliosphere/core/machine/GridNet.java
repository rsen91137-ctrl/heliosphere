package com.heliosphere.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The power grid: what a generator or battery is connected to through cables, and how its output is shared out.
 *
 * <p>The released search stopped after 1024 positions, counting every block it looked at beside a cable (air, stone,
 * dirt) as well as the cables: past about two hundred blocks of cable a machine at the far end was never found, so
 * a long line "sometimes" carried no power. Here only cables count (up to 8192 per network), every machine touching
 * the network is found once, and the result is kept for two seconds so long lines cost little. Output goes to
 * machines first and is shared evenly, topping up whoever still has room; a battery never feeds another battery.
 */
public final class GridNet {
    private GridNet() {}

    private static final int MAX_CABLES = 8192, KEEP = 40;
    private static final Identifier CABLE = Identifier.fromNamespaceAndPath("heliosphere", "cable");
    private static Block cable;

    private record Found(List<BlockPos> sinks, long at) {}
    private static final Map<Level, Map<Long, Found>> CACHE = new WeakHashMap<>();

    static boolean isCable(Block b) {
        if (cable == null) cable = BuiltInRegistries.BLOCK.getValue(CABLE);
        return b == cable || b instanceof CableBlockMarker;
    }

    /** Implemented by any other block that should carry power like a cable. */
    public interface CableBlockMarker {}

    /** Everything that can take power on the network {@code pos} is part of (the source itself excluded). */
    public static List<BlockPos> sinks(Level level, BlockPos pos) {
        long now = level.getGameTime();
        Map<Long, Found> byPos = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        Found f = byPos.get(pos.asLong());
        if (f != null && now - f.at < KEEP && now >= f.at) return f.sinks;
        if (byPos.size() > 4096) byPos.values().removeIf(x -> now - x.at > KEEP * 5);
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> sinks = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) queue.add(pos.relative(d));
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (p.equals(pos) || cables.contains(p) || sinks.contains(p) || !level.isLoaded(p)) continue;
            if (isCable(level.getBlockState(p).getBlock())) {
                if (cables.size() >= MAX_CABLES) continue;
                cables.add(p);
                for (Direction d : Direction.values()) { BlockPos q = p.relative(d); if (!cables.contains(q)) queue.add(q); }
            } else if (level.getBlockEntity(p) instanceof EnergyNode) sinks.add(p.immutable());
        }
        List<BlockPos> out = List.copyOf(sinks);
        byPos.put(pos.asLong(), new Found(out, now));
        return out;
    }

    /** Sends up to {@code amount} from {@code source} (at {@code pos}) into its network. */
    public static void push(Level level, BlockPos pos, EnergyNode source, long amount) {
        long budget = Math.min(amount, source.energy());
        if (budget <= 0) return;
        boolean storage = source.canReceive();                       // a battery: it feeds machines, not other batteries
        List<EnergyNode> machines = new ArrayList<>(), batteries = new ArrayList<>();
        for (BlockPos p : sinks(level, pos)) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof EnergyNode n) || n == source) continue;
            if (!n.canReceive() || n.energy() >= n.capacity()) continue;
            if (n.canExtract()) { if (!storage) batteries.add(n); }
            else machines.add(n);
        }
        long sent = share(machines, budget);
        sent += share(batteries, budget - sent);
        if (sent > 0) source.setEnergy(source.energy() - sent);
    }

    /** Shares {@code budget} evenly, in a few rounds so that what a full one cannot take goes to the others. */
    private static long share(List<EnergyNode> to, long budget) {
        long sent = 0;
        for (int round = 0; round < 3 && budget - sent > 0 && !to.isEmpty(); round++) {
            long each = Math.max(1, (budget - sent) / to.size());
            List<EnergyNode> room = new ArrayList<>();
            for (EnergyNode n : to) {
                if (budget - sent <= 0) break;
                long took = n.receive(Math.min(each, budget - sent));
                sent += took;
                if (n.energy() < n.capacity()) room.add(n);
            }
            to = room;
        }
        return sent;
    }
}
