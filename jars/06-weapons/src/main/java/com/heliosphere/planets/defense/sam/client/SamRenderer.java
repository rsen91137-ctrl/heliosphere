package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.planets.defense.sam.SamKind;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the air-defence vehicles after the real ones: the Buk-M3 on its tracked chassis (TELAR with six containers
 * on a turning launcher and the fire-control radar on the turret front; TADS with its big tilted search antenna),
 * and the SAMP/T and IRIS-T SLM on 8x8 trucks (launchers with eight canisters that rise to the vertical; radar
 * trucks with a shelter and a turning antenna). Built facing +Z in blocks, origin at the centre of the master block.
 */
public final class SamRenderer {
    private SamRenderer() {}

    public static final class State {
        SamKind kind = SamKind.BUK_TELAR;
        float raise, yaw, elev, age, sinceLaunch;
        int ammo, lightCoords;
        boolean active;
    }

    static final int BUK = 0x55603A, BUK_DARK = 0x3E4629, SAMP = 0x66674A, SAMP_DARK = 0x4A4B35, IRIS = 0x3E4A35, IRIS_DARK = 0x2C3526,
            RUBBER = 0x1E1F20, STEEL = 0x6E7276, GLASS = 0x22303A, CAP = 0xB02A20, RADAR = 0xA8AA9A, WHITE = 0xE2E2DA, BLACK = 0x202224;

    static void build(BoxMesh m, State s) {
        switch (s.kind) {
            case BUK_TELAR -> bukTelar(m, s);
            case BUK_TADS -> bukTads(m, s);
            case SAMP_TEL -> vls(m, s, SAMP, SAMP_DARK, SamKind.VLS_PIVOT_SAMP, SamKind.SAMP_CAN, false);
            case SAMP_TADS -> sampTads(m, s);
            case IRIS_TEL -> vls(m, s, IRIS, IRIS_DARK, SamKind.VLS_PIVOT_IRIS, SamKind.IRIS_CAN, true);
            case IRIS_TADS -> irisTads(m, s);
        }
    }

    // ---- chassis --------------------------------------------------------------------------------------------------

    /** GM-569 tracked chassis: low hull, sloped glacis, six road wheels a side, drive sprocket and idler. */
    private static void tracked(BoxMesh m, int paint, int dark) {
        m.box(-1.25, 0.55, -3.3, 1.25, 1.55, 3.0, BoxMesh.PLATE, paint);
        m.push(); m.at(0, 1.05, 3.0); m.rx(-0.55); m.box(-1.25, -0.5, 0, 1.25, 0.5, 0.75, BoxMesh.PLATE, paint); m.pop();   // glacis
        m.box(-1.55, 1.42, -3.4, 1.55, 1.52, 3.35, BoxMesh.PLATE, dark);                  // track guards
        m.box(-1.1, 1.55, 1.6, -0.2, 1.8, 2.6, BoxMesh.VENT, dark);                       // engine deck
        m.box(0.3, 1.55, 2.0, 0.9, 1.75, 2.7, BoxMesh.HEAVY, dark);
        m.cbox(-0.85, 1.62, 2.75, 0.3, 0.12, 0.2, BoxMesh.LENS, GLASS);                    // driver's periscopes
        for (int side = -1; side <= 1; side += 2) {
            double x = side * 1.4;
            m.box(x - 0.17, 0.02, -3.35, x + 0.17, 0.12, 3.3, BoxMesh.TREAD, RUBBER);
            m.box(x - 0.17, 0.95, -3.0, x + 0.17, 1.02, 2.9, BoxMesh.TREAD, RUBBER);
            for (int i = 0; i < 6; i++) wheel(m, x, 0.42, -2.55 + i * 1.0, 0.36, 0.28, RUBBER, STEEL);
            wheel(m, x, 0.62, 3.05, 0.32, 0.3, dark, STEEL);                               // sprocket
            wheel(m, x, 0.6, -3.15, 0.3, 0.3, dark, STEEL);                                // idler
        }
    }

