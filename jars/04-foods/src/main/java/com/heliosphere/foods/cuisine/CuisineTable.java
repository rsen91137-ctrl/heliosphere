package com.heliosphere.foods.cuisine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The runtime table of the food expansion, assets/heliosphere/cuisine/cuisine.json, written by
 * tools/gen/cuisine_spec.py (the single source of truth: never edit the JSON by hand). Item references are full ids
 * ("heliosphere:tofu", "minecraft:egg"); effect references too ("minecraft:speed", "heliosphere:warmth").
 */
public record CuisineTable(List<Effect> effects, List<Crop> crops, List<Ingredient> ingredients, List<Dish> dishes,
                           List<Machine> machines, List<Tool> tools, List<Named> blocks, List<Named> other,
                           List<Recipe> recipes, List<Assembly> assembly) {

    /** Where the table sits in the jar. */
    public static final String PATH = "assets/heliosphere/cuisine/cuisine.json";

    /** A food buff of our own: registered as heliosphere:{@code id}, beneficial, in {@code color}. */
    public record Effect(String id, int color) {}
    /** Hunger points and the saturation modifier (saturation = nutrition * modifier * 2). */
    public record Food(int nutrition, float saturationModifier) {}
    /** A crop: block {@code id} (age 0-3), seed item {@code seed}, produce item {@code produce} (food when {@code raw} is set). */
    public record Crop(String id, String seed, String produce, Food raw, String model, int grass) {}
    /** A kitchen ingredient, edible as it is when {@code food} is set. */
    public record Ingredient(String id, Food food) {}
    /** One buff of a dish: {@code seconds} long, {@code level} is the amplifier (0 = I). */
    public record Buff(String effect, int seconds, int level) {}
    /** A dish; {@code container} is "bowl", "bottle" or null. */
    public record Dish(String id, int nutrition, float saturationModifier, List<Buff> effects, String container) {}
    /** A cookware machine: slots, whether it needs a heat source below, FE/t while working, whether it has a start button. */
    public record Machine(String id, int inputs, int outputs, boolean heat, int power, boolean gated) {}
    public record Tool(String id, int durability) {}
    public record Named(String id) {}
    /** {@code count} of the item {@code item} (a full id, or "#namespace:tag"). */
    public record Stack(String item, int count) {}
    public record Recipe(String machine, List<Stack> inputs, Stack output, int seconds) {}
    public record Assembly(Stack output, List<Stack> inputs) {}

    /** Reads the table from the jar through the class loader. */
    public static CuisineTable load() {
        ClassLoader loader = CuisineTable.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("The food expansion's table " + PATH + " is missing from the jar");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + PATH, e);
        }
    }

    public static CuisineTable parse(JsonObject root) {
        List<Effect> effects = new ArrayList<>();
        for (JsonObject o : objects(root, "effects")) effects.add(new Effect(str(o, "id"), o.get("color").getAsInt()));
        List<Crop> crops = new ArrayList<>();
        for (JsonObject o : objects(root, "crops"))
            crops.add(new Crop(str(o, "id"), str(o, "seed"), str(o, "produce"), food(o.get("raw")), str(o, "model"), o.get("grass").getAsInt()));
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonObject o : objects(root, "ingredients")) ingredients.add(new Ingredient(str(o, "id"), food(o.get("food"))));
        List<Dish> dishes = new ArrayList<>();
        for (JsonObject o : objects(root, "dishes")) {
            List<Buff> buffs = new ArrayList<>();
            for (JsonObject b : objects(o, "effects"))
                buffs.add(new Buff(str(b, "effect"), b.get("seconds").getAsInt(), b.get("level").getAsInt()));
            dishes.add(new Dish(str(o, "id"), o.get("n").getAsInt(), o.get("s").getAsFloat(), List.copyOf(buffs), str(o, "container")));
        }
        List<Machine> machines = new ArrayList<>();
        for (JsonObject o : objects(root, "machines"))
            machines.add(new Machine(str(o, "id"), o.get("inputs").getAsInt(), o.get("outputs").getAsInt(),
                    o.get("heat").getAsBoolean(), o.get("power").getAsInt(), o.get("gated").getAsBoolean()));
        List<Tool> tools = new ArrayList<>();
        for (JsonObject o : objects(root, "tools")) tools.add(new Tool(str(o, "id"), o.get("durability").getAsInt()));
        List<Named> blocks = new ArrayList<>();
        for (JsonObject o : objects(root, "blocks")) blocks.add(new Named(str(o, "id")));
        List<Named> other = new ArrayList<>();
        for (JsonObject o : objects(root, "other")) other.add(new Named(str(o, "id")));
        List<Recipe> recipes = new ArrayList<>();
        for (JsonObject o : objects(root, "recipes"))
            recipes.add(new Recipe(str(o, "machine"), stacks(o.getAsJsonArray("inputs")), stack(o.get("output")), o.get("seconds").getAsInt()));
        List<Assembly> assembly = new ArrayList<>();
        for (JsonObject o : objects(root, "assembly"))
            assembly.add(new Assembly(stack(o.get("output")), stacks(o.getAsJsonArray("inputs"))));
        return new CuisineTable(List.copyOf(effects), List.copyOf(crops), List.copyOf(ingredients), List.copyOf(dishes),
                List.copyOf(machines), List.copyOf(tools), List.copyOf(blocks), List.copyOf(other), List.copyOf(recipes), List.copyOf(assembly));
    }

    private static List<JsonObject> objects(JsonObject parent, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Food food(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        return new Food(o.get("n").getAsInt(), o.get("s").getAsFloat());
    }

    /** {@code ["ns:id", count]}, or a bare {@code "ns:id"} for one (the assembly outputs). */
    private static Stack stack(JsonElement e) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return new Stack(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsInt() : 1);
        }
        return new Stack(e.getAsString(), 1);
    }

    private static List<Stack> stacks(JsonArray a) {
        List<Stack> out = new ArrayList<>();
        if (a != null) for (JsonElement e : a) out.add(stack(e));
        return List.copyOf(out);
    }

    /** The crop with block id {@code id}, or null. */
    public Crop crop(String id) {
        for (Crop c : crops) if (c.id().equals(id)) return c;
        return null;
    }

    /** The machine {@code id}, or null. */
    public Machine machine(String id) {
        for (Machine m : machines) if (m.id().equals(id)) return m;
        return null;
    }
}
