package com.heliosphere.industry.borer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Messages of the tunnel borer: the player's orders (control panel buttons, the auto key, the inventory key while
 * riding) and the control panel's picture of the machine. The server checks every order: distance or riding, the
 * owner for packing up.
 */
public final class BorerNet {
    private BorerNet() {}

    /** Orders ({@link Action#action}). */
    public static final int AUTO = 1, SLOPE = 2, DISTANCE = 3, JUNK = 4, LINING = 5, VEINS = 6, LIGHTS = 7,
            BIN = 8, PACK = 9, PANEL = 10, BOARD = 11;

    /** Client to server: an order for the borer with entity id {@code entity}. */
    public record Action(int action, int entity) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "borer_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b, a) -> { b.writeVarInt(a.action); b.writeVarInt(a.entity); },
                b -> new Action(b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Server to client: open (or refresh, when it is already open) the control panel of a borer. {@code flags} are
     * {@link TunnelBorer#AUTO} and the other flag bits, {@code slope} -1/0/1, {@code distance} an entry of
     * {@link TunnelBorer#DISTANCES} (-1 unlimited), {@code status} a TunnelBorer.S_* code, {@code fuel} the number of
     * fuel items in the bin, {@code owner} whether the viewer may pack the machine up.
     */
    public record Panel(int entity, int energy, int capacity, int flags, int slope, int distance, int binUsed, int binSize,
                        int status, int dug, int ores, int fuel, boolean owner) implements CustomPacketPayload {
        public static final Type<Panel> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "borer_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entity); b.writeVarInt(p.energy); b.writeVarInt(p.capacity); b.writeVarInt(p.flags); b.writeVarInt(p.slope);
            b.writeVarInt(p.distance); b.writeVarInt(p.binUsed); b.writeVarInt(p.binSize); b.writeVarInt(p.status); b.writeVarInt(p.dug);
            b.writeVarInt(p.ores); b.writeVarInt(p.fuel); b.writeBoolean(p.owner);
        }, b -> new Panel(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Registers both payload types (common code, both sides). */
    static void register() {
        PayloadTypeRegistry.playC2S().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.playS2C().register(Panel.TYPE, Panel.CODEC);
    }
}
