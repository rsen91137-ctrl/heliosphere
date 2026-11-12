package com.heliosphere.disasters.tide;

import com.heliosphere.core.client.CameraShake;
import com.heliosphere.core.client.fx.FxEngine;
import com.heliosphere.core.fx.sim.AsteroidImpact;
import com.heliosphere.core.fx.sim.MushroomCloud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Client side of {@link ImpactFx}: the impact itself, the flash, and the ground shaking as the blast arrives. */
public final class ImpactFxClient implements ClientModInitializer {
    /** Something that happens later on the client: a camera shake (flash < 0) or a screen flash. */
    private record Pending(Vec3 at, long arrives, float strength, float flash) {}
    private static final List<Pending> WAVES = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ImpactFx.ID, (p, ctx) -> ctx.client().execute(() -> show(p, ctx.client())));
        ClientPlayNetworking.registerGlobalReceiver(RemoteImpact.ID, (p, ctx) -> ctx.client().execute(() -> remote(p, ctx.client())));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || WAVES.isEmpty()) return;
            long now = mc.level.getGameTime();
            WAVES.removeIf(w -> {
                if (now < w.arrives) return false;
                if (w.flash >= 0) FxEngine.flash(w.flash, 0xFFF4E0, 3f);
                else CameraShake.amount = Math.max(CameraShake.amount, w.strength);
                return true;
            });
        });
    }

    private static void show(ImpactFx p, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Vec3 at = new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
        float age = p.age() / 20f;
        float k = p.scale();
        int seed = p.seed();
        // the direction it came in from: fixed per impact
        double heading = (seed & 1023) / 1023.0 * Math.PI * 2;
        float ax = (float) Math.cos(heading), az = (float) Math.sin(heading);
        float elevation = 0.45f + ((seed >>> 10) & 255) / 255f * 0.5f;
        MushroomCloud.Env own = new MushroomCloud.Env();
        own.dustR = 0.48f; own.dustG = 0.40f; own.dustB = 0.32f;
        FxEngine.add((s, t, env) -> {
            own.daylight = env.daylight;
            own.detail = env.detail;
            own.windX = env.windX; own.windZ = env.windZ;
            AsteroidImpact.emit(s, at.x, at.y, at.z, t, k, seed, ax, az, elevation, own);
            return t < AsteroidImpact.DURATION;
        }, age);
        double d = Math.hypot(mc.player.getX() - at.x, mc.player.getZ() - at.z);
        long now = mc.level.getGameTime();
        // contact comes ENTRY seconds into the effect: the sky brightens as the body comes in, then the flash
        float contact = AsteroidImpact.ENTRY - age;
        if (contact > -1.5f) {
            float f = AsteroidImpact.flash(0, (float) d, k);
            if (f > 0.02f) {
                for (float pre = 0.6f; pre < 1.0f; pre += 0.2f) {
                    float when = contact - AsteroidImpact.ENTRY * (1 - pre);
                    if (when >= 0) WAVES.add(new Pending(at, now + (long) (when * 20), 0, f * 0.45f * pre * pre));
                }
                WAVES.add(new Pending(at, now + (long) Math.max(0, contact * 20), 0, f));
            }
        }
        // the ground wave reaches here after d / speed seconds
        float travel = contact + (float) (d / AsteroidImpact.WAVE_SPEED);
        if (d < AsteroidImpact.WAVE_REACH * 1.6 && travel > 0)
            WAVES.add(new Pending(at, now + (long) (travel * 20), (float) Math.max(0.25, 1.2 * (1 - d / (AsteroidImpact.WAVE_REACH * 1.6))), -1));
    }

    /**
     * Seen from the Moon: a point of light flares on the Earth's disc, then a dark smudge spreads across it with a
     * dull red glow at its heart. Drawn in the direction the sky draws the Earth, far out along the line of sight.
     */
    private static void remote(RemoteImpact p, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        var body = com.heliosphere.core.env.Planets.bodyOf(mc.level);
        Vector3f dir = body == null ? null : earthDirection(mc, body);
        FxEngine.flash(0.25f, 0xFFF4E0, 1.5f);
        if (dir == null) return;
        int seed = p.seed();
        FxEngine.add((s, t, env) -> {
            if (t > 240) return false;
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            double dist = 600;
            double x = cam.x + dir.x * dist, y = cam.y + dir.y * dist, z = cam.z + dir.z * dist;
            float flash = (float) Math.exp(-t / 1.2);
            s.billboard(x, y, z, 60 * flash + 6, 0, com.heliosphere.core.fx.sim.FxAtlas.GLOW, 0, 0, 0, 0, flash, flash * 0.95f, flash * 0.85f);
            s.billboard(x, y, z, 90 * flash, 0.4f, com.heliosphere.core.fx.sim.FxAtlas.FLARE, 0, 0, 0, 0, flash, flash, flash);
            float glow = (float) Math.exp(-t / 60.0) * 0.8f;
            s.billboard(x, y, z, 4, 0, com.heliosphere.core.fx.sim.FxAtlas.GLOW, 0, 0, 0, 0, glow, glow * 0.35f, glow * 0.1f);
            float spread = 2 + 10 * (1 - (float) Math.exp(-t / 50.0));
            float a = 0.55f * Math.min(1, t / 8f) * (1 - Math.max(0, (t - 180) / 60f));
            s.billboard(x, y, z, spread, seed, com.heliosphere.core.fx.sim.FxAtlas.SMOKE + (seed & 7), 0.18f, 0.16f, 0.15f, a, 0, 0, 0);
            return true;
        });
    }

    private static java.lang.reflect.Method lockedParentDir;

    /** The direction the sky draws the planet a tidally locked moon faces (the Earth, from the Moon). */
    private static Vector3f earthDirection(Minecraft mc, com.heliosphere.core.celestial.CelestialBody body) {
        if (!"earth".equals(body.parent)) return null;
        try {
            if (lockedParentDir == null) {
                lockedParentDir = Class.forName("com.heliosphere.core.client.sky.SpaceSky").getDeclaredMethod("lockedParentDir",
                        net.minecraft.client.multiplayer.ClientLevel.class, com.heliosphere.core.celestial.CelestialBody.class, net.minecraft.world.entity.player.Player.class);
                lockedParentDir.setAccessible(true);
            }
            Vector3f v = (Vector3f) lockedParentDir.invoke(null, mc.level, body, mc.player);
            return v == null ? null : new Vector3f(v).normalize();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
