package com.heliosphere.core.mixin.machine;

import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.core.machine.StackFix;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A stackable product leaves the machine without a tolerance ({@link StackFix}), so it stacks in the output slot. */
@Mixin(value = MachineBlockEntity.class, remap = false)
public abstract class MachineStackMixin {
    @Inject(method = "insertOutput", at = @At("HEAD"), remap = false)
    private void heliosphere$stackable(MachineType type, ItemStack stack, CallbackInfo ci) {
        StackFix.clean(stack);
        if (((MachineBlockEntity) (Object) this).getLevel() != null && ((MachineBlockEntity) (Object) this).getLevel().getGameTime() % 20 == 0)
            StackFix.clean((MachineBlockEntity) (Object) this);       // and what an older version left in the slots
    }
}
