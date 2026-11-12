package com.heliosphere.planets.defense.mk;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Registers the seven turrets, the five upgrade kits and their recipes. */
public final class Sentries implements ModInitializer {
    public static final Map<TurretKind, SentryBlock> BLOCKS = new EnumMap<>(TurretKind.class);
    public static BlockEntityType<SentryBlockEntity> BLOCK_ENTITY;
    public static final String[] KITS = {"turret_kit_mk1", "turret_kit_mk2", "turret_kit_mk3", "turret_kit_mk4", "turret_kit_mk5"};

    private record Task(long due, Runnable run) {}
    private static final Map<ServerLevel, List<Task>> TASKS = new WeakHashMap<>();

    @Override
    public void onInitialize() {
        Reg.tab("weapons");
        for (TurretKind k : TurretKind.values())
            BLOCKS.put(k, Reg.blockWithItem(k.id, p -> new SentryBlock(p, k), Reg.metal(MapColor.METAL, 4.5f)));
        BLOCK_ENTITY = Reg.blockEntity("sentry", SentryBlockEntity::new, BLOCKS.values().toArray(Block[]::new));
        for (String kit : KITS) Reg.item(kit);
        MachineType.addProvider(Sentries::recipes);
        ServerTickEvents.END_WORLD_TICK.register(Sentries::tick);
        com.heliosphere.planets.weapon.guided.SmartLauncher.init();
    }

    /** Upgrade level of a kit item (the released Mk II and Mk III kits count too), or 0. */
    static int kitLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (int i = 0; i < KITS.length; i++) if (KITS[i].equals(id)) return i + 1;
        if (id.equals("defense_turret_upgrade_2")) return 2;
        if (id.equals("defense_turret_upgrade_3")) return 3;
        return 0;
    }

    static void later(ServerLevel level, int ticks, Runnable r) {
        TASKS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Task(level.getGameTime() + ticks, r));
    }

    private static void tick(ServerLevel level) {
        List<Task> list = TASKS.get(level);
        if (list == null || list.isEmpty()) return;
        long now = level.getGameTime();
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = list.iterator(); it.hasNext(); ) { Task t = it.next(); if (t.due <= now) { due.add(t); it.remove(); } }
        for (Task t : due) try { t.run.run(); } catch (RuntimeException ignored) { }
    }

    private static Item I(String id) { return Reg.get(id); }

    private static void recipes() {
        MachineType asm = MachineType.get("assembly_station");
        if (asm == null) return;
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 6).in(I("rifle_round"), 32).in(I("circuit_board"), 2).in(I("electric_motor"), 2).out(I("autocannon_turret")).time(400).power(80));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 8).in(I("grenade_40mm"), 8).in(I("processor")).in(I("electric_motor"), 2).out(I("flak_turret")).time(500).power(100));
        asm.recipe(MachineRecipe.of().in(I("ti64_plate"), 6).in(I("guidance_computer")).in(I("rdx"), 8).in(I("processor"), 2).out(I("missile_turret")).time(700).power(150));
        asm.recipe(MachineRecipe.of().in(I("ndfeb_magnet"), 8).in(I("niobium_ingot"), 4).in(I("tungsten_ingot"), 4).in(I("processor"), 2).in(I("battery_cell"), 6).out(I("rail_turret")).time(900).power(220));
        asm.recipe(MachineRecipe.of().in(I("copper_wire"), 24).in(I("ndfeb_magnet"), 4).in(I("battery_cell"), 4).in(I("circuit_board"), 4).out(I("tesla_turret")).time(600).power(140));
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 4).in(I("circuit_board"), 2).in(I("copper_wire"), 4).out(I("turret_kit_mk1")).time(200).power(40));
        asm.recipe(MachineRecipe.of().in(I("ti64_plate"), 2).in(I("processor")).in(I("battery_cell"), 2).out(I("turret_kit_mk2")).time(300).power(80));
        asm.recipe(MachineRecipe.of().in(I("inconel_plate"), 2).in(I("processor"), 2).in(I("ndfeb_magnet"), 2).out(I("turret_kit_mk3")).time(400).power(120));
        asm.recipe(MachineRecipe.of().in(I("alien_alloy"), 2).in(I("alien_chip")).in(I("graphene_sheet"), 2).out(I("turret_kit_mk4")).time(600).power(200));
        asm.recipe(MachineRecipe.of().in(I("zero_point_cell")).in(I("alien_chip"), 2).in(I("deep_space_computer")).in(I("magnetic_sail_coil")).out(I("turret_kit_mk5")).time(1200).power(400));
    }
}
