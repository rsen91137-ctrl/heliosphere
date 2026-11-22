package com.heliosphere.industry.borer.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.industry.borer.TunnelBorer;

/**
 * The tunnel borer drawn from boxes, in the machine's own frame: +Z ahead, +X to the driver's left, the 3×3×3 cube
 * spanning x -1.5..1.5, y 0..3, z -1.5..1.5 (origin at the middle of its floor).
 *
 * <p>Outside: the yellow shield (sixteen plates round the bore axis) with a hazard band at the tail, the cutter head
 * at the front (spokes, disc cutters, muck openings and a nose cone, turning while it cuts), headlamps round the
 * front rim, crawler tracks along both lower sides whose cleats run with the distance travelled, cab windows, vents
 * and hoses, and behind the cube a short trailing gantry carrying the open collection bin (the heap rises as the bin
 * fills) fed by a belt conveyor out of the tail.
 *
 * <p>Inside (the rider's first-person view): no shell and no cutter disc, only a cockpit built round the eye: a low
 * dashboard with the energy and bin gauges, thin pillars at the sides, a roof beam at the top edge of the view, and
 * the cutter's outer rim far ahead, its teeth turning at the edges of the view.
 */
final class BorerModel {
    private BorerModel() {}

    /** What one frame of one machine looks like (filled by the renderer). */
    static final class State {
        /** Cutter angle (radians), ticks for the wobble and the beacon. */
        float cutter, age;
        /** Bin fill and charge, 0..1. */
        float fill, energy;
        /** Where each track's cleats are round the loop, and how far the conveyor belt has run (blocks). */
        double trackLeft, trackRight, belt;
        boolean boring, moving, auto, firstPerson;
        int status, ores;
        /** The rider's eye in the machine frame (first person only). */
        double eyeX, eyeY = 1.27, eyeZ = -0.1;
    }

    static final int YELLOW = 0xF2B62C, YELLOW_DARK = 0xB8841E, STEEL = 0x8E9399, STEEL_DARK = 0x4A4F56, STEEL_LIGHT = 0xC8CCD2,
            RUBBER = 0x2A2B2E, CLEAT = 0x3A3B3F, GLASS = 0x7FB4D0, HAZARD_C = 0xF2C230, CABLE_C = 0x26282C, HOSE_C = 0x8A4A24,
            LAMP_C = 0xFFF0D0, ROCK = 0xE0D2BC, BIN_C = 0x5E646B, CONSOLE = 0x3A3E45, FRAME = 0x2C2F35, SEAT = 0x4A3A30,
            ENERGY_C = 0x48E0FF, ENERGY_DIM = 0x0E3440, BIN_GLOW = 0xFFB040, BIN_DIM = 0x40280C;
    static final int[] ORE_COLOURS = {0xE0B898, 0xF5D24A, 0x70ECDE, 0xE83030, 0xE88050, 0x50D870, 0x4070E8};

    /** Shield radius, height of the bore axis; flat-to-flat half size and half chord of the sixteen plates. */
    static final double R = 1.45, CY = 1.5, APO = R * Math.cos(Math.PI / 16), HALF = R * Math.sin(Math.PI / 16);

    /**
     * Tracks: centre line at x = ±TRACK_X, loop of length LOOP round two end wheels of radius WR, one cleat every
     * SPACING. The gauge is chosen so that a full turn on the spot moves each track a whole number of cleats, and
     * SPACING divides one block, so the cleats never jump when the yaw wraps or the facing flips.
     */
    static final double TRACK_X = 23 / (6 * Math.PI), SPACING = 1 / 3.0, LOOP = 6.0, WR = 0.30,
            RUN = (LOOP - 2 * Math.PI * WR) / 2, TZ = -0.12, TY = WR + 0.07;
    static final int CLEATS = 18;

