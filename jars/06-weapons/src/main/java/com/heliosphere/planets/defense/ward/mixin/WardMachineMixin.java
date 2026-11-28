package com.heliosphere.planets.defense.ward.mixin;

import com.heliosphere.planets.defense.ward.Wards;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A machine in a ward field (see WardGeneratorBlockEntity). In a malfunction it does nothing but the basics of being
 * alive (status effects, being hurt) and sags a little: no moving, no aiming, no firing. Slowed, a drone loses a fifth
 * of its speed (Slowness only reaches walkers).
 */
@Mixin(value = TechMob.class, remap = false)
public abstract class WardMachineMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void heliosphere$malfunction(CallbackInfo ci) {
        TechMob self = (TechMob) (Object) this;
        if (self.level().isClientSide() || !Wards.malfunctioning(self)) return;
        self.baseTick();
        self.getNavigation().stop();
        Vec3 v = self.getDeltaMovement();
        self.setDeltaMovement(v.x * 0.5, self.isNoGravity() ? -0.04 : Math.max(v.y - 0.08, -0.6), v.z * 0.5);
        self.move(MoverType.SELF, self.getDeltaMovement());
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void heliosphere$slowed(CallbackInfo ci) {
        TechMob self = (TechMob) (Object) this;
        if (self.level().isClientSide() || !self.isNoGravity() || !Wards.slowed(self)) return;
        Vec3 v = self.getDeltaMovement();
        self.setDeltaMovement(v.x * 0.8, v.y, v.z * 0.8);
    }

    @Inject(method = "fire", at = @At("HEAD"), cancellable = true)
    private void heliosphere$noFire(ServerLevel level, LivingEntity target, Vec3 aim, CallbackInfo ci) {
        if (Wards.malfunctioning((TechMob) (Object) this)) ci.cancel();
    }
}
