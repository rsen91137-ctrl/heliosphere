package com.heliosphere.foods.cuisine;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds from grass: short and tall grass, fern and large fern get one more loot pool, a 6 % chance (not when cut with
 * shears) of one seed of the expansion, picked by the crops' grass weights. Wheat seeds drop as before.
 */
final class GrassSeeds {
    private GrassSeeds() {}

    static final float CHANCE = 0.06f;

    static void register() {
        Set<ResourceKey<LootTable>> grass = new HashSet<>();
        for (Block b : List.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN)) b.getLootTable().ifPresent(grass::add);
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!grass.contains(key) || !source.isBuiltin() || Cuisine.TABLE == null) return;
            LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(CHANCE))
                    .when(InvertedLootItemCondition.invert(MatchTool.toolMatches(
                            ItemPredicate.Builder.item().of(registries.lookupOrThrow(Registries.ITEM), Items.SHEARS))));
            int entries = 0;
            for (CuisineTable.Crop crop : Cuisine.TABLE.crops()) {
                Item seed = Cuisine.SEEDS.get(crop.id());
                if (seed == null || crop.grass() <= 0) continue;
                pool.add(LootItem.lootTableItem(seed).setWeight(crop.grass()).apply(ApplyExplosionDecay.explosionDecay()));
                entries++;
            }
            if (entries > 0) table.withPool(pool);
        });
    }
}
