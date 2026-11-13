package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.RealCabin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The old drawn cockpit is not drawn while you are in the block cabin. */
@Mixin(targets = "com.heliosphere.rocketry.client.WindowCockpit", remap = false)
public abstract class CockpitHideMixin {
    @Inject(method = "lambda$onInitializeClient$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$hide(CallbackInfo ci) { if (RealCabin.active()) ci.cancel(); }
}
