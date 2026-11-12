package com.heliosphere.disasters.tide;

import com.heliosphere.core.env.Planets;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * An impact on Earth seen from elsewhere: everyone off Earth (and on Earth beyond sight of it) is told, and a
 * player on the Moon sees the flash and the spreading cloud on the Earth in their sky.
 */
public record RemoteImpact(float scale, int seed) implements CustomPacketPayload {
    public static final Type<RemoteImpact> ID = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "remote_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteImpact> CODEC = StreamCodec.of(
            (b, p) -> { b.writeFloat(p.scale); b.writeInt(p.seed); }, b -> new RemoteImpact(b.readFloat(), b.readInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    static void init() { PayloadTypeRegistry.playS2C().register(ID, CODEC); }

    static void broadcast(ServerLevel earth, int x, int z, float scale) {
        for (ServerPlayer p : earth.getServer().getPlayerList().getPlayers()) {
            if (p.level().dimension() == Level.OVERWORLD) {
                double d = MagmaTide.distance(earth, x, z, p.getX(), p.getZ());
                if (d > ImpactFx.VISIBLE)
                    p.displayClientMessage(Component.translatable("message.heliosphere.impact_far", String.format("%.0f", d / 1000)), false);
                continue;
            }
            var body = Planets.bodyOf(p.level());
            String where = body == null ? "" : body.id;
            p.displayClientMessage(Component.translatable("moon".equals(where) ? "message.heliosphere.impact_seen_moon" : "message.heliosphere.impact_seen_space"), false);
            if (ServerPlayNetworking.canSend(p, ID)) ServerPlayNetworking.send(p, new RemoteImpact(scale, x * 31 + z * 17));
        }
    }
}
