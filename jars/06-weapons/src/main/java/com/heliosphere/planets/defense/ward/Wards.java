package com.heliosphere.planets.defense.ward;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.planets.tech.TechMob;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Base defences that are not turrets: the ward generator (a powered field that helps players and hinders machines
 * within 32 blocks) and the blast-proof blocks (concrete and glass that no machine warhead, drill or bunker buster
 * gets through, cheap enough to build whole walls of).
 */
public final class Wards implements ModInitializer {
    public static WardGeneratorBlock GENERATOR;
    public static BlockEntityType<WardGeneratorBlockEntity> GENERATOR_BE;
    public static Block BLAST_CONCRETE, BLAST_GLASS;

    /** Above the strongest machine warhead's limit (1300, obsidian is 1200): nothing the machines fire breaks it. */
    public static final float BLAST_PROOF = 2000f;

    @Override
    public void onInitialize() {
        Reg.tab("weapons");
        GENERATOR = Reg.blockWithItem("ward_generator", WardGeneratorBlock::new, Reg.metal(MapColor.COLOR_PURPLE, 5f)
                .lightLevel(s -> s.getValue(WardGeneratorBlock.ACTIVE) ? 11 : 2));
        GENERATOR_BE = Reg.blockEntity("ward_generator", WardGeneratorBlockEntity::new, GENERATOR);
        BLAST_CONCRETE = Reg.blockWithItem("blast_concrete", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                .strength(4f, BLAST_PROOF).requiresCorrectToolForDrops().sound(SoundType.STONE));
        BLAST_GLASS = Reg.blockWithItem("blast_glass", TransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                .strength(3f, BLAST_PROOF).requiresCorrectToolForDrops().sound(SoundType.GLASS).noOcclusion()
                .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
        MachineType.addProvider(Wards::recipes);
    }

    private static Item I(String id) { return Reg.get(id); }

    private static void recipes() {
        MachineType asm = MachineType.get("assembly_station");
        if (asm == null) return;
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 6).in(I("processor"), 2).in(I("circuit_board"), 4).in(I("copper_wire"), 8)
                .in(I("battery_cell"), 2).in(Items.DIAMOND, 2).out(GENERATOR).time(600).power(120));
    }

    // ---- what the field does to a machine: per machine, the tick its malfunction ends and the tick its slowing ends ----

    private static final Map<TechMob, long[]> HINDERED = new WeakHashMap<>();

    static void slow(TechMob m, long until) { HINDERED.computeIfAbsent(m, k -> new long[2])[1] = until; }
    static void malfunction(TechMob m, long until) { HINDERED.computeIfAbsent(m, k -> new long[2])[0] = until; }

    /** Whether the machine is in a malfunction just now (it neither moves nor fires). */
    public static boolean malfunctioning(TechMob m) {
        long[] h = HINDERED.get(m);
        return h != null && h[0] > m.level().getGameTime();
    }

    /** Whether the machine is slowed by a field just now. */
    public static boolean slowed(TechMob m) {
        long[] h = HINDERED.get(m);
        return h != null && h[1] > m.level().getGameTime();
    }
}
