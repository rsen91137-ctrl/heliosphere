package com.heliosphere.foods.cuisine;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.core.machine.MachineBlock;
import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.core.machine.Startable;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The food expansion: crops and seeds, kitchen ingredients, dishes with buffs, hand tools, cookware machines, the
 * induction stove and the hydroponic planter, all registered from cuisine.json (written by tools/gen/cuisine_spec.py).
 *
 * <p>Everything is reachable by its table id for tests and other code: {@link #ITEMS} (every item), {@link #BLOCKS}
 * (every block), {@link #CROPS}/{@link #SEEDS}/{@link #PRODUCE} by crop id, {@link #DISHES}, {@link #INGREDIENTS},
 * {@link #TOOLS}, {@link #MACHINES} (the machine types) and {@link #COOKWARE} (their blocks) by id.
 */
public final class Cuisine implements ModInitializer {
    public static final String NS = Heliosphere.MOD_ID;

    public static final String TIP_HEAT = "tooltip.heliosphere.cuisine.heat";
    public static final String TIP_CHOOSE = "tooltip.heliosphere.cuisine.choose";
    public static final String TIP_STOVE = "tooltip.heliosphere.induction_stove";
    public static final String TIP_PLANTER = "tooltip.heliosphere.hydroponic_planter";
    /** Right-click message of the stove and the planter: block name, stored FE, capacity. */
    public static final String MSG_STORED = "message.heliosphere.cuisine.stored";
    /** Shown where a language file lacks {@link #MSG_STORED} (the en_us text). */
    static final String MSG_STORED_FALLBACK = "%s · Charge %s / %s FE";

    /** Id of the block entity type all cookware machines share. */
    public static final String MACHINE_BE_ID = "cuisine_machine";
    public static final float COMPOST_SEED = 0.3f, COMPOST_PRODUCE = 0.65f;
    /** The electric cookware and the two blocks, made in the assembly station like the other machines. */
    public static final int ASSEMBLY_TICKS = 300, ASSEMBLY_POWER = 60;

    public static CuisineTable TABLE;
    /** Every item of the expansion by id: seeds, produce, ingredients, dishes, tools, machine and block items, the seed packet. */
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    /** Every block of the expansion by id: crops, cookware, the induction stove and the planter. */
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    /** By crop id (the block id): the crop block, its seed, its produce. */
    public static final Map<String, CuisineCropBlock> CROPS = new LinkedHashMap<>();
    public static final Map<String, Item> SEEDS = new LinkedHashMap<>();
    public static final Map<String, Item> PRODUCE = new LinkedHashMap<>();
    public static final Map<String, Item> INGREDIENTS = new LinkedHashMap<>();
    public static final Map<String, DishItem> DISHES = new LinkedHashMap<>();
    public static final Map<String, KitchenToolItem> TOOLS = new LinkedHashMap<>();
    /** The cookware machine types and their blocks, by machine id. */
    public static final Map<String, MachineType> MACHINES = new LinkedHashMap<>();
    public static final Map<String, CookwareBlock> COOKWARE = new LinkedHashMap<>();
    /** Machine ids that need a heat source below. */
    public static final Set<String> HEAT_MACHINES = new LinkedHashSet<>();

    public static BlockEntityType<MachineBlockEntity> MACHINE_BE;
    public static BlockEntityType<InductionStoveBlockEntity> STOVE_BE;
    public static BlockEntityType<HydroponicPlanterBlockEntity> PLANTER_BE;
    public static InductionStoveBlock INDUCTION_STOVE;
    public static HydroponicPlanterBlock HYDROPONIC_PLANTER;
    public static SeedPacketItem SEED_PACKET;

    private static boolean ready;

    @Override
    public void onInitialize() { initialize(); }

    public static synchronized void initialize() {
        if (ready) return;
        ready = true;
        Heliosphere.bootstrap();
        TABLE = CuisineTable.load();
        CuisineEffects.register(TABLE.effects());

        Reg.tab("food");
        registerCookware();
        registerBlocks();
        registerTools();
        registerOther();
        registerCrops();
        registerIngredients();
        registerDishes();

        MachineType.addProvider(Cuisine::recipes);
        GrassSeeds.register();
        for (Item seed : SEEDS.values()) CompostingChanceRegistry.INSTANCE.add(seed, COMPOST_SEED);
        for (Item produce : new LinkedHashSet<>(PRODUCE.values())) CompostingChanceRegistry.INSTANCE.add(produce, COMPOST_PRODUCE);
        Heliosphere.LOG.info("[cuisine] {} crops, {} ingredients, {} dishes, {} cookware machines, {} effects",
                CROPS.size(), INGREDIENTS.size(), DISHES.size(), COOKWARE.size(), CuisineEffects.ALL.size());
    }

    // ---- registration ----

    private static void put(String id, Item item) { ITEMS.put(id, item); }

    private static void registerCookware() {
        for (CuisineTable.Machine m : TABLE.machines()) {
            MachineType type = MachineType.processor(m.id(), m.inputs(), m.outputs());
            if (m.heat()) {
                type.when(Heat.CONDITION);
                HEAT_MACHINES.add(m.id());
            }
            if (m.gated()) Startable.gate(m.id());
            MACHINES.put(m.id(), type);

            // heat machines cook on what they stand on: their recipes take no power (see recipes())
            boolean usesPower = !m.heat() && m.power() > 0;
            CookwareBlock block = Reg.block(m.id(), p -> new CookwareBlock(p, m.id(), () -> MACHINE_BE, m.heat(), m.gated(), usesPower),
                    cookwareProperties(m.id(), CookwareBlock.fullCube(m.id())));
            List<String> hints = new ArrayList<>();
            if (m.heat()) hints.add(TIP_HEAT);
            if (m.gated()) hints.add(TIP_CHOOSE);
            put(m.id(), Reg.blockItem(block, m.id(), (b, p) -> new CuisineBlockItem(b, p, hints), new Item.Properties()));
            COOKWARE.put(m.id(), block);
            BLOCKS.put(m.id(), block);
        }
        MACHINE_BE = Reg.blockEntity(MACHINE_BE_ID, (pos, state) -> new MachineBlockEntity(MACHINE_BE, pos, state),
                COOKWARE.values().toArray(new Block[0]));
    }

    /** No tool needed to pick a machine up again (the loot tables drop the block). */
    private static BlockBehaviour.Properties cookwareProperties(String id, boolean full) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of();
        switch (id) {
            case "wok" -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5f, 4f).sound(SoundType.METAL);
            case "cooking_pot" -> p.mapColor(MapColor.METAL).strength(1.5f, 4f).sound(SoundType.METAL);
            case "steamer" -> p.mapColor(MapColor.SAND).strength(1f, 1.5f).sound(SoundType.BAMBOO_WOOD);
            case "fermenter" -> p.mapColor(MapColor.TERRACOTTA_BROWN).strength(1.25f, 4f).sound(SoundType.DECORATED_POT);
            case "oven" -> p.mapColor(MapColor.METAL).strength(2.5f, 6f).sound(SoundType.METAL)
                    .lightLevel(s -> s.getValue(MachineBlock.LIT) ? 9 : 0);
            case "grain_mill" -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(2.5f, 6f).sound(SoundType.METAL);
            case "juicer" -> p.mapColor(MapColor.SNOW).strength(1.5f, 4f).sound(SoundType.METAL);
            default -> p.mapColor(MapColor.METAL).strength(2f, 6f).sound(SoundType.METAL);
        }
        if (!full) p.noOcclusion();
        return p;
    }

    private static void registerBlocks() {
        for (CuisineTable.Named b : TABLE.blocks()) {
            String id = b.id();
            Block block;
            List<String> hints;
            switch (id) {
                case "induction_stove" -> {
                    block = INDUCTION_STOVE = Reg.block(id, InductionStoveBlock::new, BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK).strength(2f, 6f).sound(SoundType.METAL)
                            .lightLevel(s -> s.getValue(InductionStoveBlock.LIT) ? 5 : 0));
                    hints = List.of(TIP_STOVE);
                }
                case "hydroponic_planter" -> {
                    block = HYDROPONIC_PLANTER = Reg.block(id, HydroponicPlanterBlock::new, BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL).strength(2f, 6f).sound(SoundType.METAL).noOcclusion()
                            .lightLevel(s -> s.getValue(HydroponicPlanterBlock.LIT) ? HydroponicPlanterBlock.LIGHT : 0));
                    hints = List.of(TIP_PLANTER);
                }
                default -> {
                    block = Reg.block(id, Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2f, 6f).sound(SoundType.METAL));
                    hints = List.of();
                }
            }
            put(id, Reg.blockItem(block, id, (blk, p) -> new CuisineBlockItem(blk, p, hints), new Item.Properties()));
            BLOCKS.put(id, block);
        }
        if (INDUCTION_STOVE != null) STOVE_BE = Reg.blockEntity("induction_stove", InductionStoveBlockEntity::new, INDUCTION_STOVE);
        if (HYDROPONIC_PLANTER != null) PLANTER_BE = Reg.blockEntity("hydroponic_planter", HydroponicPlanterBlockEntity::new, HYDROPONIC_PLANTER);
    }

    private static void registerTools() {
        for (CuisineTable.Tool t : TABLE.tools()) {
            KitchenToolItem tool = Reg.item(t.id(), KitchenToolItem::new, new Item.Properties().durability(Math.max(1, t.durability())));
            TOOLS.put(t.id(), tool);
            put(t.id(), tool);
        }
    }

    private static void registerOther() {
        for (CuisineTable.Named o : TABLE.other()) {
            if ("seed_packet".equals(o.id())) {
                SEED_PACKET = Reg.item(o.id(), SeedPacketItem::new, new Item.Properties());
                put(o.id(), SEED_PACKET);
            } else put(o.id(), Reg.item(o.id(), Item::new, new Item.Properties()));
        }
    }

    private static void registerCrops() {
        for (CuisineTable.Crop c : TABLE.crops()) {
            CuisineCropBlock crop = Reg.block(c.id(), p -> new CuisineCropBlock(p, c.id()), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
            CROPS.put(c.id(), crop);
            BLOCKS.put(c.id(), crop);
            Item seed = Reg.item(c.seed(), p -> new BlockItem(crop, p), new Item.Properties().useItemDescriptionPrefix());
            crop.setSeed(seed);
            SEEDS.put(c.id(), seed);
            put(c.seed(), seed);
        }
        for (CuisineTable.Crop c : TABLE.crops()) {
            Item produce = ITEMS.get(c.produce());
            if (produce == null) {
                Item.Properties p = new Item.Properties();
                if (c.raw() != null) edible(p, c.raw(), false, servedIn(c.produce()), List.of());
                produce = Reg.item(c.produce(), Item::new, p);
                put(c.produce(), produce);
            }
            PRODUCE.put(c.id(), produce);
        }
    }

    /** Ingredients made into a glass bottle: using one in a recipe gives the bottle back. */
    private static final java.util.Set<String> BOTTLED = java.util.Set.of("soy_sauce", "vinegar", "cooking_oil");

    private static void registerIngredients() {
        for (CuisineTable.Ingredient i : TABLE.ingredients()) {
            Item.Properties p = new Item.Properties();
            if (i.food() != null) edible(p, i.food(), false, servedIn(i.id()), List.of());
            if (BOTTLED.contains(i.id())) p.craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE);   // the sauce is used, the bottle comes back
            Item item = Reg.item(i.id(), Item::new, p);
            INGREDIENTS.put(i.id(), item);
            put(i.id(), item);
        }
    }

    private static void registerDishes() {
        for (CuisineTable.Dish d : TABLE.dishes()) {
            List<MobEffectInstance> buffs = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (CuisineTable.Buff b : d.effects()) {
                Holder<MobEffect> effect = effect(b.effect());
                if (effect == null) {
                    Heliosphere.LOG.warn("[cuisine] {}: unknown effect {} left out", d.id(), b.effect());
                    continue;
                }
                buffs.add(new MobEffectInstance(effect, Math.max(1, b.seconds()) * 20, Math.max(0, b.level())));
                Identifier id = Identifier.tryParse(b.effect());
                if (id != null && NS.equals(id.getNamespace()) && CuisineEffects.ALL.containsKey(id.getPath())) {
                    String note = "effect." + NS + "." + id.getPath() + ".desc";
                    if (!notes.contains(note)) notes.add(note);
                }
            }
            Item.Properties p = edible(new Item.Properties(), new CuisineTable.Food(d.nutrition(), d.saturationModifier()), true, d.container(), buffs);
            DishItem dish = Reg.item(d.id(), q -> new DishItem(q, d.id(), buffs, notes), p);
            DISHES.put(d.id(), dish);
            put(d.id(), dish);
        }
    }

    /**
     * Makes {@code p} a food: {@code food} hunger and saturation (saturation = nutrition x modifier x 2), drunk from a
     * bottle or eaten (from a bowl), the container given back and the stack held to 16 when there is one, and
     * {@code buffs} applied when it is finished.
     */
    private static Item.Properties edible(Item.Properties p, CuisineTable.Food food, boolean alwaysEdible, String container, List<MobEffectInstance> buffs) {
        boolean bowl = "bowl".equals(container), bottle = "bottle".equals(container);
        FoodProperties.Builder f = new FoodProperties.Builder().nutrition(food.nutrition()).saturationModifier(food.saturationModifier());
        if (alwaysEdible) f.alwaysEdible();
        Consumable.Builder eating = bottle ? Consumables.defaultDrink() : Consumables.defaultFood();
        if (!buffs.isEmpty()) eating.onConsume(new ApplyStatusEffectsConsumeEffect(List.copyOf(buffs)));
        p.food(f.build(), eating.build());
        if (bowl) p.stacksTo(16).usingConvertsTo(Items.BOWL);
        else if (bottle) p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE);
        return p;
    }

    /**
     * What an edible ingredient comes in, read from the recipes making it: "bowl" when every one of them uses up a bowl
     * per portion (steamed rice), "bottle" likewise for glass bottles, else null. Eating it then gives the bowl back.
     */
    private static String servedIn(String id) {
        String ref = NS + ":" + id;
        boolean any = false, bowl = true, bottle = true;
        for (CuisineTable.Recipe r : TABLE.recipes()) {
            if (!r.output().item().equals(ref)) continue;
            any = true;
            int bowls = 0, bottles = 0;
            for (CuisineTable.Stack in : r.inputs()) {
                if (in.item().equals("minecraft:bowl")) bowls += in.count();
                if (in.item().equals("minecraft:glass_bottle")) bottles += in.count();
            }
            bowl &= bowls >= r.output().count();
            bottle &= bottles >= r.output().count();
        }
        return !any ? null : bowl ? "bowl" : bottle ? "bottle" : null;
    }

    // ---- recipes (run when the machines first need them, after every module has registered its items) ----

    private static void recipes() {
        if (TABLE == null) return;
        int added = 0, skipped = 0;
        for (CuisineTable.Recipe r : TABLE.recipes()) {
            MachineType type = MACHINES.get(r.machine());
            CuisineTable.Machine m = TABLE.machine(r.machine());
            MachineRecipe recipe = type == null || m == null ? null : build(r.inputs(), r.output());
            if (recipe == null) {
                skipped++;
                continue;
            }
            type.recipe(recipe.time(Math.max(1, r.seconds() * 20)).power(m.heat() ? 0 : Math.max(0, m.power())));
            added++;
        }
        MachineType assembly = MachineType.get("assembly_station");
        if (assembly != null) {
            for (CuisineTable.Assembly a : TABLE.assembly()) {
                MachineRecipe recipe = build(a.inputs(), a.output());
                if (recipe == null) {
                    skipped++;
                    continue;
                }
                assembly.recipe(recipe.time(ASSEMBLY_TICKS).power(ASSEMBLY_POWER));
                added++;
            }
        }
        Heliosphere.LOG.info("[cuisine] {} machine recipes added, {} left out (an item they use is not installed)", added, skipped);
    }

    /** A recipe from table stacks, or null when one of its items does not exist. */
    private static MachineRecipe build(List<CuisineTable.Stack> inputs, CuisineTable.Stack output) {
        Item out = item(output.item());
        if (out == Items.AIR || output.count() <= 0) return null;
        MachineRecipe recipe = MachineRecipe.of();
        for (CuisineTable.Stack in : inputs) {
            if (in.count() <= 0) continue;
            if (in.item().startsWith("#")) {
                Identifier tag = Identifier.tryParse(in.item().substring(1));
                if (tag == null) return null;
                recipe.in(TagKey.create(Registries.ITEM, tag), in.count());
            } else {
                Item item = item(in.item());
                if (item == Items.AIR) return null;
                recipe.in(item, in.count());
            }
        }
        if (recipe.inputs.isEmpty()) return null;
        return recipe.out(out, output.count());
    }

    // ---- lookups ----

    /** The item {@code ref} ("ns:path", or a bare id of ours), or air when it does not exist. */
    public static Item item(String ref) {
        if (ref == null) return Items.AIR;
        Identifier id = Identifier.tryParse(ref.contains(":") ? ref : NS + ":" + ref);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return Items.AIR;
        return BuiltInRegistries.ITEM.getValue(id);
    }

    /** A block of the expansion by id, or null. */
    public static Block block(String id) { return BLOCKS.get(id); }

    /** A cookware machine type by id, or null. */
    public static MachineType machine(String id) { return MACHINES.get(id); }

    /** Whether the machine {@code id} needs a heat source below. */
    public static boolean isHeatMachine(String id) { return id != null && HEAT_MACHINES.contains(id); }

    /** The effect {@code ref} ("minecraft:speed", "heliosphere:warmth"; no namespace = minecraft), or null. */
    public static Holder<MobEffect> effect(String ref) {
        if (ref == null) return null;
        Identifier id = Identifier.tryParse(ref.contains(":") ? ref : "minecraft:" + ref);
        if (id == null) return null;
        if (NS.equals(id.getNamespace())) {
            Holder<MobEffect> ours = CuisineEffects.ALL.get(id.getPath());
            if (ours != null) return ours;
        }
        return BuiltInRegistries.MOB_EFFECT.get(id).<Holder<MobEffect>>map(h -> h).orElse(null);
    }

    /** One crop seed picked by the crops' grass weights (as from grass and the seed packet), or null before start-up. */
    public static Item randomSeed(RandomSource random) {
        if (TABLE == null) return null;
        int total = 0;
        for (CuisineTable.Crop c : TABLE.crops()) if (SEEDS.containsKey(c.id())) total += Math.max(0, c.grass());
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        for (CuisineTable.Crop c : TABLE.crops()) {
            Item seed = SEEDS.get(c.id());
            int weight = Math.max(0, c.grass());
            if (seed == null || weight == 0) continue;
            if (pick < weight) return seed;
            pick -= weight;
        }
        return null;
    }

    /**
     * Tells {@code player} how much a stove or planter holds. The numbers are formatted here on the server, so in a
     * fixed locale (a dedicated server's default locale is anybody's guess).
     */
    static void showCharge(Player player, Block block, EnergyNode node) {
        player.displayClientMessage(Component.translatableWithFallback(MSG_STORED, MSG_STORED_FALLBACK, block.getName(),
                String.format(Locale.ROOT, "%,d", node.energy()), String.format(Locale.ROOT, "%,d", node.capacity())).withStyle(ChatFormatting.AQUA), true);
    }

    /** Every item of the expansion by id, read-only. */
    public static Map<String, Item> items() { return Collections.unmodifiableMap(ITEMS); }
}
