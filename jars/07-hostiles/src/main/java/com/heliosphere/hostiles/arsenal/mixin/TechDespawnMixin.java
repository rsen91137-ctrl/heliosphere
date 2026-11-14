package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.planets.tech.TechMob;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Machines are registered as ordinary monsters, so vanilla despawning used to make them vanish on their own: in
 * daylight any drone more than 32 blocks from a player could disappear at random, and anything past 128 blocks at
 * once. Fortress and siege machines now never despawn; other machines only when they are far beyond sight.
 */
@Mixin(Mob.class)
public class TechDespawnMixin {
    private static final double FAR = 176 * 176;
    private static final double STRIKE_FAR = (com.heliosphere.hostiles.arsenal.StrikeCraft.ACQUIRE + 60) * (com.heliosphere.hostiles.arsenal.StrikeCraft.ACQUIRE + 60);

    @Inject(method = "removeWhenFarAway", at = @At("HEAD"), cancellable = true)
    private void heliosphere$machines(double distSqr, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof TechMob m)) return;
        if (m.getTags().contains("heliosphere.fortress") || m.getTags().contains("heliosphere.siege") || m.hasCustomName()) cir.setReturnValue(false);
        else if (com.heliosphere.hostiles.arsenal.StrikeCraft.is(m)) cir.setReturnValue(distSqr > STRIKE_FAR);   // they fight from 200-330 blocks out
        else cir.setReturnValue(distSqr > FAR);
    }
}
