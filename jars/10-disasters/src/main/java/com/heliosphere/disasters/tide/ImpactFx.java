package com.heliosphere.disasters.tide;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Server to client: an asteroid struck at (x, y, z) {@code age} ticks ago. {@code scale} is 1 for the
 * primary impact and smaller for the showers that follow. Sent when the ledger records an impact and again
 * to anyone who joins while it is still playing out.
 */
public record ImpactFx(int x, int y, int z, int age, float scale, int seed) implements CustomPacketPayload {
    public static final Type<ImpactFx> ID = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "asteroid_impact_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImpactFx> CODEC = StreamCodec.of(
            (b, p) -> { b.writeInt(p.x); b.writeInt(p.y); b.writeInt(p.z); b.writeVarInt(p.age); b.writeFloat(p.scale); b.writeInt(p.seed); },
            b -> new ImpactFx(b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readFloat(), b.readInt()));

    /** How far away an impact can be seen, blocks (beyond, it is below the horizon). */
    static final double VISIBLE = 60000;
    /** Ticks an impact effect lasts (AsteroidImpact.DURATION). */
    static final int LASTS = 330 * 20;

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    static void init() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer p = handler.getPlayer();
            if (p.level().dimension() != Level.OVERWORLD) return;
            ServerLevel level = (ServerLevel) p.level();
            List<MagmaTide.Impact> all = MagmaTide.impacts(level);
            for (int i = 0; i < all.size(); i++) {
                MagmaTide.Impact m = all.get(i);
                long age = level.getGameTime() - m.started();
                if (age >= 0 && age < LASTS) send(p, level, m.x(), m.y(), m.z(), (int) age, i == 0 ? 1f : 0.45f);
            }
        }));
    }

    static void broadcast(ServerLevel level, int x, int y, int z, int age, float scale) {
        for (ServerPlayer p : level.players()) send(p, level, x, y, z, age, scale);
    }

    private static void send(ServerPlayer p, ServerLevel level, int x, int y, int z, int age, float scale) {
        if (MagmaTide.distance(level, x, z, p.getX(), p.getZ()) > VISIBLE) return;
        if (ServerPlayNetworking.canSend(p, ID)) ServerPlayNetworking.send(p, new ImpactFx(x, y, z, age, scale, x * 31 + z * 17));
    }
}
