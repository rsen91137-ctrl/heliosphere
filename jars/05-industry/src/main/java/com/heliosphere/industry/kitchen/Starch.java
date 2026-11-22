package com.heliosphere.industry.kitchen;

import com.heliosphere.core.Reg;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.machine.MachineBlock;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.industry.Industry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.MapColor;

import java.util.List;

/**
 * Starch from carbon dioxide, as in the artificial starch synthesis: the starch reactor turns CO2 (a canister, dry ice,
 * or the open air, quickly where the air is CO2 as on Mars and Venus, slowly on Earth) and water (a bucket or ice)
 * into starch with electricity; the CO2-and-hydrogen route needs no water. The food synthesizer makes every one of
 * its meals and the staple foods out of starch alone, each starch-made portion feeding one shank (two points) less
 * than the real thing, never below one point; buffs stay. The starch-made food is named and marked as such and does
 * not stack with the real thing.
 */
public final class Starch {
    private Starch() {}

    public static final String REACTOR_ID = "starch_reactor";
    /** CO2 and water in (three slots), starch and the empty canister out. */
    public static final MachineType REACTOR = MachineType.processor(REACTOR_ID, 3, 2);
    public static MachineBlock REACTOR_BLOCK;
    public static Item STARCH;

    /** Hunger points a starch-made portion feeds less than the real food: one shank. */
    public static final int LESS = 2;

    /** Where the air is mostly carbon dioxide (Mars, Venus): the reactor draws it straight from the air. */
    private static final MachineType.Condition CO2_AIR = (level, pos) -> {
        var body = Planets.bodyOf(level);
        boolean co2 = body != null && ("mars".equals(body.id) || "venus".equals(body.id));
        return co2 && level.canSeeSky(pos.above()) ? null : "condition.heliosphere.co2_air";
    };
    /** Any air at all, with the sky open above the reactor: slow direct air capture (Earth's 0.04 % CO2). */
    private static final MachineType.Condition OPEN_AIR = (level, pos) ->
            Environment.hasAtmosphere(level) && level.canSeeSky(pos.above()) ? null : "condition.heliosphere.open_air";

    /** Called from {@link Kitchen#onInitialize} after the synthesizer and its meals exist. */
    static void register() {
        Reg.tab("industry");
        REACTOR_BLOCK = Reg.blockWithItem(REACTOR_ID, p -> new MachineBlock(p, REACTOR_ID, () -> Industry.MACHINE_BE),
                Reg.metal(MapColor.COLOR_LIGHT_GREEN, 3.5f).lightLevel(s -> s.getValue(MachineBlock.LIT) ? 10 : 0));
        Industry.MACHINE_BE.addSupportedBlock(REACTOR_BLOCK);
        Industry.MACHINES.put(REACTOR_ID, REACTOR_BLOCK);
        Reg.tab("food");
        STARCH = Reg.item("starch", Item::new, new Item.Properties());
        MachineType.addProvider(Starch::recipes);
    }

