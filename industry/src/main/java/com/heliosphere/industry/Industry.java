package com.heliosphere.industry;

import com.heliosphere.core.CreativeTabs;
import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import com.heliosphere.core.block.CoreBlocks;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.machine.MachineBlock;
import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Earth industry: 22 new ores, refining and alloying chains, precision manufacturing and power. */
public final class Industry implements ModInitializer {
    public static final Map<String, MachineBlock> MACHINES = new LinkedHashMap<>();
    public static BlockEntityType<MachineBlockEntity> MACHINE_BE;

    // machine types (recipes are attached lazily in IndustryRecipes)
    public static final MachineType COAL_GENERATOR = MachineType.fuelGenerator("coal_generator", 40)
            .when((l, p) -> Environment.breathable(l, p) ? null : "condition.heliosphere.needs_oxygen");
    public static final MachineType SOLAR_PANEL = MachineType.solar("solar_panel", 30);
    public static final MachineType BATTERY_BANK = MachineType.battery("battery_bank", 2_000_000);
    public static final MachineType ARC_FURNACE = MachineType.processor("electric_arc_furnace", 2, 2);
    public static final MachineType ALLOY_FURNACE = MachineType.processor("alloy_furnace", 3, 1);
    public static final MachineType ELECTROLYZER = MachineType.processor("electrolyzer", 3, 3);
    public static final MachineType CHEMICAL_REACTOR = MachineType.processor("chemical_reactor", 3, 2);
    public static final MachineType REFINERY = MachineType.processor("refinery", 2, 3);
    public static final MachineType AIR_SEPARATOR = MachineType.processor("air_separator", 1, 3)
            .when((l, p) -> Environment.breathable(l, p) && Environment.pressureBar(l) > 0.5 ? null : "condition.heliosphere.needs_air");
    public static final MachineType VACUUM_FURNACE = MachineType.processor("vacuum_furnace", 2, 2);
    public static final MachineType CNC = MachineType.processor("cnc_machine", 2, 1);
    public static final MachineType PRINTER = MachineType.processor("metal_3d_printer", 2, 1);
    public static final MachineType ELECTRONICS = MachineType.processor("electronics_assembler", 3, 1);
    public static final MachineType ASSEMBLY = MachineType.processor("assembly_station", 9, 1);
    public static final MachineType TEST_STAND = MachineType.processor("engine_test_stand", 1, 1);
    public static final MachineType CENTRIFUGE = MachineType.processor("centrifuge", 1, 2);

    @Override
    public void onInitialize() {
        Reg.tab("industry");
        for (String[] ore : GenIds.ORES) {
            float h = switch (ore[1]) { case "diamond" -> 4.5f; case "iron" -> 3.5f; default -> 3.0f; };
            Reg.blockWithItem(ore[0] + "_ore", Block::new, Reg.stone(MapColor.STONE, h));
            Reg.blockWithItem("deepslate_" + ore[0] + "_ore", Block::new, Reg.deepslate(MapColor.DEEPSLATE, h));
        }
        Reg.blockWithItem("oil_sand", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8f).sound(SoundType.SAND));
        for (String[] it : GenIds.ITEMS) Reg.item(it[0]);

        List<MachineBlock> blocks = new ArrayList<>();
        for (String[] m : GenIds.MACHINES) {
            String id = m[0];
            MachineBlock b = Reg.blockWithItem(id, p -> new MachineBlock(p, id, () -> MACHINE_BE),
                    Reg.metal(MapColor.METAL, 3.5f).lightLevel(s -> s.getValue(MachineBlock.LIT) ? 9 : 0));
            MACHINES.put(id, b);
            blocks.add(b);
        }
        MACHINE_BE = Reg.blockEntity("industry_machine", (pos, state) -> new MachineBlockEntity(MACHINE_BE, pos, state),
                blocks.toArray(new Block[0]));

        for (String[] ore : GenIds.ORES) {
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("ore_" + ore[0]));
        }
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.DESERT, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS,
                Biomes.BEACH, Biomes.SAVANNA), GenerationStep.Decoration.UNDERGROUND_ORES, placed("oil_sand"));

        MachineType.addProvider(IndustryRecipes::register);
        CreativeTabs.register("industry", () -> new ItemStack(MACHINES.get("electric_arc_furnace")), "industry", "core");
        Heliosphere.LOG.info("Heliosphere industry: {} ores, {} machines", GenIds.ORES.length, MACHINES.size());
        CoreBlocks.CABLE.asItem();
    }

    private static ResourceKey<PlacedFeature> placed(String id) { return ResourceKey.create(Registries.PLACED_FEATURE, Heliosphere.id(id)); }
}
