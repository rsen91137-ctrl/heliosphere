package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.core.client.fx.FxEngine;
import com.heliosphere.core.fx.sim.FxAtlas;
import com.heliosphere.core.fx.sim.MushroomCloud;
import com.heliosphere.core.fx.sim.SpriteSink;
import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MissileSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Smoke and fire of missiles in flight: a thick white trail behind the boost motor and a thinner one behind the
 * sustainer, both slowly spreading, drifting and fading; the bright plume at the nozzle; and the cloud a missile
 * throws up as it leaves its launcher (a short puff first for the cold-launched Aster).
 */
final class MissileTrails {
    private MissileTrails() {}

    private static final Set<Integer> TRACKED = new HashSet<>();

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) { TRACKED.clear(); return; }
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof GuidedMissile m && TRACKED.add(m.getId())) {
                    FxEngine.add(new Trail(m));
                    if (m.spec().launchSpeed > 0 && m.flightAge() < 6) FxEngine.add(new LaunchCloud(m.position(), m.spec()));
                }
            }
        });
    }

    static double length(MissileSpec.Model model) {
        return switch (model) {
            case BUK -> 5.55; case ASTER -> 5.3; case ASTER_BOOSTER -> 2.7; case IRIS -> 3.1; case TURRET -> 1.9;
            case PLAYER -> 1.3; case MACHINE -> 1.5; case MACHINE_HEAVY -> 2.1; case NUKE -> 2.8;
        };
    }

    /** How much smoke a motor makes: IRIS-T burns a low-smoke propellant, a thin, short-lived trail. */
    static float smoke(MissileSpec.Model model) { return model == MissileSpec.Model.IRIS ? 0.3f : 1f; }

    static float size(MissileSpec.Model model) {
        return switch (model) {
            case BUK -> 1.5f; case ASTER -> 1.25f; case IRIS -> 1.0f; case TURRET -> 0.8f; case PLAYER -> 0.6f;
            case MACHINE -> 0.6f; case MACHINE_HEAVY -> 0.85f; case ASTER_BOOSTER -> 0.5f; case NUKE -> 1.1f;
        };
    }

    /** One puff: position, birth time on the effect clock, starting size, how hot it left the motor. */
    private record Puff(double x, double y, double z, double born, float size, float heat, float roll) {}

    private static final class Trail implements FxEngine.Effect {
        private final GuidedMissile missile;
        private final int id;
        private final List<Puff> puffs = new ArrayList<>();
        private Vec3 last;
        private int n;

        Trail(GuidedMissile m) { missile = m; id = m.getId(); }

        @Override
        public boolean emit(SpriteSink sink, float age, MushroomCloud.Env env) {
            Minecraft mc = Minecraft.getInstance();
            double now = FxEngine.clock();
            boolean alive = missile.isAlive() && !missile.isRemoved() && missile.level() == mc.level;
            MissileSpec spec = missile.spec();
            float base = size(spec.model);
            if (alive) {
                float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
                Vec3 pos = missile.getPosition(partial), dir = missile.getViewVector(partial);
                double len = missile.boosterAttached() || spec.model != MissileSpec.Model.ASTER ? length(spec.model) : 2.7;
                Vec3 tail = pos.subtract(dir.scale(len));
                int motor = missile.motor();
                if (motor > 0) {
                    if (last == null) last = tail;
                    Vec3 seg = tail.subtract(last);
                    double d = seg.length(), step = Math.max(0.35, base * 0.32) / smoke(spec.model);   // a low-smoke motor leaves a thin trail
                    int count = (int) Math.min(80, d / step);
                    for (int i = 1; i <= count; i++) {
                        Vec3 p = last.add(seg.scale(i / (double) count));
                        n++;
                        puffs.add(new Puff(p.x, p.y, p.z, now - (count - i) * 0.002, base * (motor == 2 ? 1f : 0.62f), motor == 2 ? 1f : 0.55f, (n * 2.39996f) % 6.283f));
                    }
                    if (count > 0) last = tail;
                    // plume at the nozzle
                    float flick = 0.85f + 0.15f * (float) Math.sin(now * 90 + id);
                    float pl = base * (motor == 2 ? 2.4f : 1.4f) * flick;
                    Vec3 c = tail.subtract(dir.scale(pl * 0.25));
                    sink.billboard(c.x, c.y, c.z, pl * 2.2f, 0, FxAtlas.GLOW, 0, 0, 0, 0, 0.55f, 0.32f, 0.12f);
                    sink.billboard(c.x, c.y, c.z, pl, (float) (now * 7), FxAtlas.FIRE + (n & 3), 1, 0.8f, 0.5f, 0.2f, 1f, 0.75f, 0.4f);
                    sink.billboard(tail.x, tail.y, tail.z, pl * 0.45f, 0, FxAtlas.CORE, 0, 0, 0, 0, 1f, 0.95f, 0.85f);
                    if (motor == 2) sink.billboard(tail.x, tail.y, tail.z, pl * 3.2f, (float) (now * 2), FxAtlas.FLARE, 0, 0, 0, 0, 0.25f, 0.18f, 0.1f);
                } else last = null;
            }
            // the trail
            float day = 0.25f + 0.75f * env.daylight;
            float sm = smoke(spec.model);
            for (int i = puffs.size() - 1; i >= 0; i--) {
                Puff p = puffs.get(i);
                double t = now - p.born;
                double life = (3.5 + p.heat * 4.5) * (0.4 + 0.6 * sm);
                if (t > life) { puffs.remove(i); continue; }
                float k = (float) (t / life);
                float size = p.size * (1f + (float) t * 1.1f);
                float a = 0.5f * (float) Math.pow(1 - k, 1.6) * (0.6f + 0.4f * p.heat) * (0.35f + 0.65f * sm);
                float shade = day * (0.82f + 0.1f * ((i * 7) % 3) / 2f);
                float hot = t < 0.12 ? (float) (1 - t / 0.12) * p.heat : 0;
                sink.billboard(p.x + env.windX * t * 0.25, p.y + t * 0.35, p.z + env.windZ * t * 0.25, size, p.roll + (float) t * 0.2f,
                        hot > 0.3f ? FxAtlas.FIRE + (i & 3) : FxAtlas.SMOKE + (i & 7), shade, shade, shade * 1.02f, a, hot * 0.9f, hot * 0.45f, hot * 0.15f);
            }
            return alive || !puffs.isEmpty();
        }
    }

    /** The cloud at the launcher: dust and motor smoke rolling out over the ground. */
    private static final class LaunchCloud implements FxEngine.Effect {
        private final Vec3 at;
        private final float scale;
        private final boolean cold;

        LaunchCloud(Vec3 at, MissileSpec spec) {
            this.at = at;
            this.scale = size(spec.model);
            this.cold = spec.ignite > 0;
        }

        @Override
        public boolean emit(SpriteSink sink, float age, MushroomCloud.Env env) {
            if (age > 7) return false;
            float day = 0.25f + 0.75f * env.daylight;
            int count = 26;
            for (int i = 0; i < count; i++) {
                double ang = i * 2.39996, out = (1 - Math.exp(-age * 1.2)) * (2 + (i % 5)) * scale * 1.6;
                double h = cold ? (i % 4) * 0.4 : Math.max(0, 1.5 - (i % 6) * 0.25) * scale;
                float size = scale * (1.4f + age * 0.9f) * (0.8f + (i % 3) * 0.2f);
                float a = 0.55f * (float) Math.pow(Math.max(0, 1 - age / 7f), 1.3);
                float hot = !cold && age < 0.25f ? 1 - age / 0.25f : 0;
                sink.billboard(at.x + Math.cos(ang) * out, at.y - scale * 0.5 + h + age * 0.3, at.z + Math.sin(ang) * out, size, (float) ang + age * 0.3f,
                        FxAtlas.SMOKE + (i & 7), day * 0.86f, day * 0.84f, day * 0.8f, a, hot * 0.9f, hot * 0.5f, hot * 0.2f);
            }
            if (age < 0.2f && !cold) sink.billboard(at.x, at.y, at.z, scale * 9, 0, FxAtlas.GLOW, 0, 0, 0, 0, 0.9f, 0.6f, 0.3f);
            return true;
        }
    }
}
