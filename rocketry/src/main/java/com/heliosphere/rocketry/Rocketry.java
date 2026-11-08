package com.heliosphere.rocketry;

import com.heliosphere.core.CreativeTabs;
import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.item.PrebuiltRocketItem;
import com.heliosphere.rocketry.launch.LaunchConsoleBlock;
import com.heliosphere.rocketry.launch.LaunchConsoleBlockEntity;
import com.heliosphere.rocketry.menu.RocketMenu;
import com.heliosphere.rocketry.net.RocketNet;
import com.heliosphere.rocketry.part.PartBlockEntity;
import com.heliosphere.rocketry.part.PartItem;
import com.heliosphere.rocketry.part.RocketPartBlock;
import com.heliosphere.rocketry.physics.Blueprints;
import com.heliosphere.rocketry.physics.PartSpec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricTrackedDataRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Rockets: parts with real engineering data, launch pads, mission control, flight and landing. */
public final class Rocketry implements ModInitializer {
    /** Part id -> model kind (matches tools/gen/content.py PARTS). */
    public static final Map<String, String> MODELS = new LinkedHashMap<>();
    public static final Map<String, Block> PARTS = new LinkedHashMap<>();
    public static BlockEntityType<PartBlockEntity> PART_BE;
    public static Block LAUNCH_PAD;
    public static final net.minecraft.sounds.SoundEvent ENGINE_SOUND = sound("rocket_engine"), IGNITION_SOUND = sound("rocket_ignition"),
            SEPARATION_SOUND = sound("rocket_separation"), ALARM_SOUND = sound("cabin_alarm");

