package com.heliosphere.industry.habitat;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The habitat field: an emitter that gives an enclosed space Earth's air, temperature and gravity on any world, and
 * force-field panels, a clear wall to enclose it with (any solid block will do as wall); and the bio-containment
 * capsule to carry creatures to it, since Earth's animals suffocate on airless worlds outside it (LifeSupport).
 */
public final class Habitat implements ModInitializer {
    public static HabitatEmitterBlock EMITTER;
    public static Block PANEL;
    public static BlockEntityType<HabitatEmitterBlockEntity> EMITTER_ENTITY;
    public static Item CAPSULE;

    @Override
    public void onInitialize() {
        Reg.tab("industry");
        EMITTER = Reg.blockWithItem("habitat_field_emitter", HabitatEmitterBlock::new,
                Reg.metal(MapColor.COLOR_LIGHT_BLUE, 5f).lightLevel(s -> s.getValue(HabitatEmitterBlock.ACTIVE) ? 12 : 2));
        PANEL = Reg.blockWithItem("habitat_field_panel", TransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(1.5f, 6f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 6)
                .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
        EMITTER_ENTITY = Reg.blockEntity("habitat_field_emitter", HabitatEmitterBlockEntity::new, EMITTER);
        CAPSULE = Reg.item("bio_capsule", BioCapsuleItem::new, new Item.Properties().stacksTo(1));
        LifeSupport.init();
        MachineType.addProvider(Habitat::recipes);
    }

    private static Item I(String id) { return Reg.get(id); }

    private static void recipes() {
        MachineType asm = MachineType.get("assembly_station");
        if (asm == null) return;
        asm.recipe(MachineRecipe.of().in(I("steel_plate"), 8).in(I("processor"), 2).in(I("electric_motor"), 2).in(I("circuit_board"), 4)
                .in(I("battery_cell"), 4).in(I("canister_oxygen"), 4).out(EMITTER).time(600).power(120));
        asm.recipe(MachineRecipe.of().in(Items.GLASS, 8).in(I("steel_plate"), 1).in(I("copper_wire"), 2).out(PANEL, 8).time(120).power(30));
        asm.recipe(MachineRecipe.of().in(Items.GLASS, 4).in(I("steel_plate"), 2).in(I("circuit_board"), 1).in(I("canister_oxygen"), 1).out(CAPSULE).time(160).power(40));
    }
}
