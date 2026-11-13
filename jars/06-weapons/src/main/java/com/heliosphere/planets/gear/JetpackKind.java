package com.heliosphere.planets.gear;

/**
 * The jetpacks, each built for the gravity it is meant for. Thrust is in blocks/tick^2 (gravity is 0.08 times the
 * local g); burn is fuel used per tick of thrust (a hydrogen canister gives 1200).
 *
 * <ul>
 *   <li>Standard: Earth-class worlds, up to about 1.5 g.</li>
 *   <li>Heavy-lift: high gravity (the deep levels of the giants, up to 4 g); strong and thirsty.</li>
 *   <li>Low-gravity manoeuvring pack: the Moon, Mars' moons, asteroids. Gentle cold-gas thrusters that also push
 *       sideways, hold you still (sneak) where a jump would carry you off for good, and sip fuel; too weak to lift
 *       you on Earth.</li>
 *   <li>Adaptive: late-game; matches its thrust to the local gravity anywhere, with side thrusters and hold.</li>
 * </ul>
 */
public enum JetpackKind {
    //          id                       thrust  burn  side   hold   maxRise  armour  colour
    STANDARD   ("jetpack",                0.13,  1.0,  0.0,  false, 0.55,    3, 0xB8BCC2),
    HEAVY      ("heavy_jetpack",          0.34,  3.0,  0.0,  false, 0.65,    5, 0x5C6470),
    LOW_G      ("rcs_pack",               0.03,  0.25, 0.025, true,  0.45,    2, 0xE6E6E0),
    ADAPTIVE   ("adaptive_jetpack",      -1.0,   1.5,  0.03, true,  0.7,     6, 0x2E3440);

    public final String id;
    public final double thrust, burn, side, maxRise;
    public final boolean hold;
    public final int armour, colour;

    JetpackKind(String id, double thrust, double burn, double side, boolean hold, double maxRise, int armour, int colour) {
        this.id = id; this.thrust = thrust; this.burn = burn; this.side = side; this.hold = hold; this.maxRise = maxRise;
        this.armour = armour; this.colour = colour;
    }

    /** Upward thrust under gravity {@code g} (in g): fixed, or (adaptive) always enough to climb. */
    public double thrust(double g) { return thrust > 0 ? thrust : 0.08 * g + 0.06; }

    /** The strongest gravity it can still climb in (in g). */
    public double ceiling() { return thrust > 0 ? thrust / 0.08 : 99; }
}
