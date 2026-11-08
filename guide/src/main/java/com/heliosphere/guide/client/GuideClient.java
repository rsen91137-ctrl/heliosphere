package com.heliosphere.guide.client;

import com.heliosphere.guide.Guide;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GuideClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Guide.OpenScroll.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ctx.client().setScreen(new ScrollScreen(p.done()))));
    }
}
