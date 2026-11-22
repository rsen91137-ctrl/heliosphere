package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.foods.cuisine.CuisineEffects;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Warmth (food buff): the impact winter's frost does not reach a player who has it, as if dressed for the cold. The
 * disasters module asks {@code DisasterDefenses.insulated} (package-private, hence the target by name).
 */
@Mixin(targets = "com.heliosphere.core.disaster.DisasterDefenses", remap = false)
public abstract class DisasterDefensesMixin {
    @Inject(method = "insulated", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$warmth(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (CuisineEffects.has(player, CuisineEffects.WARMTH)) cir.setReturnValue(true);
    }
}