    static void build(BoxMesh m, State s) {
        m.push();
        if (s.boring) {                                               // the whole machine trembles while it cuts
            double k = s.firstPerson ? 0.004 : 0.014;
            m.at(Math.sin(s.age * 2.9) * k, Math.sin(s.age * 4.3 + 1.1) * k * 0.8, Math.sin(s.age * 3.7) * k * 0.5);
        }
        if (s.firstPerson) {
            cockpit(m, s);
        } else {
            shield(m);
            cutter(m, s.cutter);
            lamps(m);
            track(m, 1, s.trackLeft);
            track(m, -1, s.trackRight);
        }
        gantry(m, s);                                                 // behind the cube: never in the driver's way
        m.pop();
    }

    // ---- shield --------------------------------------------------------------------------------------------------

    private static void shield(BoxMesh m) {
        double hb = HALF * (APO + 0.025) / APO;                       // half chord at the band's outer face
        for (int k = 0; k < 16; k++) {
            m.push();
            m.at(0, CY, 0);
            m.rz(k * Math.PI / 8);
            m.box(APO - 0.05, -HALF, -1.30, APO, HALF, 0.98, BoxMesh.HEAVY, YELLOW);            // skin plate
            m.box(APO - 0.04, -hb, -1.36, APO + 0.025, hb, -1.12, BoxMesh.HAZARD, HAZARD_C);      // hazard band at the tail
            m.box(APO - 0.04, -hb, 0.98, APO + 0.025, hb, 1.12, BoxMesh.HEAVY, YELLOW_DARK);      // front ring: cutter bearing
            m.pop();
        }
        // tail bulkhead: door, conveyor outlet, tail lamps
        m.push();
        m.at(0, CY, 0);
        m.rz(Math.PI / 16);
        m.cyl(0, 0, -1.40, -1.32, 1.40, 1.40, 16, BoxMesh.PLATE, STEEL_DARK);
        m.pop();
        m.box(0.36, 0.50, -1.44, 0.96, 1.62, -1.40, BoxMesh.DECAL, STEEL);
        m.box(0.86, 1.00, -1.47, 0.90, 1.16, -1.44, BoxMesh.JOINT, STEEL_LIGHT);                  // door handle
        m.box(-0.30, 0.82, -1.42, 0.30, 1.22, -1.40, BoxMesh.DARK, 0x101114);                     // conveyor outlet
        m.glow(-1.02, 2.24, -1.43, -0.86, 2.36, -1.40, BoxMesh.HEAVY, 0xFF3020);
        m.glow(0.86, 2.24, -1.43, 1.02, 2.36, -1.40, BoxMesh.HEAVY, 0xFF3020);
        for (int side = -1; side <= 1; side += 2) {
            // cab windows, high on both flanks near the front
            m.push();
            m.at(0, CY, 0);
            m.rz(side > 0 ? 0.32 : Math.PI - 0.32);
            m.box(R - 0.04, -0.30, -0.10, R + 0.012, 0.30, 0.86, BoxMesh.HEAVY, STEEL_DARK);
            m.box(R + 0.008, -0.25, -0.05, R + 0.028, 0.25, 0.81, BoxMesh.VISOR, GLASS);
            m.box(R + 0.02, -0.27, 0.36, R + 0.034, 0.27, 0.40, BoxMesh.HEAVY, STEEL_DARK);        // mullion
            m.pop();
            // vents low on the flanks
            m.push();
            m.at(0, CY, 0);
            m.rz(side > 0 ? -0.42 : Math.PI + 0.42);
            m.box(R - 0.04, -0.22, -1.05, R + 0.015, 0.22, -0.25, BoxMesh.VENT, YELLOW_DARK);
            m.pop();
            // chevron plate behind the windows
            m.push();
            m.at(0, CY, 0);
            m.rz(side > 0 ? 0.95 : Math.PI - 0.95);
            m.box(R - 0.04, -0.17, -1.05, R + 0.012, 0.17, -0.35, BoxMesh.DECAL, HAZARD_C);
            m.pop();
            // hoses and a cable along the upper flank
            m.push();
            m.at(0, CY, 0);
            m.rz(side > 0 ? 1.18 : Math.PI - 1.18);
            m.box(R - 0.01, -0.035, -1.34, R + 0.05, 0.035, 0.94, BoxMesh.CABLE, CABLE_C);
            m.box(R - 0.01, 0.06, -1.34, R + 0.04, 0.11, 0.94, BoxMesh.CABLE, HOSE_C);
            m.pop();
        }
        // roof: vent, hatch
        double top = CY + APO;
        m.box(-0.24, top - 0.02, -1.05, 0.24, top + 0.035, -0.30, BoxMesh.VENT, YELLOW_DARK);
        m.box(-0.22, top - 0.02, 0.05, 0.22, top + 0.055, 0.62, BoxMesh.HEAVY, STEEL_DARK);
        m.box(-0.08, top + 0.055, 0.25, 0.08, top + 0.072, 0.42, BoxMesh.JOINT, STEEL);
    }

