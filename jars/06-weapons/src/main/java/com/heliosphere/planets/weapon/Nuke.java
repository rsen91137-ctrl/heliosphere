package com.heliosphere.planets.weapon;

import com.google.gson.Gson;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.fx.sim.MushroomCloud;
import com.heliosphere.core.fx.sim.Rng;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import com.heliosphere.planets.weapon.nuke.NukeContent;
import com.heliosphere.planets.weapon.nuke.Radiation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A tactical nuclear detonation, played out over time on the server.
 *
 * <ol>
 *   <li>The instant: thermal flash (anything in line of sight within the fire radius burns) and the fireball.</li>
 *   <li>The blast wave leaves ground zero at the speed the cloud effect draws it, and hurts, throws and
 *       deafens whatever it reaches when it reaches it, not before.</li>
 *   <li>The crater is dug a slice at a time so the server keeps its tick rate. Every column is dug down from its
 *       own ground (or from ground zero where it stands higher), so the crater follows the terrain; its floor is
 *       zoned from the centre out: melt and fused glass, glassed and baked rock, then rubble thrown into a rim.
 *       Nothing is ever placed in open air.</li>
 *   <li>Beyond the crater the surface is scorched: vegetation burns off, soil bakes, fires start, and
 *       radioactive ash settles.</li>
 *   <li>Fallout: for twelve minutes the area doses anyone without a sealed suit; the glass and ash stay
 *       radioactive for good.</li>
 * </ol>
 */
public final class Nuke {
    public static final int CRATER_RADIUS = 72;
    public static final int SHOCK_RADIUS = 360;
    public static final int FALLOUT_RADIUS = 128;
    private static final int SCORCH_RADIUS = 136;
    private static final int THERMAL_RADIUS = 250;
    /** Out to here the blast wave knocks down what is built on the ground, less and less with distance. */
    private static final int FLATTEN_RADIUS = 260;
    /** Damage at ground zero (at least 10000) and how fast it falls away: 1/e every this many blocks. */
    private static final float CORE_DAMAGE = 12000f, CORE_FALLOFF = 28f;
    private static final int CRATER_DEPTH = 34;
    private static final float SHOCK_PER_TICK = MushroomCloud.SHOCK_SPEED / 20f;
    private static final long FALLOUT_TICKS = 14400;
    private static final int HEARD_RADIUS = 1600;

    private static final int CRATER_COLUMNS = (2 * CRATER_RADIUS + 1) * (2 * CRATER_RADIUS + 1);
    private static final int SCORCH_COLUMNS = (2 * SCORCH_RADIUS + 1) * (2 * SCORCH_RADIUS + 1);
    private static final int FLAT_COLUMNS = (2 * FLATTEN_RADIUS + 1) * (2 * FLATTEN_RADIUS + 1);
    /** setBlock flags: tell clients, but no neighbour updates, drops or block-entity side effects. */
    private static final int SILENT = 818;
    private static final TicketType LOAD = new TicketType(100L, TicketType.FLAG_LOADING);

    private static AttachmentType<String> saved;
    private static SoundEvent boom;
    private static final Gson GSON = new Gson();
    private static final Map<ServerLevel, List<Blast>> BLASTS = new WeakHashMap<>();

    /** Persistent state of one detonation. Field names are the save format. */
    static final class Blast {
        double x, y, z;
        long start;
        /** Next crater column (spiral index) to dig. */
        int column;
        /** Unused since v4; kept so worlds saved by earlier builds still load. */
        int depth, top;
        /** Next scorch column (spiral index) to treat. */
        int scorch;
        int seed;
        /** Crater size relative to a ground burst: smaller and shallower the higher the burst (0 in old saves = 1). */
        float scale;
        /** Next column (spiral index) the blast wave knocks down. */
        int flat;

        float k() { return scale <= 0 ? 1 : scale; }
    }

    private Nuke() {}

