package com.heliosphere.core.celestial;

import java.util.ArrayList;
import java.util.List;

/**
 * Circular-orbit ephemeris and patched-conic mission planning.
 * Positions are heliocentric, in km, in a frame where +X is the vernal equinox,
 * +Y is ecliptic north and -Z is 90° ahead of +X (so it maps directly onto Minecraft axes).
 */
public final class OrbitMath {
    /** Days between J2000.0 and 2026-01-01, the date a new world starts on. */
    public static final double EPOCH_OFFSET_DAYS = 9496.5;
    public static final double G0 = 9.80665;

    private OrbitMath() {}

    /** Standard gravitational parameter in km^3/s^2. */
    public static double mu(CelestialBody b) {
        return b.surfaceGravity * G0 * 1e-3 * b.radiusKm * b.radiusKm;
    }

    /** Heliocentric position (km) of a body at the given time (days since world epoch). */
    public static DVec position(CelestialBody b, double days) {
        if (b.parent == null) return DVec.ZERO;
        CelestialBody parent = SolarSystem.get(b.parent);
        return position(parent, days).add(orbitOffset(b, days));
    }

    /** Position relative to the parent body. */
    public static DVec orbitOffset(CelestialBody b, double days) {
        double t = days + EPOCH_OFFSET_DAYS;
        double meanLon = Math.toRadians(b.meanLongitudeDeg + 360.0 * t / b.orbitDays);
        double node = Math.toRadians(b.ascendingNodeDeg);
        double inc = Math.toRadians(b.inclinationDeg);
        double u = meanLon - node;
        double r = b.orbitKm;
        double xe = r * (Math.cos(node) * Math.cos(u) - Math.sin(node) * Math.sin(u) * Math.cos(inc));
        double ye = r * (Math.sin(node) * Math.cos(u) + Math.cos(node) * Math.sin(u) * Math.cos(inc));
        double ze = r * (Math.sin(u) * Math.sin(inc));
        return new DVec(xe, ze, -ye);
    }

    // ------------------------------------------------------------------ mission planning

    /** Radius (km) of the parking orbit used around a body. */
    public static double lowOrbitKm(CelestialBody b) {
        if (b.kind == CelestialBody.Kind.STAR) return b.radiusKm * 3;
        double alt = b.pressureBar > 0.5 ? 200 : (b.radiusKm > 500 ? 100 : Math.max(2, b.radiusKm * 0.3));
        return b.radiusKm + alt;
    }

    public static double circularSpeed(double mu, double r) { return Math.sqrt(mu / r); }

    /** Delta-v (km/s) to go from a circular orbit of radius r onto a hyperbola with excess speed vinf. */
    public static double hyperbolic(double mu, double r, double vinf) {
        return Math.sqrt(vinf * vinf + 2 * mu / r) - Math.sqrt(mu / r);
    }

    /** Surface to low orbit, km/s. Thick atmospheres use published figures. */
    public static double ascentKmS(CelestialBody b) {
        return switch (b.id) {
            case "earth" -> 9.4;
            case "mars" -> 4.1;
            case "venus" -> 27.0;
            case "titan" -> 7.6;
            default -> b.gasGiant ? Double.POSITIVE_INFINITY
                    : 1.1 * circularSpeed(mu(b), lowOrbitKm(b)) + 0.02;
        };
    }

    public enum EntryKind { VACUUM, AEROBRAKE, BURN_UP }

    /** Low orbit to surface, km/s, depending on entry hardware. */
    public static double descentKmS(CelestialBody b, boolean heatShield, boolean parachute) {
        if (b.pressureBar < 0.001) return 1.1 * circularSpeed(mu(b), lowOrbitKm(b)) + 0.02;
        if (!heatShield) return Double.POSITIVE_INFINITY; // burns up
        return switch (b.id) {
            case "earth", "titan", "venus" -> parachute ? 0.1 : 1.0;
            case "mars" -> parachute ? 0.6 : 1.4;
            default -> parachute ? 0.3 : 1.0;
        };
    }

