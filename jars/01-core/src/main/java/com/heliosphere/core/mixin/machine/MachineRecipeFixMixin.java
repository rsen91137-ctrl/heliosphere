package com.heliosphere.core.mixin.machine;

import com.heliosphere.core.machine.RecipeFixes;
import com.heliosphere.core.machine.MachineType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once every mod has added its machine recipes: {@link RecipeFixes}. */
@Mixin(value = MachineType.class, remap = false)
public abstract class MachineRecipeFixMixin {
    @Inject(method = "ensureLoaded", at = @At("TAIL"), remap = false)
    private static void heliosphere$fix(CallbackInfo ci) { RecipeFixes.apply(); }
}
