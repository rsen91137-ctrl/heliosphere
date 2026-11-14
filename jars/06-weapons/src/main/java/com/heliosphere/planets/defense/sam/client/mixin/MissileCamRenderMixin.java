package com.heliosphere.planets.defense.sam.client.mixin;

import com.heliosphere.planets.defense.sam.client.MissileCamera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Behind a missile there are no hands in view and the walk of the player does not shake the picture. */
@Mixin(GameRenderer.class)
public abstract class MissileCamRenderMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void heliosphere$noHands(float partial, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
        if (MissileCamera.active()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void heliosphere$noBob(PoseStack pose, float partial, CallbackInfo ci) {
        if (MissileCamera.active()) ci.cancel();
    }
}
