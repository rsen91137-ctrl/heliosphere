package com.heliosphere.planets.defense.sam;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An air-defence vehicle saved as blocks by an earlier version. It turns into a {@link SamVehicle} entity on its
 * first tick (see {@link SamBlockEntity}); removing it takes its hidden hull blocks and its old chunk tickets along.
 */
public class SamBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final SamKind kind;
    static boolean removing;
    static final int FORCE = SamVehicle.FORCE;

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
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        for (int dx = -FORCE; dx <= FORCE; dx++) for (int dz = -FORCE; dz <= FORCE; dz++) {     // the old block kept these loaded
            int cx = (pos.getX() >> 4) + dx, cz = (pos.getZ() >> 4) + dz;
            if (!SamVehicle.anotherNear(level, null, cx, cz)) level.setChunkForced(cx, cz, false);
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
}
