package com.heliosphere.industry.habitat;

import com.heliosphere.core.machine.EnergyNode;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The habitat field emitter: every two seconds it fills the space around it, block by block through everything you
 * can walk through, until it meets walls on every side. If that space is closed (at most 32768 blocks, within 40
 * blocks of the emitter) and the emitter has power, the whole space gets Earth's air, temperature and gravity.
 * Anything solid makes a wall: the force-field panels, glass, stone, a closed door. An open door, a missing block or
 * a gap under a fence lets the air out, and the emitter says where. Power comes in by cable or from a generator or
 * battery next to it: 30 FE/t plus 1 FE/t for every 400 blocks of space.
 */
public class HabitatEmitterBlockEntity extends BlockEntity implements EnergyNode {
    public static final long CAPACITY = 500_000;
    public static final int MAX_CELLS = 32768, MAX_REACH = 40;

    private long energy;
    private boolean creative;
    private int volume = -1;                    // -1 not scanned yet; 0 leaking
    private BlockPos leak;
    private HabitatRegistry.Region region;

    public HabitatEmitterBlockEntity(BlockPos pos, BlockState state) { super(Habitat.EMITTER_ENTITY, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    void setCreative(boolean on) { creative = on; if (on) energy = CAPACITY; setChanged(); }

    public int cost() { return volume <= 0 ? 0 : 30 + volume / 400; }

    /** Server and client: rescans every two seconds; the server also pays for it and sets the lamp. */
    void tick(Level level, BlockPos pos, BlockState state) {
        boolean server = level instanceof ServerLevel;
        if (server && creative) energy = CAPACITY;
        if ((level.getGameTime() + (pos.asLong() & 31)) % 40 == 0 || volume < 0) scan(level, pos);
        boolean sealed = region != null;
        boolean running;
        if (server) {
            running = sealed && energy >= cost();
            if (running && !creative) { energy -= cost(); setChanged(); }
            if (state.getValue(HabitatEmitterBlock.ACTIVE) != running) level.setBlock(pos, state.setValue(HabitatEmitterBlock.ACTIVE, running), 3);
        } else running = sealed && state.getValue(HabitatEmitterBlock.ACTIVE);
        if (running) HabitatRegistry.put(level, pos, region);
        else HabitatRegistry.remove(level, pos);
        if (!server && running && level.random.nextInt(6) == 0) {                         // a faint shimmer in the air inside
            long[] cells = region.cells().toLongArray();
            BlockPos at = BlockPos.of(cells[level.random.nextInt(cells.length)]);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, at.getX() + level.random.nextDouble(), at.getY() + level.random.nextDouble(),
                    at.getZ() + level.random.nextDouble(), 0, 0.005, 0);
        }
    }

    private static boolean open(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty();
    }

    /** Fills the space round the emitter; a closed space becomes the region, an open one is reported where it leaks. */
    private void scan(Level level, BlockPos origin) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (Direction d : Direction.values()) {
            BlockPos n = origin.relative(d);
            if (open(level, n) && seen.add(n.asLong())) queue.enqueue(n.asLong());
        }
        int minX = origin.getX(), minY = origin.getY(), minZ = origin.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        leak = null;
        while (!queue.isEmpty()) {
            long c = queue.dequeueLong();
            m.set(c);
            if (Math.abs(m.getX() - origin.getX()) > MAX_REACH || Math.abs(m.getY() - origin.getY()) > MAX_REACH || Math.abs(m.getZ() - origin.getZ()) > MAX_REACH
                    || seen.size() > MAX_CELLS || level.isOutsideBuildHeight(m) || !level.isLoaded(m)) {
                leak = m.immutable();
                break;
            }
            minX = Math.min(minX, m.getX()); minY = Math.min(minY, m.getY()); minZ = Math.min(minZ, m.getZ());
            maxX = Math.max(maxX, m.getX()); maxY = Math.max(maxY, m.getY()); maxZ = Math.max(maxZ, m.getZ());
            for (Direction d : Direction.values()) {
                long n = BlockPos.offset(c, d);
                if (seen.contains(n)) continue;
                BlockPos np = BlockPos.of(n);
                if (!open(level, np)) continue;
                seen.add(n);
                queue.enqueue(n);
            }
        }
        if (leak != null || seen.isEmpty()) { region = null; volume = 0; return; }
        region = new HabitatRegistry.Region(seen, minX, minY, minZ, maxX, maxY, maxZ);
        volume = seen.size();
    }

    void status(Player p) {
        if (region == null) {
            if (leak != null) p.displayClientMessage(Component.translatable("message.heliosphere.habitat_leak", leak.getX(), leak.getY(), leak.getZ()).withStyle(ChatFormatting.RED), false);
            else p.displayClientMessage(Component.translatable("message.heliosphere.habitat_nothing").withStyle(ChatFormatting.RED), false);
            return;
        }
        boolean on = getBlockState().getValue(HabitatEmitterBlock.ACTIVE);
        p.displayClientMessage(Component.translatable(on ? "message.heliosphere.habitat_on" : "message.heliosphere.habitat_no_power", volume, cost(), energy, CAPACITY)
                .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GOLD), false);
    }

    /** For tests: the size of the sealed space (0 open, -1 not scanned). */
    public int volume() { return volume; }
    public BlockPos leak() { return leak; }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) HabitatRegistry.remove(level, worldPosition);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy = in.getLongOr("energy", 0);
        creative = in.getBooleanOr("creative", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("energy", energy);
        out.putBoolean("creative", creative);
    }
}
