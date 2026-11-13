package com.heliosphere.planets.defense.sam;

import com.heliosphere.core.machine.EnergyNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * One air-defence vehicle. A radar sweeps for machines and other hostiles it can see and keeps them as tracks; a
 * launcher takes its radar's tracks (or, alone, what its own sensor sees), picks the most threatening target not
 * already engaged, raises or turns its launcher onto it, and fires when laid. Missiles reload slowly from grid power.
 */
public class SamBlockEntity extends BlockEntity implements EnergyNode {
    private static final Map<Level, Set<SamBlockEntity>> LOADED = new WeakHashMap<>();

    private long power;
    private int ammo = -1, reloadTicks, cooldown, idle, scanClock;
    private UUID owner;
    private final List<UUID> tracks = new ArrayList<>();
    private record Engagement(UUID missile, long since) {}
    private final Map<UUID, Engagement> engaged = new HashMap<>();
    private BlockPos radar;
    private UUID current;
    // shared with the client for drawing
    float raise, yaw, elev;
    float raiseO, yawO, elevO;
    long lastLaunch = -1000;
    int tube;
    boolean active;

    public SamBlockEntity(BlockPos pos, BlockState state) { super(SamSites.BLOCK_ENTITY, pos, state); }

    public SamKind kind() { return getBlockState().getBlock() instanceof SamBlock b ? b.kind : SamKind.BUK_TELAR; }
    public Direction facing() { return getBlockState().hasProperty(SamBlock.FACING) ? getBlockState().getValue(SamBlock.FACING) : Direction.SOUTH; }
    public int ammo() { return ammo < 0 ? kind().magazine : ammo; }
    void owner(UUID id) { owner = id; setChanged(); }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        LOADED.computeIfAbsent(level, l -> java.util.Collections.newSetFromMap(new WeakHashMap<>())).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    /** Vehicles loaded in this level whose centre is within {@code r} of {@code c}. */
    static List<SamBlockEntity> near(Level level, Vec3 c, double r) {
        List<SamBlockEntity> out = new ArrayList<>();
        Set<SamBlockEntity> all = LOADED.get(level);
        if (all == null) return out;
        for (SamBlockEntity v : new ArrayList<>(all))
            if (!v.isRemoved() && v.origin().distanceToSqr(c) <= r * r) out.add(v);
        return out;
    }

