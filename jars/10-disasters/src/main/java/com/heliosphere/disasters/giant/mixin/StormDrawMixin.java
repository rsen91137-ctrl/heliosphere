package com.heliosphere.disasters.giant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The released flat storm disks and lightning are replaced by GiantSkyClient. */
@Mixin(targets = "com.heliosphere.core.storm.PlanetStormClient", remap = false)
public abstract class StormDrawMixin {
    @Inject(method = "drawStorm", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$replaced(CallbackInfo ci) { ci.cancel(); }
}