    private static net.minecraft.sounds.SoundEvent sound(String id) {
        return net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Heliosphere.id(id));
    }
    public static LaunchConsoleBlock LAUNCH_CONSOLE;
    public static BlockEntityType<LaunchConsoleBlockEntity> CONSOLE_BE;
    public static EntityType<RocketEntity> ROCKET;

    static {
        String[][] m = {{"engine_kerolox", "engine"}, {"engine_hydrolox", "engine_vac"}, {"engine_methalox", "engine"}, {"engine_methalox_vac", "engine_vac"},
                {"srb_segment", "tank"}, {"srb_nozzle", "engine"}, {"tank_rp1", "tank"}, {"tank_lox", "tank"}, {"tank_lh2", "tank"}, {"tank_ch4", "tank"},
                {"decoupler", "band"}, {"rocket_frame", "truss"}, {"avionics_t1", "box"}, {"avionics_t2", "box"}, {"crew_capsule", "capsule"},
                {"heat_shield", "slab"}, {"heat_shield_t2", "slab"}, {"parachute_pack", "box"}, {"landing_leg", "leg"}, {"nose_cone", "cone"},
                {"rocket_fin", "fin"}, {"escape_tower", "tower"}, {"rocket_battery", "box"}, {"life_support", "box"},
                {"plasma_thruster", "engine_vac"}, {"tank_argon", "tank"}, {"light_sail", "truss"}};
        for (String[] r : m) MODELS.put(r[0], r[1]);
    }

    @Override
    public void onInitialize() {
        com.heliosphere.core.Heliosphere.bootstrap();
        FabricTrackedDataRegistry.register(Heliosphere.id("rocket_structure"), RocketEntity.STRUCTURE_SERIALIZER);
        for (net.minecraft.sounds.SoundEvent se : new net.minecraft.sounds.SoundEvent[]{ENGINE_SOUND, IGNITION_SOUND, SEPARATION_SOUND, ALARM_SOUND})
            Registry.register(BuiltInRegistries.SOUND_EVENT, se.location(), se);
        Reg.tab("rocketry");
        for (Blueprints.Blueprint bp : Blueprints.all())
            Reg.item("rocket_" + bp.id(), p -> new PrebuiltRocketItem(p, bp.id()), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        Reg.item("rocket_recovery_tool", com.heliosphere.rocketry.item.RecoveryToolItem::new, new Item.Properties().stacksTo(1));
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!player.getItemInHand(hand).is(Reg.get("rocket_recovery_tool")) || !(entity instanceof com.heliosphere.rocketry.entity.RocketEntity r)) return net.minecraft.world.InteractionResult.PASS;
            if (r.phase() != com.heliosphere.rocketry.entity.RocketEntity.LANDED) return net.minecraft.world.InteractionResult.FAIL;
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                int n = r.recover(sl, player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.heliosphere.recovered", n), true);
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        });

        LAUNCH_PAD = Reg.blockWithItem("launch_pad", Block::new, Reg.metal(MapColor.COLOR_GRAY, 5f).explosionResistance(1200f));
        LAUNCH_CONSOLE = Reg.blockWithItem("launch_console", LaunchConsoleBlock::new, Reg.metal(MapColor.COLOR_GRAY, 4f));
        CONSOLE_BE = Reg.blockEntity("launch_console", LaunchConsoleBlockEntity::new, LAUNCH_CONSOLE);

        for (PartSpec spec : PartSpec.all()) {
            String model = MODELS.getOrDefault(spec.id, "box");
            boolean full = model.equals("tank") || model.equals("engine") || model.equals("engine_vac") || model.equals("band")
                    || model.equals("box") || model.equals("capsule") || model.equals("truss");
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 4f).sound(SoundType.METAL);
            if (!full) props = props.noOcclusion();
            RocketPartBlock b = Reg.block(spec.id, p -> new RocketPartBlock(p, spec.id, model), props);
            Reg.blockItem(b, spec.id, PartItem::new, new Item.Properties());
            PARTS.put(spec.id, b);
        }
        PART_BE = Reg.blockEntity("rocket_part", PartBlockEntity::new, PARTS.values().toArray(new Block[0]));

        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Heliosphere.id("rocket"));
        ROCKET = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                .sized(3f, 10f).clientTrackingRange(32).updateInterval(1).fireImmune().build(key));

        RocketMenu.init();
        RocketNet.init();
        Environment.sealedCabin = e -> e.getVehicle() instanceof RocketEntity;
        MachineType.addProvider(Rocketry::recipes);
        CreativeTabs.register("rocketry", () -> new ItemStack(PARTS.get("engine_kerolox")), "rocketry");
    }

    static Item I(String id) { return Reg.get(id); }
    static MachineRecipe r() { return MachineRecipe.of(); }

    /** Rocket-part assembly and engine test firing. */
    private static void recipes() {
        MachineType asm = MachineType.get("assembly_station");
        MachineType stand = MachineType.get("engine_test_stand");
        // tier 3: Venus bismuth, Mercury graphene, Martian argon
        asm.recipe(r().in(I("bismuth_ingot"), 2).in(I("ndfeb_magnet"), 4).in(I("graphene_sheet"), 2).in(I("processor")).in(I("inconel_plate"), 2)
                .in(I("canister_argon")).out(I("plasma_thruster")).time(1200).power(300).precise());
        asm.recipe(r().in(I("ti64_plate"), 4).in(I("valve")).in(I("copv")).out(I("tank_argon"), 2).time(300).power(80));
        asm.recipe(r().in(I("graphene_sheet"), 8).in(I("carbon_fiber"), 4).in(I("silver_ingot"), 2).in(I("nylon"), 4).out(I("light_sail")).time(1200).power(200));
        // starter versions for the Moon rocket: steel instead of aerospace alloys, listed first
        asm.recipe(r().in(I("turbopump")).in(I("combustion_chamber")).in(I("injector")).in(I("valve"), 2).in(I("igniter")).in(I("nozzle_bell"))
                .out(I("engine_kerolox")).time(400).power(100));
        asm.recipe(r().in(I("turbopump")).in(I("combustion_chamber")).in(I("injector")).in(I("valve"), 2).in(I("igniter")).in(I("nozzle_bell"), 2)
                .out(I("engine_hydrolox")).time(400).power(100));
        asm.recipe(r().in(I("steel_plate"), 4).in(I("valve")).out(I("tank_rp1"), 2).time(200).power(60));
        asm.recipe(r().in(I("steel_plate"), 4).in(I("valve")).in(I("copv")).out(I("tank_lox"), 2).time(200).power(60));
        asm.recipe(r().in(I("steel_plate"), 4).in(I("valve")).in(I("copv")).in(Items.PACKED_ICE).out(I("tank_lh2"), 2).time(200).power(60));
        asm.recipe(r().in(I("steel_plate"), 2).in(I("steel_ingot")).out(I("rocket_frame"), 4).time(100).power(30));
        asm.recipe(r().in(I("guidance_computer")).in(I("battery_cell")).in(I("steel_plate"), 2).out(I("avionics_t1")).time(300).power(80));
        asm.recipe(r().in(I("steel_plate"), 6).in(Items.GLASS_PANE, 2).in(I("circuit_board"), 2).in(I("battery_cell")).out(I("crew_capsule")).time(400).power(100));
        asm.recipe(r().in(I("heat_shield_tile"), 6).in(I("steel_plate"), 2).out(I("heat_shield")).time(300).power(80));
        asm.recipe(r().in(I("parachute_canopy"), 3).in(Items.STRING, 4).out(I("parachute_pack")).time(200).power(40));
        asm.recipe(r().in(I("steel_plate"), 2).in(I("steel_ingot"), 2).out(I("landing_leg"), 2).time(200).power(50));
        asm.recipe(r().in(I("steel_plate"), 3).out(I("nose_cone")).time(120).power(40));
        asm.recipe(r().in(I("steel_plate"), 2).out(I("rocket_fin"), 2).time(120).power(40));
        asm.recipe(r().in(I("battery_cell"), 4).in(I("steel_plate")).out(I("rocket_battery")).time(160).power(40));
        asm.recipe(r().in(I("turbopump")).in(I("combustion_chamber")).in(I("injector")).in(I("valve"), 2).in(I("igniter")).in(I("engine_controller"))
                .in(I("gimbal")).in(I("nozzle_bell")).out(I("engine_kerolox")).time(600).power(160).precise());
        asm.recipe(r().in(I("turbopump"), 2).in(I("combustion_chamber")).in(I("injector")).in(I("valve"), 2).in(I("igniter")).in(I("engine_controller"))
                .in(I("nozzle_bell"), 2).out(I("engine_hydrolox")).time(600).power(160).precise());
        asm.recipe(r().in(I("lunar_turbopump"), 2).in(I("methalox_chamber")).in(I("injector")).in(I("valve"), 2).in(I("igniter")).in(I("engine_controller"))
                .in(I("gimbal")).in(I("nozzle_bell")).out(I("engine_methalox")).time(800).power(200).precise());
        asm.recipe(r().in(I("lunar_turbopump"), 2).in(I("methalox_chamber")).in(I("injector")).in(I("valve"), 2).in(I("igniter")).in(I("engine_controller"))
                .in(I("nozzle_bell"), 3).out(I("engine_methalox_vac")).time(800).power(200).precise());
        asm.recipe(r().in(I("steel_plate"), 2).in(I("ammonium_perchlorate"), 4).in(I("aluminum_ingot"), 2).in(I("plastic")).out(I("srb_segment")).time(300).power(80));
        asm.recipe(r().in(I("steel_plate"), 2).in(I("carbon_fiber"), 2).in(I("graphite"), 2).out(I("srb_nozzle")).time(300).power(80).precise());
        asm.recipe(r().in(I("al_li_plate"), 4).in(I("valve")).out(I("tank_rp1"), 2).time(300).power(80));
        asm.recipe(r().in(I("al_li_plate"), 4).in(I("valve")).in(I("copv")).out(I("tank_lox"), 2).time(300).power(80));
        asm.recipe(r().in(I("al_li_plate"), 4).in(I("valve")).in(I("carbon_fiber"), 2).out(I("tank_lh2"), 2).time(300).power(90));
        asm.recipe(r().in(I("al_li_plate"), 4).in(I("valve")).in(I("lunar_glass_fiber")).out(I("tank_ch4"), 2).time(300).power(90));
        asm.recipe(r().in(I("steel_plate"), 4).in(I("igniter")).out(I("decoupler"), 2).time(240).power(60));
        asm.recipe(r().in(I("al_li_plate"), 2).in(I("steel_ingot")).out(I("rocket_frame"), 4).time(160).power(40));
        asm.recipe(r().in(I("guidance_computer")).in(I("imu")).in(I("battery_cell"), 2).in(I("al_li_plate"), 2).in(I("science_suborbital"))
                .out(I("avionics_t1")).time(600).power(120));
        asm.recipe(r().in(I("deep_space_computer")).in(I("imu")).in(I("rad_hard_chip")).in(I("battery_cell"), 2).in(I("al_li_plate"), 2).in(I("science_moon"))
                .out(I("avionics_t2")).time(800).power(160));
        asm.recipe(r().in(I("ti64_plate"), 6).in(Items.GLASS_PANE, 2).in(I("circuit_board"), 2).in(I("canister_oxygen"), 2).in(I("battery_cell"), 2)
                .out(I("crew_capsule")).time(800).power(150));
        asm.recipe(r().in(I("heat_shield_tile"), 6).in(I("ti64_plate"), 2).out(I("heat_shield")).time(400).power(100));
        asm.recipe(r().in(I("lunar_ceramic"), 6).in(I("heat_shield_tile"), 2).in(I("lunar_ti_alloy_ingot"), 2).out(I("heat_shield_t2")).time(500).power(120));
        asm.recipe(r().in(I("parachute_canopy"), 3).in(I("nylon"), 2).in(I("steel_plate")).out(I("parachute_pack")).time(300).power(60));
        asm.recipe(r().in(I("ti64_plate"), 2).in(I("steel_ingot"), 2).in(I("electric_motor")).out(I("landing_leg"), 2).time(300).power(80));
        asm.recipe(r().in(I("al_li_plate"), 3).in(I("carbon_fiber")).out(I("nose_cone")).time(200).power(60));
        asm.recipe(r().in(I("al_li_plate"), 2).in(I("electric_motor")).out(I("rocket_fin"), 2).time(200).power(60));
        asm.recipe(r().in(I("srb_segment")).in(I("steel_plate"), 4).in(I("engine_controller")).out(I("escape_tower")).time(400).power(100));
        asm.recipe(r().in(I("battery_cell"), 6).in(I("al_li_plate")).in(I("circuit_board")).out(I("rocket_battery")).time(240).power(60));
        asm.recipe(r().in(I("lunar_glass"), 4).in(I("canister_oxygen"), 4).in(I("electric_motor"), 2).in(I("circuit_board")).in(I("lunar_ice_chunk"), 8)
                .in(I("science_moon")).out(I("life_support")).time(800).power(150));
        for (String engine : new String[]{"engine_kerolox", "engine_hydrolox", "engine_methalox", "engine_methalox_vac", "srb_nozzle"})
            stand.recipe(r().in(I(engine)).out(I(engine)).tested().time(400).power(200));
    }
}
