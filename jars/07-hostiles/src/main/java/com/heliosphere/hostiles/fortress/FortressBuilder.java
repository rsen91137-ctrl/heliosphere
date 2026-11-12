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

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a machine fortress, larger and better defended the higher its difficulty (1-15): a levelled platform,
 * a crenellated curtain wall with a gate, four corner towers, a keep of several floors with an antenna, and a
 * vault beneath the keep. Robot fabricators sit in the keep and the towers; the garrison and, from difficulty 6,
 * a boss are placed when it is built.
 */
public final class FortressBuilder {
    private FortressBuilder() {}

    private static final int FLAGS = 2 | 16;

    public record Result(List<BlockPos> fabricators, String boss, BlockPos keepTop) {}

    public static Result build(ServerLevel level, int cx, int cz, int tier, FortressStyle st, RandomSource r) {
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        int R = 9 + tier, H = 5 + tier / 3, K = 4 + tier / 4, HK = H + 4 + tier / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        // platform: level the ground, fill under, clear above
        for (int dx = -R - 2; dx <= R + 2; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) {
            int x = cx + dx, z = cz + dz;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int y = Math.max(h, y0); y <= y0 + HK + 8; y++) set(level, p.set(x, y, z), Blocks.AIR.defaultBlockState());
            for (int y = y0 - 1; y >= Math.max(level.getMinY() + 1, Math.min(h, y0) - 16) && y >= h - 1; y--) set(level, p.set(x, y, z), st.trim());
            boolean grid = Math.floorMod(dx, 5) == 0 || Math.floorMod(dz, 5) == 0;
            set(level, p.set(x, y0 - 1, z), grid ? st.trim() : st.floor());
        }
        // curtain wall
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            if (edge < R - 1) continue;
            boolean gate = dz == R || dz == R - 1 ? Math.abs(dx) <= 1 : false;
            for (int y = 0; y < H; y++) {
                if (gate && y < 4) { set(level, p.set(cx + dx, y0 + y, cz + dz), Blocks.AIR.defaultBlockState()); continue; }
                BlockState b = y == H / 2 ? st.accent() : st.wall();
                if (edge == R - 1 && y == 2 && Math.floorMod(dx + dz, 6) == 0) b = st.light();
                set(level, p.set(cx + dx, y0 + y, cz + dz), b);
            }
            if (edge == R && Math.floorMod(dx + dz, 2) == 0) set(level, p.set(cx + dx, y0 + H, cz + dz), st.trim());
        }
        for (int y = 0; y < 4; y++) for (int sx = -2; sx <= 2; sx += 4) set(level, p.set(cx + sx, y0 + y, cz + R), st.accent());   // gate frame
        // corner towers
        List<BlockPos> fabs = new ArrayList<>();
        int[][] corners = {{-R, -R}, {R, -R}, {-R, R}, {R, R}};
        for (int[] c : corners) {
            int tx = cx + c[0], tz = cz + c[1], th = H + 5;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int y = 0; y <= th; y++) {
                    BlockState b = shell ? (y % 4 == 3 && (dx == 0 || dz == 0) ? st.glass() : st.wall()) : (y == H ? st.floor() : Blocks.AIR.defaultBlockState());
                    set(level, p.set(tx + dx, y0 + y, tz + dz), b);
                }
                if (shell && Math.floorMod(dx + dz, 2) == 0) set(level, p.set(tx + dx, y0 + th + 1, tz + dz), st.trim());
                if (!shell) set(level, p.set(tx + dx, y0 + th, tz + dz), st.trim());
            }
            for (int y = 0; y < H; y++) set(level, p.set(tx, y0 + y, tz + (c[1] < 0 ? 2 : -2)), Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, c[1] < 0 ? Direction.NORTH : Direction.SOUTH));
            // open the tower to the courtyard
            for (int y = 0; y < 3; y++) set(level, p.set(tx - Integer.signum(c[0]) * 3, y0 + y, tz), Blocks.AIR.defaultBlockState());
            set(level, p.set(tx, y0 + th + 1, tz), st.light());
            fabs.add(new BlockPos(tx, y0 + H + 1, tz));
        }
        // keep
        for (int dx = -K; dx <= K; dx++) for (int dz = -K; dz <= K; dz++) {
            boolean shell = Math.abs(dx) == K || Math.abs(dz) == K;
            for (int y = 0; y <= HK; y++) {
                BlockState b;
                if (shell) b = y % 5 == 2 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) ? st.glass() : (y % 5 == 0 ? st.accent() : st.wall());
                else b = y % 5 == 0 && y > 0 ? st.floor() : Blocks.AIR.defaultBlockState();
                set(level, p.set(cx + dx, y0 + y, cz + dz), b);
            }
            set(level, p.set(cx + dx, y0 + HK + 1, cz + dz), shell ? st.trim() : st.wall());
        }
        for (int y = 0; y < 3; y++) set(level, p.set(cx, y0 + y, cz + K), Blocks.AIR.defaultBlockState());        // door
        for (int y = 0; y <= HK; y++) {                                                                            // ladder up through the floors
            set(level, p.set(cx - K + 1, y0 + y, cz - K + 1), Blocks.AIR.defaultBlockState());
            set(level, p.set(cx - K + 1, y0 + y, cz - K + 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int y = 1; y <= 4 + tier / 3; y++) set(level, p.set(cx, y0 + HK + 1 + y, cz), y % 2 == 0 ? st.accent() : Blocks.IRON_BARS.defaultBlockState());
        set(level, p.set(cx, y0 + HK + 6 + tier / 3, cz), st.light());
        fabs.add(new BlockPos(cx + 2, y0 + 1, cz));
        for (int f = 5; f < HK && fabs.size() < 7; f += 5) fabs.add(new BlockPos(cx + (f % 2 == 0 ? 2 : -2), y0 + f + 1, cz + 1));
        // vault under the keep
        int vy = y0 - 6;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = 0; y <= 4; y++) {
            boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3 || y == 0 || y == 4;
            set(level, p.set(cx + dx, vy + y, cz + dz), shell ? st.trim() : Blocks.AIR.defaultBlockState());
        }
        set(level, p.set(cx, vy + 3, cz), st.light());
        for (int y = vy + 1; y < y0; y++) {
            set(level, p.set(cx - 2, y, cz - 2), Blocks.AIR.defaultBlockState());
            set(level, p.set(cx - 2, y, cz - 2), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        set(level, p.set(cx - 2, y0 - 1, cz - 2), Blocks.AIR.defaultBlockState());
        int chests = 1 + tier / 5;
        for (int i = 0; i < chests; i++) {
            BlockPos at = new BlockPos(cx + 2, vy + 1, cz - 2 + i * 1);
            level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), 3);
            if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) FortressLoot.fill(chest, tier, 4 + tier + r.nextInt(3), r);
        }

        // fabricators: one plus one for every three difficulty, in the keep first
        int count = Math.min(fabs.size(), 1 + tier / 3);
        List<BlockPos> placed = new ArrayList<>();
        List<BlockPos> order = new ArrayList<>(fabs.subList(4, fabs.size()));
        order.addAll(fabs.subList(0, 4));
        for (int i = 0; i < count; i++) {
            BlockPos at = order.get(i);
            level.setBlock(at, FortressStyle.fabricator(), 3);
            if (level.getBlockEntity(at) instanceof FabricatorBlockEntity fb) fb.setup(tier);
            placed.add(at);
        }

        // garrison and boss
        List<String> pool = EnemyRoster.pool(tier);
        for (int i = 0; i < 3 + tier; i++) {
            String id = pool.get(r.nextInt(pool.size()));
            double x = cx + r.nextInt(2 * R - 6) - R + 3, z = cz + r.nextInt(2 * R - 6) - R + 3;
            if (Math.abs(x - cx) <= K + 1 && Math.abs(z - cz) <= K + 1) x += K + 3;
            garrison(level, id, x, y0 + 0.1, z, tier);
        }
        String boss = EnemyRoster.boss(tier);
        String bossId = null;
        if (boss != null) {
            TechMob b = garrison(level, boss, cx + 0.5, y0 + 0.1, cz - K - 4, tier);
            if (b != null) bossId = b.getUUID().toString();
        }
        return new Result(placed, bossId, new BlockPos(cx, y0 + HK + 2, cz));
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) { level.setBlock(p, s, FLAGS); }

    private static final Identifier ELITE = Identifier.fromNamespaceAndPath("heliosphere", "fortress_elite");

    /** A machine stationed in a fortress: it does not despawn, and it is tougher the higher the difficulty. */
    static TechMob garrison(ServerLevel level, String id, double x, double y, double z, int tier) {
        TechMob m = TechCombat.spawn(level, id, x, y, z, null);
        if (m == null) return null;
        m.setPersistenceRequired();
        var health = m.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(ELITE) == null) {
            health.addPermanentModifier(new AttributeModifier(ELITE, 0.08 * tier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            m.setHealth(m.getMaxHealth());
        }
        return m;
    }
}
