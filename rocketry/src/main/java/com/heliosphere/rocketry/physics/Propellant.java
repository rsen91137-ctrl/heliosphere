package com.heliosphere.rocketry.physics;

/** Propellants. Density in tonnes per cubic metre (one tank block = 1 m³). */
public enum Propellant {
    NONE("无", 0),
    RP1("RP-1 火箭煤油", 0.81),
    LOX("液氧", 1.14),
    LH2("液氢", 0.071),
    CH4("液甲烷", 0.42),
    APCP("高氯酸铵复合固体推进剂", 1.75);

    public final String nameZh;
    public final double density;

    Propellant(String nameZh, double density) { this.nameZh = nameZh; this.density = density; }
}
