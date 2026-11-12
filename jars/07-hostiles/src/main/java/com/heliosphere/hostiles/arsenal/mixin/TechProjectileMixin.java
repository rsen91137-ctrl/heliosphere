package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.Missiles;
import com.heliosphere.planets.tech.TechProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Marks the round being updated, so its blast (see Missiles) knows who fired it; lets a seeker pick a target. */
@Mixin(TechProjectile.class)
public abstract class TechProjectileMixin implements Missiles.Round {
    @Shadow(remap = false) private UUID owner;
    @Shadow(remap = false) private UUID target;

    @Override public UUID heliosphere$owner() { return owner; }
    @Override public UUID heliosphere$target() { return target; }
    @Override public void heliosphere$target(UUID id) { target = id; }

    @Inject(method = "tick", at = @At("HEAD"))
    private void heliosphere$begin(CallbackInfo ci) { Missiles.begin((TechProjectile) (Object) this); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void heliosphere$end(CallbackInfo ci) { Missiles.end(); }
}
