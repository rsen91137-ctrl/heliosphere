package com.heliosphere.sphereworld.giant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The ocean at the bottom of a giant planet: a fluid hot enough to vaporise anything. You sink into it slowly
 * and burn; nothing survives at the bottom of Jupiter.
 */
public class GiantOceanBlock extends Block {
    public final int color, kelvin;

    public GiantOceanBlock(Properties p, int color, int kelvin) { super(p); this.color = color; this.kelvin = kelvin; }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity e, InsideBlockEffectApplier effects, boolean intersects) {
        e.makeStuckInBlock(state, new Vec3(0.6, 0.25, 0.6));
        if (level instanceof ServerLevel server && e instanceof LivingEntity living && level.getGameTime() % 5 == 0) {
            living.igniteForSeconds(10);
            living.hurtServer(server, server.damageSources().hotFloor(), 12f);
        } else if (!(e instanceof LivingEntity) && level instanceof ServerLevel && level.getGameTime() % 20 == 0) {
            e.discard();
        }
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState other, Direction side) {
        return other.is(this) || super.skipRendering(state, other, side);
    }
}
