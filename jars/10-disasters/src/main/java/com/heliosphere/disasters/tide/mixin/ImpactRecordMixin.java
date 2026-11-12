package com.heliosphere.disasters.tide.mixin;

import com.heliosphere.core.disaster.ImpactExpansionLedger;
import com.heliosphere.disasters.tide.TideHooks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every impact the released code records (primary and showers) also plays the new impact effect. */
@Mixin(value = ImpactExpansionLedger.class, remap = false)
public class ImpactRecordMixin {
    @Inject(method = "record", at = @At("HEAD"))
    private static void heliosphere$impact(ServerLevel level, int x, int y, int z, long tick, CallbackInfo ci) {
        TideHooks.recorded(level, x, y, z, tick);
    }
}
