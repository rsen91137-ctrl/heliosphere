package com.heliosphere.sphereworld.terrain;

/** Abstract terrain materials; each planet generator maps them to block states. */
public enum Mat {
    AIR, BEDROCK,
    // Moon
    REGOLITH, MARE_REGOLITH, HE3_REGOLITH, ANORTHOSITE, MARE_BASALT, HIGH_TI_BASALT, KREEP, MOON_ICE, METEORIC_IRON,
    // Mars
    MARS_REGOLITH, MARS_SAND, MARS_ROCK, MARS_BASALT, PERCHLORATE, HEMATITE, GYPSUM, CLAY, MARS_ICE, DRY_ICE, OLIVINE;

    private static final Mat[] VALUES = values();
    public static Mat byId(int id) { return VALUES[id]; }
}
