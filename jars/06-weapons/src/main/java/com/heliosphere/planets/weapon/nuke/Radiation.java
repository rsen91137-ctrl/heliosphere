package com.heliosphere.planets.weapon.nuke;

import com.heliosphere.core.env.SpaceSuits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Dose bookkeeping: how hot a place is, how much of it gets through what someone is wearing, and what it does. */
public final class Radiation {
    private Radiation() {}

    /** Dose rate from the ground itself: fused glass, baked soil and ash within two blocks of the feet. 0..1. */
    public static float ground(ServerLevel level, BlockPos feet) {
        float sum = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 1; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockState s = level.getBlockState(p.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz));
            float w = s.is(NukeContent.TRINITITE) ? 1f : s.is(NukeContent.FALLOUT_ASH) ? 0.6f : s.is(NukeContent.SCORCHED_EARTH) ? 0.12f : 0;
            if (w > 0) sum += w / (1 + dx * dx + dy * dy + dz * dz);
        }
        return Math.min(1, sum * 0.16f);
    }

    /** Fraction of the dose that reaches the body. A sealed suit stops nearly all of it; better suits, more. */
    public static float exposure(LivingEntity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return 0;
        SpaceSuits.Protection suit = SpaceSuits.of(e);
        if (!suit.sealed()) return 1f;
        return suit.tier() >= 3 ? 0f : suit.tier() == 2 ? 0.05f : 0.15f;
    }

    /** Applies a dose rate (0..1 after protection) for the next few seconds. */
    public static void irradiate(LivingEntity e, float dose) {
        if (dose < 0.04f) return;
        int severity = dose > 0.75f ? 3 : dose > 0.45f ? 2 : dose > 0.18f ? 1 : 0;
        MobEffectInstance now = e.getEffect(NukeContent.RADIATION);
        // sickness outlasts the exposure: the worse the dose, the longer it lingers
        int duration = 200 + severity * 240;
        if (now == null || now.getAmplifier() < severity || now.getDuration() < duration - 60)
            e.addEffect(new MobEffectInstance(NukeContent.RADIATION, duration, severity, false, false, true));
    }
}
