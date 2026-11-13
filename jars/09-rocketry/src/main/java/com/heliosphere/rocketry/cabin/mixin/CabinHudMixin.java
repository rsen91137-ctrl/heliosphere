package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.RealCabin;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the cabin the ordinary HUD stays (hotbar, chat, health): the released flight overlay replaces the whole HUD
 * while its cinematic runs, so for the duration of the HUD pass the cinematic reads as inactive.
 */
@Mixin(value = Gui.class, priority = 500)
public class CabinHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void heliosphere$cabinHud(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) { RealCabin.HUD_PASS.set(RealCabin.active()); }

    @Inject(method = "render", at = @At("RETURN"))
    private void heliosphere$cabinHudEnd(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) { RealCabin.HUD_PASS.set(false); }
}
