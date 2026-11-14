package com.heliosphere.planets.gear.mixin;

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

/** Right-clicking armour that carries the portable sentry with an empty cursor takes the sentry off into the cursor. */
@Mixin(Item.class)
public abstract class ShoulderSentryDetachMixin {
    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void heliosphere$takeOff(ItemStack me, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.SECONDARY || !other.isEmpty() || ShoulderSentry.charge(me) < 0) return;
        access.set(ShoulderSentry.takeOff(me));
        slot.setChanged();
        player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.9f);
        cir.setReturnValue(true);
    }
}
