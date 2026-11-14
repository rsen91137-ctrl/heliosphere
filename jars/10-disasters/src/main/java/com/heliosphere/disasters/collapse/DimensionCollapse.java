package com.heliosphere.disasters.collapse;

import com.heliosphere.core.Heliosphere;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Nether and the End hang off the overworld. When the doomsday asteroid strikes, the overworld's collapse
 * tears them away: anyone still in them dies with them, and from then on every portal leading there shatters
 * the moment it is lit or touched ({@link PortalCollapse}).
 *
 * <p>The released story keeps its state in the {@code heliosphere:story} command storage: {@code asteroid} is 1
 * while the asteroid is on its way, {@code asteroid_due} is the impact day (overworld game time / 24000), and
 * {@code impact_winter_started} appears at the strike. We only read those, and add one key of our own
 * ({@code dimension_collapse_at}) so the collapse happens once.
 *
 * <p>Players in the Nether or the End are warned three days, one day, ten, five, two and one minutes, thirty
 * seconds and ten seconds before the impact, and again whenever they arrive there during the countdown. At
 * the strike they get eight seconds of the dimension coming apart around them (they can still dive through a
 * portal home), then die. Creative and spectator players are thrown back to the overworld instead.
 */
public final class DimensionCollapse {
    private DimensionCollapse() {}

    private static final Identifier STORY = Heliosphere.id("story");
    private static final String STARTED = "impact_winter_started", COMPLETED = "impact_completed",
            DUE = "asteroid_due", PHASE = "asteroid", MARK = "dimension_collapse_at";
    private static final int DAY = 24000;
    /** How long the collapse takes for players caught in it, and for anyone found there later. */
    static final int SEQUENCE = 160, LATE = 100;
    /** Warning thresholds, in ticks before the impact (wide to narrow). */
    private static final long[] BANDS = {72000, 24000, 12000, 6000, 2400, 1200, 600, 200};
    private static final ResourceKey<DamageType> COLLAPSE = ResourceKey.create(Registries.DAMAGE_TYPE, Heliosphere.id("dimension_collapse"));

