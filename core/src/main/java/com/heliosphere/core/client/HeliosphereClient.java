package com.heliosphere.core.client;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.client.hud.EnvironmentHud;
import com.heliosphere.core.client.screen.MachineScreen;
import com.heliosphere.core.client.sky.PlanetTextures;
import com.heliosphere.core.client.sky.SpaceSky;
import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.net.HelioNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public final class HeliosphereClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpaceSky.initPipelines();
        MenuScreens.register(MachineMenu.TYPE, MachineScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(HelioNetwork.PlanetMapPayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> PlanetTextures.setBaked(payload.body(), payload.width(), payload.height(), payload.rgb())));
        HudElementRegistry.addLast(Heliosphere.id("environment"), EnvironmentHud::render);
    }
}
