package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.planets.defense.sam.SamSites;
import com.heliosphere.planets.defense.sam.SamVehicle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The engine of a crewed air-defence vehicle: a diesel loop at the vehicle that idles while someone is aboard and
 * revs up (louder, higher) as it drives, for the crew and for everyone around. It dies away once the crew is out.
 */
public final class SamEngineSound extends AbstractTickableSoundInstance {
    private static final Map<Integer, SamEngineSound> PLAYING = new HashMap<>();

    private final SamVehicle v;
    private float load;

    private SamEngineSound(SamVehicle v) {
        super(SamSites.ENGINE, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.v = v;
        looping = true;
        delay = 0;
        volume = 0.05f;
        pitch = 0.8f;
        x = v.getX(); y = v.getY() + 1; z = v.getZ();
    }

    @Override public boolean canStartSilent() { return true; }
    @Override public boolean canPlaySound() { return !v.isSilent(); }

    @Override
    public void tick() {
        if (v.isRemoved()) { stop(); return; }
        boolean crewed = !v.getPassengers().isEmpty();
        double dx = v.getX() - v.xo, dz = v.getZ() - v.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        float k = Math.min(1f, speed / 0.3f);
        if (v.driving()) k = Math.max(k, 0.25f);                    // revving to move off, turning on the spot
        load += (k - load) * 0.12f;
        float wantVolume = crewed ? 0.9f + 0.9f * load : 0f;        // over 1: heard further (16 blocks per unit)
        volume += (wantVolume - volume) * (crewed ? 0.2f : 0.08f);
        pitch = 0.78f + 0.55f * load;
        x = v.getX(); y = v.getY() + 1; z = v.getZ();
        if (!crewed && volume < 0.03f) stop();
    }

    /** For tests: {engines running, loudest volume, its pitch}. */
    public static float[] state() {
        float n = 0, vol = 0, pitch = 0;
        for (SamEngineSound s : PLAYING.values()) {
            if (s.isStopped()) continue;
            n++;
            if (s.volume > vol) { vol = s.volume; pitch = s.pitch; }
        }
        return new float[]{n, vol, pitch};
    }

    /** For tests on machines without audio, where the sound engine never ticks the loop: one step of its logic. */
    public static void stepForTest() { for (SamEngineSound s : PLAYING.values()) if (!s.isStopped()) s.tick(); }

    /** Starts the engine of every crewed vehicle in earshot that is not running yet. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || mc.player == null) { PLAYING.clear(); return; }
            PLAYING.values().removeIf(AbstractTickableSoundInstance::isStopped);
            for (SamVehicle v : mc.level.getEntitiesOfClass(SamVehicle.class, mc.player.getBoundingBox().inflate(64))) {
                if (v.getPassengers().isEmpty() || PLAYING.containsKey(v.getId())) continue;
                SamEngineSound s = new SamEngineSound(v);
                PLAYING.put(v.getId(), s);
                Minecraft.getInstance().getSoundManager().play(s);
            }
        });
    }
}
