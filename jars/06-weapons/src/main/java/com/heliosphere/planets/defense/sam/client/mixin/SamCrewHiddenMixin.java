package com.heliosphere.planets.defense.sam.client.mixin;

import com.heliosphere.planets.defense.sam.SamVehicle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The crew of an air-defence vehicle is inside it: nobody sees a player sitting (or walking) on the roof. */
@Mixin(EntityRenderDispatcher.class)
public abstract class SamCrewHiddenMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void heliosphere$crewInside(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && entity.getVehicle() instanceof SamVehicle) cir.setReturnValue(false);
    }
}
