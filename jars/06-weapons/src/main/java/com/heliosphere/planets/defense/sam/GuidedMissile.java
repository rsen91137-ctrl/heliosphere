package com.heliosphere.planets.defense.sam;

import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A guided missile flown tick by tick.
 *
 * <p>Motor: an optional delay (cold launch), a boost and a sustain phase, then a coast; drag always acts.
 * Guidance: proportional navigation, a = N·(Ω × v), where Ω is the turn rate of the line of sight; or pure pursuit,
 * which steers straight at the target; missiles with {@code mixed} guidance pursue slow targets and use
 * proportional navigation on fast ones. Limits: the commanded acceleration is capped by the overload the missile
 * can pull at its present speed, the autopilot follows the command with a lag, thrust vectoring helps only while the
 * motor burns, hard turns cost speed, and the seeker loses a target outside its cone or hidden behind blocks for too
 * long. A proximity fuse fires at the closest approach.
 */
public class GuidedMissile extends Entity {
    public enum Side { DEFENCE, MACHINE }

    private static final EntityDataAccessor<Integer> SPEC = SynchedEntityData.defineId(GuidedMissile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(GuidedMissile.class, EntityDataSerializers.INT);

    /** Called when a missile goes off: lets other modules break blocks (machine warheads, see 07). */
    public interface BlastHook { void blast(ServerLevel level, GuidedMissile missile, Vec3 at); }
    public static final List<BlastHook> BLAST_HOOKS = new ArrayList<>();

    private UUID owner, target;
    private Side side = Side.DEFENCE;
    private float damage = 20;
    private int tier;
    private double speed;
    private Vec3 dir = new Vec3(0, 1, 0), lateral = Vec3.ZERO, lastTargetPos, memoryPos;
    private int hidden, offCone, age;
    private boolean pursuitOnly;

    public GuidedMissile(EntityType<? extends GuidedMissile> type, Level level) { super(type, level); }

    // ---- launching ---------------------------------------------------------------------------------------------

    /**
     * Launches a missile. {@code target} may be null: the missile then flies out and its seeker looks for a target
     * within {@code spec.seekRange} (fire and forget).
     */
    public static GuidedMissile launch(ServerLevel level, MissileSpec spec, Entity owner, Side side, Entity target, Vec3 from, Vec3 dir,
                                       float damage, int tier) {
        GuidedMissile m = new GuidedMissile(SamSites.MISSILE, level);
        m.setSpec(spec);
        m.owner = owner == null ? null : owner.getUUID();
        m.target = target == null ? null : target.getUUID();
        m.side = side;
        m.damage = damage;
        m.tier = tier;
        m.dir = dir.normalize();
        m.speed = spec.launchSpeed;
        m.setPos(from);
        m.face();
        level.addFreshEntity(m);
        return m;
    }

    public GuidedMissile pursuitOnly() { pursuitOnly = true; return this; }

    public MissileSpec spec() { int i = entityData.get(SPEC); MissileSpec[] v = MissileSpec.values(); return v[Math.max(0, Math.min(v.length - 1, i))]; }
    private void setSpec(MissileSpec s) { entityData.set(SPEC, s.ordinal()); }
    /** Ticks since launch (synchronised, for the motor plume and the model). */
    public int flightAge() { return entityData.get(AGE); }
    public int motor() { return spec().motor(flightAge()); }
    public boolean boosterAttached() { return spec() == MissileSpec.ASTER_30 && flightAge() < spec().ignite + spec().boost; }
    public UUID target() { return target; }
    public Side side() { return side; }
    public int tier() { return tier; }
    public Entity owner() { return owner == null || !(level() instanceof ServerLevel sl) ? null : sl.getEntity(owner); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SPEC, 0);
        b.define(AGE, 0);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 512 * 512; }
    @Override protected void readAdditionalSaveData(ValueInput in) { }
    @Override protected void addAdditionalSaveData(ValueOutput out) { }

    // ---- flight ------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        MissileSpec spec = spec();
        age++;
        entityData.set(AGE, age);
        if (age > spec.life) {
            if (spec == MissileSpec.NUKE_ROCKET) { com.heliosphere.planets.weapon.Nuke.detonate(level, owner(), position()); discard(); }
            else if (spec.guided()) detonate(level, position(), null);
            else discard();
            return;
        }

