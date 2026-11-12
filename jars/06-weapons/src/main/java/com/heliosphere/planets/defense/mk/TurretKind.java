package com.heliosphere.planets.defense.mk;

/** The seven turrets: base numbers at Mk 0 (an un-upgraded turret). */
public enum TurretKind {
    //          id                   range cd  damage energy  target         colour
    LASER     ("laser_turret",        40, 10,  9f,    60,  Target.ALL,    0xFF5040),
    SONIC     ("sonic_turret",        22, 30,  8f,   160,  Target.ALL,    0x8AE8FF),
    AUTOCANNON("autocannon_turret",   48,  3,  4f,    15,  Target.ALL,    0xFFC878),
    FLAK      ("flak_turret",         64, 14, 10f,    70,  Target.AIR,    0xFFB060),
    MISSILE   ("missile_turret",      90, 50, 30f,   300,  Target.ALL,    0xFF7030),
    RAIL      ("rail_turret",        120, 60, 60f,   400,  Target.ALL,    0xA8E0FF),
    TESLA     ("tesla_turret",        24, 16, 12f,   120,  Target.ALL,    0x9AA8FF);

    public enum Target { ALL, AIR }

    public final String id;
    public final int range, cooldown, energy, colour;
    public final float damage;
    public final Target target;

    TurretKind(String id, int range, int cooldown, float damage, int energy, Target target, int colour) {
        this.id = id; this.range = range; this.cooldown = cooldown; this.damage = damage; this.energy = energy; this.target = target; this.colour = colour;
    }

    // ---- upgrade levels: Mk 0 (none) to Mk 5 ----
    public static final int MAX_MK = 5;
    private static final float[] DAMAGE = {1f, 1.25f, 1.5f, 1.85f, 2.3f, 2.8f};
    private static final float[] RATE = {1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.55f};
    /** Mk V is the long-range refit: two and a half times the reach. */
    private static final float[] RANGE = {1f, 1.1f, 1.2f, 1.3f, 1.45f, 2.6f};
    private static final long[] CAPACITY = {4000, 6000, 9000, 14000, 20000, 40000};

    public double range(int mk) { return range * RANGE[clamp(mk)]; }
    public float damage(int mk) { return damage * DAMAGE[clamp(mk)]; }
    public int cooldown(int mk) { return Math.max(2, Math.round(cooldown * RATE[clamp(mk)])); }
    public long capacity(int mk) { return CAPACITY[clamp(mk)]; }

    private static int clamp(int mk) { return Math.max(0, Math.min(MAX_MK, mk)); }

    public static TurretKind byId(String id) {
        for (TurretKind k : values()) if (k.id.equals(id)) return k;
        return LASER;
    }
}
