package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.RealCabin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the block cabin you walk the way you face (the deck turns with the rocket; its heading is 90° from Minecraft's yaw). */
@Mixin(targets = "com.heliosphere.rocketry.client.FlightCinematic", remap = false)
public abstract class WalkHeadingMixin {
    @Inject(method = "walkHeading", at = @At("RETURN"), cancellable = true, remap = false)
    private static void heliosphere$face(CallbackInfoReturnable<Float> cir) {
        if (!RealCabin.active()) return;
        var p = Minecraft.getInstance().player;
        var r = RealCabin.rocket();
        if (p == null || r == null) return;
        cir.setReturnValue(p.getYRot() - r.getYRot() + 90f);
    }
}
