package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.planets.defense.sam.SamSites;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class SamClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(SamSites.MISSILE, MissileRenderer::new);
        BlockEntityRenderers.register(SamSites.BLOCK_ENTITY, SamRenderer::new);
        MissileTrails.init();
        EntityRendererRegistry.register(SamSites.SEAT, net.minecraft.client.renderer.entity.NoopRenderer::new);
        EntityRendererRegistry.register(SamSites.DRIVE, SamDriveRenderer::new);
        SamOperatorClient.init();
        com.heliosphere.planets.weapon.guided.client.LockScreenClient.init();
    }
}
