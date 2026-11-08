package com.heliosphere.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Describes one kind of machine: slot layout, energy behaviour and its recipe list. */
public final class MachineType {
    public enum Kind { PROCESSOR, FUEL_GENERATOR, SOLAR, BATTERY, OXYGEN_BUBBLE }

    /** Extra working condition, e.g. "only in vacuum". Returns null when satisfied, else a lang key. */
    public interface Condition { String check(Level level, BlockPos pos); }

    private static final Map<String, MachineType> REGISTRY = new LinkedHashMap<>();

    public final String id;
    public final Kind kind;
    public final int inputs, outputs;
    public final int capacity;
    public final int generation;
    private final List<MachineRecipe> recipes = new ArrayList<>();
    /** Recipe providers run lazily on first use, after every module has registered its items. */
    private static final List<Runnable> PROVIDERS = new ArrayList<>();
    private static volatile boolean loaded;
    public Condition condition = (l, p) -> null;

    private MachineType(String id, Kind kind, int inputs, int outputs, int capacity, int generation) {
        this.id = id; this.kind = kind; this.inputs = inputs; this.outputs = outputs; this.capacity = capacity; this.generation = generation;
    }

    public static MachineType processor(String id, int inputs, int outputs) {
        return reg(new MachineType(id, Kind.PROCESSOR, inputs, outputs, 40_000, 0));
    }

    public static MachineType fuelGenerator(String id, int perTick) { return reg(new MachineType(id, Kind.FUEL_GENERATOR, 1, 0, 40_000, perTick)); }
    public static MachineType solar(String id, int peak) { return reg(new MachineType(id, Kind.SOLAR, 0, 0, 10_000, peak)); }
    public static MachineType battery(String id, int capacity) { return reg(new MachineType(id, Kind.BATTERY, 0, 0, capacity, 0)); }
    public static MachineType bubble(String id) { return reg(new MachineType(id, Kind.OXYGEN_BUBBLE, 1, 0, 20_000, 0)); }

    public MachineType when(Condition c) { this.condition = c; return this; }

    public MachineType recipe(MachineRecipe r) { recipes.add(r); return this; }

    public List<MachineRecipe> recipes() { ensureLoaded(); return recipes; }

    public static void addProvider(Runnable r) { PROVIDERS.add(r); }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        for (Runnable r : PROVIDERS) r.run();
    }

    public int slots() { return inputs + outputs; }

    public boolean producesEnergy() { return kind == Kind.FUEL_GENERATOR || kind == Kind.SOLAR || kind == Kind.BATTERY; }
    public boolean acceptsEnergy() { return kind == Kind.PROCESSOR || kind == Kind.BATTERY || kind == Kind.OXYGEN_BUBBLE; }

    private static MachineType reg(MachineType t) { REGISTRY.put(t.id, t); return t; }
    public static MachineType get(String id) { return REGISTRY.get(id); }
    public static Iterable<MachineType> all() { return REGISTRY.values(); }
}
