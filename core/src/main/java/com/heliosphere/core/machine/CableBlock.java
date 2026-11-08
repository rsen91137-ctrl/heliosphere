package com.heliosphere.core.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** A power cable that visually connects to neighbouring cables and energy blocks. */
public class CableBlock extends Block {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);
    private static final Map<Direction, BooleanProperty> PROPS = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);

    public CableBlock(Properties p) {
        super(p);
        BlockState s = stateDefinition.any();
        for (BooleanProperty b : PROPS.values()) s = s.setValue(b, false);
        registerDefaultState(s);
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { PROPS.values().forEach(b::add); }

    private boolean connects(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof CableBlock || level.getBlockEntity(pos) instanceof EnergyNode;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.values()) s = s.setValue(PROPS.get(d), connects(ctx.getLevel(), ctx.getClickedPos().relative(d)));
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos nPos, BlockState nState, RandomSource random) {
        return state.setValue(PROPS.get(dir), connects(level, nPos));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = CORE;
        for (Direction d : Direction.values()) {
            if (!state.getValue(PROPS.get(d))) continue;
            shape = Shapes.or(shape, switch (d) {
                case DOWN -> Block.box(6, 0, 6, 10, 6, 10);
                case UP -> Block.box(6, 10, 6, 10, 16, 10);
                case NORTH -> Block.box(6, 6, 0, 10, 10, 6);
                case SOUTH -> Block.box(6, 6, 10, 10, 10, 16);
                case WEST -> Block.box(0, 6, 6, 6, 10, 10);
                case EAST -> Block.box(10, 6, 6, 16, 10, 10);
            });
        }
        return shape;
    }
}
