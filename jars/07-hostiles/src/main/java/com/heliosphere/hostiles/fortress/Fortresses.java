package com.heliosphere.hostiles.fortress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.heliosphere.core.Reg;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.world.PlanetGeometry;
import com.heliosphere.hostiles.arsenal.WorldTiers;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Machine fortresses. The surface of the Overworld and of every solid world is cut into 576-block regions; about
 * three regions in five hold a fortress, built the first time a player comes near. Difficulty runs from 1 to 15:
 * on Earth it grows with distance from spawn (at most 8), elsewhere it starts at the world's base tier. A fortress
 * is conquered when all of its fabricators are destroyed and its boss is dead; it then drops a reward cache on
 * the keep roof.
 */
public final class Fortresses implements ModInitializer {
    public static final int REGION = 576;
    public static Block FABRICATOR;
    public static BlockEntityType<FabricatorBlockEntity> FABRICATOR_BE;

    private static final Gson GSON = new GsonBuilder().create();
    private static AttachmentType<String> saved;

    /** One fortress as stored in the world. */
    static final class Fort {
        int x, y, z, tier;
        List<long[]> fabs = new ArrayList<>();
        String boss;                       // the only warden of fortresses built before several were possible
        List<String> bosses = new ArrayList<>();
        boolean bossDead, conquered;
        int keepY;

        /** Wardens still standing (reads old saves too). */
        List<String> wardens() {
            if (bosses == null) bosses = new ArrayList<>();
            if (boss != null && !bossDead && !bosses.contains(boss)) bosses.add(boss);
            boss = null;
            return bosses;
        }

        BlockPos center() { return new BlockPos(x, y, z); }
    }

    static final class State {
        Set<Long> regions = new HashSet<>();
        List<Fort> forts = new ArrayList<>();
    }

    private static final Map<ServerLevel, State> STATES = new HashMap<>();
    private record Pending(FortressBuilder.Job job, int tier) {}
    private static final Map<ServerLevel, List<Pending>> JOBS = new HashMap<>();
    /** Blocks placed per tick per fortress under construction. */
    private static final int BUDGET = 9000;
    private static final Map<UUID, Set<Long>> ANNOUNCED = new HashMap<>();

