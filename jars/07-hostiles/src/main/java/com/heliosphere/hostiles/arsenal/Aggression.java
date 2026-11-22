package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.defense.mk.SentryBlockEntity;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What makes the machines press an attack instead of standing about.
 * <ul>
 *   <li>They notice a player from 128 blocks (the released machines only from 72, while natural deployments arrive
 *       56-88 blocks out: half of them never noticed anyone) and keep after them out to 160.</li>
 *   <li>A ground machine that cannot see its target does not stop at its firing distance and wait: after a second
 *       and a half without sight it closes right in until it can shoot.</li>
 *   <li>They shoot back at turrets: a machine fires on a working turret within reach (up to 48 blocks) when it cannot
 *       see the player or the turret is the nearer of the two, and otherwise one time in three; the turret takes the
 *       weapon's damage (see SentryBlockEntity.damage: knocked out at zero for 30 seconds). EMP spiders hit turrets
 *       three times as hard. Blasts hurt turrets too (EnemyArsenal.blast).</li>
 *   <li>Drop troops (the airborne sieges) fight in a frenzy for fifteen seconds after landing: half as fast again
 *       at the trigger and faster on their feet.</li>
 * </ul>
 */
public final class Aggression {
    private Aggression() {}

    public static final double NOTICE = 128, FORGET = 160;

    private static final class Mind { int noSight, turretCd = 30, frenzy; }
    private static final Map<TechMob, Mind> MINDS = new WeakHashMap<>();

    /** Shots fired at turrets since start (for tests). */
    public static int turretShots;

    /** For {@link com.heliosphere.hostiles.arsenal.mixin.TechAggroMixin}: cooldown access. */
    public interface Cooldown { int heliosphere$cooldown(); void heliosphere$cooldown(int ticks); }

    /** How close a ground machine walks before it stops to shoot, as a fraction of its range (released: 0.6). */
    public static double standoff(TechMob mob, double released) {
        Mind m = MINDS.get(mob);
        return m != null && m.noSight >= 30 ? 0.06 : released;
    }

    /** Dropped by pod and just landed: a frenzy. */
    public static void landed(TechMob mob) {
        MINDS.computeIfAbsent(mob, x -> new Mind()).frenzy = 300;
        mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1, false, false));
        if ((Object) mob instanceof Cooldown c) c.heliosphere$cooldown(Math.min(c.heliosphere$cooldown(), 10));
    }

    public static void tick(TechMob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isNoAi() || StrikeCraft.is(mob)) return;
        TechCombat.Role r = mob.role();
        if (r == null) return;
        Mind m = MINDS.computeIfAbsent(mob, x -> new Mind());
        LivingEntity t = mob.getTarget();
        if (t == null) { m.noSight = 0; return; }
        if (mob.tickCount % 5 == 0) m.noSight = mob.hasLineOfSight(t) ? 0 : m.noSight + 5;
        if (m.frenzy > 0) {                                           // half as fast again at the trigger
            m.frenzy--;
            if ((m.frenzy & 1) == 0 && (Object) mob instanceof Cooldown c && c.heliosphere$cooldown() > 1) c.heliosphere$cooldown(c.heliosphere$cooldown() - 1);
        }
        if (m.turretCd > 0) { m.turretCd--; return; }
        if (r.damage() <= 0) { m.turretCd = 100; return; }               // the repair automaton carries no weapon
        double reach = Math.min(48, Math.max(12, r.range()));
        Vec3 eye = mob.getEyePosition();
        SentryBlockEntity turret = SentryBlockEntity.nearest(level, eye, reach);
        if (turret == null) { m.turretCd = 20; return; }
        Vec3 at = Vec3.atCenterOf(turret.getBlockPos()).add(0, 0.3, 0);
        boolean nearer = at.distanceToSqr(eye) < t.distanceToSqr(mob);
        if (m.noSight == 0 && !nearer && level.random.nextInt(2) != 0) { m.turretCd = 20; return; }   // half the fire on turrets even with the player in sight
        boolean emp = "emp_spider".equals(r.id());
        boolean heavy = r.mode() == 2 || r.mode() == EnemyRoster.MORTAR || r.mode() == EnemyRoster.TITAN || r.mode() == EnemyRoster.PLASMA;
        CombatVfx.send(level, emp ? CombatVfx.BEAM : CombatVfx.TRACER, emp ? 0x80C0FF : 0xFF5030, eye, at, heavy ? 2.4f : 1.4f);
        if (heavy) CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, at, at, 0.9f);
        level.playSound(null, mob.blockPosition(), heavy ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.0f, heavy ? 1.5f : 1.2f);
        turret.damage(level, Math.max(4, r.damage()) * (emp ? 3f : heavy ? 1.5f : 1f) * (mob.getTags().contains("heliosphere.siege") ? 1.5f : 1f));
        turretShots++;
        m.turretCd = Math.max(25, (int) (r.interval() * 1.2f));
    }
}
