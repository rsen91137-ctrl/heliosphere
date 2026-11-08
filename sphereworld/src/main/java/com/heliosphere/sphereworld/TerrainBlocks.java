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
        BY_MAT.put(Mat.BEDROCK, Blocks.BEDROCK.defaultBlockState());
        BY_MAT.put(Mat.AIR, Blocks.AIR.defaultBlockState());
    }

    private static void map(Mat m, Block b) { BY_MAT.put(m, b.defaultBlockState()); }

    public static BlockState state(Mat m) { return BY_MAT.get(m); }

    public static Block get(String id) { return BLOCKS.get(id); }
}
