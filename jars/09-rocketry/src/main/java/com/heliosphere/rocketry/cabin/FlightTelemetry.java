package com.heliosphere.rocketry.cabin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** What the flight room's windows and display show: where the rocket is, sent to its crew several times a second. */
public record FlightTelemetry(boolean on, String body, double altitude, double speed, int phase, double x, double y, double z, boolean hold)
        implements CustomPacketPayload {
    public static final Type<FlightTelemetry> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "flight_room"));
    public static final FlightTelemetry OFF = new FlightTelemetry(false, "", 0, 0, 0, 0, 0, 0, false);
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightTelemetry> CODEC = StreamCodec.of((b, t) -> {
        b.writeBoolean(t.on); b.writeUtf(t.body); b.writeDouble(t.altitude); b.writeDouble(t.speed); b.writeVarInt(t.phase);
        b.writeDouble(t.x); b.writeDouble(t.y); b.writeDouble(t.z); b.writeBoolean(t.hold);
    }, b -> new FlightTelemetry(b.readBoolean(), b.readUtf(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
