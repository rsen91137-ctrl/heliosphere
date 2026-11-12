package com.heliosphere.hostiles.arsenal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Doomsday Cult (vindicator, pillager and evoker raids and strongholds) is retired: its tick handlers are never
 * registered. The machine sieges of com.heliosphere.hostiles.siege take its place. The cult core block stays
 * registered so old worlds still load.
 */
@Mixin(targets = "com.heliosphere.planets.enemy.Cult", remap = false)
public abstract class CultRetiredMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$retire(CallbackInfo ci) { ci.cancel(); }
}
