package com.heliosphere.core;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Registration helpers. Every module registers into the shared {@code heliosphere} namespace. */
public final class Reg {
    /** Items in registration order per creative tab group, used to populate the creative tabs. */
    public static final Map<String, List<Item>> TAB_CONTENTS = new LinkedHashMap<>();
    private static String currentTab = "misc";

    private Reg() {}

    public static void tab(String group) { currentTab = group; }

    public static ResourceKey<Block> blockKey(String id) { return ResourceKey.create(Registries.BLOCK, Heliosphere.id(id)); }
    public static ResourceKey<Item> itemKey(String id) { return ResourceKey.create(Registries.ITEM, Heliosphere.id(id)); }

    public static <B extends Block> B block(String id, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        B block = factory.apply(props.setId(blockKey(id)));
        return Registry.register(BuiltInRegistries.BLOCK, blockKey(id), block);
    }

    public static <B extends Block> B blockWithItem(String id, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        B b = block(id, factory, props);
        blockItem(b, id, BlockItem::new, new Item.Properties());
        return b;
    }

    public static <B extends Block> B blockWithItem(String id, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props,
                                                    Item.Properties itemProps) {
        B b = block(id, factory, props);
        blockItem(b, id, BlockItem::new, itemProps);
        return b;
    }

    public static Item blockItem(Block block, String id, BiFunction<Block, Item.Properties, Item> factory, Item.Properties props) {
        return item(id, p -> factory.apply(block, p), props.useBlockDescriptionPrefix());
    }

    public static <I extends Item> I item(String id, Function<Item.Properties, I> factory, Item.Properties props) {
        I it = factory.apply(props.setId(itemKey(id)));
        Registry.register(BuiltInRegistries.ITEM, itemKey(id), it);
        TAB_CONTENTS.computeIfAbsent(currentTab, k -> new ArrayList<>()).add(it);
        return it;
    }

    public static Item item(String id) { return item(id, Item::new, new Item.Properties()); }

    /** Looks up any Heliosphere item by id, failing loudly if a module forgot to register it. */
    public static Item get(String id) {
        Item i = BuiltInRegistries.ITEM.getValue(Heliosphere.id(id));
        if (i == null || i == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("Unknown Heliosphere item: " + id);
        return i;
    }

    public static Block getBlock(String id) {
        Block b = BuiltInRegistries.BLOCK.getValue(Heliosphere.id(id));
        if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) throw new IllegalStateException("Unknown Heliosphere block: " + id);
        return b;
    }

    public static Item item(String id, int stack) { return item(id, Item::new, new Item.Properties().stacksTo(stack)); }

    @SafeVarargs
    public static <T extends BlockEntity> BlockEntityType<T> blockEntity(String id, FabricBlockEntityTypeBuilder.Factory<T> factory, Block... blocks) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Heliosphere.id(id),
                FabricBlockEntityTypeBuilder.<T>create(factory, blocks).build());
    }

    // ---------------------------------------------------------------- common property presets

    public static BlockBehaviour.Properties stone(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(hardness, hardness * 2 + 3).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    public static BlockBehaviour.Properties deepslate(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(hardness + 1.5f, hardness * 2 + 3).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE);
    }

    public static BlockBehaviour.Properties soil(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(hardness).sound(SoundType.GRAVEL);
    }

    public static BlockBehaviour.Properties metal(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(hardness, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    public static BlockBehaviour.Properties ice(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(hardness).friction(0.98f).sound(SoundType.GLASS);
    }
}
