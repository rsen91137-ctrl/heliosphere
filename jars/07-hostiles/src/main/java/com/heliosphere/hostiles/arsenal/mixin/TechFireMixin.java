package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.EnemyArsenal;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** New weapons take over the attack; released weapons keep theirs and get their follow-ups added. */
@Mixin(value = TechMob.class, remap = false)
public abstract class TechFireMixin {
    @Inject(method = "fire", at = @At("HEAD"), cancellable = true)
    private void heliosphere$fire(ServerLevel level, LivingEntity target, Vec3 aim, CallbackInfo ci) {
        if (EnemyArsenal.fire((TechMob) (Object) this, level, target, aim)) ci.cancel();
    }

    @Inject(method = "fire", at = @At("TAIL"))
    private void heliosphere$followUp(ServerLevel level, LivingEntity target, Vec3 aim, CallbackInfo ci) {
        EnemyArsenal.followUp((TechMob) (Object) this, level, target, aim);
    }
}
