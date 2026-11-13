package com.heliosphere.rocketry.cabin.client;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.DVec;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.client.sky.ClientNav;
import com.heliosphere.rocketry.cabin.FlightTelemetry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The flight room seen from inside: the space sky is drawn from where the rocket is (the planet below, growing or
 * shrinking with altitude), and a small display shows altitude, speed and the rocket's coordinates. Everything
 * else is the ordinary game: you stand in a real room.
 */
public final class FlightRoomClient implements ClientModInitializer {
    private static volatile FlightTelemetry last = FlightTelemetry.OFF;
    private static volatile long stamp;
    private static boolean navSet;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FlightTelemetry.TYPE, (payload, ctx) -> {
            last = payload;
            stamp = System.currentTimeMillis();
            ctx.client().execute(FlightRoomClient::sync);
        });
        HudRenderCallback.EVENT.register((g, delta) -> hud(g));
    }

    /** Whether the local player is standing in a flight room right now. */
    public static boolean active() {
        return last.on() && System.currentTimeMillis() - stamp < 3000 && Minecraft.getInstance().player != null;
    }

    private static void sync() {
        if (active()) {
            if (!navSet) {
                ClientNav.set(new ClientNav.Source() {
                    @Override public DVec position(double t) { return observer(t); }
                    @Override public CelestialBody focus() { return body(); }
                });
                navSet = true;
            }
        } else if (navSet) {
            ClientNav.clear();
            navSet = false;
        }
    }

    private static CelestialBody body() {
        CelestialBody b = SolarSystem.get(last.body());
        return b != null ? b : SolarSystem.get("earth");
    }

    /**
     * Where the sky is drawn from: the planet sits ahead and below, its rim at the true horizon dip for the rocket's
     * altitude (level at the pad, dropping as you climb), its disc no wider than the sky can draw (55 degrees).
     */
    private static DVec observer(double t) {
        if (!active()) { Minecraft.getInstance().execute(FlightRoomClient::sync); }
        CelestialBody b = body();
        DVec centre = OrbitMath.position(b, t);
        double r = b.radiusKm, h = Math.max(0, last.altitude() / 1000.0);
        double dip = Math.acos(r / (r + h));
        double rho = Math.min(Math.asin(r / (r + h)), Math.toRadians(55));
        double dist = r / Math.sin(rho);
        double theta = rho + dip;                                                 // centre below the horizon, ahead
        double unit = centre.length() > 1e4 ? 1 : 1 / CelestialBody.AU_KM;        // positions in km, or in AU
        DVec toCentre = new DVec(0, -Math.sin(theta), Math.cos(theta));
        return centre.sub(toCentre.scale(dist * unit));
    }

    private static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui) {
            if (navSet && !active()) sync();
            return;
        }
        FlightTelemetry t = last;
        int w = mc.getWindow().getGuiScaledWidth();
        String alt = t.altitude() >= 1000 ? String.format("%.1f km", t.altitude() / 1000) : String.format("%.0f m", t.altitude());
        String spd = t.speed() >= 1000 ? String.format("%.2f km/s", t.speed() / 1000) : String.format("%.0f m/s", t.speed());
        Component line1 = Component.translatable("hud.heliosphere.flight_room", alt, spd);
        Component line2 = Component.literal(String.format("XYZ %.1f / %.1f / %.1f", t.x(), t.y(), t.z()));
        int x = w - 8, y0 = 44;                                       // under the story panel in the corner
        g.fill(x - 196, y0 - 4, x + 4, y0 + (t.hold() ? 36 : 24), 0x90101418);
        g.drawString(mc.font, line1, x - 192, y0, 0xFFBFE6FF);
        g.drawString(mc.font, line2, x - 192, y0 + 12, 0xFFE0E0E0);
        if (t.hold()) g.drawString(mc.font, Component.translatable("hud.heliosphere.flight_room_hold"), x - 192, y0 + 24, 0xFFFFD060);
    }
}
