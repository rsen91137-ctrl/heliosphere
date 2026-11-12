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

import java.util.ArrayList;
import java.util.List;

/** Client side of {@link ImpactFx}: the impact itself, the flash, and the ground shaking as the blast arrives. */
public final class ImpactFxClient implements ClientModInitializer {
    private record Pending(Vec3 at, long arrives, float strength) {}
    private static final List<Pending> WAVES = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ImpactFx.ID, (p, ctx) -> ctx.client().execute(() -> show(p, ctx.client())));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || WAVES.isEmpty()) return;
            long now = mc.level.getGameTime();
            WAVES.removeIf(w -> {
                if (now < w.arrives) return false;
                CameraShake.amount = Math.max(CameraShake.amount, w.strength);
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
        if (age < 1.5f) {
            float f = AsteroidImpact.flash(age, (float) d, k);
            if (f > 0.02f) FxEngine.flash(f, 0xFFF4E0, 3f);
        }
        // the ground wave reaches here after d / speed seconds
        float travel = (float) (d / AsteroidImpact.WAVE_SPEED);
        if (d < AsteroidImpact.WAVE_REACH * 1.6 && travel > age)
            WAVES.add(new Pending(at, mc.level.getGameTime() + (long) ((travel - age) * 20), (float) Math.max(0.25, 1.2 * (1 - d / (AsteroidImpact.WAVE_REACH * 1.6)))));
    }
}
