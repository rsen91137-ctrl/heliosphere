package com.heliosphere.planets.weapon.armor;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The planet suits were built for the environment and took a machine's shot like a leather tunic. They now carry
 * diamond-class protection (3 / 8 / 6 / 3 armour, toughness 2 a piece), in every suit and every stack already made.
 */
public final class SuitArmour {
    private SuitArmour() {}

    private static final String[] SUITS = {"space_suit", "mars_suit", "thermal_suit", "cryo_suit", "solar_suit"};
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
    private static final int[] ARMOUR = {3, 8, 6, 3};
    private static final EquipmentSlotGroup[] SLOTS = {EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};

    public static void init() {
        DefaultItemComponentEvents.MODIFY.register(ctx -> {
            for (String suit : SUITS) for (int i = 0; i < 4; i++) {
                var item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("heliosphere", suit + "_" + PIECES[i]));
                if (item.isEmpty()) continue;
                final int k = i;
                ctx.modify(item.get(), b -> {
                    Identifier id = Identifier.withDefaultNamespace("armor." + PIECES[k].replace("chestplate", "chestplate"));
                    b.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                            .add(Attributes.ARMOR, new AttributeModifier(id, ARMOUR[k], AttributeModifier.Operation.ADD_VALUE), SLOTS[k])
                            .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, 2, AttributeModifier.Operation.ADD_VALUE), SLOTS[k])
                            .build());
                });
            }
        });
    }
}