    /** Result of planning a surface-to-surface leg. */
    public record Mission(CelestialBody from, CelestialBody to, double ascent, double transfer, double capture,
                          double descent, double transferDays, boolean burnsUp, double entrySpeedKmS) {
        public double totalKmS() { return ascent + transfer + capture + descent; }
    }

    public static Mission plan(CelestialBody from, CelestialBody to, boolean heatShield, boolean parachute) {
        double ascent = ascentKmS(from);
        boolean aero = heatShield && to.pressureBar >= 0.001;
        boolean burns = to.pressureBar >= 0.001 && !heatShield;
        double descent = descentKmS(to, heatShield, parachute);
        Transfer tr = transfer(from, to);
        double capture = aero ? tr.capture * 0.1 : tr.capture;
        double entry = tr.entrySpeed;
        return new Mission(from, to, ascent, tr.departure, capture, burns ? 0 : descent, tr.days, burns, entry);
    }

    record Transfer(double departure, double capture, double days, double entrySpeed) {}

    /** Patched-conic Hohmann transfer between low orbits of two bodies. */
    static Transfer transfer(CelestialBody a, CelestialBody b) {
        if (a == b) return new Transfer(0, 0, 0, 0);
        List<CelestialBody> ca = chain(a), cb = chain(b);
        CelestialBody common = null;
        for (CelestialBody x : ca) if (cb.contains(x)) { common = x; break; }
        if (common == null) common = SolarSystem.SUN;
        double muC = mu(common);
        CelestialBody childA = childToward(ca, common), childB = childToward(cb, common);
        double rA = childA == null ? lowOrbitKm(common) : childA.orbitKm;
        double rB = childB == null ? lowOrbitKm(common) : childB.orbitKm;
        double aT = (rA + rB) / 2;
        double vPeriA = Math.sqrt(muC * (2 / rA - 1 / aT));
        double vPeriB = Math.sqrt(muC * (2 / rB - 1 / aT));
        double vinfA = Math.abs(vPeriA - circularSpeed(muC, rA));
        double vinfB = Math.abs(vPeriB - circularSpeed(muC, rB));
        double days = Math.PI * Math.sqrt(aT * aT * aT / muC) / 86400.0;
        double vA = childA == null ? vinfA : excessAt(ca, childA, vinfA);
        double vB = childB == null ? vinfB : excessAt(cb, childB, vinfB);
        double dep = childA == null ? vA : hyperbolic(mu(a), lowOrbitKm(a), vA);
        double cap = childB == null ? vB : hyperbolic(mu(b), lowOrbitKm(b), vB);
        double entry = childB == null ? vPeriB
                : Math.sqrt(2 * mu(b) / b.radiusKm + vB * vB);
        return new Transfer(dep, cap, days, entry);
    }

    /**
     * Hyperbolic excess speed needed at the bottom of {@code chain} so that the craft ends up with
     * excess speed {@code vinf} relative to {@code top} (an ancestor, or the body itself).
     */
    private static double excessAt(List<CelestialBody> chain, CelestialBody top, double vinf) {
        double v = vinf;
        int topIdx = chain.indexOf(top);
        for (int i = topIdx; i > 0; i--) {
            CelestialBody planet = chain.get(i), moon = chain.get(i - 1);
            double muP = mu(planet);
            double vReq = Math.sqrt(v * v + 2 * muP / moon.orbitKm);
            v = Math.abs(vReq - circularSpeed(muP, moon.orbitKm));
        }
        return v;
    }

    private static List<CelestialBody> chain(CelestialBody b) {
        List<CelestialBody> out = new ArrayList<>();
        for (CelestialBody x = b; x != null; x = x.parent == null ? null : SolarSystem.get(x.parent)) out.add(x);
        return out;
    }

    private static CelestialBody childToward(List<CelestialBody> chain, CelestialBody ancestor) {
        int idx = chain.indexOf(ancestor);
        return idx <= 0 ? null : chain.get(idx - 1);
    }
}
