package com.heliosphere.planets.defense.sam;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registers the guided missile, the six air-defence vehicles, their hull, the battery pack and recipes. */
public final class SamSites implements ModInitializer {
    public static EntityType<GuidedMissile> MISSILE;
    public static final Map<SamKind, SamBlock> BLOCKS = new EnumMap<>(SamKind.class);
    public static Block HULL;
    public static BlockEntityType<SamBlockEntity> BLOCK_ENTITY;
    public static EntityType<SamSeat> SEAT;
    public static EntityType<SamDriveEntity> DRIVE;
    public static EntityType<SamVehicle> VEHICLE;
    /** The vehicles as items (placing one sets down the entity). */
    public static final Map<SamKind, Item> VEHICLE_ITEMS = new EnumMap<>(SamKind.class);
    public static Block BATTERY, CRATE;
    public static BlockEntityType<AmmoCrateBlockEntity> CRATE_ENTITY;
    /** The three systems and the missile round each one fires. */
    public static final List<String> SYSTEMS = List.of("buk", "samp", "iris");
    private static final Map<String, String> ROUNDS = Map.of("buk", "missile_9m317ma", "samp", "missile_aster30", "iris", "missile_iris_t_sl");

    public static String roundOf(String system) { return ROUNDS.get(system); }

    /** The system a missile round belongs to, or null if the stack is no round. */
    public static String systemOf(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (Map.Entry<String, String> e : ROUNDS.entrySet()) if (e.getValue().equals(id)) return e.getKey();
        return null;
    }
    public static BlockEntityType<FieldBatteryBlockEntity> BATTERY_ENTITY;

