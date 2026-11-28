package com.heliosphere.industry.habitat;

import com.heliosphere.core.machine.EnergyNode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The habitat field emitter. On its own, with no walls or panels, it holds a round field of radius 32 around itself
 * (a 64-block circle), from 8 blocks below it to 32 above: everything inside gets Earth's air, temperature and
 * gravity. It runs on power from the grid (a cable to any side): 48 FE a tick while the field is up, with a buffer of
 * 96 000 FE that carries it through half a minute of a weak grid. Out of power, the field drops.
 */
public class HabitatEmitterBlockEntity extends BlockEntity implements EnergyNode {
    public static final int RADIUS = 32, BELOW = 8, ABOVE = 32;
    public static final int USE = 48;
    public static final long CAPACITY = 96_000;

    private long energy;
    private HabitatRegistry.Region region;

    public HabitatEmitterBlockEntity(BlockPos pos, BlockState state) { super(Habitat.EMITTER_ENTITY, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    /**
     * The server spends the power and sets the lamp (the block state, which the clients see); both sides keep the
     * field while the lamp is on (the client needs it for the player's own movement).
     */
    void tick(Level level, BlockPos pos, BlockState state) {
        boolean server = level instanceof ServerLevel;
        boolean running;
        if (server) {
            running = energy >= USE;
            if (running) { energy -= USE; setChanged(); }
            if (state.getValue(HabitatEmitterBlock.ACTIVE) != running) level.setBlock(pos, state.setValue(HabitatEmitterBlock.ACTIVE, running), 3);
        } else running = state.getValue(HabitatEmitterBlock.ACTIVE);
        if (running) {
            if (region == null) region = circle(pos);
            HabitatRegistry.put(level, pos, region);
        } else HabitatRegistry.remove(level, pos);
        if (!server && running && level.random.nextInt(3) == 0) {                         // a faint shimmer in the air inside, and at the rim
            double a = level.random.nextDouble() * Math.PI * 2, r = level.random.nextInt(4) == 0 ? RADIUS : Math.sqrt(level.random.nextDouble()) * RADIUS;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + level.random.nextDouble() * 8,
                    pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.005, 0);
        }
    }

    /** The columns within the radius (packed as x,0,z). */
    private static HabitatRegistry.Region circle(BlockPos o) {
        LongOpenHashSet cells = new LongOpenHashSet();
        int r2 = RADIUS * RADIUS + RADIUS;                                                  // a round edge, not a diamond
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++)
            if (dx * dx + dz * dz <= r2) cells.add(BlockPos.asLong(o.getX() + dx, 0, o.getZ() + dz));
        return new HabitatRegistry.Region(cells, o.getX() - RADIUS, o.getY() - BELOW, o.getZ() - RADIUS, o.getX() + RADIUS, o.getY() + ABOVE, o.getZ() + RADIUS);
    }

    void status(Player p) {
        String charge = String.format("%,d/%,d", energy, CAPACITY);
        if (energy < USE) {
            p.displayClientMessage(Component.translatable("message.heliosphere.habitat_unpowered", USE, charge).withStyle(ChatFormatting.RED), false);
            return;
        }
        p.displayClientMessage(Component.translatable("message.heliosphere.habitat_on", RADIUS, BELOW, ABOVE, charge, USE).withStyle(ChatFormatting.AQUA), false);
    }

    /** For tests: columns inside the field (0 when it is down). */
    public int volume() { return region != null && getBlockState().getValue(HabitatEmitterBlock.ACTIVE) ? region.cells().size() : 0; }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy = Math.max(0, Math.min(CAPACITY, in.getLongOr("energy", 0)));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("energy", energy);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) HabitatRegistry.remove(level, worldPosition);
    }
}
