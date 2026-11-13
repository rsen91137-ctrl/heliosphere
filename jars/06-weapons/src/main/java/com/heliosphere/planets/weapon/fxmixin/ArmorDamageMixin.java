package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.armor.ArmorEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Special armour: damage by kind (see ArmorEffects), and a full hazmat suit keeps poison, wither and radiation out. */
@Mixin(LivingEntity.class)
public abstract class ArmorDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float heliosphere$armour(float amount, ServerLevel level, DamageSource source) {
        return ArmorEffects.reduce((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void heliosphere$hazmat(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        Holder<?> e = effect.getEffect();
        if ((e.equals(MobEffects.POISON) || e.equals(MobEffects.WITHER) || e.equals(com.heliosphere.planets.weapon.nuke.NukeContent.RADIATION))
                && ArmorEffects.radiationProof((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }
}
