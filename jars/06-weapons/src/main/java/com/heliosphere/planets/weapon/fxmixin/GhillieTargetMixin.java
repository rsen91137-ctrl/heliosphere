package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.armor.ArmorEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crouching player in a full ghillie suit cannot be picked as a target from more than 10 blocks. */
@Mixin(Mob.class)
public abstract class GhillieTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void heliosphere$ghillie(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (target != null && self instanceof Enemy && ArmorEffects.hidden(self, target)) ci.cancel();
    }
}
