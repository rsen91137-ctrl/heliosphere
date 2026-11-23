package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.foods.cuisine.KitchenToolItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The crafting grid's repair recipe leaves kitchen tools alone. A kitchen tool stays in the grid after every recipe it
 * is used in (KitchenToolItem#getRecipeRemainder, which Fabric applies to all crafting recipes), so two knives "repaired"
 * into a fresh one also stayed in the grid: a free tool per craft, for ever. They are mended in an anvil instead.
 */
@Mixin(RepairItemRecipe.class)
public abstract class KitchenToolRepairMixin {
    @Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
    private static void heliosphere$notKitchenTools(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (first.getItem() instanceof KitchenToolItem || second.getItem() instanceof KitchenToolItem) cir.setReturnValue(false);
    }
}
