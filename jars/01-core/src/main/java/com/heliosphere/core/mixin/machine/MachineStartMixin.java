package com.heliosphere.core.mixin.machine;

import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.Startable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The start-button state of a machine ({@link Startable}), kept with the machine when the world is saved. */
@Mixin(value = MachineBlockEntity.class, remap = false)
public abstract class MachineStartMixin implements Startable {
    @Unique private boolean heliosphere$armed;
    @Unique private int heliosphere$locked = -1;

    @Override public boolean heliosphere$armed() { return heliosphere$armed; }
    @Override public void heliosphere$arm(boolean on) {
        heliosphere$armed = on;
        if (!on) heliosphere$locked = -1;
        ((MachineBlockEntity) (Object) this).setChanged();
    }
    @Override public int heliosphere$locked() { return heliosphere$locked; }
    @Override public void heliosphere$lock(int index) { heliosphere$locked = index; }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void heliosphere$save(ValueOutput out, CallbackInfo ci) {
        if (heliosphere$armed) { out.putBoolean("heliosphere_armed", true); out.putInt("heliosphere_locked", heliosphere$locked); }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void heliosphere$load(ValueInput in, CallbackInfo ci) {
        heliosphere$armed = in.getBooleanOr("heliosphere_armed", false);
        heliosphere$locked = heliosphere$armed ? in.getIntOr("heliosphere_locked", -1) : -1;
    }
}
