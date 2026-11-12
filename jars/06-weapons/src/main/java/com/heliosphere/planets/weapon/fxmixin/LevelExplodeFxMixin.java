package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.fx.GunBurst;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every explosion passes through here; GunBurst decides whether a gun's shot caused it. */
@Mixin(Level.class)
public abstract class LevelExplodeFxMixin {
    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V", at = @At("RETURN"))
    private void heliosphere$burst(Entity source, double x, double y, double z, float power, Level.ExplosionInteraction mode, CallbackInfo ci) {
        if ((Object) this instanceof ServerLevel level) GunBurst.exploded(level, source, x, y, z, power);
    }
}
