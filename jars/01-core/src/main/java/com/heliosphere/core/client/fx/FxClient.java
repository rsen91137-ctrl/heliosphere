package com.heliosphere.core.client.fx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Client entry point of the effects engine: the screen flash and tint are drawn with the HUD. */
public final class FxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((graphics, delta) -> FxEngine.hud(graphics));
    }
}
