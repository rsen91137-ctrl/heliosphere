package com.heliosphere.planets.defense.sam.client.mixin;

import com.heliosphere.planets.defense.sam.client.MissileCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The missile camera: once the camera is set up as usual, it is moved behind the missile being watched. */
@Mixin(Camera.class)
public abstract class MissileCamSetupMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void heliosphere$missileView(Level level, Entity entity, boolean wasDetached, boolean mirror, float partial, CallbackInfo ci) {
        double[] p = MissileCamera.pose(partial);
        if (p == null) return;
        setRotation((float) p[3], (float) p[4]);
        setPosition(p[0], p[1], p[2]);
        detached = true;                                     // the player is drawn where they stand, and no hands
    }
}
