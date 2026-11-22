package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.foods.cuisine.CuisineEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nourished (food buff): running, jumping, fighting and digging do not wear the hunger bar down. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void heliosphere$nourished(float exhaustion, CallbackInfo ci) {
        if (CuisineEffects.has((Player) (Object) this, CuisineEffects.NOURISHED)) ci.cancel();
    }
}
