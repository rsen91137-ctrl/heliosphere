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
    public static final MachineType SUIT_FABRICATOR = MachineType.processor("suit_fabricator", 6, 1);
    public static final MachineType STORM_SHIELD = MachineType.bubble("storm_shield_generator");
    public static final MachineType RADIO_ARRAY = MachineType.processor("deep_space_radio_array", 3, 1);
    public static final MachineType CORE_DRILL = MachineType.processor("planet_core_drill", 2, 1);

    public static final TagKey<Item> REPAIRS_SUIT = TagKey.create(Registries.ITEM, Heliosphere.id("repairs_space_suit"));

    @Override
    public void onInitialize() {
        com.heliosphere.core.Heliosphere.bootstrap();
        Reg.tab("planets");
        for (String[] it : GenIds.ITEMS) Reg.item(it[0]);
        // space food (crafting recipes generated in tools/gen)
        food("space_ration", 6, 0.8f);
        food("tube_beef_paste", 8, 0.9f);
        food("freeze_dried_fruit", 4, 0.5f);
        food("astronaut_ice_cream", 5, 0.6f);
        food("space_coffee", 3, 0.4f);
        food("mars_potato", 5, 0.6f);
        food("hydroponic_salad", 4, 0.7f);
        food("spirulina_bar", 6, 1.0f);
        food("tholin_protein", 7, 0.9f);
        food("rehydrated_pasta", 7, 0.8f);
        food("lunar_ice_water", 2, 0.3f);
        food("plume_soda", 3, 0.5f);
        registerSuit("space_suit", 1, -175, 130, 6000);
        registerSuit("mars_suit", 2, -200, 160, 12000);
        registerSuit("thermal_suit", 3, -150, 480, 12000);
        registerSuit("cryo_suit", 3, -240, 60, 18000);
        registerSuit("solar_suit", 4, -100, 2500, 6000);

        List<Block> blocks = new ArrayList<>();
        for (String[] m : GenIds.MACHINES) {
            String id = m[0];
            MachineBlock b = Reg.blockWithItem(id, p -> new MachineBlock(p, id, () -> MACHINE_BE),
                    Reg.metal(MapColor.METAL, 3.5f).lightLevel(s -> s.getValue(MachineBlock.LIT) ? 9 : 0));
            MACHINES.put(id, b);
            blocks.add(b);
        }
        MACHINE_BE = Reg.blockEntity("planets_machine", (pos, state) -> new MachineBlockEntity(MACHINE_BE, pos, state), blocks.toArray(new Block[0]));

        // Scooping Titan's seas and gas-giant clouds with an empty canister
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Reg.get("empty_canister"))) return InteractionResult.PASS;
            net.minecraft.core.BlockPos pos = hit.getBlockPos();
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
            String out = null;
            if (id.equals("liquid_methane")) out = "canister_ch4";
            else if (id.endsWith("_cloud")) {
                out = level.getRandom().nextFloat() < 0.12f && !id.equals("methane_cloud") ? "canister_helium3" : "canister_hydrogen";
                if (!level.isClientSide()) level.removeBlock(pos, false);
            }
            if (out == null) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                held.shrink(1);
                ItemStack full = new ItemStack(Reg.get(out));
                if (!player.getInventory().add(full)) player.drop(full, false);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1f, 0.8f);
            }
            return InteractionResult.SUCCESS;
        });

        // Playing back the Proxima recording decodes the signal and starts the fleet countdown
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Reg.get("proxima_signal"))) return InteractionResult.PASS;
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                if (com.heliosphere.core.story.Story.decode(sl.getServer())) {
                    ItemStack map = new ItemStack(Reg.get("star_map"));
                    if (!player.getInventory().add(map)) player.drop(map, false);
                    sl.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 0.6f);
                } else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("story.heliosphere.already"), true);
            }
            return InteractionResult.SUCCESS;
        });

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

        Reg.tab("planets");
        Reg.blockWithItem("stargate", Stargate.GateBlock::new, Reg.metal(MapColor.COLOR_BLUE, 50f).lightLevel(st -> 12).explosionResistance(3600f));
        Stargate.init();
        Reg.blockWithItem("defense_silo", Asteroid.SiloBlock::new, Reg.metal(MapColor.COLOR_GRAY, 20f).explosionResistance(1200f));
        com.heliosphere.core.story.Story.impact = Asteroid::impact;
        com.heliosphere.planets.enemy.Vanguard.init();
        com.heliosphere.planets.weapon.Weapons.register();
        MachineType.addProvider(PlanetRecipes::register);
        MachineType.addProvider(com.heliosphere.planets.weapon.Weapons::recipes);
        CreativeTabs.register("planets", () -> new ItemStack(Reg.get("space_suit_helmet")), "planets");
        Heliosphere.LOG.info("Heliosphere planets ready ({} on the Moon and Mars)", Planets.MOON.identifier());
    }

    private static void food(String id, int nutrition, float saturation) {
        Reg.item(id, Item::new, new Item.Properties().food(new net.minecraft.world.food.FoodProperties(nutrition, saturation, true)));
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
