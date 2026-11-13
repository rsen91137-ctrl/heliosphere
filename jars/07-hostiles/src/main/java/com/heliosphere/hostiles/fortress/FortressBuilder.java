package com.heliosphere.hostiles.fortress;

import com.heliosphere.hostiles.arsenal.EnemyRoster;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Machine fortresses in four size classes:
 * <ul>
 *   <li>outposts (difficulty 1-4): a walled compound with corner towers, a keep and a few buildings;</li>
 *   <li>fortresses (5-9): a moat, thick walls with walkways and gatehouses, round towers, a courtyard of barracks,
 *       factory halls, radar domes, hangars and depots, a tall keep and an underground bunker;</li>
 *   <li>citadels (10-12): all that, larger, with an inner wall around the keep;</li>
 *   <li>mega-fortresses (13-15): up to some 160 blocks across, towers along every wall, three gates, a reactor,
 *       a stepped keep and several wardens.</li>
 * </ul>
 * A fortress is planned first (every block into a list) and then placed a slice per tick (see {@link Job}), so
 * even the largest one does not stall the server. Robot fabricators sit in the factory halls, the keep, the towers
 * and the bunker; garrison and wardens appear when the last block is in place.
 */
public final class FortressBuilder {
    private FortressBuilder() {}

    private static final int FLAGS = 2 | 16;

    public record Result(List<BlockPos> fabricators, List<String> bosses, BlockPos keepTop) {}

    /** Outer radius of a fortress of this difficulty (half its width). */
    public static int radius(int tier) {
        if (tier <= 4) return 16 + tier * 2;
        if (tier <= 9) return 26 + (tier - 5) * 3;
        if (tier <= 12) return 42 + (tier - 10) * 4;
        return 62 + (tier - 13) * 8;
    }

    public static Job plan(ServerLevel level, int cx, int cz, int tier, FortressStyle st, RandomSource r) {
        Job j = new Job(level, cx, cz, tier, st, r);
        j.design();
        return j;
    }

    /** One fortress being built. */
    public static final class Job {
        final ServerLevel level;
        final int cx, cz, tier, y0, R, wallH, thick;
        final FortressStyle st;
        final RandomSource r;
        private long[] pos = new long[1 << 16];
        private BlockState[] states = new BlockState[1 << 16];
        private int count, cursor;
        final List<BlockPos> fabSpots = new ArrayList<>(), chestSpots = new ArrayList<>();
        final List<Vec3> guardSpots = new ArrayList<>(), bossSpots = new ArrayList<>();
        BlockPos keepTop;
        private final BlockState air = Blocks.AIR.defaultBlockState();
        private final boolean breathable;

        Job(ServerLevel level, int cx, int cz, int tier, FortressStyle st, RandomSource r) {
            this.level = level; this.cx = cx; this.cz = cz; this.tier = tier; this.st = st; this.r = r;
            this.R = radius(tier);
            this.wallH = 7 + tier / 2;
            this.thick = tier < 5 ? 1 : tier < 10 ? 2 : 3;
            this.breathable = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
            // a level close to the typical ground of the site, not to one odd column
            int[] hs = new int[25];
            int n = 0;
            for (int i = -2; i <= 2; i++) for (int k = -2; k <= 2; k++)
                hs[n++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + i * R / 2, cz + k * R / 2);
            Arrays.sort(hs);
            this.y0 = hs[12];
        }

        public int size() { return count; }
        public boolean done() { return cursor >= count; }
        public double progress() { return count == 0 ? 1 : cursor / (double) count; }

        private void set(int x, int y, int z, BlockState s) {
            if (y <= level.getMinY() || y >= level.getMaxY()) return;
            if (count == pos.length) { pos = Arrays.copyOf(pos, count * 2); states = Arrays.copyOf(states, count * 2); }
            pos[count] = BlockPos.asLong(x, y, z);
            states[count++] = s;
        }

