package com.heliosphere.planets.gear.mixin;

import com.heliosphere.planets.gear.JetpackItem;
import com.heliosphere.planets.gear.ShoulderSentry;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicking a space suit chestplate that carries a jetpack with an empty cursor takes the pack off into the
 * cursor (a portable sentry on it comes off first, see ShoulderSentryDetachMixin).
 */
@Mixin(Item.class)
public abstract class SuitJetpackDetachMixin {
    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void heliosphere$takeOffJetpack(ItemStack me, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.SECONDARY || !other.isEmpty() || !JetpackItem.suit(me) || JetpackItem.kindOn(me) == null) return;
        if (ShoulderSentry.charge(me) >= 0 || !slot.allowModification(player)) return;
        access.set(JetpackItem.takeOff(me));
        slot.setChanged();
        player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.0f);
        cir.setReturnValue(true);
    }
}