    @Override
    public void onInitialize() {
        Reg.tab("enemies");
        FABRICATOR = Reg.blockWithItem("robot_fabricator", FabricatorBlock::new,
                Reg.metal(MapColor.COLOR_RED, 40f).lightLevel(s -> 9).strength(40f, 1200f).requiresCorrectToolForDrops());
        FABRICATOR_BE = Reg.blockEntity("robot_fabricator", FabricatorBlockEntity::new, FABRICATOR);
        saved = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("heliosphere", "machine_fortresses_v1"), Codec.STRING);
        ServerTickEvents.END_WORLD_TICK.register(Fortresses::tick);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("hsfortress").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.argument("tier", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 15)).executes(c -> {
                            var src = c.getSource();
                            int tier = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tier");
                            Vec3 at = src.getPosition();
                            int off = FortressBuilder.radius(tier) + 24;
                            forceAt(src.getLevel(), (int) at.x + off, (int) at.z, tier);
                            src.sendSuccess(() -> Component.literal("Fortress (difficulty " + tier + ") under construction at " + ((int) at.x + off) + ", " + (int) at.z), true);
                            return 1;
                        }))));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel level) bossDied(level, entity);
            if (entity instanceof com.heliosphere.planets.tech.TechMob mob) machineDown(mob, source);
        });
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> {
            try {
                State s = GSON.fromJson(l.getAttachedOrElse(saved, "{}"), State.class);
                if (s == null) s = new State();
                if (s.regions == null) s.regions = new HashSet<>();
                if (s.forts == null) s.forts = new ArrayList<>();
                return s;
            } catch (RuntimeException e) {
                return new State();
            }
        });
    }

    private static void save(ServerLevel level) { level.setAttached(saved, GSON.toJson(state(level))); }

    /** Whether fortresses appear in this world at all. */
    static boolean hosts(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) return true;
        if (!Planets.isHeliosphereDimension(level) || Planets.isSpace(level) || Planets.isGasGiant(level)) return false;
        return Planets.bodyOf(level) != null;
    }

    static String bodyId(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) return "earth";
        CelestialBody b = Planets.bodyOf(level);
        return b == null ? "earth" : b.id;
    }

    private static void tick(ServerLevel level) {
        List<Pending> jobs = JOBS.get(level);
        if (jobs != null && !jobs.isEmpty()) {
            Pending pj = jobs.getFirst();
            if (pj.job().step(BUDGET)) {
                jobs.removeFirst();
                FortressBuilder.Result res = pj.job().finish();
                State st = state(level);
                Fort f = new Fort();
                f.x = pj.job().cx; f.z = pj.job().cz; f.tier = pj.tier();
                f.y = pj.job().y0;
                f.keepY = res.keepTop().getY();
                for (BlockPos b : res.fabricators()) f.fabs.add(new long[]{b.asLong()});
                f.bosses = new ArrayList<>(res.bosses());
                f.bossDead = f.bosses.isEmpty();
                st.forts.add(f);
                save(level);
            }
        }
        long t = level.getGameTime();
        if (t % 40 != 7 || level.players().isEmpty() || !hosts(level)) return;
        State st = state(level);
        boolean dirty = false;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int rx = Math.floorDiv(p.getBlockX(), REGION), rz = Math.floorDiv(p.getBlockZ(), REGION);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) dirty |= consider(level, st, rx + dx, rz + dz, p);
        }
        for (Fort f : st.forts) if (!f.conquered) dirty |= watch(level, f);
        if (dirty) save(level);
    }

    private static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xffffffffL); }

    /** Builds the region's fortress once a player is close enough and its ground is loaded. */
    private static boolean consider(ServerLevel level, State st, int rx, int rz, ServerPlayer p) {
        long key = key(rx, rz);
        if (st.regions.contains(key)) return false;
        RandomSource r = RandomSource.create(level.getSeed() ^ key * 0x9E3779B97F4A7C15L ^ level.dimension().identifier().hashCode());
        if (r.nextFloat() > 0.6f) { st.regions.add(key); return true; }
        int tier = tier(level, rx * REGION + REGION / 2, rz * REGION + REGION / 2, r);
        int R = FortressBuilder.radius(tier);
        int margin = R + 48;                                                   // big fortresses never reach into the next region's
        int x = rx * REGION + margin + r.nextInt(Math.max(1, REGION - 2 * margin)), z = rz * REGION + margin + r.nextInt(Math.max(1, REGION - 2 * margin));
        double dx = p.getX() - x, dz = p.getZ() - z;
        double trigger = 200 + R;
        if (dx * dx + dz * dz > trigger * trigger) return false;
        int span = R + 10;
        for (int cx = (x - span) >> 4; cx <= (x + span) >> 4; cx++)
            for (int cz = (z - span) >> 4; cz <= (z + span) >> 4; cz++)
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
        st.regions.add(key);
        if (!fits(level, x, z, span)) return true;
        JOBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(FortressBuilder.plan(level, x, z, tier, FortressStyle.of(bodyId(level)), r), tier));
        return true;
    }

    private static int tier(ServerLevel level, int x, int z, RandomSource r) {
        if (level.dimension() == Level.OVERWORLD) {
            BlockPos spawn = level.getRespawnData().pos();
            double d = Math.sqrt(spawn.distSqr(new BlockPos(x, spawn.getY(), z)));
            return Mth.clamp(2 + (int) (d / 700) + r.nextInt(3) - 1, 1, 10);
        }
        return Mth.clamp(WorldTiers.base(bodyId(level)) + 1 + r.nextInt(6), 1, 15);
    }

    /** No fortress near the Overworld spawn, in the sea, in lava or across a planet's seam or pole. */
    private static boolean fits(ServerLevel level, int x, int z, int span) {
        if (level.dimension() == Level.OVERWORLD) {
            BlockPos spawn = level.getRespawnData().pos();
            if (spawn.distSqr(new BlockPos(x, spawn.getY(), z)) < 400 * 400) return false;
        } else {
            PlanetGeometry g = Planets.geometry(level);
            if (g != null && (Math.abs(x) > g.halfWidth() - span - 32 || Math.abs(z) > g.poleZ() - span - 32)) return false;
        }
        int wet = 0;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
            int px = x + i * span, pz = z + j * span;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz);
            if (!level.getFluidState(new BlockPos(px, y - 1, pz)).isEmpty()) wet++;
        }
        if (wet > 2) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return y > level.getMinY() + 12 && y < level.getMaxY() - 60;
    }

    // ---- conquest ------------------------------------------------------------------------------------------------

    private static boolean watch(ServerLevel level, Fort f) {
        ServerPlayer near = null;
        double best = 160 * 160;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(f.x + 0.5, p.getY(), f.z + 0.5);
            if (d < best) { best = d; near = p; }
        }
        if (near == null) return false;
        announce(level, f, near, best);
        if (level.getChunkSource().getChunkNow(f.x >> 4, f.z >> 4) == null) return false;
        boolean dirty = false;
        for (var it = f.fabs.iterator(); it.hasNext(); ) {
            BlockPos b = BlockPos.of(it.next()[0]);
            if (level.getChunkSource().getChunkNow(b.getX() >> 4, b.getZ() >> 4) == null) continue;
            if (!level.getBlockState(b).is(FABRICATOR)) { it.remove(); dirty = true; }
        }
        if (!f.bossDead && f.fabs.isEmpty() && best < 96 * 96) {
            boolean removed = f.wardens().removeIf(id -> { Entity b = level.getEntity(UUID.fromString(id)); return b == null || !b.isAlive(); });
            if (f.wardens().isEmpty()) { f.bossDead = true; dirty = true; }
            else if (removed) dirty = true;
        }
        if (f.fabs.isEmpty() && f.bossDead) { conquer(level, f); dirty = true; }
        return dirty;
    }

    private static void announce(ServerLevel level, Fort f, ServerPlayer p, double distSq) {
        if (distSq > 112 * 112) return;
        long id = BlockPos.asLong(f.x, 0, f.z);
        if (!ANNOUNCED.computeIfAbsent(p.getUUID(), u -> new HashSet<>()).add(id)) return;
        p.displayClientMessage(Component.translatable("message.heliosphere.fortress_detected", f.tier, f.fabs.size())
                .withStyle(f.tier >= 11 ? ChatFormatting.DARK_RED : f.tier >= 6 ? ChatFormatting.RED : ChatFormatting.GOLD), false);
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.7f, 1.4f);
    }

    static void fabricatorLost(ServerLevel level, BlockPos pos) {
        if (!hosts(level)) return;
        State st = state(level);
        for (Fort f : st.forts) {
            if (f.conquered) continue;
            boolean hit = f.fabs.removeIf(a -> a[0] == pos.asLong());
            if (!hit) continue;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.4);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4f, 0.8f);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(Vec3.atCenterOf(pos)) >= 160 * 160) continue;
                if (p.distanceToSqr(Vec3.atCenterOf(pos)) < 32 * 32) com.heliosphere.core.progress.Achievements.grant(p, "fabricator");
                p.displayClientMessage(Component.translatable(f.fabs.isEmpty() ? "message.heliosphere.fabricators_down" : "message.heliosphere.fabricator_down",
                        f.fabs.size()).withStyle(ChatFormatting.AQUA), true);
            }
            if (f.fabs.isEmpty() && f.bossDead) conquer(level, f);
            save(level);
            return;
        }
    }

    /** Advancements for destroying machines: by kind, and by the weapon that did it. */
    private static void machineDown(com.heliosphere.planets.tech.TechMob mob, net.minecraft.world.damagesource.DamageSource source) {
        ServerPlayer p = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (source.getDirectEntity() instanceof com.heliosphere.planets.defense.sam.GuidedMissile gm) {
            var spec = gm.spec();
            if (p != null && spec == com.heliosphere.planets.defense.sam.MissileSpec.PLAYER) com.heliosphere.core.progress.Achievements.grant(p, "lock_on");
            boolean sam = spec == com.heliosphere.planets.defense.sam.MissileSpec.BUK_9M317MA || spec == com.heliosphere.planets.defense.sam.MissileSpec.ASTER_30
                    || spec == com.heliosphere.planets.defense.sam.MissileSpec.IRIS_T_SL;
            if (sam && p != null && mob.isNoGravity()) com.heliosphere.core.progress.Achievements.grant(p, "sam_kill");
        }
        if (p == null) return;
        com.heliosphere.core.progress.Achievements.grant(p, "first_machine");
        String id = mob.role() == null ? "" : mob.role().id();
        if (id.equals("titan_walker")) com.heliosphere.core.progress.Achievements.grant(p, "titan_slayer");
        if (id.equals("colossus")) com.heliosphere.core.progress.Achievements.grant(p, "colossus");
    }

    private static void bossDied(ServerLevel level, Entity e) {
        if (!hosts(level)) return;
        String id = e.getUUID().toString();
        State st = state(level);
        for (Fort f : st.forts) {
            if (f.conquered || f.bossDead || !f.wardens().contains(id)) continue;
            f.wardens().remove(id);
            if (!f.wardens().isEmpty()) {
                for (ServerPlayer p : level.players())
                    if (p.distanceToSqr(e) < 160 * 160)
                        p.displayClientMessage(Component.translatable("message.heliosphere.fortress_boss_down", e.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
                save(level);
                return;
            }
            f.bossDead = true;
            for (ServerPlayer p : level.players())
                if (p.distanceToSqr(e) < 160 * 160)
                    p.displayClientMessage(Component.translatable("message.heliosphere.fortress_boss_down", e.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
            if (f.fabs.isEmpty()) conquer(level, f);
            save(level);
            return;
        }
    }

    /** Drops the reward cache on the keep roof and tells everyone nearby. */
    private static void conquer(ServerLevel level, Fort f) {
        if (f.conquered) return;
        f.conquered = true;
        RandomSource r = level.random;
        BlockPos at = new BlockPos(f.x + 1, f.keepY, f.z + 1);
        int caches = 1 + f.tier / 6;
        for (int i = 0; i < caches; i++) {
            BlockPos c = at.east(i);
            level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
            if (level.getBlockEntity(c) instanceof ChestBlockEntity chest) FortressLoot.fill(chest, Math.min(15, f.tier + 1), 6 + f.tier * 2, r);
        }
        level.setBlock(at.north(), Blocks.BEACON.defaultBlockState(), 3);
        int xp = 40 + f.tier * f.tier * 12;
        ExperienceOrb.award(level, Vec3.atCenterOf(at.above()), xp);
        level.sendParticles(ParticleTypes.FIREWORK, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 120, 2, 3, 2, 0.25);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.4);
        level.playSound(null, at, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 1f);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(f.x, p.getY(), f.z) < 200 * 200) {
                com.heliosphere.core.progress.Achievements.grant(p, "fortress_1");
                if (f.tier >= 10) com.heliosphere.core.progress.Achievements.grant(p, "fortress_10");
                if (f.tier >= 13) com.heliosphere.core.progress.Achievements.grant(p, "mega_fortress");
            }
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(f.x, p.getY(), f.z) < 200 * 200)
                p.displayClientMessage(Component.translatable("message.heliosphere.fortress_conquered", f.tier, at.getX(), at.getY(), at.getZ())
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
    }

    // ---- commands support ----------------------------------------------------------------------------------------

    /** Builds a fortress of the given difficulty right here (used by the test run and /heliosphere commands). */
    public static void forceAt(ServerLevel level, int x, int z, int tier) {
        JOBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(FortressBuilder.plan(level, x, z, tier, FortressStyle.of(bodyId(level)), level.random), tier));
    }
}
