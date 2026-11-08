package com.heliosphere.sphereworld.terrain;

import com.heliosphere.sphereworld.terrain.ProfileTerrain.Feature;
import com.heliosphere.sphereworld.terrain.ProfileTerrain.Profile;
import com.heliosphere.sphereworld.terrain.ProfileTerrain.Shape;

import java.util.HashMap;
import java.util.Map;

import static com.heliosphere.sphereworld.terrain.Mat.*;

/** Terrain profiles of every solid body beyond the Moon and Mars, with their real landmarks. */
public final class WorldProfiles {
    private static final Map<String, Profile> P = new HashMap<>();

    private WorldProfiles() {}

    public static Profile get(String id) { return P.get(id); }

    private static Profile add(Profile p) { P.put(p.id, p); return p; }

    static {
        // Mercury: heavily cratered, Caloris basin, lobate scarps, sulfide hollows, polar ice in shadowed craters
        add(new Profile("mercury", 2439.7, MERCURY_REGOLITH, MERCURY_CRUST, MERCURY_CRUST).relief(1.8, 0.3, 3).craters(0.9, 1.0)
                .polar(MOON_ICE, 86).craterFloor(MERCURY_CRUST)
                .feature(Feature.of("Caloris Planitia", 30.5, 170.2, 775, Shape.BASIN, 2.0, MERCURY_CRUST))
                .feature(Feature.of("Rachmaninoff", 27.6, 57.6, 145, Shape.BASIN, 3.0, HOLLOWS_DEPOSIT))
                .feature(Feature.of("Discovery Rupes", -55, -38, 220, Shape.CLIFF, 1.5, null))
                .feature(Feature.of("Raditladi", 27.3, 119, 130, Shape.BASIN, 2.5, HOLLOWS_DEPOSIT))
                .ore(HOLLOWS_DEPOSIT, 0.6f, 1, 10).ore(METEORIC_IRON, 0.5f, 2, 30));
        // Venus: volcanic plains, Maxwell Montes, Ishtar and Aphrodite highlands, metal frost on the summits
        add(new Profile("venus", 6051.8, VENUS_REGOLITH, VENUS_BASALT, VENUS_BASALT).relief(1.2, 0.2, 2.5).craters(0.08, 0.6).depths(2, 30)
                .feature(Feature.of("Ishtar Terra", 70, 27, 1500, Shape.PLATEAU, 3.5, TESSERA_ROCK))
                .feature(Feature.of("Maxwell Montes", 65.2, 3.3, 450, Shape.DOME, 7.5, METAL_FROST))
                .feature(Feature.of("Aphrodite Terra", -5, 105, 2500, Shape.PLATEAU, 2.5, TESSERA_ROCK))
                .feature(Feature.of("Maat Mons", 0.5, -165.2, 200, Shape.DOME, 4.5, METAL_FROST))
                .feature(Feature.of("Beta Regio", 25.3, -77, 900, Shape.PLATEAU, 3.0, TESSERA_ROCK))
                .feature(Feature.of("Sif Mons", 22, -8.8, 150, Shape.DOME, 2.0, VENUS_BASALT))
                .ore(METAL_FROST, 0.15f, 1, 6).ore(OLIVINE, 0.4f, 6, 40));
        // Phobos & Deimos: captured carbonaceous rubble
        add(new Profile("phobos", 11.08, PHOBOS_REGOLITH, CARBONACEOUS_ROCK, CARBONACEOUS_ROCK).relief(0.6, 0.05, 2).craters(0.9, 0.9)
                .feature(Feature.of("Stickney", 1, -49, 4.5, Shape.BASIN, 1.2, null)).ore(CARBONACEOUS_ROCK, 0.9f, 1, 40));
        add(new Profile("deimos", 6.2, PHOBOS_REGOLITH, CARBONACEOUS_ROCK, CARBONACEOUS_ROCK).relief(0.3, 0.04, 2).craters(0.5, 0.6)
                .ore(CARBONACEOUS_ROCK, 0.9f, 1, 30));
        // Ceres: hydrated clays, Occator's bright natron spots, Ahuna Mons cryovolcano
        add(new Profile("ceres", 469.7, CERES_REGOLITH, AMMONIATED_CLAY, AMMONIATED_CLAY).relief(1.5, 0.2, 3).craters(0.8, 0.9).polar(MOON_ICE, 80)
                .feature(Feature.of("Occator", 19.8, -120.7, 46, Shape.BASIN, 2.5, NATRON_DEPOSIT))
                .feature(Feature.of("Ahuna Mons", -10.5, -44.1, 10, Shape.DOME, 4.0, NATRON_DEPOSIT))
                .feature(Feature.of("Kerwan", -10.8, 123.9, 140, Shape.BASIN, 1.5, null))
                .ore(NATRON_DEPOSIT, 0.3f, 1, 12).ore(AMMONIATED_CLAY, 0.5f, 4, 40));
        // Vesta: Rheasilvia basin with its 22 km central peak, eucrite and diogenite crust
        add(new Profile("vesta", 262.7, VESTA_REGOLITH, EUCRITE, DIOGENITE).relief(2.5, 0.2, 2.5).craters(0.8, 1.0)
                .feature(Feature.of("Rheasilvia", -75, 301, 250, Shape.BASIN, 6.0, DIOGENITE))
                .feature(Feature.of("Rheasilvia peak", -75, 301, 90, Shape.DOME, 14.0, DIOGENITE))
                .feature(Feature.of("Veneneia", -52, 170, 200, Shape.BASIN, 4.0, null))
                .ore(DIOGENITE, 0.6f, 3, 40).ore(METEORIC_IRON, 0.4f, 2, 20));
        // Io: sulfur plains, lava lakes, giant mountains, no craters at all
        add(new Profile("io", 1821.6, SULFUR_CRUST, SO2_FROST, IO_SILICATE).relief(0.8, 0.2, 4).craters(0, 1).depths(3, 12)
                .feature(Feature.of("Loki Patera", 13, 51, 25, Shape.BASIN, 1.0, IO_SILICATE).lake(LAVA, -0.4))
                .feature(Feature.of("Pele", -18.7, -104.5, 15, Shape.BASIN, 0.8, IO_SILICATE).lake(LAVA, -0.3))
                .feature(Feature.of("Tvashtar Paterae", 63, -124, 15, Shape.BASIN, 0.8, IO_SILICATE).lake(LAVA, -0.3))
                .feature(Feature.of("Boosaule Montes", -5.4, -91.7, 160, Shape.DOME, 17.5, IO_SILICATE))
                .feature(Feature.of("Prometheus", -1.5, -153, 40, Shape.PATCH, 0, SO2_FROST))
                .ore(SULFUR_CRUST, 0.7f, 1, 15).ore(IO_SILICATE, 0.3f, 6, 40));
        // Europa: young ice shell criss-crossed by salt-stained lineae, a liquid ocean ~120 blocks down
        add(new Profile("europa", 1560.8, EUROPA_ICE, EUROPA_ICE, EUROPA_ICE).relief(0.2, 0.1, 4).craters(0.05, 0.4).depths(3, 60)
                .lineae(LINEAE_SALT, 9, 0.2).ocean(WATER, 110, 90)
                .feature(Feature.of("Conamara Chaos", 9.7, -86.6, 40, Shape.PATCH, 0, LINEAE_SALT))
                .feature(Feature.of("Pwyll", -25.2, -88.8, 13, Shape.BASIN, 0.6, null))
                .ore(LINEAE_SALT, 0.6f, 1, 30));
        // Ganymede: dark ancient terrain and bright grooved ice
        add(new Profile("ganymede", 2634.1, GANYMEDE_DARK, GROOVED_ICE, GROOVED_ICE).relief(1.0, 0.2, 3).craters(0.5, 0.7)
                .lineae(GROOVED_ICE, 5, 0.4).polar(GROOVED_ICE, 60)
                .feature(Feature.of("Galileo Regio", 35, -145, 1600, Shape.PATCH, 0, GANYMEDE_DARK))
                .feature(Feature.of("Tros", 11, -27, 47, Shape.BASIN, 1.0, GROOVED_ICE))
                .ore(METEORIC_IRON, 0.3f, 4, 40));
        // Callisto: the most cratered surface in the Solar System, Valhalla multi-ring basin
        add(new Profile("callisto", 2410.3, CALLISTO_REGOLITH, GROOVED_ICE, GROOVED_ICE).relief(0.8, 0.15, 3).craters(1.0, 0.8)
                .feature(Feature.of("Valhalla", 14.7, -56, 300, Shape.BASIN, 1.0, GROOVED_ICE))
                .feature(Feature.of("Asgard", 32.2, -140, 230, Shape.BASIN, 0.8, GROOVED_ICE))
                .ore(CARBONACEOUS_ROCK, 0.4f, 1, 20));
        // Enceladus: snow-white plains, tiger stripes at the south pole venting plumes
        add(new Profile("enceladus", 252.1, ENCELADUS_SNOW, EUROPA_ICE, EUROPA_ICE).relief(0.6, 0.1, 3).craters(0.5, 0.6).ocean(WATER, 90, 50)
                .feature(Feature.of("South Polar Terrain", -90, 0, 160, Shape.PATCH, 0, ENCELADUS_SNOW))
                .lineae(TIGER_STRIPE_ICE, 4, 0.3)
                .ore(TIGER_STRIPE_ICE, 0.5f, 1, 15));
        // Titan: equatorial tholin dunes, Xanadu, northern methane seas
        add(new Profile("titan", 2574.73, THOLIN_SAND, TITAN_ICE_BEDROCK, WATER_ICE_BEDROCK).relief(0.6, 0.12, 3).craters(0.03, 0.4).depths(4, 20)
                .dunes(THOLIN_SAND, 30)
                .feature(Feature.of("Xanadu", -10, -100, 1800, Shape.PLATEAU, 0.6, TITAN_ICE_BEDROCK))
                .feature(Feature.of("Kraken Mare", 68, -50, 600, Shape.BASIN, 0.6, THOLIN_DEPOSIT).lake(LIQUID_METHANE, -0.1))
                .feature(Feature.of("Ligeia Mare", 79, -110, 250, Shape.BASIN, 0.5, THOLIN_DEPOSIT).lake(LIQUID_METHANE, -0.1))
                .feature(Feature.of("Punga Mare", 85, -20, 190, Shape.BASIN, 0.4, THOLIN_DEPOSIT).lake(LIQUID_METHANE, -0.08))
                .feature(Feature.of("Ontario Lacus", -72, -175, 120, Shape.BASIN, 0.3, THOLIN_DEPOSIT).lake(LIQUID_METHANE, -0.06))
                .ore(THOLIN_DEPOSIT, 0.7f, 1, 10));
        // Miranda: patchwork coronae and Verona Rupes, the tallest cliff known
        add(new Profile("miranda", 235.8, MIRANDA_ICE, CORONA_RIDGE, MIRANDA_ICE).relief(1.5, 0.15, 3).craters(0.6, 0.8)
                .lineae(CORONA_RIDGE, 6, 0.5)
                .feature(Feature.of("Verona Rupes", -18, -12, 60, Shape.CLIFF, 12.0, CORONA_RIDGE))
                .feature(Feature.of("Inverness Corona", -66, 325, 100, Shape.PATCH, 0, CORONA_RIDGE)));
        // Triton: cantaloupe terrain, nitrogen ice cap, geysers
        add(new Profile("triton", 1353.4, NITROGEN_ICE, CANTALOUPE_TERRAIN, WATER_ICE_BEDROCK).relief(0.5, 0.15, 4).craters(0.1, 0.5)
                .lineae(CANTALOUPE_TERRAIN, 12, 0.15)
                .feature(Feature.of("Bubembe Regio", 20, -30, 900, Shape.PATCH, 0, CANTALOUPE_TERRAIN))
                .feature(Feature.of("Ruach Planitia", 25, 25, 200, Shape.BASIN, 0.5, NITROGEN_ICE))
                .ore(NITROGEN_ICE, 0.7f, 1, 10));
        // Pluto: Sputnik Planitia (the heart), al-Idrisi water-ice mountains, Tartarus bladed methane ice, red Cthulhu
        add(new Profile("pluto", 1188.3, THOLIN_CRUST, WATER_ICE_BEDROCK, WATER_ICE_BEDROCK).relief(1.2, 0.2, 3).craters(0.4, 0.6)
                .feature(Feature.of("Sputnik Planitia", 25, 175, 520, Shape.BASIN, 3.0, NITROGEN_ICE))
                .feature(Feature.of("Tombaugh Regio", 0, 180, 900, Shape.PATCH, 0, NITROGEN_ICE))
                .feature(Feature.of("al-Idrisi Montes", 38, 155, 120, Shape.PLATEAU, 3.5, WATER_ICE_BEDROCK))
                .feature(Feature.of("Tartarus Dorsa", 20, -135, 400, Shape.PLATEAU, 2.0, METHANE_ICE))
                .feature(Feature.of("Wright Mons", -21, 173, 75, Shape.DOME, 4.0, WATER_ICE_BEDROCK))
                .ore(NITROGEN_ICE, 0.5f, 1, 8).ore(METHANE_ICE, 0.5f, 1, 15).ore(THOLIN_CRUST, 0.4f, 1, 4));
        // Charon: grey ice, red Mordor Macula cap, Serenity Chasma
        add(new Profile("charon", 606, CHARON_REGOLITH, WATER_ICE_BEDROCK, WATER_ICE_BEDROCK).relief(1.5, 0.2, 3).craters(0.5, 0.8)
                .feature(Feature.of("Mordor Macula", 85, 0, 230, Shape.PATCH, 0, THOLIN_CRUST))
                .feature(Feature.of("Serenity Chasma", 15, 30, 180, Shape.BASIN, 2.5, null))
                .ore(THOLIN_CRUST, 0.3f, 1, 4));
        // Eris: bright methane frost
        add(new Profile("eris", 1163, ERIS_FROST, METHANE_ICE, WATER_ICE_BEDROCK).relief(0.8, 0.15, 3).craters(0.4, 0.6)
                .ore(METHANE_ICE, 0.6f, 1, 15).ore(NITROGEN_ICE, 0.4f, 1, 8));
    }
}
