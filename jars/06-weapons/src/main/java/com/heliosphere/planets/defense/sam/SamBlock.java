package com.heliosphere.planets.defense.sam;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The master block of an air-defence vehicle. The vehicle is drawn by SamRenderer; on placement it fills its whole
 * footprint with hidden hull blocks, so it is solid to walk against, and breaking any part removes it all.
 */
public class SamBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final SamKind kind;
    static boolean removing;
    /** Chunks kept loaded around a vehicle (radius, in chunks): it keeps watch while its owner is far away. */
    static final int FORCE = 2;

    public SamBlock(Properties p, SamKind kind) {
        super(p.noOcclusion());
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new SamBlock(p, kind)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getOcclusionShape(BlockState s) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SamBlockEntity(pos, state); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection();
        Level level = ctx.getLevel();
        for (BlockPos p : kind.footprint(ctx.getClickedPos(), f)) {
            if (p.equals(ctx.getClickedPos())) continue;
            if (!level.getBlockState(p).canBeReplaced()) {
                if (ctx.getPlayer() != null && !level.isClientSide())
                    ctx.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.heliosphere.sam_no_room", kind.length, kind.width), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide()) return;
        for (BlockPos p : kind.footprint(pos, state.getValue(FACING)))
            if (!p.equals(pos)) level.setBlock(p, SamSites.HULL.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof SamBlockEntity be) {
            if (placer instanceof Player p) be.owner(p.getUUID());
            var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null) { var t = data.copyTag(); if (t.contains("sam_ammo")) be.unpack(t.getIntOr("sam_ammo", kind.magazine), t.getLongOr("sam_power", 0)); }
        }
        if (level instanceof ServerLevel sl)
            for (int dx = -FORCE; dx <= FORCE; dx++) for (int dz = -FORCE; dz <= FORCE; dz++) sl.setChunkForced((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, true);
        if (level instanceof ServerLevel sl) SamSites.markBuilt(sl);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        for (int dx = -FORCE; dx <= FORCE; dx++) for (int dz = -FORCE; dz <= FORCE; dz++) {     // let the chunks go unless another vehicle needs them
            int cx = (pos.getX() >> 4) + dx, cz = (pos.getZ() >> 4) + dz;
            if (!SamBlockEntity.anotherNear(level, pos, cx, cz)) level.setChunkForced(cx, cz, false);
        }
        if (removing) return;
        removing = true;
        try {
            for (BlockPos p : kind.footprint(pos, state.getValue(FACING)))
                if (!p.equals(pos) && level.getBlockState(p).is(SamSites.HULL)) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        } finally { removing = false; }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != SamSites.BLOCK_ENTITY ? null : (l, p, s, be) -> ((SamBlockEntity) be).tick((ServerLevel) l);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SamBlockEntity be) {
            be.status(player);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) SamOperation.panel(sp, be);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof SamWrenchItem ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;   // the wrench works on it
    }
}
