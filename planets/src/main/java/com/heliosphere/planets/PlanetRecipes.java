package com.heliosphere.planets;

import com.heliosphere.core.Reg;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import static com.heliosphere.planets.PlanetsMod.*;

/**
 * Off-world processing. Lunar materials gate the second technology tier: Mars-capable engines need
 * lunar high-titanium alloy, deep-space avionics need vacuum-grown wafers and lunar rare earths.
 */
public final class PlanetRecipes {
    private PlanetRecipes() {}

    static Item I(String id) { return Reg.get(id); }
    static MachineRecipe r() { return MachineRecipe.of(); }
    static MachineType M(String id) { return MachineType.get(id); }

    public static void register() {
        // ---------------------------------------------------------------- oxygen
        OXYGEN_COMPRESSOR.recipe(r().in(I("empty_canister")).out(I("canister_oxygen")).time(160).power(40)
                .when((l, p) -> Environment.breathable(l, p) ? null : "condition.heliosphere.needs_air"));
        OXYGEN_COMPRESSOR.recipe(r().in(I("canister_lox")).in(I("empty_canister")).out(I("canister_oxygen"), 2).time(120).power(30));

        // ---------------------------------------------------------------- Moon
        REGOLITH_PROCESSOR.recipe(r().in(I("helium3_regolith"), 8).in(I("empty_canister")).chance(I("canister_helium3"), 1, 0.5f)
                .out(I("moon_regolith"), 4).time(400).power(120));
        REGOLITH_PROCESSOR.recipe(r().in(I("moon_regolith"), 4).out(I("lunar_glass"), 2).time(200).power(80));
        REGOLITH_PROCESSOR.recipe(r().in(I("mare_regolith"), 4).out(I("lunar_glass"), 2).chance(I("lunar_iron"), 1, 0.3f).time(200).power(80));
        REGOLITH_PROCESSOR.recipe(r().in(I("anorthosite"), 2).out(I("lunar_ceramic"), 2).time(240).power(90));
        REGOLITH_PROCESSOR.recipe(r().in(I("kreep_rock"), 2).out(I("kreep_concentrate"), 2).time(240).power(90));
        REGOLITH_PROCESSOR.recipe(r().in(I("meteoric_iron_ore")).out(I("meteoric_iron"), 2).time(160).power(60));
        ILMENITE_REDUCER.recipe(r().in(I("high_ti_basalt"), 2).in(I("canister_hydrogen")).in(I("empty_canister"))
                .out(I("lunar_titanium_ingot")).out(I("lunar_iron")).out(I("canister_lox")).time(400).power(160));
        VACUUM_DEPOSITION.recipe(r().in(I("silicon_wafer"), 2).out(I("vacuum_wafer"), 2).time(400).power(120));
        ISRU_ELECTROLYZER.recipe(r().in(I("lunar_ice_chunk"), 3).in(I("empty_canister"), 2).out(I("canister_lh2")).out(I("canister_lox")).time(300).power(140));
        ISRU_ELECTROLYZER.recipe(r().in(I("mars_ice_chunk"), 3).in(I("empty_canister"), 2).out(I("canister_lh2")).out(I("canister_lox"))
                .chance(I("heavy_water"), 1, 0.25f).time(300).power(140));
        ISRU_ELECTROLYZER.recipe(r().in(I("heavy_water"), 2).in(I("empty_canister")).out(I("canister_deuterium")).out(I("canister_oxygen")).time(400).power(200));

        // ---------------------------------------------------------------- Mars
        ATMOSPHERE_EXTRACTOR.recipe(r().in(I("empty_canister"), 3).out(I("canister_co2"), 2).chance(I("canister_argon"), 1, 0.35f)
                .chance(I("canister_ln2"), 1, 0.25f).time(240).power(100));
        SABATIER.recipe(r().in(I("canister_co2")).in(I("canister_hydrogen"), 2).out(I("canister_ch4")).out(I("empty_canister"), 2).time(240).power(80));
        SABATIER.recipe(r().in(I("perchlorate_salt"), 3).in(I("canister_ammonia")).out(I("ammonium_perchlorate"), 3).out(I("empty_canister")).time(200).power(60));

        // ---------------------------------------------------------------- lunar materials in Earth machines
        M("alloy_furnace").recipe(r().in(I("lunar_titanium_ingot"), 3).in(I("aluminum_ingot")).in(I("vanadium_ingot")).out(I("lunar_ti_alloy_ingot"), 4).time(260).power(90));
        M("alloy_furnace").recipe(r().in(I("meteoric_iron"), 2).in(I("nickel_ingot")).out(I("steel_ingot"), 3).time(160).power(50));
        M("vacuum_furnace").recipe(r().in(I("lunar_glass"), 2).out(I("lunar_glass_fiber"), 4).time(200).power(80));
        M("vacuum_furnace").recipe(r().in(I("lunar_iron"), 2).out(Items.IRON_INGOT, 2).time(120).power(40));
        M("chemical_reactor").recipe(r().in(I("kreep_concentrate"), 2).in(I("sulfuric_acid")).out(I("lunar_rare_earth"), 2).chance(I("thorium"), 1, 0.3f).time(240).power(70));
        M("chemical_reactor").recipe(r().in(I("hematite_nodule"), 2).in(Items.COAL).out(Items.IRON_INGOT, 2).time(160).power(40));
        M("chemical_reactor").recipe(r().in(I("gypsum_crystal"), 2).in(Items.WATER_BUCKET).out(I("sulfuric_acid")).time(200).power(50));
        M("electronics_assembler").recipe(r().in(I("vacuum_wafer")).in(I("lunar_rare_earth")).in(Items.GOLD_INGOT).out(I("rad_hard_chip")).time(300).power(90));
        M("electronics_assembler").recipe(r().in(I("rad_hard_chip"), 2).in(I("guidance_computer")).in(I("lunar_rare_earth")).out(I("deep_space_computer")).time(400).power(120));
        M("metal_3d_printer").recipe(r().in(I("grcop_ingot"), 3).in(I("lunar_ti_alloy_ingot"), 2).out(I("methalox_chamber")).time(500).power(180).precise());
        M("assembly_station").recipe(r().in(I("turbine_blade"), 4).in(I("si3n4_bearing"), 2).in(I("inducer")).in(I("lunar_ti_alloy_ingot"), 3).in(I("fluoroelastomer"))
                .out(I("lunar_turbopump")).time(500).power(140).precise());

        // ---------------------------------------------------------------- space suits
        MachineType asm = M("assembly_station");
        asm.recipe(r().in(I("plastic"), 2).in(Items.GLASS_PANE, 2).in(I("al_li_plate")).in(I("circuit_board")).out(I("space_suit_helmet")).time(300).power(60));
        asm.recipe(r().in(I("nylon"), 6).in(I("copv")).in(I("battery_cell")).in(I("circuit_board")).in(I("al_li_plate")).out(I("space_suit_chestplate")).time(400).power(80));
        asm.recipe(r().in(I("nylon"), 5).in(I("al_li_plate")).in(I("fluoroelastomer")).out(I("space_suit_leggings")).time(300).power(60));
        asm.recipe(r().in(I("nylon"), 3).in(I("fluoroelastomer"), 2).in(I("al_li_plate")).out(I("space_suit_boots")).time(240).power(60));
        asm.recipe(r().in(I("space_suit_helmet")).in(I("lunar_glass"), 2).in(I("rad_hard_chip")).out(I("mars_suit_helmet")).time(400).power(80));
        asm.recipe(r().in(I("space_suit_chestplate")).in(I("lunar_glass_fiber"), 6).in(I("copv"), 2).in(I("rad_hard_chip")).out(I("mars_suit_chestplate")).time(500).power(100));
        asm.recipe(r().in(I("space_suit_leggings")).in(I("lunar_glass_fiber"), 4).in(I("lunar_ti_alloy_ingot")).out(I("mars_suit_leggings")).time(400).power(80));
        asm.recipe(r().in(I("space_suit_boots")).in(I("lunar_glass_fiber"), 2).in(I("lunar_ti_alloy_ingot")).out(I("mars_suit_boots")).time(300).power(80));
        // astrobiology: studying Martian clays yields science data
        asm.recipe(r().in(I("microfossil"), 4).in(I("smectite_clay"), 8).in(I("processor")).out(I("science_mars")).time(600).power(80));
    }
}