    @Override
    public void onInitialize() {
        Identifier id = Identifier.fromNamespaceAndPath("heliosphere", "guided_missile");
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        MISSILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<GuidedMissile>of(GuidedMissile::new, MobCategory.MISC).sized(0.5f, 0.5f)
                        .clientTrackingRange(32).updateInterval(1).noSave().build(key));
        Identifier seatId = Identifier.fromNamespaceAndPath("heliosphere", "sam_seat");
        ResourceKey<EntityType<?>> seatKey = ResourceKey.create(Registries.ENTITY_TYPE, seatId);
        SEAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, seatKey, EntityType.Builder.<SamSeat>of(SamSeat::new, MobCategory.MISC).sized(0.4f, 0.4f)
                .clientTrackingRange(8).build(seatKey));                // ridable entities must be savable; an empty seat removes itself
        Identifier driveId = Identifier.fromNamespaceAndPath("heliosphere", "sam_drive");
        ResourceKey<EntityType<?>> driveKey = ResourceKey.create(Registries.ENTITY_TYPE, driveId);
        DRIVE = Registry.register(BuiltInRegistries.ENTITY_TYPE, driveKey, EntityType.Builder.<SamDriveEntity>of(SamDriveEntity::new, MobCategory.MISC).sized(3.0f, 2.6f)
                .clientTrackingRange(12).updateInterval(1).build(driveKey));
        Identifier vehicleId = Identifier.fromNamespaceAndPath("heliosphere", "sam_vehicle");
        ResourceKey<EntityType<?>> vehicleKey = ResourceKey.create(Registries.ENTITY_TYPE, vehicleId);
        VEHICLE = Registry.register(BuiltInRegistries.ENTITY_TYPE, vehicleKey, EntityType.Builder.<SamVehicle>of(SamVehicle::new, MobCategory.MISC).sized(2.9f, 2.4f)
                .fireImmune().clientTrackingRange(16).updateInterval(2).build(vehicleKey));
        SamOperation.init();
        Reg.tab("weapons");
        for (SamKind k : SamKind.values()) {
            BLOCKS.put(k, Reg.block(k.id, p -> new SamBlock(p, k), Reg.metal(MapColor.COLOR_GREEN, 8f).noLootTable()));   // as saved by earlier versions
            VEHICLE_ITEMS.put(k, Reg.item(k.id, p -> new SamVehicleItem(p, k), new Item.Properties().stacksTo(1).useBlockDescriptionPrefix()));
        }
        HULL = Reg.block("sam_hull", SamHullBlock::new, Reg.metal(MapColor.COLOR_GREEN, 8f).noLootTable());
        BLOCK_ENTITY = Reg.blockEntity("sam_vehicle", SamBlockEntity::new, BLOCKS.values().toArray(Block[]::new));
        BATTERY = Reg.block("sam_battery", FieldBatteryBlock::new, Reg.metal(MapColor.COLOR_GREEN, 5f).noLootTable());
        Reg.blockItem(BATTERY, "sam_battery", FieldBatteryItem::new, new Item.Properties().stacksTo(16));
        BATTERY_ENTITY = Reg.blockEntity("sam_battery", FieldBatteryBlockEntity::new, BATTERY);
        CRATE = Reg.blockWithItem("sam_ammo_crate", AmmoCrateBlock::new, Reg.metal(MapColor.COLOR_GREEN, 4f));
        CRATE_ENTITY = Reg.blockEntity("sam_ammo_crate", AmmoCrateBlockEntity::new, CRATE);
        for (String r : ROUNDS.values()) Reg.item(r, Item::new, new Item.Properties().stacksTo(16));
        Reg.item("sam_wrench", SamWrenchItem::new, new Item.Properties().stacksTo(1));
        for (String part : List.of("sam_chassis", "phased_array_antenna", "fire_control_computer", "launcher_module")) Reg.item(part, Item::new, new Item.Properties());
        MachineType.addProvider(SamSites::recipes);
        com.heliosphere.planets.weapon.ammo.AmmoBox.init();
        com.heliosphere.planets.weapon.armor.Armory.init();
    }

    private static final Identifier STORY = Identifier.fromNamespaceAndPath("heliosphere", "story");
    private static final String BUILT = "air_defence_built";

    /** Whether anyone has set up an air-defence vehicle in this world: the machines answer with more raids. */
    public static boolean airDefenceBuilt(net.minecraft.server.MinecraftServer server) {
        return server.getCommandStorage().get(STORY).getBooleanOr(BUILT, false);
    }

    static void markBuilt(net.minecraft.server.level.ServerLevel level) {
        var server = level.getServer();
        if (airDefenceBuilt(server)) return;
        var t = server.getCommandStorage().get(STORY);
        t.putBoolean(BUILT, true);
        server.getCommandStorage().set(STORY, t);
        for (var p : server.getPlayerList().getPlayers())
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.heliosphere.sam_noticed")
                    .withStyle(net.minecraft.ChatFormatting.RED), false);
    }

    private static Item I(String id) { return Reg.get(id); }

    private static void recipes() {
        MachineType asm = MachineType.get("assembly_station");
        if (asm == null) return;
        // the vehicles are strong, so they are built from four major assemblies, each a project of its own
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 24).in(I("stainless_steel_plate"), 8).in(I("electric_motor"), 6).in(I("battery_cell"), 8).in(I("carbon_fiber"), 4)
                .out(I("sam_chassis")).time(1600).power(200));
        asm.recipe(MachineRecipe.of().in(I("silicon_wafer"), 8).in(I("rad_hard_chip"), 4).in(I("copper_wire"), 32).in(I("ndfeb_magnet"), 8).in(I("al_li_plate"), 6)
                .out(I("phased_array_antenna")).time(2000).power(260));
        asm.recipe(MachineRecipe.of().in(I("processor"), 8).in(I("guidance_computer"), 4).in(I("rad_hard_chip"), 2).in(I("circuit_board"), 8)
                .out(I("fire_control_computer")).time(1800).power(240));
        asm.recipe(MachineRecipe.of().in(I("inconel_plate"), 6).in(I("steel_plate"), 16).in(I("electric_motor"), 4).in(I("carbon_fiber"), 4)
                .out(I("launcher_module")).time(1400).power(200));
        asm.recipe(MachineRecipe.of().in(I("sam_chassis"), 1).in(I("phased_array_antenna"), 2).in(I("fire_control_computer"), 1).in(I("steel_plate"), 12).out(I("buk_tads")).time(3000).power(300));
        asm.recipe(MachineRecipe.of().in(I("sam_chassis"), 1).in(I("launcher_module"), 1).in(I("phased_array_antenna"), 1).in(I("fire_control_computer"), 1).in(I("steel_plate"), 12).out(I("buk_telar")).time(3200).power(300));
        asm.recipe(MachineRecipe.of().in(I("sam_chassis"), 1).in(I("phased_array_antenna"), 3).in(I("fire_control_computer"), 2).in(I("ti64_plate"), 12).out(I("samp_tads")).time(3600).power(340));
        asm.recipe(MachineRecipe.of().in(I("sam_chassis"), 1).in(I("launcher_module"), 2).in(I("fire_control_computer"), 1).in(I("ti64_plate"), 12).in(I("inconel_plate"), 6).out(I("samp_tel")).time(3400).power(320));
        asm.recipe(MachineRecipe.of().in(I("sam_chassis"), 1).in(I("phased_array_antenna"), 2).in(I("fire_control_computer"), 1).in(I("al_li_plate"), 8).out(I("iris_tads")).time(2800).power(280));
        asm.recipe(MachineRecipe.of().in(I("sam_chassis"), 1).in(I("launcher_module"), 1).in(I("fire_control_computer"), 1).in(I("steel_plate"), 12).out(I("iris_tel")).time(2800).power(280));
        // ammunition is simple: a case, a charge and a guidance board
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 4).in(I("electric_motor"), 1).out(I("sam_wrench")).time(200).power(30));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 10).in(I("copper_wire"), 8).in(I("circuit_board"), 2).out(I("sam_ammo_crate")).time(300).power(40));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 2).in(I("rdx"), 2).in(I("circuit_board"), 1).out(I("missile_9m317ma"), 2).time(200).power(40));
        asm.recipe(MachineRecipe.of().in(I("ti64_plate"), 2).in(I("rdx"), 2).in(I("circuit_board"), 1).out(I("missile_aster30"), 2).time(240).power(50));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 2).in(I("rdx"), 1).in(I("circuit_board"), 1).out(I("missile_iris_t_sl"), 2).time(160).power(40));
        asm.recipe(MachineRecipe.of().in(I("battery_cell"), 16).in(I("solar_cell"), 6).in(I("steel_plate"), 10).in(I("copper_wire"), 24).in(I("circuit_board"), 4).out(I("sam_battery")).time(600).power(80));
    }
}
