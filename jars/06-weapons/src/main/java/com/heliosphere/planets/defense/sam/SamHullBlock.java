package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The hidden, solid body of an air-defence vehicle. It never hides its neighbours' faces (no x-ray holes). */
public class SamHullBlock extends Block {
    public SamHullBlock(Properties p) { super(p.noOcclusion()); }

    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getOcclusionShape(BlockState s) { return Shapes.empty(); }
    @Override protected float getShadeBrightness(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return 1f; }

    static BlockPos master(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, -3, -5), pos.offset(5, 0, 5))) {
            if (level.getBlockState(p).getBlock() instanceof SamBlock sb) {
                for (BlockPos q : sb.kind.footprint(p, level.getBlockState(p).getValue(SamBlock.FACING)))
                    if (q.equals(pos)) return p.immutable();
            }
        }
        return null;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (SamBlock.removing) return;
        BlockPos m = master(level, pos);
        if (m != null) level.destroyBlock(m, true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos m = master(level, pos);
        if (m != null && level.getBlockEntity(m) instanceof SamBlockEntity be) be.status(player);
        return InteractionResult.SUCCESS;
    }
}
