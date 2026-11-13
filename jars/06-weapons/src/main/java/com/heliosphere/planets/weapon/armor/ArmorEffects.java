package com.heliosphere.planets.weapon.armor;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * What the special armour does, piece by piece: combat uniforms stop 6 % of bullet and missile damage each, the
 * bomb suit 15 % of blast damage each, the alien plates 10 % of fire and energy damage each; a full hazmat suit
 * keeps radiation, poison and wither out; a full ghillie suit, while you crouch, hides you from machines further
 * than 10 blocks away.
 */
public final class ArmorEffects {
    private ArmorEffects() {}

    public static float reduce(LivingEntity e, DamageSource src, float amount) {
        if (amount <= 0) return amount;
        for (ArmorSet set : ArmorSet.values()) {
            int n = Armory.worn(e, set);
            if (n == 0) continue;
            switch (set.special) {
                case BALLISTIC -> { if (src.is(DamageTypeTags.IS_PROJECTILE) || bullet(src)) amount *= 1 - 0.06f * n; }
                case BLAST -> { if (src.is(DamageTypeTags.IS_EXPLOSION)) amount *= 1 - 0.15f * n; }
                case ENERGY -> { if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_LIGHTNING) || bullet(src)) amount *= 1 - 0.1f * n; }
                case HAZMAT -> { if (n == 4 && (src.is(net.minecraft.world.damagesource.DamageTypes.WITHER) || src.is(net.minecraft.world.damagesource.DamageTypes.MAGIC))) amount *= 0.2f; }
                default -> { }
            }
        }
        return amount;
    }

    private static boolean bullet(DamageSource src) {
        return src.getMsgId().contains("gunshot") || src.getDirectEntity() instanceof com.heliosphere.planets.defense.sam.GuidedMissile;
    }

    public static boolean radiationProof(LivingEntity e) { return Armory.worn(e, ArmorSet.HAZMAT) == 4; }

    /** Whether a machine at {@code mob} may take {@code target} as its target. */
    public static boolean hidden(Mob mob, LivingEntity target) {
        return target instanceof Player p && p.isShiftKeyDown() && Armory.worn(p, ArmorSet.GHILLIE) == 4 && mob.distanceTo(p) > 10;
    }
}
