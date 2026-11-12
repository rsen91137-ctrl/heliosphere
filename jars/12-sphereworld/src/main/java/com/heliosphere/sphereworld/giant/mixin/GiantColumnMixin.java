package com.heliosphere.sphereworld.giant.mixin;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.sphereworld.gen.PlanetChunkGenerator;
import com.heliosphere.sphereworld.giant.GiantDepths;
import com.heliosphere.sphereworld.terrain.GasGiantTerrain;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the giant planets their full depth and fills it (see GiantDepths). */
@Mixin(PlanetChunkGenerator.class)
public abstract class GiantColumnMixin {
    @Shadow(remap = false) @Final private String planet;
    @Shadow(remap = false) @Final private CelestialBody body;

    private boolean heliosphere$giant() { return body != null && body.gasGiant; }

    @Inject(method = "getMinY", at = @At("HEAD"), cancellable = true)
    private void heliosphere$minY(CallbackInfoReturnable<Integer> cir) { if (heliosphere$giant()) cir.setReturnValue(GiantDepths.MIN_Y); }

    @Inject(method = "getGenDepth", at = @At("HEAD"), cancellable = true)
    private void heliosphere$depth(CallbackInfoReturnable<Integer> cir) { if (heliosphere$giant()) cir.setReturnValue(GiantDepths.HEIGHT); }

    @Inject(method = "fillGas", at = @At("RETURN"), remap = false)
    private void heliosphere$depths(GasGiantTerrain terrain, ChunkAccess chunk, CallbackInfoReturnable<ChunkAccess> cir) {
        GiantDepths.fill(planet, chunk);
    }
}
