package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MissileSpec;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The two strike aircraft, which fight from beyond visual range instead of closing in like the other machines.
 * <ul>
 *   <li>The VIII strike drone climbs to about seventy blocks above its target and circles it two hundred blocks out,
 *       firing a stand-off missile every eight seconds or so.</li>
 *   <li>The fixed-wing attack jet never hovers: it flies at constant speed with a limited turn rate, runs in at its
 *       target, releases a pair of missiles from about 230 blocks, breaks away to 330 blocks and comes round again.</li>
 * </ul>
 * Their missiles ({@link MissileSpec#STANDOFF}) need no line of sight at launch, warn the player like every machine
 * missile and are shot down by air defence. The aircraft only strike a player who is out under the open sky or in
 * their line of sight: a roof is cover. They keep their own patch of the world running so they do not freeze in the
 * air when they are out past the players' simulation distance, and a player's air defence sees and shoots them there.
 */
public final class StrikeCraft {
    private StrikeCraft() {}

    public static final String DRONE = "strike_drone", JET = "strike_jet";

    /** Running area around each aircraft: its own chunk and the eight around it tick entities. */
    private static final TicketType TICKET = new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);

    /** Players beyond this are not hunted; natural aircraft beyond it from every player leave. */
    public static final double ACQUIRE = 400;
    /** Natural strike aircraft near one player at a time; more are recalled when they arrive. */
    private static final int CAP = 2;

    /** Diagnostics for the world test. */
    public static int launched;
    public static double lastLaunchRange;

    private static final class Brain {
        UUID target;
        Vec3 home, vel = Vec3.ZERO, heading;
        int missileCd = 80, phase, phaseTime, retarget;
        double orbit = 1;
        int salvo;
    }

    private static final Map<TechMob, Brain> BRAINS = new WeakHashMap<>();

    public static boolean is(TechMob mob) {
        TechCombat.Role r = mob.role();
        return r != null && (DRONE.equals(r.id()) || JET.equals(r.id()));
    }

    public static boolean isId(String id) { return DRONE.equals(id) || JET.equals(id); }

    /** Starts the patch of world around a newly deployed aircraft, which may be out where nothing runs. */
    public static void wake(ServerLevel level, Vec3 at) {
        level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(BlockPos.containing(at)), 3);
    }

    private static boolean exempt(TechMob mob) { return EnemyDays.exempt(mob) || mob.hasCustomName(); }

    /** After the released machine logic: takes over the flight and the stand-off attack. */
    public static void tick(TechMob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isNoAi() || !is(mob)) return;
        Brain b = BRAINS.get(mob);
        if (b == null) {
            b = new Brain();
            b.home = mob.position();
            b.heading = Vec3.directionFromRotation(0, mob.getYRot()).multiply(1, 0, 1).normalize();
            b.orbit = level.random.nextBoolean() ? 1 : -1;
            BRAINS.put(mob, b);
            if (!exempt(mob) && crowded(level, mob)) { mob.discard(); return; }
        }
        boolean jet = JET.equals(mob.role().id());

        LivingEntity target = target(level, mob, b);
        boolean playersNear = target != null || level.getNearestPlayer(mob, ACQUIRE + 100) != null;
        if (playersNear && mob.tickCount % 4 == 0)
            level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(mob.blockPosition()), 3);

        Vec3 want = jet ? jet(level, mob, b, target) : drone(level, mob, b, target);
        b.vel = want;
        mob.setDeltaMovement(want);
        if (b.heading != null) {
            float yaw = (float) (Mth.atan2(b.heading.z, b.heading.x) * Mth.RAD_TO_DEG) - 90f;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
        }
        if (b.missileCd > 0) b.missileCd--;
        mob.fallDistance = 0;
    }

    // ---- targets ------------------------------------------------------------------------------------------------

    private static boolean huntable(LivingEntity e) {
        return e instanceof ServerPlayer p && p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    private static LivingEntity target(ServerLevel level, TechMob mob, Brain b) {
        LivingEntity t = b.target == null ? null : (level.getEntity(b.target) instanceof LivingEntity le ? le : null);
        if (t != null && (!huntable(t) || t.distanceToSqr(mob) > (ACQUIRE + 80) * (ACQUIRE + 80))) t = null;
        boolean fortress = mob.getTags().contains("heliosphere.fortress");
        if (t != null && fortress && t.position().distanceToSqr(b.home) > 420 * 420) t = null;   // fortress craft do not leave their post
        if (t == null && --b.retarget <= 0) {
            b.retarget = 20;
            LivingEntity siege = mob.getTarget();
            if (siege != null && huntable(siege)) t = siege;
            double best = ACQUIRE * ACQUIRE;
            if (t == null) for (ServerPlayer p : level.players()) {
                if (!huntable(p)) continue;
                double d = p.distanceToSqr(mob);
                if (fortress && p.position().distanceToSqr(b.home) > 420 * 420) continue;
                if (d < best) { best = d; t = p; }
            }
        }
        b.target = t == null ? null : t.getUUID();
        return t;
    }

    private static boolean crowded(ServerLevel level, TechMob mob) {
        int n = 0;
        for (TechMob m : level.getEntitiesOfClass(TechMob.class, mob.getBoundingBox().inflate(ACQUIRE), x -> x != mob && x.isAlive() && is(x)))
            n++;
        return n >= CAP;
    }

    /** Why the last missile was allowed (for the world test). */
    public static String lastReason = "";

    /** Whether a missile can be sent at the target: it is out under the sky, or in plain sight. */
    private static boolean exposed(ServerLevel level, TechMob mob, LivingEntity t) {
        // nothing over the head (heightmap, not sky light: light lags behind a roof just built)
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(t.getX()), Mth.floor(t.getZ()));
        if (t.getEyeY() >= top) { lastReason = "open sky (eye " + (int) t.getEyeY() + " >= top " + top + ")"; return true; }
        var hit = level.clip(new ClipContext(mob.position(), t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        lastReason = "sight: " + hit.getType() + " at " + hit.getBlockPos().toShortString() + " from " + mob.blockPosition().toShortString() + " to " + BlockPos.containing(t.getEyePosition()).toShortString();
        return hit.getType() == HitResult.Type.MISS;
    }

    // ---- flight -------------------------------------------------------------------------------------------------

    /** The ground (or treetops) below a point, if that chunk is loaded; never loads one. */
    private static int ground(ServerLevel level, double x, double z, int fallback) {
        ChunkAccess c = level.getChunkSource().getChunkNow(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        if (c == null) return fallback;
        return c.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x) & 15, Mth.floor(z) & 15);
    }

    /** Height to fly at: {@code above} over the target, but always clear of the ground ahead and under the build limit. */
    private static double altitude(ServerLevel level, TechMob mob, Vec3 ahead, double wanted) {
        int g = Math.max(ground(level, mob.getX(), mob.getZ(), mob.getBlockY() - 30), ground(level, ahead.x, ahead.z, mob.getBlockY() - 30));
        return Mth.clamp(Math.max(wanted, g + 24), level.getMinY() + 8, level.getMaxY() - 10);
    }

    private static Vec3 flat(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    private static Vec3 turnToward(Vec3 heading, Vec3 want, double maxDeg) {
        double a = Math.atan2(heading.z, heading.x), w = Math.atan2(want.z, want.x);
        double d = Mth.wrapDegrees(Math.toDegrees(w - a));
        d = Mth.clamp(d, -maxDeg, maxDeg);
        double n = a + Math.toRadians(d);
        return new Vec3(Math.cos(n), 0, Math.sin(n));
    }

    private static Vec3 drone(ServerLevel level, TechMob mob, Brain b, LivingEntity t) {
        Vec3 pos = mob.position();
        Vec3 centre = t != null ? t.position() : b.home;
        double radius = t != null ? 190 : 48, speed = t != null ? 0.75 : 0.45;
        Vec3 rel = flat(pos.subtract(centre));
        double d = rel.length();
        Vec3 out = d < 1e-3 ? new Vec3(1, 0, 0) : rel.scale(1 / d);
        Vec3 tangent = new Vec3(-out.z * b.orbit, 0, out.x * b.orbit);
        Vec3 dir = tangent.add(out.scale(Mth.clamp((radius - d) / 40, -1.5, 1.5))).normalize();
        b.heading = turnToward(b.heading == null ? dir : b.heading, dir, 4);
        Vec3 h = b.heading.scale(speed);
        double alt = altitude(level, mob, pos.add(h.scale(30)), centre.y + (t != null ? 70 : 40));
        double vy = Mth.clamp((alt - pos.y) * 0.05, -0.3, 0.35);
        if (mob.horizontalCollision) vy = 0.4;

        if (t != null && b.missileCd <= 0) {
            double range = t.distanceTo(mob);
            if (range <= 320 && range >= 40 && exposed(level, mob, t)) {
                launch(level, mob, t, b.heading);
                b.missileCd = 150 + level.random.nextInt(50);
            } else b.missileCd = 20;
        }
        return new Vec3(h.x, vy, h.z);
    }

    private static Vec3 jet(ServerLevel level, TechMob mob, Brain b, LivingEntity t) {
        Vec3 pos = mob.position();
        if (b.heading == null) b.heading = new Vec3(1, 0, 0);
        double speed = 1.3, wantY;
        b.phaseTime++;
        if (t == null) {                                                      // loiter: a wide circle round home
            b.heading = turnToward(b.heading, new Vec3(-b.heading.z * b.orbit, 0, b.heading.x * b.orbit), 1.2);
            Vec3 back = flat(b.home.subtract(pos));
            if (back.lengthSqr() > 160 * 160) b.heading = turnToward(b.heading, back.normalize(), 3);
            wantY = b.home.y + 50;
            speed = 1.0;
        } else {
            Vec3 to = flat(t.position().subtract(pos));
            double d = to.length();
            Vec3 toN = d < 1e-3 ? b.heading : to.scale(1 / d);
            if (b.phase == 0) {                                               // run in
                b.heading = turnToward(b.heading, toN, 3.5);
                wantY = t.getY() + 45;
                double off = Math.toDegrees(Math.acos(Mth.clamp(b.heading.dot(toN), -1, 1)));
                if (b.missileCd <= 0 && d <= 235 && d >= 60 && off < 25) {
                    if (exposed(level, mob, t)) {
                        launch(level, mob, t, b.heading);
                        b.salvo = 1;
                        b.missileCd = 300;
                        b.phase = 1; b.phaseTime = 0;
                    } else b.missileCd = 20;
                }
                if (d < 70 && b.phaseTime > 40) { b.phase = 1; b.phaseTime = 0; }  // overflew without a shot: go round
            } else {                                                          // break away and extend
                b.heading = turnToward(b.heading, toN.scale(-1).add(new Vec3(-toN.z, 0, toN.x).scale(0.35 * b.orbit)).normalize(), 3.0);
                wantY = t.getY() + 60;
                speed = 1.55;
                if ((d > 330 && b.phaseTime > 60) || b.phaseTime > 400) { b.phase = 0; b.phaseTime = 0; b.orbit = -b.orbit; }
            }
            if (b.salvo > 0 && b.salvo < 2 && b.phaseTime == 6) { launch(level, mob, t, b.heading); b.salvo = 2; }
        }
        Vec3 h = b.heading.scale(speed);
        double alt = altitude(level, mob, pos.add(h.scale(30)), wantY);
        double vy = Mth.clamp((alt - pos.y) * 0.04, -0.45, 0.5);
        if (mob.horizontalCollision) vy = 0.8;
        return new Vec3(h.x, vy, h.z);
    }

    // ---- weapons ------------------------------------------------------------------------------------------------

    private static void launch(ServerLevel level, TechMob mob, LivingEntity t, Vec3 heading) {
        TechCombat.Role r = mob.role();
        Vec3 side = new Vec3(-heading.z, 0, heading.x).scale(level.random.nextBoolean() ? 0.9 : -0.9);
        Vec3 from = mob.position().add(side).add(0, -0.3, 0);
        Vec3 dir = heading.scale(0.6).add(t.getEyePosition().subtract(from).normalize()).add(0, 0.15, 0);
        GuidedMissile.launch(level, MissileSpec.STANDOFF, mob, GuidedMissile.Side.MACHINE, t, from, dir, r.damage(), EnemyRoster.tier(r.id()))
                .carrierSpeed(mob.getDeltaMovement().length());                // dropped from a moving aircraft, it keeps the aircraft's speed
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 4.0f, 0.5f);
        launched++;
        lastLaunchRange = t.distanceTo(mob);
        if (t instanceof ServerPlayer p)
            p.displayClientMessage(Component.translatable("message.heliosphere.standoff_launch", Component.translatable(mob.getType().getDescriptionId()), (int) lastLaunchRange)
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /** Close in, the aircraft use their cannon: a four-round burst (from the released attack, if anything comes that near). */
    public static void cannon(TechMob mob, ServerLevel level, LivingEntity target, float damage) {
        for (int i = 0; i < 4; i++) {
            final int n = i;
            Runnable shot = () -> {
                if (!mob.isAlive() || !target.isAlive()) return;
                Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.3, 0);
                Vec3 to = target.getEyePosition().add(level.random.nextGaussian() * 0.5, level.random.nextGaussian() * 0.35 - 0.2, level.random.nextGaussian() * 0.5);
                com.heliosphere.planets.tech.TechProjectile.launch(level, mob, target, from, to, 1, damage * 0.35f);
            };
            if (n == 0) shot.run(); else EnemyArsenal.later(level, n * 3, shot);
        }
    }
}
