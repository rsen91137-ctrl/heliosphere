package com.heliosphere.rocketry.physics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Physical specification of a rocket part block. Masses in tonnes, thrust in kN (vacuum), Isp in seconds.
 * Engine data is modelled on real engines of each class, scaled to one block.
 */
public final class PartSpec {
    public enum Category {
        ENGINE, SOLID_SEGMENT, SOLID_NOZZLE, TANK, DECOUPLER, STRUCTURE, AVIONICS, CAPSULE, HEAT_SHIELD,
        PARACHUTE, LANDING_LEG, NOSE_CONE, FIN, ESCAPE_TOWER, BATTERY, LIFE_SUPPORT
    }

    public final String id;
    public final Category category;
    public final int tier;
    public final double dryMass;
    public final double strength;      // tonnes this part can carry at 1 g
    // engines
    public final double thrustVac, ispSea, ispVac, mixtureRatio;
    public final Propellant fuel, oxidizer;
    // tanks / solid segments
    public final Propellant stores;
    public final double capacity;      // tonnes
    // systems
    public final int navTier;          // avionics: 1 = Earth-Moon, 2 = inner solar system
    public final double lifeSupportDays;
    public final double shieldRatingKmS;

    private PartSpec(Builder b) {
        id = b.id; category = b.category; tier = b.tier; dryMass = b.dryMass; strength = b.strength;
        thrustVac = b.thrustVac; ispSea = b.ispSea; ispVac = b.ispVac; mixtureRatio = b.mixtureRatio;
        fuel = b.fuel; oxidizer = b.oxidizer; stores = b.stores; capacity = b.capacity;
        navTier = b.navTier; lifeSupportDays = b.lifeSupportDays; shieldRatingKmS = b.shieldRatingKmS;
    }

    public boolean isEngine() { return category == Category.ENGINE || category == Category.SOLID_NOZZLE; }
    public double thrustSea() { return ispVac <= 0 ? 0 : thrustVac * ispSea / ispVac; }

    // ------------------------------------------------------------------ registry

    private static final Map<String, PartSpec> SPECS = new LinkedHashMap<>();

    public static PartSpec get(String id) { return SPECS.get(id); }
    public static Collection<PartSpec> all() { return Collections.unmodifiableCollection(SPECS.values()); }

    private static PartSpec reg(Builder b) { PartSpec s = new PartSpec(b); SPECS.put(s.id, s); return s; }
    private static Builder b(String id, Category c, int tier, double mass, double strength) { return new Builder(id, c, tier, mass, strength); }

    // Tier 1 — built from Earth materials, good for the Earth-Moon system
    public static final PartSpec ENGINE_KEROLOX = reg(b("engine_kerolox", Category.ENGINE, 1, 1.3, 60)
            .engine(900, 285, 315, Propellant.RP1, Propellant.LOX, 2.6));
    public static final PartSpec ENGINE_HYDROLOX = reg(b("engine_hydrolox", Category.ENGINE, 1, 0.35, 20)
            .engine(110, 200, 450, Propellant.LH2, Propellant.LOX, 5.5));
    public static final PartSpec SRB_SEGMENT = reg(b("srb_segment", Category.SOLID_SEGMENT, 1, 0.25, 50)
            .tank(Propellant.APCP, 1.6));
    public static final PartSpec SRB_NOZZLE = reg(b("srb_nozzle", Category.SOLID_NOZZLE, 1, 0.3, 50)
            .engine(450, 245, 268, Propellant.APCP, Propellant.NONE, 0));
    public static final PartSpec TANK_RP1 = reg(b("tank_rp1", Category.TANK, 1, 0.06, 40).tank(Propellant.RP1, Propellant.RP1.density));
    public static final PartSpec TANK_LOX = reg(b("tank_lox", Category.TANK, 1, 0.07, 40).tank(Propellant.LOX, Propellant.LOX.density));
    public static final PartSpec TANK_LH2 = reg(b("tank_lh2", Category.TANK, 1, 0.04, 25).tank(Propellant.LH2, Propellant.LH2.density));
    public static final PartSpec DECOUPLER = reg(b("decoupler", Category.DECOUPLER, 1, 0.15, 40));
    public static final PartSpec FRAME = reg(b("rocket_frame", Category.STRUCTURE, 1, 0.08, 60));
    public static final PartSpec AVIONICS_T1 = reg(b("avionics_t1", Category.AVIONICS, 1, 0.3, 15).nav(1));
    public static final PartSpec CAPSULE = reg(b("crew_capsule", Category.CAPSULE, 1, 2.5, 25).life(14));
    public static final PartSpec HEAT_SHIELD = reg(b("heat_shield", Category.HEAT_SHIELD, 1, 0.4, 30).shield(11.3));
    public static final PartSpec PARACHUTE = reg(b("parachute_pack", Category.PARACHUTE, 1, 0.15, 10));
    public static final PartSpec LANDING_LEG = reg(b("landing_leg", Category.LANDING_LEG, 1, 0.12, 20));
    public static final PartSpec NOSE_CONE = reg(b("nose_cone", Category.NOSE_CONE, 1, 0.1, 10));
    public static final PartSpec FIN = reg(b("rocket_fin", Category.FIN, 1, 0.05, 10));
    public static final PartSpec ESCAPE_TOWER = reg(b("escape_tower", Category.ESCAPE_TOWER, 1, 0.6, 10));
    public static final PartSpec BATTERY = reg(b("rocket_battery", Category.BATTERY, 1, 0.1, 15));

