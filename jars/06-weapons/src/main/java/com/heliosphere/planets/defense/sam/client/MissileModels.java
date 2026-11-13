package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.planets.defense.sam.MissileSpec;

/**
 * Missile bodies, modelled on the real rounds: the nose tip at the origin, the body along -Z, in blocks (diameters
 * are a little fuller than true scale so a missile still reads at a distance).
 */
final class MissileModels {
    private MissileModels() {}

    static final int WHITE = 0xECECE6, OFFWHITE = 0xDCDCD2, RADOME = 0xC8C8BE, BLACK = 0x26282A, GREY = 0x8A8E92, DARK = 0x3A3D42,
            OLIVE = 0x5A6440, NOZZLE = 0x2A2622, RED = 0xC03020, YELLOW = 0xD8B030;
    private static final int S = 12;            // facets of a body

    static void draw(BoxMesh m, MissileSpec.Model model, boolean booster, int motor, float age) {
        switch (model) {
            case BUK -> buk(m, motor);
            case ASTER -> aster(m, booster, motor);
            case ASTER_BOOSTER -> { m.push(); m.rz(age * 0.4); m.rx(Math.sin(age * 0.13) * 0.3); asterBooster(m, 0); m.pop(); }
            case IRIS -> iris(m, motor);
            case TURRET -> small(m, 1.8, 0.12, GREY, DARK, motor);
            case PLAYER -> small(m, 1.25, 0.095, OLIVE, YELLOW, motor);
            case MACHINE -> machine(m, 1.4, 0.12, motor);
            case MACHINE_HEAVY -> machine(m, 2.0, 0.16, motor);
            case NUKE -> nuke(m, motor);
        }
    }

    /** An ogive nose from the tip (z=0) back to {@code len}, reaching radius r. */
    private static void ogive(BoxMesh m, double len, double r, int rgb) {
        double[] f = {0, 0.32, 0.56, 0.76, 0.9, 1.0};
        double[] rr = {0, 0.42, 0.68, 0.86, 0.96, 1.0};
        for (int i = 0; i < f.length - 1; i++) m.cyl(0, 0, -len * f[i + 1], -len * f[i], r * rr[i + 1], r * rr[i], S, BoxMesh.PLATE, rgb);
    }

    private static void band(BoxMesh m, double z, double w, double r, int rgb) { m.cyl(0, 0, z - w, z, r * 1.01, r * 1.01, S, BoxMesh.PLATE, rgb); }

    private static void fins(BoxMesh m, double roll0, double r0, double span, double rootFront, double rootBack, double tipFront, double tipBack, int rgb) {
        for (int i = 0; i < 4; i++)
            m.fin(roll0 + i * Math.PI / 2, r0, r0 + span, rootFront, rootBack, tipFront, tipBack, 0.035, BoxMesh.PLATE, rgb);
    }

    private static void plume(BoxMesh m, double z, double r, int motor) {
        if (motor <= 0) return;
        double len = motor == 2 ? 1.6 : 0.8;
        m.cylGlow(0, 0, z - len, z, 0, r * 0.95, S, BoxMesh.EMIT, motor == 2 ? 0xFFE8B0 : 0xFFC880);
        m.cylGlow(0, 0, z - len * 0.45, z, 0, r * 0.6, S, BoxMesh.EMIT, 0xFFFFFF);
    }

    /** 9M317MA: long white body, grey radome, four long low strakes and swept tail controls. */
    private static void buk(BoxMesh m, int motor) {
        double r = 0.23;
        ogive(m, 1.15, r, RADOME);
        m.cyl(0, 0, -5.35, -1.15, r, r, S, BoxMesh.PLATE, WHITE);
        band(m, -1.15, 0.06, r, BLACK);
        band(m, -2.95, 0.05, r, BLACK);
        band(m, -4.2, 0.04, r, GREY);
        fins(m, Math.PI / 4, r, 0.2, -1.9, -3.9, -2.4, -3.95, OFFWHITE);           // strakes
        fins(m, Math.PI / 4, r, 0.5, -4.55, -5.35, -5.05, -5.4, OFFWHITE);         // tail controls
        m.cyl(0, 0, -5.55, -5.35, r * 0.7, r * 0.85, S, BoxMesh.DARK, NOZZLE);
        plume(m, -5.55, r * 0.7, motor);
    }

    /** Aster 30: the dart (white, black nose band, long strakes) on a fat finned booster until separation. */
    private static void aster(BoxMesh m, boolean booster, int motor) {
        double r = 0.135;
        ogive(m, 0.65, r, WHITE);
        band(m, -0.32, 0.12, r * 0.8, BLACK);
        m.cyl(0, 0, -2.6, -0.65, r, r, S, BoxMesh.PLATE, WHITE);
        band(m, -1.0, 0.05, r, BLACK);
        fins(m, 0, r, 0.16, -0.95, -2.3, -1.15, -2.3, OFFWHITE);
        fins(m, 0, r, 0.2, -2.35, -2.6, -2.45, -2.62, OFFWHITE);
        if (booster) {
            m.push();
            m.at(0, 0, -2.6);
            asterBooster(m, motor);
            m.pop();
        } else {
            m.cyl(0, 0, -2.7, -2.6, r * 0.7, r * 0.85, S, BoxMesh.DARK, NOZZLE);
            plume(m, -2.7, r * 0.7, motor);
        }
    }

