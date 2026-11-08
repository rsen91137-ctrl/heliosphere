package com.heliosphere.rocketry.net;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.rocketry.menu.RocketMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Network messages for the launch console and in-flight sky. */
public final class RocketNet {
    public static final int SELECT_DEST = 0, SELECT_SITE = 1, FUEL = 2, LAUNCH = 3, FORCE_LAUNCH = 4, DISASSEMBLE = 5;

    public record Action(int action, String arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Heliosphere.id("rocket_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::action, ByteBufCodecs.STRING_UTF8, Action::arg, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Everything the launch screen shows; computed on the server, which owns the block entities. */
    public record Report(List<String> lines, List<Integer> severity, List<String> stats, List<String> destinations, String destination,
                         List<String> sites, String site, String fuel, boolean landed, boolean canLaunch) implements CustomPacketPayload {
        public static final Type<Report> TYPE = new Type<>(Heliosphere.id("rocket_report"));
        private static final StreamCodec<ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
        public static final StreamCodec<ByteBuf, Report> CODEC = new StreamCodec<>() {
            @Override public Report decode(ByteBuf b) {
                return new Report(STRINGS.decode(b), ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(b), STRINGS.decode(b),
                        STRINGS.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), STRINGS.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                        ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.BOOL.decode(b));
            }
            @Override public void encode(ByteBuf b, Report r) {
                STRINGS.encode(b, r.lines); ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(b, r.severity); STRINGS.encode(b, r.stats);
                STRINGS.encode(b, r.destinations); ByteBufCodecs.STRING_UTF8.encode(b, r.destination); STRINGS.encode(b, r.sites);
                ByteBufCodecs.STRING_UTF8.encode(b, r.site); ByteBufCodecs.STRING_UTF8.encode(b, r.fuel); ByteBufCodecs.BOOL.encode(b, r.landed);
                ByteBufCodecs.BOOL.encode(b, r.canLaunch);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Starts (duration > 0) or ends a transfer: the client sky follows the trajectory. */
    public record Transit(String from, String to, long start, int duration) implements CustomPacketPayload {
        public static final Type<Transit> TYPE = new Type<>(Heliosphere.id("rocket_transit"));
        public static final StreamCodec<ByteBuf, Transit> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Transit::from, ByteBufCodecs.STRING_UTF8, Transit::to,
                ByteBufCodecs.VAR_LONG, Transit::start, ByteBufCodecs.VAR_INT, Transit::duration, Transit::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private RocketNet() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.playS2C().register(Report.TYPE, Report.CODEC);
        PayloadTypeRegistry.playS2C().register(Transit.TYPE, Transit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, ctx) -> ctx.server().execute(() -> {
            if (ctx.player().containerMenu instanceof RocketMenu m) m.handle(ctx.player(), payload.action(), payload.arg());
        }));
    }

    public static void sendTransit(ServerPlayer p, String from, String to, long start, int duration) {
        ServerPlayNetworking.send(p, new Transit(from, to, start, duration));
    }
}
