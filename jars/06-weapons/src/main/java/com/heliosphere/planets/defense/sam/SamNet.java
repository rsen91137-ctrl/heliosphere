package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Messages of the operator's station: the control panel, the radar picture, and the operator's orders. */
public final class SamNet {
    private SamNet() {}

    public static final int NEXT = 1, FIRE = 2, AUTO = 10, MANUAL = 11, BOARD = 12, IFF_ON = 13, IFF_OFF = 14;

    /** Client to server: an order for the vehicle at {@code pos} (or the one being operated). */
    public record Action(int action, BlockPos pos) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "sam_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b, a) -> { b.writeVarInt(a.action); b.writeBlockPos(a.pos); },
                b -> new Action(b.readVarInt(), b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: open the control panel of a vehicle. */
    public record Panel(BlockPos pos, int kind, boolean manual, int ammo, int magazine, long power, long capacity, int crate, boolean iff) implements CustomPacketPayload {
        public static final Type<Panel> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "sam_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> CODEC = StreamCodec.of((b, p) -> {
            b.writeBlockPos(p.pos); b.writeVarInt(p.kind); b.writeBoolean(p.manual); b.writeVarInt(p.ammo); b.writeVarInt(p.magazine);
            b.writeVarLong(p.power); b.writeVarLong(p.capacity); b.writeVarInt(p.crate); b.writeBoolean(p.iff);
        }, b -> new Panel(b.readBlockPos(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarLong(), b.readVarInt(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client, several times a second while operating: what the vehicle sees and its state. */
    public record Picture(boolean on, int kind, boolean manual, boolean driving, int ammo, int magazine, int selected, List<double[]> targets, boolean iff, int range) implements CustomPacketPayload {
        public static final Type<Picture> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "sam_picture"));
        public static final Picture OFF = new Picture(false, 0, false, false, 0, 0, -1, List.of(), true, 0);
        public static final StreamCodec<RegistryFriendlyByteBuf, Picture> CODEC = StreamCodec.of((b, p) -> {
            b.writeBoolean(p.on); b.writeVarInt(p.kind); b.writeBoolean(p.manual); b.writeBoolean(p.driving); b.writeVarInt(p.ammo); b.writeVarInt(p.magazine);
            b.writeVarInt(p.selected); b.writeVarInt(p.targets.size());
            for (double[] t : p.targets) { b.writeDouble(t[0]); b.writeDouble(t[1]); b.writeDouble(t[2]); b.writeFloat((float) t[3]); b.writeBoolean(t[4] > 0); }
            b.writeBoolean(p.iff); b.writeVarInt(p.range);
        }, b -> {
            boolean on = b.readBoolean(); int kind = b.readVarInt(); boolean manual = b.readBoolean(), driving = b.readBoolean();
            int ammo = b.readVarInt(), mag = b.readVarInt(), sel = b.readVarInt(), n = b.readVarInt();
            List<double[]> t = new ArrayList<>();
            for (int i = 0; i < n; i++) t.add(new double[]{b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readBoolean() ? 1 : 0});
            return new Picture(on, kind, manual, driving, ammo, mag, sel, t, b.readBoolean(), b.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
