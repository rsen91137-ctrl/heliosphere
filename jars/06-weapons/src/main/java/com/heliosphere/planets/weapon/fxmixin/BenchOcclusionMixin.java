package com.heliosphere.planets.weapon.fxmixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The gunsmith bench is not a full cube but was registered as one that occludes, so the faces of the blocks
 * under and beside it were culled and the world showed through. It now hides nothing.
 */
@Mixin(targets = "com.heliosphere.planets.tech.Arsenal$Bench")
public abstract class BenchOcclusionMixin extends Block {
    protected BenchOcclusionMixin(Properties p) { super(p); }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }
}