        if (spec == MissileSpec.ASTER_30 && age == spec.ignite + spec.boost) dropBooster(level);

        Entity t = target == null ? null : level.getEntity(target);
        if (t != null && (!t.isAlive() || t.isRemoved())) { t = null; target = null; }
        if (t == null && spec.seekRange > 0 && age >= spec.guideStart && age % 4 == 0) t = seek(level, spec);

        Vec3 vel = dir.scale(speed);
        Vec3 cmd = Vec3.ZERO;
        if (spec.guided() && age >= spec.guideStart) {
            if (t != null) {
                Vec3 aim = t.getBoundingBox().getCenter();
                Vec3 tVel = lastTargetPos == null ? Vec3.ZERO : aim.subtract(lastTargetPos);
                lastTargetPos = aim;
                Vec3 r = aim.subtract(position());
                double dist = r.length();
                // seeker cone
                double cosHalf = Math.cos(Math.toRadians(spec.fov / 2.0));
                if (dist > 1e-3 && r.scale(1 / dist).dot(dir) < cosHalf) { if (++offCone > 3) { lose(t); t = null; } }
                else offCone = 0;
                // line of sight (terrain masking)
                if (t != null && age % 2 == 0) {
                    boolean clear = level.clip(new ClipContext(position(), aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
                    if (clear) { hidden = 0; memoryPos = null; }
                    else if ((hidden += 2) > spec.memory) { lose(t); t = null; }
                    else if (memoryPos == null) memoryPos = aim.add(tVel.scale(4));
                }
                if (t != null) {
                    Vec3 aimPoint = hidden > 0 && memoryPos != null ? memoryPos : aim;
                    Vec3 rr = aimPoint.subtract(position());
                    boolean pursuit = pursuitOnly || (spec.mixed && tVel.length() < 0.35);
                    cmd = pursuit ? pursuit(rr, vel) : proNav(spec, rr, hidden > 0 ? Vec3.ZERO : tVel, vel);
                    if (side == Side.MACHINE && t instanceof ServerPlayer sp && age % 10 == 0) warn(level, sp, dist);
                }
            }
            // gravity compensation keeps a guided missile from sagging below its course
            cmd = cmd.add(0, spec.gravity, 0);
            cmd = cmd.subtract(dir.scale(cmd.dot(dir)));
            // available overload: lift grows with the square of speed; thrust vectoring adds while the motor burns
            double q = Math.min(1.0, (speed / spec.vRef) * (speed / spec.vRef));
            double aMax = spec.gLimit * q + (spec.motor(age) > 0 ? spec.tvc * Math.max(0.3, speed) * 0.5 : 0);
            double c = cmd.length();
            if (c > aMax && c > 0) cmd = cmd.scale(aMax / c);
            lateral = lateral.add(cmd.subtract(lateral).scale(1.0 / Math.max(1.0, spec.tau)));
        } else {
            lateral = Vec3.ZERO;
        }

        // speed: thrust, parasitic drag, and the drag of manoeuvring
        double lat = lateral.length();
        speed += spec.thrust(age) - spec.drag * speed * speed - 0.35 * lat * lat / Math.max(0.4, speed);
        speed = Math.max(0.05, speed);
        Vec3 v = dir.scale(speed).add(lateral).add(0, -spec.gravity, 0);
        double vl = v.length();
        if (vl > 1e-6) { dir = v.scale(1 / vl); speed = vl; }
        Vec3 from = position(), to = from.add(v);

        // fuse: closest approach to the target (or, for a seeker missile, to anything it may hit) along this step
        if (spec.guided() && age >= spec.guideStart) {
            Entity hit = fuseCheck(level, spec, t, from, to);
            if (hit != null) { detonate(level, closest(from, to, hit.getBoundingBox().getCenter()), hit); return; }
        }
        if (spec == MissileSpec.BUK_9M317MA || spec == MissileSpec.ASTER_30 || spec == MissileSpec.IRIS_T_SL) {
            // a battery reaches 500 blocks, past the area players keep running: keep the missile's own chunk ticking
            if (age % 10 == 0) level.getChunkSource().addTicketWithRadius(SAM_TICKET, new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(to.add(v.scale(10)))), 2);
        }
        if (spec == MissileSpec.NUKE_ROCKET) {
            // keep the way ahead loaded (without stalling the server: the tickets load in the background)
            Vec3 ahead = to.add(v.scale(12));
            level.getChunkSource().addTicketWithRadius(FLIGHT_TICKET, new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(ahead)), 2);
            level.getChunkSource().addTicketWithRadius(FLIGHT_TICKET, new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(to)), 1);
            for (Entity e : level.getEntities(this, new AABB(from, to).inflate(1.5), x -> x instanceof LivingEntity && x.isAlive() && !x.getUUID().equals(owner)))
                if (e.getBoundingBox().inflate(0.8).clip(from, to).isPresent()) { nukeAt(level, e.getBoundingBox().getCenter()); return; }
        }
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS && age > 2) {
            if (spec == MissileSpec.NUKE_ROCKET) nukeAt(level, block.getLocation());
            else if (spec.guided()) detonate(level, block.getLocation(), null);
            else discard();
            return;
        }
        setPos(to);
        setDeltaMovement(v);
        face();
        if (age % 3 == 0 && spec.motor(age) > 0)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 0.6f, 0.5f + (float) speed * 0.05f);
    }

    private static final net.minecraft.server.level.TicketType SAM_TICKET = new net.minecraft.server.level.TicketType(40L,
            net.minecraft.server.level.TicketType.FLAG_LOADING | net.minecraft.server.level.TicketType.FLAG_SIMULATION);
    private static final net.minecraft.server.level.TicketType FLIGHT_TICKET = new net.minecraft.server.level.TicketType(60L, net.minecraft.server.level.TicketType.FLAG_LOADING);

    private void nukeAt(ServerLevel level, Vec3 at) {
        com.heliosphere.planets.weapon.Nuke.detonate(level, owner(), at);
        discard();
    }

    /** Proportional navigation: N times the line-of-sight turn rate, crossed with the missile's velocity. */
    private static Vec3 proNav(MissileSpec spec, Vec3 r, Vec3 tVel, Vec3 vel) {
        double r2 = r.lengthSqr();
        if (r2 < 1e-6) return Vec3.ZERO;
        Vec3 rel = tVel.subtract(vel);
        Vec3 omega = r.cross(rel).scale(1.0 / r2);
        Vec3 a = omega.cross(vel).scale(spec.nav);
        // when the target is far off the nose (just after launch), add a pull towards it so the missile turns in
        Vec3 los = r.normalize(), d = vel.normalize();
        double off = 1 - los.dot(d);
        if (off > 0.05) a = a.add(los.subtract(d.scale(los.dot(d))).scale(vel.length() * off * 0.6));
        return a;
    }

    /** Pure pursuit: steer the velocity straight at the target. */
    private static Vec3 pursuit(Vec3 r, Vec3 vel) {
        double s = vel.length();
        if (s < 1e-6 || r.lengthSqr() < 1e-6) return Vec3.ZERO;
        return r.normalize().subtract(vel.scale(1 / s)).scale(s * 0.6);
    }

    private void lose(Entity t) {
        if (side == Side.MACHINE && t instanceof ServerPlayer sp && sp.isAlive() && age > 20)
            com.heliosphere.core.progress.Achievements.grant(sp, "dodge");
        target = null;
        lastTargetPos = null;
        memoryPos = null;
        hidden = 0;
        offCone = 0;
    }

    private Entity seek(ServerLevel level, MissileSpec spec) {
        double cosHalf = Math.cos(Math.toRadians(Math.min(spec.fov, 90) / 2.0));
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(spec.seekRange))) {
            if (!prey(e)) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(position());
            double d = to.length();
            if (d > spec.seekRange || d < 1e-3) continue;
            double cos = to.scale(1 / d).dot(dir);
            if (cos < cosHalf) continue;
            double score = d * (2 - cos);
            if (score < bestScore && level.clip(new ClipContext(position(), e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
                bestScore = score; best = e;
            }
        }
        if (best != null) target = best.getUUID();
        return best;
    }

    /** Whether the seeker may pick {@code e} by itself: monsters for the defence, everyone else for the machines. */
    public boolean prey(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e.getUUID().equals(owner)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (side == Side.DEFENCE) return e instanceof Enemy;
        return !(e instanceof Enemy);                                      // machines do not shoot each other
    }

    /**
     * Whether the warhead hurts {@code e}. Besides its prey, a missile always hurts what it was sent after (a player
     * can lock onto any creature), and a hand-held launcher's warhead hurts everything in the blast but the shooter
     * and the shooter's own pets; a battery or turret missile spares animals, villagers and players.
     */
    public boolean hostile(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e.getUUID().equals(owner)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (prey(e)) return true;
        if (target != null && e.getUUID().equals(target)) return true;
        if (side == Side.DEFENCE && spec() == MissileSpec.PLAYER)
            return !(e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() != null && o.getOwner().getUUID().equals(owner));
        return false;
    }

    private Entity fuseCheck(ServerLevel level, MissileSpec spec, Entity t, Vec3 from, Vec3 to) {
        if (t != null && distToSegment(from, to, t.getBoundingBox().getCenter()) <= spec.fuse + t.getBbWidth() * 0.5) return t;
        // flying into something it may hurt sets it off whatever the seeker was after
        for (Entity e : level.getEntities(this, new AABB(from, to).inflate(1.5), this::hostile))
            if (e.getBoundingBox().inflate(0.3).clip(from, to).isPresent() || e.getBoundingBox().inflate(0.3).contains(to)) return e;
        if (age < spec.guideStart + 4) return null;
        AABB box = new AABB(from, to).inflate(spec.fuse * 0.6);
        for (Entity e : level.getEntities(this, box, this::prey))
            if (distToSegment(from, to, e.getBoundingBox().getCenter()) <= spec.fuse * 0.6 + e.getBbWidth() * 0.5) return e;
        return null;
    }

    private static double distToSegment(Vec3 a, Vec3 b, Vec3 p) { return p.distanceTo(closest(a, b, p)); }

    private static Vec3 closest(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        if (l2 < 1e-9) return a;
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / l2));
        return a.add(ab.scale(t));
    }

    private void face() {
        setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        setXRot((float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, dir.y)))));
    }

    private void dropBooster(ServerLevel level) {
        GuidedMissile b = new GuidedMissile(SamSites.MISSILE, level);
        b.setSpec(MissileSpec.ASTER_BOOSTER);
        b.side = side;
        b.dir = dir;
        b.speed = speed * 0.8;
        b.setPos(position().subtract(dir.scale(1.6)));
        b.face();
        level.addFreshEntity(b);
        CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFE0B0, position(), position(), 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 2f, 0.6f);
    }

    private static void warn(ServerLevel level, ServerPlayer p, double dist) {
        p.displayClientMessage(Component.translatable("message.heliosphere.missile_warning", (int) dist).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 0.8f, dist < 24 ? 2f : 1.6f);
    }

    // ---- warhead -----------------------------------------------------------------------------------------------

    private void detonate(ServerLevel level, Vec3 at, Entity direct) {
        MissileSpec spec = spec();
        double radius = side == Side.MACHINE ? MachineWarheads.hurtRadius(tier, spec.blast) : spec.blast;
        float dmg = damage * (side == Side.MACHINE ? MachineWarheads.damageScale(tier) : 1);
        Entity src = owner();
        DamageSource source = level.damageSources().explosion(this, src instanceof LivingEntity le ? le : null);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
            if (!hostile(e)) continue;
            double d = e.getBoundingBox().getCenter().distanceTo(at);
            if (d > radius) continue;
            float k = (float) Math.pow(1 - d / radius, 0.7);
            if (e == direct) k = Math.max(k, 1f);
            e.invulnerableTime = 0;
            e.hurtServer(level, source, dmg * k);
            Vec3 push = e.position().subtract(at).normalize().scale(0.8 * k);
            e.push(push.x, 0.3 * k, push.z);
            e.hurtMarked = true;
        }
        float size = (float) Math.max(1.2, radius * 0.45);
        CombatVfx.send(level, CombatVfx.EXPLOSION, side == Side.MACHINE ? 0xFF5030 : 0xFFC070, at, at, size);
        if (radius > 7) CombatVfx.send(level, CombatVfx.SHOCK, 0xFFD0A0, at, at, (float) radius * 1.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, (float) Math.min(8, 1.5 + radius * 0.3), radius > 7 ? 0.55f : 0.9f);
        for (BlastHook h : BLAST_HOOKS) h.blast(level, this, at);
        discard();
    }
}
