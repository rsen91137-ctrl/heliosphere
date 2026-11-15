package com.heliosphere.disasters.acid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The acid-proof roof cloth, made translucent: it was registered as a solid block (a cobweb's class with a carpet's
 * properties), so it shut out the sky and a solar panel under it saw no sun. Now it lets daylight through like glass
 * (full sky light under it, so solar panels and the shoulder sentry's panel work) while still keeping the acid rain
 * off: the acid checks for any block overhead, which it still is. It no longer slows whoever walks into it.
 */
public class AcidClothBlock extends TransparentBlock {
    /** As thin as the carpet it is drawn as (it used to collide as a full block). */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public AcidClothBlock(Properties p) { super(p); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
}