    // ---- cutter head ---------------------------------------------------------------------------------------------

    private static void cutter(BoxMesh m, float angle) {
        m.push();
        m.at(0, CY, 0);
        m.rz(angle);
        m.cyl(0, 0, 1.12, 1.27, 1.40, 1.40, 24, BoxMesh.HEAVY, STEEL_DARK);                       // the disc
        for (int k = 0; k < 24; k++) {                                                           // gauge ring round its edge
            m.push();
            m.rz(k * Math.PI / 12);
            m.box(-0.185, 1.27, 1.27, 0.185, 1.40, 1.31, BoxMesh.HEAVY, STEEL);
            m.pop();
        }
        for (int k = 0; k < 6; k++) {
            m.push();
            m.rz(k * Math.PI / 3);
            m.box(-0.085, 0.24, 1.27, 0.085, 1.33, 1.37, BoxMesh.HEAVY, STEEL);                   // spoke
            for (int j = 0; j < 2; j++) {                                                        // two disc cutters a spoke, staggered
                double r = (j == 0 ? 0.52 : 0.98) + (k % 2) * 0.22;
                m.push();
                m.at(0, r, 0);
                m.box(-0.12, -0.10, 1.30, 0.12, 0.10, 1.375, BoxMesh.HEAVY, STEEL_DARK);          // saddle
                m.at(0, 0, 1.385);
                m.ry(Math.PI / 2);
                m.cyl(0, 0, -0.035, 0.035, 0.11, 0.11, 10, BoxMesh.JOINT, STEEL_LIGHT);            // the disc, rolling on the face
                m.pop();
            }
            m.pop();
            m.push();
            m.rz(k * Math.PI / 3 + Math.PI / 6);
            m.box(-0.19, 1.03, 1.265, 0.19, 1.30, 1.30, BoxMesh.DARK, 0x16171A);                   // muck opening
            m.box(-0.19, 0.98, 1.27, 0.19, 1.03, 1.36, BoxMesh.HEAVY, STEEL_LIGHT);               // scraper lip
            m.pop();
        }
        m.cyl(0, 0, 1.27, 1.33, 0.36, 0.36, 12, BoxMesh.HEAVY, STEEL_DARK);                       // hub
        m.cyl(0, 0, 1.33, 1.50, 0.30, 0, 12, BoxMesh.HEAVY, STEEL);                               // nose cone
        m.pop();
    }

    /** Headlamps round the front rim, shining ahead past the cutter. */
    private static void lamps(BoxMesh m) {
        for (int i = 0; i < 8; i++) {
            m.push();
            m.at(0, CY, 0);
            m.rz(Math.PI / 8 + i * Math.PI / 4);
            m.box(R - 0.02, -0.11, 0.97, R + 0.045, 0.11, 1.12, BoxMesh.HEAVY, STEEL_DARK);
            m.glow(R - 0.005, -0.08, 1.118, R + 0.035, 0.08, 1.132, BoxMesh.HEAVY, LAMP_C);
            m.glow(R + 0.044, -0.06, 1.0, R + 0.05, 0.06, 1.1, BoxMesh.HEAVY, LAMP_C);
            m.pop();
        }
    }

