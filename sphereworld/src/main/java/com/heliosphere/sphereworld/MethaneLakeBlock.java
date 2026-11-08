package com.heliosphere.sphereworld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Titan's hydrocarbon seas: you wade through them; an empty canister scoops up liquid methane fuel. */
public class MethaneLakeBlock extends Block {
    public MethaneLakeBlock(Properties p) { super(p); }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean b) {
        entity.makeStuckInBlock(state, new Vec3(0.55, 0.6, 0.55));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction dir) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, dir);
    }
}
