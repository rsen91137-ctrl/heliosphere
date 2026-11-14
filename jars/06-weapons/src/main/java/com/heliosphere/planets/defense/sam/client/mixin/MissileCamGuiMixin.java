package com.heliosphere.planets.defense.sam.client.mixin;

import com.heliosphere.planets.defense.sam.client.MissileCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No crosshair in the missile camera: the missile itself sits in the middle of the picture. */
@Mixin(Gui.class)
public abstract class MissileCamGuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void heliosphere$noCrosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (MissileCamera.active()) ci.cancel();
    }
}
