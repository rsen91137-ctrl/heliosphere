package com.heliosphere.foods.cuisine;

import com.heliosphere.core.Heliosphere;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The food buffs of the expansion. Each is a plain beneficial effect; what it does is done where the danger is
 * (mixins): warmth keeps out the impact-winter frost and powder-snow freezing, acid ward stops acid rain hurting,
 * nourished stops hunger dropping, and the radiation shield cuts the dose to a quarter (weapons module, by id).
 */
public final class CuisineEffects {
    private CuisineEffects() {}

    public static final String WARMTH_ID = "warmth", RAD_SHIELD_ID = "rad_shield", ACID_SHIELD_ID = "acid_shield", NOURISHED_ID = "nourished";

    /** Every effect of ours by id (without namespace), in table order. */
    public static final Map<String, Holder<MobEffect>> ALL = new LinkedHashMap<>();
    public static Holder<MobEffect> WARMTH, RAD_SHIELD, ACID_SHIELD, NOURISHED;

    /** A food buff: no behaviour of its own (see the class comment). */
    public static final class CuisineEffect extends MobEffect {
        public CuisineEffect(int color) { super(MobEffectCategory.BENEFICIAL, color); }
    }

    static void register(Iterable<CuisineTable.Effect> effects) {
        for (CuisineTable.Effect e : effects) {
            if (ALL.containsKey(e.id())) continue;
            MobEffect effect = new CuisineEffect(e.color() & 0xFFFFFF);
            Holder<MobEffect> holder = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                    Identifier.fromNamespaceAndPath(Heliosphere.MOD_ID, e.id()), effect);
            ALL.put(e.id(), holder);
            switch (e.id()) {
                case WARMTH_ID -> WARMTH = holder;
                case RAD_SHIELD_ID -> RAD_SHIELD = holder;
                case ACID_SHIELD_ID -> ACID_SHIELD = holder;
                case NOURISHED_ID -> NOURISHED = holder;
                default -> { }
            }
        }
    }

    /** Whether {@code entity} has {@code effect} (false while the effect is not registered). */
    public static boolean has(LivingEntity entity, Holder<MobEffect> effect) {
        return effect != null && entity != null && entity.hasEffect(effect);
    }

    /** Ours by id, read-only. */
    public static Map<String, Holder<MobEffect>> all() { return Collections.unmodifiableMap(ALL); }
}
