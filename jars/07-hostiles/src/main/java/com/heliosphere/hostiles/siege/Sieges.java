package com.heliosphere.hostiles.siege;

import com.google.gson.Gson;
import com.heliosphere.core.env.Planets;
import com.heliosphere.hostiles.arsenal.EnemyRoster;
import com.heliosphere.hostiles.arsenal.WorldTiers;
import com.heliosphere.hostiles.fortress.FortressLoot;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Machine sieges. The machines learn where each player lives: every ten seconds the place a player stands in is
 * scored, more when it is full of chests, furnaces and machines or holds the player's bed. The best scored place
 * becomes the player's shelter. Every one and a half to three days, while the player is at home, the machines
 * attack it: first a warning, then one of five kinds of attack whose strength grows with the days survived, the
 * world and the number of sieges already beaten.
 */
public final class Sieges implements ModInitializer {
    static final String TAG = "heliosphere.siege";
    private static final Gson GSON = new Gson();
    private static AttachmentType<String> memory;

    /** What the machines know about one player. */
    static final class Memory {
        List<Cell> cells = new ArrayList<>();
        long next = -1;
        int beaten;
        int launched;
    }

    static final class Cell {
        String dim;
        int cx, cz;
        double score, sx, sz;
        int n;

        BlockPos center(ServerLevel l) {
            int x = (int) (sx / Math.max(1, n)), z = (int) (sz / Math.max(1, n));
            return new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
    }

    enum Kind {
        RECON(1, 1), ASSAULT(2, 2), BOMBARD(4, 1), INFILTRATION(5, 2), ONSLAUGHT(7, 3);
        final int from, waves;
        Kind(int from, int waves) { this.from = from; this.waves = waves; }
        String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** A siege under way. */
    static final class Siege {
        UUID player;
        ServerLevel level;
        BlockPos home;
        Kind kind;
        int difficulty, wave, total;
        long nextWave, started;
        final List<UUID> mobs = new ArrayList<>();
        ServerBossEvent bar;
    }

    private static final Map<UUID, Memory> MEMORY = new HashMap<>();
    private static final List<Siege> ACTIVE = new ArrayList<>();

