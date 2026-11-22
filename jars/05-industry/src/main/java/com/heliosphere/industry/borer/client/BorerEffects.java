package com.heliosphere.industry.borer.client;

import com.heliosphere.industry.borer.TunnelBorer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * What a cutting borer gives off, on every client near it: chips of the rock it is cutting (crack particles of the
 * blocks on its face), sparks at the cutter rim, a puff of dust now and then, the blocks' hit sounds, and a low
 * grinding loop that dies away when the cutter stops.
 */
final class BorerEffects {
    private BorerEffects() {}

    private static final Map<Integer, GrindSound> PLAYING = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { PLAYING.clear(); return; }
        if (mc.isPaused()) return;
        PLAYING.values().removeIf(AbstractTickableSoundInstance::isStopped);
        for (TunnelBorer b : level.getEntitiesOfClass(TunnelBorer.class, mc.player.getBoundingBox().inflate(48))) {
            if (!b.boring()) continue;
            cutting(level, b);
            if (!PLAYING.containsKey(b.getId())) {
                GrindSound s = new GrindSound(b);
                PLAYING.put(b.getId(), s);
                mc.getSoundManager().play(s);
            }
        }
    }

    /** The face: the blocks one or two ahead of the cube (a sloped step reaches one row higher or lower). */
    private static BlockState face(ClientLevel level, TunnelBorer b, int lateral, int height, BlockPos.MutableBlockPos at) {
        Direction f = b.facing(), side = f.getClockWise();
        BlockPos base = BlockPos.containing(b.getX(), b.getY() + 0.01, b.getZ());
        int[] rows = b.slope() == 0 ? new int[]{0} : new int[]{0, b.slope(), -b.slope()};
        for (int d = 2; d <= 3; d++)
            for (int dy : rows) {
                at.set(base.getX() + f.getStepX() * d + side.getStepX() * lateral, base.getY() + height + dy, base.getZ() + f.getStepZ() * d + side.getStepZ() * lateral);
                BlockState st = level.getBlockState(at);
                if (!st.isAir() && st.getFluidState().isEmpty()) return st;
            }
        return null;
    }

    private static void cutting(ClientLevel level, TunnelBorer b) {
        RandomSource r = RANDOM;
        Direction f = b.facing(), side = f.getClockWise();
        double fx = f.getStepX(), fz = f.getStepZ(), sx = side.getStepX(), sz = side.getStepZ();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        // chips of rock off the face, thrown back at the cutter (what the driver sees)
        for (int i = 0; i < 2; i++) {
            double u = (r.nextDouble() - 0.5) * 2.8, v = 0.1 + r.nextDouble() * 2.8;
            BlockState st = face(level, b, (int) Math.round(u), (int) Math.floor(v), at);
            if (st == null) continue;
            double back = 0.05 + r.nextDouble() * 0.12, spread = (r.nextDouble() - 0.5) * 0.12;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st),
                    b.getX() + fx * 1.46 + sx * u, b.getY() + v, b.getZ() + fz * 1.46 + sz * u,
                    -fx * back + sx * spread, 0.03 + r.nextDouble() * 0.08, -fz * back + sz * spread);
        }
        // and streaming back along the corners of the bore, outside the round shield (what everyone else sees)
        {
            int cu = r.nextBoolean() ? 1 : -1, cv = r.nextBoolean() ? 2 : 0;
            BlockState st = face(level, b, cu, cv, at);
            if (st != null) {
                double u = cu * (1.28 + r.nextDouble() * 0.15), v = cv == 2 ? 2.75 + r.nextDouble() * 0.15 : 0.1 + r.nextDouble() * 0.15;
                double back = 0.18 + r.nextDouble() * 0.15;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st),
                        b.getX() + fx * 1.4 + sx * u, b.getY() + v, b.getZ() + fz * 1.4 + sz * u,
                        -fx * back, cv == 2 ? 0.02 : 0.08, -fz * back);
            }
        }
        // sparks where the gauge cutters bite at the rim
        if (r.nextFloat() < 0.6f) {
            double a = r.nextDouble() * Math.PI * 2, rad = 1.30 + r.nextDouble() * 0.12, c = Math.cos(a), s = Math.sin(a);
            double out = 0.4 + r.nextDouble() * 0.5;                   // spark particles keep a quarter of this
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    b.getX() + fx * 1.47 + sx * c * rad, b.getY() + 1.5 + s * rad, b.getZ() + fz * 1.47 + sz * c * rad,
                    sx * c * out - fx * 0.2, s * out + 0.15, sz * c * out - fz * 0.2);
        }
        // dust: a puff at the face now and then, and a haze drifting back out of the top corners
        if (r.nextFloat() < 0.12f) {
            double u = (r.nextDouble() - 0.5) * 2.2, v = 0.3 + r.nextDouble() * 2.2;
            level.addParticle(ParticleTypes.POOF, b.getX() + fx * 1.4 + sx * u, b.getY() + v, b.getZ() + fz * 1.4 + sz * u,
                    -fx * 0.02, 0.01, -fz * 0.02);
        }
        if (r.nextFloat() < 0.2f) {
            double u = (r.nextBoolean() ? 1 : -1) * (1.25 + r.nextDouble() * 0.15), v = 2.65 + r.nextDouble() * 0.2;
            level.addParticle(ParticleTypes.SMOKE, b.getX() + fx * 1.2 + sx * u, b.getY() + v, b.getZ() + fz * 1.2 + sz * u,
                    -fx * 0.12, 0.0, -fz * 0.12);
        }
        // the face's own hit sound, as when a player mines it
        if (b.tickCount % 4 == 0) {
            BlockState st = face(level, b, r.nextInt(3) - 1, r.nextInt(3), at);
            if (st != null) {
                SoundType type = st.getSoundType();
                level.playLocalSound(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, type.getHitSound(), SoundSource.BLOCKS,
                        (type.getVolume() + 1f) / 5f, type.getPitch() * (0.45f + r.nextFloat() * 0.15f), false);
            }
        }
    }

    /** A low grinding loop at the cutter while it cuts; it fades out between steps and stops once the cutter is idle. */
    static final class GrindSound extends AbstractTickableSoundInstance {
        private final TunnelBorer b;
        private int idle;

        GrindSound(TunnelBorer b) {
            super(SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.b = b;
            looping = true;
            delay = 0;
            volume = 0.02f;
            pitch = 0.5f;
            place();
        }

        @Override public boolean canStartSilent() { return true; }
        @Override public boolean canPlaySound() { return !b.isSilent(); }

        @Override
        public void tick() {
            if (b.isRemoved()) { stop(); return; }
            boolean cutting = b.boring();
            idle = cutting ? 0 : idle + 1;
            float want = cutting ? 0.45f : b.moving() ? 0.15f : 0f;
            volume += (want - volume) * (cutting ? 0.3f : 0.12f);
            pitch = cutting ? 0.5f + 0.04f * (float) Math.sin(b.tickCount * 0.6) : 0.45f;
            place();
            if (idle > 40 && volume < 0.02f) stop();
        }

        private void place() {
            Direction f = b.facing();
            x = b.getX() + f.getStepX() * 1.3;
            y = b.getY() + 1.5;
            z = b.getZ() + f.getStepZ() * 1.3;
        }
    }
}
