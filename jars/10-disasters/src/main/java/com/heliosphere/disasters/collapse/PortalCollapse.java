package com.heliosphere.disasters.collapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * After the collapse a portal has nothing left to open onto. Lighting a Nether portal frame, completing an End
 * portal or touching a portal that was already open makes it shatter: the portal sheet goes, the frame cracks,
 * and the stored energy blows out.
 *
 * <p>The mixins only queue the work; it runs at the end of the server tick, away from block placement and
 * entity ticking.
 */
public final class PortalCollapse {
    private PortalCollapse() {}

    private static final int MAX_BLOCKS = 1024;
    private record Job(ResourceKey<Level> dimension, List<BlockPos> sheet, boolean end) {}
    private static final Map<String, Job> QUEUE = new LinkedHashMap<>();

    /** Whether a portal of this kind, in this level, would lead into a dimension that no longer exists. */
    public static boolean blocked(Level level, boolean end) {
        if (!(level instanceof ServerLevel sl)) return false;
        if (end ? level.dimension() == Level.END : level.dimension() == Level.NETHER) return false;
        return DimensionCollapse.collapsed(sl.getServer());
    }

    /** An open portal was touched: shatter the whole connected sheet. */
    public static void touched(ServerLevel level, BlockPos pos, boolean end) {
        Block portal = end ? Blocks.END_PORTAL : Blocks.NETHER_PORTAL;
        String key = level.dimension().identifier() + "@" + pos.asLong();
        if (QUEUE.containsKey(key)) return;
        List<BlockPos> sheet = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(pos.immutable());
        seen.add(pos.immutable());
        while (!open.isEmpty() && sheet.size() < MAX_BLOCKS) {
            BlockPos p = open.poll();
            if (!level.getBlockState(p).is(portal)) continue;
            sheet.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n)) open.add(n);
            }
        }
        if (sheet.isEmpty()) return;
        for (BlockPos p : sheet) QUEUE.put(level.dimension().identifier() + "@" + p.asLong(), null);
        QUEUE.put(key, new Job(level.dimension(), sheet, end));
    }

    /** A portal was about to form over these blocks (the frame's inside). */
    public static void forming(ServerLevel level, List<BlockPos> inside, boolean end) {
        if (inside.isEmpty()) return;
        QUEUE.put(level.dimension().identifier() + "@" + inside.get(0).asLong(), new Job(level.dimension(), inside, end));
    }

    static void clear() { QUEUE.clear(); }

    static void flush(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        List<Job> jobs = new ArrayList<>();
        for (Job j : QUEUE.values()) if (j != null) jobs.add(j);
        QUEUE.clear();
        for (Job j : jobs) {
            ServerLevel level = server.getLevel(j.dimension());
            if (level != null) shatter(level, j.sheet(), j.end());
        }
    }

    private static void shatter(ServerLevel level, List<BlockPos> sheet, boolean end) {
        RandomSource r = level.getRandom();
        double cx = 0, cy = 0, cz = 0;
        Set<BlockPos> inside = new HashSet<>(sheet);
        for (BlockPos p : sheet) {
            cx += p.getX() + 0.5; cy += p.getY() + 0.5; cz += p.getZ() + 0.5;
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE))
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        cx /= sheet.size(); cy /= sheet.size(); cz /= sheet.size();
        // crack the frame
        Set<BlockPos> frame = new HashSet<>();
        for (BlockPos p : sheet) for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            if (!inside.contains(n)) frame.add(n);
        }
        for (BlockPos n : frame) {
            BlockState s = level.getBlockState(n);
            if (s.is(Blocks.OBSIDIAN)) {
                float roll = r.nextFloat();
                if (roll < 0.3f) {
                    level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, n, Block.getId(s));
                    level.setBlock(n, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else if (roll < 0.75f) {
                    level.setBlock(n, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                }
            } else if (s.is(Blocks.END_PORTAL_FRAME) && s.getValue(EndPortalFrameBlock.HAS_EYE)) {
                level.setBlock(n, s.setValue(EndPortalFrameBlock.HAS_EYE, false), Block.UPDATE_ALL);
                level.levelEvent(LevelEvent.PARTICLES_EYE_OF_ENDER_DEATH, n.above(), 0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 260, 1.2, 1.6, 1.2, 0.9);
        level.sendParticles(end ? ParticleTypes.END_ROD : ParticleTypes.PORTAL, cx, cy, cz, 180, 1, 1.5, 1, 1.5);
        level.playSound(null, cx, cy, cz, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2f, 0.5f);
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.explode(null, cx, cy, cz, end ? 4.5f : 3.5f, false, Level.ExplosionInteraction.BLOCK);
        Component msg = Component.translatable(end ? "message.heliosphere.portal_shatter_end" : "message.heliosphere.portal_shatter_nether");
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(cx, cy, cz) < 48 * 48) p.sendSystemMessage(msg);
    }
}
