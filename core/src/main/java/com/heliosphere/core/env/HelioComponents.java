package com.heliosphere.core.env;

import com.heliosphere.core.Heliosphere;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Item data components shared by all Heliosphere modules. */
public final class HelioComponents {
    /** Breathing oxygen stored in a suit or tank, in ticks of breathing. */
    public record Oxygen(int amount, int capacity) {
        public static final Codec<Oxygen> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("amount").forGetter(Oxygen::amount),
                Codec.INT.fieldOf("capacity").forGetter(Oxygen::capacity)).apply(i, Oxygen::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Oxygen> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Oxygen::amount, ByteBufCodecs.VAR_INT, Oxygen::capacity, Oxygen::new);
        public Oxygen withAmount(int a) { return new Oxygen(Math.max(0, Math.min(capacity, a)), capacity); }
        public float fraction() { return capacity <= 0 ? 0 : amount / (float) capacity; }
    }

    /** Environmental rating of a space-suit piece. */
    public record SuitRating(int tier, int minTempC, int maxTempC, boolean sealed) {
        public static final Codec<SuitRating> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("tier").forGetter(SuitRating::tier),
                Codec.INT.fieldOf("min_temp").forGetter(SuitRating::minTempC),
                Codec.INT.fieldOf("max_temp").forGetter(SuitRating::maxTempC),
                Codec.BOOL.fieldOf("sealed").forGetter(SuitRating::sealed)).apply(i, SuitRating::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, SuitRating> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SuitRating::tier, ByteBufCodecs.VAR_INT, SuitRating::minTempC,
                ByteBufCodecs.VAR_INT, SuitRating::maxTempC, ByteBufCodecs.BOOL, SuitRating::sealed, SuitRating::new);
    }

    /** Manufacturing quality of a precision part: tolerance in [0,1] and whether it passed a test firing. */
    public record Quality(float tolerance, boolean tested) {
        public static final Codec<Quality> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("tolerance").forGetter(Quality::tolerance),
                Codec.BOOL.fieldOf("tested").forGetter(Quality::tested)).apply(i, Quality::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Quality> STREAM = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Quality::tolerance, ByteBufCodecs.BOOL, Quality::tested, Quality::new);
    }

    public static DataComponentType<Oxygen> OXYGEN;
    public static DataComponentType<SuitRating> SUIT;
    public static DataComponentType<Quality> QUALITY;
    /** Stored propellant (tonnes x 1000) in a tank block item, so fuelled tanks keep their load when broken. */
    public static DataComponentType<Integer> FILL;

    public static void init() {
        OXYGEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Heliosphere.id("oxygen"),
                DataComponentType.<Oxygen>builder().persistent(Oxygen.CODEC).networkSynchronized(Oxygen.STREAM).build());
        SUIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Heliosphere.id("suit"),
                DataComponentType.<SuitRating>builder().persistent(SuitRating.CODEC).networkSynchronized(SuitRating.STREAM).build());
        QUALITY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Heliosphere.id("quality"),
                DataComponentType.<Quality>builder().persistent(Quality.CODEC).networkSynchronized(Quality.STREAM).build());
        FILL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Heliosphere.id("fill"),
                DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    }

    private HelioComponents() {}
}
