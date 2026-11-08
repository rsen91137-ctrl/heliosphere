package com.heliosphere.sphereworld.terrain;

/** A named circular region on a sphere, located at real coordinates. */
public final class Region {
    public final String name;
    public final double lat, lon, radiusKm;
    final double cx, cy, cz;

    public Region(String name, double lat, double lon, double radiusKm) {
        this.name = name; this.lat = lat; this.lon = lon; this.radiusKm = radiusKm;
        double la = Math.toRadians(lat), lo = Math.toRadians(lon), c = Math.cos(la);
        cx = c * Math.cos(lo); cy = Math.sin(la); cz = -c * Math.sin(lo);
    }

    /** Distance from the region centre in units of its radius (angle * planetRadius / radius). */
    public double x(double px, double py, double pz, double planetRadiusKm) {
        double d = px * cx + py * cy + pz * cz;
        double ang = Math.acos(Math.max(-1, Math.min(1, d)));
        return ang * planetRadiusKm / radiusKm;
    }

    public static double smoothstep(double e0, double e1, double v) {
        double t = (v - e0) / (e1 - e0);
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
