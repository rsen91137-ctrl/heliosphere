package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.RealCabin;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No letterbox bars or flight timeline in the cabin: it is not a cutscene. */
@Mixin(targets = "com.heliosphere.rocketry.client.FlightCinematic", remap = false)
public class CabinCinematicMixin {
    @Inject(method = "active", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$hudPass(CallbackInfoReturnable<Boolean> cir) { if (RealCabin.HUD_PASS.get()) cir.setReturnValue(false); }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$noBars(GuiGraphics g, float partial, CallbackInfo ci) { if (RealCabin.active()) ci.cancel(); }
}
