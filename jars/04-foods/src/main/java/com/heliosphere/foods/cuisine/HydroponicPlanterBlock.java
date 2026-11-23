package com.heliosphere.foods.cuisine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The hydroponic planter: a crop of the expansion, or wheat, carrots, potatoes or beetroot, is planted right on it and
 * grows as on watered farmland (the tray holds the nutrient solution), needing neither farmland nor water. Powered
 * (lit), it draws 8 FE/t, gives light 12 and gives the crop on it an extra growth tick every two seconds; see
 * {@link HydroponicPlanterBlockEntity} for when it lights up.
 */
public class HydroponicPlanterBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<HydroponicPlanterBlock> CODEC = simpleCodec(HydroponicPlanterBlock::new);
    /** Light while powered. */
    public static final int LIGHT = 12;

    public HydroponicPlanterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    /**
     * What the planters under a crop at {@code cropPos} add to its growth speed (CropBlock.getGrowthSpeed): as much as
     * watered farmland would, 3 for the one right under it and 3/4 for each planter beside that one; 0 when the crop is
     * not standing on a planter. This is before crowding: vanilla halves its whole sum when {@link #crowded} holds, and
     * the caller halves this as well (added after the halving, a field of one crop grew twice as fast on planters as
     * on watered farmland).
     */
    public static float growthBonus(BlockGetter level, BlockPos cropPos) {
        BlockPos below = cropPos.below();
        if (!(level.getBlockState(below).getBlock() instanceof HydroponicPlanterBlock)) return 0;
        float bonus = 3f;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && level.getBlockState(below.offset(dx, 0, dz)).getBlock() instanceof HydroponicPlanterBlock) bonus += 0.75f;
        }
        return bonus;
    }

    /**
     * Whether CropBlock.getGrowthSpeed halves the growth speed of {@code crop} at {@code pos} for crowding, by the same
     * test: the same crop beside it both east-west and north-south, or on a diagonal.
     */
    public static boolean crowded(Block crop, BlockGetter level, BlockPos pos) {
        BlockPos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        boolean rowX = level.getBlockState(w).is(crop) || level.getBlockState(e).is(crop);
        boolean rowZ = level.getBlockState(n).is(crop) || level.getBlockState(s).is(crop);
        if (rowX && rowZ) return true;
        return level.getBlockState(w.north()).is(crop) || level.getBlockState(e.north()).is(crop)
                || level.getBlockState(e.south()).is(crop) || level.getBlockState(w.south()).is(crop);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HydroponicPlanterBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, Cuisine.PLANTER_BE, HydroponicPlanterBlockEntity::serverTick);
    }

    /** Seeds are planted on it as usual; only the empty hand asks for the charge. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HydroponicPlanterBlockEntity be) Cuisine.showCharge(player, this, be);
        return InteractionResult.SUCCESS;
    }
}
