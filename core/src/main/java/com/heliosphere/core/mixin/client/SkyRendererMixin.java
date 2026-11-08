package com.heliosphere.core.mixin.client;

import com.heliosphere.core.client.sky.SpaceSky;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla sun/moon in Heliosphere dimensions with the real solar system. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void heliosphere$solarSystem(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
                                         float rain, float starBrightness, CallbackInfo ci) {
        if (SpaceSky.active(Minecraft.getInstance().level)) {
            SpaceSky.render((SkyRenderer) (Object) this, pose, sunAngle, starAngle, starBrightness);
            ci.cancel();
        }
    }
}
