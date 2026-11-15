package com.heliosphere.core.mixin.machine;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Maximum health may go past vanilla's 1024: the machine bosses were given 1500 to 4000 and more, but every value
 * above 1024 was silently cut back to it, so the colossus had no more health than a titan. Now up to a million.
 */
@Mixin(RangedAttribute.class)
public abstract class HealthCapMixin {
    @Shadow @Final @Mutable private double maxValue;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void heliosphere$raiseHealthCap(String id, double def, double min, double max, CallbackInfo ci) {
        if ("attribute.name.max_health".equals(id)) maxValue = 1_000_000;
    }
}
