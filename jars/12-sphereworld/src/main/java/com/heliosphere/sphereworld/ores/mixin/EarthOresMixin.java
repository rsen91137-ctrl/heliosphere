package com.heliosphere.sphereworld.ores.mixin;

import com.heliosphere.sphereworld.gen.PlanetChunkGenerator;
import com.heliosphere.sphereworld.ores.EarthOres;
import com.heliosphere.sphereworld.terrain.Column;
import com.heliosphere.sphereworld.terrain.PlanetTerrain;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a rocky world's own ore veins, the Earth ores too (see EarthOres). */
@Mixin(PlanetChunkGenerator.class)
public abstract class EarthOresMixin {
    @org.spongepowered.asm.mixin.Shadow(remap = false) @org.spongepowered.asm.mixin.Final private String planet;

    @Inject(method = "placeVeins", at = @At("TAIL"), remap = false)
    private void heliosphere$earthOres(PlanetTerrain t, ChunkAccess chunk, ChunkPos cp, int[] tops, Column[] cols, RandomSource rand, CallbackInfo ci) {
        EarthOres.place(chunk, tops, RandomSource.create(rand.nextLong() ^ planet.hashCode() * 0x9E3779B97F4A7C15L));   // each world its own veins
    }
}
