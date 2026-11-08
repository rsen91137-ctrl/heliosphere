package com.heliosphere.core.net;

import com.heliosphere.core.Heliosphere;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Sends each planet's baked surface map (equirectangular RGB) to clients so the sky can draw the
 * planet exactly as its terrain was generated.
 */
public final class HelioNetwork {
    public static final int MAP_W = 256, MAP_H = 128;

    public record PlanetMapPayload(String body, int width, int height, byte[] rgb) implements CustomPacketPayload {
        public static final Type<PlanetMapPayload> TYPE = new Type<>(Heliosphere.id("planet_map"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, PlanetMapPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PlanetMapPayload::body, ByteBufCodecs.VAR_INT, PlanetMapPayload::width,
                ByteBufCodecs.VAR_INT, PlanetMapPayload::height, ByteBufCodecs.BYTE_ARRAY, PlanetMapPayload::rgb, PlanetMapPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Body id -> function producing an RGB map (MAP_W x MAP_H, row-major, north at row 0). */
    private static final Map<String, Function<MinecraftServer, int[]>> BAKERS = new ConcurrentHashMap<>();
    private static final Map<String, PlanetMapPayload> CACHE = new ConcurrentHashMap<>();

    private HelioNetwork() {}

    public static void registerMapBaker(String body, Function<MinecraftServer, int[]> baker) { BAKERS.put(body, baker); }

    public static void init() {
        PayloadTypeRegistry.playS2C().registerLarge(PlanetMapPayload.TYPE, PlanetMapPayload.CODEC, 1 << 20);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CACHE.clear();
            Thread t = new Thread(() -> bakeAll(server), "Heliosphere map baker");
            t.setDaemon(true);
            t.start();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (PlanetMapPayload p : CACHE.values()) sender.sendPacket(p);
        });
    }

    private static void bakeAll(MinecraftServer server) {
        for (Map.Entry<String, Function<MinecraftServer, int[]>> e : BAKERS.entrySet()) {
            try {
                int[] rgb = e.getValue().apply(server);
                byte[] bytes = new byte[rgb.length * 3];
                for (int i = 0; i < rgb.length; i++) {
                    bytes[i * 3] = (byte) (rgb[i] >> 16);
                    bytes[i * 3 + 1] = (byte) (rgb[i] >> 8);
                    bytes[i * 3 + 2] = (byte) rgb[i];
                }
                PlanetMapPayload payload = new PlanetMapPayload(e.getKey(), MAP_W, MAP_H, bytes);
                CACHE.put(e.getKey(), payload);
                server.execute(() -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, payload);
                });
            } catch (Throwable t) {
                Heliosphere.LOG.warn("Could not bake surface map for {}", e.getKey(), t);
            }
        }
    }
}
