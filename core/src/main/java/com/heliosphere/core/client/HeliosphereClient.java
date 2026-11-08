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
        ClientPlayNetworking.registerGlobalReceiver(com.heliosphere.core.story.Story.Payload.TYPE, (payload, ctx) -> com.heliosphere.core.story.Story.client = payload);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tctx, type, lines) -> {
            com.heliosphere.core.env.HelioComponents.SuitRating r = stack.get(com.heliosphere.core.env.HelioComponents.SUIT);
            if (r != null) {
                lines.add(net.minecraft.network.chat.Component.literal("§7密封等级 " + r.tier() + " · 防护温度 §b" + r.minTempC() + "°C §7~ §c" + r.maxTempC() + "°C"));
                lines.add(net.minecraft.network.chat.Component.literal("§8须穿齐四件才能密封；防护温度取四件中最窄的范围"));
            }
            com.heliosphere.core.env.HelioComponents.Oxygen ox = stack.get(com.heliosphere.core.env.HelioComponents.OXYGEN);
            if (ox != null) lines.add(net.minecraft.network.chat.Component.literal("§7氧气 " + ox.amount() / 20 + " / " + ox.capacity() / 20 + " 秒"));
        });
    }
}
