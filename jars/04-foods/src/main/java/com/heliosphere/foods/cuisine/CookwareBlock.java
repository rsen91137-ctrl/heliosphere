package com.heliosphere.foods.cuisine;

import com.heliosphere.core.machine.MachineBlock;
import com.heliosphere.core.machine.MachineBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A cookware machine (wok, cooking pot, steamer, oven, grain mill, juicer, fermentation crock): one of the standard
 * processing machines with its own screen and recipe list, in its own shape. The low ones (wok, pot, steamer, crock)
 * are not full blocks; the wok, pot and steamer cook only on a heat source ({@link Heat}).
 */
public class CookwareBlock extends MachineBlock {
    /** Outline and collision box in pixels, the same as the block model's. */
    public static VoxelShape shapeOf(String id) {
        return switch (id) {
            case "wok" -> Block.box(1, 0, 1, 15, 5, 15);
            case "cooking_pot" -> Block.box(2, 0, 2, 14, 10, 14);
            case "steamer" -> Block.box(2, 0, 2, 14, 11, 14);
            case "fermenter" -> Block.box(3, 0, 3, 13, 14, 13);
            default -> Shapes.block();                       // oven, grain mill, juicer
        };
    }

    /** Whether the block of machine {@code id} is a full cube (the others render cut out and do not occlude). */
    public static boolean fullCube(String id) { return shapeOf(id) == Shapes.block(); }

    private final Supplier<BlockEntityType<MachineBlockEntity>> beType;
    private final VoxelShape shape;
    /** Needs a heat source below to cook. */
    public final boolean heat;
    /** Has a start button and a clickable recipe list. */
    public final boolean gated;
    /** Runs on grid power (oven, grain mill, juicer); the others take none from the cables (CookwarePowerMixin). */
    public final boolean usesPower;

    public CookwareBlock(Properties properties, String typeId, Supplier<BlockEntityType<MachineBlockEntity>> beType, boolean heat, boolean gated, boolean usesPower) {
        super(properties, typeId, beType);
        this.beType = beType;
        this.shape = shapeOf(typeId);
        this.heat = heat;
        this.gated = gated;
        this.usesPower = usesPower;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new CookwareBlock(p, typeId, beType, heat, gated, usesPower));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape; }

    /** Whether {@code state} is a heat machine of ours working right now (its LIT state). */
    public static boolean cookingWithHeat(BlockState state) {
        return state.getBlock() instanceof CookwareBlock c && c.heat && state.hasProperty(LIT) && state.getValue(LIT);
    }
}
