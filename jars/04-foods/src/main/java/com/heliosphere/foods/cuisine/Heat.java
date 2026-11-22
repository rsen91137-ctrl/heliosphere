package com.heliosphere.foods.cuisine;

import com.heliosphere.core.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a wok, pot or steamer can stand on to cook: a lit campfire or soul campfire, fire or soul fire, lava (also in
 * a cauldron), a magma block, a lit furnace, smoker or blast furnace, or a hot (powered) induction stove. Read from the
 * block states only, so the client can tell the player the same as the server decides.
 */
public final class Heat {
    private Heat() {}

    /** Translation key shown as the unmet condition of a heat machine. */
    public static final String NEEDS_HEAT = "condition.heliosphere.needs_heat";

    /** The machine condition of the heat machines: null (fine) when the block below is a heat source. */
    public static final MachineType.Condition CONDITION = (level, pos) -> isHeatSource(level, pos.below()) ? null : NEEDS_HEAT;

    /** Whether the block at {@code pos} heats whatever stands on it. */
    public static boolean isHeatSource(BlockGetter level, BlockPos pos) {
        if (level == null) return false;
        return isHeatSource(level.getBlockState(pos));
    }

    public static boolean isHeatSource(BlockState s) {
        if (s.getBlock() instanceof CampfireBlock) return s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT);
        if (s.is(BlockTags.FIRE) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) return true;
        if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.LAVA_CAULDRON)) return true;
        if (s.getFluidState().is(FluidTags.LAVA)) return true;
        if (s.getBlock() instanceof AbstractFurnaceBlock) return s.hasProperty(AbstractFurnaceBlock.LIT) && s.getValue(AbstractFurnaceBlock.LIT);
        return InductionStoveBlock.isHot(s);
    }
}
