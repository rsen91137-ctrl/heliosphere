package com.heliosphere.foods.cuisine;

import com.heliosphere.core.machine.EnergyNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The planter's charge and grow lights: lit, it draws 8 FE/t and gives the crop on it an extra random tick every 2 s.
 * It lights up once it holds two seconds' worth ({@link #LIGHT_UP}) and stays lit until it is empty.
 */
public class HydroponicPlanterBlockEntity extends BlockEntity implements EnergyNode {
    public static final long CAPACITY = 4000;
    /** FE per tick while powered. */
    public static final int USE = 8;
    /** Ticks between the extra growth ticks given to the crop on top. */
    public static final int BOOST_INTERVAL = 300;   // an extra random tick every 15 s: about 4-5x the natural rate
    /**
     * Charge a dark planter needs before it lights up (two seconds' worth); a lit one stays lit until it is empty. On a
     * grid that cannot keep up, the lamps then switch every few seconds instead of every few ticks (each switch makes
     * the light engine relight the area around it).
     */
    public static final long LIGHT_UP = (long) USE * BOOST_INTERVAL;
    /**
     * How the lamps are switched: no shape update for the crop on top. It would check its light (8 needed) against the
     * light of before the switch, which the light engine has not updated yet, so a crop in a dark room broke off the
     * moment the power came back.
     */
    private static final int LAMP_FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;

    private long energy;

    public HydroponicPlanterBlockEntity(BlockPos pos, BlockState state) { super(Cuisine.PLANTER_BE, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    static void serverTick(Level level, BlockPos pos, BlockState state, HydroponicPlanterBlockEntity be) {
        boolean lit = state.getValue(HydroponicPlanterBlock.LIT);
        boolean powered = lit ? be.energy > 0 : be.energy >= LIGHT_UP;
        if (powered) {
            be.energy = Math.max(0, be.energy - USE);
            be.setChanged();
            if ((level.getGameTime() + (pos.asLong() & 31)) % BOOST_INTERVAL == 0 && level instanceof ServerLevel server) boost(server, pos.above());
        }
        if (lit != powered) level.setBlock(pos, state.setValue(HydroponicPlanterBlock.LIT, powered), LAMP_FLAGS);
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
