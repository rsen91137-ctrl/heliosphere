package com.heliosphere.core.mixin.grid;

import com.heliosphere.core.machine.EnergyNet;
import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.core.machine.GridNet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Power goes through {@link GridNet}: the whole cable network is searched, not just its first stretch. */
@Mixin(value = EnergyNet.class, remap = false)
public abstract class EnergyNetMixin {
    @Inject(method = "push", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heliosphere$grid(Level level, BlockPos pos, EnergyNode source, long amount, CallbackInfo ci) {
        GridNet.push(level, pos, source, amount);
        ci.cancel();
    }
}
