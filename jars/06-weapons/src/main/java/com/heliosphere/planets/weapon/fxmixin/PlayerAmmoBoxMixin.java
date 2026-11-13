package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.ammo.AmmoBox;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During such a reload, and only then, the player counts as creative for the ammunition check. */
@Mixin(Player.class)
public abstract class PlayerAmmoBoxMixin {
    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void heliosphere$ammoBox(CallbackInfoReturnable<Boolean> cir) {
        if (AmmoBox.RELOADING.get() == (Object) this) cir.setReturnValue(true);
    }
}
