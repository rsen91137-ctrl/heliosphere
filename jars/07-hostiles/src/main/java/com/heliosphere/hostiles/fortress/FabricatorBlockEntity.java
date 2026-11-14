package com.heliosphere.hostiles.fortress;

import com.heliosphere.hostiles.arsenal.EnemyRoster;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Builds a robot from the fortress's pool whenever a player is within 40 blocks and fewer than the cap of machines
 * stand around it. The cap and the build time scale with the fortress difficulty.
 */
public class FabricatorBlockEntity extends BlockEntity {
    private int tier = 1;
    private int cooldown = 100;

    public FabricatorBlockEntity(BlockPos pos, BlockState state) { super(Fortresses.FABRICATOR_BE, pos, state); }

    public void setup(int tier) { this.tier = Math.max(1, Math.min(15, tier)); this.cooldown = 60; setChanged(); }
    public int tier() { return tier; }

    void tick(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) return;
        BlockPos pos = getBlockPos();
        ServerPlayer near = null;
        double best = 40 * 40;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            double d = p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d < best) { best = d; near = p; }
        }
        if (near == null) return;
        if (level.getGameTime() % 20 == 0)
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.02);
        cooldown -= 10;
        if (cooldown > 0) return;
        cooldown = Math.max(80, 300 - tier * 14) + level.random.nextInt(60);
        setChanged();
        int cap = 2 + tier / 4;
        if (level.getEntitiesOfClass(TechMob.class, new AABB(pos).inflate(24)).size() >= cap + 2) return;
        BlockPos at = exit(level, pos);
        if (at == null) return;
        List<String> pool = EnemyRoster.groundPool(tier);
        String id = pool.get(level.random.nextInt(pool.size()));
        TechMob m = TechCombat.spawn(level, id, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, near);
        if (m == null) return;
        m.addTag("heliosphere.fortress");
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 0.8f, 0.6f);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.6f, 1.6f);
    }

    /** A free spot beside the fabricator a robot can step out onto. */
    private static BlockPos exit(ServerLevel level, BlockPos pos) {
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        int start = level.random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            for (int dist = 1; dist <= 2; dist++) {
                BlockPos at = pos.relative(dirs[(start + i) % 4], dist);
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos q = at.above(dy);
                    if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() && !level.getBlockState(q.below()).isAir()) return q;
                }
            }
        }
        BlockPos up = pos.above();
        return level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir() ? up : null;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tier = in.getIntOr("tier", 1);
        cooldown = in.getIntOr("cooldown", 100);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("tier", tier);
        out.putInt("cooldown", cooldown);
    }
}
