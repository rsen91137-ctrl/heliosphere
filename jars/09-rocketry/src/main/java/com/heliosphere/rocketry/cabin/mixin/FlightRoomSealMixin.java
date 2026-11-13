package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.FlightRoom;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The flight room is pressurised and heated like the capsule it stands for: no vacuum, cold or radiation inside. */
@Mixin(targets = "com.heliosphere.rocketry.flight.CabinProtection", remap = false)
public class FlightRoomSealMixin {
    @Inject(method = "sealed", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$room(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && FlightRoom.inAnyRoom(entity.level(), entity.blockPosition())) cir.setReturnValue(true);
    }
}
