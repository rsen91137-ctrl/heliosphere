package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.GunItem;
import com.heliosphere.planets.weapon.GunStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The gun's tooltip shows the damage it really does to enemies (see PlayerWeaponDamageMixin). */
@Mixin(GunItem.class)
public abstract class GunTooltipDamageMixin {
    @Redirect(method = "appendHoverText", at = @At(value = "INVOKE", target = "Lcom/heliosphere/planets/weapon/GunStats;damage()F", remap = false))
    private float heliosphere$shownDamage(GunStats stats) {
        return stats.damage() * com.heliosphere.planets.weapon.PlayerDamage.MULTIPLIER;
    }
}
