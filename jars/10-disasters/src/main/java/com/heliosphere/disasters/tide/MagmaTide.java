package com.heliosphere.disasters.tide;

import com.heliosphere.core.disaster.ImpactExpansionLedger;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.fx.sim.Rng;
import com.heliosphere.core.world.PlanetGeometry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * After an asteroid strike the whole planet's surface is slowly given over to magma, not only the impact
 * region. Every impact in the ledger contributes: near it the ground melts within a day or two and is eaten
 * away ten blocks deep; half a world away it takes weeks, and only the top block or two melt, with more of
 * it crusting over. Several impacts compound.
 *
 * <p>The planet is too big to process, so the tide is applied where it can be seen: around every player on
 * Earth, columns are sampled at random and brought up to date with the coverage expected at that place and
 * time. Walk somewhere new and the lava that should already be there spreads in over a minute or two.
 *
 * <p>Only natural ground melts. A column whose top is something built (planks, glass, concrete, ...) is left
 * alone, and so are the seas.
 */
public final class MagmaTide {
    private MagmaTide() {}

    /** Days for coverage to reach 63% at the impact point; grows with distance (see {@link #timescale}). */
    private static final double T0_DAYS = 1.2;
    private static final double T_DISTANCE = 6000;
    private static final double T_EXPONENT = 1.1;
    private static final int DAY_TICKS = 24000;
    private static final int SAMPLE_RADIUS = 112;
    private static final int SAMPLES_PER_PLAYER = 240;
    private static final int CONVERT_BUDGET = 160;
    private static final int SILENT = 818;
    private static final int REPORT_TICKS = 3600;

    record Impact(int x, int y, int z, long started) {}

