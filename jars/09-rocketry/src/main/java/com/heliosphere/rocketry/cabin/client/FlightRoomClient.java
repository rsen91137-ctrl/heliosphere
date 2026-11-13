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
     * Where the sky is drawn from: straight above the planet at the rocket's altitude, so the planet lies below the
     * room (seen through the glass in the deck), shrinking as you climb. Its disc is kept within 38
     * degrees of radius: the space sky draws a planet no wider (the cruise starts at the same size).
     */
    private static DVec observer(double t) {
        if (!active()) { Minecraft.getInstance().execute(FlightRoomClient::sync); }
        CelestialBody b = body();
        DVec centre = OrbitMath.position(b, t);
        double r = b.radiusKm, h = Math.max(0, last.altitude() / 1000.0);
        double rho = Math.toRadians(38) * Math.asin(r / (r + h)) / (Math.PI / 2);   // shrinks as you climb, like the real one
        double unit = centre.length() > 1e4 ? 1 : 1 / CelestialBody.AU_KM;        // positions in km, or in AU
        // above the planet and toward the Sun (which sits on this sky's horizon): the day side faces the room
        DVec toSun = centre.scale(-1).normalize();
        double up = Math.toRadians(60);
        DVec dir = toSun.scale(Math.cos(up)).add(new DVec(0, Math.sin(up), 0)).normalize();
        return centre.add(dir.scale(r / Math.sin(rho) * unit));
    }

    private static net.minecraft.world.phys.Vec3 lastPos;
    private static long lastTime;
    private static double rideSpeed;

    /** Riding in the cabin: altitude, speed and the rocket's coordinates, in place of the old flight overlay. */
    private static void cabinHud(GuiGraphics g, Minecraft mc) {
        var r = RealCabin.rocket();
        if (r == null) return;
        long now = System.currentTimeMillis();
        net.minecraft.world.phys.Vec3 pos = r.position();
        if (lastPos != null && now > lastTime) rideSpeed = rideSpeed * 0.85 + pos.distanceTo(lastPos) / ((now - lastTime) / 1000.0) * 0.15;
        lastPos = pos; lastTime = now;
        int gx = r.getBlockX(), gz = r.getBlockZ();
        double ground = mc.level != null && mc.level.getChunkSource().hasChunk(gx >> 4, gz >> 4)
                ? mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz) : 64;
        double alt = Math.max(0, r.getY() - ground);
        String a = alt >= 1000 ? String.format("%.1f km", alt / 1000) : String.format("%.0f m", alt);
        String v = rideSpeed >= 1000 ? String.format("%.2f km/s", rideSpeed / 1000) : String.format("%.0f m/s", rideSpeed);
        int w = mc.getWindow().getGuiScaledWidth(), x = w - 8, y0 = 44;
        g.fill(x - 196, y0 - 4, x + 4, y0 + 24, 0x90101418);
        g.drawString(mc.font, Component.translatable("hud.heliosphere.flight_room", a, v), x - 192, y0, 0xFFBFE6FF);
        g.drawString(mc.font, Component.literal(String.format("XYZ %.1f / %.1f / %.1f", r.getX(), r.getY(), r.getZ())), x - 192, y0 + 12, 0xFFE0E0E0);
    }

    private static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (RealCabin.active() && !mc.options.hideGui) { cabinHud(g, mc); return; }
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
