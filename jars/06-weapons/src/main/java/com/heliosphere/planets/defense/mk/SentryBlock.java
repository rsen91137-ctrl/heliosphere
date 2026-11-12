package com.heliosphere.planets.defense.mk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A defence turret. The block is drawn by SentryRenderer; it never hides the faces of the blocks around it. */
public class SentryBlock extends BaseEntityBlock {
    public final TurretKind kind;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public SentryBlock(Properties p, TurretKind kind) { super(p.noOcclusion()); this.kind = kind; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new SentryBlock(p, kind)); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override protected VoxelShape getOcclusionShape(BlockState s) { return Shapes.empty(); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SentryBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != Sentries.BLOCK_ENTITY ? null : (l, p, s, be) -> ((SentryBlockEntity) be).tick((ServerLevel) l);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof SentryBlockEntity be && placer instanceof Player p) be.owner(p.getUUID());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int kit = Sentries.kitLevel(stack);
        if (kit <= 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SentryBlockEntity be) {
            if (be.mk() >= kit) {
                player.displayClientMessage(Component.translatable("message.heliosphere.turret_has_mk", be.mk()), true);
                return InteractionResult.FAIL;
            }
            be.upgrade(kit);
            if (!player.isCreative()) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.heliosphere.turret_upgraded", kit), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SentryBlockEntity be) be.status(player);
        return InteractionResult.SUCCESS;
    }
}