    /** 8x8 heavy truck: frame, cab with windscreen, four axles. */
    private static void truck(BoxMesh m, int paint, int dark, double cabFront, boolean flatCab) {
        m.box(-1.0, 0.95, -4.45, 1.0, 1.3, cabFront - 1.6, BoxMesh.HEAVY, dark);           // frame
        double c0 = cabFront - 1.6;
        m.box(-1.25, 0.95, c0, 1.25, 2.95, cabFront, BoxMesh.PLATE, paint);               // cab
        m.box(-1.1, 2.0, cabFront - 0.02, 1.1, 2.75, cabFront + 0.02, BoxMesh.LENS, GLASS);   // windscreen
        for (int side = -1; side <= 1; side += 2) m.box(side * 1.26 - 0.01, 2.0, c0 + 0.3, side * 1.26 + 0.01, 2.65, cabFront - 0.25, BoxMesh.LENS, GLASS);
        m.box(-1.25, 0.95, cabFront, 1.25, 1.35, cabFront + 0.12, BoxMesh.HEAVY, dark);    // bumper
        m.cglow(-0.95, 1.5, cabFront + 0.06, 0.2, 0.14, 0.04, BoxMesh.LAMP, 0xFFE8B0);
        m.cglow(0.95, 1.5, cabFront + 0.06, 0.2, 0.14, 0.04, BoxMesh.LAMP, 0xFFE8B0);
        if (!flatCab) m.box(-1.0, 2.95, c0 + 0.2, 1.0, 3.2, cabFront - 0.3, BoxMesh.VENT, dark);
        double[] axles = {cabFront - 1.0, cabFront - 2.4, -2.2, -3.6};
        for (double z : axles)
            for (int side = -1; side <= 1; side += 2) {
                wheel(m, side * 1.18, 0.55, z, 0.55, 0.42, RUBBER, STEEL);
                m.box(side * 1.18 - 0.24, 1.08, z - 0.62, side * 1.18 + 0.24, 1.16, z + 0.62, BoxMesh.PLATE, dark);     // mudguard
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

    // ---- Buk ------------------------------------------------------------------------------------------------------

    private static void bukTelar(BoxMesh m, State s) {
        tracked(m, BUK, BUK_DARK);
        Vec3 t = SamKind.BUK_TURRET;
        m.push();
        m.at(t.x, t.y, t.z);
        m.ry(s.yaw);
        m.cyl(0, -0.5, 0, 0.01, 1.15, 1.15, 16, BoxMesh.PLATE, BUK_DARK);                 // ring, drawn as a flat drum
        m.push(); m.rx(Math.PI / 2); m.cyl(0, 0, 0.0, 0.5, 1.15, 1.1, 16, BoxMesh.PLATE, BUK_DARK); m.pop();
        m.box(-1.15, -0.5, -1.4, 1.15, 0.45, 1.5, BoxMesh.PLATE, BUK);                     // turret body
        m.box(-1.0, 0.45, -1.2, 1.0, 0.6, 0.9, BoxMesh.VENT, BUK_DARK);
        // fire-control radar: an octagonal dish on the turret front
        m.push();
        m.at(0, 0.6, 1.55);
        m.rx(-0.15);
        m.box(-0.95, -0.85, 0, 0.95, 0.85, 0.22, BoxMesh.MESH, RADAR);
        m.box(-0.7, -1.0, 0.02, 0.7, 1.0, 0.2, BoxMesh.MESH, RADAR);
        m.box(-0.12, -0.12, 0.22, 0.12, 0.12, 0.4, BoxMesh.JOINT, STEEL);
        m.pop();
        m.cbox(0.75, 0.75, 0.9, 0.35, 0.3, 0.35, BoxMesh.LENS, GLASS);                     // electro-optical sight
        m.cglow(0.75, 0.75, 1.08, 0.18, 0.14, 0.02, BoxMesh.EMIT, s.active ? 0x60FF80 : 0x204028);
        // launcher arm with six containers
        Vec3 a = SamKind.BUK_ARM;
        m.push();
        m.at(a.x, a.y, a.z);
        m.rx(-s.elev);
        for (int side = -1; side <= 1; side += 2) m.box(side * 0.9 - 0.08, -0.1, -0.6, side * 0.9 + 0.08, 0.1, 4.6, BoxMesh.HEAVY, BUK_DARK);
        m.box(-0.95, -0.12, -0.55, 0.95, 0.05, 0.2, BoxMesh.HEAVY, BUK_DARK);
        for (int i = 0; i < 6; i++) {
            double x = (i % 3 - 1) * 0.52, y = 0.28 + (i / 3) * 0.52;
            m.cyl(x, y, -SamKind.BUK_TUBE_BACK, SamKind.BUK_TUBE - SamKind.BUK_TUBE_BACK, 0.25, 0.25, 12, BoxMesh.PLATE, BUK);
            m.cyl(x, y, 1.4, 1.5, 0.265, 0.265, 12, BoxMesh.HEAVY, BUK_DARK);                // container ribs
            m.cyl(x, y, 3.2, 3.3, 0.265, 0.265, 12, BoxMesh.HEAVY, BUK_DARK);
            boolean loaded = i >= 6 - s.ammo;
            double mz = SamKind.BUK_TUBE - SamKind.BUK_TUBE_BACK;
            if (loaded) m.cyl(x, y, mz, mz + 0.08, 0.26, 0.24, 12, BoxMesh.PLATE, CAP);
            else m.cyl(x, y, mz - 0.02, mz + 0.001, 0.2, 0.2, 12, BoxMesh.DARK, BLACK);
        }
        m.pop();
        m.pop();
    }

    private static void bukTads(BoxMesh m, State s) {
        tracked(m, BUK, BUK_DARK);
        m.box(-1.2, 1.55, -3.2, 1.2, 2.6, -0.6, BoxMesh.PLATE, BUK);                       // equipment cabin
        m.box(-1.0, 2.6, -3.0, 1.0, 2.75, -0.8, BoxMesh.VENT, BUK_DARK);
        m.box(-1.2, 1.55, 0.6, 1.2, 2.0, 1.6, BoxMesh.PLATE, BUK);
        double spin = s.active ? s.age * 0.035 : 0;
        m.push();
        m.at(0, 2.0, 0);
        m.ry(spin);
        m.cyl(0, 0, 0, 0.4, 0.45, 0.45, 12, BoxMesh.JOINT, STEEL);
        m.push(); m.rx(-Math.PI / 2); m.cyl(0, 0, 0, 0.9, 0.22, 0.22, 10, BoxMesh.HEAVY, BUK_DARK); m.pop();
        m.at(0, 0.9, 0);
        m.rx(0.28);                                                                        // tilted back
        m.box(-1.25, -0.2, -0.18, 1.25, 3.4, 0.05, BoxMesh.MESH, BUK);
        m.box(-1.3, -0.25, -0.24, 1.3, -0.1, 0.08, BoxMesh.HEAVY, BUK_DARK);
        m.box(-1.3, 3.35, -0.24, 1.3, 3.5, 0.08, BoxMesh.HEAVY, BUK_DARK);
        m.box(-1.15, 0, 0.05, 1.15, 3.2, 0.09, BoxMesh.MESH, RADAR);
        m.pop();
        m.push(); m.at(1.0, 1.55, 2.2); m.rx(-0.25); m.box(-0.03, 0, -0.03, 0.03, 4.0, 0.03, BoxMesh.CABLE, STEEL); m.pop();   // whip antenna
    }

    // ---- vertical launchers -------------------------------------------------------------------------------------

    private static void vls(BoxMesh m, State s, int paint, int dark, Vec3 pivot, double can, boolean iris) {
        truck(m, paint, dark, 4.45, iris);
        m.box(-1.15, 1.3, 0.5, 1.15, 2.4, 2.75, BoxMesh.PLATE, paint);                    // equipment boxes behind the cab
        m.box(-1.05, 2.4, 1.0, 1.05, 2.6, 2.6, BoxMesh.VENT, dark);
        m.box(-1.05, 1.3, -4.4, 1.05, 1.5, 0.4, BoxMesh.HEAVY, dark);                      // launcher bed
        // stabiliser legs, extended while raised
        double leg = Math.min(1, s.raise * 3);
        for (int side = -1; side <= 1; side += 2)
            for (double z : new double[]{-4.2, 0.2}) {
                m.box(side * 1.05 - 0.08, 1.0 - 0.95 * leg, z - 0.08, side * 1.05 + 0.08, 1.35, z + 0.08, BoxMesh.HEAVY, STEEL);
                m.box(side * 1.05 - 0.22, 1.0 - 0.95 * leg - 0.05, z - 0.22, side * 1.05 + 0.22, 1.0 - 0.95 * leg, z + 0.22, BoxMesh.HEAVY, dark);
            }
        // antennas
        m.box(0.85, 2.6, 1.2, 0.9, iris ? 7.5 : 6.5, 1.25, BoxMesh.CABLE, STEEL);
        if (iris) m.box(-0.9, 2.6, 1.6, -0.85, 7.0, 1.65, BoxMesh.CABLE, STEEL);
        // the launcher module: canisters stand up as it is raised
        double ang = s.raise * Math.PI / 2;
        m.push();
        m.at(pivot.x, pivot.y, pivot.z);
        m.rx(-ang);
        m.box(-1.0, 0.02, -0.05, 1.0, 1.08, can + 0.05, BoxMesh.HEAVY, dark);             // frame
        for (int i = 0; i < 8; i++) {
            double x = (i % 4 - 1.5) * 0.46, y = 0.3 + (i / 4) * 0.48;
            m.box(x - 0.21, y - 0.21, 0, x + 0.21, y + 0.21, can, BoxMesh.PLATE, paint);
            m.box(x - 0.215, y - 0.215, can * 0.3, x + 0.215, y + 0.215, can * 0.3 + 0.08, BoxMesh.HEAVY, dark);
            m.box(x - 0.215, y - 0.215, can * 0.7, x + 0.215, y + 0.215, can * 0.7 + 0.08, BoxMesh.HEAVY, dark);
            boolean loaded = i >= 8 - s.ammo;
            if (loaded) m.box(x - 0.2, y - 0.2, can, x + 0.2, y + 0.2, can + 0.04, BoxMesh.PLATE, iris ? 0xC8C8B8 : CAP);
            else m.box(x - 0.17, y - 0.17, can - 0.01, x + 0.17, y + 0.17, can + 0.005, BoxMesh.DARK, BLACK);
        }
        m.box(-1.02, -0.02, can * 0.48, 1.02, 1.1, can * 0.52, BoxMesh.HAZARD, 0xC8902A);  // lifting band
        m.pop();
        // hydraulic strut from the bed to the module
        Vec3 base = new Vec3(0, 1.5, -1.6);
        Vec3 top = pivot.add(new Vec3(0, 0.5, can * 0.45).xRot((float) ang));
        strut(m, base, top, 0.11, STEEL);
    }

    private static void strut(BoxMesh m, Vec3 a, Vec3 b, double r, int rgb) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        m.push();
        m.at(a.x, a.y, a.z);
        m.ry(Math.atan2(d.x, d.z));
        m.rx(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
        m.cyl(0, 0, 0, len * 0.55, r * 1.3, r * 1.3, 8, BoxMesh.HEAVY, rgb);
        m.cyl(0, 0, len * 0.5, len, r, r, 8, BoxMesh.JOINT, 0xA0A4A8);
        m.pop();
    }

    // ---- radar trucks -------------------------------------------------------------------------------------------

    private static void shelter(BoxMesh m, int paint, int dark, double z0, double z1, double top) {
        m.box(-1.25, 1.3, z0, 1.25, top, z1, BoxMesh.PLATE, paint);
        m.box(-1.26, 1.6, z0 + 0.4, 1.26, top - 0.4, z0 + 1.6, BoxMesh.VENT, dark);
        m.box(-1.26, 1.5, z1 - 1.4, 1.26, 2.6, z1 - 0.5, BoxMesh.HEAVY, dark);            // door
        m.box(-1.0, top, z0 + 0.3, 1.0, top + 0.12, z1 - 0.3, BoxMesh.HEAVY, dark);
    }

    private static void sampTads(BoxMesh m, State s) {
        truck(m, SAMP, SAMP_DARK, 4.45, false);
        shelter(m, SAMP, SAMP_DARK, -4.3, 2.7, 3.3);
        double spin = s.active ? s.age * 0.35 : 0.6;                                       // Arabel turns fast
        m.push();
        m.at(0, 3.42, -1.0);
        m.cyl(0, 0, 0, 0.01, 0.55, 0.55, 12, BoxMesh.JOINT, STEEL);
        m.push(); m.rx(-Math.PI / 2); m.cyl(0, 0, 0, 0.55, 0.42, 0.36, 12, BoxMesh.HEAVY, SAMP_DARK); m.pop();
        m.at(0, 0.55, 0);
        m.ry(spin);
        m.rx(0.18);
        m.box(-0.95, 0, -0.35, 0.95, 1.9, 0.0, BoxMesh.HEAVY, BLACK);
        m.box(-0.88, 0.07, 0.0, 0.88, 1.83, 0.04, BoxMesh.MESH, 0x3A3E3A);
        m.box(-0.95, 0, -0.4, 0.95, 0.2, -0.35, BoxMesh.HEAVY, SAMP_DARK);
        m.pop();
    }

    private static void irisTads(BoxMesh m, State s) {
        truck(m, IRIS, IRIS_DARK, 4.45, true);
        shelter(m, IRIS, IRIS_DARK, -4.3, 2.7, 2.9);
        double spin = s.active ? s.age * 0.18 : 0;
        m.push();
        m.at(0, 3.0, -2.6);
        m.cyl(0, 0, 0, 0.01, 0.6, 0.6, 12, BoxMesh.JOINT, STEEL);
        m.push(); m.rx(-Math.PI / 2); m.cyl(0, 0, 0, 0.4, 0.45, 0.45, 12, BoxMesh.HEAVY, IRIS_DARK); m.pop();
        m.at(0, 0.4, 0);
        m.ry(spin);
        m.rx(0.12);
        m.box(-1.1, 0, -0.3, 1.1, 2.7, 0.05, BoxMesh.PLATE, IRIS);                       // TRML-4D panel
        m.box(-1.0, 0.1, 0.05, 1.0, 2.6, 0.09, BoxMesh.MESH, 0x4E5848);
        m.box(-0.3, 2.7, -0.2, 0.3, 2.85, 0.0, BoxMesh.HEAVY, IRIS_DARK);
        m.pop();
        for (int side = -1; side <= 1; side += 2) m.box(side * 0.6, 2.9, 1.8, side * 0.6 + 0.05, 6.8, 1.85, BoxMesh.CABLE, STEEL);
    }
}
