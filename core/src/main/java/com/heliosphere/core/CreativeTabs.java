package com.heliosphere.core;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Creative tabs filled lazily from {@link Reg#TAB_CONTENTS} plus custom stacks (e.g. prebuilt rockets). */
public final class CreativeTabs {
    public static final Map<String, List<Supplier<ItemStack>>> EXTRA = new LinkedHashMap<>();

    private CreativeTabs() {}

    public static void addExtra(String group, Supplier<ItemStack> stack) { EXTRA.computeIfAbsent(group, k -> new ArrayList<>()).add(stack); }

    public static void register(String tab, Supplier<ItemStack> icon, String... groups) {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Heliosphere.id(tab), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.heliosphere." + tab))
                .icon(icon)
                .displayItems((params, out) -> {
                    for (String g : groups) {
                        for (Supplier<ItemStack> s : EXTRA.getOrDefault(g, List.of())) out.accept(s.get());
                        for (Item i : Reg.TAB_CONTENTS.getOrDefault(g, List.of())) out.accept(i);
                    }
                })
                .build());
    }
}
