package com.heliosphere.planets;

import com.heliosphere.core.CreativeTabs;
import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.HelioComponents;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.machine.MachineBlock;
import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Surviving off-world: space suits and oxygen, lunar and Martian resource processing (ISRU). */
public final class PlanetsMod implements ModInitializer {
    public static final Map<String, MachineBlock> MACHINES = new LinkedHashMap<>();
    public static BlockEntityType<MachineBlockEntity> MACHINE_BE;

    public static final MachineType OXYGEN_COMPRESSOR = MachineType.processor("oxygen_compressor", 2, 1);
    public static final MachineType OXYGEN_DISTRIBUTOR = MachineType.bubble("oxygen_distributor");
    public static final MachineType REGOLITH_PROCESSOR = MachineType.processor("regolith_processor", 2, 2);
    public static final MachineType ILMENITE_REDUCER = MachineType.processor("ilmenite_reducer", 3, 3);
    public static final MachineType VACUUM_DEPOSITION = MachineType.processor("vacuum_deposition", 2, 1)
            .when((l, p) -> Environment.pressureBar(l) < 0.001 ? null : "condition.heliosphere.needs_vacuum");
    public static final MachineType SABATIER = MachineType.processor("sabatier_reactor", 2, 2);
    public static final MachineType ATMOSPHERE_EXTRACTOR = MachineType.processor("atmosphere_extractor", 1, 3)
            .when((l, p) -> {
                double bar = Environment.pressureBar(l);
                return bar > 0.001 && !Environment.breathable(l, p) ? null : "condition.heliosphere.needs_foreign_air";
            });
    public static final MachineType ISRU_ELECTROLYZER = MachineType.processor("isru_electrolyzer", 2, 3);

    public static final TagKey<Item> REPAIRS_SUIT = TagKey.create(Registries.ITEM, Heliosphere.id("repairs_space_suit"));

    @Override
    public void onInitialize() {
        com.heliosphere.core.Heliosphere.bootstrap();
        Reg.tab("planets");
        for (String[] it : GenIds.ITEMS) Reg.item(it[0]);
        registerSuit("space_suit", 1, -175, 130, 6000);
        registerSuit("mars_suit", 2, -200, 160, 12000);

        List<Block> blocks = new ArrayList<>();
        for (String[] m : GenIds.MACHINES) {
            String id = m[0];
            MachineBlock b = Reg.blockWithItem(id, p -> new MachineBlock(p, id, () -> MACHINE_BE),
                    Reg.metal(MapColor.METAL, 3.5f).lightLevel(s -> s.getValue(MachineBlock.LIT) ? 9 : 0));
            MACHINES.put(id, b);
            blocks.add(b);
        }
        MACHINE_BE = Reg.blockEntity("planets_machine", (pos, state) -> new MachineBlockEntity(MACHINE_BE, pos, state), blocks.toArray(new Block[0]));

        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            boolean o2 = held.is(Reg.get("canister_oxygen")), lox = held.is(Reg.get("canister_lox"));
            if (!o2 && !lox) return InteractionResult.PASS;
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            HelioComponents.Oxygen ox = chest.get(HelioComponents.OXYGEN);
            if (ox == null || ox.amount() >= ox.capacity()) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                chest.set(HelioComponents.OXYGEN, ox.withAmount(ox.amount() + (lox ? 12000 : 6000)));
                held.shrink(1);
                ItemStack empty = new ItemStack(Reg.get("empty_canister"));
                if (!player.getInventory().add(empty)) player.drop(empty, false);
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1f, 0.6f);
                player.displayClientMessage(Component.translatable("message.heliosphere.oxygen_refilled"), true);
            }
            return InteractionResult.SUCCESS;
        });

        MachineType.addProvider(PlanetRecipes::register);
        CreativeTabs.register("planets", () -> new ItemStack(Reg.get("space_suit_helmet")), "planets");
        Heliosphere.LOG.info("Heliosphere planets ready ({} on the Moon and Mars)", Planets.MOON.identifier());
    }

    private static void registerSuit(String id, int tier, int minT, int maxT, int oxygen) {
        Map<ArmorType, Integer> def = new EnumMap<>(ArmorType.class);
        def.put(ArmorType.HELMET, 2); def.put(ArmorType.CHESTPLATE, 5); def.put(ArmorType.LEGGINGS, 4); def.put(ArmorType.BOOTS, 2);
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Heliosphere.id(id));
        ArmorMaterial mat = new ArmorMaterial(30 + tier * 10, def, 9, SoundEvents.ARMOR_EQUIP_IRON, tier - 1f, 0f, REPAIRS_SUIT, asset);
        HelioComponents.SuitRating rating = new HelioComponents.SuitRating(tier, minT, maxT, true);
        for (ArmorType type : new ArmorType[]{ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
            Item.Properties props = new Item.Properties().humanoidArmor(mat, type).component(HelioComponents.SUIT, rating);
            if (type == ArmorType.CHESTPLATE) props.component(HelioComponents.OXYGEN, new HelioComponents.Oxygen(oxygen, oxygen));
            Reg.item(id + "_" + type.getName(), Item::new, props);
        }
    }
}
