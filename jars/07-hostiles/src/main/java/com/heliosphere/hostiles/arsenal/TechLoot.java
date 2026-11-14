package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.tech.TechMob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * What a machine leaves behind when a player destroys it, by its technology level (EnemyRoster.TIER). The released
 * drop was the same for every machine (a circuit board, a steel ingot or a piece of alien alloy, sometimes a chip),
 * so a titan dropped what a patrol drone did. Now each level draws from the things of its own level and the two
 * below it, never the basics far beneath it, and the harder the machine the more draws: a patrol drone gives a little
 * salvage, a level 5 machine processors, magnets and the odd turret kit or attachment, a level 8 machine zero-point
 * cells, netherite and Mk IV-V kits. The bosses always drop a weapon of their level and a stack of rare parts.
 * Machines built by fortress fabricators (endless) draw half as often, so a fabricator is not a loot farm.
 */
public final class TechLoot {
    private TechLoot() {}

    /** id, level, weight, min, max. */
    private record Entry(String id, int tier, int weight, int min, int max) {}

    private static final Entry[] TABLE = {
            new Entry("circuit_board", 1, 20, 1, 2), new Entry("copper_wire", 1, 14, 3, 6), new Entry("steel_ingot", 1, 16, 2, 4),
            new Entry("battery_cell", 1, 10, 1, 1),
            new Entry("electric_motor", 2, 14, 1, 1), new Entry("steel_plate", 2, 12, 2, 4), new Entry("rifle_round", 2, 10, 12, 24),
            new Entry("battery_cell", 2, 10, 1, 2), new Entry("circuit_board", 2, 10, 2, 3),
            new Entry("processor", 3, 14, 1, 1), new Entry("power_cell", 3, 12, 1, 2), new Entry("ndfeb_magnet", 3, 12, 1, 2),
            new Entry("grenade_40mm", 3, 8, 2, 4), new Entry("electric_motor", 3, 8, 1, 2),
            new Entry("ti64_ingot", 4, 14, 2, 4), new Entry("alien_alloy", 4, 12, 1, 2), new Entry("plasma_cell", 4, 10, 1, 3),
            new Entry("turret_kit_mk1", 4, 5, 1, 1), new Entry("attachment_red_dot", 4, 4, 1, 1), new Entry("attachment_extended_mag", 4, 4, 1, 1),
            new Entry("processor", 4, 10, 1, 2),
            new Entry("alien_chip", 5, 12, 1, 1), new Entry("guidance_computer", 5, 8, 1, 1), new Entry("graphene_sheet", 5, 10, 1, 2),
            new Entry("tungsten_slug", 5, 8, 4, 8), new Entry("turret_kit_mk2", 5, 5, 1, 1), new Entry("attachment_scope_4x", 5, 4, 1, 1),
            new Entry("attachment_suppressor", 5, 4, 1, 1), new Entry("attachment_drum_mag", 5, 3, 1, 1),
            new Entry("alien_alloy", 6, 12, 2, 4), new Entry("antimatter_cell", 6, 8, 1, 1), new Entry("minecraft:diamond", 6, 10, 2, 4),
            new Entry("turret_kit_mk3", 6, 5, 1, 1), new Entry("rad_hard_chip", 6, 8, 1, 2), new Entry("processor", 6, 8, 2, 3),
            new Entry("alien_chip", 7, 12, 1, 3), new Entry("antimatter_cell", 7, 10, 1, 2), new Entry("minecraft:netherite_ingot", 7, 6, 1, 1),
            new Entry("turret_kit_mk4", 7, 5, 1, 1), new Entry("attachment_scope_8x", 7, 4, 1, 1), new Entry("deep_space_computer", 7, 4, 1, 1),
            new Entry("zero_point_cell", 8, 6, 1, 1), new Entry("minecraft:netherite_ingot", 8, 8, 1, 2), new Entry("turret_kit_mk5", 8, 4, 1, 1),
            new Entry("antimatter_trap", 8, 4, 1, 1), new Entry("graphene_sheet", 8, 8, 3, 6), new Entry("alien_chip", 8, 8, 2, 4),
    };

    /** The weapon a boss always carries, by its level. */
    private static final String[][] BOSS_WEAPONS = {
            {"sniper_rifle", "coilgun", "designated_marksman_rifle"},                 // level 6-7 keep bosses (rail sentinel, titan)
            {"railgun", "plasma_cannon", "coilgun"},                                 // overseer, carrier (9)
            {"antimatter_rifle", "singularity_cannon", "railgun", "plasma_cannon"},  // colossus (10)
    };

    private static Item item(String id) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("heliosphere", id);
        Item it = BuiltInRegistries.ITEM.getValue(key);
        return it == null ? Items.AIR : it;
    }

    /** Draws for a machine of this level (before the fabricator halving). */
    static int rolls(int tier, RandomSource r) {
        return switch (tier) {
            case 1 -> r.nextFloat() < 0.6f ? 1 : 0;
            case 2 -> 1;
            case 3, 4 -> 1 + (r.nextFloat() < 0.5f ? 1 : 0);
            case 5, 6 -> 2 + (r.nextFloat() < 0.4f ? 1 : 0);
            case 7, 8 -> 3 + (r.nextFloat() < 0.5f ? 1 : 0);
            default -> 6;
        };
    }

    public static List<ItemStack> roll(String id, boolean fabricated, RandomSource r) {
        int tier = EnemyRoster.tier(id);
        boolean boss = EnemyRoster.BOSSES.contains(id);
        int n = rolls(tier, r);
        if (fabricated) n = (n + (r.nextBoolean() ? 1 : 0)) / 2;
        int top = Math.min(8, tier), floor = Math.max(1, top - 2);
        List<Entry> pool = new ArrayList<>();
        int total = 0;
        for (Entry e : TABLE)
            if (e.tier <= top && e.tier >= floor && item(e.id) != Items.AIR) { pool.add(e); total += e.weight * (1 + e.tier - floor); }
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < n && total > 0; i++) {
            int pick = r.nextInt(total);
            for (Entry e : pool) {
                pick -= e.weight * (1 + e.tier - floor);
                if (pick >= 0) continue;
                out.add(new ItemStack(item(e.id), e.min + r.nextInt(e.max - e.min + 1)));
                break;
            }
        }
        if (boss || (tier >= 6 && !fabricated && r.nextFloat() < (tier - 5) * 0.04f)) {        // bosses always, high machines rarely
            String[] arms = BOSS_WEAPONS[tier >= 10 ? 2 : tier >= 9 ? 1 : 0];
            Item w = item(arms[r.nextInt(arms.length)]);
            if (w != Items.AIR) out.add(new ItemStack(w));
        }
        if (boss) {
            out.add(new ItemStack(item(tier >= 10 ? "zero_point_cell" : "antimatter_cell"), tier >= 10 ? 2 : 3));
            out.add(new ItemStack(item("turret_kit_mk" + (tier >= 10 ? 5 : 4))));
            if (tier >= 10) out.add(new ItemStack(Items.NETHER_STAR));
        }
        out.removeIf(ItemStack::isEmpty);
        return out;
    }

    /** In place of the released drop. */
    public static void drop(TechMob mob, ServerLevel level, boolean byPlayer) {
        String id = mob.role() == null ? "" : mob.role().id();
        boolean boss = EnemyRoster.BOSSES.contains(id);
        // fabricator output, and kills no player took part in (turrets, air defence, a fall): half the draws
        boolean half = !boss && (mob.getTags().contains("heliosphere.fortress") || !byPlayer);
        for (ItemStack st : roll(id, half, level.random))
            mob.spawnAtLocation(level, st);
    }
}
