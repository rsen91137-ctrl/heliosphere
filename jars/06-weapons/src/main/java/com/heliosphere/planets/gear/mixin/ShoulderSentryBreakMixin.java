package com.heliosphere.planets.gear.mixin;

import com.heliosphere.planets.gear.ShoulderSentry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Armour breaking with the portable sentry on it: the sentry is saved ({@link ShoulderSentry#armourBreaking}). */
@Mixin(ItemStack.class)
public abstract class ShoulderSentryBreakMixin {
    @Inject(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void heliosphere$saveSentry(int damage, ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
        ShoulderSentry.armourBreaking((ItemStack) (Object) this, player);
        com.heliosphere.planets.gear.JetpackItem.suitBreaking((ItemStack) (Object) this, player);
    }
}
