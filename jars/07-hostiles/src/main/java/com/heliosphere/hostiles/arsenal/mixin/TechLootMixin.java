package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.TechLoot;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A destroyed machine drops what {@link TechLoot} gives for its level instead of the same salvage for all. */
@Mixin(value = TechMob.class, remap = false)
public abstract class TechLootMixin {
    @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"), cancellable = true)
    private void heliosphere$loot(ServerLevel level, DamageSource source, boolean byPlayer, CallbackInfo ci) {
        TechLoot.drop((TechMob) (Object) this, level, byPlayer);
        ci.cancel();
    }
}