    @Override
    public void onInitialize() {
        memory = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("heliosphere", "siege_memory_v1"),
                b -> b.persistent(Codec.STRING).copyOnDeath());
        ServerTickEvents.END_SERVER_TICK.register(Sieges::tick);
        // leftovers of the retired Doomsday Cult in old worlds
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> { if (e.getTags().contains("heliosphere.cult")) e.discard(); });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("hssiege").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(net.minecraft.commands.Commands.argument("difficulty", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 15)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Kind k;
                                    try { k = Kind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind").toUpperCase(java.util.Locale.ROOT)); }
                                    catch (IllegalArgumentException e) { k = Kind.ASSAULT; }
                                    begin(p, p.level(), p.blockPosition(), k, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "difficulty"));
                                    return 1;
                                })))));
    }

    /** Siege machines that found no place to come in (for the world test). */
    public static int spawnFails;

    /** The home a siege machine is attacking, or null (Underground: sabotage). */
    public static BlockPos homeOf(Entity mob) {
        for (Siege s : ACTIVE) if (s.level == mob.level() && s.mobs.contains(mob.getUUID())) return s.home;
        return null;
    }

    private static Memory mem(ServerPlayer p) {
        return MEMORY.computeIfAbsent(p.getUUID(), u -> {
            try {
                Memory m = GSON.fromJson(p.getAttachedOrElse(memory, "{}"), Memory.class);
                if (m == null) m = new Memory();
                if (m.cells == null) m.cells = new ArrayList<>();
                return m;
            } catch (RuntimeException e) { return new Memory(); }
        });
    }

    private static void save(ServerPlayer p, Memory m) { p.setAttached(memory, GSON.toJson(m)); }

    private static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (!ACTIVE.isEmpty()) run(server);
        if (t % 200 != 13) return;
        long day = server.overworld().getDayTime() / 24000L;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            Memory m = mem(p);
            learn(p, m);
            if (p.isCreative() || busy(p)) { save(p, m); continue; }
            long now = server.overworld().getGameTime();
            // closer together as the machines evolve (2.5 days at stage 1, half a day at stage 15), and once air defence exists
            int gap = com.heliosphere.hostiles.arsenal.Evolution.siegeGap(com.heliosphere.hostiles.arsenal.Evolution.stage(day),
                    com.heliosphere.planets.defense.sam.SamSites.airDefenceBuilt(server));
            if (m.next < 0 || m.next - now > gap * 3L / 2) m.next = now + gap + p.getRandom().nextInt(gap / 2);
            Cell home = shelter(m);
            if (home != null && day >= 3 && now >= m.next && home.dim.equals(dim(p.level()))) {
                BlockPos c = home.center(p.level());
                if (p.blockPosition().distSqr(new BlockPos(c.getX(), p.getBlockY(), c.getZ())) < 80 * 80 && p.level().getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4) != null) {
                    int d = difficulty(p, m, day);
                    begin(p, p.level(), c, pick(p.getRandom(), d, m), d);
                    m.launched++;
                    m.next = now + gap + p.getRandom().nextInt(gap / 2);
                }
            }
            save(p, m);
        }
    }

    private static String dim(Level l) { return l.dimension().identifier().toString(); }

    private static boolean busy(ServerPlayer p) {
        for (Siege s : ACTIVE) if (s.player.equals(p.getUUID())) return true;
        return false;
    }

    // ---- learning where the player lives -------------------------------------------------------------------------

    private static void learn(ServerPlayer p, Memory m) {
        ServerLevel l = p.level();
        if (Planets.isSpace(l)) return;
        int cx = Math.floorDiv(p.getBlockX(), 32), cz = Math.floorDiv(p.getBlockZ(), 32);
        String d = dim(l);
        Cell cell = null;
        for (Cell c : m.cells) { c.score *= 0.999; if (c.cx == cx && c.cz == cz && c.dim.equals(d)) cell = c; }
        if (cell == null) {
            cell = new Cell();
            cell.dim = d; cell.cx = cx; cell.cz = cz;
            m.cells.add(cell);
        }
        int stuff = 0;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
            LevelChunk ch = l.getChunkSource().getChunkNow((p.getBlockX() >> 4) + i, (p.getBlockZ() >> 4) + j);
            if (ch != null) stuff += ch.getBlockEntities().size();
        }
        double gain = 1 + Math.min(stuff, 48) / 8.0;
        var respawn = p.getRespawnConfig();
        if (respawn != null && respawn.respawnData().dimension() == l.dimension()
                && respawn.respawnData().pos().distSqr(p.blockPosition()) < 40 * 40) gain += 2;
        cell.score += gain;
        cell.sx += p.getX(); cell.sz += p.getZ(); cell.n++;
        if (cell.n > 400) { cell.sx /= 2; cell.sz /= 2; cell.n /= 2; }
        m.cells.removeIf(c -> c.score < 0.5);
        if (m.cells.size() > 48) {
            m.cells.sort((a, b) -> Double.compare(b.score, a.score));
            m.cells.subList(48, m.cells.size()).clear();
        }
    }

    static Cell shelter(Memory m) {
        Cell best = null;
        for (Cell c : m.cells) if (c.score >= 60 && (best == null || c.score > best.score)) best = c;
        return best;
    }

    private static int difficulty(ServerPlayer p, Memory m, long day) {
        int d = com.heliosphere.hostiles.arsenal.Evolution.stage(day) + m.beaten / 3;
        if (p.level().dimension() != Level.OVERWORLD) {
            var body = Planets.bodyOf(p.level());
            if (body != null) d += WorldTiers.base(body.id) - 1;
        }
        return Mth.clamp(d, 1, 15);
    }

    private static Kind pick(RandomSource r, int d, Memory m) {
        List<Kind> ok = new ArrayList<>();
        for (Kind k : Kind.values()) if (d >= k.from && (k != Kind.ONSLAUGHT || m.beaten >= 3)) ok.add(k);
        if (ok.size() > 1 && r.nextFloat() < 0.6f) ok.remove(Kind.RECON);
        return ok.get(r.nextInt(ok.size()));
    }

    // ---- the siege ----------------------------------------------------------------------------------------------

    static void begin(ServerPlayer p, ServerLevel level, BlockPos home, Kind kind, int difficulty) {
        Siege s = new Siege();
        s.player = p.getUUID(); s.level = level; s.home = home; s.kind = kind; s.difficulty = difficulty;
        s.started = level.getGameTime();
        s.nextWave = s.started + (kind == Kind.INFILTRATION ? 300 : 600);
        s.bar = new ServerBossEvent(Component.translatable("event.heliosphere.siege." + kind.key() + ".bar", difficulty),
                kind == Kind.ONSLAUGHT ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        s.bar.setProgress(1f);
        ACTIVE.add(s);
        for (ServerPlayer q : level.players())
            if (q.blockPosition().distSqr(home) < 160 * 160) {
                q.displayClientMessage(Component.translatable("event.heliosphere.siege." + kind.key() + ".warn", difficulty, home.getX(), home.getZ())
                        .withStyle(kind == Kind.ONSLAUGHT ? ChatFormatting.DARK_RED : ChatFormatting.RED), false);
                q.displayClientMessage(Component.translatable("event.heliosphere.siege.eta", (s.nextWave - s.started) / 20).withStyle(ChatFormatting.GOLD), true);
                level.playSound(null, q.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, kind == Kind.ONSLAUGHT ? 0.6f : 1.1f);
                s.bar.addPlayer(q);
            }
    }

    private static void run(MinecraftServer server) {
        for (Iterator<Siege> it = ACTIVE.iterator(); it.hasNext(); ) {
            Siege s = it.next();
            ServerLevel l = s.level;
            long now = l.getGameTime();
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            if (p == null || p.level() != l || now - s.started > 12000) { end(s, false); it.remove(); continue; }
            if (s.wave < s.kind.waves && now >= s.nextWave) {
                wave(s, p);
                s.wave++;
                s.nextWave = now + 900;
            }
            if (now % 10 == 0) {
                s.mobs.removeIf(id -> { Entity e = l.getEntity(id); return e == null || !e.isAlive(); });
                float left = s.total == 0 ? 1f : s.mobs.size() / (float) s.total;
                s.bar.setProgress(Mth.clamp(left, 0f, 1f));
                s.bar.setName(Component.translatable("event.heliosphere.siege." + s.kind.key() + ".bar", s.difficulty)
                        .append(Component.translatable("event.heliosphere.siege.left", s.mobs.size(), s.wave, s.kind.waves)));
                for (ServerPlayer q : l.players())
                    if (q.blockPosition().distSqr(s.home) < 160 * 160) s.bar.addPlayer(q); else s.bar.removePlayer(q);
                if (p.isDeadOrDying()) { end(s, false); it.remove(); continue; }
                if (s.wave >= s.kind.waves && s.mobs.isEmpty() && s.total > 0) { end(s, true); it.remove(); }
            }
        }
    }

    private static void wave(Siege s, ServerPlayer p) {
        ServerLevel l = s.level;
        RandomSource r = l.random;
        int d = s.difficulty;
        boolean last = s.wave == s.kind.waves - 1;
        List<String> pool = EnemyRoster.pool(d);
        List<String> pick = switch (s.kind) {
            case RECON -> filter(pool, id -> id.contains("drone"));
            case BOMBARD -> filter(pool, id -> id.equals("mortar_walker") || id.equals("siege_drone") || id.equals("missile_drone") || id.equals("siege_walker")
                    || id.equals("strike_drone") || id.equals("strike_jet"));
            case INFILTRATION -> filter(pool, id -> id.contains("stalker") || id.equals("arc_hound") || id.equals("emp_spider"));
            default -> pool;
        };
        if (pick.isEmpty()) pick = pool;
        int n = switch (s.kind) {
            case RECON -> 3 + d / 3;
            case ASSAULT -> 4 + d / 2;
            case BOMBARD -> 2 + d / 4;
            case INFILTRATION -> 2 + d / 4;
            case ONSLAUGHT -> 5 + d / 2;
        };
        double a = r.nextDouble() * Math.PI * 2;
        double dist = s.kind == Kind.INFILTRATION ? 14 + r.nextInt(8) : s.kind == Kind.BOMBARD ? 44 + r.nextInt(12) : 34 + r.nextInt(14);
        boolean pods = s.kind == Kind.ASSAULT || s.kind == Kind.ONSLAUGHT;
        for (int i = 0; i < n; i++) {
            String id = pick.get(r.nextInt(pick.size()));
            spawn(s, p, id, a + r.nextGaussian() * 0.25, dist + r.nextGaussian() * 3, pods);
        }
        if (s.kind == Kind.BOMBARD)                              // escorts for the guns
            for (int i = 0; i < 1 + d / 5; i++) spawn(s, p, pool.get(r.nextInt(pool.size())), a + r.nextGaussian() * 0.2, dist - 10, false);
        if (com.heliosphere.hostiles.arsenal.Underground.covered(l, p)) {             // the player hides: diggers, and bunker busters from the sky
            boolean deep = com.heliosphere.hostiles.arsenal.Underground.deep(l, p);
            if (d >= 3 && deep) for (int i = 0; i < 1 + d / 6; i++) spawn(s, p, com.heliosphere.hostiles.arsenal.Underground.DRILL, a + r.nextGaussian() * 0.3, dist - 6, false);
            if (d >= 4) com.heliosphere.hostiles.arsenal.Underground.bunkerStrike(l, p, 1 + d / 5, Math.min(10, 2 + d / 2), 14 + d * 2);
            for (ServerPlayer q : l.players())
                if (q.blockPosition().distSqr(s.home) < 160 * 160)
                    q.displayClientMessage(Component.translatable(deep ? "event.heliosphere.siege.underground" : "event.heliosphere.siege.covered").withStyle(ChatFormatting.DARK_RED), false);
        }
        if (s.kind == Kind.ONSLAUGHT && last) {
            String boss = EnemyRoster.boss(Math.max(6, d));
            if (boss != null) spawn(s, p, boss, a, dist + 6, false);
        }
        for (ServerPlayer q : l.players())
            if (q.blockPosition().distSqr(s.home) < 160 * 160) {
                q.displayClientMessage(Component.translatable(pods ? "event.heliosphere.siege.pods" : "event.heliosphere.siege.wave", s.wave + 1, s.kind.waves,
                        Component.translatable("event.heliosphere.dir." + direction(a))).withStyle(ChatFormatting.RED), true);
                l.playSound(null, q.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.2f, 0.7f);
            }
    }

    private static List<String> filter(List<String> pool, java.util.function.Predicate<String> f) {
        List<String> out = new ArrayList<>();
        for (String id : pool) if (f.test(id)) out.add(id);
        return out;
    }

    private static String direction(double a) {
        // x = cos, z = sin; north is -z
        int i = Math.floorMod((int) Math.round(Math.toDegrees(a) / 90.0), 4);
        return new String[]{"east", "south", "west", "north"}[i];
    }

    private static void spawn(Siege s, ServerPlayer p, String id, double a, double dist, boolean pod) {
        ServerLevel l = s.level;
        boolean ground = !pod && !id.contains("drone") && !id.equals("hive_carrier") && !id.equals("strike_jet");
        // the home is underground: infiltrators all, other walkers half the time, come out of the caves (drills dig in from outside)
        boolean fromCaves = !id.equals(com.heliosphere.hostiles.arsenal.Underground.DRILL) && (s.kind == Kind.INFILTRATION || l.random.nextFloat() < 0.5f);
        if (ground && fromCaves && com.heliosphere.hostiles.arsenal.Underground.deep(l, p)) {
            BlockPos cave = com.heliosphere.hostiles.arsenal.Underground.caveSpot(l, p.blockPosition(), l.random);   // out of the caves round the home
            if (cave != null) {
                TechMob m = TechCombat.spawn(l, id, cave.getX() + 0.5, cave.getY(), cave.getZ() + 0.5, p);
                if (m != null) {
                    m.addTag(TAG);
                    m.setTarget(p);
                    s.mobs.add(m.getUUID());
                    s.total++;
                }
                return;
            }
        }
        for (int tries = 0; tries < 6; tries++) {
            int x = (int) (s.home.getX() + Math.cos(a) * dist), z = (int) (s.home.getZ() + Math.sin(a) * dist);
            if (l.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) { dist *= 0.8; continue; }
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!l.getFluidState(new BlockPos(x, y - 1, z)).isEmpty() && !id.contains("drone") && !id.equals("hive_carrier")) { a += 0.4; continue; }
            double sy = pod ? y + 38 + l.random.nextInt(10) : y + (id.contains("drone") ? 6 : 0.1);
            TechMob m = TechCombat.spawn(l, id, x + 0.5, sy, z + 0.5, p);
            if (m == null) { spawnFails++; return; }
            m.addTag(TAG);
            if (m instanceof Mob mob) mob.setTarget(p);
            if (pod) {
                m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
                Pods.add(m);
            }
            s.mobs.add(m.getUUID());
            s.total++;
            return;
        }
        spawnFails++;                                            // water or unloaded ground all round
    }

    private static void end(Siege s, boolean won) {
        ServerLevel l = s.level;
        s.bar.removeAllPlayers();
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(s.player);
        if (p == null) return;
        Memory m = mem(p);
        if (won) {
            m.beaten++;
            com.heliosphere.core.progress.Achievements.grant(p, "siege");
            if (m.beaten >= 10) com.heliosphere.core.progress.Achievements.grant(p, "siege_10");
            int rolls = 2 + s.difficulty + s.kind.ordinal();
            SimpleContainer box = new SimpleContainer(27);
            FortressLoot.fill(box, Math.max(1, s.difficulty - 1), rolls, l.random);
            Containers.dropContents(l, p.blockPosition().above(), box);
            ExperienceOrb.award(l, p.position(), 20 + s.difficulty * s.difficulty * 6);
            l.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 2, p.getZ(), 60, 1.5, 1.5, 1.5, 0.2);
            l.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1f);
            for (ServerPlayer q : l.players())
                if (q.blockPosition().distSqr(s.home) < 160 * 160)
                    q.displayClientMessage(Component.translatable("event.heliosphere.siege.won", m.beaten).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        } else {
            p.displayClientMessage(Component.translatable("event.heliosphere.siege.over").withStyle(ChatFormatting.GRAY), false);
        }
        save(p, m);
    }
}
