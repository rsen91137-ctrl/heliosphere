package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.planets.defense.sam.SamSites;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class SamClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(SamSites.MISSILE, MissileRenderer::new);
        MissileTrails.init();
        EntityRendererRegistry.register(SamSites.SEAT, net.minecraft.client.renderer.entity.NoopRenderer::new);
        EntityRendererRegistry.register(SamSites.DRIVE, net.minecraft.client.renderer.entity.NoopRenderer::new);
        EntityRendererRegistry.register(SamSites.VEHICLE, SamVehicleRenderer::new);
        SamOperatorClient.init();
        SamEngineSound.init();
        com.heliosphere.planets.weapon.guided.client.LockScreenClient.init();
        com.heliosphere.planets.gear.client.JetpackClient.init();
    }
}
