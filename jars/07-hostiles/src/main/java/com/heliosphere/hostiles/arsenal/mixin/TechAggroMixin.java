package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.Aggression;
import com.heliosphere.planets.tech.TechMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The machines press their attacks ({@link Aggression}): they notice players from 128 blocks instead of 72 and keep
 * after them to 160 instead of 100, close in when they cannot see their target, and shoot back at turrets.
 */
@Mixin(value = TechMob.class, remap = false)
public abstract class TechAggroMixin implements Aggression.Cooldown {
    @Shadow(remap = false) private int cooldown;

    @Override public int heliosphere$cooldown() { return cooldown; }
    @Override public void heliosphere$cooldown(int ticks) { cooldown = ticks; }

    /** A target further than this (squared) is dropped: 100 blocks in the released code. */
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 10000.0), remap = false)
    private double heliosphere$forget(double sq) { return Aggression.FORGET * Aggression.FORGET; }

    /** A player nearer than this (squared) is noticed: 72 blocks in the released code. */
    @ModifyConstant(method = "lambda$method_5773$0", constant = @Constant(doubleValue = 5184.0), remap = false)
    private double heliosphere$notice(double sq) { return Aggression.NOTICE * Aggression.NOTICE; }

    /** The fraction of its range at which a ground machine stops walking (the second 0.6 of the method; the first is the drones' bobbing). */
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 0.6, ordinal = 1), remap = false)
    private double heliosphere$standoff(double k) { return Aggression.standoff((TechMob) (Object) this, k); }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void heliosphere$aggression(CallbackInfo ci) { Aggression.tick((TechMob) (Object) this); }
}
