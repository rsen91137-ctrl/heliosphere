package com.heliosphere.hostiles.fortress;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine fortress's point-defence launcher (difficulty 7 and up): vertical cells of hit-to-kill interceptors and
 * a small fire-control radar. It shoots down the players' missiles, the tactical nuke included, before they reach
 * the fortress. Break it to open the sky.
 */
public class PointDefenceBlock extends BaseEntityBlock {
    public PointDefenceBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(PointDefenceBlock::new); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PointDefenceBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != Fortresses.POINT_DEFENCE_BE ? null : (l, p, s, be) -> ((PointDefenceBlockEntity) be).tick((ServerLevel) l);
    }
}
