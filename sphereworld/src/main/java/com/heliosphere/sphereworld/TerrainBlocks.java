package com.heliosphere.sphereworld;

import com.heliosphere.core.Reg;
import com.heliosphere.sphereworld.terrain.Mat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lunar and Martian terrain blocks. Ids match tools/gen/content.py. */
public final class TerrainBlocks {
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<Mat, BlockState> BY_MAT = new EnumMap<>(Mat.class);

    private TerrainBlocks() {}

    private static Block soil(String id, MapColor c, float h) { return put(id, Reg.blockWithItem(id, Block::new, Reg.soil(c, h))); }
    private static Block rock(String id, MapColor c, float h) { return put(id, Reg.blockWithItem(id, Block::new, Reg.stone(c, h))); }
    private static Block ice(String id, MapColor c, float h) { return put(id, Reg.blockWithItem(id, Block::new, Reg.ice(c, h))); }
    private static Block put(String id, Block b) { BLOCKS.put(id, b); return b; }

    public static void init() {
        Reg.tab("planets");
        map(Mat.REGOLITH, soil("moon_regolith", MapColor.COLOR_LIGHT_GRAY, 0.6f));
        map(Mat.MARE_REGOLITH, soil("mare_regolith", MapColor.COLOR_GRAY, 0.6f));
        map(Mat.HE3_REGOLITH, soil("helium3_regolith", MapColor.COLOR_GRAY, 0.7f));
        map(Mat.ANORTHOSITE, rock("anorthosite", MapColor.QUARTZ, 2.0f));
        map(Mat.MARE_BASALT, rock("mare_basalt", MapColor.COLOR_BLACK, 2.2f));
        map(Mat.HIGH_TI_BASALT, rock("high_ti_basalt", MapColor.COLOR_BLACK, 2.5f));
        map(Mat.KREEP, rock("kreep_rock", MapColor.COLOR_PURPLE, 2.5f));
        map(Mat.MOON_ICE, ice("moon_ice", MapColor.ICE, 1.0f));
        map(Mat.METEORIC_IRON, rock("meteoric_iron_ore", MapColor.RAW_IRON, 3.0f));
        map(Mat.MARS_REGOLITH, soil("mars_regolith", MapColor.TERRACOTTA_ORANGE, 0.6f));
        map(Mat.MARS_SAND, soil("mars_sand", MapColor.TERRACOTTA_BROWN, 0.5f));
        map(Mat.MARS_ROCK, rock("mars_rock", MapColor.TERRACOTTA_RED, 2.0f));
        map(Mat.MARS_BASALT, rock("mars_basalt", MapColor.TERRACOTTA_BLACK, 2.4f));
        map(Mat.PERCHLORATE, soil("perchlorate_deposit", MapColor.TERRACOTTA_WHITE, 1.0f));
        map(Mat.HEMATITE, rock("hematite_deposit", MapColor.TERRACOTTA_BROWN, 2.5f));
        map(Mat.GYPSUM, rock("gypsum_deposit", MapColor.SAND, 1.5f));
        map(Mat.CLAY, soil("smectite_clay_block", MapColor.TERRACOTTA_LIGHT_GRAY, 0.8f));
        map(Mat.MARS_ICE, ice("mars_ice", MapColor.ICE, 1.0f));
        map(Mat.DRY_ICE, ice("dry_ice", MapColor.SNOW, 0.8f));
        map(Mat.OLIVINE, rock("olivine_rock", MapColor.COLOR_GREEN, 2.5f));
        rock("moon_bricks", MapColor.COLOR_LIGHT_GRAY, 2.0f);
        rock("mars_bricks", MapColor.TERRACOTTA_RED, 2.0f);
        // ---- outer solar system
        world(Mat.MERCURY_REGOLITH, "mercury_regolith", "soil", 0.6f, 0x6A6660);
        world(Mat.MERCURY_CRUST, "mercury_crust", "rock", 2.2f, 0x3A3836);
        world(Mat.HOLLOWS_DEPOSIT, "hollows_deposit", "rock", 1.2f, 0x8A96A8);
        world(Mat.VENUS_REGOLITH, "venus_regolith", "soil", 0.8f, 0x8A6A48);
        world(Mat.VENUS_BASALT, "venus_basalt", "rock", 2.5f, 0x5A4436);
        world(Mat.TESSERA_ROCK, "tessera_rock", "rock", 3.0f, 0x7A6A5A);
        world(Mat.METAL_FROST, "metal_frost", "ice", 2.0f, 0xB8B4A8);
        world(Mat.PHOBOS_REGOLITH, "phobos_regolith", "soil", 0.5f, 0x4A4240);
        world(Mat.CARBONACEOUS_ROCK, "carbonaceous_rock", "rock", 1.8f, 0x2E2A28);
        world(Mat.CERES_REGOLITH, "ceres_regolith", "soil", 0.6f, 0x5A5854);
        world(Mat.AMMONIATED_CLAY, "ammoniated_clay", "soil", 1.0f, 0x6A6660);
        world(Mat.NATRON_DEPOSIT, "natron_deposit", "rock", 1.0f, 0xD8D8D0);
        world(Mat.VESTA_REGOLITH, "vesta_regolith", "soil", 0.6f, 0x8A8478);
        world(Mat.EUCRITE, "eucrite", "rock", 2.5f, 0x6A6458);
        world(Mat.DIOGENITE, "diogenite", "rock", 2.5f, 0x6A6A40);
        world(Mat.SULFUR_CRUST, "sulfur_crust", "soil", 0.8f, 0xD8C040);
        world(Mat.SO2_FROST, "so2_frost", "ice", 0.6f, 0xE8E8D0);
        world(Mat.IO_SILICATE, "io_silicate", "rock", 2.5f, 0x3A3020);
        world(Mat.EUROPA_ICE, "europa_ice", "ice", 1.2f, 0xC8C0B0);
        world(Mat.LINEAE_SALT, "lineae_salt", "rock", 1.0f, 0x8A5A3A);
        world(Mat.GANYMEDE_DARK, "ganymede_dark", "rock", 1.5f, 0x4A4440);
        world(Mat.GROOVED_ICE, "grooved_ice", "ice", 1.5f, 0xA8A8A8);
        world(Mat.CALLISTO_REGOLITH, "callisto_regolith", "soil", 0.6f, 0x3A342E);
        world(Mat.ENCELADUS_SNOW, "enceladus_snow", "ice", 0.4f, 0xF0F4F8);
        world(Mat.TIGER_STRIPE_ICE, "tiger_stripe_ice", "ice", 1.2f, 0x9AB8D0);
        world(Mat.THOLIN_SAND, "tholin_sand", "soil", 0.5f, 0x5A3A20);
        world(Mat.TITAN_ICE_BEDROCK, "titan_ice_bedrock", "ice", 2.0f, 0x8A8070);
        world(Mat.THOLIN_DEPOSIT, "tholin_deposit", "soil", 1.0f, 0x7A4A20);
        world(Mat.MIRANDA_ICE, "miranda_ice", "ice", 1.5f, 0x9A9A98);
        world(Mat.CORONA_RIDGE, "corona_ridge", "rock", 2.0f, 0x6A6A68);
        world(Mat.NITROGEN_ICE, "nitrogen_ice", "ice", 0.6f, 0xE8E0D8);
        world(Mat.CANTALOUPE_TERRAIN, "cantaloupe_terrain", "rock", 1.2f, 0xB8A898);
        world(Mat.METHANE_ICE, "methane_ice", "ice", 1.0f, 0xD8C8C0);
        world(Mat.THOLIN_CRUST, "tholin_crust", "soil", 0.8f, 0x6A3020);
        world(Mat.WATER_ICE_BEDROCK, "water_ice_bedrock", "ice", 2.5f, 0xB0B0B8);
        world(Mat.CHARON_REGOLITH, "charon_regolith", "soil", 0.6f, 0x8A8682);
        world(Mat.ERIS_FROST, "eris_frost", "ice", 0.6f, 0xF4F4F4);
        world(Mat.AMMONIA_CLOUD, "ammonia_cloud", "cloud", 0.2f, 0xE8DCC0);
        world(Mat.NH4SH_CLOUD, "nh4sh_cloud", "cloud", 0.2f, 0xA86040);
        world(Mat.METHANE_CLOUD, "methane_cloud", "cloud", 0.2f, 0x90C8E0);
        world(Mat.LIQUID_METHANE, "liquid_methane", "methane", 100.0f, 0x1A1410);
        world(Mat.AEROSTAT_DECK, "aerostat_deck", "rock", 4.0f, 0x8A8E96);
        BY_MAT.put(Mat.LAVA, Blocks.LAVA.defaultBlockState());
        BY_MAT.put(Mat.WATER, Blocks.WATER.defaultBlockState());
        BY_MAT.put(Mat.BEDROCK, Blocks.BEDROCK.defaultBlockState());
        BY_MAT.put(Mat.AIR, Blocks.AIR.defaultBlockState());
    }

    private static void map(Mat m, Block b) { BY_MAT.put(m, b.defaultBlockState()); }

    /** Map colour per material, used when baking orbital maps. */
    public static final Map<Mat, Integer> COLOR = new EnumMap<>(Mat.class);

    private static void world(Mat m, String id, String kind, float hardness, int rgb) {
        COLOR.put(m, rgb);
        Block b = switch (kind) {
            case "cloud" -> put(id, Reg.blockWithItem(id, CloudBlock::new, Reg.soil(MapColor.SNOW, 0.2f).noCollision().noOcclusion()));
            case "methane" -> put(id, Reg.blockWithItem(id, MethaneLakeBlock::new, Reg.soil(MapColor.COLOR_BLACK, 100f).noCollision().noLootTable()));
            case "ice" -> ice(id, MapColor.ICE, hardness);
            case "soil" -> soil(id, MapColor.TERRACOTTA_GRAY, hardness);
            default -> rock(id, MapColor.STONE, hardness);
        };
        map(m, b);
    }

    public static BlockState state(Mat m) { return BY_MAT.get(m); }

    public static Block get(String id) { return BLOCKS.get(id); }
}
