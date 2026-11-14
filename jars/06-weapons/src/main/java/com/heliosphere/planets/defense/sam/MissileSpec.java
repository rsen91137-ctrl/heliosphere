package com.heliosphere.planets.defense.sam;

/**
 * Flight data of every guided missile, after the real weapons but scaled to Minecraft: speeds in blocks per tick
 * (5 b/t = 100 blocks a second, fast enough to feel like a SAM without crossing a loaded area in a blink), overload
 * in multiples of {@link #G} = 0.04 b/t². What a missile can do follows from these numbers alone: it turns with
 * {@code gLimit} at full speed but less when slow (lift falls with the square of speed), its autopilot answers a
 * command only after {@code tau} ticks, every hard turn bleeds speed, it loses its target outside its seeker cone
 * or when the target stays hidden behind blocks for {@code memory} ticks, and its motor burns out.
 */
public enum MissileSpec {
    //               model                ign boost bAcc   sus  sAcc   drag    vRef  G   tau  N    fov  mem fuse blast guide tvc   grav  life launch mixed seek
    BUK_9M317MA     (Model.BUK,           0,  30, 0.170, 110, 0.088, 0.0035, 5.0, 30, 3.0, 4.0, 120, 20, 3.5, 6.0,  6, 0.00, 0.012, 400, 0.8, false, 0),
    ASTER_30        (Model.ASTER,         5,  22, 0.280,  95, 0.095, 0.0032, 5.4, 50, 1.5, 4.5, 140, 25, 2.0, 3.5,  5, 0.35, 0.012, 420, 0.7, false, 0),
    IRIS_T_SL       (Model.IRIS,          2,  26, 0.150,  80, 0.060, 0.0045, 3.6, 40, 2.0, 4.0, 180,  6, 3.0, 4.5,  3, 0.30, 0.012, 380, 0.6, false, 48),
    TURRET          (Model.TURRET,        0,  20, 0.140,  30, 0.050, 0.0050, 3.0, 25, 3.0, 3.5, 100, 10, 2.5, 4.5,  3, 0.15, 0.012, 220, 0.6, false, 40),
    PLAYER          (Model.PLAYER,        0,  18, 0.130,  40, 0.045, 0.0050, 2.8, 22, 3.0, 3.5,  70, 10, 2.0, 4.0,  2, 0.10, 0.012, 240, 0.9, true,  64),
    MACHINE_LIGHT   (Model.MACHINE,       0,  15, 0.070,  25, 0.012, 0.0060, 1.35, 7, 6.0, 3.0,  60,  8, 1.4, 3.0,  4, 0.00, 0.010, 160, 0.4, true,  32),
    MACHINE_MEDIUM  (Model.MACHINE,       0,  16, 0.085,  30, 0.016, 0.0055, 1.65, 9, 5.0, 3.0,  70,  8, 1.5, 3.2,  4, 0.00, 0.010, 180, 0.45, true, 36),
    MACHINE_HEAVY   (Model.MACHINE_HEAVY, 0,  18, 0.100,  35, 0.020, 0.0050, 2.0, 12, 4.0, 3.5,  80, 10, 1.6, 3.5,  4, 0.00, 0.010, 200, 0.5, true,  40),
    MACHINE_ELITE   (Model.MACHINE_HEAVY, 0,  20, 0.110,  40, 0.024, 0.0048, 2.3, 14, 4.0, 3.5,  90, 12, 1.8, 4.0,  4, 0.00, 0.010, 220, 0.55, true, 44),
    ASTER_BOOSTER   (Model.ASTER_BOOSTER, 999, 0, 0,      0,  0,     0.0100, 1.0,  0, 1.0, 0,     0,  0, 0,   0,  999, 0.00, 0.040, 240, 0,   false, 0),
    /** The tactical nuke's warhead rocket: unguided, straight and fast, good for 5000 blocks. */
    NUKE_ROCKET     (Model.NUKE,          0,  20, 0.300, 700, 0.140, 0.0025, 7.5,  0, 1.0, 0,     0,  0, 1.5, 0,  999, 0.00, 0.000, 720, 2.0, false, 0),
    /** A hit-to-kill interceptor (machine fortresses' point defence): very fast, very agile, kills missiles only. */
    INTERCEPTOR     (Model.IRIS,          0,  16, 0.420,  70, 0.150, 0.0024, 9.5, 70, 1.0, 5.0, 180, 40, 5.0, 3.0,  2, 0.60, 0.012, 260, 1.2, false, 0);

    /** One g of overload, in blocks per tick squared. */
    public static final double G = 0.04;

    public enum Model { BUK, ASTER, ASTER_BOOSTER, IRIS, TURRET, PLAYER, MACHINE, MACHINE_HEAVY, NUKE }

    public final Model model;
    public final int ignite, boost, sustain, memory, guideStart, life, fov, seekRange;
    public final double boostAcc, sustainAcc, drag, vRef, gLimit, tau, nav, fuse, blast, tvc, gravity, launchSpeed;
    public final boolean mixed;

    MissileSpec(Model model, int ignite, int boost, double boostAcc, int sustain, double sustainAcc, double drag, double vRef, int g,
                double tau, double nav, int fov, int memory, double fuse, double blast, int guideStart, double tvc, double gravity,
                int life, double launchSpeed, boolean mixed, int seekRange) {
        this.model = model; this.ignite = ignite; this.boost = boost; this.boostAcc = boostAcc; this.sustain = sustain; this.sustainAcc = sustainAcc;
        this.drag = drag; this.vRef = vRef; this.gLimit = g * G; this.tau = tau; this.nav = nav; this.fov = fov; this.memory = memory;
        this.fuse = fuse; this.blast = blast; this.guideStart = guideStart; this.tvc = tvc; this.gravity = gravity; this.life = life;
        this.launchSpeed = launchSpeed; this.mixed = mixed; this.seekRange = seekRange;
    }

    /** 0 motor off, 1 sustainer, 2 boost. */
    public int motor(int age) {
        if (age < ignite) return 0;
        if (age < ignite + boost) return 2;
        if (age < ignite + boost + sustain) return 1;
        return 0;
    }

    public double thrust(int age) {
        return switch (motor(age)) { case 2 -> boostAcc; case 1 -> sustainAcc; default -> 0; };
    }

    public boolean guided() { return gLimit > 0; }

    /** The machine missile for a robot of this technology level (1-10). */
    public static MissileSpec machine(int tier) {
        if (tier >= 9) return MACHINE_ELITE;
        if (tier >= 7) return MACHINE_HEAVY;
        if (tier >= 5) return MACHINE_MEDIUM;
        return MACHINE_LIGHT;
    }
}
