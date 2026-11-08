package com.heliosphere.core.story;

import com.heliosphere.core.Heliosphere;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * World-wide story state, persisted in command storage: the universe clock (game days plus every day spent
 * coasting between worlds) and the Centaurian fleet countdown that starts when the Proxima signal is decoded.
 */
public final class Story {
    public static final double FLEET_YEARS = 160;
    private static final Identifier KEY = Heliosphere.id("story");

    public record Payload(boolean active, double remainingDays, int asteroid, double asteroidDays) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Heliosphere.id("story"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Payload::active, ByteBufCodecs.DOUBLE, Payload::remainingDays,
                ByteBufCodecs.VAR_INT, Payload::asteroid, ByteBufCodecs.DOUBLE, Payload::asteroidDays, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client copy, updated every two seconds. */
    public static volatile Payload client = new Payload(false, 0, 0, 0);

    /** Days from world start until asteroid 2097-ΩX hits, unless intercepted. */
    public static final double ASTEROID_DAYS = 60;
    /** Called when the asteroid arrives un-deflected (set by the planets module: crater + meteorite). */
    public static java.util.function.Consumer<MinecraftServer> impact = s -> {};

    private Story() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
    }

    private static CompoundTag tag(MinecraftServer s) { return s.getCommandStorage().get(KEY); }
    private static void save(MinecraftServer s, CompoundTag t) { s.getCommandStorage().set(KEY, t); }

    /** Days elapsed in-universe: world days plus all interplanetary/interstellar coast time. */
    public static double universeDay(MinecraftServer s) {
        return s.overworld().getGameTime() / 24000.0 + tag(s).getDoubleOr("travel_days", 0);
    }

    public static void addTravelDays(MinecraftServer s, double days) {
        if (days <= 0) return;
        CompoundTag t = tag(s);
        t.putDouble("travel_days", t.getDoubleOr("travel_days", 0) + days);
        save(s, t);
    }

    public static boolean decoded(MinecraftServer s) { return tag(s).getBooleanOr("decoded", false); }

    /** Days left before the Centaurian fleet reaches Earth (negative once it has arrived). */
    public static double remainingDays(MinecraftServer s) {
        CompoundTag t = tag(s);
        if (!t.getBooleanOr("decoded", false)) return Double.NaN;
        return t.getDoubleOr("arrival", 0) - universeDay(s);
    }

    /** First decoding of the Proxima signal: starts the countdown. Returns false if it was already running. */
    public static boolean decode(MinecraftServer s) {
        CompoundTag t = tag(s);
        if (t.getBooleanOr("decoded", false)) return false;
        t.putBoolean("decoded", true);
        t.putDouble("arrival", universeDay(s) + FLEET_YEARS * 365.25);
        save(s, t);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("story.heliosphere.signal_1"));
            p.sendSystemMessage(Component.translatable("story.heliosphere.signal_2"));
            p.sendSystemMessage(Component.translatable("story.heliosphere.signal_3", (int) FLEET_YEARS));
        }
        sync(s);
        return true;
    }

    /** 0 = not started, 1 = incoming, 2 = deflected, 3 = impacted */
    public static int asteroidState(MinecraftServer s) { return tag(s).getIntOr("asteroid", 0); }

    public static double asteroidDays(MinecraftServer s) { return tag(s).getDoubleOr("asteroid_due", 0) - universeDay(s); }

    public static void setAsteroid(MinecraftServer s, int state) {
        CompoundTag t = tag(s);
        t.putInt("asteroid", state);
        save(s, t);
        sync(s);
    }

    private static void tickAsteroid(MinecraftServer s) {
        CompoundTag t = tag(s);
        int st = t.getIntOr("asteroid", 0);
        if (st == 0) {
            t.putInt("asteroid", 1);
            t.putDouble("asteroid_due", universeDay(s) + ASTEROID_DAYS);
            save(s, t);
        } else if (st == 1 && asteroidDays(s) <= 0) {
            t.putInt("asteroid", 3);
            save(s, t);
            impact.accept(s);
        }
    }

    public static void sync(MinecraftServer s) {
        boolean on = decoded(s);
        int ast = asteroidState(s);
        Payload p = new Payload(on, on ? remainingDays(s) : 0, ast, ast == 1 ? asteroidDays(s) : 0);
        for (ServerPlayer pl : s.getPlayerList().getPlayers()) ServerPlayNetworking.send(pl, p);
    }

    public static void tick(MinecraftServer s) {
        if (s.getTickCount() % 40 == 0) { tickAsteroid(s); sync(s); }
    }

    /** "87 年 142 天" */
    public static String format(double days) {
        if (days <= 0) return "已抵达";
        int y = (int) (days / 365.25);
        int d = (int) (days - y * 365.25);
        return y > 0 ? y + " 年 " + d + " 天" : d + " 天";
    }
}
