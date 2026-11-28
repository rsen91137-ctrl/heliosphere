package com.heliosphere.planets.weapon.fxmixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Player weapons hit 2.5 times as hard against everything but other players: every gun (the shot, its blast, plasma,
 * singularity and antimatter effects), launched missiles, grenades and melee. PvP is left as it was, and so are
 * the turrets, SAM vehicles and the shoulder sentry that fire in their owner's name.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerWeaponDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float heliosphere$weaponDamage(float amount, ServerLevel level, DamageSource source) {
        if (amount <= 0 || (Object) this instanceof Player || !(source.getEntity() instanceof Player)) return amount;
        if (com.heliosphere.planets.weapon.PlayerDamage.automaticNow()) return amount;               // a turret firing in its owner's name
        if (source.getDirectEntity() instanceof com.heliosphere.planets.defense.sam.GuidedMissile m                   // missiles: only those fired from
                && m.spec() != com.heliosphere.planets.defense.sam.MissileSpec.PLAYER                                // a launcher in hand, not the SAM
                && m.spec() != com.heliosphere.planets.defense.sam.MissileSpec.NUKE_ROCKET) return amount;          // vehicles or the missile turret
        return amount * com.heliosphere.planets.weapon.PlayerDamage.MULTIPLIER;
    }
}