    // ---- energy ----
    @Override public long energy() { return power; }
    @Override public long capacity() { return kind().radar ? 20000 : kind().energy * 12L; }
    @Override public void setEnergy(long e) { power = Math.max(0, Math.min(capacity(), e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    // ---- geometry ----
    Vec3 origin() { return Vec3.atBottomCenterOf(worldPosition); }
    Vec3 world(Vec3 local) { return origin().add(SamKind.toWorld(local, facing())); }
    Vec3 sensor() { return world(new Vec3(0, kind().radar ? 4.2 : 3.2, kind().radar ? -0.5 : 1.5)); }

    // ---- server ----
    void tick(ServerLevel level) {
        SamKind k = kind();
        if (ammo < 0) { ammo = k.magazine; setChanged(); }
        raiseO = raise; yawO = yaw; elevO = elev;
        if (k.radar) { radarTick(level, k); return; }
        launcherTick(level, k);
    }

    private void radarTick(ServerLevel level, SamKind k) {
        boolean on = power >= 3;
        if (on) power -= 3;
        if (on != active) { active = on; sync(); }
        if (!on) { tracks.clear(); return; }
        if (++scanClock % 10 != 0) return;
        tracks.clear();
        Vec3 eye = sensor();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(k.radarRange))) {
            if (!AirTargets.is(m)) continue;                                    // air targets only
            Vec3 c = m.getBoundingBox().getCenter();
            if (c.distanceTo(eye) > k.radarRange) continue;
            if (!visible(level, eye, m)) continue;
            tracks.add(m.getUUID());
        }
    }

    private void launcherTick(ServerLevel level, SamKind k) {
        if (cooldown > 0) cooldown--;
        // reload from the grid
        // rearm from an ammunition crate nearby (as in War Thunder): no crate with our rounds, no reload
        if (ammo < k.magazine) {
            int time = Math.max(20, k.reload / 2);
            long per = Math.max(1, k.energy / Math.max(1, time));
            AmmoCrateBlockEntity crate = AmmoCrateBlockEntity.near(level, origin(), k.system);
            if (crate != null && power >= per) {
                power -= per;
                if (++reloadTicks >= time) { reloadTicks = 0; if (crate.take(k.system)) { ammo++; sync(); } }
            }
        }
        engaged.values().removeIf(e -> {
            Entity m = level.getEntity(e.missile);
            return m == null || !m.isAlive() || level.getGameTime() - e.since > 420;
        });
        // the battery: find a radar of this system nearby now and then
        if (radar == null || level.getGameTime() % 100 == 0) radar = findRadar(level, k);
        SamBlockEntity link = radar != null && level.getBlockEntity(radar) instanceof SamBlockEntity r && r.kind().radar && r.active ? r : null;
        double range = link != null ? k.linkedRange : k.ownRange;

        if (level.getGameTime() % 5 == 0) current = choose(level, k, link, range);
        Entity target = current == null ? null : level.getEntity(current);
        if (target != null && !target.isAlive()) { target = null; current = null; }

        // lay the launcher
        boolean wantUp = target != null && ammo > 0;
        if (wantUp) idle = 0; else idle++;
        float raiseGoal = wantUp || (raise > 0 && idle < 200) ? 1f : 0f;
        float before = raise;
        raise += Math.signum(raiseGoal - raise) * Math.min(Math.abs(raiseGoal - raise), 0.025f);
        boolean laid = raise >= 0.995f;
        if (k == SamKind.BUK_TELAR) {
            float wantYaw = 0, wantElev = (float) Math.toRadians(5);
            if (target != null) {
                Vec3 local = SamKind.toLocal(target.getBoundingBox().getCenter().subtract(world(SamKind.BUK_TURRET)), facing());
                wantYaw = (float) Math.atan2(local.x, local.z);
                double pitch = Math.atan2(local.y, Math.hypot(local.x, local.z));
                wantElev = (float) Math.max(Math.toRadians(18), Math.min(Math.toRadians(70), pitch + Math.toRadians(15)));
            }
            float dy = wrap(wantYaw - yaw);
            float step = (float) Math.toRadians(4);
            yaw = wrap(yaw + Math.max(-step, Math.min(step, dy)));
            elev += Math.max(-0.05f, Math.min(0.05f, wantElev - elev)) * Math.min(1f, raise * 2);
            laid = laid && Math.abs(dy) < Math.toRadians(4) && Math.abs(wantElev - elev) < 0.06;
        }
        if (Math.abs(raise - before) > 1e-4 || k == SamKind.BUK_TELAR && (Math.abs(yaw - yawO) > 1e-4 || Math.abs(elev - elevO) > 1e-4)) {
            if (level.getGameTime() % 2 == 0 || raise == raiseGoal) sync();
        }
        boolean nowActive = target != null;
        if (nowActive != active) { active = nowActive; sync(); }

        // fire
        if (target != null && laid && cooldown == 0 && ammo > 0) launch(level, k, target);
    }

    private SamBlockEntity linkedRadar(ServerLevel level) {
        return radar != null && level.getBlockEntity(radar) instanceof SamBlockEntity r && r.kind().radar ? r : null;
    }

    private BlockPos findRadar(ServerLevel level, SamKind k) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (SamBlockEntity be : LOADED.getOrDefault(level, Set.of())) {
            if (be.isRemoved() || !be.kind().radar || !be.kind().system.equals(k.system)) continue;
            double d = be.worldPosition.distSqr(worldPosition);
            if (d <= SamKind.LINK * SamKind.LINK && d < bestD) { bestD = d; best = be.worldPosition; }
        }
        return best;
    }

