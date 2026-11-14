package com.heliosphere.planets.defense.sam.client.mixin;

import com.heliosphere.planets.defense.sam.client.MissileCamera;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the missile camera is on, the scroll wheel moves the view in and out instead of changing the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MissileCamInputMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void heliosphere$zoom(long window, double dx, double dy, CallbackInfo ci) {
        if (MissileCamera.active()) {
            MissileCamera.zoom(dy);
            ci.cancel();
        }
    }
}