    private static final Map<UUID, Integer> WARNED = new HashMap<>();
    private static final Map<UUID, Long> DOOM = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DimensionCollapse::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { WARNED.clear(); DOOM.clear(); PortalCollapse.clear(); });
    }

    private static CompoundTag story(MinecraftServer server) { return server.getCommandStorage().get(STORY); }

    /** True once the overworld has been struck: the Nether and the End are gone. */
    public static boolean collapsed(MinecraftServer server) {
        CompoundTag t = story(server);
        return t.contains(STARTED) || t.getBooleanOr(COMPLETED, false);
    }

    /** The dimensions that go down with the overworld. */
    public static boolean lost(ResourceKey<Level> dimension) { return dimension == Level.NETHER || dimension == Level.END; }

    /** Ticks until the primary impact, or {@link Long#MIN_VALUE} when no asteroid is due. */
    private static long ticksLeft(CompoundTag t, long now) {
        if (t.contains(STARTED) || t.getIntOr(PHASE, 0) != 1 || !t.contains(DUE)) return Long.MIN_VALUE;
        return Math.round(t.getDoubleOr(DUE, 0) * DAY) - now;
    }

    /**
     * Days added to the released 100-day deadline, once per world: the asteroid now strikes 365 days after it is spotted.
     * Worlds that already had the earlier 150-day extension get the rest.
     */
    public static final double DEADLINE = 365, RELEASED = 100;
    private static final String EXTENDED_150 = "asteroid_due_150", EXTENDED = "asteroid_due_365";

    private static void extend(MinecraftServer server) {
        CompoundTag t = story(server);
        if (t.getIntOr(PHASE, 0) != 1 || !t.contains(DUE) || t.contains(STARTED) || t.getBooleanOr(EXTENDED, false)) return;
        double extra = DEADLINE - (t.getBooleanOr(EXTENDED_150, false) ? 150 : RELEASED);
        t.putDouble(DUE, t.getDoubleOr(DUE, 0) + extra);
        t.putBoolean(EXTENDED, true);
        t.putBoolean(EXTENDED_150, true);
        server.getCommandStorage().set(STORY, t);
        com.heliosphere.core.story.Story.sync(server);
    }

    private static void tick(MinecraftServer server) {
        PortalCollapse.flush(server);
        long now = server.overworld().getGameTime();
        if (now % 20 == 0) extend(server);
        if (now % 5 != 0) return;
        CompoundTag t = story(server);
        if (!(t.contains(STARTED) || t.getBooleanOr(COMPLETED, false))) {
            DOOM.clear();
            long left = ticksLeft(t, now);
            if (left == Long.MIN_VALUE || left > BANDS[0]) { WARNED.clear(); return; }
            countdown(server, Math.max(0, left), now);
            return;
        }
        if (!t.contains(MARK)) {
            t.putLong(MARK, now);
            server.getCommandStorage().set(STORY, t);
            begin(server, now);
        }
        doom(server, now, t.getLongOr(MARK, now));
    }

    // ---- before the impact -----------------------------------------------------------------------------------

    private static void countdown(MinecraftServer server, long left, long now) {
        int band = -1;
        for (int i = 0; i < BANDS.length; i++) if (left <= BANDS[i]) band = i;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!lost(p.level().dimension())) { WARNED.remove(p.getUUID()); continue; }
            Integer prev = WARNED.get(p.getUUID());
            if (prev == null || band > prev) {
                WARNED.put(p.getUUID(), band);
                p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_warning", time(left)));
                if (prev == null) p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_advice"));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.7f, 0.6f);
            }
            if (left <= 200 && now % 20 == 0) {
                p.displayClientMessage(Component.translatable("message.heliosphere.collapse_count", (left + 19) / 20), true);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1f, 0.5f + (200 - left) / 400f);
            }
        }
    }

    static Component time(long ticks) {
        if (ticks >= DAY) return Component.translatable("message.heliosphere.collapse_days", String.format("%.1f", ticks / (double) DAY), ticks / 1200);
        long s = (ticks + 19) / 20;
        if (s >= 60) return Component.translatable("message.heliosphere.collapse_minutes", s / 60, s % 60);
        return Component.translatable("message.heliosphere.collapse_seconds", s);
    }

    // ---- the collapse ----------------------------------------------------------------------------------------

    private static void begin(MinecraftServer server, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_broadcast"));
            if (lost(p.level().dimension()) && p.isAlive()) {
                DOOM.put(p.getUUID(), now + SEQUENCE);
                p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_now"));
            }
        }
    }

    private static void doom(MinecraftServer server, long now, long mark) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayer.RespawnConfig cfg = p.getRespawnConfig();
            if (cfg != null && lost(cfg.respawnData().dimension())) {
                p.setRespawnPosition(null, false);
                p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_anchor"));
            }
            if (!lost(p.level().dimension()) || !p.isAlive()) { DOOM.remove(p.getUUID()); continue; }
            Long end = DOOM.get(p.getUUID());
            if (end == null) {
                long rest = mark + SEQUENCE - now;
                end = now + (rest > 40 ? rest : LATE);
                DOOM.put(p.getUUID(), end);
                p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_now"));
            }
            if (now >= end) {
                DOOM.remove(p.getUUID());
                finish(server, p);
            } else {
                tear(p, end - now);
            }
        }
    }

    /** The dimension coming apart around a player: rifts, blasts, darkness. */
    private static void tear(ServerPlayer p, long left) {
        ServerLevel level = (ServerLevel) p.level();
        RandomSource r = level.getRandom();
        double x = p.getX(), y = p.getY() + 1, z = p.getZ();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 4, 2.5, 4, 0.6);
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 60, 3, 2, 3, 1.2);
        for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * 10;
            level.sendParticles(ParticleTypes.EXPLOSION, x + Math.cos(a) * d, y + r.nextDouble() * 6 - 2, z + Math.sin(a) * d, 1, 0, 0, 0, 0);
        }
        if (left % 20 < 5) {
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.4f + r.nextFloat() * 0.3f);
            level.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.6f, 0.5f);
            p.displayClientMessage(Component.translatable("message.heliosphere.collapse_tearing", (left + 19) / 20), true);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false));
        }
    }

    private static void finish(MinecraftServer server, ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 2, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 200, 1.5, 1.5, 1.5, 1.0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2f, 0.5f);
        if (p.isCreative() || p.isSpectator()) {
            home(server, p);
            p.sendSystemMessage(Component.translatable("message.heliosphere.collapse_spared"));
            return;
        }
        p.hurtServer(level, source(level), Float.MAX_VALUE);
        if (p.isAlive()) p.kill(level);
    }

    /** The overworld spawn, for players the collapse throws out instead of killing. */
    private static void home(MinecraftServer server, ServerPlayer p) {
        LevelData.RespawnData rd = server.getRespawnData();
        ServerLevel to = lost(rd.dimension()) || server.getLevel(rd.dimension()) == null ? server.overworld() : server.getLevel(rd.dimension());
        BlockPos at = rd.pos();
        int y = to.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
        p.teleportTo(to, at.getX() + 0.5, Math.max(y, to.getMinY() + 1), at.getZ() + 0.5, Set.of(), rd.yaw(), rd.pitch(), true);
    }

    private static DamageSource source(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(COLLAPSE)
                .map(h -> new DamageSource(h)).orElseGet(() -> level.damageSources().fellOutOfWorld());
    }
}