    private static void asterBooster(BoxMesh m, int motor) {
        double r = 0.26;
        m.cyl(0, 0, -0.3, 0, r, 0.135, S, BoxMesh.PLATE, WHITE);
        m.cyl(0, 0, -2.5, -0.3, r, r, S, BoxMesh.PLATE, WHITE);
        band(m, -0.7, 0.35, r, BLACK);
        band(m, -1.6, 0.06, r, BLACK);
        fins(m, Math.PI / 4, r, 0.42, -1.95, -2.5, -2.3, -2.55, OFFWHITE);
        m.cyl(0, 0, -2.7, -2.5, r * 0.8, r * 0.9, S, BoxMesh.DARK, NOZZLE);
        plume(m, -2.7, r * 0.85, motor);
    }

    /** IRIS-T SL: infra-red seeker dome, slim body with long strakes, four tail fins. */
    private static void iris(BoxMesh m, int motor) {
        double r = 0.15;
        m.cyl(0, 0, -0.18, 0, r * 0.85, 0.04, S, BoxMesh.LENS, 0x405060);
        m.cyl(0, 0, -0.4, -0.18, r, r * 0.85, S, BoxMesh.PLATE, OFFWHITE);
        m.cyl(0, 0, -3.0, -0.4, r, r, S, BoxMesh.PLATE, 0xE4E4DE);
        band(m, -0.42, 0.05, r, GREY);
        band(m, -1.6, 0.04, r, YELLOW);
        fins(m, Math.PI / 4, r, 0.07, -0.8, -2.5, -0.95, -2.5, OFFWHITE);
        fins(m, Math.PI / 4, r, 0.26, -2.62, -3.0, -2.86, -3.02, OFFWHITE);
        m.cyl(0, 0, -3.12, -3.0, r * 0.7, r * 0.85, S, BoxMesh.DARK, NOZZLE);
        plume(m, -3.12, r * 0.7, motor);
    }

    private static void small(BoxMesh m, double len, double r, int body, int stripe, int motor) {
        ogive(m, len * 0.25, r, body);
        m.cyl(0, 0, -len, -len * 0.25, r, r, S, BoxMesh.PLATE, body);
        band(m, -len * 0.3, 0.04, r, stripe);
        fins(m, Math.PI / 4, r, r * 1.4, -len * 0.8, -len, -len * 0.9, -len * 1.01, body);
        fins(m, 0, r, r * 0.6, -len * 0.3, -len * 0.42, -len * 0.33, -len * 0.42, body);
        m.cyl(0, 0, -len - 0.08, -len, r * 0.7, r * 0.8, S, BoxMesh.DARK, NOZZLE);
        plume(m, -len - 0.08, r * 0.7, motor);
    }

    /** The tactical nuke's warhead rocket: a fat olive warhead with yellow bands on a slim motor, ring fins. */
    private static void nuke(BoxMesh m, int motor) {
        double r = 0.3;
        ogive(m, 0.55, r, OLIVE);
        m.cyl(0, 0, -1.25, -0.55, r, r, S, BoxMesh.PLATE, OLIVE);
        band(m, -0.6, 0.08, r, YELLOW);
        band(m, -1.15, 0.08, r, YELLOW);
        m.cyl(0, 0, -1.25, -1.24, r * 1.02, r * 1.02, S, BoxMesh.HAZARD, YELLOW);
        m.cyl(0, 0, -1.55, -1.25, 0.14, r, S, BoxMesh.PLATE, DARK);
        m.cyl(0, 0, -2.6, -1.55, 0.14, 0.14, S, BoxMesh.PLATE, 0x6A7050);
        fins(m, Math.PI / 4, 0.14, 0.24, -2.25, -2.6, -2.4, -2.62, DARK);
        m.cyl(0, 0, -2.75, -2.6, 0.1, 0.12, S, BoxMesh.DARK, NOZZLE);
        plume(m, -2.75, 0.12, motor);
    }

    /** The machines' missiles: dark gunmetal with a glowing red seeker and fins. */
    private static void machine(BoxMesh m, double len, double r, int motor) {
        m.cylGlow(0, 0, -len * 0.18, 0, r * 0.9, 0.03, S, BoxMesh.EMIT, 0xFF3020);
        m.cyl(0, 0, -len * 0.25, -len * 0.18, r, r * 0.9, S, BoxMesh.HEAVY, DARK);
        m.cyl(0, 0, -len, -len * 0.25, r, r, S, BoxMesh.HEAVY, 0x2E3136);
        m.cylGlow(0, 0, -len * 0.5, -len * 0.47, r * 1.03, r * 1.03, S, BoxMesh.EMIT, 0xFF4030);
        fins(m, Math.PI / 4, r, r * 1.5, -len * 0.78, -len, -len * 0.9, -len * 1.02, 0x5A1A18);
        m.cyl(0, 0, -len - 0.1, -len, r * 0.7, r * 0.85, S, BoxMesh.DARK, NOZZLE);
        plume(m, -len - 0.1, r * 0.7, motor);
    }
}
