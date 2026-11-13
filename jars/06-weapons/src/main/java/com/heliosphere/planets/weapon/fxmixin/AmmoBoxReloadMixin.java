package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.ammo.AmmoBox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks a reload by a player carrying the creative ammo box: it then needs no ammunition (see PlayerAmmoBoxMixin). */
@Mixin(targets = "com.heliosphere.planets.tech.Attachments", remap = false)
public abstract class AmmoBoxReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"), remap = false)
    private static void heliosphere$begin(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && AmmoBox.carries(player)) AmmoBox.RELOADING.set(player);
    }

    @Inject(method = "reload", at = @At("RETURN"), remap = false)
    private static void heliosphere$end(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        AmmoBox.RELOADING.remove();
    }
}
