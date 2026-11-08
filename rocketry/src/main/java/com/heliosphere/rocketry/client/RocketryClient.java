package com.heliosphere.rocketry.client;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.DVec;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.client.sky.ClientNav;
import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.menu.RocketMenu;
import com.heliosphere.rocketry.net.RocketNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class RocketryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Rocketry.ROCKET, RocketRenderer::new);
        MenuScreens.register(RocketMenu.TYPE, RocketScreen::new);
        for (String id : new String[]{"nose_cone", "rocket_fin", "landing_leg", "escape_tower", "rocket_frame", "heat_shield", "heat_shield_t2"})
            BlockRenderLayerMap.putBlock(Rocketry.PARTS.get(id), ChunkSectionLayer.CUTOUT);
        ClientPlayNetworking.registerGlobalReceiver(RocketNet.Report.TYPE, (p, ctx) -> ctx.client().execute(() -> RocketScreen.latest = p));
        ClientPlayNetworking.registerGlobalReceiver(RocketNet.Transit.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (p.duration() <= 0) { ClientNav.clear(); return; }
            CelestialBody from = SolarSystem.get(p.from()), to = SolarSystem.get(p.to());
            long start = p.start();
            int dur = p.duration();
            ClientNav.set(new ClientNav.Source() {
                @Override public DVec position(double days) {
                    long now = Minecraft.getInstance().level == null ? start : Minecraft.getInstance().level.getGameTime();
                    double t = Math.max(0, Math.min(1, (now - start) / (double) dur));
                    double s = t * t * (3 - 2 * t);
                    DVec a = OrbitMath.position(from, days), b = OrbitMath.position(to, days);
                    DVec offA = a.normalize().scale(-(from.radiusKm * 1.6 + 500)), offB = b.normalize().scale(-(to.radiusKm * 1.6 + 500));
                    return a.add(offA).lerp(b.add(offB), s);
                }
                @Override public CelestialBody focus() { return to; }
            });
        }));
    }
}
