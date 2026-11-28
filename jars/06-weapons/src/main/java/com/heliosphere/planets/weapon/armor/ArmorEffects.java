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
 * than 10 blocks away. Any armour also blunts attacks from machines and creatures ({@link #guard}).
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
        if (e instanceof Player p && src.getEntity() != null && !(src.getEntity() instanceof Player)) amount *= guard(p);
        return amount;
    }

    /**
     * Armour against attacks from machines and creatures (their shots, blasts and blows): on top of the vanilla
     * reduction, every point of armour takes 5 % more off, down to 30 % of the hit with 14 points or more. Without it a
     * machine's ordinary 30-point shot went straight through a space suit (13 armour, toughness 1) in one hit; now a
     * suit or iron takes four or five such hits, diamond, netherite and the military sets seven or eight. PvP and
     * falls, fire, the void and the like are left as they were.
     */
    public static float guard(Player p) {
        return Math.max(0.3f, 1f - 0.05f * p.getArmorValue());
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
