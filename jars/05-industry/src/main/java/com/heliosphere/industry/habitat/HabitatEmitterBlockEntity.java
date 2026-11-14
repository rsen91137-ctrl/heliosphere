package com.heliosphere.industry.habitat;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The habitat field emitter. Every two seconds it looks along the ground it stands on, outwards in every direction,
 * until it meets a wall: a ring of force-field panels, or any solid block, one block high is enough. If the ring is
 * closed (within 40 blocks of the emitter, at most 6400 columns inside), everything inside it, from 6 blocks below
 * the emitter to 30 above, gets Earth's air, temperature and gravity. No roof is needed and it needs no power. A gap
 * in the ring switches it off, and the emitter says where.
 */
public class HabitatEmitterBlockEntity extends BlockEntity {
    public static final int MAX_COLUMNS = 6400, MAX_REACH = 40, BELOW = 6, ABOVE = 30;

    private int area = -1;                      // columns inside: -1 not looked yet, 0 open
    private BlockPos leak;
    private HabitatRegistry.Region region;

    public HabitatEmitterBlockEntity(BlockPos pos, BlockState state) { super(Habitat.EMITTER_ENTITY, pos, state); }

    /** Server and client both look (the client needs it for the player's own movement); the server sets the lamp. */
    void tick(Level level, BlockPos pos, BlockState state) {
        boolean server = level instanceof ServerLevel;
        if ((level.getGameTime() + (pos.asLong() & 31)) % 40 == 0 || area < 0) scan(level, pos);
        boolean running = region != null;
        if (server && state.getValue(HabitatEmitterBlock.ACTIVE) != running) level.setBlock(pos, state.setValue(HabitatEmitterBlock.ACTIVE, running), 3);
        if (running) HabitatRegistry.put(level, pos, region);
        else HabitatRegistry.remove(level, pos);
        if (!server && running && level.random.nextInt(4) == 0) {                         // a faint shimmer in the air inside
            long[] cols = region.cells().toLongArray();
            BlockPos c = BlockPos.of(cols[level.random.nextInt(cols.length)]);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, c.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble() * 6,
                    c.getZ() + level.random.nextDouble(), 0, 0.005, 0);
        }
    }

    /** A wall of the ring: anything solid at the emitter's height. */
    private static boolean wall(Level level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** Fills the ground level round the emitter; a closed ring becomes the region, an open one is reported where it leaks. */
    private void scan(Level level, BlockPos origin) {
        int y = origin.getY();
        LongOpenHashSet inside = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long start = BlockPos.asLong(origin.getX(), 0, origin.getZ());
        inside.add(start);
        queue.enqueue(start);
        int minX = origin.getX(), minZ = origin.getZ(), maxX = minX, maxZ = minZ;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        leak = null;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            long c = queue.dequeueLong();
            int x = BlockPos.getX(c), z = BlockPos.getZ(c);
            if (Math.abs(x - origin.getX()) > MAX_REACH || Math.abs(z - origin.getZ()) > MAX_REACH || inside.size() > MAX_COLUMNS || !level.isLoaded(m.set(x, y, z))) {
                leak = new BlockPos(x, y, z);
                break;
            }
            minX = Math.min(minX, x); minZ = Math.min(minZ, z); maxX = Math.max(maxX, x); maxZ = Math.max(maxZ, z);
            for (int[] d : dirs) {
                long n = BlockPos.asLong(x + d[0], 0, z + d[1]);
                if (inside.contains(n)) continue;
                if (wall(level, m.set(x + d[0], y, z + d[1]))) continue;
                inside.add(n);
                queue.enqueue(n);
            }
        }
        if (leak != null) { region = null; area = 0; return; }
        // the ring itself belongs to the inside too (walking along it, leaning on it)
        LongOpenHashSet withWalls = new LongOpenHashSet(inside);
        for (long c : inside) for (int[] d : dirs) withWalls.add(BlockPos.asLong(BlockPos.getX(c) + d[0], 0, BlockPos.getZ(c) + d[1]));
        region = new HabitatRegistry.Region(withWalls, minX - 1, y - BELOW, minZ - 1, maxX + 1, y + ABOVE, maxZ + 1);
        area = inside.size();
    }

    void status(Player p) {
        if (region == null) {
            p.displayClientMessage(Component.translatable("message.heliosphere.habitat_leak", leak == null ? 0 : leak.getX(), leak == null ? 0 : leak.getY(),
                    leak == null ? 0 : leak.getZ()).withStyle(ChatFormatting.RED), false);
            return;
        }
        p.displayClientMessage(Component.translatable("message.heliosphere.habitat_on", area, BELOW, ABOVE).withStyle(ChatFormatting.AQUA), false);
    }

    /** For tests: columns inside the ring (0 open, -1 not looked yet). */
    public int volume() { return area; }
    public BlockPos leak() { return leak; }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) HabitatRegistry.remove(level, worldPosition);
    }
}
