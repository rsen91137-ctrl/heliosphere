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
import java.util.Map;

/** Registers the guided missile, the six air-defence vehicles, their hull, the battery pack and recipes. */
public final class SamSites implements ModInitializer {
    public static EntityType<GuidedMissile> MISSILE;
    public static final Map<SamKind, SamBlock> BLOCKS = new EnumMap<>(SamKind.class);
    public static Block HULL;
    public static BlockEntityType<SamBlockEntity> BLOCK_ENTITY;
    public static Block BATTERY;
    public static BlockEntityType<FieldBatteryBlockEntity> BATTERY_ENTITY;

    @Override
    public void onInitialize() {
        Identifier id = Identifier.fromNamespaceAndPath("heliosphere", "guided_missile");
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        MISSILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<GuidedMissile>of(GuidedMissile::new, MobCategory.MISC).sized(0.5f, 0.5f)
                        .clientTrackingRange(32).updateInterval(1).noSave().build(key));
        Reg.tab("weapons");
        for (SamKind k : SamKind.values())
            BLOCKS.put(k, Reg.blockWithItem(k.id, p -> new SamBlock(p, k), Reg.metal(MapColor.COLOR_GREEN, 8f)));
        HULL = Reg.block("sam_hull", SamHullBlock::new, Reg.metal(MapColor.COLOR_GREEN, 8f).noLootTable());
        BLOCK_ENTITY = Reg.blockEntity("sam_vehicle", SamBlockEntity::new, BLOCKS.values().toArray(Block[]::new));
        BATTERY = Reg.block("sam_battery", FieldBatteryBlock::new, Reg.metal(MapColor.COLOR_GREEN, 5f).noLootTable());
        Reg.blockItem(BATTERY, "sam_battery", FieldBatteryItem::new, new Item.Properties().stacksTo(16));
        BATTERY_ENTITY = Reg.blockEntity("sam_battery", FieldBatteryBlockEntity::new, BATTERY);
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
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 24).in(I("circuit_board"), 12).in(I("processor"), 6).in(I("copper_wire"), 32).in(I("ndfeb_magnet"), 6).out(I("buk_tads")).time(1200).power(160));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 28).in(I("guidance_computer"), 4).in(I("rdx"), 24).in(I("electric_motor"), 4).in(I("processor"), 4).out(I("buk_telar")).time(1400).power(180));
        asm.recipe(MachineRecipe.of().in(I("ti64_plate"), 16).in(I("circuit_board"), 16).in(I("processor"), 10).in(I("copper_wire"), 40).in(I("ndfeb_magnet"), 10).out(I("samp_tads")).time(1600).power(220));
        asm.recipe(MachineRecipe.of().in(I("ti64_plate"), 20).in(I("guidance_computer"), 8).in(I("rdx"), 32).in(I("processor"), 8).in(I("inconel_plate"), 6).out(I("samp_tel")).time(1800).power(240));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 18).in(I("circuit_board"), 10).in(I("processor"), 6).in(I("copper_wire"), 28).in(I("ndfeb_magnet"), 4).out(I("iris_tads")).time(1000).power(140));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 20).in(I("guidance_computer"), 4).in(I("rdx"), 16).in(I("processor"), 4).in(I("electric_motor"), 2).out(I("iris_tel")).time(1100).power(150));
        asm.recipe(MachineRecipe.of().in(I("battery_cell"), 16).in(I("solar_cell"), 6).in(I("steel_plate"), 10).in(I("copper_wire"), 24).in(I("circuit_board"), 4).out(I("sam_battery")).time(600).power(80));
    }
}