    // Tier 2 — needs lunar materials, opens the inner solar system
    public static final PartSpec ENGINE_METHALOX = reg(b("engine_methalox", Category.ENGINE, 2, 1.6, 80)
            .engine(1800, 330, 363, Propellant.CH4, Propellant.LOX, 3.6));
    public static final PartSpec ENGINE_METHALOX_VAC = reg(b("engine_methalox_vac", Category.ENGINE, 2, 1.3, 40)
            .engine(1200, 180, 380, Propellant.CH4, Propellant.LOX, 3.6));
    public static final PartSpec TANK_CH4 = reg(b("tank_ch4", Category.TANK, 2, 0.05, 45).tank(Propellant.CH4, Propellant.CH4.density));
    public static final PartSpec AVIONICS_T2 = reg(b("avionics_t2", Category.AVIONICS, 2, 0.35, 15).nav(2));
    public static final PartSpec LIFE_SUPPORT = reg(b("life_support", Category.LIFE_SUPPORT, 2, 1.2, 20).life(400));
    public static final PartSpec HEAT_SHIELD_T2 = reg(b("heat_shield_t2", Category.HEAT_SHIELD, 2, 0.55, 35).shield(16));

    // Tier 3 — outer Solar System: low-thrust, very high specific impulse, and propellantless sails
    public static final PartSpec PLASMA_THRUSTER = reg(b("plasma_thruster", Category.ENGINE, 3, 0.4, 25)
            .engine(6, 0, 3200, Propellant.ARGON, Propellant.NONE, 0));
    public static final PartSpec TANK_ARGON = reg(b("tank_argon", Category.TANK, 3, 0.08, 45).tank(Propellant.ARGON, Propellant.ARGON.density));
    public static final PartSpec LIGHT_SAIL = reg(b("light_sail", Category.STRUCTURE, 3, 0.15, 10));

    public static final class Builder {
        final String id; final Category category; final int tier; final double dryMass, strength;
        double thrustVac, ispSea, ispVac, mixtureRatio, capacity, lifeSupportDays, shieldRatingKmS;
        Propellant fuel = Propellant.NONE, oxidizer = Propellant.NONE, stores = Propellant.NONE;
        int navTier;

        Builder(String id, Category c, int tier, double mass, double strength) {
            this.id = id; this.category = c; this.tier = tier; this.dryMass = mass; this.strength = strength;
        }
        Builder engine(double thrust, double isl, double ivac, Propellant f, Propellant ox, double of) {
            thrustVac = thrust; ispSea = isl; ispVac = ivac; fuel = f; oxidizer = ox; mixtureRatio = of; return this;
        }
        Builder tank(Propellant p, double cap) { stores = p; capacity = cap; return this; }
        Builder nav(int t) { navTier = t; return this; }
        Builder life(double days) { lifeSupportDays = days; return this; }
        Builder shield(double kms) { shieldRatingKmS = kms; return this; }
    }
}
