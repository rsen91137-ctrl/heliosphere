package com.heliosphere.core.mixin.client;

import com.heliosphere.core.client.CameraShake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void heliosphere$shake(Level level, Entity entity, boolean detached, boolean mirrored, float pt, CallbackInfo ci) {
        float a = CameraShake.amount;
        if (a <= 0.01f) return;
        double t = (System.nanoTime() / 1e9);
        float dy = (float) (Math.sin(t * 47) * 0.6 + Math.sin(t * 83 + 1.3) * 0.4) * a;
        float dx = (float) (Math.sin(t * 61 + 0.7) * 0.6 + Math.sin(t * 97 + 2.1) * 0.4) * a;
        setRotation(yRot() + dy, xRot() + dx);
    }
}
