package com.heliosphere.industry.kitchen;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineBlock;
import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.industry.Industry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The food synthesizer and its menu. The machine is one more of the industry's processing machines (same screen with
 * the recipe list, powered through cables, six ingredient slots, the meal and any empty bucket, bottle or canister
 * come out on the right) and cooks fifteen meals from crops, meat, fish and a few things from other worlds. Every
 * meal feeds the player and gives a buff of its own for a few minutes; the captain's feast gives six at once.
 * The machine itself is put together in the assembly station.
 */
public final class Kitchen implements ModInitializer {
    public static final String MACHINE_ID = "food_synthesizer";
    /** Six ingredients in, two out: the meal, and the container an ingredient came in. */
    public static final MachineType SYNTHESIZER = MachineType.processor(MACHINE_ID, 6, 2);

    public static MachineBlock BLOCK;
    /** Every meal of the synthesizer by id, in menu order. */
    public static final Map<String, BuffFood> MEALS = new LinkedHashMap<>();

    private static final int SECOND = 20, MINUTE = 60 * SECOND;
    /** Seconds to eat a meal: the usual, a capsule swallowed in the middle of a fight, a feast taken slowly. */
    private static final float USUAL = 1.6f, QUICK = 0.8f, SLOW = 2.4f;

    @Override
    public void onInitialize() {
        BlockEntityType<MachineBlockEntity> host = Industry.MACHINE_BE;
        if (host == null) throw new IllegalStateException("The food synthesizer must be registered after the industry machines");
        Reg.tab("industry");
        BLOCK = Reg.blockWithItem(MACHINE_ID, p -> new MachineBlock(p, MACHINE_ID, () -> Industry.MACHINE_BE),
                Reg.metal(MapColor.METAL, 3.5f).lightLevel(s -> s.getValue(MachineBlock.LIT) ? 9 : 0));
        host.addSupportedBlock(BLOCK);                       // the industry's machine block entity now also lives in this block
        Industry.MACHINES.put(MACHINE_ID, BLOCK);

        Reg.tab("food");
        meal("sprint_gel", 4, 0.6f, false, USUAL, 16, fx(MobEffects.SPEED, 3 * MINUTE, 1));
        meal("miner_stew", 8, 0.8f, false, USUAL, 16, fx(MobEffects.HASTE, 5 * MINUTE, 1));
        meal("myofiber_steak", 10, 0.9f, false, USUAL, 16, fx(MobEffects.STRENGTH, 3 * MINUTE, 1));
        meal("regen_broth", 6, 0.8f, true, USUAL, 16, fx(MobEffects.REGENERATION, 2 * MINUTE + 30 * SECOND, 0));
        meal("bulwark_ration", 8, 0.9f, false, USUAL, 16, fx(MobEffects.RESISTANCE, 3 * MINUTE, 1));
        meal("cryo_jelly", 4, 0.5f, false, USUAL, 16, fx(MobEffects.FIRE_RESISTANCE, 5 * MINUTE, 0));
        meal("owl_juice", 4, 0.6f, true, USUAL, 16, fx(MobEffects.NIGHT_VISION, 8 * MINUTE, 0));
        meal("kelp_sushi", 6, 0.7f, false, USUAL, 16, fx(MobEffects.WATER_BREATHING, 6 * MINUTE, 0));
        meal("bounce_jelly", 4, 0.5f, false, USUAL, 16, fx(MobEffects.JUMP_BOOST, 3 * MINUTE, 1));
        meal("hydrogen_meringue", 4, 0.5f, false, USUAL, 16, fx(MobEffects.SLOW_FALLING, 3 * MINUTE, 0));
        meal("shield_shake", 6, 0.7f, true, USUAL, 16, fx(MobEffects.ABSORPTION, 4 * MINUTE, 1));
        meal("vital_pudding", 6, 0.7f, false, USUAL, 16, fx(MobEffects.HEALTH_BOOST, 5 * MINUTE, 1));
        meal("fortune_cookie", 3, 0.4f, false, USUAL, 16, fx(MobEffects.LUCK, 10 * MINUTE, 0));
        meal("nutrient_capsule", 2, 1.0f, false, QUICK, 16, fx(MobEffects.SATURATION, 18, 0));  // a point of hunger a tick: the whole bar
        meal("captain_feast", 16, 1.0f, false, SLOW, 4,
                fx(MobEffects.STRENGTH, 5 * MINUTE, 1), fx(MobEffects.SPEED, 5 * MINUTE, 0), fx(MobEffects.HASTE, 5 * MINUTE, 0),
                fx(MobEffects.RESISTANCE, 5 * MINUTE, 0), fx(MobEffects.ABSORPTION, 5 * MINUTE, 1), fx(MobEffects.REGENERATION, MINUTE, 0));

        MachineType.addProvider(Kitchen::recipes);
    }

    private static MobEffectInstance fx(Holder<MobEffect> effect, int ticks, int amplifier) {
        return new MobEffectInstance(effect, ticks, amplifier);
    }

