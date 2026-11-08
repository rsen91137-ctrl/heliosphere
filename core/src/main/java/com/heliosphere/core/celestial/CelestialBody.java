package com.heliosphere.core.celestial;

/**
 * Real physical data of a solar-system body. All game sizes derive from
 * {@link #SCALE}: 1 block = 100 m real, i.e. sizes and distances are 1/100 of km values * 1000.
 */
public final class CelestialBody {
    /** Blocks per real kilometre (1/100 scale: 1 km real = 10 blocks). */
    public static final double BLOCKS_PER_KM = 10.0;
    public static final double AU_KM = 149_597_870.7;

    public enum Kind { STAR, PLANET, DWARF_PLANET, MOON, ASTEROID }

    public final String id;
    public final String nameZh;
    public final String nameEn;
    public final Kind kind;
    public final String parent;          // null for the Sun
    public final double radiusKm;
    public final double orbitKm;         // semi-major axis around parent
    public final double orbitDays;       // sidereal orbital period (negative = retrograde)
    public final double inclinationDeg;
    public final double ascendingNodeDeg;
    public final double meanLongitudeDeg; // at epoch
    public final double solarDayHours;   // length of local solar day (negative = retrograde)
    public final double surfaceGravity;  // in g
    public final double pressureBar;
    public final boolean breathable;
    public final double tempDayC;
    public final double tempNightC;
    public final int color;              // average albedo colour, 0xRRGGBB
    public final boolean gasGiant;
    public final boolean tidallyLocked;
    public final double ringInnerKm;
    public final double ringOuterKm;

    CelestialBody(Builder b) {
        this.id = b.id; this.nameZh = b.nameZh; this.nameEn = b.nameEn; this.kind = b.kind; this.parent = b.parent;
        this.radiusKm = b.radiusKm; this.orbitKm = b.orbitKm; this.orbitDays = b.orbitDays;
        this.inclinationDeg = b.inclinationDeg; this.ascendingNodeDeg = b.ascendingNodeDeg; this.meanLongitudeDeg = b.meanLongitudeDeg;
        this.solarDayHours = b.solarDayHours; this.surfaceGravity = b.surfaceGravity; this.pressureBar = b.pressureBar;
        this.breathable = b.breathable; this.tempDayC = b.tempDayC; this.tempNightC = b.tempNightC; this.color = b.color;
        this.gasGiant = b.gasGiant; this.tidallyLocked = b.tidallyLocked; this.ringInnerKm = b.ringInnerKm; this.ringOuterKm = b.ringOuterKm;
    }

    /** Diameter in blocks at 1/100 scale. */
    public double diameterBlocks() { return radiusKm * 2 * BLOCKS_PER_KM; }
    public double radiusBlocks() { return radiusKm * BLOCKS_PER_KM; }
    /** Equatorial circumference in blocks: width of the surface map (east-west). */
    public double circumferenceBlocks() { return Math.PI * diameterBlocks(); }
    /** Pole-to-pole length of the surface map (north-south). */
    public double meridianBlocks() { return circumferenceBlocks() / 2.0; }
    /** Ratio of the body's solar day to an Earth day (vanilla day = 20 min). */
    public double dayLengthFactor() { return Math.abs(solarDayHours) / 24.0; }

    public static Builder builder(String id, String zh, String en, Kind kind) { return new Builder(id, zh, en, kind); }

    public static final class Builder {
        final String id, nameZh, nameEn; final Kind kind;
        String parent; double radiusKm, orbitKm, orbitDays = 1, inclinationDeg, ascendingNodeDeg, meanLongitudeDeg;
        double solarDayHours = 24, surfaceGravity = 1, pressureBar, tempDayC, tempNightC; boolean breathable, gasGiant, tidallyLocked;
        int color = 0xAAAAAA; double ringInnerKm, ringOuterKm;
        Builder(String id, String zh, String en, Kind kind) { this.id = id; this.nameZh = zh; this.nameEn = en; this.kind = kind; }
        public Builder parent(String p) { parent = p; return this; }
        public Builder radius(double km) { radiusKm = km; return this; }
        public Builder orbitAu(double au, double days) { orbitKm = au * AU_KM; orbitDays = days; return this; }
        public Builder orbitKm(double km, double days) { orbitKm = km; orbitDays = days; return this; }
        public Builder plane(double inc, double node, double meanLon) { inclinationDeg = inc; ascendingNodeDeg = node; meanLongitudeDeg = meanLon; return this; }
        public Builder day(double hours) { solarDayHours = hours; return this; }
        public Builder gravity(double g) { surfaceGravity = g; return this; }
        public Builder air(double bar, boolean canBreathe) { pressureBar = bar; breathable = canBreathe; return this; }
        public Builder temp(double day, double night) { tempDayC = day; tempNightC = night; return this; }
        public Builder color(int rgb) { color = rgb; return this; }
        public Builder gasGiant() { gasGiant = true; return this; }
        public Builder locked() { tidallyLocked = true; return this; }
        public Builder rings(double innerKm, double outerKm) { ringInnerKm = innerKm; ringOuterKm = outerKm; return this; }
        public CelestialBody build() { return new CelestialBody(this); }
    }
}
