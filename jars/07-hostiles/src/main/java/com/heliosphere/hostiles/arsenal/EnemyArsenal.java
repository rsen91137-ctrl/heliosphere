package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import com.heliosphere.planets.tech.TechProjectile;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The weapons of the machines. The released ones (modes 0-6) keep their attack and get follow-ups here; the
 * new ones (modes 7-13, see {@link EnemyRoster}) are implemented entirely here. Every weapon that can kill
 * telegraphs first: the lance draws its aim line before it fires, mortar rounds mark where they will land,
 * and the swarm drones whine as they dive.
 */
public final class EnemyArsenal implements ModInitializer {
    private record Task(long due, Runnable run) {}
    private static final Map<ServerLevel, List<Task>> TASKS = new WeakHashMap<>();
    private static boolean spawningSwarm;

    @Override
    public void onInitialize() {
        EnemyDays.init();
        ServerTickEvents.END_WORLD_TICK.register(EnemyArsenal::tick);
        Missiles.init();
    }

    private static void later(ServerLevel level, int ticks, Runnable r) {
        TASKS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Task(level.getGameTime() + ticks, r));
    }

    private static void tick(ServerLevel level) {
        List<Task> list = TASKS.get(level);
        if (list != null && !list.isEmpty()) {
            long now = level.getGameTime();
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = list.iterator(); it.hasNext(); ) {
                Task t = it.next();
                if (t.due <= now) { due.add(t); it.remove(); }
            }
            for (Task t : due) {
                try { t.run.run(); } catch (RuntimeException ignored) { }
            }
        }
        if (level.getGameTime() % 10 == 0) cloak(level);
        if (level.getGameTime() % 20 == 0) bossBars(level);
        if (level.getGameTime() % 1200 == 600) outerPatrols(level);
    }

    private static TechCombat.Role role(TechMob mob) { return mob.role(); }

    private static boolean sees(ServerLevel level, Vec3 from, Entity target) {
        return level.clip(new ClipContext(from, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target))
                .getType() == HitResult.Type.MISS;
    }

    private static boolean shootable(LivingEntity e) {
        return e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Area damage with linear falloff; machines are spared their own blasts. */
    private static void blast(ServerLevel level, TechMob mob, Vec3 at, float radius, float damage) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
            if (e instanceof TechMob || !shootable(e)) continue;
            double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(at);
            if (d > radius) continue;
            float k = (float) (1 - d / radius);
            e.hurtServer(level, mob != null && mob.isAlive() ? level.damageSources().mobAttack(mob) : level.damageSources().explosion(null, null), damage * (0.35f + 0.65f * k));
            Vec3 push = e.position().subtract(at).normalize().scale(0.6 * k);
            e.push(push.x, 0.25 * k, push.z);
            e.hurtMarked = true;
        }
    }

    // ---- new weapons --------------------------------------------------------------------------------------------

    /** Returns true when this overlay handled the attack (the released code must not run). */
    public static boolean fire(TechMob mob, ServerLevel level, LivingEntity target, Vec3 aim) {
        TechCombat.Role r = role(mob);
        if (r != null && r.mode() == 3) {                                  // missiles: the new guided missile replaces the old round
            Vec3 from = mob.getEyePosition().add(0, 0.3, 0);
            Missiles.fire(level, mob, target, from, target.getEyePosition().subtract(from).normalize().add(0, 0.6, 0), r.damage());
            later(level, 8, () -> {                                        // and its pair
                if (!mob.isAlive() || !target.isAlive()) return;
                Vec3 f2 = mob.getEyePosition().add(0, 0.3, 0);
                Missiles.fire(level, mob, target, f2, target.getEyePosition().subtract(f2).normalize().add(0.15, 0.6, -0.15), r.damage());
            });
            return true;
        }
        if (r == null || r.mode() < EnemyRoster.FLAME) return false;
        Vec3 eye = mob.getEyePosition();
        switch (r.mode()) {
            case EnemyRoster.FLAME -> flame(mob, level, target, r);
            case EnemyRoster.LANCE -> lance(mob, level, target, r);
            case EnemyRoster.MORTAR -> mortar(mob, level, target, r);
            case EnemyRoster.KAMIKAZE -> {
                if (mob.distanceTo(target) < 2.8f) detonate(mob, level, r);
            }
            case EnemyRoster.AEGIS -> aegis(mob, level, r);
            case EnemyRoster.TITAN -> titan(mob, level, target, r, eye);
            case EnemyRoster.BLADE -> blade(mob, level, target, r);
            case EnemyRoster.PLASMA -> plasmaBolt(mob, level, target, r.damage(), 3.2f);
            case EnemyRoster.GRAVITY -> gravity(mob, level, target, r);
            case EnemyRoster.PHASE -> phase(mob, level, target, r);
            case EnemyRoster.OVERSEER -> overseer(mob, level, target, r);
            case EnemyRoster.CARRIER -> carrier(mob, level, target, r);
            case EnemyRoster.COLOSSUS -> colossus(mob, level, target, r);
            default -> { return false; }
        }
        return true;
    }

    /** A cone of fire: everything within range and 25 degrees of the nozzle burns. */
    private static void flame(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        Vec3 from = mob.getEyePosition().add(0, -0.35, 0);
        Vec3 dir = target.getEyePosition().add(0, -0.4, 0).subtract(from).normalize();
        Vec3 to = from.add(dir.scale(r.range() + 1));
        CombatVfx.send(level, CombatVfx.FLAME, 0xFF8030, from, to, 1f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(2.5))) {
            if (e instanceof TechMob || !shootable(e)) continue;
            Vec3 off = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
            double d = off.length();
            if (d > r.range() + 1 || off.normalize().dot(dir) < 0.9) continue;
            e.hurtServer(level, level.damageSources().mobAttack(mob), r.damage());
            e.igniteForSeconds(5);
        }
        if (mob.tickCount % 20 < 5)
            level.playSound(null, mob.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.55f + level.random.nextFloat() * 0.2f);
    }

    /** The lance: 0.8 s of red aim line, then a beam that only hits if the line of sight still holds. */
    private static void lance(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        CombatVfx.send(level, CombatVfx.BEAM, 0xFF3030, mob.getEyePosition(), target.getEyePosition(), 0.2f);
        level.playSound(null, mob.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4f, 1.8f);
        later(level, 16, () -> {
            if (!mob.isAlive() || !target.isAlive()) return;
            Vec3 from = mob.getEyePosition(), to = target.getEyePosition();
            boolean clear = sees(level, from, target);
            Vec3 end = clear ? to : level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getLocation();
            CombatVfx.send(level, CombatVfx.BEAM, 0xFF2A40, from, end, 3.2f);
            level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.6f, 1.7f);
            if (clear && shootable(target)) {
                target.hurtServer(level, level.damageSources().mobAttack(mob), r.damage());
                target.igniteForSeconds(3);
            }
        });
    }

    /** A mortar round: the landing point is marked, the round arcs over for two and a half seconds, then bursts. */
    private static void mortar(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        // lead the target a little and scatter by distance
        Vec3 v = target.getDeltaMovement();
        double scatter = 0.6 + mob.distanceTo(target) * 0.03;
        double x = target.getX() + v.x * 30 + level.random.nextGaussian() * scatter;
        double z = target.getZ() + v.z * 30 + level.random.nextGaussian() * scatter;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        Vec3 land = new Vec3(x, y, z);
        int flight = 50;
        Vec3 from = mob.position().add(0, r.height(), 0);
        CombatVfx.send(level, CombatVfx.WARNING, flight, land, land, 4f);
        CombatVfx.send(level, CombatVfx.MORTAR, flight, from, land, 1f);
        level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.3f, 1.7f);
        later(level, flight, () -> {
            CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, land.add(0, 0.6, 0), land, 2.4f);
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.4f, 0.95f);
            blast(level, mob, land.add(0, 0.6, 0), 4.5f, r.damage());
        });
    }

    private static void detonate(TechMob mob, ServerLevel level, TechCombat.Role r) {
        Vec3 at = mob.position().add(0, 0.3, 0);
        CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, at, at, 1.8f);
        level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.8f, 1.25f);
        blast(level, mob, at, 3.6f, r.damage());
        mob.discard();
    }

    /** The shield bearer: allies within range get damage resistance and a layer of absorption, renewed while it lives. */
    private static void aegis(TechMob mob, ServerLevel level, TechCombat.Role r) {
        CombatVfx.send(level, CombatVfx.SHIELD, 0x5AB4FF, mob.position(), mob.position(), r.range());
        for (TechMob ally : level.getEntitiesOfClass(TechMob.class, mob.getBoundingBox().inflate(r.range()))) {
            if (ally == mob || ally.distanceTo(mob) > r.range()) continue;
            ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 50, 1, true, false, false));
            if (ally.getAbsorptionAmount() < 8) ally.setAbsorptionAmount(Math.min(8, ally.getAbsorptionAmount() + 2));
            CombatVfx.send(level, CombatVfx.SHIELD, 0x5AB4FF, ally.position(), ally.position(), Math.max(1.2f, ally.getBbWidth()));
        }
    }

    /** The titan alternates a plasma barrage with a missile salvo. */
    private static void titan(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r, Vec3 eye) {
        boolean barrage = (mob.tickCount / Math.max(1, r.interval())) % 2 == 0;
        if (barrage) {
            for (int i = 0; i < 3; i++) {
                int shot = i;
                later(level, i * 7, () -> {
                    if (!mob.isAlive()) return;
                    Vec3 from = mob.getEyePosition().add(0, 0.6, 0);
                    Vec3 at = target.position().add(level.random.nextGaussian() * 1.4, 0.5, level.random.nextGaussian() * 1.4);
                    Vec3 end = level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getLocation();
                    CombatVfx.send(level, CombatVfx.BEAM, 0x78E6FF, from, end, 2.4f);
                    CombatVfx.send(level, CombatVfx.PLASMA, 0x78E6FF, end, end, 2.2f);
                    level.playSound(null, mob.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2f, 0.5f + shot * 0.1f);
                    blast(level, mob, end, 3.2f, r.damage() * 0.7f);
                });
            }
        } else {
            for (int i = 0; i < 4; i++) {
                int shot = i;
                later(level, i * 5, () -> {
                    if (!mob.isAlive() || !target.isAlive()) return;
                    Vec3 from = mob.getEyePosition().add((shot % 2 == 0 ? -1 : 1) * 1.1, 0.8, 0);
                    Vec3 dir = new Vec3(level.random.nextGaussian() * 0.25, 1.2, level.random.nextGaussian() * 0.25);
                    Missiles.fire(level, mob, target, from, dir, r.damage() * 0.55f);
                });
            }
            if (target instanceof ServerPlayer p) p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.heliosphere.titan_salvo"), true);
        }
    }

    /** The stalker's blades: a heavy cut that also leaves the target bleeding. */
    private static void blade(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        if (mob.distanceTo(target) > 3.2f) return;
        mob.removeEffect(MobEffects.INVISIBILITY);
        Vec3 at = target.getEyePosition().add(0, -0.5, 0);
        CombatVfx.send(level, CombatVfx.SLASH, 0x9FD8FF, at, at.add(mob.getLookAngle()), 1f);
        level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3f, 0.6f);
        if (!shootable(target)) return;
        target.hurtServer(level, level.damageSources().mobAttack(mob), r.damage());
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
    }

    // ---- Mk VII and up ---------------------------------------------------------------------------------------

    /** A plasma bolt: a searing beam and a burst that also melts what it hits (level 7 machines break obsidian). */
    private static void plasmaBolt(TechMob mob, ServerLevel level, LivingEntity target, float damage, float radius) {
        Vec3 from = mob.getEyePosition();
        Vec3 at = target.getBoundingBox().getCenter().add(level.random.nextGaussian() * 0.6, 0, level.random.nextGaussian() * 0.6);
        Vec3 end = level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getLocation();
        CombatVfx.send(level, CombatVfx.BEAM, 0x78E6FF, from, end, 2.0f);
        CombatVfx.send(level, CombatVfx.PLASMA, 0x78E6FF, end, end, radius * 0.7f);
        level.playSound(null, mob.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.6f, 0.6f);
        blast(level, mob, end, radius, damage);
        MachineBlast.breakBlocks(level, end, 1.6f, EnemyRoster.tier(role(mob).id()));
    }

    /** The graviton walker drags its target in and crushes it. */
    private static void gravity(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        Vec3 pull = mob.position().subtract(target.position()).normalize().scale(1.1);
        target.push(pull.x, 0.25, pull.z);
        target.hurtMarked = true;
        CombatVfx.send(level, CombatVfx.BEAM, 0xB070FF, mob.getEyePosition(), target.getEyePosition(), 1.6f);
        CombatVfx.send(level, CombatVfx.SHIELD, 0xB070FF, target.position(), target.position(), 1.6f);
        level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.4f, 0.7f);
        if (shootable(target) && mob.distanceTo(target) < r.range()) {
            target.hurtServer(level, level.damageSources().mobAttack(mob), r.damage());
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
        }
    }

    /** The phase stalker steps through space to stand behind its target, then cuts. */
    private static void phase(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
        CombatVfx.send(level, CombatVfx.PLASMA, 0xC080FF, mob.getBoundingBox().getCenter(), mob.getBoundingBox().getCenter(), 0.8f);
        if (level.noCollision(mob, mob.getBoundingBox().move(behind.subtract(mob.position())))) mob.teleportTo(behind.x, behind.y, behind.z);
        CombatVfx.send(level, CombatVfx.PLASMA, 0xC080FF, mob.getBoundingBox().getCenter(), mob.getBoundingBox().getCenter(), 0.8f);
        level.playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 0.6f);
        blade(mob, level, target, r);
    }

    // ---- bosses -------------------------------------------------------------------------------------------------

    /** The overseer: three telegraphed lances, a wave of swarm drones, and its own shield. */
    private static void overseer(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        int phase = (mob.tickCount / Math.max(1, r.interval())) % 3;
        switch (phase) {
            case 0 -> {
                for (int i = 0; i < 3; i++) later(level, i * 10, () -> { if (mob.isAlive() && target.isAlive()) lance(mob, level, target, r); });
            }
            case 1 -> {
                for (int i = 0; i < 3; i++)
                    TechCombat.spawn(level, "swarm_drone", mob.getX() + level.random.nextGaussian() * 2, mob.getY() + 3, mob.getZ() + level.random.nextGaussian() * 2, target);
                level.playSound(null, mob.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2f, 0.6f);
            }
            default -> {
                aegis(mob, level, r);
                mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 1, true, false, false));
                if (mob.getAbsorptionAmount() < 40) mob.setAbsorptionAmount(mob.getAbsorptionAmount() + 20);
            }
        }
    }

    /** The carrier launches swarm drones from its bays and fires missile salvos. */
    private static void carrier(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        if ((mob.tickCount / Math.max(1, r.interval())) % 2 == 0) {
            for (int i = 0; i < 4; i++)
                TechCombat.spawn(level, "swarm_drone", mob.getX() + (i % 2 == 0 ? -2 : 2), mob.getY() - 1, mob.getZ() + (i < 2 ? -1.5 : 1.5), target);
            level.playSound(null, mob.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 2f, 0.5f);
        } else {
            for (int i = 0; i < 6; i++) {
                int shot = i;
                later(level, i * 4, () -> {
                    if (!mob.isAlive() || !target.isAlive()) return;
                    Vec3 from = mob.position().add((shot % 2 == 0 ? -1.8 : 1.8), 0.4, 0);
                    Missiles.fire(level, mob, target, from, new Vec3(level.random.nextGaussian() * 0.4, 1.0, level.random.nextGaussian() * 0.4), r.damage() * 0.6f);
                });
            }
            if (target instanceof ServerPlayer p) p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.heliosphere.titan_salvo"), true);
        }
    }

    /** The colossus: a plasma barrage, a ground slam that throws everyone near it, and mortar rain. */
    private static void colossus(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        int phase = (mob.tickCount / Math.max(1, r.interval())) % 3;
        switch (phase) {
            case 0 -> {
                for (int i = 0; i < 5; i++) later(level, i * 6, () -> { if (mob.isAlive() && target.isAlive()) plasmaBolt(mob, level, target, r.damage() * 0.7f, 3.6f); });
            }
            case 1 -> {
                Vec3 at = mob.position();
                CombatVfx.send(level, CombatVfx.RING, 0xFFB060, at, at, 12f);
                CombatVfx.send(level, CombatVfx.SHOCK, 0, at, at, 0.8f);
                level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.5f);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12))) {
                    if (e instanceof TechMob || !shootable(e)) continue;
                    double d = e.distanceTo(mob);
                    if (d > 12) continue;
                    e.hurtServer(level, level.damageSources().mobAttack(mob), (float) (r.damage() * (1 - d / 14)));
                    Vec3 push = e.position().subtract(at).normalize().scale(1.8 * (1 - d / 14));
                    e.push(push.x, 0.9, push.z);
                    e.hurtMarked = true;
                }
            }
            default -> {
                for (int i = 0; i < 4; i++) later(level, i * 12, () -> { if (mob.isAlive() && target.isAlive()) mortar(mob, level, target, r); });
            }
        }
    }

    // ---- boss bars --------------------------------------------------------------------------------------------

    private static final Map<java.util.UUID, net.minecraft.server.level.ServerBossEvent> BARS = new java.util.HashMap<>();

    private static void bossBars(ServerLevel level) {
        for (Iterator<Map.Entry<java.util.UUID, net.minecraft.server.level.ServerBossEvent>> it = BARS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity ent = level.getEntity(e.getKey());
            if (ent == null && level.getServer().getAllLevels().iterator().hasNext() && level.dimension() == Level.OVERWORLD) {
                boolean anywhere = false;
                for (ServerLevel l : level.getServer().getAllLevels()) if (l.getEntity(e.getKey()) != null) { anywhere = true; break; }
                if (!anywhere) { e.getValue().removeAllPlayers(); it.remove(); }
            }
        }
        for (ServerPlayer p : level.players()) {
            for (TechMob mob : level.getEntitiesOfClass(TechMob.class, p.getBoundingBox().inflate(72))) {
                TechCombat.Role r = role(mob);
                if (r == null || !EnemyRoster.BOSSES.contains(r.id())) continue;
                var bar = BARS.computeIfAbsent(mob.getUUID(), id -> {
                    var b = new net.minecraft.server.level.ServerBossEvent(net.minecraft.network.chat.Component.translatable("entity.heliosphere." + r.id()),
                            net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
                    b.setDarkenScreen(true);
                    return b;
                });
                bar.setProgress(Math.max(0, mob.getHealth() / mob.getMaxHealth()));
                if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
                if (!mob.isAlive()) bar.removeAllPlayers();
            }
            for (var bar : BARS.values()) if (bar.getPlayers().contains(p) && !near(level, p, bar)) bar.removePlayer(p);
        }
    }

    private static boolean near(ServerLevel level, ServerPlayer p, net.minecraft.server.level.ServerBossEvent bar) {
        for (var e : BARS.entrySet()) if (e.getValue() == bar) {
            Entity ent = level.getEntity(e.getKey());
            return ent != null && ent.isAlive() && ent.distanceTo(p) < 96;
        }
        return false;
    }

    // ---- patrols on other worlds ----------------------------------------------------------------------------

    /** Every minute or so, a player on a solid world other than Earth may meet a patrol of that world's machines. */
    private static void outerPatrols(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD || com.heliosphere.core.env.Planets.isSpace(level) || com.heliosphere.core.env.Planets.isGasGiant(level)) return;
        var body = com.heliosphere.core.env.Planets.bodyOf(level);
        if (body == null) return;
        int difficulty = WorldTiers.base(body.id);
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || level.random.nextFloat() > 0.45f) continue;
            if (level.getEntitiesOfClass(TechMob.class, p.getBoundingBox().inflate(64)).size() >= 6) continue;
            List<String> pool = EnemyRoster.pool(difficulty);
            int n = 1 + level.random.nextInt(2 + difficulty / 5);
            double a = level.random.nextDouble() * Math.PI * 2, d = 40 + level.random.nextDouble() * 24;
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int i = 0; i < n; i++)
                TechCombat.spawn(level, pool.get(level.random.nextInt(pool.size())), x + level.random.nextGaussian() * 2, y + 1, z + level.random.nextGaussian() * 2, p);
        }
    }

    /** Stalkers fade from sight while nobody is near them. */
    private static void cloak(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            for (TechMob mob : level.getEntitiesOfClass(TechMob.class, p.getBoundingBox().inflate(64))) {
                TechCombat.Role r = role(mob);
                if (r == null || r.mode() != EnemyRoster.BLADE) continue;
                Player near = level.getNearestPlayer(mob, 7);
                if (near == null && mob.getLastHurtByMobTimestamp() + 60 < mob.tickCount)
                    mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
                else mob.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    // ---- follow-ups for the released weapons --------------------------------------------------------------------

    public static void followUp(TechMob mob, ServerLevel level, LivingEntity target, Vec3 aim) {
        TechCombat.Role r = role(mob);
        if (r == null) return;
        switch (r.mode()) {
            case 1 -> {                                            // bullets: a three-round burst
                for (int i = 1; i <= 2; i++) {
                    later(level, i * 3, () -> {
                        if (!mob.isAlive() || !target.isAlive()) return;
                        Vec3 from = mob.getEyePosition();
                        Vec3 to = target.getEyePosition().add(level.random.nextGaussian() * 0.35, level.random.nextGaussian() * 0.25 - 0.2, level.random.nextGaussian() * 0.35);
                        TechProjectile.launch(level, mob, target, from, to, 1, r.damage() * 0.8f);
                    });
                }
            }
            case 3 -> later(level, 8, () -> {                       // missiles: launched in pairs
                if (!mob.isAlive() || !target.isAlive()) return;
                Vec3 from = mob.getEyePosition().add(0, 0.3, 0);
                Vec3 dir = target.getEyePosition().subtract(from).normalize().add(0, 0.6, 0);
                Missiles.fire(level, mob, target, from, dir, r.damage());
            });
            case 6 -> {                                            // rail: the slug's impact bursts
                Vec3 from = mob.getEyePosition();
                Vec3 end = level.clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getLocation();
                CombatVfx.send(level, CombatVfx.EXPLOSION, 0xA0D0FF, end, end, 1.2f);
            }
            default -> { }
        }
    }

    // ---- spawning -----------------------------------------------------------------------------------------------

    public static void spawned(ServerLevel level, String id, double x, double y, double z, LivingEntity target, TechMob mob) {
        if (mob == null || spawningSwarm || !"swarm_drone".equals(id)) return;
        spawningSwarm = true;
        try {
            for (int i = 0; i < 4; i++)
                TechCombat.spawn(level, id, x + level.random.nextGaussian() * 2, y + 1 + level.random.nextFloat() * 2, z + level.random.nextGaussian() * 2, target);
        } finally {
            spawningSwarm = false;
        }
    }
}
