package com.heliosphere.core.mixin;

import com.heliosphere.core.env.Environment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Falls hurt in proportion to impact energy, which scales with local gravity. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallMixin {
    @ModifyVariable(method = "calculateFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double heliosphere$scaleFall(double distance) {
        double g = Environment.gravity(((LivingEntity) (Object) this).level());
        return g < 1.0 ? distance * g : distance;
    }
}