    /**
     * Registers one meal: always edible (a buff is worth having on a full stomach), eaten or drunk in {@code seconds},
     * and rare when it gives more than one buff.
     */
    private static void meal(String id, int nutrition, float saturation, boolean drink, float seconds, int stack, MobEffectInstance... buffs) {
        List<MobEffectInstance> list = List.of(buffs);
        Consumable eating = (drink ? Consumables.defaultDrink() : Consumables.defaultFood()).consumeSeconds(seconds)
                .onConsume(new ApplyStatusEffectsConsumeEffect(list)).build();
        Item.Properties properties = new Item.Properties().stacksTo(stack)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible().build(), eating);
        if (list.size() > 1) properties.rarity(Rarity.RARE);
        MEALS.put(id, Reg.item(id, p -> new BuffFood(p, id, list), properties));
    }

    /** An item of the mod by id; air when the jar that brings it is not installed (its recipes are then left out). */
    private static Item I(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("heliosphere", id)); }

    private static MachineRecipe cook(int ticks, int power) { return MachineRecipe.of().time(ticks).power(power); }

    private static void add(MachineType machine, MachineRecipe recipe) {
        for (MachineRecipe.Input in : recipe.inputs) if (in.tag() == null && (in.item() == null || in.item() == Items.AIR)) return;
        for (MachineRecipe.Output out : recipe.outputs) if (out.stack().isEmpty()) return;
        machine.recipe(recipe);
    }

    /**
     * The menu, and the machine itself. No two recipes of the synthesizer use the same set of ingredients, so the
     * machine always knows which meal is meant; containers come back through the second output slot.
     */
    private static void recipes() {
        MachineType m = SYNTHESIZER;
        add(m, cook(120, 20).in(Items.SUGAR, 3).in(Items.HONEY_BOTTLE, 1).in(I("plastic"), 1)
                .out(MEALS.get("sprint_gel"), 2).out(Items.GLASS_BOTTLE, 1));
        add(m, cook(200, 30).in(Items.BEEF, 1).in(Items.POTATO, 2).in(Items.CARROT, 2).out(MEALS.get("miner_stew"), 2));
        add(m, cook(240, 40).in(Items.BEEF, 2).in(Items.EGG, 1).in(Items.BEETROOT, 2).out(MEALS.get("myofiber_steak"), 2));
        add(m, cook(200, 30).in(Items.CHICKEN, 1).in(Items.BROWN_MUSHROOM, 1).in(Items.RED_MUSHROOM, 1).out(MEALS.get("regen_broth"), 2));
        add(m, cook(240, 40).in(I("space_ration"), 2).in(Items.PORKCHOP, 1).in(Items.DRIED_KELP, 1).out(MEALS.get("bulwark_ration"), 2));
        add(m, cook(160, 30).in(I("lunar_ice_chunk"), 1).in(Items.SWEET_BERRIES, 2).in(Items.SUGAR, 1).out(MEALS.get("cryo_jelly"), 2));
        add(m, cook(140, 20).in(Items.CARROT, 3).in(Items.GLOW_BERRIES, 2).in(I("plastic"), 1).out(MEALS.get("owl_juice"), 2));
        add(m, cook(160, 25).in(Items.DRIED_KELP, 2).in(Items.SALMON, 1).in(Items.WHEAT, 1).out(MEALS.get("kelp_sushi"), 2));
        add(m, cook(140, 25).in(Items.SLIME_BALL, 1).in(Items.SUGAR, 2).in(Items.APPLE, 1).out(MEALS.get("bounce_jelly"), 2));
        add(m, cook(160, 30).in(Items.EGG, 2).in(Items.SUGAR, 1).in(I("canister_hydrogen"), 1)
                .out(MEALS.get("hydrogen_meringue"), 2).out(I("empty_canister"), 1));
        add(m, cook(160, 30).in(Items.MILK_BUCKET, 1).in(Items.COCOA_BEANS, 2).in(Items.EGG, 1)
                .out(MEALS.get("shield_shake"), 2).out(Items.BUCKET, 1));
        add(m, cook(200, 35).in(Items.MELON_SLICE, 2).in(Items.HONEY_BOTTLE, 1).in(Items.EGG, 1)
                .out(MEALS.get("vital_pudding"), 2).out(Items.GLASS_BOTTLE, 1));
        add(m, cook(120, 20).in(Items.WHEAT, 2).in(Items.SUGAR, 1).in(Items.GOLD_NUGGET, 2).out(MEALS.get("fortune_cookie"), 3));
        add(m, cook(100, 40).in(I("space_ration"), 1).in(Items.GOLDEN_CARROT, 1).in(I("plastic"), 1).out(MEALS.get("nutrient_capsule"), 2));
        add(m, cook(300, 60).in(MEALS.get("myofiber_steak"), 1).in(MEALS.get("regen_broth"), 1).in(Items.GOLDEN_APPLE, 1)
                .in(I("tholin"), 2).in(I("europan_salt"), 2).in(I("lunar_ice_chunk"), 1).out(MEALS.get("captain_feast"), 1));

        MachineType assembly = MachineType.get("assembly_station");
        if (assembly != null) add(assembly, MachineRecipe.of().in(I("steel_plate"), 6).in(I("circuit_board"), 2).in(I("electric_motor"), 1)
                .in(I("copper_wire"), 4).in(Items.SMOKER, 1).in(Items.GLASS, 2).out(BLOCK).time(300).power(60));
    }
}
