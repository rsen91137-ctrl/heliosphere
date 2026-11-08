package com.heliosphere.core.client.sky;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.DVec;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;

/**
 * Where the camera is in navigation space while in the space dimension. The rocketry module updates
 * this during transfers so the sky shows the real view along the trajectory.
 */
public final class ClientNav {
    public interface Source { DVec position(double days); CelestialBody focus(); }

    private static Source source = null;

    private ClientNav() {}

    public static void set(Source s) { source = s; }
    public static void clear() { source = null; }

    /** Heliocentric observer position (km). Default: low Earth orbit on the day side. */
    public static DVec observer(double days) {
        if (source != null) return source.position(days);
        DVec earth = OrbitMath.position(SolarSystem.EARTH, days);
        return earth.add(earth.normalize().scale(-(SolarSystem.EARTH.radiusKm + 2000)));
    }

    public static CelestialBody focus() { return source == null ? SolarSystem.EARTH : source.focus(); }
}