    // ---- tracks --------------------------------------------------------------------------------------------------

    private static void track(BoxMesh m, int side, double phase) {
        double x = side * TRACK_X, zf = TZ + RUN / 2, zr = TZ - RUN / 2;
        m.box(x - 0.13, TY - 0.17, zr, x + 0.13, TY + 0.17, zf, BoxMesh.HEAVY, YELLOW_DARK);           // track frame
        for (int i = 0; i < 4; i++) wheel(m, x, TY - WR + 0.15, zr + 0.28 + i * (RUN - 0.56) / 3, 0.12, 0.34, STEEL_DARK, STEEL);
        wheel(m, x, TY, zf, WR + 0.015, 0.40, RUBBER, STEEL_LIGHT);                                  // belt round the sprocket
        wheel(m, x, TY, zr, WR + 0.015, 0.40, RUBBER, STEEL);                                        // and round the idler
        m.box(x - 0.20, TY - WR - 0.015, zr, x + 0.20, TY - WR + 0.025, zf, BoxMesh.TREAD, RUBBER);  // bottom run
        m.box(x - 0.20, TY + WR - 0.025, zr, x + 0.20, TY + WR + 0.015, zf, BoxMesh.TREAD, RUBBER);  // top run
        for (int i = 0; i < CLEATS; i++) {
            double a = mod(i * SPACING + phase, LOOP), y, z, tilt;
            if (a < RUN) {                                            // bottom run, going back: still on the ground
                z = zf - a; y = TY - WR; tilt = Math.PI;
            } else if (a < RUN + Math.PI * WR) {                      // up round the idler
                double t = (a - RUN) / WR;
                z = zr - WR * Math.sin(t); y = TY - WR * Math.cos(t); tilt = Math.atan2(-Math.sin(t), -Math.cos(t));
            } else if (a < 2 * RUN + Math.PI * WR) {                  // top run, going forward
                z = zr + (a - RUN - Math.PI * WR); y = TY + WR; tilt = 0;
            } else {                                                  // down round the sprocket
                double t = (a - 2 * RUN - Math.PI * WR) / WR;
                z = zf + WR * Math.sin(t); y = TY + WR * Math.cos(t); tilt = t;
            }
            m.push();
            m.at(x, y, z);
            m.rx(tilt);
            m.box(-0.21, 0.01, -0.07, 0.21, 0.065, 0.07, BoxMesh.TREAD, CLEAT);
            m.pop();
        }
    }

    /** A wheel turning about the X axis. */
    private static void wheel(BoxMesh m, double x, double y, double z, double r, double w, int tyre, int hub) {
        m.push();
        m.at(x - w / 2, y, z);
        m.ry(Math.PI / 2);
        m.cyl(0, 0, 0, w, r, r, 12, BoxMesh.TREAD, tyre);
        m.cyl(0, 0, -0.01, w + 0.01, r * 0.5, r * 0.5, 8, BoxMesh.JOINT, hub);
        m.pop();
    }

    // ---- trailing gantry with the bin ----------------------------------------------------------------------------

