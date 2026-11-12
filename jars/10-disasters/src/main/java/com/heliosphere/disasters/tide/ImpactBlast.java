package com.heliosphere.disasters.tide;

import com.heliosphere.core.fx.sim.AsteroidImpact;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The asteroid's blast wave on the ground. The released code already hurts and throws whoever it reaches; this
 * adds what it does to the world: as the front passes a player it knocks down buildings around them, more the
 * nearer the impact, and any rocket in the air is torn apart with everyone aboard.
 */
public final class ImpactBlast {
    private ImpactBlast() {}

    private static final int RADIUS = 112, SILENT = 818, BUDGET = 3000;
    /** Blocks per tick (the released blast front: 8 blocks a tick). */
    private static final double FRONT_PER_TICK = AsteroidImpact.WAVE_SPEED / 20.0;

    private record Job(int cx, int cz, double severity, int[] cursor) {}
    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static final Set<String> DONE = new HashSet<>();

    static void init() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.dimension() != Level.OVERWORLD) return;
            if (level.getGameTime() % 4 == 0) watch(level);
            work(level);
        });
    }

    private static void watch(ServerLevel level) {
        List<MagmaTide.Impact> impacts = MagmaTide.impacts(level);
        if (impacts.isEmpty()) return;
        long now = level.getGameTime();
        for (MagmaTide.Impact i : impacts) {
            long age = now - i.started();
            double front = age * FRONT_PER_TICK;
            if (age < 0 || front > AsteroidImpact.WAVE_REACH + RADIUS) continue;
            for (ServerPlayer p : level.players()) {
                double d = MagmaTide.distance(level, i.x(), i.z(), p.getX(), p.getZ());
                if (d > front || d > AsteroidImpact.WAVE_REACH) continue;
                String key = i.started() + ":" + p.getUUID();
                if (!DONE.add(key)) continue;
                double severity = Math.max(0, 1 - d / AsteroidImpact.WAVE_REACH);
                JOBS.add(new Job(p.getBlockX(), p.getBlockZ(), severity, new int[1]));
                p.displayClientMessage(Component.translatable("message.heliosphere.impact_wave"), true);
                if (severity > 0.3) wreckRockets(level, p, severity);
            }
        }
        if (DONE.size() > 4096) DONE.clear();
    }

    /** Any rocket off the ground near the wave is destroyed, and its crew with it. */
    private static void wreckRockets(ServerLevel level, ServerPlayer near, double severity) {
        EntityType<?> rocket = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("heliosphere", "rocket"));
        if (rocket == null) return;
        for (Entity e : level.getEntities((Entity) null, near.getBoundingBox().inflate(512), en -> en.getType() == rocket)) {
            boolean flying = !e.onGround() || e.getDeltaMovement().y > 0.02 || !e.getPassengers().isEmpty() && e.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ()) + 2;
            if (!flying) continue;
            for (Entity crew : List.copyOf(e.getPassengers())) {
                if (crew instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                crew.stopRiding();
                crew.hurtServer(level, level.damageSources().explosion(null, null), Float.MAX_VALUE);
                if (crew instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.heliosphere.rocket_torn"), false);
            }
            level.explode(e, e.getX(), e.getY() + 2, e.getZ(), (float) (6 + 6 * severity), Level.ExplosionInteraction.NONE);
            e.discard();
        }
    }

    private static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.BASALT)
                || s.is(Blocks.BLACKSTONE) || s.is(Blocks.OBSIDIAN) || !s.getFluidState().isEmpty();
    }

    /** Knocks down what stands above the natural ground around one player, a budget of columns a tick. */
    private static void work(ServerLevel level) {
        int budget = BUDGET;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (budget > 0 && !JOBS.isEmpty()) {
            Job j = JOBS.peek();
            int columns = (2 * RADIUS + 1) * (2 * RADIUS + 1);
            if (j.cursor[0] >= columns) { JOBS.poll(); continue; }
            int[] s = spiral(j.cursor[0]++);
            if (s[0] * s[0] + s[1] * s[1] > RADIUS * RADIUS) continue;
            int x = j.cx + s[0], z = j.cz + s[1];
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) continue;
            budget--;
            double sev = j.severity;
            float limit = sev > 0.85 ? 600f : sev > 0.6 ? 7f : sev > 0.35 ? 3.1f : sev > 0.15 ? 0.9f : 0.35f;
            int top = Math.min(level.getMaxY() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
            for (int y = top, guard = 0; y > level.getMinY() && guard < 96; y--, guard++) {
                BlockState st = level.getBlockState(p.set(x, y, z));
                if (st.isAir()) continue;
                if (ground(st)) break;
                if (st.getBlock().getExplosionResistance() <= limit && st.getDestroySpeed(level, p) >= 0) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), SILENT);
                    budget--;
                }
            }
        }
    }

    static int[] spiral(int n) {
        if (n == 0) return new int[]{0, 0};
        int ring = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        int side = 2 * ring, first = (2 * ring - 1) * (2 * ring - 1);
        int k = n - first, edge = k / side, off = k % side;
        return switch (edge) {
            case 0 -> new int[]{ring, -ring + 1 + off};
            case 1 -> new int[]{ring - 1 - off, ring};
            case 2 -> new int[]{-ring, ring - 1 - off};
            default -> new int[]{-ring + 1 + off, -ring};
        };
    }
}
