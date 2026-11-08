package com.heliosphere.planets.weapon;

import com.heliosphere.core.CreativeTabs;
import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.planets.weapon.GunItem.Effect;
import com.heliosphere.planets.weapon.GunItem.Spec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * Firearms, from chemical-age rifles to stargate-tier exotics. Balance reference: vanilla netherite sword 8,
 * zombie 20 HP, iron golem 100 HP, warden 500 HP.
 */
public final class Weapons {
    private Weapons() {}

    private static void gun(String id, Spec s, Rarity r) {
        Reg.item(id, p -> new GunItem(p, s), new Item.Properties().stacksTo(1).rarity(r));
    }

    public static void register() {
        Reg.tab("weapons");
        var crit = ParticleTypes.CRIT;
        gun("bolt_rifle", new Spec("rifle_round", 14, 1, 0.3f, 96, 22, false, 0, Effect.NONE, crit, SoundEvents.FIREWORK_ROCKET_BLAST, 0.55f, 4f, 1f), Rarity.COMMON);
        gun("shotgun", new Spec("shotgun_shell", 3.5f, 9, 7f, 28, 18, false, 0, Effect.NONE, crit, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 6f, 1f), Rarity.COMMON);
        gun("smg", new Spec("rifle_round", 3.5f, 1, 2.5f, 40, 2, true, 0, Effect.NONE, crit, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.8f, 0.5f), Rarity.COMMON);
        gun("assault_rifle", new Spec("rifle_round", 6, 1, 1.2f, 72, 4, true, 0, Effect.NONE, crit, SoundEvents.FIREWORK_ROCKET_BLAST, 1.1f, 1.2f, 1f), Rarity.UNCOMMON);
        gun("sniper_rifle", new Spec("rifle_round", 32, 1, 0f, 220, 45, false, 0, Effect.PIERCE, ParticleTypes.CRIT, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.4f, 8f, 1f), Rarity.UNCOMMON);
        gun("grenade_launcher", new Spec("grenade_40mm", 8, 1, 0.8f, 64, 30, false, 2.6f, Effect.EXPLODE, ParticleTypes.SMOKE, SoundEvents.CROSSBOW_SHOOT, 0.5f, 5f, 1f), Rarity.UNCOMMON);
        gun("coilgun", new Spec("steel_slug", 18, 1, 0.2f, 120, 12, false, 0, Effect.PIERCE, ParticleTypes.ELECTRIC_SPARK, SoundEvents.BEACON_ACTIVATE, 2.0f, 3f, 1f), Rarity.RARE);
        gun("railgun", new Spec("tungsten_slug", 42, 1, 0f, 256, 50, false, 0, Effect.PIERCE, ParticleTypes.ELECTRIC_SPARK, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 9f, 1f), Rarity.RARE);
        gun("laser_rifle", new Spec("power_cell", 4.5f, 1, 0f, 96, 2, true, 0, Effect.FIRE, ParticleTypes.END_ROD, SoundEvents.BEACON_POWER_SELECT, 2.0f, 0.2f, 0.05f), Rarity.RARE);
        gun("plasma_cannon", new Spec("plasma_cell", 24, 1, 0.5f, 80, 25, false, 2.2f, Effect.FIRE, ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.BLAZE_SHOOT, 0.6f, 5f, 1f), Rarity.EPIC);
        gun("nuke_launcher", new Spec("nuke_warhead", 100, 1, 0f, 200, 200, false, 0, Effect.NUKE, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.4f, 12f, 1f), Rarity.EPIC);
        gun("antimatter_rifle", new Spec("antimatter_cell", 40, 1, 0f, 160, 16, false, 3.5f, Effect.ANTIMATTER, ParticleTypes.REVERSE_PORTAL, SoundEvents.WARDEN_SONIC_BOOM, 2.2f, 4f, 0.25f), Rarity.EPIC);
        gun("singularity_cannon", new Spec("antimatter_cell", 20, 1, 0f, 96, 60, false, 0, Effect.SINGULARITY, ParticleTypes.PORTAL, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 6f, 1f), Rarity.EPIC);
        CreativeTabs.register("weapons", () -> new ItemStack(Reg.get("assault_rifle")), "weapons");
    }

    static Item I(String id) { return Reg.get(id); }
    static MachineRecipe r() { return MachineRecipe.of(); }

