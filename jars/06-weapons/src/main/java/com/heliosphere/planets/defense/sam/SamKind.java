package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The six vehicles of the three air-defence systems. Each battery is a radar vehicle (TADS) and launchers
 * (TEL/TELAR); a launcher within {@link #LINK} blocks of a radar of its system engages at full range, without one
 * it is limited to its own sensor (the Buk TELAR has its own tracking radar, the IRIS-T missile its own infra-red
 * seeker, the SAMP/T launcher none).
 *
 * <p>Vehicle frame: origin at the centre of the master block at ground level, +Z towards the front (cab), blocks.
 */
public enum SamKind {
    //        id            system  radar  L  W  H  missile                 mag interval reload energy own  linked radarRange damage
    BUK_TELAR("buk_telar",  "buk",  false, 7, 3, 2, MissileSpec.BUK_9M317MA, 6,  40,     300,   1500,  64,  150,  0,   400f),
    BUK_TADS ("buk_tads",   "buk",  true,  7, 3, 2, null,                    0,   0,       0,      0,   0,    0, 190,     0f),
    SAMP_TEL ("samp_tel",   "samp", false, 9, 3, 2, MissileSpec.ASTER_30,    8,  15,     400,   2000,   0,  180,  0,   320f),
    SAMP_TADS("samp_tads",  "samp", true,  9, 3, 3, null,                    0,   0,       0,      0,   0,    0, 210,     0f),
    IRIS_TEL ("iris_tel",   "iris", false, 9, 3, 2, MissileSpec.IRIS_T_SL,   8,  20,     240,   1200,  40,  120,  0,   240f),
    IRIS_TADS("iris_tads",  "iris", true,  9, 3, 3, null,                    0,   0,       0,      0,   0,    0, 160,     0f);

    /** How far a launcher looks for its radar. */
    public static final int LINK = 48;

    public final String id, system;
    public final boolean radar;
    public final int length, width, height, magazine, interval, reload, energy, ownRange, linkedRange, radarRange;
    public final MissileSpec missile;
    public final float damage;

    SamKind(String id, String system, boolean radar, int length, int width, int height, MissileSpec missile, int magazine, int interval,
            int reload, int energy, int ownRange, int linkedRange, int radarRange, float damage) {
        this.id = id; this.system = system; this.radar = radar; this.length = length; this.width = width; this.height = height;
        this.missile = missile; this.magazine = magazine; this.interval = interval; this.reload = reload; this.energy = energy;
        this.ownRange = ownRange; this.linkedRange = linkedRange; this.radarRange = radarRange; this.damage = damage;
    }

    public boolean vertical() { return this == SAMP_TEL || this == IRIS_TEL; }

    /** Every block the vehicle stands on, the master included. */
    public List<BlockPos> footprint(BlockPos master, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        int hl = (length - 1) / 2, hw = (width - 1) / 2;
        for (int dz = -hl; dz <= length - 1 - hl; dz++)
            for (int dx = -hw; dx <= width - 1 - hw; dx++)
                for (int dy = 0; dy < height; dy++)
                    out.add(master.offset(rotate(dx, dz, facing)[0], dy, rotate(dx, dz, facing)[1]));
        return out;
    }

    /** Local (x, z) rotated to world offsets for a vehicle facing {@code f}. */
    public static int[] rotate(int x, int z, Direction f) {
        return switch (f) {
            case SOUTH -> new int[]{x, z};
            case NORTH -> new int[]{-x, -z};
            case EAST -> new int[]{z, -x};
            default -> new int[]{-z, x};            // WEST
        };
    }

    /** Local vector to world for a vehicle facing {@code f} (rotation about Y). */
    public static Vec3 toWorld(Vec3 v, Direction f) {
        return switch (f) {
            case SOUTH -> v;
            case NORTH -> new Vec3(-v.x, v.y, -v.z);
            case EAST -> new Vec3(v.z, v.y, -v.x);
            default -> new Vec3(-v.z, v.y, v.x);
        };
    }

    /** World offset back to the vehicle frame. */
    public static Vec3 toLocal(Vec3 w, Direction f) {
        return switch (f) {
            case SOUTH -> w;
            case NORTH -> new Vec3(-w.x, w.y, -w.z);
            case EAST -> new Vec3(-w.z, w.y, w.x);
            default -> new Vec3(w.z, w.y, -w.x);
        };
    }

    // ---- launcher geometry (shared with the renderer) ----

    /** Buk: turret pivot, launcher arm pivot (in the turret frame), tube length and spacing. */
    public static final Vec3 BUK_TURRET = new Vec3(0, 2.05, -0.7), BUK_ARM = new Vec3(0, 0.55, -1.9);
    public static final double BUK_TUBE = 5.6, BUK_TUBE_BACK = 0.5;
    /** Vertical launchers: pivot at the rear of the bed; canisters lie forward when stowed and stand up when raised. */
    public static final Vec3 VLS_PIVOT_SAMP = new Vec3(0, 1.55, -4.1), VLS_PIVOT_IRIS = new Vec3(0, 1.5, -4.1);
    public static final double SAMP_CAN = 5.4, IRIS_CAN = 3.8;

    /** Position of tube {@code i}'s mouth in the vehicle frame. yaw/elev in radians (Buk), raise 0..1. */
    public Vec3 mouth(int i, double yaw, double elev, double raise) {
        if (this == BUK_TELAR) {
            double x = (i % 3 - 1) * 0.52, y = 0.28 + (i / 3) * 0.52;
            Vec3 p = new Vec3(x, y, BUK_TUBE - BUK_TUBE_BACK).xRot((float) elev);
            p = p.add(BUK_ARM).yRot((float) yaw);
            return p.add(BUK_TURRET);
        }
        Vec3 pivot = this == SAMP_TEL ? VLS_PIVOT_SAMP : VLS_PIVOT_IRIS;
        double len = this == SAMP_TEL ? SAMP_CAN : IRIS_CAN;
        double x = (i % 4 - 1.5) * 0.46, y = 0.3 + (i / 4) * 0.48;
        Vec3 p = new Vec3(x, y, len).xRot((float) (raise * Math.PI / 2));
        return p.add(pivot);
    }

    /** Direction a missile leaves tube {@code i} in, vehicle frame. */
    public Vec3 axis(double yaw, double elev, double raise) {
        if (this == BUK_TELAR) return new Vec3(0, 0, 1).xRot((float) elev).yRot((float) yaw);
        return new Vec3(0, 0, 1).xRot((float) (raise * Math.PI / 2));
    }

    public static SamKind byId(String id) {
        for (SamKind k : values()) if (k.id.equals(id)) return k;
        return BUK_TELAR;
    }
}
