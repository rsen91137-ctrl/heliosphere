package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.DeckView;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The haze on the flight deck's horizon follows its sky into black space. */
@Mixin(FogRenderer.class)
public abstract class DeckFogMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void heliosphere$deckFog(Camera camera, float partial, ClientLevel level, int renderDistance, float darken, CallbackInfoReturnable<Vector4f> cir) {
        float d = DeckView.skyDarken();
        if (d <= 0.001f) return;
        Vector4f c = cir.getReturnValue();
        if (c != null) c.set(c.x * (1 - d), c.y * (1 - d), c.z * (1 - d), c.w);
    }
}
