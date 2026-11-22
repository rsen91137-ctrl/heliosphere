package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.core.disaster.AcidMask;
import com.heliosphere.foods.cuisine.CuisineEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Acid ward (food buff): acid rain does not hurt, as with the acid mask on. Only where the damage is decided (the
 * server): the mask's clearer view through the acid fog, which the client also asks this for, stays the mask's.
 */
@Mixin(value = AcidMask.class, remap = false)
public abstract class AcidMaskMixin {
    @Inject(method = "protectedBy", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$acidWard(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && !entity.level().isClientSide() && CuisineEffects.has(entity, CuisineEffects.ACID_SHIELD)) cir.setReturnValue(true);
    }
}
