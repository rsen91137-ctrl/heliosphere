package com.heliosphere.sphereworld.giant.mixin;

import com.heliosphere.core.env.Environment;
import com.heliosphere.sphereworld.giant.GiantClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the giant planets the temperature depends on depth: freezing above the clouds, thousands of degrees below. */
@Mixin(value = Environment.class, remap = false)
public abstract class GiantTemperatureMixin {
    @Inject(method = "temperatureC", at = @At("RETURN"), cancellable = true, remap = false)
    private static void heliosphere$depth(Level level, BlockPos pos, CallbackInfoReturnable<Double> cir) {
        String planet = GiantClimate.planet(level);
        if (planet != null) cir.setReturnValue(GiantClimate.celsius(planet, pos.getY(), cir.getReturnValue()));
    }
}
