package com.heliosphere.industry.habitat.mixin;

import com.heliosphere.core.env.Environment;
import com.heliosphere.industry.habitat.HabitatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inside a running habitat field it is as in an oxygen bubble (breathable air, 20 °C, out of the storms), and an
 * entity standing in one has Earth's gravity.
 */
@Mixin(value = Environment.class, remap = false)
public abstract class HabitatEnvironmentMixin {
    @Inject(method = "insideBubble", at = @At("HEAD"), cancellable = true)
    private static void heliosphere$habitat(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (HabitatRegistry.contains(level, pos)) cir.setReturnValue(true);
    }

    @Inject(method = "gravity", at = @At("HEAD"), cancellable = true)
    private static void heliosphere$earthGravity(Level level, CallbackInfoReturnable<Double> cir) {
        if (HabitatRegistry.earthGravity(level)) cir.setReturnValue(1.0);
    }
}