    private static void gantry(BoxMesh m, State s) {
        double z0 = -1.58, z1 = -2.44, px = 0.92;
        for (int side = -1; side <= 1; side += 2) {
            double x = side * px;
            m.box(x - 0.06, 0.22, z0 - 0.06, x + 0.06, 2.12, z0 + 0.06, BoxMesh.HEAVY, YELLOW_DARK);    // posts
            m.box(x - 0.06, 0.22, z1 - 0.06, x + 0.06, 2.12, z1 + 0.06, BoxMesh.HEAVY, YELLOW_DARK);
            m.box(x - 0.06, 2.04, z1 - 0.06, x + 0.06, 2.16, z0 + 0.06, BoxMesh.HEAVY, YELLOW_DARK);    // top rail
            m.box(x - 0.05, 0.30, z1, x + 0.05, 0.40, z0, BoxMesh.HEAVY, STEEL_DARK);                   // bottom rail
            rod(m, x, 0.40, z0 - 0.02, x, 2.04, z1 + 0.02, 0.025, BoxMesh.HEAVY, STEEL);              // brace
            wheel(m, x, 0.13, z0, 0.13, 0.12, RUBBER, STEEL);
            wheel(m, x, 0.13, z1, 0.13, 0.12, RUBBER, STEEL);
            m.glow(x - 0.05, 0.56, z1 - 0.075, x + 0.05, 0.68, z1 - 0.06, BoxMesh.HEAVY, 0xFF3020);    // tail light
        }
        m.box(-px - 0.06, 2.04, z0 - 0.06, px + 0.06, 2.16, z0 + 0.06, BoxMesh.HEAVY, YELLOW_DARK);     // cross beams
        m.box(-px - 0.06, 2.04, z1 - 0.06, px + 0.06, 2.16, z1 + 0.06, BoxMesh.HEAVY, YELLOW_DARK);
        m.box(-0.04, 2.08, z1 + 0.06, 0.04, 2.16, z0 - 0.06, BoxMesh.HEAVY, YELLOW_DARK);               // spine (carries the conveyor)
        m.box(-0.10, 0.30, z0, 0.10, 0.42, -1.40, BoxMesh.HEAVY, STEEL_DARK);                           // drawbar
        if (!s.firstPerson) {                                                                          // hoses to the tail
            rod(m, 0.55, 2.38, -1.38, 0.74, 2.10, z0, 0.03, BoxMesh.CABLE, CABLE_C);
            rod(m, -0.55, 2.38, -1.38, -0.74, 2.10, z0, 0.03, BoxMesh.CABLE, HOSE_C);
        }

        // the collection bin, open at the top
        double bx = 0.80, y0 = 0.42, y1 = 1.22, f0 = -1.68, f1 = -2.36;                               // f0 front wall, f1 back wall
        m.box(-0.90, 0.30, f1 - 0.08, 0.90, y0, f0 + 0.06, BoxMesh.MESH, STEEL_DARK);                  // deck
        m.box(-bx - 0.05, y0, f0, bx + 0.05, y1, f0 + 0.05, BoxMesh.HEAVY, BIN_C);
        m.box(-bx - 0.05, y0, f1 - 0.05, bx + 0.05, y1, f1, BoxMesh.HEAVY, BIN_C);
        m.box(bx, y0, f1, bx + 0.05, y1, f0, BoxMesh.HEAVY, BIN_C);
        m.box(-bx - 0.05, y0, f1, -bx, y1, f0, BoxMesh.HEAVY, BIN_C);
        m.box(-bx - 0.06, y1, f0 - 0.005, bx + 0.06, y1 + 0.04, f0 + 0.055, BoxMesh.HAZARD, HAZARD_C); // hazard edge
        m.box(-bx - 0.06, y1, f1 - 0.055, bx + 0.06, y1 + 0.04, f1 + 0.005, BoxMesh.HAZARD, HAZARD_C);
        m.box(bx - 0.005, y1, f1, bx + 0.055, y1 + 0.04, f0, BoxMesh.HAZARD, HAZARD_C);
        m.box(-bx - 0.055, y1, f1, -bx + 0.005, y1 + 0.04, f0, BoxMesh.HAZARD, HAZARD_C);
        if (s.fill > 0.001) {                                         // the heap, rising with the fill
            double top = y0 + 0.04 + 0.70 * s.fill, mound = Math.min(1, s.fill * 3);
            m.box(-bx, y0, f1, bx, top, f0, BoxMesh.DARK, ROCK);
            m.box(-0.55, top, f1 + 0.12, 0.50, top + 0.07 * mound, f0 - 0.12, BoxMesh.DARK, ROCK);
            m.box(-0.30, top + 0.07 * mound, f1 + 0.24, 0.25, top + 0.13 * mound, f0 - 0.22, BoxMesh.DARK, ROCK);
            double[][] spots = {{0.64, -1.76}, {-0.64, -2.27}, {0.62, -2.26}, {-0.63, -1.79}, {0.02, -1.74}, {0.06, -2.30}, {0.66, -2.02}};
            int lumps = Math.min(spots.length, s.ores > 0 ? 1 + s.ores / 8 : 0);
            for (int i = 0; i < lumps; i++)                            // ore glinting in the heap
                m.box(spots[i][0] - 0.05, top - 0.01, spots[i][1] - 0.05, spots[i][0] + 0.05, top + 0.05, spots[i][1] + 0.05,
                        BoxMesh.HEAVY, ORE_COLOURS[i % ORE_COLOURS.length]);
        }

        // belt conveyor: out of the tail bulkhead, up over the middle of the bin
        double ay = 0.98, az = -1.36, by = 1.62, bz = -2.02, len = Math.hypot(by - ay, bz - az);
        m.push();
        m.at(0, ay, az);
        m.ry(Math.PI);
        m.rx(-Math.atan2(by - ay, az - bz));
        m.box(-0.20, -0.03, 0, 0.20, 0.01, len, BoxMesh.TREAD, RUBBER);
        m.box(-0.26, -0.10, -0.02, -0.20, 0.06, len + 0.02, BoxMesh.HEAVY, YELLOW_DARK);
        m.box(0.20, -0.10, -0.02, 0.26, 0.06, len + 0.02, BoxMesh.HEAVY, YELLOW_DARK);
        for (int i = 0; i < 5; i++) {
            double z = mod(i * len / 5 + s.belt, len);
            m.box(-0.18, 0.01, Math.max(0, z - 0.02), 0.18, 0.045, Math.min(len, z + 0.02), BoxMesh.TREAD, CLEAT);
            if (s.boring && z > 0.12) m.box(-0.08 + (i % 3) * 0.05, 0.01, z - 0.11, 0.02 + (i % 3) * 0.05, 0.08, z - 0.03, BoxMesh.DARK, ROCK);
        }
        wheel(m, 0, -0.01, len, 0.06, 0.42, STEEL_DARK, STEEL);                                       // head pulley
        m.pop();
        m.box(-0.03, 1.64, -2.05, 0.03, 2.08, -1.99, BoxMesh.HEAVY, STEEL);                           // hanger

        // warning beacon (turns while the machine works) and the status lamp
        boolean busy = s.boring || s.moving || s.auto;
        m.box(0.60, 2.16, z1 - 0.06, 0.80, 2.21, z1 + 0.10, BoxMesh.HEAVY, STEEL_DARK);
        m.push();
        m.at(0.70, 2.21, z1 + 0.02);
        if (busy) {
            m.ry(s.age * 0.35);
            m.glow(-0.05, 0, -0.05, 0.05, 0.12, 0.05, BoxMesh.HEAVY, 0xFFB030);
            m.glow(0.0, 0.02, -0.015, 0.11, 0.10, 0.015, BoxMesh.HEAVY, 0xFFD070);
        } else {
            m.box(-0.05, 0, -0.05, 0.05, 0.12, 0.05, BoxMesh.HEAVY, 0x9A6A20);
        }
        m.pop();
        m.box(-0.80, 2.16, z1 - 0.06, -0.60, 2.21, z1 + 0.10, BoxMesh.HEAVY, STEEL_DARK);
        m.glow(-0.75, 2.21, z1 - 0.03, -0.65, 2.29, z1 + 0.07, BoxMesh.HEAVY, statusRgb(s.status));
    }

