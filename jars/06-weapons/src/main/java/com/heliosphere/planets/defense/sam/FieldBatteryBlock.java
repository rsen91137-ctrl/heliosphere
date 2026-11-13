package com.heliosphere.planets.defense.sam;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The air-defence battery pack: a field battery with a solar lid. Set it down near the vehicles and it powers every
 * radar and launcher within {@link FieldBatteryBlockEntity#RANGE} blocks, no cables needed. It leaves the assembly
 * station fully charged, keeps its charge when picked up, tops itself up in daylight and also takes grid power.
 */
public class FieldBatteryBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public FieldBatteryBlock(Properties p) {
        super(p.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(FieldBatteryBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FieldBatteryBlockEntity(pos, state); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FieldBatteryBlockEntity be) be.setEnergy(FieldBatteryItem.stored(stack));
    }

    /** Picked up by hand it keeps its charge (explosions and creative players get nothing). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && level.getBlockEntity(pos) instanceof FieldBatteryBlockEntity be) {
            ItemStack drop = FieldBatteryItem.withEnergy(new ItemStack(asItem()), be.energy());
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, drop);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != SamSites.BATTERY_ENTITY ? null : (l, p, s, be) -> ((FieldBatteryBlockEntity) be).tick((ServerLevel) l);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FieldBatteryBlockEntity be) be.status(player);
        return InteractionResult.SUCCESS;
    }
}
