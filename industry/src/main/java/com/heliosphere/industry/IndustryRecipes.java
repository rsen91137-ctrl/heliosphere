package com.heliosphere.industry;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import static com.heliosphere.industry.Industry.*;

/**
 * Earth processing chains. Real industrial routes, simplified: Bayer + Hall-Héroult for aluminium,
 * the Hunter process for titanium, chlor-alkali electrolysis, Haber ammonia, Czochralski silicon...
 */
public final class IndustryRecipes {
    private IndustryRecipes() {}

    static Item I(String id) { return Reg.get(id); }
    static MachineRecipe r() { return MachineRecipe.of(); }

    public static void register() {
        // ---------------------------------------------------------------- electric arc furnace
        ARC_FURNACE.recipe(r().in(Items.IRON_INGOT, 2).in(I("graphite")).out(I("steel_ingot"), 2).time(160).power(40));
        ARC_FURNACE.recipe(r().in(I("raw_pentlandite")).out(I("nickel_ingot")).chance(Items.IRON_NUGGET, 3, 0.5f).time(160).power(40));
        ARC_FURNACE.recipe(r().in(I("raw_chromite")).out(I("chromium_ingot")).chance(Items.IRON_NUGGET, 3, 0.5f).time(180).power(50));
        ARC_FURNACE.recipe(r().in(I("raw_pyrochlore")).out(I("niobium_ingot")).time(200).power(60));
        ARC_FURNACE.recipe(r().in(I("raw_cassiterite")).out(I("tin_ingot")).time(100).power(30));
        ARC_FURNACE.recipe(r().in(I("raw_galena")).out(I("lead_ingot")).chance(I("silver_ingot"), 1, 0.3f).time(120).power(30));
        ARC_FURNACE.recipe(r().in(I("raw_sphalerite")).out(I("zinc_ingot")).time(120).power(30));
        ARC_FURNACE.recipe(r().in(I("raw_molybdenite")).out(I("molybdenum_ingot")).chance(I("rhenium_powder"), 1, 0.15f).time(200).power(60));
        ARC_FURNACE.recipe(r().in(I("raw_wolframite")).out(I("tungsten_ingot")).time(240).power(80));
        ARC_FURNACE.recipe(r().in(I("raw_cobaltite")).out(I("cobalt_ingot")).time(180).power(50));
        ARC_FURNACE.recipe(r().in(I("raw_sperrylite")).out(I("platinum_ingot")).time(240).power(60));
        ARC_FURNACE.recipe(r().in(I("beryl")).in(I("fluorite")).out(I("beryllium_ingot")).time(240).power(70));
        ARC_FURNACE.recipe(r().in(I("raw_titanomagnetite")).out(I("vanadium_ingot")).out(Items.IRON_INGOT).time(200).power(60));
        ARC_FURNACE.recipe(r().in(Items.QUARTZ, 2).in(I("graphite")).out(I("metallurgical_silicon")).time(200).power(70));
        ARC_FURNACE.recipe(r().in(Items.RAW_IRON).out(Items.IRON_INGOT).time(80).power(20));
        ARC_FURNACE.recipe(r().in(Items.RAW_COPPER).out(Items.COPPER_INGOT).time(80).power(20));
        ARC_FURNACE.recipe(r().in(Items.RAW_GOLD).out(Items.GOLD_INGOT).time(80).power(20));

        // ---------------------------------------------------------------- alloy furnace
        ALLOY_FURNACE.recipe(r().in(I("aluminum_ingot"), 3).in(I("lithium_ingot")).in(Items.COPPER_INGOT).out(I("al_li_ingot"), 4).time(200).power(60));
        ALLOY_FURNACE.recipe(r().in(I("titanium_ingot"), 3).in(I("aluminum_ingot")).in(I("vanadium_ingot")).out(I("ti64_ingot"), 4).time(240).power(80));
        ALLOY_FURNACE.recipe(r().in(I("nickel_ingot"), 3).in(I("chromium_ingot")).in(I("niobium_ingot")).out(I("inconel_ingot"), 4).time(240).power(80));
        ALLOY_FURNACE.recipe(r().in(Items.COPPER_INGOT, 4).in(I("chromium_ingot")).in(I("niobium_ingot")).out(I("grcop_ingot"), 5).time(200).power(70));
        ALLOY_FURNACE.recipe(r().in(I("steel_ingot"), 3).in(I("chromium_ingot")).in(I("nickel_ingot")).out(I("stainless_steel_ingot"), 4).time(180).power(60));
        ALLOY_FURNACE.recipe(r().in(I("neodymium")).in(Items.IRON_INGOT, 2).out(I("ndfeb_magnet"), 2).time(160).power(50));

        // ---------------------------------------------------------------- electrolyzer
        ELECTROLYZER.recipe(r().in(Items.WATER_BUCKET).in(I("empty_canister"), 3).out(I("canister_hydrogen"), 2).out(I("canister_oxygen")).time(200).power(90));
        ELECTROLYZER.recipe(r().in(I("rock_salt"), 2).in(Items.WATER_BUCKET).in(I("empty_canister")).out(I("caustic_soda"), 2).out(I("canister_chlorine")).time(200).power(80));
        ELECTROLYZER.recipe(r().in(I("rock_salt"), 2).in(I("empty_canister")).out(I("sodium"), 2).out(I("canister_chlorine")).time(240).power(100));
        ELECTROLYZER.recipe(r().in(I("alumina"), 2).in(I("cryolite")).in(I("graphite")).out(I("aluminum_ingot"), 2).time(240).power(120));
        ELECTROLYZER.recipe(r().in(I("lithium_carbonate"), 2).out(I("lithium_ingot")).time(200).power(100));
        ELECTROLYZER.recipe(r().in(I("rare_earth_oxide"), 2).out(I("neodymium")).out(I("yttrium")).time(240).power(100));

        // ---------------------------------------------------------------- chemical reactor
        CHEMICAL_REACTOR.recipe(r().in(I("raw_bauxite"), 2).in(I("caustic_soda")).out(I("alumina"), 2).time(160).power(40));
        CHEMICAL_REACTOR.recipe(r().in(I("fluorite"), 2).in(I("alumina")).in(I("caustic_soda")).out(I("cryolite"), 2).time(160).power(40));
        CHEMICAL_REACTOR.recipe(r().in(I("raw_ilmenite"), 2).in(I("canister_chlorine")).in(I("sodium"), 2).out(I("titanium_sponge"), 2).out(I("empty_canister")).time(240).power(60));
        CHEMICAL_REACTOR.recipe(r().in(I("raw_spodumene"), 2).in(I("sulfuric_acid")).out(I("lithium_carbonate"), 2).out(I("empty_canister")).time(200).power(50));
        CHEMICAL_REACTOR.recipe(r().in(I("raw_monazite"), 2).in(I("sulfuric_acid")).out(I("rare_earth_oxide"), 2).out(I("empty_canister")).time(200).power(50));
        CHEMICAL_REACTOR.recipe(r().in(I("raw_uraninite"), 2).in(I("sulfuric_acid")).out(I("yellowcake"), 2).out(I("empty_canister")).time(240).power(60));
        CHEMICAL_REACTOR.recipe(r().in(I("sulfur"), 2).in(Items.WATER_BUCKET).in(I("empty_canister")).out(I("sulfuric_acid")).time(120).power(30));
        CHEMICAL_REACTOR.recipe(r().in(I("canister_ln2")).in(I("canister_hydrogen"), 3).out(I("canister_ammonia"), 2).out(I("empty_canister"), 2).time(240).power(80));
        CHEMICAL_REACTOR.recipe(r().in(I("rock_salt"), 2).in(I("canister_ammonia")).in(I("canister_oxygen")).out(I("ammonium_perchlorate"), 3).out(I("empty_canister"), 2).time(240).power(80));
        CHEMICAL_REACTOR.recipe(r().in(I("bitumen"), 2).in(Items.COAL).out(I("phenolic_resin"), 2).time(160).power(40));
        CHEMICAL_REACTOR.recipe(r().in(I("aluminum_ingot")).in(I("bitumen")).in(I("empty_canister")).out(I("triethylaluminium")).time(200).power(60));
        CHEMICAL_REACTOR.recipe(r().in(I("fluorite")).in(I("plastic")).out(I("fluoroelastomer"), 2).time(160).power(40));
        CHEMICAL_REACTOR.recipe(r().in(I("metallurgical_silicon")).in(I("canister_ln2")).out(I("silicon_nitride"), 2).out(I("empty_canister")).time(200).power(70));
        CHEMICAL_REACTOR.recipe(r().in(I("plastic"), 2).in(I("canister_ammonia")).out(I("nylon"), 4).out(I("empty_canister")).time(160).power(40));
        CHEMICAL_REACTOR.recipe(r().in(I("carbon_fiber")).in(I("phenolic_resin")).out(I("pica"), 2).time(200).power(50));

        // ---------------------------------------------------------------- refinery
        REFINERY.recipe(r().in(I("oil_sand"), 6).in(I("empty_canister"), 2).out(I("canister_rp1")).out(I("canister_ch4")).out(I("bitumen"), 2).time(240).power(60));
        REFINERY.recipe(r().in(I("oil_sand"), 3).out(I("plastic"), 2).out(I("bitumen")).time(160).power(40));
        REFINERY.recipe(r().in(I("bitumen"), 2).in(I("empty_canister")).out(I("canister_rp1")).time(200).power(50));

        // ---------------------------------------------------------------- air separation (Earth air only)
        AIR_SEPARATOR.recipe(r().in(I("empty_canister"), 2).out(I("canister_lox")).out(I("canister_ln2")).chance(I("canister_argon"), 1, 0.12f).time(200).power(80));
        AIR_SEPARATOR.recipe(r().in(I("canister_hydrogen"), 2).out(I("canister_lh2")).out(I("empty_canister")).time(240).power(120));

        // ---------------------------------------------------------------- vacuum furnace
        VACUUM_FURNACE.recipe(r().in(I("titanium_sponge"), 2).out(I("titanium_ingot"), 2).time(200).power(100));
        VACUUM_FURNACE.recipe(r().in(I("inconel_ingot"), 2).out(I("turbine_blade")).time(300).power(140).precise());
        VACUUM_FURNACE.recipe(r().in(I("metallurgical_silicon"), 2).out(I("silicon_wafer"), 2).time(300).power(120));
        VACUUM_FURNACE.recipe(r().in(I("bitumen"), 3).out(I("carbon_fiber"), 2).time(240).power(100));

        // ---------------------------------------------------------------- CNC machining (precision)
        CNC.recipe(r().in(I("steel_ingot"), 2).out(I("steel_plate")).time(100).power(60).precise());
        CNC.recipe(r().in(I("al_li_ingot"), 2).out(I("al_li_plate")).time(100).power(60).precise());
        CNC.recipe(r().in(I("ti64_ingot"), 2).out(I("ti64_plate")).time(140).power(80).precise());
        CNC.recipe(r().in(I("inconel_ingot"), 2).out(I("inconel_plate")).time(140).power(80).precise());
        CNC.recipe(r().in(I("stainless_steel_ingot"), 2).out(I("stainless_steel_plate")).time(120).power(70).precise());
        CNC.recipe(r().in(Items.COPPER_INGOT, 2).out(I("copper_plate")).time(80).power(50));
        CNC.recipe(r().in(I("ti64_plate")).in(I("steel_ingot")).out(I("inducer")).time(200).power(90).precise());
        CNC.recipe(r().in(I("stainless_steel_plate")).in(I("fluoroelastomer")).out(I("valve")).time(160).power(80).precise());
        CNC.recipe(r().in(I("ti64_plate")).in(I("copper_plate")).out(I("injector")).time(240).power(100).precise());
        CNC.recipe(r().in(I("steel_plate"), 2).in(I("ti64_ingot")).out(I("gimbal")).time(200).power(90).precise());
        CNC.recipe(r().in(I("niobium_ingot"), 2).in(I("inconel_plate")).out(I("nozzle_bell")).time(240).power(100).precise());
        CNC.recipe(r().in(I("silicon_nitride"), 2).in(I("steel_ingot")).out(I("si3n4_bearing"), 2).time(200).power(90).precise());

        // ---------------------------------------------------------------- metal 3D printing
        PRINTER.recipe(r().in(I("grcop_ingot"), 3).in(I("inconel_plate")).out(I("combustion_chamber")).time(400).power(160).precise());

        // ---------------------------------------------------------------- electronics
        ELECTRONICS.recipe(r().in(I("copper_plate")).in(I("copper_wire"), 4).in(I("phenolic_resin")).out(I("circuit_board"), 2).time(120).power(30));
        ELECTRONICS.recipe(r().in(I("circuit_board")).in(I("silicon_wafer")).in(Items.GOLD_INGOT).out(I("processor")).time(200).power(60));
        ELECTRONICS.recipe(r().in(I("processor")).in(I("ndfeb_magnet")).in(I("circuit_board")).out(I("imu")).time(240).power(60));
        ELECTRONICS.recipe(r().in(I("processor"), 2).in(I("imu")).in(I("circuit_board")).out(I("guidance_computer")).time(300).power(80));
        ELECTRONICS.recipe(r().in(I("processor")).in(I("circuit_board")).in(I("copper_wire"), 4).out(I("engine_controller")).time(240).power(60));
        ELECTRONICS.recipe(r().in(I("silicon_wafer"), 2).in(I("silver_ingot")).in(I("copper_wire")).out(I("solar_cell"), 3).time(200).power(50));
        ELECTRONICS.recipe(r().in(I("lithium_ingot")).in(I("cobalt_ingot")).in(I("graphite")).out(I("battery_cell"), 2).time(200).power(50));
        ELECTRONICS.recipe(r().in(I("triethylaluminium")).in(I("steel_plate")).in(I("copper_wire")).out(I("igniter")).time(160).power(40));

        // ---------------------------------------------------------------- assembly (sub-assemblies)
        ASSEMBLY.recipe(r().in(I("steel_ingot"), 2).in(I("copper_wire"), 4).in(I("ndfeb_magnet")).out(I("electric_motor")).time(200).power(60));
        ASSEMBLY.recipe(r().in(I("turbine_blade"), 4).in(I("si3n4_bearing"), 2).in(I("inducer")).in(I("inconel_plate"), 2).in(I("fluoroelastomer"))
                .out(I("turbopump")).time(400).power(120).precise());
        ASSEMBLY.recipe(r().in(I("pica"), 2).in(I("carbon_fiber")).out(I("heat_shield_tile"), 2).time(200).power(60));
        ASSEMBLY.recipe(r().in(I("nylon"), 6).out(I("parachute_canopy")).time(200).power(40));
        ASSEMBLY.recipe(r().in(I("carbon_fiber"), 3).in(I("al_li_plate")).out(I("copv")).time(240).power(80));

        // ---------------------------------------------------------------- centrifuge & test stand
        CENTRIFUGE.recipe(r().in(I("yellowcake"), 4).out(I("enriched_uranium")).chance(I("plutonium_238"), 1, 0.05f).time(600).power(200));
    }
}