    /** The colour of the status lamp: green working, blue turning or reversing, red stopped by something, grey idle. */
    static int statusRgb(int status) {
        return switch (status) {
            case TunnelBorer.S_BORING, TunnelBorer.S_DONE -> 0x50FF70;
            case TunnelBorer.S_TURNING, TunnelBorer.S_REVERSING -> 0x60C8FF;
            case TunnelBorer.S_IDLE -> 0x9AA0A6;
            default -> 0xFF3A28;
        };
    }

    // ---- first-person cockpit ------------------------------------------------------------------------------------

    /**
     * Everything is placed relative to the rider's eye (so it works whatever the seat height), kept below the eye
     * line or at the edges of the view; the cutter's rim stays where the cutter is.
     */
    private static void cockpit(BoxMesh m, State s) {
        double ey = s.eyeY, ez = s.eyeZ;
        double floor = Math.max(0.02, ey - 1.62);
        m.push();
        m.at(Math.max(-0.4, Math.min(0.4, s.eyeX)), 0, 0);
        // floor plate and seat (under and behind the eye)
        m.box(-0.90, floor - 0.04, ez - 0.70, 0.90, floor, ez + 1.05, BoxMesh.MESH, FRAME);
        double seat = Math.max(floor + 0.10, ey - 0.88);                         // a seated rider's thighs
        m.box(-0.30, seat - 0.12, ez - 0.40, 0.30, seat, ez + 0.18, BoxMesh.HEAVY, SEAT);
        m.box(-0.30, seat, ez - 0.48, 0.30, ey - 0.25, ez - 0.36, BoxMesh.HEAVY, SEAT);
        // side consoles with a lever and lit buttons
        for (int side = -1; side <= 1; side += 2) {
            double x0 = side > 0 ? 0.46 : -0.80, x1 = side > 0 ? 0.80 : -0.46, cy = Math.max(floor + 0.2, ey - 0.82);
            m.box(x0, floor, ez - 0.32, x1, cy, ez + 0.62, BoxMesh.PLATE, CONSOLE);
            m.box(side * 0.58 - 0.025, cy, ez + 0.10, side * 0.58 + 0.025, cy + 0.22, ez + 0.15, BoxMesh.JOINT, STEEL);
            m.box(side * 0.58 - 0.045, cy + 0.20, ez + 0.08, side * 0.58 + 0.045, cy + 0.26, ez + 0.17, BoxMesh.HEAVY, 0x202226);
            for (int i = 0; i < 3; i++)
                m.glow(side * 0.70 - 0.03, cy, ez + 0.30 + i * 0.09, side * 0.70 + 0.03, cy + 0.012, ez + 0.36 + i * 0.09,
                        BoxMesh.HEAVY, i == 0 ? 0x60FF80 : i == 1 ? 0xFFC040 : 0x60C0FF);
        }
        // dashboard: body, then a panel sloping up and away from the driver with the two gauges
        double hingeY = ey - 0.56, hingeZ = ez + 0.60;
        m.box(-0.82, floor, hingeZ, 0.82, hingeY, hingeZ + 0.40, BoxMesh.PLATE, CONSOLE);
        m.push();
        m.at(0, hingeY, hingeZ);
        m.rx(-0.70);
        m.box(-0.82, -0.04, 0, 0.82, 0, 0.40, BoxMesh.HEAVY, FRAME);
        gauge(m, 0.10, 0.64, s.energy, ENERGY_C, ENERGY_DIM);                      // energy on the left
        gauge(m, -0.64, -0.10, s.fill, BIN_GLOW, BIN_DIM);                         // bin on the right
        lamp(m, 0.07, s.auto, 0x50FF70);                                           // auto
        lamp(m, 0.17, s.boring, 0xFFA030);                                         // cutting
        int st = s.status;
        lamp(m, 0.27, st != TunnelBorer.S_IDLE && st != TunnelBorer.S_BORING && st != TunnelBorer.S_DONE
                && st != TunnelBorer.S_TURNING && st != TunnelBorer.S_REVERSING, 0xFF3A28);    // stopped by something
        m.pop();
        // pillars at the sides and the roof beam along the top edge of the view
        for (int side = -1; side <= 1; side += 2) {
            m.push();
            m.at(side * 0.93, hingeY, ez + 0.86);
            m.rz(side * 0.07);
            m.box(-0.028, 0, -0.035, 0.028, 1.20, 0.035, BoxMesh.HEAVY, FRAME);
            m.pop();
        }
        m.box(-0.98, ey + 0.58, ez + 0.78, 0.98, ey + 0.66, ez + 0.94, BoxMesh.HEAVY, FRAME);
        m.glow(-0.28, ey + 0.575, ez + 0.83, 0.28, ey + 0.58, ez + 0.87, BoxMesh.HEAVY, 0xC8B890);      // cab light
        m.pop();
        // the cutter's outer rim far ahead: only its edge shows round the view
        m.push();
        m.at(0, CY, 0);
        m.rz(s.cutter);
        for (int k = 0; k < 24; k++) {
            m.push();
            m.rz(k * Math.PI / 12);
            m.box(-0.185, 1.20, 1.22, 0.185, 1.40, 1.30, BoxMesh.HEAVY, STEEL_DARK);
            m.pop();
        }
        for (int k = 0; k < 12; k++) {
            m.push();
            m.rz(k * Math.PI / 6 + 0.13);
            m.box(-0.05, 1.22, 1.30, 0.05, 1.34, 1.44, BoxMesh.HEAVY, STEEL_LIGHT);
            m.pop();
        }
        m.pop();
    }

