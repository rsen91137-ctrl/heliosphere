package com.heliosphere.core.mixin;

import com.heliosphere.core.env.Environment;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scales every entity's gravity by the gravity of the planet it stands on. */
@Mixin(Entity.class)
public abstract class EntityGravityMixin {
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void heliosphere$planetGravity(CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        double g = Environment.gravity(self.level());
        // spin-gravity in pressurised cabins and stations in orbit
        if (g < 0.2 && com.heliosphere.core.env.Planets.isSpace(self.level()) && Environment.insideBubble(self.level(), self.blockPosition())) g = 1.0;
        if (g != 1.0) cir.setReturnValue(cir.getReturnValue() * g);
    }
}
