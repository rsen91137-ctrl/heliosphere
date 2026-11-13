package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The crew deck stays walkable through the descent and a suborbital return, not only on the way up. */
@Mixin(targets = "com.heliosphere.rocketry.flight.LaunchCabin", remap = false)
public abstract class LandingWalkMixin {
    @Inject(method = "walking", at = @At("RETURN"), cancellable = true, remap = false)
    private static void heliosphere$landing(RocketEntity rocket, Entity rider, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || rocket == null || rider == null || rider.getVehicle() != rocket || rocket.isRemoved()) return;
        int phase = rocket.phase();
        if ((phase == RocketEntity.DESCENT || phase == RocketEntity.SUBORBITAL)
                && rocket.structure().parts().stream().anyMatch(p -> "crew_capsule".equals(p.spec()))) cir.setReturnValue(true);
    }
}
