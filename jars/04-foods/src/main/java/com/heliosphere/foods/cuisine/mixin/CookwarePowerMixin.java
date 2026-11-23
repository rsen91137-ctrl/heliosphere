package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.foods.cuisine.CookwareBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cookware that never uses power (the wok, pot and steamer cook on heat, the fermentation crock needs none) takes none
 * from the grid. As standard processors they would each soak up 40,000 FE from a cable they touch, ahead of the
 * batteries (the grid feeds machines first), and never spend it.
 */
@Mixin(value = MachineBlockEntity.class, remap = false)
public abstract class CookwarePowerMixin {
    @Inject(method = "canReceive", at = @At("HEAD"), cancellable = true, remap = false)
    private void heliosphere$unpoweredCookware(CallbackInfoReturnable<Boolean> cir) {
        if (((MachineBlockEntity) (Object) this).getBlockState().getBlock() instanceof CookwareBlock c && !c.usesPower) cir.setReturnValue(false);
    }
}
