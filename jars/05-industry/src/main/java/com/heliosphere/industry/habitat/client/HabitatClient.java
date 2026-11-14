package com.heliosphere.industry.habitat.client;

import com.heliosphere.industry.habitat.Habitat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class HabitatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.putBlock(Habitat.PANEL, ChunkSectionLayer.TRANSLUCENT);
        com.heliosphere.industry.habitat.HabitatRegistry.viewer = () -> net.minecraft.client.Minecraft.getInstance().player;
    }
}
