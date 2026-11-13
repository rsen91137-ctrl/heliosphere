package com.heliosphere.rocketry.client;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.client.sky.SkyHooks;

/**
 * The view out of the flight deck's windows above the ground snapshot: the same scene the old launch sequence drew
 * behind the rocket (the planet's landscape in layers, clouds, haze, the curve of the horizon and the sky turning to
 * space), fed with the rocket's altitude and position instead of the camera's. Published every frame while the crew
 * is on the deck.
 */
public final class DeckSky {
    private DeckSky() {}

    /** Shows the scene for a rocket {@code altitude} metres above {@code body} at ({@code x}, {@code z}). */
    public static void publish(CelestialBody body, double altitude, double x, double z) {
        AscentScene a = AscentScene.INSTANCE;
        boolean earth = body == null || "earth".equals(body.id);
        double pressure = earth ? 1 : body.pressureBar;
        float day = (float) FlightCinematic.clamp(0.5 + 1.6 * Math.cos(SkyHooks.sunAngle()));
        float space = earth ? (float) FlightCinematic.smooth((altitude - 6000) / 64000) : 0f;
        a.altitude = altitude;
        a.radius = (body != null && body.radiusKm > 10 ? body.radiusKm : 6371) * 1000;
        a.originX = x;
        a.originZ = z;
        a.earthlike = earth;
        a.air = pressure >= 0.001;
        a.space = space;
        a.daylight = earth ? day : 1f;
        if (earth || body == null) { a.tintR = 1; a.tintG = 1; a.tintB = 1; }
        else {
            a.tintR = 0.35f + 0.65f * (body.color >> 16 & 255) / 255f;
            a.tintG = 0.35f + 0.65f * (body.color >> 8 & 255) / 255f;
            a.tintB = 0.35f + 0.65f * (body.color & 255) / 255f;
        }
        float haze = (1 - space) * (0.2f + 0.8f * a.daylight);
        if (earth) { a.hazeR = 0.74f * haze; a.hazeG = 0.84f * haze; a.hazeB = haze; }
        else { a.hazeR = a.tintR * 0.8f; a.hazeG = a.tintG * 0.8f; a.hazeB = a.tintB * 0.8f; }
        float fog = earth ? (float) FlightCinematic.smooth(altitude / 400) : 0f;
        SkyHooks.publish(a, space, fog, a.hazeR, a.hazeG, a.hazeB);
    }
}