    private static Method stateMethod;
    private static Field jobsField;
    private static boolean broken;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.dimension() == Level.OVERWORLD && level.getGameTime() % 10 == 0) tick(level);
        });
    }

    /** The impacts recorded by the released ImpactExpansionLedger (primary strike and every shower since). */
    static List<Impact> impacts(ServerLevel level) {
        List<Impact> out = new ArrayList<>();
        if (broken) return out;
        try {
            if (stateMethod == null) {
                stateMethod = ImpactExpansionLedger.class.getDeclaredMethod("state", ServerLevel.class);
                stateMethod.setAccessible(true);
            }
            Object state = stateMethod.invoke(null, level);
            if (jobsField == null) {
                jobsField = state.getClass().getDeclaredField("jobs");
                jobsField.setAccessible(true);
            }
            for (Object o : (List<?>) jobsField.get(state)) {
                ImpactExpansionLedger.Job j = (ImpactExpansionLedger.Job) o;
                out.add(new Impact(j.x, j.y, j.z, j.started));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;                                      // never spam: the tide just stays off
            com.heliosphere.core.Heliosphere.LOG.error("Heliosphere magma tide disabled: cannot read the impact ledger", e);
        }
        return out;
    }

    /** Horizontal distance on Earth, with longitude wrapping round the planet. */
    static double distance(ServerLevel level, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0;
        PlanetGeometry g = Planets.geometry(level);
        if (g != null && g.circumference > 0) {
            double c = g.circumference;
            dx = ((dx % c) + c * 1.5) % c - c * 0.5;
        }
        return Math.sqrt(dx * dx + dz * dz);
    }

    static double timescale(double distance) {
        return T0_DAYS * Math.pow(1 + distance / T_DISTANCE, T_EXPONENT) * DAY_TICKS;
    }

    /** Expected molten fraction of the surface at (x, z) now, all impacts together. */
    static double coverage(ServerLevel level, List<Impact> impacts, double x, double z, double[] nearest) {
        double keep = 1;
        nearest[0] = Double.MAX_VALUE;
        long now = level.getGameTime();
        for (Impact i : impacts) {
            long age = now - i.started;
            if (age <= 0) continue;
            double d = distance(level, i.x, i.z, x, z);
            nearest[0] = Math.min(nearest[0], d);
            keep *= Math.exp(-age / timescale(d));
        }
        return 1 - keep;
    }

    private static void tick(ServerLevel level) {
        if (level.players().isEmpty()) return;
        List<Impact> impacts = impacts(level);
        if (impacts.isEmpty()) return;
        RandomSource random = level.getRandom();
        int budget = CONVERT_BUDGET;
        double[] nearest = new double[1];
        boolean report = level.getGameTime() % REPORT_TICKS == 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (report) {
                double c = coverage(level, impacts, player.getX(), player.getZ(), nearest);
                if (c > 0.002) player.displayClientMessage(Component.translatable("message.heliosphere.magma_tide",
                        String.format("%.1f", c * 100), String.format("%.0f", nearest[0] / 1000.0)), true);
            }
            for (int n = 0; n < SAMPLES_PER_PLAYER && budget > 0; n++) {
                int x = player.getBlockX() + random.nextInt(SAMPLE_RADIUS * 2 + 1) - SAMPLE_RADIUS;
                int z = player.getBlockZ() + random.nextInt(SAMPLE_RADIUS * 2 + 1) - SAMPLE_RADIUS;
                double c = coverage(level, impacts, x, z, nearest);
                if (c <= threshold(x, z)) continue;
                if (melt(level, x, z, nearest[0])) budget--;
            }
        }
    }

    /**
     * When a column gives way, as a fraction of coverage: mostly large-scale noise, so the magma comes in
     * flows and pools that widen and join, with a little grain so their edges are ragged.
     */
    static double threshold(int x, int z) {
        float broad = Rng.fbm(x / 96f, 0.37f, z / 96f, 9137, 4);
        float grain = (Rng.hash(x * 73856093 ^ z * 19349663 ^ 0x5bd1e995) >>> 8) / 16777216f;
        return Math.min(0.995, Math.max(0.0, (broad - 0.25) * 1.6 * 0.82 + grain * 0.18));
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.CLAY) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES)
                || s.is(BlockTags.COPPER_ORES) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(Blocks.TUFF) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.POWDER_SNOW);
    }

    /** Things that burn off or wash away before the ground beneath melts. */
    private static boolean cover(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SAPLINGS) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || s.is(Blocks.SNOW) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.BEE_NEST) || s.is(Blocks.VINE) || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.FIRE);
    }

    private static boolean molten(BlockState s) {
        return s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.OBSIDIAN);
    }

    /** Melts the column at (x, z) if it is natural ground. Returns whether anything changed. */
    static boolean melt(ServerLevel level, int x, int z, double distance) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        int floor = Math.max(level.getMinY() + 1, y - 48);
        // strip what grows on top (trees, plants, snow) down to the ground
        List<BlockPos> stripped = new ArrayList<>();
        BlockState ground = null;
        for (; y > floor; y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.isAir()) continue;
            if (cover(s)) { stripped.add(p.immutable()); continue; }
            ground = s;
            break;
        }
        if (ground == null || molten(ground) || !ground.getFluidState().isEmpty() || !natural(ground)) return false;
        for (BlockPos q : stripped) level.setBlock(q, Blocks.AIR.defaultBlockState(), SILENT);

        // near the impact the ground is eaten away; far away only the skin melts
        float n = Rng.fbm(x / 40f, 0.81f, z / 40f, 4421, 3);
        int depth = Math.max(1, (int) Math.round((1 + 10 * Math.exp(-distance / 25000)) * (0.55 + 0.9 * n)));
        int bottom = y;
        for (int i = 0; i < depth - 1; i++) {
            BlockState s = level.getBlockState(p.set(x, bottom, z));
            BlockState below = level.getBlockState(p.set(x, bottom - 1, z));
            if (!natural(s) || !natural(below) || bottom - 1 <= level.getMinY()) break;
            level.setBlock(p.set(x, bottom, z), Blocks.AIR.defaultBlockState(), SILENT);
            bottom--;
        }
        // what the melt becomes: open lava, or a crust over it (more crust the further out)
        double crust = 0.18 + 0.5 * (1 - Math.exp(-distance / 60000));
        int h = Rng.hash(x * 31 + z * 7919 + 77);
        double roll = (h >>> 8) / 16777216.0;
        BlockState top;
        if (roll < crust * 0.55) top = Blocks.MAGMA_BLOCK.defaultBlockState();
        else if (roll < crust * 0.8) top = Blocks.BASALT.defaultBlockState();
        else if (roll < crust) top = Blocks.BLACKSTONE.defaultBlockState();
        else top = Blocks.LAVA.defaultBlockState();
        p.set(x, bottom, z);
        // never open lava under someone's feet: they get a crust and a warning instead
        if (top.is(Blocks.LAVA) && level.getNearestPlayer(x + 0.5, bottom + 1, z + 0.5, 2.5, false) != null)
            top = Blocks.MAGMA_BLOCK.defaultBlockState();
        level.setBlock(p, top, top.is(Blocks.LAVA) ? 3 : SILENT);
        // a pool hanging over a cave would pour into it: give it a floor of crust
        if (top.is(Blocks.LAVA)) {
            BlockState under = level.getBlockState(p.set(x, bottom - 1, z));
            if (under.isAir() || !under.getFluidState().isEmpty()) level.setBlock(p.set(x, bottom, z), Blocks.MAGMA_BLOCK.defaultBlockState(), SILENT);
        }
        return true;
    }
}
