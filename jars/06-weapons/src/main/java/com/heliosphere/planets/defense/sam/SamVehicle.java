package com.heliosphere.planets.defense.sam;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * One air-defence vehicle, an entity from the moment it is set down: it stands on the ground under gravity, drives
 * like a boat does (the driver's own game moves it, so the ride is smooth), and while parked its radar sweeps for
 * targets or its launcher engages them. A radar keeps contacts as tracks; a launcher takes its radar's tracks (or,
 * alone, what its own sensor sees), picks the most threatening target not already engaged, raises or turns its
 * launcher onto it and fires when laid. Missiles come from an ammunition crate nearby, power from a battery pack.
 *
 * <p>Left-click with an empty hand takes the hatch, right-click opens the control panel, the recovery wrench packs
 * it up. W/A/S/D drive (the launcher is stowed first), Shift leaves.
 */
public class SamVehicle extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RAISE = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURRET = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ELEV = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DRIVING = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> LAUNCH = SynchedEntityData.defineId(SamVehicle.class, EntityDataSerializers.LONG);

    private static final Map<Level, Set<SamVehicle>> LOADED = new WeakHashMap<>();
    /** Chunks kept loaded around a parked vehicle (radius, in chunks): it keeps watch while its owner is far away. */
    static final int FORCE = 2;

    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

    // ---- server state ----
    private long power;
    private int reloadTicks, cooldown, idle, scanClock, moveClock, tube;
    private UUID owner, manualTarget, current, radar;
    private final List<UUID> tracks = new ArrayList<>();
    /** Manual mode: the operator picks the target and gives the order to fire. */
    private boolean manual;
    /** Identification friend or foe: on, only hostile machines and monsters; off, players too. */
    private boolean iff = true;
    private boolean fireQueued, registered;
    /** Set down in creative mode: needs no power. */
    private boolean creative;
    public void creative(boolean on) { creative = on; if (on) power = capacity(); }
    private record Engagement(UUID missile, long since) {}
    private final Map<UUID, Engagement> engaged = new HashMap<>();
    private ChunkPos forcedAt;
    private float raise, turret, elev;

    // ---- driving (on the driver's client) ----
    private float speed, deltaRotation;

    public SamVehicle(EntityType<? extends SamVehicle> type, Level level) { super(type, level); }

    /** A new vehicle facing {@code yaw}, with what it carried when it was packed (missiles, charge, mode) if anything. */
    void setup(SamKind kind, float yaw, CompoundTag carry, UUID owner) {
        entityData.set(KIND, kind.ordinal());
        setYRot(yaw);
        yRotO = yaw;
        this.owner = owner;
        entityData.set(AMMO, kind.magazine);
        if (carry != null) restore(carry);
    }

    /** Sets a vehicle down where {@code pos} is, facing {@code yaw} (the item, old saves and tests use this). */
    public static SamVehicle place(ServerLevel level, SamKind kind, Vec3 pos, float yaw, UUID owner, CompoundTag carry, net.minecraft.world.entity.EntitySpawnReason why) {
        SamVehicle v = SamSites.VEHICLE.create(level, why);
        if (v == null) return null;
        v.setup(kind, yaw, carry, owner);
        v.snapTo(pos.x, pos.y, pos.z, yaw, 0);
        level.addFreshEntity(v);
        return v;
    }

    public SamKind kind() { int k = entityData.get(KIND); return SamKind.values()[Math.max(0, Math.min(SamKind.values().length - 1, k))]; }
    public int ammo() { return entityData.get(AMMO); }
    private void setAmmo(int a) { entityData.set(AMMO, Math.max(0, Math.min(kind().magazine, a))); }
    public boolean active() { return entityData.get(ACTIVE); }
    public boolean driving() { return entityData.get(DRIVING); }
    public long lastLaunch() { return entityData.get(LAUNCH); }
    public UUID owner() { return owner; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0); b.define(AMMO, 0); b.define(RAISE, 0f); b.define(TURRET, 0f); b.define(ELEV, (float) Math.toRadians(5));
        b.define(ACTIVE, false); b.define(DRIVING, false); b.define(LAUNCH, -1000L);
    }

    // ---- an entity that is a vehicle ----
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }   // packed up with the wrench
    @Override public boolean isAttackable() { return false; }
    @Override public boolean skipAttackInteraction(Entity attacker) { return true; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public float getPickRadius() { return Math.max(0, (kind().length - kind().width) * 0.5f); }   // the length of the hull
    @Override public boolean canBeCollidedWith(Entity other) { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public float maxUpStep() { return 1.05f; }
    @Override public boolean fireImmune() { return true; }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e instanceof Player; }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float scale) {
        return SamOperation.hatch(kind()).yRot((float) Math.toRadians(-getYRot()));
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction f) {
        super.positionRider(passenger, f);
        if (deltaRotation != 0) {                       // the driver's view turns with the vehicle
            passenger.setYRot(passenger.getYRot() + deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        SamKind k = kind();
        for (double side : new double[]{1, -1}) {                      // beside the cab, either side
            Vec3 at = world(new Vec3(side * (k.width * 0.5 + 0.9), 0, k.length * 0.5 - 1.5));
            BlockPos b = BlockPos.containing(at.x, getY() + 1.5, at.z);
            for (int i = 0; i < 5; i++, b = b.below()) {
                BlockState below = level().getBlockState(b.below());
                if (!below.getCollisionShape(level(), b.below()).isEmpty() && level().getBlockState(b).getCollisionShape(level(), b).isEmpty()
                        && level().getBlockState(b.above()).getCollisionShape(level(), b.above()).isEmpty())
                    return new Vec3(at.x, b.getY() + below.getCollisionShape(level(), b.below()).max(net.minecraft.core.Direction.Axis.Y) - 1, at.z);
            }
        }
        return super.getDismountLocationForPassenger(p);
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        if (e instanceof ServerPlayer sp) SamOperation.left(sp);
    }

    // ---- geometry: vehicle frame (+Z ahead, blocks) to world ----
    Vec3 origin() { return position(); }
    Vec3 world(Vec3 local) { return position().add(local.yRot((float) Math.toRadians(-getYRot()))); }
    Vec3 toWorldDir(Vec3 local) { return local.yRot((float) Math.toRadians(-getYRot())); }
    Vec3 toLocalDir(Vec3 w) { return w.yRot((float) Math.toRadians(getYRot())); }
    Vec3 sensor() { return world(new Vec3(0, kind().radar ? 4.2 : 3.2, kind().radar ? -0.5 : 1.5)); }

    // ---- energy (fed by battery packs nearby) ----
    public long energy() { return power; }
    public long capacity() { return kind().radar ? 20000 : kind().energy * 12L; }
    public void setEnergy(long e) { power = Math.max(0, Math.min(capacity(), e)); }

    /** Vehicles loaded in this level within {@code r} of {@code c}. */
    public static List<SamVehicle> near(Level level, Vec3 c, double r) {
        List<SamVehicle> out = new ArrayList<>();
        Set<SamVehicle> all = LOADED.get(level);
        if (all == null) return out;
        for (SamVehicle v : new ArrayList<>(all)) if (!v.isRemoved() && v.position().distanceToSqr(c) <= r * r) out.add(v);
        return out;
    }

    /** Whether another parked vehicle still keeps this chunk loaded. */
    static boolean anotherNear(Level level, SamVehicle self, int cx, int cz) {
        Set<SamVehicle> all = LOADED.get(level);
        if (all == null) return false;
        for (SamVehicle v : new ArrayList<>(all)) {
            if (v == self || v.isRemoved() || v.forcedAt == null) continue;
            if (Math.abs(v.forcedAt.x - cx) <= FORCE && Math.abs(v.forcedAt.z - cz) <= FORCE) return true;
        }
        return false;
    }

    // ---- tick ----
    @Override
    public void tick() {
        super.tick();
        interpolation.interpolate();
        if (isLocalInstanceAuthoritative()) {
            drive();
            move(MoverType.SELF, getDeltaMovement());
        } else setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel sl) serverTick(sl);
    }

    /** Tracks and wheels: gentle acceleration, turning on the spot, gravity. The launcher must be stowed first. */
    private void drive() {
        float fwd = 0, turn = 0;
        if (getControllingPassenger() instanceof Player p && entityData.get(RAISE) < 0.01f) {
            fwd = Math.signum(p.zza);
            turn = -Math.signum(p.xxa);                           // A (left) turns left: yaw down
        }
        float target = fwd > 0 ? 0.32f : fwd < 0 ? -0.12f : 0f;
        speed += Math.max(-0.03f, Math.min(0.012f, target - speed));
        if (target == 0 && Math.abs(speed) < 0.012f) speed = 0;
        deltaRotation = turn * 2.6f * (fwd < 0 ? -1 : 1);
        setYRot(getYRot() + deltaRotation);
        double r = Math.toRadians(getYRot());
        double vy = onGround() ? -0.04 : Math.max(-2.5, getDeltaMovement().y - 0.08);
        setDeltaMovement(-Math.sin(r) * speed, vy, Math.cos(r) * speed);
    }

    private void serverTick(ServerLevel level) {
        if (!registered) { LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this); registered = true; }
        SamKind k = kind();
        if (creative) power = capacity();
        ServerPlayer op = getFirstPassenger() instanceof ServerPlayer sp ? sp : null;
        Input in = op == null ? Input.EMPTY : op.getLastClientInput();
        if (in.forward() || in.backward() || in.left() || in.right()) moveClock = 40;
        else if (moveClock > 0) moveClock--;
        boolean driving = moveClock > 0;
        if (driving != driving()) entityData.set(DRIVING, driving);
        if (k.radar) radarTick(level, k, driving); else launcherTick(level, k, op, driving);
        entityData.set(RAISE, raise); entityData.set(TURRET, turret); entityData.set(ELEV, elev);
        if (!driving && tickCount % 20 == 0) keepLoaded(level);
        if (op != null && tickCount % 3 == 0) SamOperation.picture(op, this, level);
    }

    private void radarTick(ServerLevel level, SamKind k, boolean driving) {
        boolean on = !driving && power >= 3;                    // the antenna is folded on the move
        if (on) power -= 3;
        if (on != active()) entityData.set(ACTIVE, on);
        if (!on) { tracks.clear(); return; }
        if (++scanClock % 10 != 0) return;
        tracks.clear();
        Vec3 eye = sensor();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(k.radarRange))) {
            if (!AirTargets.is(m)) continue;                                    // air targets only
            if (m.getBoundingBox().getCenter().distanceTo(eye) > k.radarRange || !visible(level, eye, m)) continue;
            tracks.add(m.getUUID());
        }
        for (ServerPlayer pl : level.players())                             // every player it can see (IFF sorts them out)
            if (trackable(pl) && pl.getBoundingBox().getCenter().distanceTo(eye) <= k.radarRange && visible(level, eye, pl))
                tracks.add(pl.getUUID());
    }

    private boolean trackable(Player pl) {
        return pl.isAlive() && !pl.isSpectator() && !pl.isCreative() && pl.getVehicle() != this;
    }

    private void launcherTick(ServerLevel level, SamKind k, ServerPlayer op, boolean driving) {
        if (cooldown > 0) cooldown--;
        // rearm from an ammunition crate nearby (as in War Thunder): no crate with our rounds, no reload
        if (ammo() < k.magazine && !driving) {
            int time = Math.max(20, k.reload / 2);
            long per = Math.max(1, k.energy / Math.max(1, time));
            AmmoCrateBlockEntity crate = AmmoCrateBlockEntity.near(level, origin(), k.system);
            if (crate != null && power >= per) {
                power -= per;
                if (++reloadTicks >= time) { reloadTicks = 0; if (crate.take(k.system)) setAmmo(ammo() + 1); }
            }
        }
        engaged.values().removeIf(e -> {
            Entity m = level.getEntity(e.missile);
            return m == null || !m.isAlive() || level.getGameTime() - e.since > 420;
        });
        if (radar == null || level.getGameTime() % 100 == 0) radar = findRadar(level, k);
        SamVehicle link = linkedRadar(level);
        double range = link != null ? k.linkedRange : k.ownRange;

        if (driving) { current = null; fireQueued = false; }
        else if (level.getGameTime() % 5 == 0) current = manual ? manualPick(level, range) : choose(level, k, link, range);
        Entity target = current == null ? null : level.getEntity(current);
        if (target != null && !target.isAlive()) { target = null; current = null; }

        // in manual mode the Buk's operator lays the turret by looking: any bearing, 0-85 degrees
        Player aimer = !driving && manual && k == SamKind.BUK_TELAR ? op : null;

        boolean wantUp = !driving && (target != null || aimer != null) && ammo() > 0;
        if (wantUp) idle = 0; else idle++;
        float raiseGoal = !driving && (wantUp || raise > 0 && idle < 200) ? 1f : 0f;
        raise += Math.signum(raiseGoal - raise) * Math.min(Math.abs(raiseGoal - raise), driving ? 0.05f : 0.025f);   // stowed quicker to drive off
        boolean laid = raise >= 0.995f;
        if (k == SamKind.BUK_TELAR) {
            float wantYaw = 0, wantElev = (float) Math.toRadians(5);
            if (aimer != null) {
                Vec3 local = toLocalDir(Vec3.directionFromRotation(aimer.getXRot(), aimer.getYRot()));
                wantYaw = (float) Math.atan2(local.x, local.z);
                wantElev = (float) Math.max(0, Math.min(Math.toRadians(85), Math.atan2(local.y, Math.hypot(local.x, local.z))));
            } else if (target != null) {
                Vec3 local = toLocalDir(target.getBoundingBox().getCenter().subtract(world(SamKind.BUK_TURRET)));
                wantYaw = (float) Math.atan2(local.x, local.z);
                double pitch = Math.atan2(local.y, Math.hypot(local.x, local.z));
                wantElev = (float) Math.max(Math.toRadians(18), Math.min(Math.toRadians(70), pitch + Math.toRadians(15)));
            }
            float dy = wrap(wantYaw - turret);
            float step = (float) Math.toRadians(aimer != null ? 9 : 4);
            turret = wrap(turret + Math.max(-step, Math.min(step, dy)));
            elev += Math.max(-0.09f, Math.min(0.09f, wantElev - elev)) * Math.min(1f, raise * 2);
            laid = laid && Math.abs(dy) < Math.toRadians(4) && Math.abs(wantElev - elev) < 0.06;
        }
        boolean nowActive = target != null;
        if (nowActive != active()) entityData.set(ACTIVE, nowActive);

        if (driving) return;
        if (aimer != null) {                                   // the operator fires where the turret points (guided onto the selection, if any)
            if (fireQueued && raise >= 0.995f && cooldown == 0 && ammo() > 0) { launch(level, k, target, op); fireQueued = false; }
        } else if (target != null && laid && cooldown == 0 && ammo() > 0 && (!manual || fireQueued)) { launch(level, k, target, manual ? op : null); fireQueued = false; }
    }

    // ---- operated by hand ----

    private UUID manualPick(ServerLevel level, double range) {
        if (manualTarget == null) return null;
        Entity e = level.getEntity(manualTarget);
        if (e == null || !e.isAlive() || e.getBoundingBox().getCenter().distanceTo(origin()) > Math.max(range, 64) || !engages(e, true)) {
            manualTarget = null; fireQueued = false; return null;
        }
        return manualTarget;
    }

    /** What this vehicle can engage or show right now: its radar's tracks (or its own, for a radar), else its own sensor's. */
    public List<Entity> sensed(ServerLevel level) {
        SamKind k = kind();
        List<Entity> out = new ArrayList<>();
        if (k.radar) { for (UUID id : tracks) { Entity e = level.getEntity(id); if (e != null && e.isAlive()) out.add(e); } return out; }
        SamVehicle link = linkedRadar(level);
        if (link != null) { for (UUID id : link.tracks) { Entity e = level.getEntity(id); if (e != null && e.isAlive()) out.add(e); } }
        else if (k.ownRange > 0) out.addAll(ownSensor(level, k.ownRange));
        out.removeIf(e -> !engages(e, manual));
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin())));
        return out;
    }

    private List<Entity> ownSensor(ServerLevel level, double range) {
        List<Entity> out = new ArrayList<>();
        Vec3 eye = sensor();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(range)))
            if (AirTargets.is(m) && visible(level, eye, m)) out.add(m);
        for (ServerPlayer pl : level.players())
            if (trackable(pl) && pl.distanceTo(this) <= range && visible(level, eye, pl)) out.add(pl);
        return out;
    }

    /**
     * Whether this vehicle may engage {@code e}. Players only with IFF off, never in creative or spectator mode and
     * never its own operator. By hand the operator may lock any player it sees, on the ground or in the air; the
     * automatic fire control takes only players in the air, and spares the vehicle's owner.
     */
    boolean engages(Entity e, boolean byHand) {
        if (!(e instanceof Player pl)) return true;
        if (iff || !trackable(pl)) return false;
        if (byHand) return true;
        return !pl.getUUID().equals(owner) && AirTargets.airbornePlayer(pl);
    }

    public boolean iff() { return iff; }
    public void setIff(boolean on) { iff = on; manualTarget = null; fireQueued = false; }
    public boolean manual() { return manual; }
    public void setManual(boolean on) { manual = on; manualTarget = null; fireQueued = false; }
    public void select(UUID target) { manualTarget = target; fireQueued = false; }
    public UUID selected() { return manualTarget; }

    /** The operator's order to fire at the selected target (the launcher lays on it first). */
    public boolean order() {
        if (kind().radar || ammo() <= 0) return false;
        if (manualTarget == null && kind() != SamKind.BUK_TELAR) return false;          // the Buk can fire where its turret points
        fireQueued = true;
        return true;
    }

    private SamVehicle linkedRadar(ServerLevel level) {
        return radar != null && level.getEntity(radar) instanceof SamVehicle r && r.kind().radar && r.active()
                && r.distanceToSqr(this) <= SamKind.LINK * SamKind.LINK ? r : null;
    }

    private UUID findRadar(ServerLevel level, SamKind k) {
        UUID best = null;
        double bestD = Double.MAX_VALUE;
        for (SamVehicle v : LOADED.getOrDefault(level, Set.of())) {
            if (v.isRemoved() || !v.kind().radar || !v.kind().system.equals(k.system)) continue;
            double d = v.distanceToSqr(this);
            if (d <= SamKind.LINK * SamKind.LINK && d < bestD) { bestD = d; best = v.getUUID(); }
        }
        return best;
    }

    /** The most threatening target in range that no missile of ours (or anyone's) is already chasing. */
    private UUID choose(ServerLevel level, SamKind k, SamVehicle link, double range) {
        if (range <= 0) return null;
        Vec3 site = origin();
        List<Entity> candidates = new ArrayList<>();
        if (link != null) { for (UUID id : link.tracks) { Entity e = level.getEntity(id); if (e != null) candidates.add(e); } }
        else candidates.addAll(ownSensor(level, range));
        UUID best = null;
        double bestScore = 0;
        for (Entity e : candidates) {
            if (!e.isAlive() || !engages(e, false)) continue;
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

    private void launch(ServerLevel level, SamKind k, Entity target, Player firer) {
        int i = tube % Math.max(1, k.magazine);
        Vec3 mouth = world(k.mouth(i, turret, elev, raise));
        Vec3 axis = toWorldDir(k.axis(turret, elev, raise));
        Entity from = firer != null ? firer : owner == null ? null : level.getPlayerByUUID(owner);
        GuidedMissile m = GuidedMissile.launch(level, k.missile, from, GuidedMissile.Side.DEFENCE, target, mouth, axis, k.damage, 0);
        if (target != null) engaged.put(target.getUUID(), new Engagement(m.getUUID(), level.getGameTime()));
        setAmmo(ammo() - 1);
        tube++;
        cooldown = k.interval;
        entityData.set(LAUNCH, level.getGameTime());
        current = null;
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 4f, 0.5f);
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.5f, k == SamKind.SAMP_TEL ? 1.6f : 0.6f);
    }

    private static boolean visible(ServerLevel level, Vec3 from, Entity m) {
        AABB b = m.getBoundingBox();
        for (Vec3 p : new Vec3[]{b.getCenter(), new Vec3(b.getCenter().x, b.maxY - 0.1, b.getCenter().z)})
            if (SamBlockEntity.clear(level, from, p)) return true;
        return false;
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    // ---- chunks kept loaded while parked ----
    private void keepLoaded(ServerLevel level) {
        ChunkPos here = chunkPosition();
        if (here.equals(forcedAt)) return;
        ChunkPos old = forcedAt;
        forcedAt = here;
        for (int dx = -FORCE; dx <= FORCE; dx++) for (int dz = -FORCE; dz <= FORCE; dz++) level.setChunkForced(here.x + dx, here.z + dz, true);
        if (old != null) release(level, old);
    }

    private void release(ServerLevel level, ChunkPos c) {
        for (int dx = -FORCE; dx <= FORCE; dx++) for (int dz = -FORCE; dz <= FORCE; dz++) {
            int cx = c.x + dx, cz = c.z + dz;
            if (forcedAt != null && Math.abs(forcedAt.x - cx) <= FORCE && Math.abs(forcedAt.z - cz) <= FORCE) continue;
            if (!anotherNear(level, this, cx, cz)) level.setChunkForced(cx, cz, false);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!(level() instanceof ServerLevel sl)) return;
        Set<SamVehicle> set = LOADED.get(sl);
        if (set != null) set.remove(this);
        if (reason.shouldDestroy() && forcedAt != null) { ChunkPos c = forcedAt; forcedAt = null; release(sl, c); }
    }

    // ---- hands on ----
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level() instanceof ServerLevel)) return InteractionResult.SUCCESS;
        if (p.getItemInHand(hand).getItem() instanceof SamWrenchItem) { pack(p); return InteractionResult.SUCCESS; }
        status(p);
        if (p instanceof ServerPlayer sp) SamOperation.panel(sp, this);
        return InteractionResult.SUCCESS;
    }

    /** The recovery wrench: the vehicle goes into the inventory with its missiles and charge. */
    void pack(Player p) {
        ItemStack out = new ItemStack(SamSites.VEHICLE_ITEMS.get(kind()));
        CompoundTag t = new CompoundTag();
        t.putInt("sam_ammo", ammo());
        t.putLong("sam_power", power);
        out.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_packed", out.getHoverName(), ammo(), power).withStyle(ChatFormatting.GREEN), true);
        ejectPassengers();
        discard();
        if (!p.getInventory().add(out)) p.drop(out, false);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
    }

    void status(Player p) {
        SamKind k = kind();
        if (power < 3) p.displayClientMessage(Component.translatable("message.heliosphere.sam_no_power", (int) FieldBatteryBlockEntity.RANGE), false);
        if (k.radar) {
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_radar_status", Component.translatable("block.heliosphere." + k.id),
                    active() ? Component.translatable("message.heliosphere.sam_on") : Component.translatable("message.heliosphere.sam_off"),
                    tracks.size(), k.radarRange, power, capacity()), true);
            return;
        }
        if (ammo() < k.magazine) {
            AmmoCrateBlockEntity crate = AmmoCrateBlockEntity.near(level(), origin(), k.system);
            p.displayClientMessage(crate == null ? Component.translatable("message.heliosphere.sam_no_crate", Component.translatable("item.heliosphere." + SamSites.roundOf(k.system)), (int) AmmoCrateBlockEntity.RANGE)
                    : Component.translatable("message.heliosphere.sam_rearming", crate.count(k.system)), false);
        }
        boolean linked = level() instanceof ServerLevel sl && linkedRadar(sl) != null;
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_launcher_status", Component.translatable("block.heliosphere." + k.id),
                ammo(), k.magazine, linked ? Component.translatable("message.heliosphere.sam_linked") : Component.translatable("message.heliosphere.sam_alone"),
                linked ? k.linkedRange : k.ownRange, power, capacity()), true);
    }

    /** What a packed vehicle carries: missiles, charge, mode, owner. */
    public CompoundTag carry() {
        CompoundTag t = new CompoundTag();
        t.putInt("sam_ammo", ammo()); t.putLong("sam_power", power); t.putBoolean("sam_manual", manual); t.putBoolean("sam_iff", iff);
        if (owner != null) t.putString("sam_owner", owner.toString());
        return t;
    }

    public void restore(CompoundTag t) {
        setAmmo(t.getIntOr("sam_ammo", kind().magazine));
        power = Math.max(0, Math.min(capacity(), t.getLongOr("sam_power", 0)));
        manual = t.getBooleanOr("sam_manual", manual);
        iff = t.getBooleanOr("sam_iff", iff);
        String o = t.getStringOr("sam_owner", "");
        if (!o.isEmpty()) try { owner = UUID.fromString(o); } catch (IllegalArgumentException ignored) {}
    }

    // ---- client: the launcher moves at the server's rates towards the last known pose ----
    private float shownRaise, shownTurret, shownElev;
    private long shownAt;

    /** Raise, turret yaw and elevation to draw this frame. */
    public float[] view() {
        float raiseS = entityData.get(RAISE), turretS = entityData.get(TURRET), elevS = entityData.get(ELEV);
        long now = System.nanoTime();
        float ticks = shownAt == 0 ? 1000 : Math.min(5f, (now - shownAt) / 5.0e7f);
        if (shownAt == 0) { shownRaise = raiseS; shownTurret = turretS; shownElev = elevS; }
        shownAt = now;
        shownRaise += Math.signum(raiseS - shownRaise) * Math.min(Math.abs(raiseS - shownRaise), 0.025f * ticks);
        float dy = wrap(turretS - shownTurret);
        shownTurret = wrap(shownTurret + Math.signum(dy) * Math.min(Math.abs(dy), (float) Math.toRadians(9) * ticks));
        shownElev += Math.signum(elevS - shownElev) * Math.min(Math.abs(elevS - shownElev), 0.09f * ticks);
        return new float[]{shownRaise, shownTurret, shownElev};
    }

    /** Whether the launcher is still being stowed before the vehicle can move off. */
    public boolean stowing() { return entityData.get(RAISE) >= 0.01f; }

    // ---- save ----
    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(KIND, in.getIntOr("kind", 0));
        power = in.getLongOr("power", 0);
        entityData.set(AMMO, in.getIntOr("ammo", kind().magazine));
        manual = in.getBooleanOr("manual", false);
        iff = in.getBooleanOr("iff", true);
        reloadTicks = in.getIntOr("reload", 0);
        raise = in.getFloatOr("raise", 0); turret = in.getFloatOr("turret", 0); elev = in.getFloatOr("elev", (float) Math.toRadians(5));
        tube = in.getIntOr("tube", 0);
        String o = in.getStringOr("owner", "");
        owner = o.isEmpty() ? null : UUID.fromString(o);
        creative = in.getBooleanOr("creative", false);
        if (in.getBooleanOr("forced", false)) forcedAt = new ChunkPos(in.getIntOr("fx", 0), in.getIntOr("fz", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("kind", entityData.get(KIND));
        out.putLong("power", power);
        out.putInt("ammo", ammo());
        out.putBoolean("manual", manual);
        out.putBoolean("iff", iff);
        out.putInt("reload", reloadTicks);
        out.putFloat("raise", raise); out.putFloat("turret", turret); out.putFloat("elev", elev);
        out.putInt("tube", tube);
        if (owner != null) out.putString("owner", owner.toString());
        if (creative) out.putBoolean("creative", true);
        if (forcedAt != null) { out.putBoolean("forced", true); out.putInt("fx", forcedAt.x); out.putInt("fz", forcedAt.z); }
    }
}