        private void fill(int x0, int y0_, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0_, y1); y <= Math.max(y0_, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
        }

        /** A box with walls of {@code wall}, hollow inside, floors every 5 blocks, a flat roof. */
        private void hall(int x0, int z0, int x1, int z1, int h, BlockState wall, BlockState roof) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                boolean shell = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 0; y < h; y++) {
                    BlockState b;
                    if (shell) {
                        boolean window = y % 5 == 2 && ((x + z) % 3 == 0) && !(x == x0 && z == z0) && !(x == x1 && z == z1);
                        b = window ? st.glass() : (y % 5 == 0 && y > 0 ? st.trim() : mix(wall));
                        if ((x == x0 || x == x1) && (z == z0 || z == z1)) b = st.pillar();
                    } else b = y % 5 == 0 && y > 0 ? st.floor() : air;
                    set(cx + x, y0 + y, cz + z, b);
                }
                set(cx + x, y0 + h, cz + z, shell ? st.trim() : roof);
            }
        }

        private BlockState mix(BlockState wall) {
            return wall == st.wall() && r.nextFloat() < 0.18f ? st.wall2() : wall;
        }

        // ---- the plan -------------------------------------------------------------------------------------------

        void design() {
            platform();
            if (tier >= 5) moat();
            outerWall();
            towers();
            if (tier >= 10) innerWall();
            courtyard();
            keep();
            vault();
            if (tier >= 8) bunker();
        }

        /** Level the site: clear what stands on it, fill below, pave with a grid of trim. */
        private void platform() {
            int E = R + (tier >= 5 ? 8 : 3);
            for (int dx = -E; dx <= E; dx++) for (int dz = -E; dz <= E; dz++) {
                int x = cx + dx, z = cz + dz;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = y0; y <= Math.max(h, y0) + 2; y++) if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) set(x, y, z, air);
                for (int y = y0 - 1; y >= Math.max(level.getMinY() + 1, Math.min(h, y0) - 20) && y >= h - 1; y--) set(x, y, z, st.trim());
                boolean grid = Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0;
                set(x, y0 - 1, z, grid ? st.trim() : st.floor());
            }
        }

        private void moat() {
            BlockState fluid = st.moat(breathable);
            for (int dx = -R - 7; dx <= R + 7; dx++) for (int dz = -R - 7; dz <= R + 7; dz++) {
                int e = Math.max(Math.abs(dx), Math.abs(dz));
                if (e < R + 3 || e > R + 6) continue;
                if (Math.abs(dx) <= 2 || Math.abs(dz) <= 2) continue;                    // causeways at the gates
                for (int y = -4; y < 0; y++) set(cx + dx, y0 + y, cz + dz, y < -1 ? fluid : (fluid.isAir() ? air : fluid));
                set(cx + dx, y0 - 5, cz + dz, st.trim());
            }
        }

        private boolean gate(int dx, int dz) {
            boolean south = dz >= R - thick && Math.abs(dx) <= 1;
            boolean north = tier >= 8 && dz <= -R + thick && Math.abs(dx) <= 1;
            boolean sides = tier >= 13 && Math.abs(dz) <= 1 && Math.abs(dx) >= R - thick;
            return south || north || sides;
        }

        private void outerWall() {
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                int e = Math.max(Math.abs(dx), Math.abs(dz));
                if (e < R - thick + 1) continue;
                boolean outerFace = e == R;
                for (int y = 0; y < wallH; y++) {
                    if (gate(dx, dz) && y < 5) { set(cx + dx, y0 + y, cz + dz, air); continue; }
                    BlockState b = y == wallH / 2 ? st.accent() : mix(st.wall());
                    if (outerFace && y == wallH - 3 && Math.floorMod(dx + dz, 4) == 0) b = st.glass();           // firing slits
                    set(cx + dx, y0 + y, cz + dz, b);
                }
                if (outerFace && Math.floorMod(dx + dz, 2) == 0) set(cx + dx, y0 + wallH, cz + dz, st.trim()); // crenels
                if (e == R - thick + 1 && Math.floorMod(dx + dz, 8) == 0) set(cx + dx, y0 + wallH - 2, cz + dz, st.light());
            }
            // walkway inside the wall
            for (int dx = -R + thick; dx <= R - thick; dx++) for (int dz = -R + thick; dz <= R - thick; dz++) {
                int e = Math.max(Math.abs(dx), Math.abs(dz));
                if (e != R - thick) continue;
                set(cx + dx, y0 + wallH - 1, cz + dz, st.floor());
                if (Math.floorMod(dx * 7 + dz * 3, 11) == 0) guardSpots.add(new Vec3(cx + dx + 0.5, y0 + wallH, cz + dz + 0.5));
            }
            // gatehouses
            gatehouse(0, R, Direction.SOUTH);
            if (tier >= 8) gatehouse(0, -R, Direction.NORTH);
            if (tier >= 13) { gatehouse(R, 0, Direction.EAST); gatehouse(-R, 0, Direction.WEST); }
        }

        private void gatehouse(int gx, int gz, Direction d) {
            boolean ns = d.getAxis() == Direction.Axis.Z;
            for (int side = -1; side <= 1; side += 2) {
                int tx = ns ? gx + side * 4 : gx, tz = ns ? gz : gz + side * 4;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    for (int y = 0; y < wallH + 4; y++) {
                        boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        set(cx + tx + dx, y0 + y, cz + tz + dz, shell ? (y == wallH + 3 ? st.trim() : mix(st.wall())) : air);
                    }
                set(cx + tx, y0 + wallH + 4, cz + tz, st.light());
                guardSpots.add(new Vec3(cx + tx + 0.5, y0 + wallH + 4, cz + tz + 0.5));
            }
            // portcullis frame over the opening
            for (int k = -2; k <= 2; k++) set(cx + (ns ? gx + k : gx), y0 + 5, cz + (ns ? gz : gz + k), st.accent());
        }

        private void roundTower(int tx, int tz, int rad, int h) {
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rad + 0.4) continue;
                boolean shell = d > rad - 0.9;
                for (int y = 0; y <= h; y++) {
                    BlockState b = shell ? (y % 6 == 3 && (dx == 0 || dz == 0) ? st.glass() : mix(st.wall())) : (y % 6 == 0 && y > 0 ? st.floor() : air);
                    set(cx + tx + dx, y0 + y, cz + tz + dz, b);
                }
                if (shell && Math.floorMod(dx + dz, 2) == 0) set(cx + tx + dx, y0 + h + 1, cz + tz + dz, st.trim());
                if (!shell) set(cx + tx + dx, y0 + h, cz + tz + dz, st.floor());
            }
            for (int y = 0; y < h; y++)
                set(cx + tx, y0 + y, cz + tz - rad + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            set(cx + tx, y0 + h + 2, cz + tz, st.light());
            fabSpots.add(new BlockPos(cx + tx + 1, y0 + h + 1, cz + tz + 1));
            guardSpots.add(new Vec3(cx + tx - 1.5, y0 + h + 1, cz + tz - 1.5));
            // open to the courtyard at the bottom
            int ox = -Integer.signum(tx) * rad, oz = -Integer.signum(tz) * rad;
            for (int y = 0; y < 3; y++) { set(cx + tx + ox, y0 + y, cz + tz, air); set(cx + tx, y0 + y, cz + tz + oz, air); }
        }

        private void towers() {
            int rad = 3 + tier / 4, h = wallH + 6 + tier / 3;
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) roundTower(sx * R, sz * R, rad, h);
            if (R > 40) {                                                              // towers along the walls
                int step = 30;
                for (int k = -R + step; k <= R - step; k += step) {
                    if (Math.abs(k) < 8) continue;
                    roundTower(k, -R, rad - 1, h - 2); roundTower(k, R, rad - 1, h - 2);
                    roundTower(-R, k, rad - 1, h - 2); roundTower(R, k, rad - 1, h - 2);
                }
            }
        }

        private void innerWall() {
            int I = (int) (R * 0.5);
            int h = wallH + 3;
            for (int dx = -I; dx <= I; dx++) for (int dz = -I; dz <= I; dz++) {
                int e = Math.max(Math.abs(dx), Math.abs(dz));
                if (e < I - 1) continue;
                boolean gap = Math.abs(dx) <= 1 && dz >= I - 1;
                for (int y = 0; y < h; y++) set(cx + dx, y0 + y, cz + dz, gap && y < 5 ? air : (y == h - 1 ? st.trim() : mix(st.wall())));
                if (e == I && Math.floorMod(dx + dz, 2) == 0) set(cx + dx, y0 + h, cz + dz, st.trim());
            }
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) roundTower(sx * I, sz * I, 3, h + 5);
        }

        /** The ground between the walls: plots with buildings of several kinds, lanes between them. */
        private void courtyard() {
            int K = keepHalf() + 3;
            int inner = tier >= 10 ? (int) (R * 0.5) + 3 : K;
            int plot = 14, limit = R - thick - 5;
            List<int[]> plots = new ArrayList<>();
            for (int px = -limit; px + plot <= limit; px += plot + 3)
                for (int pz = -limit; pz + plot <= limit; pz += plot + 3) {
                    int mx = px + plot / 2, mz = pz + plot / 2;
                    if (Math.max(Math.abs(px), Math.abs(px + plot)) < inner && Math.max(Math.abs(pz), Math.abs(pz + plot)) < inner) continue;
                    if (Math.abs(mx) < inner + plot / 2 && Math.abs(mz) < inner + plot / 2) continue;
                    if (Math.abs(mx) <= 4 && pz + plot > 0) continue;                    // keep the south lane open
                    plots.add(new int[]{px, pz});
                }
            int built = 0, want = Math.min(plots.size(), 2 + tier);
            java.util.Collections.shuffle(plots, new java.util.Random(r.nextLong()));
            for (int[] p : plots) {
                if (built >= want) break;
                int kind = (built + r.nextInt(6)) % 6;
                if (built == 0) kind = 1;                                              // always at least one factory
                switch (kind) {
                    case 0 -> barracks(p[0], p[1], plot);
                    case 1 -> factory(p[0], p[1], plot);
                    case 2 -> radar(p[0], p[1], plot);
                    case 3 -> hangar(p[0], p[1], plot);
                    case 4 -> reactor(p[0], p[1], plot);
                    default -> depot(p[0], p[1], plot);
                }
                built++;
            }
            // lamp posts along the main lanes
            for (int k = -R + thick + 3; k <= R - thick - 3; k += 7) {
                for (int[] lp : new int[][]{{3, k}, {-3, k}}) {
                    if (Math.abs(lp[1]) < keepHalf() + 2) continue;
                    set(cx + lp[0], y0, cz + lp[1], st.pillar());
                    set(cx + lp[0], y0 + 1, cz + lp[1], Blocks.IRON_BARS.defaultBlockState());
                    set(cx + lp[0], y0 + 2, cz + lp[1], st.light());
                }
            }
        }

        private void barracks(int px, int pz, int s) {
            hall(px, pz + 3, px + s, pz + s - 3, 6, st.wall(), st.roof());
            set(cx + px + s / 2, y0, cz + pz + s - 3, air); set(cx + px + s / 2, y0 + 1, cz + pz + s - 3, air);
            for (int i = 0; i < 3; i++) guardSpots.add(new Vec3(cx + px + 2 + i * 4 + 0.5, y0, cz + pz + s / 2 + 0.5));
        }

        private void factory(int px, int pz, int s) {
            hall(px, pz, px + s, pz + s, 9, st.wall(), st.roof());
            for (int y = 0; y < 3; y++) for (int k = -1; k <= 1; k++) set(cx + px + s / 2 + k, y0 + y, cz + pz + s, air);   // big door
            for (int sx : new int[]{px + 3, px + s - 3}) {                            // smoke stacks
                for (int y = 9; y < 16; y++) set(cx + sx, y0 + y, cz + pz + 3, y == 15 ? st.accent() : st.pillar());
            }
            fabSpots.add(new BlockPos(cx + px + s / 2, y0, cz + pz + s / 2));
            fabSpots.add(new BlockPos(cx + px + 3, y0 + 5 + 1, cz + pz + s / 2));
            for (int i = 0; i < 2; i++) guardSpots.add(new Vec3(cx + px + 3 + i * 6 + 0.5, y0, cz + pz + 3.5));
        }

        private void radar(int px, int pz, int s) {
            int mx = px + s / 2, mz = pz + s / 2;
            hall(px + 2, pz + 2, px + s - 2, pz + s - 2, 5, st.wall(), st.roof());
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int dy = 0; dy <= 5; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.4);
                if (d > 5.2 || d < 4.2) continue;
                set(cx + mx + dx, y0 + 6 + dy, cz + mz + dz, (dx + dz + dy) % 3 == 0 ? st.glass() : st.accent());
            }
            set(cx + mx, y0 + 12, cz + mz, st.light());
            guardSpots.add(new Vec3(cx + mx + 0.5, y0 + 6, cz + mz + 0.5));
        }

        private void hangar(int px, int pz, int s) {
            for (int x = px; x <= px + s; x++) for (int z = pz; z <= pz + s; z++) {
                double t = (x - px) / (double) s * Math.PI;
                int top = (int) Math.round(Math.sin(t) * 7);
                boolean end = z == pz;
                for (int y = 0; y <= top; y++) {
                    boolean shell = y == top || end;
                    if (end && Math.abs(x - (px + s / 2)) <= 3 && y < 5) continue;   // the hangar door
                    set(cx + x, y0 + y, cz + z, shell ? (z % 3 == 0 ? st.trim() : st.roof()) : air);
                }
            }
            for (int i = 0; i < 2; i++) guardSpots.add(new Vec3(cx + px + s / 2 + 0.5, y0 + 1, cz + pz + 4 + i * 4 + 0.5));
        }

        private void reactor(int px, int pz, int s) {
            int mx = px + s / 2, mz = pz + s / 2;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 6.4) continue;
                for (int y = 0; y < 12; y++) {
                    boolean shell = d > 5.4;
                    BlockState b = shell ? (y % 4 == 1 ? st.accent() : st.wall()) : (d < 1.5 && y > 1 && y < 10 ? st.light() : air);
                    set(cx + mx + dx, y0 + y, cz + mz + dz, b);
                }
                set(cx + mx + dx, y0 + 12, cz + mz + dz, d > 5.4 ? st.trim() : st.glass());
            }
            for (int y = 0; y < 3; y++) set(cx + mx, y0 + y, cz + mz + 6, air);
            fabSpots.add(new BlockPos(cx + mx + 3, y0, cz + mz));
        }

        private void depot(int px, int pz, int s) {
            for (int i = 0; i < 9; i++) {
                int x = px + 1 + (i % 3) * 4, z = pz + 1 + (i / 3) * 4, h = 1 + r.nextInt(3);
                fill(cx + x, y0, cz + z, cx + x + 2, y0 + h - 1, cz + z + 2, i % 2 == 0 ? st.trim() : st.wall2());
            }
            chestSpots.add(new BlockPos(cx + px + s / 2, y0, cz + pz + s / 2));
            guardSpots.add(new Vec3(cx + px + 1.5, y0, cz + pz + s - 1.5));
        }

        int keepHalf() { return 6 + tier / 2; }

        /** The keep: a tall hall with floors, stepped for the largest fortresses, a spire on top. */
        private void keep() {
            int K = keepHalf();
            int HK = wallH + 10 + tier;
            if (tier >= 13) {
                hall(-K, -K, K, K, HK / 2, st.wall(), st.floor());
                hall(-K + 3, -K + 3, K - 3, K - 3, HK, st.wall(), st.floor());
                hall(-K + 6, -K + 6, K - 6, K - 6, HK + HK / 3, st.wall(), st.roof());
                HK = HK + HK / 3;
            } else hall(-K, -K, K, K, HK, st.wall(), st.roof());
            for (int y = 0; y < 3; y++) for (int k = -1; k <= 1; k++) set(cx + k, y0 + y, cz + K, air);     // door
            int lx = -K + 1, lz = -K + 1;
            if (tier >= 13) { lx = -K + 7; lz = -K + 7; }
            for (int y = 0; y <= HK; y++) set(cx + lx, y0 + y, cz + lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            int spire = 6 + tier / 2;
            for (int y = 1; y <= spire; y++) set(cx, y0 + HK + y, cz, y % 3 == 0 ? st.accent() : st.pillar());
            set(cx, y0 + HK + spire + 1, cz, st.light());
            keepTop = new BlockPos(cx, y0 + HK + 1, cz);
            for (int f = 0; f < HK && fabSpots.size() < 40; f += 5) {
                if (f % 10 == 0) fabSpots.add(new BlockPos(cx + 2, y0 + f + (f == 0 ? 0 : 1), cz + 1));
                guardSpots.add(new Vec3(cx - 2 + 0.5, y0 + f + 1, cz - 2 + 0.5));
            }
            bossSpots.add(new Vec3(cx + 0.5, y0 + 0.1, cz - K - 5 + 0.5));
            bossSpots.add(new Vec3(cx + K + 5 + 0.5, y0 + 0.1, cz + 0.5));
            bossSpots.add(new Vec3(cx - K - 5 + 0.5, y0 + 0.1, cz + 0.5));
        }

        private void vault() {
            int vy = y0 - 7;
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 0; y <= 5; y++) {
                boolean shell = Math.abs(dx) == 4 || Math.abs(dz) == 4 || y == 0 || y == 5;
                set(cx + dx, vy + y, cz + dz, shell ? st.trim() : air);
            }
            set(cx, vy + 4, cz, st.light());
            for (int y = vy + 1; y < y0; y++) set(cx - 3, y, cz - 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            set(cx - 3, y0 - 1, cz - 3, air);
            int chests = 2 + tier / 3;
            for (int i = 0; i < chests; i++) chestSpots.add(new BlockPos(cx + 3, vy + 1, cz - 3 + (i % 7)));
        }

        /** An underground level: a cross of corridors from the keep to four fabricator halls. */
        private void bunker() {
            int by = y0 - 16, L = Math.min(R - 10, 24 + tier);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int i = 4; i <= L; i++) {
                    int x = cx + d[0] * i, z = cz + d[1] * i;
                    for (int w = -2; w <= 2; w++) for (int y = 0; y <= 4; y++) {
                        int xx = x + (d[1] != 0 ? w : 0), zz = z + (d[0] != 0 ? w : 0);
                        boolean shell = Math.abs(w) == 2 || y == 0 || y == 4;
                        set(xx, by + y, zz, shell ? st.trim() : air);
                    }
                    if (i % 6 == 0) set(x, by + 3, z, st.light());
                }
                int hx = cx + d[0] * (L + 4), hz = cz + d[1] * (L + 4);
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int y = 0; y <= 6; y++) {
                    boolean shell = Math.abs(dx) == 5 || Math.abs(dz) == 5 || y == 0 || y == 6;
                    set(hx + dx, by + y, hz + dz, shell ? st.trim() : air);
                }
                set(hx, by + 5, hz, st.light());
                fabSpots.add(new BlockPos(hx, by + 1, hz));
                guardSpots.add(new Vec3(hx + 2.5, by + 1, hz + 2.5));
            }
            // the shaft down from the keep
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = by + 1; y < y0 - 7; y++) set(cx + 5 + dx, y, cz + 5 + dz, air);
            for (int y = by + 1; y < y0; y++) set(cx + 5, y, cz + 4, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            for (int y = by + 1; y < by + 4; y++) for (int k = 2; k <= 5; k++) set(cx + k, y, cz + 5, air);
        }

        // ---- placing and finishing ------------------------------------------------------------------------------

        /** Places up to {@code budget} blocks; true once everything is in place. */
        public boolean step(int budget) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int end = Math.min(count, cursor + budget);
            for (; cursor < end; cursor++) {
                p.set(pos[cursor]);
                if (level.getBlockState(p) != states[cursor]) level.setBlock(p, states[cursor], FLAGS);
            }
            return cursor >= count;
        }

        /** Fabricators, the loot, the garrison and the wardens. */
        public Result finish() {
            pos = null; states = null;
            int want = Math.min(fabSpots.size(), 2 + tier / 2 + (tier >= 13 ? 2 : 0));
            List<BlockPos> spots = new ArrayList<>(fabSpots);
            List<BlockPos> placed = new ArrayList<>();
            // spread them: take every n-th spot so that factories, towers, keep and bunker all get some
            double stride = spots.size() / (double) Math.max(1, want);
            for (int i = 0; i < want; i++) {
                BlockPos at = spots.get((int) Math.min(spots.size() - 1, Math.floor(i * stride)));
                level.setBlock(at, FortressStyle.fabricator(), 3);
                if (level.getBlockEntity(at) instanceof FabricatorBlockEntity fb) fb.setup(tier);
                placed.add(at);
            }
            for (BlockPos c : chestSpots) {
                level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), 3);
                if (level.getBlockEntity(c) instanceof ChestBlockEntity chest) FortressLoot.fill(chest, tier, 4 + tier + r.nextInt(3), r);
            }
            List<String> pool = EnemyRoster.pool(tier);
            int guards = 5 + (int) Math.round(tier * 1.6);
            java.util.Collections.shuffle(guardSpots, new java.util.Random(r.nextLong()));
            for (int i = 0; i < guards && !guardSpots.isEmpty(); i++) {
                Vec3 g = guardSpots.get(i % guardSpots.size());
                garrison(level, pool.get(r.nextInt(pool.size())), g.x, g.y + 0.1, g.z, tier);
            }
            List<String> bosses = new ArrayList<>();
            List<String> kinds = new ArrayList<>();
            String main = EnemyRoster.boss(tier);
            if (main != null) kinds.add(main);
            if (tier >= 12) kinds.add(EnemyRoster.boss(tier - 3));
            if (tier >= 14) kinds.add(tier >= 15 ? "hive_carrier" : "fortress_overseer");
            for (int i = 0; i < kinds.size() && i < bossSpots.size(); i++) {
                if (kinds.get(i) == null) continue;
                Vec3 at = bossSpots.get(i);
                TechMob b = garrison(level, kinds.get(i), at.x, at.y, at.z, tier);
                if (b != null) bosses.add(b.getUUID().toString());
            }
            return new Result(placed, bosses, keepTop);
        }
    }

    private static final Identifier ELITE = Identifier.fromNamespaceAndPath("heliosphere", "fortress_elite");

    /** A machine stationed in a fortress: it does not despawn, and it is tougher the higher the difficulty. */
    static TechMob garrison(ServerLevel level, String id, double x, double y, double z, int tier) {
        TechMob m = TechCombat.spawn(level, id, x, y, z, null);
        if (m == null) return null;
        m.setPersistenceRequired();
        m.addTag("heliosphere.fortress");
        var health = m.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(ELITE) == null) {
            health.addPermanentModifier(new AttributeModifier(ELITE, 0.12 * tier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            m.setHealth(m.getMaxHealth());
        }
        return m;
    }
}
