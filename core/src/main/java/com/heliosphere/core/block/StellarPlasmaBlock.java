package com.heliosphere.core.block;

import com.heliosphere.core.env.SpaceSuits;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A block of stellar photosphere plasma (~5800 K). It is the material the Sun is rendered with and can be
 * placed as a fusion core. Radiant heat falls off with the square of distance; without a stellar-rated
 * suit anything close to it is incinerated. Heat is computed per block entity every half second, so it
 * costs nothing like a particle effect would.
 */
public class StellarPlasmaBlock extends BaseEntityBlock {
    public static final MapCodec<StellarPlasmaBlock> CODEC = simpleCodec(StellarPlasmaBlock::new);
    /** Suits must be rated at least this hot to stand next to plasma. */
    public static final int STELLAR_SUIT_TEMP = 2000;
    public static final double RADIUS = 10;

    public StellarPlasmaBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Tile(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, CoreBlocks.STELLAR_PLASMA_BE, (l, p, s, be) -> be.serverTick((ServerLevel) l));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide()) entity.setRemainingFireTicks(200);
        super.stepOn(level, pos, state, entity);
    }

    public static class Tile extends BlockEntity {
        private int timer;

        public Tile(BlockPos pos, BlockState state) { super(CoreBlocks.STELLAR_PLASMA_BE, pos, state); }

        void serverTick(ServerLevel level) {
            if (++timer % 10 != 0) return;
            BlockPos p = getBlockPos();
            AABB box = new AABB(p).inflate(RADIUS);
            List<Entity> list = level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive());
            for (Entity e : list) {
                double d2 = Math.max(1, e.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                if (d2 > RADIUS * RADIUS) continue;
                double flux = 100.0 / d2; // relative irradiance, 100 at 1 block
                if (e instanceof ItemEntity item) { item.discard(); continue; }
                if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                if (e instanceof LivingEntity le) {
                    SpaceSuits.Protection prot = SpaceSuits.of(le);
                    double shield = prot.maxTempC() >= STELLAR_SUIT_TEMP ? 0.02 : 1.0;
                    float dmg = (float) (flux * 0.8 * shield);
                    if (dmg < 0.5f) continue;
                    le.setRemainingFireTicks(Math.max(le.getRemainingFireTicks(), 60));
                    le.hurtServer(level, level.damageSources().inFire(), dmg);
                }
            }
        }
    }
}
