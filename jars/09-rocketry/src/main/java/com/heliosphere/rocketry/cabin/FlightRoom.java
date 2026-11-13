package com.heliosphere.rocketry.cabin;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.env.Planets;
import com.heliosphere.rocketry.entity.RocketEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Launch and landing in a real room. When you board, you step into the crew cabin built of real blocks in its own
 * dimension, the flight deck: you walk, jump, look around, open the lockers and pull the launch lever exactly as
 * anywhere else in the world, with your own keys, view and sounds. The rocket itself flies on in the real world, so
 * everyone on the ground watches it climb away and come down. Outside the windows is the flight as it looked in the
 * old launch sequence: the real ground around the pad falling away beneath you (a snapshot of the terrain, see
 * {@link DeckGround}), then the planet's landscape, clouds, haze and the curve of the horizon, the sky darkening into
 * space; your altitude, speed and the rocket's coordinates are on the flight display.
 *
 * <p>Between worlds the released cruise cabin takes over (also a real room). On arrival you are back in this room
 * for the descent, and at touchdown you step out next to the rocket.
 */
public final class FlightRoom {
    private FlightRoom() {}

    /** The flight deck: an empty world with an open sky, one room per player. */
    public static final ResourceKey<Level> DECK = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("heliosphere", "flight_deck"));
    private static final int BASE_X = 0, BASE_Z = 0, Y = 120, SLOT = 64;
    /** Where the rocket's axis passes through the room (room frame): under the glass panel in the deck. */
    static final BlockPos AXIS = new BlockPos(0, 0, -4);
    private static final TicketType TICKET = new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    static final class Crew {
        final UUID player;
        UUID rocket;
        BlockPos origin;
        boolean inRoom, flown, handedOver, launch, groundDone;
        int groundAt = -1000;
        int missing, phase = -1;
        ResourceKey<Level> lastDim;
        Vec3 lastPos = Vec3.ZERO;
        double ground = Double.NaN, speed;
        Crew(UUID player, UUID rocket) { this.player = player; this.rocket = rocket; }
    }

    private static final Map<UUID, Crew> CREW = new HashMap<>();

    public static void init() {
        PayloadTypeRegistry.playS2C().register(FlightTelemetry.TYPE, FlightTelemetry.CODEC);
        PayloadTypeRegistry.playS2C().register(DeckGround.TYPE, DeckGround.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(FlightRoom::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> CREW.clear());
        // pressurised and heated: the environment treats the room like the capsule (as the cruise cabin does)
        var prev = com.heliosphere.core.env.Environment.sealedCabin;
        com.heliosphere.core.env.Environment.sealedCabin = e -> (prev != null && prev.test(e)) || (e != null && inAnyRoom(e.level(), e.blockPosition()));
        // the room is part of the ship: it cannot be broken
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !inAnyRoom(level, pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp) || level.isClientSide()) return InteractionResult.PASS;
            Crew c = CREW.get(sp.getUUID());
            if (c == null || !c.inRoom || !hit.getBlockPos().equals(c.origin.offset(CabinLayout.LEVER))) return InteractionResult.PASS;
            c.launch = true;                                                   // taken up on the next tick
            return InteractionResult.PASS;                                   // the lever still flips
        });
    }

    /** The rocket this player is flying in while standing in the room, if any. */
    public static RocketEntity rocketOf(ServerPlayer p) {
        Crew c = CREW.get(p.getUUID());
        return c == null ? null : find(p.level().getServer(), c.rocket);
    }

    /** The launch lever of this player's room (for tests and tools), or null. */
    public static BlockPos lever(ServerPlayer p) {
        Crew c = CREW.get(p.getUUID());
        return c == null || c.origin == null ? null : c.origin.offset(CabinLayout.LEVER);
    }

    /** A one-line state dump for tests. */
    public static String debug(ServerPlayer p) {
        Crew c = CREW.get(p.getUUID());
        if (c == null) return "no crew entry";
        RocketEntity r = find(p.level().getServer(), c.rocket);
        return "inRoom " + c.inRoom + " missing " + c.missing + " phase " + c.phase + " origin " + c.origin + " rocket " + (r == null ? "null" : r.phase() + " " + r.getTags())
                + " lever " + (c.origin == null ? "?" : p.level().getBlockState(c.origin.offset(CabinLayout.LEVER)));
    }

    public static boolean inRoom(ServerPlayer p) {
        Crew c = CREW.get(p.getUUID());
        return c != null && c.inRoom;
    }

    // ---- entering and leaving ---------------------------------------------------------------------------------

    /** Takes a boarded crew member from the rocket into the room. */
    public static void enter(RocketEntity rocket, ServerPlayer p) {
        if (!ENABLED) return;
        Crew c = CREW.computeIfAbsent(p.getUUID(), u -> new Crew(u, rocket.getUUID()));
        c.lastDim = rocket.level().dimension();
        c.lastPos = rocket.position();
        ((ServerLevel) rocket.level()).getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(rocket.blockPosition()), 2);
        moveIn(p.level().getServer(), c, p, rocket);
    }

    private static void moveIn(MinecraftServer server, Crew c, ServerPlayer p, RocketEntity rocket) {
        ServerLevel deck = server.getLevel(DECK);
        if (deck == null) return;
        if (c.origin == null) {
            int slot = Math.floorMod(p.getUUID().hashCode(), 4096);
            c.origin = new BlockPos(BASE_X + (slot % 64) * SLOT, Y, BASE_Z + (slot / 64) * SLOT);
        }
        build(deck, c.origin);
        c.ground = groundUnder(rocket);
        sendGround(c, p, rocket);                                       // the view out of the windows first
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(deck, c.origin.getX() + 0.5, c.origin.getY(), c.origin.getZ() - 2.5, Set.of(), 0, 5, true);
        c.inRoom = true;
    }

    /** How high the crew deck is above the rocket's base. */
    static float seat(RocketEntity rocket) {
        double[] s = rocket.structure().seat();
        return s != null && s.length > 1 ? (float) s[1] : Math.max(2f, rocket.getBbHeight() - 3f);
    }

    /** The ground around the rocket for the windows: taken again while chunks under a descent are still coming in. */
    private static void sendGround(Crew c, ServerPlayer p, RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel l) || c.origin == null) return;
        DeckGround g = DeckGround.take(l, rocket.getX(), rocket.getZ(), c.origin.offset(AXIS), seat(rocket));
        ServerPlayNetworking.send(p, g);
        c.groundDone = g.coverage() > 0.97;
        c.groundAt = p.level().getServer().getTickCount();
    }

    /** Steps out beside the rocket where it stands now. */
    private static void stepOut(MinecraftServer server, Crew c, ServerPlayer p, RocketEntity rocket) {
        c.inRoom = false;
        ServerLevel to = rocket != null ? (ServerLevel) rocket.level() : server.getLevel(c.lastDim);
        Vec3 at = rocket != null ? rocket.position() : c.lastPos;
        if (to == null) to = server.overworld();
        double x = at.x + 3.5, z = at.z + 0.5;
        to.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        int y = to.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        double standY = rocket != null && Math.abs(at.y - y) < 4 ? Math.max(y, at.y) : y;     // never into the sky
        p.teleportTo(to, x, standY, z, Set.of(), p.getYRot(), 0, true);
        p.fallDistance = 0;
        ServerPlayNetworking.send(p, FlightTelemetry.OFF);
    }

    /** Puts the crew member back on the rocket (for the released cruise cabin or the touchdown). */
    private static void seat(Crew c, ServerPlayer p, RocketEntity rocket) {
        c.inRoom = false;
        ServerPlayNetworking.send(p, FlightTelemetry.OFF);
        p.teleportTo((ServerLevel) rocket.level(), rocket.getX(), rocket.getY() + 1, rocket.getZ(), Set.of(), p.getYRot(), 0, true);
        p.startRiding(rocket, true, true);
    }

    /** Leaves the flight before launch (the cabin's "leave"): back on the pad. */
    static void abort(ServerPlayer p) {
        Crew c = CREW.remove(p.getUUID());
        if (c == null) return;
        RocketEntity r = find(p.level().getServer(), c.rocket);
        if (c.inRoom) stepOut(p.level().getServer(), c, p, r);
    }

    /**
     * Called just before the rocket leaves its world for space (the released code replaces it with a new rocket
     * there and takes only its passengers along): the crew goes back aboard first.
     */
    public static void boardBeforeLeaving(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level)) return;
        for (Crew c : CREW.values()) {
            if (!c.inRoom || !c.rocket.equals(rocket.getUUID())) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(c.player);
            if (p != null) seat(c, p, rocket);
        }
    }

    // ---- every tick -------------------------------------------------------------------------------------------

    /** Launch and landing are flown from the room on the flight deck (the rocket flies on in the real world). */
    static final boolean ENABLED = true;

    private static final java.util.Set<String> CRUISING = new java.util.HashSet<>();
    /** Crew still sitting in a rocket that has just landed: not boarded again until they get out. */
    private static final java.util.Set<UUID> LANDED = new java.util.HashSet<>();

    /** Crew riding into the transfer go straight into the released cruise cabin (a real room), not an orbit shot. */
    private static void cruise(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p.getVehicle() instanceof RocketEntity r && r.phase() == RocketEntity.TRANSIT && CRUISING.add(r.getUUID() + "/" + p.getUUID()))
                CruiseCabinAccess.open(r, p);
        if (CRUISING.size() > 256) CRUISING.clear();
    }

    private static void tick(MinecraftServer server) {
        cruise(server);
        // whoever gets into a crewed rocket goes to the room: on the pad (boarding: the countdown waits for the lever)
        // or already under way (the rocket menu)
        if (ENABLED && server.getTickCount() % 5 == 0)
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!(p.getVehicle() instanceof RocketEntity r)) { LANDED.remove(p.getUUID()); continue; }
                if (CREW.containsKey(p.getUUID()) || r.structure().parts().stream().noneMatch(x -> "crew_capsule".equals(x.spec()))) continue;
                int ph = r.phase();
                if (ph == RocketEntity.COUNTDOWN || ph == RocketEntity.ASCENT || ph == RocketEntity.DESCENT || ph == RocketEntity.SUBORBITAL) enter(r, p);
                else if (ph == RocketEntity.LANDED && !LANDED.contains(p.getUUID()) && !r.getTags().contains(Boarding.HOLD)) {
                    if (!Boarding.board(r, p)) LANDED.add(p.getUUID());     // checks failed: not again until they get out
                }
            }
        if (CREW.isEmpty()) return;
        var it = CREW.values().iterator();
        while (it.hasNext()) {
            Crew c = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(c.player);
            if (p != null && p.getVehicle() instanceof RocketEntity v && !v.getUUID().equals(c.rocket)) c.rocket = v.getUUID();   // the rocket was replaced (into space, on arrival)
            RocketEntity r = find(server, c.rocket);
            if (r == null && c.lastDim != null && server.getTickCount() % 10 == 0) {     // reload where it was last seen
                ServerLevel l = server.getLevel(c.lastDim);
                if (l != null) l.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(net.minecraft.core.BlockPos.containing(c.lastPos)), 2);
            }
            if (r == null) {
                if (++c.missing > 200) {                                // lost (destroyed, or unloaded for good)
                    if (p != null && c.inRoom) stepOut(server, c, p, null);
                    it.remove();
                }
                continue;
            }
            c.missing = 0;
            if (c.lastDim == r.level().dimension()) c.speed = c.speed * 0.8 + r.position().distanceTo(c.lastPos) * 20 * 0.2;   // m/s
            c.lastDim = r.level().dimension();
            c.lastPos = r.position();
            if (server.getTickCount() % 10 == 0)                       // the rocket flies on without a passenger: keep it ticking
                ((ServerLevel) r.level()).getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(r.blockPosition()), 2);
            if (p == null) continue;
            int phase = r.phase();
            if (phase != c.phase) { c.phase = phase; if (phase == RocketEntity.DESCENT) { c.ground = Double.NaN; c.groundDone = false; } }
            if (phase >= RocketEntity.ASCENT && phase != RocketEntity.DEBRIS) c.flown = true;

            if (phase == RocketEntity.TRANSIT) {
                if (!c.handedOver) {                                      // between worlds: the released cruise cabin, at once
                    if (p.getVehicle() != r) seat(c, p, r);
                    CruiseCabinAccess.open(r, p);
                    c.handedOver = true;
                }
                continue;
            }
            if (phase == RocketEntity.LANDED || phase == RocketEntity.DEBRIS) {
                if (c.inRoom) stepOut(server, c, p, r);
                if (c.flown || phase == RocketEntity.DEBRIS) { it.remove(); LANDED.add(c.player); }
                continue;
            }
            // countdown, ascent, sub-orbital hop, descent: in the room
            if (!c.inRoom) {
                if (p.getVehicle() == r || (c.handedOver && phase == RocketEntity.DESCENT)) moveIn(server, c, p, r);
                else continue;
            }
            if (Double.isNaN(c.ground)) c.ground = groundUnder(r);
            // coming down: load the ground under the landing site and keep the windows' view of it up to date
            if ((phase == RocketEntity.DESCENT || phase == RocketEntity.SUBORBITAL) && !c.groundDone && server.getTickCount() - c.groundAt >= 40) {
                ((ServerLevel) r.level()).getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(r.blockPosition()), 9);
                sendGround(c, p, r);
            }
            // a moment before touchdown the crew is back aboard, so the landing goes as it always has
            if ((phase == RocketEntity.DESCENT || phase == RocketEntity.SUBORBITAL && r.getDeltaMovement().y < 0) && c.flown
                    && !Double.isNaN(c.ground) && r.getY() - c.ground < 3.0) {
                seat(c, p, r);
                continue;
            }
            keepInside(c, p);
            // the launch lever on the console (however it was pulled) starts the countdown
            if (p.level() instanceof ServerLevel room) {
                var st = room.getBlockState(c.origin.offset(CabinLayout.LEVER));
                if (st.hasProperty(net.minecraft.world.level.block.LeverBlock.POWERED) && st.getValue(net.minecraft.world.level.block.LeverBlock.POWERED)) c.launch = true;
            }
            if (c.launch && r.getTags().contains(Boarding.HOLD)) { Boarding.go(r, p); c.launch = false; }
            telemetry(c, p, r);
        }
    }

    private static void telemetry(Crew c, ServerPlayer p, RocketEntity r) {
        CelestialBody body = Planets.bodyOf(r.level());
        double alt = Double.isNaN(c.ground) ? r.getY() - 64 : r.getY() - c.ground;
        ServerPlayNetworking.send(p, new FlightTelemetry(true, body == null ? "earth" : body.id, Math.max(0, alt), c.speed,
                r.phase(), r.getX(), r.getY(), r.getZ(), r.getTags().contains(Boarding.HOLD)));
    }

    private static void keepInside(Crew c, ServerPlayer p) {
        Vec3 at = p.position().subtract(Vec3.atLowerCornerOf(c.origin));
        if (p.level().dimension() != DECK || at.x < -5 || at.x > 5 || at.z < -11 || at.z > 3 || at.y < -3 || at.y > 7) {
            ServerLevel deck = p.level().getServer().getLevel(DECK);
            if (deck != null) p.teleportTo(deck, c.origin.getX() + 0.5, c.origin.getY(), c.origin.getZ() - 2.5, Set.of(), p.getYRot(), p.getXRot(), true);
            p.fallDistance = 0;
        }
    }

    private static double groundUnder(RocketEntity r) {
        Level l = r.level();
        int x = r.getBlockX(), z = r.getBlockZ();
        if (l.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return Double.NaN;
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    static RocketEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof RocketEntity r && r.isAlive()) return r;
        }
        return null;
    }

    // ---- the room ---------------------------------------------------------------------------------------------

    private static void build(ServerLevel level, BlockPos origin) {
        for (int cx = (origin.getX() - 5) >> 4; cx <= (origin.getX() + 4) >> 4; cx++)
            for (int cz = (origin.getZ() - 10) >> 4; cz <= (origin.getZ() + 2) >> 4; cz++) level.getChunk(cx, cz);
        // air inside first (a previous flight may have left things lying about), then the shell and furniture
        for (int x = -3; x <= 2; x++) for (int z = -8; z <= 0; z++) for (int y = 0; y < 4; y++) {
            BlockPos at = origin.offset(x, y, z);
            if (!(level.getBlockEntity(at) != null)) level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (CabinLayout.Part part : CabinLayout.parts()) {
            BlockPos at = origin.offset(part.at());
            if (!level.getBlockState(at).is(part.state().getBlock())) level.setBlock(at, part.state(), Block.UPDATE_CLIENTS);
        }
        // a solid wall behind the door: the rest of the ship
        for (int x = -4; x <= 3; x++) for (int y = -1; y <= 4; y++) {
            BlockPos at = origin.offset(x, y, -10);
            if (level.getBlockState(at).isAir()) level.setBlock(at, net.minecraft.world.level.block.Blocks.WHITE_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static boolean inAnyRoom(Level level, BlockPos pos) {
        if (level.dimension() != DECK) return false;
        int dx = pos.getX() - BASE_X, dz = pos.getZ() - BASE_Z;
        return dx >= -SLOT && dz >= -SLOT && dx < 64 * SLOT + SLOT && dz < 64 * SLOT + SLOT && Math.abs(pos.getY() - Y) < 12;
    }

    static ChatFormatting style() { return ChatFormatting.AQUA; }

    static Component hint() { return Component.translatable("message.heliosphere.flight_room_hint").withStyle(ChatFormatting.AQUA); }
}
