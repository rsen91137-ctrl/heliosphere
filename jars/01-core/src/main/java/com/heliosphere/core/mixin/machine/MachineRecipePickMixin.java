package com.heliosphere.core.mixin.machine;

import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.core.machine.Startable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Which recipe a machine runs. The released machines took the first recipe whose inputs were present, so a cheap
 * recipe whose inputs are part of a bigger one (three steel plates for a nozzle bell inside the eight of a habitat
 * emitter, the turbopump inside the portable sentry...) always won and the bigger one could never be made; and of
 * recipes told apart only by where the machine stands (the planet core drill), only the first was ever tried. Now the
 * most specific recipe wins: one whose condition holds where the machine stands, then the one using the most kinds of
 * input, then the most items, then the earlier. A machine with a start button ({@link Startable}) chooses only when
 * it is pressed, and keeps to that recipe until its materials run out.
 */
@Mixin(value = MachineBlockEntity.class, remap = false)
public abstract class MachineRecipePickMixin {
    @Shadow(remap = false)
    private boolean matches(MachineRecipe recipe, MachineType type) { throw new AssertionError(); }

    @Inject(method = "findRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void heliosphere$mostSpecific(MachineType type, CallbackInfoReturnable<MachineRecipe> cir) {
        MachineBlockEntity self = (MachineBlockEntity) (Object) this;
        List<MachineRecipe> list = type.recipes();
        Startable gate = Startable.gated(type) ? (Startable) self : null;
        if (gate != null) {                                         // waits for its start button
            if (!gate.heliosphere$armed()) { cir.setReturnValue(null); return; }
            int locked = gate.heliosphere$locked();
            if (locked >= 0) {                                       // the recipe locked in by the press, while its materials last
                if (locked < list.size() && matches(list.get(locked), type)) cir.setReturnValue(list.get(locked));
                else { gate.heliosphere$arm(false); cir.setReturnValue(null); }
                return;
            }
        }
        MachineRecipe best = null;
        long bestKey = Long.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            MachineRecipe r = list.get(i);
            if (!matches(r, type)) continue;
            boolean here = r.cond == null || self.getLevel() == null || r.cond.check(self.getLevel(), self.getBlockPos()) == null;
            int total = 0;
            for (MachineRecipe.Input in : r.inputs) total += in.count();
            long key = ((here ? 1L : 0L) << 48) | ((long) Math.min(r.inputs.size(), 0xFF) << 40)
                    | ((long) Math.min(total, 0xFFFFFF) << 16) | (0xFFFF - Math.min(i, 0xFFFF));
            if (key > bestKey) { bestKey = key; best = r; }
        }
        if (gate != null) {
            if (best == null) gate.heliosphere$arm(false);                // nothing to make: the press is spent
            else gate.heliosphere$lock(list.indexOf(best));
        }
        cir.setReturnValue(best);
    }
}
