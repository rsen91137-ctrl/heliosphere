package com.heliosphere.industry.habitat.mixin;

import com.heliosphere.industry.habitat.HabitatRegistry;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An entity inside a running habitat field falls as on Earth: its plain gravity, before any planet scales it. */
@Mixin(Entity.class)
public abstract class HabitatGravityMixin {
    @Shadow protected abstract double getDefaultGravity();
    @Shadow public abstract boolean isNoGravity();

    @Inject(method = "getGravity", at = @At("HEAD"), cancellable = true)
    private void heliosphere$earth(CallbackInfoReturnable<Double> cir) {
        if (HabitatRegistry.contains((Entity) (Object) this)) cir.setReturnValue(isNoGravity() ? 0.0 : getDefaultGravity());
    }
}
