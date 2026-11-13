package com.heliosphere.planets.defense.sam;

/**
 * Warheads of the machines' missiles by technology level (1-10). Every one breaks blocks; the crater and the damage
 * grow with the level. Levels 1-4 stop at obsidian and ancient debris, level 5 and up cut through them, and the top
 * levels blow out a crater like a few dozen TNT going off together (radius 13 at level 10; one TNT is about 4).
 */
public final class MachineWarheads {
    private MachineWarheads() {}

    private static final double[] CRATER = {0, 2.2, 2.6, 3.0, 3.5, 4.5, 5.5, 7.0, 8.5, 10.5, 13.0};

    public static int clamp(int tier) { return Math.max(1, Math.min(10, tier)); }

    /** Radius of the crater in blocks. */
    public static double crater(int tier) { return CRATER[clamp(tier)]; }

    /** Highest explosion resistance a warhead of this level breaks (obsidian and ancient debris: 1200). */
    public static float resistanceLimit(int tier) { return clamp(tier) >= 5 ? 1300f : 6.5f; }

    /** Radius within which living things are hurt. */
    public static double hurtRadius(int tier, double specBlast) { return Math.max(specBlast, crater(tier) * 1.15); }

    public static float damageScale(int tier) { return 1f + 0.15f * (clamp(tier) - 1); }
}
