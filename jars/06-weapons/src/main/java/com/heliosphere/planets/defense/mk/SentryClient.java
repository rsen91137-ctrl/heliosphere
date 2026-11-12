package com.heliosphere.planets.defense.mk;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class SentryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() { BlockEntityRenderers.register(Sentries.BLOCK_ENTITY, SentryRenderer::new); }
}
