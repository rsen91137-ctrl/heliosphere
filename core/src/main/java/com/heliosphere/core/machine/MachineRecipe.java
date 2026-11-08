package com.heliosphere.core.machine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/** A processing recipe: unordered inputs, outputs (optionally probabilistic), duration and power draw. */
public final class MachineRecipe {
    public record Input(Item item, TagKey<Item> tag, int count) {
        public boolean test(ItemStack s) { return !s.isEmpty() && (item != null ? s.is(item) : s.is(tag)); }
        public ItemStack display() {
            if (item != null) return new ItemStack(item, count);
            return BuiltInRegistries.ITEM.get(tag).flatMap(h -> h.stream().findFirst()).map(h -> new ItemStack(h, count)).orElse(ItemStack.EMPTY);
        }
    }

    public record Output(ItemStack stack, float chance) {}

    public final List<Input> inputs = new ArrayList<>();
    public final List<Output> outputs = new ArrayList<>();
    public int ticks = 200;
    public int power = 20;
    /** Chance in [0,1] that the run fails violently (engine test stand). */
    public float failChance;
    /** Precision outputs receive a manufacturing tolerance. */
    public boolean precision;
    /** Outputs keep the data components of the first input (e.g. a tested engine keeps its tolerance). */
    public boolean keepComponents;
    public boolean markTested;
    /** Optional per-recipe working condition (null = always). */
    public MachineType.Condition cond;

    public static MachineRecipe of() { return new MachineRecipe(); }

    public MachineRecipe in(ItemLike item, int count) { inputs.add(new Input(item.asItem(), null, count)); return this; }
    public MachineRecipe in(ItemLike item) { return in(item, 1); }
    public MachineRecipe in(TagKey<Item> tag, int count) { inputs.add(new Input(null, tag, count)); return this; }
    public MachineRecipe out(ItemLike item, int count) { outputs.add(new Output(new ItemStack(item, count), 1f)); return this; }
    public MachineRecipe out(ItemLike item) { return out(item, 1); }
    public MachineRecipe chance(ItemLike item, int count, float chance) { outputs.add(new Output(new ItemStack(item, count), chance)); return this; }
    public MachineRecipe time(int t) { ticks = t; return this; }
    public MachineRecipe power(int p) { power = p; return this; }
    public MachineRecipe fail(float f) { failChance = f; return this; }
    public MachineRecipe precise() { precision = true; return this; }
    public MachineRecipe keep() { keepComponents = true; return this; }
    public MachineRecipe tested() { markTested = true; keepComponents = true; return this; }
    public MachineRecipe when(MachineType.Condition c) { cond = c; return this; }
}
