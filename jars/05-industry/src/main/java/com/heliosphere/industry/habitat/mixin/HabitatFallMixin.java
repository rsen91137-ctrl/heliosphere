package com.heliosphere.industry.habitat.mixin;

import com.heliosphere.industry.habitat.HabitatRegistry;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Falls inside a habitat hurt as on Earth. */
@Mixin(LivingEntity.class)
public abstract class HabitatFallMixin {
    @Inject(method = "calculateFallDamage", at = @At("HEAD"))
    private void heliosphere$whose(double distance, float multiplier, CallbackInfoReturnable<Integer> cir) {
        HabitatRegistry.gravityOf((LivingEntity) (Object) this);
    }
}
