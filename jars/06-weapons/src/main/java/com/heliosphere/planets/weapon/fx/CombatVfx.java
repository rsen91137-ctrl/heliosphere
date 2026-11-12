package com.heliosphere.planets.weapon.fx;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * One combat effect, server to client. {@code kind} selects the effect, the other fields mean what that
 * effect needs (see the constants). The wire format and the first four kinds are those of the released
 * jars, which other modules (turrets, hostile machines) already send.
 */
public record CombatVfx(int kind, int color, Vec3 start, Vec3 end, float strength) implements CustomPacketPayload {
    /** Bullet tracer from start to end. */
    public static final int TRACER = 0;
    /** Energy beam from start to end; strength scales its width. */
    public static final int BEAM = 1;
    /** Ground pulse at start of radius strength. A warm colour means the shell of an explosive warhead. */
    public static final int RING = 2;
    /** Nuclear detonation at start. {@code color} carries the age in ticks for late viewers. */
    public static final int NUKE = 3;
    /** High-explosive burst at start, of explosion power strength. */
    public static final int EXPLOSION = 4;
    /** Plasma burst at start, tinted color. */
    public static final int PLASMA = 5;
    /** Antimatter annihilation at start. */
    public static final int ANTIMATTER = 6;
    /** Singularity at start, of radius strength. */
    public static final int SINGULARITY = 7;
    /** Fallout hanging in the air around start: strength is the intensity 0..1 where the viewer stands. */
    public static final int FALLOUT = 8;
    /** The blast wave of a detonation at start has just reached the viewer; strength 0..1. */
    public static final int SHOCK = 9;
    /** Flamethrower jet from start to end. */
    public static final int FLAME = 10;
    /** Ground target marker at start of radius strength, lasting color ticks. */
    public static final int WARNING = 11;
    /** Mortar round from start to end, flight time color ticks. */
    public static final int MORTAR = 12;
    /** Projected shield around start of radius strength, tinted color. */
    public static final int SHIELD = 13;
    /** Blade strike at start; end - start is the attacker's facing. */
    public static final int SLASH = 14;

    public static final Type<CombatVfx> ID = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "combat_vfx_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatVfx> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.kind); buf.writeInt(p.color);
                buf.writeDouble(p.start.x); buf.writeDouble(p.start.y); buf.writeDouble(p.start.z);
                buf.writeDouble(p.end.x); buf.writeDouble(p.end.y); buf.writeDouble(p.end.z);
                buf.writeFloat(p.strength);
            },
            buf -> new CombatVfx(buf.readInt(), buf.readInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat()));

    public static void init() { PayloadTypeRegistry.playS2C().register(ID, CODEC); }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    /** How far away an effect of this kind is still worth sending, in blocks. */
    private static double reach(int kind) {
        return switch (kind) { case NUKE -> 1024; case EXPLOSION, PLASMA, ANTIMATTER, SINGULARITY -> 256; default -> 192; };
    }

    /** Told of every effect sent, before it goes out (other modules use it to react to impacts). */
    public interface Listener { void sent(ServerLevel level, int kind, int color, Vec3 start, Vec3 end, float strength); }
    public static final java.util.List<Listener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Sends the effect to every player of the level close enough to see it. */
    public static void send(ServerLevel level, int kind, int color, Vec3 start, Vec3 end, float strength) {
        for (Listener l : LISTENERS) l.sent(level, kind, color, start, end, strength);
        CombatVfx p = new CombatVfx(kind, color, start, end, strength);
        double r = reach(kind), r2 = r * r;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(start.x, start.y, start.z) < r2 || player.distanceToSqr(end.x, end.y, end.z) < r2) to(player, p);
        }
    }

    public static void to(ServerPlayer player, CombatVfx p) {
        if (ServerPlayNetworking.canSend(player, ID)) ServerPlayNetworking.send(player, p);
    }
}
