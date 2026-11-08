package com.heliosphere.core.world;

import com.heliosphere.core.celestial.CelestialBody;

/**
 * Maps a planet's surface onto block coordinates with an equirectangular (latitude/longitude) layout.
 * <ul>
 *   <li>X spans one full circumference: longitude = x / C * 360°, wrapping east-west.</li>
 *   <li>Z spans pole to pole: latitude = -z / (C/2) * 180°, so north is -Z like vanilla.</li>
 *   <li>Past a pole the map reflects: you come out at longitude + 180° heading the other way,
 *       which is how walking over a real pole works.</li>
 * </ul>
 */
public final class PlanetGeometry {
    public final double circumference;

    public PlanetGeometry(double circumference) { this.circumference = circumference; }

    public static PlanetGeometry of(CelestialBody body) { return new PlanetGeometry(body.circumferenceBlocks()); }

    public double halfWidth() { return circumference / 2.0; }
    public double poleZ() { return circumference / 4.0; }

    public double lonToX(double lonDeg) { return lonDeg / 360.0 * circumference; }
    public double latToZ(double latDeg) { return -latDeg / 180.0 * (circumference / 2.0); }

    /** Returns {latDeg, lonDeg} for a block position, reflecting across the poles. */
    public double[] latLon(double x, double z) {
        double lat = -z / (circumference / 2.0) * 180.0;
        double lon = x / circumference * 360.0;
        // fold latitude into [-90, 90] (reflection across poles flips longitude by 180)
        lat = ((lat + 90) % 360 + 360) % 360 - 90; // now in [-90, 270)
        if (lat > 90) { lat = 180 - lat; lon += 180; }
        lon = ((lon + 180) % 360 + 360) % 360 - 180;
        return new double[]{lat, lon};
    }

    /** Unit vector on the sphere (x toward lon 0, y north, z toward lon -90). */
    public static void unit(double latDeg, double lonDeg, double[] out) {
        double la = Math.toRadians(latDeg), lo = Math.toRadians(lonDeg);
        double c = Math.cos(la);
        out[0] = c * Math.cos(lo);
        out[1] = Math.sin(la);
        out[2] = -c * Math.sin(lo);
    }

    /** Great-circle angle (radians) between two lat/lon points. */
    public static double angle(double lat1, double lon1, double lat2, double lon2) {
        double[] a = new double[3], b = new double[3];
        unit(lat1, lon1, a);
        unit(lat2, lon2, b);
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.acos(Math.max(-1, Math.min(1, d)));
    }

    /**
     * Where a position outside the canonical map should be moved to, or null if it is inside.
     * Result is {x, z, yawDelta}.
     */
    public double[] wrap(double x, double z) {
        double half = halfWidth(), pole = poleZ();
        double nx = x, nz = z, yaw = 0;
        boolean moved = false;
        if (nz < -pole) { nz = -2 * pole - nz; nx += half; yaw = 180; moved = true; }
        else if (nz > pole) { nz = 2 * pole - nz; nx += half; yaw = 180; moved = true; }
        if (nx >= half || nx < -half) {
            nx = ((nx + half) % circumference + circumference) % circumference - half;
            moved = true;
        }
        return moved ? new double[]{nx, nz, yaw} : null;
    }
}