    private static Item I(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("heliosphere", id)); }

    private static void add(MachineType machine, MachineRecipe recipe) {
        for (MachineRecipe.Input in : recipe.inputs) if (in.tag() == null && (in.item() == null || in.item() == Items.AIR)) return;
        for (MachineRecipe.Output out : recipe.outputs) if (out.stack().isEmpty()) return;
        machine.recipe(recipe);
    }

    private static void recipes() {
        MachineType r = REACTOR;
        Item co2 = I("canister_co2"), h2 = I("canister_hydrogen"), empty = I("empty_canister");
        add(r, MachineRecipe.of().in(co2, 1).in(Items.WATER_BUCKET, 1).out(STARCH, 6).out(empty, 1).time(240).power(48));
        add(r, MachineRecipe.of().in(co2, 1).in(h2, 1).out(STARCH, 8).out(empty, 2).time(200).power(40));       // CO2 + H2 -> methanol -> starch
        add(r, MachineRecipe.of().in(I("dry_ice"), 2).in(Items.WATER_BUCKET, 1).out(STARCH, 6).time(240).power(48));
        add(r, MachineRecipe.of().in(co2, 1).in(Items.ICE, 2).out(STARCH, 6).out(empty, 1).time(260).power(48));
        add(r, MachineRecipe.of().in(co2, 1).in(I("lunar_ice_chunk"), 2).out(STARCH, 6).out(empty, 1).time(260).power(48));
        add(r, MachineRecipe.of().in(Items.WATER_BUCKET, 1).out(STARCH, 4).time(300).power(56).when(CO2_AIR));
        add(r, MachineRecipe.of().in(Items.WATER_BUCKET, 1).out(STARCH, 2).time(480).power(64).when(OPEN_AIR));

        MachineType assembly = MachineType.get("assembly_station");      // the reactor itself: grow lights, glass tubes, control electronics
        if (assembly != null) add(assembly, MachineRecipe.of().in(I("steel_plate"), 6).in(Items.GLASS, 4).in(I("circuit_board"), 2).in(I("processor"), 1)
                .in(I("copper_wire"), 4).in(Items.GLOWSTONE_DUST, 4).out(REACTOR_BLOCK).time(300).power(60));

        // the food synthesizer: everything out of starch alone (choose the dish in its recipe list)
        MachineType m = Kitchen.SYNTHESIZER;
        String[] ids = {"sprint_gel", "miner_stew", "myofiber_steak", "regen_broth", "bulwark_ration", "cryo_jelly", "owl_juice", "kelp_sushi",
                "bounce_jelly", "hydrogen_meringue", "shield_shake", "vital_pudding", "fortune_cookie", "nutrient_capsule", "captain_feast"};
        int[] starch = {2, 3, 4, 3, 3, 2, 2, 3, 2, 2, 3, 3, 2, 2, 10};
        int[] count = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 2, 1};
        for (int i = 0; i < ids.length; i++) {
            Item meal = Kitchen.MEALS.get(ids[i]);
            if (meal != null) made(m, meal, count[i], starch[i], null, 0, ids[i].equals("captain_feast") ? 400 : 120);
        }
        made(m, Items.BREAD, 2, 2, null, 0, 100);
        made(m, Items.COOKIE, 4, 1, null, 0, 80);
        made(m, Items.BAKED_POTATO, 2, 2, null, 0, 100);
        made(m, Items.PUMPKIN_PIE, 1, 3, null, 0, 140);
        made(m, Items.COOKED_BEEF, 2, 3, null, 0, 140);
        made(m, Items.COOKED_PORKCHOP, 2, 3, null, 0, 140);
        made(m, Items.COOKED_CHICKEN, 2, 2, null, 0, 120);
        made(m, Items.COOKED_MUTTON, 2, 2, null, 0, 120);
        made(m, Items.COOKED_COD, 2, 2, null, 0, 120);
        made(m, Items.COOKED_SALMON, 2, 2, null, 0, 120);
        made(m, Items.MUSHROOM_STEW, 1, 2, Items.BOWL, 1, 120);
        made(m, Items.BEETROOT_SOUP, 1, 2, Items.BOWL, 1, 120);
        made(m, Items.RABBIT_STEW, 1, 3, Items.BOWL, 1, 160);
        made(m, Items.GOLDEN_CARROT, 1, 1, Items.GOLD_NUGGET, 8, 120);
        String[] space = {"space_ration", "tube_beef_paste", "freeze_dried_fruit", "astronaut_ice_cream", "space_coffee", "mars_potato",
                "hydroponic_salad", "spirulina_bar", "rehydrated_pasta", "plume_soda", "tholin_protein"};
        int[] spaceStarch = {3, 3, 2, 2, 1, 2, 2, 2, 3, 1, 3};
        for (int i = 0; i < space.length; i++) made(m, I(space[i]), 2, spaceStarch[i], null, 0, 120);
    }

    /** A synthesizer recipe: {@code starch} starch (and an extra ingredient, e.g. the bowl) -> {@code count} starch-made {@code food}. */
    private static void made(MachineType m, Item food, int count, int starch, ItemLike extra, int extraCount, int ticks) {
        if (food == null || food == Items.AIR) return;
        MachineRecipe recipe = MachineRecipe.of().in(STARCH, starch);
        if (extra != null) recipe.in(extra, extraCount);
        recipe.out(food, count).time(ticks).power(20);
        if (recipe.outputs.isEmpty()) return;
        starchMade(recipe.outputs.get(0).stack());        // the template the machine copies for every portion
        add(m, recipe);
    }

    /** Marks {@code stack} as made of starch: one shank less food, its name and a line saying so. */
    public static ItemStack starchMade(ItemStack stack) {
        FoodProperties f = stack.get(DataComponents.FOOD);
        if (f != null) {
            int n = Math.max(1, f.nutrition() - LESS);
            float saturation = f.nutrition() > 0 ? f.saturation() * n / f.nutrition() : f.saturation();
            stack.set(DataComponents.FOOD, new FoodProperties(n, saturation, f.canAlwaysEat()));
        }
        if (Kitchen.MEALS.get("nutrient_capsule") == stack.getItem())       // it feeds through its Saturation effect: that one shank less too
            stack.set(DataComponents.CONSUMABLE, Consumables.defaultFood().consumeSeconds(0.8f)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.SATURATION, 17, 0)))).build());
        Component base = stack.getItemName();
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.heliosphere.starch_made", base));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("tooltip.heliosphere.starch_made").withStyle(ChatFormatting.GRAY))));
        return stack;
    }
}
