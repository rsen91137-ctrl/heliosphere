package com.heliosphere.industry.habitat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The habitat field emitter (see {@link HabitatEmitterBlockEntity}). Right-click for its state; it glows while running. */
public class HabitatEmitterBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public HabitatEmitterBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(HabitatEmitterBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ACTIVE); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HabitatEmitterBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type != Habitat.EMITTER_ENTITY ? null : (l, p, s, be) -> ((HabitatEmitterBlockEntity) be).tick(l, p, s);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player p && p.isCreative() && level.getBlockEntity(pos) instanceof HabitatEmitterBlockEntity be) be.setCreative(true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HabitatEmitterBlockEntity be) be.status(player);
        return InteractionResult.SUCCESS;
    }
}
