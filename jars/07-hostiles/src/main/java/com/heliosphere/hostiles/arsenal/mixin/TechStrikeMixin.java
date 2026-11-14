package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.StrikeCraft;
import com.heliosphere.planets.tech.TechMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The strike aircraft fly their own way: after the released machine logic has run, {@link StrikeCraft} sets their course.
 * At every return: the released tick returns early when no player is within 72 blocks, which is where these aircraft fight.
 */
@Mixin(value = TechMob.class, remap = false)
public abstract class TechStrikeMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void heliosphere$strike(CallbackInfo ci) {
        StrikeCraft.tick((TechMob) (Object) this);
        com.heliosphere.hostiles.arsenal.Underground.tick((TechMob) (Object) this);   // digging, sabotage
    }
}
