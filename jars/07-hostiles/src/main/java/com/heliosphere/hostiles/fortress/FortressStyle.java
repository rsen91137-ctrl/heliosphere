package com.heliosphere.hostiles.fortress;

import com.heliosphere.core.Reg;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The materials a fortress is built from, chosen by the world it stands on. */
public record FortressStyle(String name, BlockState wall, BlockState trim, BlockState accent, BlockState light, BlockState glass, BlockState floor) {

    private static BlockState mod(String id, Block fallback) {
        Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("heliosphere", id));
        return (b == null || b == Blocks.AIR ? fallback : b).defaultBlockState();
    }

    public static FortressStyle of(String body) {
        return switch (body == null ? "earth" : body) {
            case "moon" -> new FortressStyle("lunar", mod("moon_bricks", Blocks.STONE_BRICKS), Blocks.SMOOTH_STONE.defaultBlockState(),
                    Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
                    mod("moon_regolith", Blocks.GRAVEL));
            case "mars", "phobos", "deimos" -> new FortressStyle("martian", mod("mars_bricks", Blocks.RED_NETHER_BRICKS), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(),
                    Blocks.WEATHERED_COPPER.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.ORANGE_STAINED_GLASS.defaultBlockState(),
                    Blocks.TERRACOTTA.defaultBlockState());
            case "mercury", "venus", "io" -> new FortressStyle("volcanic", Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState(),
                    Blocks.GILDED_BLACKSTONE.defaultBlockState(), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState(),
                    Blocks.SMOOTH_BASALT.defaultBlockState());
            case "europa", "enceladus", "ganymede", "callisto", "titan", "miranda", "triton" -> new FortressStyle("glacial",
                    Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.CYAN_STAINED_GLASS.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState());
            case "ceres", "vesta", "pluto", "charon", "eris" -> new FortressStyle("xeno", Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState(),
                    Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
                    Blocks.PURPUR_PILLAR.defaultBlockState());
            default -> new FortressStyle("bunker", Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                    Blocks.IRON_BLOCK.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState(),
                    Blocks.SMOOTH_STONE.defaultBlockState());
        };
    }

    static BlockState fabricator() { return Reg.getBlock("robot_fabricator").defaultBlockState(); }
}
