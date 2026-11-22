package com.heliosphere.foods.cuisine;

import com.heliosphere.core.machine.EnergyNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The induction stove's charge: filled from the grid like any machine, spent only while a heat machine on top cooks. */
public class InductionStoveBlockEntity extends BlockEntity implements EnergyNode {
    public static final long CAPACITY = 4000;
    /** FE per tick while the wok, pot or steamer on top is cooking. */
    public static final int USE = 12;

    private long energy;

    public InductionStoveBlockEntity(BlockPos pos, BlockState state) { super(Cuisine.STOVE_BE, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    static void serverTick(Level level, BlockPos pos, BlockState state, InductionStoveBlockEntity be) {
        if (be.energy > 0 && CookwareBlock.cookingWithHeat(level.getBlockState(pos.above()))) {
            be.energy = Math.max(0, be.energy - USE);
            be.setChanged();
        }
        boolean hot = be.energy > 0;
        if (state.getValue(InductionStoveBlock.LIT) != hot) level.setBlock(pos, state.setValue(InductionStoveBlock.LIT, hot), 3);
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