    /** A screen on the sloping panel: dark glass, a bar of ten segments lit from the driver's left in proportion to {@code f}. */
    private static void gauge(BoxMesh m, double x0, double x1, float f, int rgb, int dim) {
        m.box(x0, 0, 0.05, x1, 0.012, 0.35, BoxMesh.VISOR, 0x10161A);
        m.glow(x0 + 0.02, 0.0125, 0.05, x1 - 0.02, 0.014, 0.065, BoxMesh.HEAVY, dim);      // frame lines
        m.glow(x0 + 0.02, 0.0125, 0.335, x1 - 0.02, 0.014, 0.35, BoxMesh.HEAVY, dim);
        double level = 10 * Math.max(0, Math.min(1, f)), w = (x1 - x0 - 0.06) / 10;
        for (int i = 0; i < 10; i++) {
            double start = x1 - 0.03 - i * w, end = start - w + 0.008;          // the driver's left is +X
            double lit = Math.max(0, Math.min(1, level - i)), cut = start - (start - end) * lit;
            if (lit > 0) m.glow(cut, 0.013, 0.12, start, 0.017, 0.30, BoxMesh.HEAVY, rgb);
            if (lit < 1) m.glow(end, 0.013, 0.12, cut, 0.017, 0.30, BoxMesh.HEAVY, dim);
        }
    }

    private static void lamp(BoxMesh m, double z, boolean on, int rgb) {
        if (on) m.glow(-0.035, 0.0, z, 0.035, 0.016, z + 0.06, BoxMesh.HEAVY, rgb);
        else m.box(-0.035, 0.0, z, 0.035, 0.016, z + 0.06, BoxMesh.HEAVY, 0x26282C);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** A square rod of half-width {@code r} from a to b. */
    private static void rod(BoxMesh m, double ax, double ay, double az, double bx, double by, double bz, double r, int tile, int rgb) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        m.push();
        m.at(ax, ay, az);
        m.ry(Math.atan2(dx, dz));
        m.rx(-Math.atan2(dy, Math.hypot(dx, dz)));
        m.box(-r, -r, 0, r, r, len, tile, rgb);
        m.pop();
    }

    static double mod(double a, double n) {
        double r = a % n;
        return r < 0 ? r + n : r;
    }
}