    public static void init() {
        NukeContent.init();
        saved = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("heliosphere", "tactical_nuclear_blasts_v3"), Codec.STRING);
        boom = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("heliosphere", "tactical_nuclear_blast"));
        ServerTickEvents.END_WORLD_TICK.register(Nuke::tick);
    }

    private static List<Blast> blasts(ServerLevel level) {
        return BLASTS.computeIfAbsent(level, l -> {
            try {
                Blast[] a = GSON.fromJson(l.getAttachedOrElse(saved, "[]"), Blast[].class);
                return new ArrayList<>(a == null ? List.of() : Arrays.asList(a));
            } catch (RuntimeException e) {
                return new ArrayList<>();
            }
        });
    }

    private static void save(ServerLevel level, List<Blast> list) { level.setAttached(saved, GSON.toJson(list)); }

    public static void detonate(ServerLevel level, Entity source, Vec3 c) {
        // The warhead may go off in the air (a shot that hit nothing ends in mid-air). The crater is always dug
        // into the ground below; the higher the burst, the shallower and smaller it is.
        int gx = (int) Math.floor(c.x), gz = (int) Math.floor(c.z);
        level.getChunk(gx >> 4, gz >> 4);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
        double height = c.y - ground;
        Blast b = new Blast();
        b.x = c.x; b.z = c.z;
        b.y = height > 1.5 ? ground : c.y;
        b.scale = height > 1.5 ? (float) Math.max(0.12, Math.min(1, 1 - height / 140.0)) : 1f;
        b.start = level.getGameTime();
        b.seed = level.getRandom().nextInt();
        List<Blast> list = blasts(level);
        if (list.size() >= 8) list.removeFirst();
        list.add(b);
        save(level, list);

        if (height < 40) level.explode(source, b.x, b.y, b.z, 12f, Level.ExplosionInteraction.TNT);
        // thermal pulse: arrives with the light, burns whatever can see the fireball
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(THERMAL_RADIUS))) {
            double d = e.position().distanceTo(c);
            if (d > THERMAL_RADIUS || !sees(level, c, e)) continue;
            float k = (float) (1 - d / THERMAL_RADIUS);
            e.igniteForSeconds(4 + 12 * k);
            e.hurtServer(level, level.damageSources().inFire(), 60f * k * k * k);
        }
        CombatVfx.send(level, CombatVfx.NUKE, 0, c, new Vec3(b.seed, 0, 0), 1f);
    }

    private static boolean sees(ServerLevel level, Vec3 c, Entity e) {
        Vec3 from = c.add(0, 6, 0);
        return level.clip(new ClipContext(from, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
    }

    // ---- per tick -----------------------------------------------------------------------------------------------

    private static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 20 == 0) {
            // the ground stays hot whether or not a blast is still on the books
            for (ServerPlayer p : level.players()) {
                float g = Radiation.ground(level, p.blockPosition());
                if (g > 0) Radiation.irradiate(p, g * Radiation.exposure(p));
            }
        }
        if (level.getAttachedOrElse(saved, "[]").length() <= 2 && !BLASTS.containsKey(level)) return;
        List<Blast> list = blasts(level);
        if (list.isEmpty()) return;

        long working = list.stream().filter(b -> b.scorch < SCORCH_COLUMNS).count();
        boolean lagging = level.getServer().getCurrentSmoothedTickTime() > 35f;
        int budget = (int) ((lagging ? 1200 : 5000) / Math.max(1, working));
        boolean dirty = false;
        for (Blast b : list) {
            long age = now - b.start;
            if (age * SHOCK_PER_TICK <= HEARD_RADIUS + SHOCK_PER_TICK) shock(level, b, (age - 1) * SHOCK_PER_TICK, age * SHOCK_PER_TICK);
            if (b.column < CRATER_COLUMNS) { carve(level, b, budget); dirty = true; }
            else if (b.scorch < SCORCH_COLUMNS) { scorch(level, b, budget * 2); dirty = true; }
            if (b.flat < FLAT_COLUMNS) { flatten(level, b, budget, age * SHOCK_PER_TICK); dirty = true; }
            if (age < FALLOUT_TICKS && now % 40 == 0) fallout(level, b, age);
        }
        if (list.removeIf(b -> now - b.start > FALLOUT_TICKS && b.scorch >= SCORCH_COLUMNS && b.flat >= FLAT_COLUMNS)) dirty = true;
        if (dirty && now % 20 == 0) save(level, list);
    }

    /** The blast front has moved from radius r0 to r1 this tick: everything in that shell feels it now. */
    private static void shock(ServerLevel level, Blast b, float r0, float r1) {
        Vec3 c = new Vec3(b.x, b.y, b.z);
        if (r0 < SHOCK_RADIUS) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(Math.min(r1, SHOCK_RADIUS) + 1))) {
                double d = e.position().distanceTo(c);
                if (d <= r0 || d > r1 || d > SHOCK_RADIUS) continue;
                // overwhelming at the centre (12000), falling off fast; a long, weaker tail out to the safe distance (400)
                double k = Math.pow(1 - d / SHOCK_RADIUS, 2);
                float damage = (float) (CORE_DAMAGE * Math.exp(-d / CORE_FALLOFF) + 140 * k * (sees(level, c, e) ? 1 : 0.35));
                e.invulnerableTime = 0;
                e.hurtServer(level, level.damageSources().explosion(null, null), damage);
                e.knockback(4.5 * (1 - d / SHOCK_RADIUS), b.x - e.getX(), b.z - e.getZ());
                e.hurtMarked = true;
            }
        }
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(b.x, b.y, b.z));
            if (d <= r0 || d > r1) continue;
            float near = (float) Math.max(0, 1 - d / SHOCK_RADIUS);
            float volume = d < 96 ? 4f : (float) Math.max(0.35, 3.2 * (1 - d / HEARD_RADIUS));
            p.connection.send(new ClientboundSoundPacket(Holder.direct(boom), SoundSource.NEUTRAL, p.getX(), p.getY(), p.getZ(), volume, 0.85f, level.getGameTime()));
            CombatVfx.to(p, new CombatVfx(CombatVfx.SHOCK, 0, c, p.position(), near));
        }
    }

    /** Lingering fallout: doses the living and tells nearby clients how thick the ash in the air is. */
    private static void fallout(ServerLevel level, Blast b, long age) {
        float decay = age < 1200 ? 1f : (float) Math.exp(-(age - 1200) / 4000.0);
        Vec3 c = new Vec3(b.x, b.y, b.z);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(FALLOUT_RADIUS))) {
            double d = e.position().distanceTo(c);
            if (d > FALLOUT_RADIUS || e.isSpectator()) continue;
            float air = (float) (1 - d / FALLOUT_RADIUS) * decay * (d < CRATER_RADIUS ? 1.25f : 1f);
            Radiation.irradiate(e, Math.min(1, air) * Radiation.exposure(e));
        }
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(b.x, b.y, b.z));
            if (d < FALLOUT_RADIUS + 32) CombatVfx.to(p, new CombatVfx(CombatVfx.FALLOUT, 0, c, p.position(), (float) Math.max(0.05, (1 - d / (FALLOUT_RADIUS + 32)) * decay)));
        }
    }

    // ---- terrain ------------------------------------------------------------------------------------------------

    /** Square spiral: index 0 is the centre, then rings outward. Returns {dx, dz}. */
    static int[] spiral(int n) {
        if (n == 0) return new int[]{0, 0};
        int ring = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        int side = 2 * ring, first = (2 * ring - 1) * (2 * ring - 1);
        int k = n - first, edge = k / side, off = k % side;
        return switch (edge) {
            case 0 -> new int[]{ring, -ring + 1 + off};
            case 1 -> new int[]{ring - 1 - off, ring};
            case 2 -> new int[]{-ring, ring - 1 - off};
            default -> new int[]{-ring + 1 + off, -ring};
        };
    }

    /** The chunk of a column, or null (after asking for it to load) if it is not in memory yet. */
    private static LevelChunk chunk(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) level.getChunkSource().addTicketWithRadius(LOAD, new ChunkPos(x >> 4, z >> 4), 0);
        return chunk;
    }

    private static float noise(Blast b, int x, int z, float scale, int salt) {
        return Rng.noise(x * scale, 0.5f, z * scale, b.seed + salt);
    }

    private static void carve(ServerLevel level, Blast b, int budget) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int cx = (int) Math.floor(b.x), cy = (int) Math.floor(b.y), cz = (int) Math.floor(b.z);
        boolean air = Environment.breathable(level, BlockPos.containing(b.x, b.y + 2, b.z));
        while (budget > 0 && b.column < CRATER_COLUMNS) {
            int[] s = spiral(b.column);
            double r = Math.hypot(s[0], s[1]);
            double crater = CRATER_RADIUS * (0.45 + 0.55 * b.k());
            if (r > crater + 9) { b.column++; continue; }
            int x = cx + s[0], z = cz + s[1];
            LevelChunk chunk = chunk(level, x, z);
            if (chunk == null) return;                                       // wait for it; resume on a later tick
            b.column++;
            int surface = Math.min(level.getMaxY() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
            // this column's own natural ground: below its trees, buildings and plants
            int groundY = surface;
            for (int guard = 0; guard < 160 && groundY > level.getMinY() + 1; guard++, groundY--) {
                BlockState st = level.getBlockState(p.set(x, groundY, z));
                if (!st.isAir() && ground(st)) break;
            }
            float wobble = (noise(b, x, z, 0.09f, 1) - 0.5f) * 0.16f;
            float rn = (float) (r / crater) + wobble;                        // normalised radius with a ragged edge
            int h = Rng.hash(x * 31 + z * 17 + b.seed);

            if (rn >= 1f) {                                                  // the rim: ejecta heaped on the real ground
                if (!ground(level.getBlockState(p.set(x, groundY, z)))) continue;
                int pile = Math.round((1.14f - rn) / 0.14f * (2.2f + 2.5f * noise(b, x, z, 0.21f, 2)));
                for (int i = 1; i <= pile && groundY + i < level.getMaxY(); i++) {
                    p.set(x, groundY + i, z);
                    BlockState above = level.getBlockState(p);
                    if (!above.isAir() && !above.canBeReplaced()) break;
                    level.setBlock(p, rubble(h + i * 7), SILENT);
                    budget--;
                }
                continue;
            }
            // the bowl, measured from this column's ground (or from ground zero where the column stands higher):
            // the crater follows the terrain instead of being stamped at one height
            float bowl = (float) Math.pow(1 - rn * rn, 0.85);
            int ref = Math.min(groundY, cy);
            int depth = Math.round(CRATER_DEPTH * b.k() * bowl + (noise(b, x, z, 0.17f, 3) - 0.5f) * 3f);
            int floor = Math.max(level.getMinY() + 1, ref - Math.max(1, depth));
            for (int y = surface; y > floor; y--) {                          // everything above the floor goes, trees and buildings too
                p.set(x, y, z);
                BlockState state = level.getBlockState(p);
                if (state.isAir() || state.getDestroySpeed(level, p) < 0) continue;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), SILENT);
                budget--;
            }
            // the floor: the rock that is there turns to glass, slag and baked stone; nothing is set in open air,
            // so a cave below simply opens up
            for (int y = floor; y > floor - 2 && y > level.getMinY(); y--) {
                p.set(x, y, z);
                BlockState state = level.getBlockState(p);
                if (state.isAir() || !state.getFluidState().isEmpty() && !state.is(Blocks.WATER) || state.getDestroySpeed(level, p) < 0) continue;
                BlockState glass = floorBlock(rn, h + y * 13, y == floor, b, x, z);
                if (glass.is(Blocks.LAVA) && level.getBlockState(p.below()).isAir()) glass = NukeContent.TRINITITE.defaultBlockState();
                level.setBlock(p, glass, SILENT);
                budget--;
            }
            if (air && rn > 0.45f && (h >>> 9) % 23 == 0) {
                p.set(x, floor + 1, z);
                if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()) level.setBlock(p, BaseFireBlock.getState(level, p), 3);
            }
        }
    }

    /** What the crater floor is made of at normalised radius rn. */
    private static BlockState floorBlock(float rn, int h, boolean top, Blast b, int x, int z) {
        int roll = (h >>> 4) % 100;
        float veins = noise(b, x, z, 0.33f, 4);                              // magma shows through in veins, not as a sheet
        if (rn < 0.10f && top && veins > 0.42f) return Blocks.LAVA.defaultBlockState();          // the melt pool at the very centre
        if (rn < 0.30f) {
            if (veins > 0.66f) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return roll < 62 ? NukeContent.TRINITITE.defaultBlockState() : roll < 80 ? Blocks.OBSIDIAN.defaultBlockState()
                    : roll < 93 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }
        if (rn < 0.62f) {
            if (veins > 0.74f) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return roll < 30 ? NukeContent.TRINITITE.defaultBlockState() : roll < 55 ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 75 ? Blocks.SMOOTH_BASALT.defaultBlockState() : roll < 92 ? NukeContent.SCORCHED_EARTH.defaultBlockState()
                    : Blocks.BASALT.defaultBlockState();
        }
        return roll < 42 ? NukeContent.SCORCHED_EARTH.defaultBlockState() : roll < 58 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                : roll < 72 ? Blocks.TUFF.defaultBlockState() : roll < 84 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                : roll < 94 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    private static BlockState rubble(int h) {
        int roll = (Rng.hash(h) >>> 4) % 100;
        return roll < 30 ? Blocks.COBBLESTONE.defaultBlockState() : roll < 50 ? Blocks.GRAVEL.defaultBlockState()
                : roll < 68 ? NukeContent.SCORCHED_EARTH.defaultBlockState() : roll < 84 ? Blocks.COARSE_DIRT.defaultBlockState()
                : roll < 94 ? Blocks.TUFF.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    }

    /** Outside the crater: burn off what grows, bake the soil, start fires, lay down ash. Weaker with distance. */
    private static void scorch(ServerLevel level, Blast b, int budget) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int cx = (int) Math.floor(b.x), cz = (int) Math.floor(b.z);
        boolean air = Environment.breathable(level, BlockPos.containing(b.x, b.y + 2, b.z));
        while (budget > 0 && b.scorch < SCORCH_COLUMNS) {
            int[] s = spiral(b.scorch);
            double r = Math.hypot(s[0], s[1]);
            if (r > SCORCH_RADIUS || r < CRATER_RADIUS * 0.55) { b.scorch++; continue; }
            int x = cx + s[0], z = cz + s[1];
            LevelChunk chunk = chunk(level, x, z);
            if (chunk == null) return;
            b.scorch++;
            budget--;
            float heat = (float) (1 - (r - CRATER_RADIUS) / (SCORCH_RADIUS - CRATER_RADIUS));     // 1 at the rim, 0 at the edge
            heat = Math.min(1.2f, heat + (noise(b, x, z, 0.07f, 5) - 0.5f) * 0.35f);
            if (heat <= 0) continue;
            int h = Rng.hash(x * 131 + z * 71 + b.seed);
            float roll = ((h >>> 5) & 1023) / 1023f;

            // strip the canopy: leaves, plants, snow, and (close in) the timber itself
            int y = Math.min(level.getMaxY() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
            for (int guard = 0; guard < 40 && y > level.getMinY(); guard++, y--) {
                BlockState state = level.getBlockState(p.set(x, y, z));
                if (state.isAir()) continue;
                boolean flimsy = state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                        || state.is(BlockTags.SNOW) || state.is(BlockTags.WOOL_CARPETS) || state.getCollisionShape(level, p).isEmpty() && state.getFluidState().isEmpty();
                boolean timber = state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_STAIRS)
                        || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOOL);
                if (flimsy && roll < heat * 1.6f || timber && roll < heat * 0.9f) { level.setBlock(p, Blocks.AIR.defaultBlockState(), SILENT); budget--; continue; }
                break;
            }
            BlockState ground = level.getBlockState(p.set(x, y, z));
            if (!ground.getFluidState().isEmpty()) {
                if (ground.getFluidState().is(FluidTags.WATER) && heat > 0.75f && roll < 0.5f) level.setBlock(p, Blocks.AIR.defaultBlockState(), SILENT);   // flashed to steam
                continue;
            }
            if (ground.is(BlockTags.ICE) && roll < heat * 1.5f) { level.setBlock(p, Blocks.WATER.defaultBlockState(), 3); continue; }
            boolean soil = ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.CLAY) || ground.is(Blocks.SNOW_BLOCK);
            if (soil && roll < heat * 1.25f) {
                level.setBlock(p, heat > 0.55f || (h & 1) == 0 ? NukeContent.SCORCHED_EARTH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), SILENT);
                budget--;
            } else if (ground.is(BlockTags.BASE_STONE_OVERWORLD) && heat > 0.7f && roll < 0.3f) {
                level.setBlock(p, Blocks.SMOOTH_BASALT.defaultBlockState(), SILENT);
            }
            // on top: a fire here and there, ash everywhere it can lie
            p.set(x, y + 1, z);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) continue;
            int top = (h >>> 16) & 255;
            if (air && top < 10 * heat) level.setBlock(p, BaseFireBlock.getState(level, p), 3);
            else if (top < 40 + 150 * Math.min(1, heat)) level.setBlock(p, NukeContent.FALLOUT_ASH.defaultBlockState(), SILENT);
        }
    }

    private static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(NukeContent.SCORCHED_EARTH) || s.is(NukeContent.TRINITITE)
                || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN)
                || !s.getFluidState().isEmpty() || s.getDestroySpeed(null, BlockPos.ZERO) < 0;
    }

    /**
     * The blast wave knocks down what stands on the ground as it passes: near the crater everything short of
     * obsidian-grade material goes, further out only timber, glass and the like, at the edge only glass and
     * leaves. Works outward behind the front ({@code front} blocks from ground zero), never ahead of it.
     */
    private static void flatten(ServerLevel level, Blast b, int budget, double front) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int cx = (int) Math.floor(b.x), cz = (int) Math.floor(b.z);
        double inner = CRATER_RADIUS * (0.45 + 0.55 * b.k()) * 0.9;
        while (budget > 0 && b.flat < FLAT_COLUMNS) {
            int[] s = spiral(b.flat);
            double r = Math.hypot(s[0], s[1]);
            if (r > FLATTEN_RADIUS || r < inner) { b.flat++; continue; }
            if (r > front) return;                                           // the wave has not got here yet
            int x = cx + s[0], z = cz + s[1];
            LevelChunk chunk = chunk(level, x, z);
            if (chunk == null) return;
            b.flat++;
            budget--;
            double sev = 1 - (r - inner) / (FLATTEN_RADIUS - inner);         // 1 at the rim, 0 at the edge
            sev = Math.max(0, Math.min(1, sev + (noise(b, x, z, 0.05f, 9) - 0.5) * 0.3));
            // what the wave can still break here, as blast resistance
            float limit = sev > 0.8 ? 600f : sev > 0.55 ? 7f : sev > 0.3 ? 3.1f : sev > 0.08 ? 0.9f : 0.35f;
            int top = Math.min(level.getMaxY() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
            for (int y = top, guard = 0; y > level.getMinY() && guard < 96; y--, guard++) {
                BlockState st = level.getBlockState(p.set(x, y, z));
                if (st.isAir()) continue;
                if (ground(st)) break;                                       // reached the natural ground
                if (st.getBlock().getExplosionResistance() <= limit && st.getDestroySpeed(level, p) >= 0) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), SILENT);
                    budget--;
                }
            }
        }
    }
}
