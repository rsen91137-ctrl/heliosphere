package com.heliosphere.planets.defense.sam.client.mixin;

import com.heliosphere.planets.defense.sam.client.MissileCamera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the missile camera is on, moving the mouse looks round the missile; the player does not turn. */
@Mixin(Entity.class)
public abstract class MissileCamTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void heliosphere$dragView(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && MissileCamera.active()) {
            MissileCamera.drag(yaw, pitch);
            ci.cancel();
        }
    }
}
