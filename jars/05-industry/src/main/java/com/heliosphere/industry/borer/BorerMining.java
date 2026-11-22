package com.heliosphere.industry.borer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * What the tunnel borer makes of a block: whether it can cut it, how hard it is, what it costs, whether it is ore,
 * and which of the items it digs are junk (waste rock, thrown away but for a little lining material).
 */
public final class BorerMining {
    private BorerMining() {}

    /** The common ore tag ({@code c:ores}); ores of other mods are found by it or by a registry name ending in _ore. */
    public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

    /**
     * The plain ground of the planets: regolith, rock and sand that is neither ore nor ice. Rocks the planet machines
     * work for something of their own (helium-3 regolith, KREEP rock, high-titanium basalt, sulfur and tholin crust,
     * the clays and deposits) are not junk. Looked up in the registry, so a planet that is not installed is skipped.
     */
    private static final String[] PLANET_GROUND = {
            "anorthosite", "callisto_regolith", "carbonaceous_rock", "ceres_regolith", "charon_regolith", "corona_ridge",
            "diogenite", "eucrite", "ganymede_dark", "io_silicate", "mare_basalt", "mare_regolith", "mars_basalt",
            "mars_regolith", "mars_rock", "mars_sand", "mercury_crust", "mercury_regolith", "moon_regolith", "olivine_rock",
            "phobos_regolith", "tessera_rock", "venus_basalt", "venus_regolith", "vesta_regolith"};

    private static volatile Set<Item> junk;

    /** Junk items: waste rock and soil, the planets' plain ground, and the borer's own lining segments. */
    public static Set<Item> junk() {
        Set<Item> j = junk;
        if (j != null) return j;
        Set<Item> s = new HashSet<>(Set.of(Items.STONE, Items.COBBLESTONE, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.TUFF,
                Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.DIRT, Items.COARSE_DIRT,
                Items.GRAVEL, Items.SAND, Items.RED_SAND, Items.NETHERRACK, Items.BASALT, Items.SMOOTH_BASALT, Items.BLACKSTONE,
                Items.END_STONE));
        for (String id : PLANET_GROUND)
            BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("heliosphere", id))
                    .map(Block::asItem).filter(i -> i != Items.AIR).ifPresent(s::add);
        if (Borers.SEGMENT != null && Borers.SEGMENT.asItem() != Items.AIR) s.add(Borers.SEGMENT.asItem());   // mined lining goes back in as lining
        j = Collections.unmodifiableSet(s);
        junk = j;
        return j;
    }

    public static boolean isJunk(ItemStack stack) { return !stack.isEmpty() && junk().contains(stack.getItem()); }

    /** A block that is nothing but fluid: water or lava, still or flowing, or a bubble column. */
    public static boolean isFluid(BlockState s) {
        return s.getBlock() instanceof LiquidBlock || s.is(Blocks.BUBBLE_COLUMN);
    }

    /** Ore: the {@code c:ores} tag, a registry name ending in {@code _ore}, or ancient debris. */
    public static boolean isOre(BlockState s) {
        if (s.isAir() || isFluid(s)) return false;
        if (s.is(ORES) || s.is(Blocks.ANCIENT_DEBRIS)) return true;
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
    }

    /** Blocks the cutter cannot take: anything unbreakable by hand (bedrock, barrier, portal frames) and reinforced deepslate. */
    public static boolean unbreakable(BlockState s, BlockGetter level, BlockPos pos) {
        if (s.isAir() || isFluid(s)) return false;
        return s.getDestroySpeed(level, pos) < 0 || s.is(Blocks.REINFORCED_DEEPSLATE);
    }

    /** Hardness for cutting time and energy; air and fluids count nothing. */
    public static float hardness(BlockState s, BlockGetter level, BlockPos pos) {
        if (s.isAir() || isFluid(s)) return 0;
        return Math.max(0, s.getDestroySpeed(level, pos));
    }

    /** Energy to cut one block, in FE. */
    public static int cost(float hardness) { return Math.round(20 + 30 * hardness); }

    /** Cutting time of a face, in ticks, from the sum of its blocks' hardness. */
    public static int cuttingTicks(float hardnessSum) { return Math.max(8, Math.round(2 * hardnessSum)); }

    /** Free space the machine can back into without digging: nothing to bump into. */
    public static boolean passable(BlockState s, BlockGetter level, BlockPos pos) {
        return s.isAir() || s.getCollisionShape(level, pos).isEmpty();
    }

    /** Where the machine may be set down: air, plants and snow that are simply replaced, or open water or lava. */
    public static boolean clearForPlacing(BlockState s) {
        return s.isAir() || s.canBeReplaced() || isFluid(s);
    }
}
