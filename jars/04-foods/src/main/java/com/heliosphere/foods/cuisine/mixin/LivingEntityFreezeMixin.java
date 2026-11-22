package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.foods.cuisine.CuisineEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Warmth (food buff): no freezing in powder snow. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFreezeMixin {
    @Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
    private void heliosphere$warmth(CallbackInfoReturnable<Boolean> cir) {
        if (CuisineEffects.has((LivingEntity) (Object) this, CuisineEffects.WARMTH)) cir.setReturnValue(false);
    }
}
