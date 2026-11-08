package com.heliosphere.rocketry.client;

import com.heliosphere.core.client.CameraShake;
import com.heliosphere.core.env.Planets;
import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Engine roar that follows each burning rocket: loud and bright outside, a deep muffled rumble for the crew,
 * and silent in vacuum unless you are aboard (structure-borne vibration). Also drives the camera shake.
 */
public final class RocketSound extends AbstractTickableSoundInstance {
    private static final Map<Integer, RocketSound> PLAYING = new HashMap<>();
    private final RocketEntity rocket;

    private RocketSound(RocketEntity r) {
        super(Rocketry.ENGINE_SOUND, SoundSource.NEUTRAL, RandomSource.create());
        this.rocket = r;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.x = r.getX(); this.y = r.getY(); this.z = r.getZ();
    }

    @Override
    public void tick() {
        if (rocket.isRemoved() || rocket.throttle() <= 0.01f) { stop(); PLAYING.remove(rocket.getId()); return; }
        LocalPlayer me = Minecraft.getInstance().player;
        boolean aboard = me != null && me.getVehicle() == rocket;
        boolean vacuum = Planets.isSpace(rocket.level()) || com.heliosphere.core.env.Environment.pressureBar(rocket.level()) < 0.001;
        float t = rocket.throttle();
        x = rocket.getX(); y = rocket.getY(); z = rocket.getZ();
        if (aboard) { volume = vacuum ? 0.35f * t : 0.75f * t; pitch = vacuum ? 0.55f : 0.75f; x = me.getX(); y = me.getY(); z = me.getZ(); }
        else if (vacuum) volume = 0f;
        else { volume = Math.min(1f, 0.2f + t); pitch = 0.85f + (float) Math.min(0.3, rocket.getDeltaMovement().length() * 0.1); }
    }

    /** Called every client tick: start sounds for newly lit rockets and update shake. */
    public static void clientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { CameraShake.amount = 0; return; }
        float shake = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof RocketEntity r)) continue;
            float t = r.throttle();
            double d = r.distanceTo(mc.player);
            if (t > 0.01f && d < 200 && !PLAYING.containsKey(r.getId())) {
                RocketSound s = new RocketSound(r);
                PLAYING.put(r.getId(), s);
                mc.getSoundManager().play(s);
            }
            if (t > 0.01f) {
                boolean aboard = mc.player.getVehicle() == r;
                boolean air = !Planets.isSpace(r.level()) && com.heliosphere.core.env.Environment.pressureBar(r.level()) > 0.001;
                float near = aboard ? (air ? 1.1f : 0.25f) : (float) Math.max(0, 1 - d / 90) * (air ? 0.9f : 0f);
                shake = Math.max(shake, near * t * (r.phase() == RocketEntity.COUNTDOWN ? 0.5f : 1f));
            }
        }
        CameraShake.amount = shake;
    }
}
