package com.heliosphere.industry.borer;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/** The tunnel borer: entity, item, the lining and lamp blocks it sets into the tunnel wall, and its messages. */
public final class Borers implements ModInitializer {
    public static EntityType<TunnelBorer> ENTITY;
    public static Item ITEM;
    /** Lining segment set into the tunnel's walls, floor and roof where there is a gap, water or lava. */
    public static Block SEGMENT;
    /** A lining segment with a lamp, set into the roof every few blocks. */
    public static Block LAMP;

    @Override
    public void onInitialize() {
        Identifier id = Identifier.fromNamespaceAndPath("heliosphere", "tunnel_borer");
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<TunnelBorer>of(TunnelBorer::new, MobCategory.MISC)
                .sized(2.95f, 2.95f).fireImmune().clientTrackingRange(10).updateInterval(1).build(key));
        BorerNet.register();
        ServerPlayNetworking.registerGlobalReceiver(BorerNet.Action.TYPE, (a, ctx) -> ctx.server().execute(() -> BorerOrders.act(ctx.player(), a)));
        Reg.tab("industry");
        SEGMENT = Reg.blockWithItem("tunnel_segment", Block::new, Reg.stone(MapColor.STONE, 3f));
        LAMP = Reg.blockWithItem("tunnel_lamp", Block::new, Reg.stone(MapColor.STONE, 3f).lightLevel(s -> 15));
        ITEM = Reg.item("tunnel_borer", TunnelBorerItem::new, new Item.Properties().stacksTo(1));
        MachineType.addProvider(Borers::recipes);
    }

    /** An item of the mod by id; air when the jar that brings it is not installed (the recipe is then left out). */
    private static Item I(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("heliosphere", id)); }

    /** The machine is put together in the assembly station. */
    private static void recipes() {
        MachineType assembly = MachineType.get("assembly_station");
        if (assembly == null) return;
        MachineRecipe r = MachineRecipe.of().in(I("steel_plate"), 12).in(I("electric_motor"), 4).in(I("processor"), 1)
                .in(I("circuit_board"), 2).in(I("tungsten_ingot"), 4).in(I("si3n4_bearing"), 2).in(I("battery_cell"), 2)
                .in(Items.CHEST, 2).in(Items.DIAMOND, 3).out(ITEM).time(600).power(80);
        for (MachineRecipe.Input in : r.inputs) if (in.tag() == null && (in.item() == null || in.item() == Items.AIR)) return;
        assembly.recipe(r);
    }
}
