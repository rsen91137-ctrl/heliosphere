package com.heliosphere.hostiles.fortress;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** What a fortress of a given difficulty holds: better things, and more of them, the harder it is. */
public final class FortressLoot {
    private FortressLoot() {}

    /** id ("minecraft:" or mod id), first difficulty, weight, min, max. */
    private record Entry(String id, int from, int weight, int min, int max) {}

    private static final Entry[] TABLE = {
            new Entry("steel_ingot", 1, 30, 4, 12), new Entry("copper_wire", 1, 20, 4, 12), new Entry("circuit_board", 1, 18, 1, 4),
            new Entry("rifle_round", 1, 22, 16, 48), new Entry("shotgun_shell", 1, 12, 8, 24), new Entry("battery_cell", 1, 14, 1, 4),
            new Entry("minecraft:iron_ingot", 1, 18, 4, 16), new Entry("minecraft:cooked_beef", 1, 10, 4, 12), new Entry("space_ration", 1, 8, 2, 6),
            new Entry("processor", 3, 14, 1, 3), new Entry("ti64_ingot", 3, 14, 2, 6), new Entry("power_cell", 3, 12, 2, 6),
            new Entry("grenade_40mm", 3, 10, 2, 8), new Entry("turret_kit_mk1", 3, 6, 1, 1), new Entry("attachment_red_dot", 3, 5, 1, 1),
            new Entry("attachment_extended_mag", 3, 5, 1, 1), new Entry("minecraft:gold_ingot", 3, 10, 2, 8),
            new Entry("alien_alloy", 5, 12, 1, 4), new Entry("alien_chip", 5, 8, 1, 2), new Entry("guidance_computer", 5, 6, 1, 1),
            new Entry("ndfeb_magnet", 5, 10, 2, 6), new Entry("turret_kit_mk2", 5, 6, 1, 1), new Entry("plasma_cell", 5, 8, 2, 6),
            new Entry("heavy_pistol", 5, 3, 1, 1), new Entry("designated_marksman_rifle", 5, 3, 1, 1), new Entry("attachment_scope_4x", 5, 4, 1, 1),
            new Entry("tungsten_slug", 7, 10, 4, 12), new Entry("graphene_sheet", 7, 8, 1, 4), new Entry("turret_kit_mk3", 7, 6, 1, 1),
            new Entry("sniper_rifle", 7, 3, 1, 1), new Entry("coilgun", 7, 3, 1, 1), new Entry("minecraft:diamond", 7, 8, 2, 6),
            new Entry("attachment_scope_8x", 7, 4, 1, 1), new Entry("attachment_suppressor", 7, 4, 1, 1),
            new Entry("antimatter_cell", 9, 8, 2, 4), new Entry("turret_kit_mk4", 9, 5, 1, 1), new Entry("railgun", 9, 2, 1, 1),
            new Entry("plasma_cannon", 9, 2, 1, 1), new Entry("minecraft:netherite_ingot", 9, 4, 1, 2), new Entry("antimatter_trap", 9, 3, 1, 1),
            new Entry("zero_point_cell", 12, 3, 1, 1), new Entry("turret_kit_mk5", 12, 3, 1, 1), new Entry("antimatter_rifle", 12, 2, 1, 1),
            new Entry("singularity_cannon", 13, 1, 1, 1), new Entry("nuke_warhead", 12, 2, 1, 1), new Entry("deep_space_computer", 12, 3, 1, 1),
    };

    private static Item item(String id) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("heliosphere", id);
        Item it = BuiltInRegistries.ITEM.getValue(key);
        return it == null ? Items.AIR : it;
    }

    /** Fills a container with {@code rolls} draws from what this difficulty can hold. */
    public static void fill(Container box, int difficulty, int rolls, RandomSource r) {
        List<Entry> pool = new ArrayList<>();
        int total = 0;
        for (Entry e : TABLE) if (e.from <= difficulty && item(e.id) != Items.AIR) { pool.add(e); total += e.weight + (difficulty - e.from); }
        if (pool.isEmpty()) return;
        for (int i = 0; i < rolls; i++) {
            int pick = r.nextInt(total);
            for (Entry e : pool) {
                pick -= e.weight + (difficulty - e.from);
                if (pick >= 0) continue;
                ItemStack stack = new ItemStack(item(e.id), e.min + r.nextInt(e.max - e.min + 1));
                int slot = r.nextInt(box.getContainerSize());
                for (int tries = 0; tries < box.getContainerSize() && !box.getItem(slot).isEmpty(); tries++) slot = (slot + 1) % box.getContainerSize();
                if (box.getItem(slot).isEmpty()) box.setItem(slot, stack);
                break;
            }
        }
    }
}