    /** The most threatening target in range that no missile of ours is already chasing. */
    private UUID choose(ServerLevel level, SamKind k, SamBlockEntity link, double range) {
        if (range <= 0) return null;
        Vec3 site = origin();
        List<Entity> candidates = new ArrayList<>();
        if (link != null) {
            for (UUID id : link.tracks) { Entity e = level.getEntity(id); if (e != null) candidates.add(e); }
        } else {
            Vec3 eye = sensor();
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(range)))
                if (AirTargets.is(m) && visible(level, eye, m)) candidates.add(m);
        }
        UUID best = null;
        double bestScore = 0;
        for (Entity e : candidates) {
            if (!e.isAlive() || engaged.containsKey(e.getUUID()) && !e.getUUID().equals(current)) continue;
            if (engaged.containsKey(e.getUUID())) continue;
            if (GuidedMissile.hunted(level, e.getUUID())) continue;                 // a missile from another launcher or turret has it
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(site);
            if (d > range || d < 6) continue;
            boolean air = e.isNoGravity() || c.y > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ()) + 3;
            Vec3 v = e.getDeltaMovement();
            double closing = Math.max(0, -v.dot(c.subtract(site).normalize()));
            double hp = e instanceof Mob m ? m.getMaxHealth() : 20;
            double score = (air ? 3 : 1) * (1 + closing * 20) * (1 + hp / 300) / (1 + d / 50);
            if (score > bestScore) { bestScore = score; best = e.getUUID(); }
        }
        return best;
    }

    private void launch(ServerLevel level, SamKind k, Entity target) {
        int i = tube % Math.max(1, k.magazine);
        Vec3 mouth = world(k.mouth(i, yaw, elev, raise));
        Vec3 axis = SamKind.toWorld(k.axis(yaw, elev, raise), facing());
        Player p = owner == null ? null : level.getPlayerByUUID(owner);
        GuidedMissile m = GuidedMissile.launch(level, k.missile, p, GuidedMissile.Side.DEFENCE, target, mouth, axis, k.damage, 0);
        engaged.put(target.getUUID(), new Engagement(m.getUUID(), level.getGameTime()));
        ammo--;
        tube++;
        cooldown = k.interval;
        lastLaunch = level.getGameTime();
        current = null;
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 4f, 0.5f);
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.5f, k == SamKind.SAMP_TEL ? 1.6f : 0.6f);
        sync();
    }

    private static boolean visible(ServerLevel level, Vec3 from, Mob m) {
        AABB b = m.getBoundingBox();
        for (Vec3 p : new Vec3[]{b.getCenter(), new Vec3(b.getCenter().x, b.maxY - 0.1, b.getCenter().z)})
            if (clear(level, from, p)) return true;
        return false;
    }

    /**
     * Line of sight over up to 500 blocks without loading anything: a vanilla ray cast would load (or generate) every
     * chunk on the way and stall the server. The ray is marched in half-block steps through chunks already in memory;
     * a chunk that is not loaded counts as blocking.
     */
    static boolean clear(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1) return true;
        int n = (int) (len * 2);
        net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        net.minecraft.world.level.chunk.LevelChunk chunk = null;
        int ccx = Integer.MIN_VALUE, ccz = Integer.MIN_VALUE, lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            at.set(a.x + d.x * t, a.y + d.y * t, a.z + d.z * t);
            if (at.getX() == lx && at.getY() == ly && at.getZ() == lz) continue;
            lx = at.getX(); ly = at.getY(); lz = at.getZ();
            if (level.isOutsideBuildHeight(ly)) continue;
            int cx = lx >> 4, cz = lz >> 4;
            if (cx != ccx || cz != ccz) { chunk = level.getChunkSource().getChunkNow(cx, cz); ccx = cx; ccz = cz; }
            if (chunk == null) return false;
            BlockState s = chunk.getBlockState(at);
            if (!s.isAir() && !s.getCollisionShape(level, at).isEmpty()) return false;
        }
        return true;
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    void status(Player p) {
        SamKind k = kind();
        if (power < 3) p.displayClientMessage(Component.translatable("message.heliosphere.sam_no_power", (int) FieldBatteryBlockEntity.RANGE), false);
        if (k.radar) {
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_radar_status", Component.translatable("block.heliosphere." + k.id),
                    active ? Component.translatable("message.heliosphere.sam_on") : Component.translatable("message.heliosphere.sam_off"),
                    tracks.size(), k.radarRange, power, capacity()), true);
            return;
        }
        if (ammo() < k.magazine && level != null) {
            AmmoCrateBlockEntity crate = AmmoCrateBlockEntity.near(level, origin(), k.system);
            p.displayClientMessage(crate == null ? Component.translatable("message.heliosphere.sam_no_crate", Component.translatable("item.heliosphere." + SamSites.roundOf(k.system)), (int) AmmoCrateBlockEntity.RANGE)
                    : Component.translatable("message.heliosphere.sam_rearming", crate.count(k.system)), false);
        }
        boolean linked = level instanceof ServerLevel sl && linkedRadar(sl) != null;
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_launcher_status", Component.translatable("block.heliosphere." + k.id),
                ammo(), k.magazine, linked ? Component.translatable("message.heliosphere.sam_linked") : Component.translatable("message.heliosphere.sam_alone"),
                linked ? k.linkedRange : k.ownRange, power, capacity()), true);
    }

    // ---- sync and save ----
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    // client-side smoothing between updates: the launcher moves at the server's rates towards the last known pose
    private float shownRaise, shownYaw, shownElev;
    private long shownAt;

    /** Raise, turret yaw and elevation to draw this frame (client). */
    public float[] view() {
        long now = System.nanoTime();
        float ticks = shownAt == 0 ? 1000 : Math.min(5f, (now - shownAt) / 5.0e7f);
        if (shownAt == 0) { shownRaise = raise; shownYaw = yaw; shownElev = elev; }
        shownAt = now;
        shownRaise += Math.signum(raise - shownRaise) * Math.min(Math.abs(raise - shownRaise), 0.025f * ticks);
        float dy = wrap(yaw - shownYaw);
        shownYaw = wrap(shownYaw + Math.signum(dy) * Math.min(Math.abs(dy), (float) Math.toRadians(4) * ticks));
        shownElev += Math.signum(elev - shownElev) * Math.min(Math.abs(elev - shownElev), 0.05f * ticks);
        return new float[]{shownRaise, shownYaw, shownElev};
    }

    public boolean active() { return active; }
    public long lastLaunch() { return lastLaunch; }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        power = in.getLongOr("power", 0);
        ammo = in.getIntOr("ammo", -1);
        reloadTicks = in.getIntOr("reload", 0);
        raise = in.getFloatOr("raise", 0);
        yaw = in.getFloatOr("yaw", 0);
        elev = in.getFloatOr("elev", 0);
        lastLaunch = in.getLongOr("launch", -1000);
        tube = in.getIntOr("tube", 0);
        active = in.getBooleanOr("active", false);
        String o = in.getStringOr("owner", "");
        owner = o.isEmpty() ? null : UUID.fromString(o);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("power", power);
        out.putInt("ammo", ammo);
        out.putInt("reload", reloadTicks);
        out.putFloat("raise", raise);
        out.putFloat("yaw", yaw);
        out.putFloat("elev", elev);
        out.putLong("launch", lastLaunch);
        out.putInt("tube", tube);
        out.putBoolean("active", active);
        if (owner != null) out.putString("owner", owner.toString());
    }
}
