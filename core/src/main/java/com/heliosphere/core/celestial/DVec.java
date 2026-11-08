package com.heliosphere.core.celestial;

/** Immutable double-precision vector used for navigation-space positions (km). */
public record DVec(double x, double y, double z) {
    public static final DVec ZERO = new DVec(0, 0, 0);

    public DVec add(DVec o) { return new DVec(x + o.x, y + o.y, z + o.z); }
    public DVec sub(DVec o) { return new DVec(x - o.x, y - o.y, z - o.z); }
    public DVec scale(double s) { return new DVec(x * s, y * s, z * s); }
    public double dot(DVec o) { return x * o.x + y * o.y + z * o.z; }
    public DVec cross(DVec o) { return new DVec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public DVec normalize() { double l = length(); return l < 1e-12 ? ZERO : scale(1 / l); }
    public DVec lerp(DVec o, double t) { return new DVec(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }
}
