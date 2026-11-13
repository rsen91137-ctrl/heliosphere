package com.heliosphere.planets.weapon.armor;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

/** Registers the armour sets: materials, the four pieces of each, their recipes. */
public final class Armory {
    private Armory() {}

    public static final Map<ArmorSet, ArmorMaterial> MATERIALS = new EnumMap<>(ArmorSet.class);
    public static final Map<ArmorSet, Item[]> PIECES = new EnumMap<>(ArmorSet.class);
    private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
    public static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};

    public static void init() {
        Reg.tab("tools");
        for (ArmorSet set : ArmorSet.values()) {
            Map<ArmorType, Integer> def = new EnumMap<>(ArmorType.class);
            def.put(ArmorType.HELMET, set.helmet); def.put(ArmorType.CHESTPLATE, set.chest);
            def.put(ArmorType.LEGGINGS, set.legs); def.put(ArmorType.BOOTS, set.boots); def.put(ArmorType.BODY, set.chest);
            Holder<SoundEvent> sound = switch (set.sound) {
                case LEATHER -> SoundEvents.ARMOR_EQUIP_LEATHER;
                case IRON -> SoundEvents.ARMOR_EQUIP_IRON;
                case NETHERITE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
            };
            ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("heliosphere", set.id));
            TagKey<Item> repair = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("heliosphere", "repairs_" + set.id + "_armor"));
            ArmorMaterial mat = new ArmorMaterial(set.durability, def, set.enchant, sound, set.toughness, set.knockback, repair, asset);
            MATERIALS.put(set, mat);
            Item[] items = new Item[4];
            for (int i = 0; i < 4; i++) {
                ArmorType type = TYPES[i];
                Item.Properties props = new Item.Properties().humanoidArmor(mat, type);
                ItemAttributeModifiers extra = extras(set, type, mat);
                if (extra != null) props.attributes(extra);
                if (set == ArmorSet.ALIEN || set == ArmorSet.EXO) props.fireResistant();
                items[i] = Reg.item(set.id + "_" + SUFFIX[i], Item::new, props);
            }
            PIECES.put(set, items);
        }
        MachineType.addProvider(Armory::recipes);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 != 0) return;
            for (var p : server.getPlayerList().getPlayers())
                for (ArmorSet set : ArmorSet.values())
                    if (worn(p, set) == 4) { com.heliosphere.core.progress.Achievements.grant(p, "full_armor"); break; }
        });
    }

    /** Set-specific attributes on top of the armour values. */
    private static ItemAttributeModifiers extras(ArmorSet set, ArmorType type, ArmorMaterial mat) {
        ItemAttributeModifiers base = mat.createAttributes(type);
        EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(type.getSlot());
        Identifier id = Identifier.fromNamespaceAndPath("heliosphere", "armor_" + set.id + "_" + type.getName());
        return switch (set) {
            case EXO -> switch (type) {
                case CHESTPLATE -> base.withModifierAdded(Attributes.MAX_HEALTH, new AttributeModifier(id, 4, AttributeModifier.Operation.ADD_VALUE), slot);
                case LEGGINGS -> base.withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
                case BOOTS -> base.withModifierAdded(Attributes.SAFE_FALL_DISTANCE, new AttributeModifier(id, 5, AttributeModifier.Operation.ADD_VALUE), slot);
                default -> base.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(id, 1, AttributeModifier.Operation.ADD_VALUE), slot);
            };
            case EOD -> base.withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
            case GHILLIE -> type == ArmorType.LEGGINGS || type == ArmorType.BOOTS
                    ? base.withModifierAdded(Attributes.SNEAKING_SPEED, new AttributeModifier(id, 0.1, AttributeModifier.Operation.ADD_VALUE), slot) : null;
            default -> null;
        };
    }

    /** Pieces of {@code set} that {@code e} is wearing (0-4). */
    public static int worn(LivingEntity e, ArmorSet set) {
        Item[] items = PIECES.get(set);
        if (items == null) return 0;
        int n = 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) if (e.getItemBySlot(slots[i]).is(items[i])) n++;
        return n;
    }

    private static Item I(String id) { return Reg.get(id); }

    private static void recipes() {
        MachineType suit = MachineType.get("suit_fabricator");
        if (suit == null) suit = MachineType.get("assembly_station");
        if (suit == null) return;
        // material per set: the soft part, the hard part
        Object[][] mats = {
                {ArmorSet.US_OCP, "nylon", "steel_plate"}, {ArmorSet.RU_RATNIK, "nylon", "steel_plate"}, {ArmorSet.PLA_STARRY, "nylon", "steel_plate"},
                {ArmorSet.SWAT, "nylon", "stainless_steel_plate"}, {ArmorSet.EOD, "carbon_fiber", "ti64_plate"}, {ArmorSet.HAZMAT, "plastic", "lead_ingot"},
                {ArmorSet.GHILLIE, "nylon", "plastic"}, {ArmorSet.EXO, "carbon_fiber", "inconel_plate"}, {ArmorSet.ALIEN, "graphene_sheet", "alien_alloy"}};
        int[][] counts = {{3, 2}, {5, 4}, {4, 3}, {2, 1}};
        for (Object[] row : mats) {
            ArmorSet set = (ArmorSet) row[0];
            Item soft = I((String) row[1]), hard = I((String) row[2]);
            Item[] out = PIECES.get(set);
            for (int i = 0; i < 4; i++) {
                MachineRecipe r = MachineRecipe.of().in(soft, counts[i][0] * 2).in(hard, counts[i][1] * (set == ArmorSet.EOD || set == ArmorSet.EXO ? 3 : 2));
                if (set == ArmorSet.EXO) r = r.in(I("electric_motor"), i == 2 ? 2 : 1).in(I("battery_cell"), 1);
                if (set == ArmorSet.ALIEN) r = r.in(I("alien_chip"), 1);
                if (set == ArmorSet.HAZMAT && i == 0) r = r.in(I("lunar_glass"), 2);
                suit.recipe(r.out(out[i]).time(200 + i * 40).power(60));
            }
        }
    }
}
