package com.heliosphere.core.mixin.fx;

import com.heliosphere.core.client.fx.RecipeFilter;
import com.heliosphere.core.client.screen.MachineScreen;
import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.machine.MachineRecipe;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** A machine with a start button lists only the recipes its ingredients allow ({@link RecipeFilter}). */
@Mixin(value = MachineScreen.class, remap = false)
public abstract class MachineScreenRecipesMixin extends AbstractContainerScreen<MachineMenu> {
    private MachineScreenRecipesMixin(MachineMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Inject(method = "recipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void heliosphere$filtered(CallbackInfoReturnable<List<MachineRecipe>> cir) {
        cir.setReturnValue(RecipeFilter.visible(this.menu));
    }
}