    /** Ammunition runs through the chemical industry: Haber ammonia → Ostwald nitric acid → nitrocellulose and RDX. */
    public static void recipes() {
        MachineType chem = MachineType.get("chemical_reactor"), alloy = MachineType.get("alloy_furnace"), asm = MachineType.get("assembly_station");
        chem.recipe(r().in(I("canister_ammonia")).in(I("canister_oxygen"), 2).in(Items.WATER_BUCKET).out(I("nitric_acid"), 2).out(I("empty_canister"), 3).time(200).power(60));
        chem.recipe(r().in(I("nitric_acid")).in(I("sulfuric_acid")).in(Items.PAPER, 2).out(I("smokeless_powder"), 4).time(160).power(40));
        chem.recipe(r().in(I("nitric_acid"), 2).in(I("canister_ammonia")).in(Items.PAPER).out(I("rdx"), 2).out(I("empty_canister")).time(240).power(60));
        alloy.recipe(r().in(Items.COPPER_INGOT, 3).in(I("zinc_ingot")).out(I("brass_ingot"), 4).time(160).power(40));
        asm.recipe(r().in(I("brass_ingot")).in(I("lead_ingot")).in(I("smokeless_powder")).out(I("rifle_round"), 16).time(80).power(30));
        asm.recipe(r().in(I("plastic")).in(I("lead_ingot"), 2).in(I("smokeless_powder")).out(I("shotgun_shell"), 8).time(80).power(30));
        asm.recipe(r().in(I("steel_plate")).in(I("rdx")).in(I("brass_ingot")).out(I("grenade_40mm"), 4).time(120).power(40));
        asm.recipe(r().in(I("steel_ingot"), 2).out(I("steel_slug"), 8).time(60).power(30));
        asm.recipe(r().in(I("tungsten_ingot")).in(I("copper_plate")).out(I("tungsten_slug"), 4).time(120).power(50));
        asm.recipe(r().in(I("battery_cell"), 2).in(I("copper_wire"), 2).out(I("power_cell")).time(80).power(40));
        asm.recipe(r().in(I("bismuth_ingot")).in(I("canister_hydrogen")).in(I("ndfeb_magnet")).out(I("plasma_cell"), 4).time(160).power(80));
        asm.recipe(r().in(I("enriched_uranium"), 4).in(I("plutonium_238"), 2).in(I("rdx"), 8).in(I("beryllium_ingot"), 2).in(I("processor"))
                .in(I("steel_plate"), 4).out(I("nuke_warhead")).time(1200).power(300));
        asm.recipe(r().in(I("antimatter_trap")).in(I("power_cell"), 2).out(I("antimatter_cell"), 4).time(400).power(300));
        // planetary defence: reachable before the Moon (kerolox engine, guidance, RDX)
        asm.recipe(r().in(I("engine_kerolox")).in(I("guidance_computer")).in(I("rdx"), 8).in(I("steel_plate"), 6).in(I("tank_rp1")).in(I("tank_lox"))
                .out(I("kinetic_interceptor")).time(1200).power(200));
        // guns
        asm.recipe(r().in(I("steel_ingot"), 4).in(Items.OAK_PLANKS, 2).in(I("steel_plate")).out(I("bolt_rifle")).time(200).power(40));
        asm.recipe(r().in(I("steel_ingot"), 4).in(Items.OAK_PLANKS, 2).in(I("steel_plate"), 2).out(I("shotgun")).time(200).power(40));
        asm.recipe(r().in(I("steel_plate"), 3).in(I("steel_ingot"), 2).in(Items.IRON_INGOT, 2).out(I("smg")).time(240).power(50));
        asm.recipe(r().in(I("steel_plate"), 4).in(I("ti64_ingot"), 2).in(I("plastic"), 2).in(I("steel_ingot"), 2).out(I("assault_rifle")).time(300).power(60));
        asm.recipe(r().in(I("steel_plate"), 4).in(I("ti64_ingot"), 2).in(Items.SPYGLASS).in(I("carbon_fiber"), 2).out(I("sniper_rifle")).time(400).power(80));
        asm.recipe(r().in(I("steel_plate"), 5).in(I("steel_ingot"), 2).in(I("plastic"), 2).out(I("grenade_launcher")).time(300).power(60));
        asm.recipe(r().in(I("copper_wire"), 12).in(I("battery_cell"), 2).in(I("steel_plate"), 4).in(I("circuit_board"), 2).out(I("coilgun")).time(400).power(100));
        asm.recipe(r().in(I("ndfeb_magnet"), 6).in(I("niobium_ingot"), 4).in(I("battery_cell"), 4).in(I("processor"), 2).in(I("tungsten_ingot"), 2)
                .in(I("grcop_ingot"), 2).out(I("railgun")).time(800).power(200));
        asm.recipe(r().in(I("lunar_rare_earth"), 2).in(I("processor"), 2).in(I("battery_cell"), 2).in(I("lunar_glass"), 2).in(I("ti64_plate"), 2)
                .out(I("laser_rifle")).time(600).power(150));
        asm.recipe(r().in(I("bismuth_ingot"), 2).in(I("ndfeb_magnet"), 4).in(I("inconel_plate"), 4).in(I("processor"), 2).in(I("canister_argon"), 2)
                .out(I("plasma_cannon")).time(800).power(250));
        asm.recipe(r().in(I("steel_plate"), 8).in(I("processor"), 2).in(I("guidance_computer")).in(I("ti64_plate"), 4).out(I("nuke_launcher")).time(1000).power(250));
        asm.recipe(r().in(I("antimatter_trap")).in(I("deep_space_computer")).in(I("graphene_sheet"), 4).in(I("saturn_core"), 2).in(I("zero_point_cell"))
                .out(I("antimatter_rifle")).time(1600).power(400));
        asm.recipe(r().in(I("antimatter_trap"), 2).in(I("magnetic_sail_coil"), 2).in(I("jupiter_core"), 2).in(I("neptune_core"), 2).in(I("zero_point_cell"), 2)
                .out(I("singularity_cannon")).time(2400).power(500));
    }
}
