package com.heliosphere.foods.cuisine;

import com.heliosphere.core.machine.EnergyNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The planter's charge and grow lights: 8 FE/t while it holds energy, an extra random tick for the crop on it every 2 s. */
public class HydroponicPlanterBlockEntity extends BlockEntity implements EnergyNode {
    public static final long CAPACITY = 4000;
    /** FE per tick while powered. */
    public static final int USE = 8;
    /** Ticks between the extra growth ticks given to the crop on top. */
    public static final int BOOST_INTERVAL = 40;

    private long energy;

    public HydroponicPlanterBlockEntity(BlockPos pos, BlockState state) { super(Cuisine.PLANTER_BE, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    static void serverTick(Level level, BlockPos pos, BlockState state, HydroponicPlanterBlockEntity be) {
        boolean powered = be.energy > 0;
        if (powered) {
            be.energy = Math.max(0, be.energy - USE);
            be.setChanged();
            if ((level.getGameTime() + (pos.asLong() & 31)) % BOOST_INTERVAL == 0 && level instanceof ServerLevel server) boost(server, pos.above());
        }
        if (state.getValue(HydroponicPlanterBlock.LIT) != powered) level.setBlock(pos, state.setValue(HydroponicPlanterBlock.LIT, powered), 3);
    }

    /** One extra random tick for a growing crop at {@code cropPos} (it still needs its light, which the lamps give). */
    private static void boost(ServerLevel level, BlockPos cropPos) {
        BlockState crop = level.getBlockState(cropPos);
        if (crop.getBlock() instanceof CropBlock && crop.isRandomlyTicking()) crop.randomTick(level, cropPos, level.getRandom());
    }

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
}
