package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.DeckView;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The flight deck's sky: blue on the pad, darkening into black space as the rocket climbs; black over airless worlds. */
@Mixin(value = SkyRenderer.class, priority = 1200)
public abstract class DeckSkyMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void heliosphere$deckSky(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float d = DeckView.skyDarken();
        if (d <= 0.001f) return;
        state.skyColor = scale(state.skyColor, 1 - d);
        state.sunriseAndSunsetColor = scale(state.sunriseAndSunsetColor, 1 - d);
        state.starBrightness = Math.max(state.starBrightness, d * 0.9f);
    }

    private static int scale(int argb, float k) {
        int a = argb >>> 24, r = (int) ((argb >> 16 & 255) * k), g = (int) ((argb >> 8 & 255) * k), b = (int) ((argb & 255) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
