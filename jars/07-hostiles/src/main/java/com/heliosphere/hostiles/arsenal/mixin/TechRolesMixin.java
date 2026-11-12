package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.EnemyRoster;
import com.heliosphere.planets.tech.TechCombat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Swaps the released roster for the rebalanced and extended one before anything reads it, so every new
 * machine gets its entity type, attributes, creative deployer, spawn schedule and renderer the way the
 * released ones do.
 */
@Mixin(value = TechCombat.class, remap = false)
public abstract class TechRolesMixin {
    @Shadow @Final @Mutable public static List<TechCombat.Role> ROLES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void heliosphere$roster(CallbackInfo ci) {
        ROLES = EnemyRoster.extend(ROLES);
    }
}
