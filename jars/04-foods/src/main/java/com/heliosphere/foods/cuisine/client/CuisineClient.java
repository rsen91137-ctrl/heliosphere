package com.heliosphere.foods.cuisine.client;

import com.heliosphere.foods.cuisine.Cuisine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Client side of the food expansion: cut-out rendering for the crops and the see-through blocks, the heat line on cookware screens. */
public final class CuisineClient implements ClientModInitializer {
    /** Blocks besides the crops whose models have see-through parts: the low cookware and the planter. */
    private static final List<String> CUTOUT = List.of("wok", "cooking_pot", "steamer", "fermenter", "hydroponic_planter");

    @Override
    public void onInitializeClient() {
        for (Block crop : Cuisine.CROPS.values()) BlockRenderLayerMap.putBlock(crop, ChunkSectionLayer.CUTOUT);
        for (String id : CUTOUT) {
            Block block = Cuisine.block(id);
            if (block != null) BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.CUTOUT);
        }
        HeatHint.register();
    }
}
