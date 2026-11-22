package com.heliosphere.foods.cuisine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crop of the expansion: four growth stages (age 0-3) like beetroot, planted from its seed on farmland or on a
 * hydroponic planter (no farmland or water needed there). Bone meal advances it a stage or two.
 */
public class CuisineCropBlock extends CropBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    /** Outline per age: a quarter of a block taller each stage. */
    private static final VoxelShape[] SHAPES = Block.boxes(MAX_AGE, age -> Block.column(16, 0, 4 + age * 4));

    /** The crop id (= block id) in the table. */
    public final String id;
    private ItemLike seed;

    public CuisineCropBlock(Properties properties, String id) {
        super(properties);
        this.id = id;
    }

    /** Set once the seed item is registered (it is registered after the block). */
    void setSeed(ItemLike seed) { this.seed = seed; }

    @Override
    public MapCodec<? extends CropBlock> codec() { return simpleCodec(p -> new CuisineCropBlock(p, id)); }

    @Override
    protected IntegerProperty getAgeProperty() { return AGE; }

    @Override
    public int getMaxAge() { return MAX_AGE; }

    @Override
    protected ItemLike getBaseSeedId() { return seed != null ? seed : Items.AIR; }

    /** Farmland, or the hydroponic planter. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof HydroponicPlanterBlock || super.mayPlaceOn(state, level, pos);
    }

    /** Grows with two chances in three on each random tick that would grow wheat, as beetroot does. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) super.randomTick(state, level, pos, random);
    }

    /** One stage, sometimes two (wheat's two to five of seven). */
    @Override
    protected int getBonemealAgeIncrease(Level level) { return Mth.nextInt(level.getRandom(), 1, 2); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[Math.min(getAge(state), MAX_AGE)];
    }
}
