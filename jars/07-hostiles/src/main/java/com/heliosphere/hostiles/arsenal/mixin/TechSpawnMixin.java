package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.EnemyArsenal;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swarm drones never come alone. */
@Mixin(value = TechCombat.class, remap = false)
public abstract class TechSpawnMixin {
    @Inject(method = "spawn", at = @At("RETURN"))
    private static void heliosphere$spawned(ServerLevel level, String id, double x, double y, double z, LivingEntity target,
                                            CallbackInfoReturnable<TechMob> cir) {
        EnemyArsenal.spawned(level, id, x, y, z, target, cir.getReturnValue());
    }
}
