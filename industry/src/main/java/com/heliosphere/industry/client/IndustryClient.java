package com.heliosphere.industry.client;

import com.heliosphere.core.Reg;
import com.heliosphere.core.block.CoreBlocks;
import com.heliosphere.industry.GenIds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class IndustryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // ore models draw a transparent crystal overlay on top of the vanilla stone texture
        for (String[] ore : GenIds.ORES) {
            BlockRenderLayerMap.putBlock(Reg.getBlock(ore[0] + "_ore"), ChunkSectionLayer.CUTOUT);
            BlockRenderLayerMap.putBlock(Reg.getBlock("deepslate_" + ore[0] + "_ore"), ChunkSectionLayer.CUTOUT);
        }
        BlockRenderLayerMap.putBlock(CoreBlocks.CABLE, ChunkSectionLayer.CUTOUT);
    }
}
