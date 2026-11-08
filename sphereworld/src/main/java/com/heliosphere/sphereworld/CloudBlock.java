package com.heliosphere.sphereworld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Gas-giant cloud deck: no surface, it only slows whatever falls through it. */
public class CloudBlock extends Block {
    public CloudBlock(Properties p) { super(p); }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean b) {
        entity.makeStuckInBlock(state, new Vec3(0.92, 0.75, 0.92));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction dir) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, dir);
    }
}
