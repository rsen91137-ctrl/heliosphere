package com.heliosphere.planets.weapon.fx;

import com.heliosphere.core.client.CameraShake;
import com.heliosphere.core.client.fx.FxEngine;
import com.heliosphere.core.fx.sim.Blasts;
import com.heliosphere.core.fx.sim.MushroomCloud;
import com.heliosphere.planets.weapon.GunItem;
import com.heliosphere.planets.weapon.nuke.NukeContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of {@link CombatVfx}: turns each packet into effects in the shared engine, shakes the camera,
 * flashes the screen, and keeps the ambience of an irradiated place going (ash in the air, the Geiger
 * counter, the sick green cast).
 */
public final class CombatVfxClient implements ClientModInitializer {
    private static int serial;
    private static float falloutLevel;
    private static int falloutTicks, clickCooldown;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CombatVfx.ID, (p, ctx) -> ctx.client().execute(() -> handle(p, ctx.client())));
        ClientTickEvents.END_CLIENT_TICK.register(CombatVfxClient::ambience);
    }

    private static void shake(float amount) { if (amount > CameraShake.amount) CameraShake.amount = Math.min(1.2f, amount); }

    private static void handle(CombatVfx p, Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        if (level == null || me == null) return;
        int seed = ++serial * 7919;
        Vec3 a = p.start(), b = p.end();
        double dist = me.getEyePosition().distanceTo(a);
        switch (p.kind()) {
            case CombatVfx.TRACER, CombatVfx.BEAM -> shot(p, mc, level, me, seed);
            case CombatVfx.RING -> {
                int c = p.color();
                boolean warhead = ((c >> 16) & 255) > (c & 255) + 40;
                if (warhead) {
                    float power = Math.max(1.5f, p.strength() * 0.75f);
                    FxEngine.add((s, t, env) -> Blasts.explosion(s, t, a.x, a.y, a.z, power, seed, env.daylight));
                    shake((float) (power * 0.14f * Math.max(0, 1 - dist / (power * 12))));
                } else {
                    float radius = Math.max(2f, p.strength());
                    FxEngine.add((s, t, env) -> Blasts.ring(s, t, a.x, a.y, a.z, c, radius));
                }
            }
            case CombatVfx.EXPLOSION -> {
                float power = p.strength();
                FxEngine.add((s, t, env) -> Blasts.explosion(s, t, a.x, a.y, a.z, power, seed, env.daylight));
                shake((float) (power * 0.14f * Math.max(0, 1 - dist / (power * 12))));
                if (dist < power * 3) FxEngine.flash(0.35f, 0xFFE0A0, 0.25f);
            }
            case CombatVfx.PLASMA -> {
                float power = p.strength();
                int c = p.color();
                FxEngine.add((s, t, env) -> Blasts.plasma(s, t, a.x, a.y, a.z, power, c, seed));
                shake((float) (power * 0.1f * Math.max(0, 1 - dist / (power * 10))));
            }
            case CombatVfx.ANTIMATTER -> {
                float power = p.strength();
                FxEngine.add((s, t, env) -> Blasts.antimatter(s, t, a.x, a.y, a.z, power, seed));
                shake((float) (power * 0.16f * Math.max(0, 1 - dist / (power * 14))));
                if (dist < power * 10) FxEngine.flash((float) (0.7f * (1 - dist / (power * 10))), 0xF4E8FF, 0.3f);
            }
            case CombatVfx.SINGULARITY -> {
                float radius = Math.max(3f, p.strength());
                FxEngine.add((s, t, env) -> Blasts.singularity(s, t, a.x, a.y, a.z, radius, seed));
                shake((float) (0.5f * Math.max(0, 1 - dist / 40)));
            }
            case CombatVfx.NUKE -> {
                int cloudSeed = (int) b.x;
                float age = p.color() / 20f;
                int ground = level.getBlockState(BlockPos.containing(a.x, a.y - 1, a.z)).getMapColor(level, BlockPos.containing(a.x, a.y - 1, a.z)).col;
                MushroomCloud.Env own = new MushroomCloud.Env();
                if (ground != 0) {
                    own.dustR = 0.25f + 0.5f * ((ground >> 16) & 255) / 255f;
                    own.dustG = 0.22f + 0.5f * ((ground >> 8) & 255) / 255f;
                    own.dustB = 0.18f + 0.5f * (ground & 255) / 255f;
                }
                FxEngine.add((s, t, env) -> {
                    own.daylight = env.daylight;
                    own.detail = env.detail;
                    MushroomCloud.emit(s, a.x, a.y, a.z, t, 1f, cloudSeed, own);
                    return t < MushroomCloud.DURATION;
                }, age);
                if (age < 1f) {
                    FxEngine.flash((float) Math.max(0.25, Math.min(1, 1.35 - dist / 700)), 0xFFF6E6, 2.2f);
                    shake(0.3f);
                }
            }
            case CombatVfx.SHOCK -> shake(0.3f + 0.9f * p.strength());
            case CombatVfx.FLAME -> FxEngine.add((s, t, env) -> Blasts.flame(s, t, a.x, a.y, a.z, b.x, b.y, b.z, seed));
            case CombatVfx.WARNING -> {
                float radius = p.strength(), duration = Math.max(0.5f, p.color() / 20f);
                FxEngine.add((s, t, env) -> Blasts.warning(s, t, a.x, a.y, a.z, radius, duration));
            }
            case CombatVfx.MORTAR -> {
                float flight = Math.max(0.5f, p.color() / 20f);
                FxEngine.add((s, t, env) -> Blasts.shell(s, t, a.x, a.y, a.z, b.x, b.y, b.z, flight, seed));
            }
            case CombatVfx.SHIELD -> {
                float radius = p.strength();
                int c = p.color();
                FxEngine.add((s, t, env) -> Blasts.shield(s, t, a.x, a.y, a.z, radius, c));
            }
            case CombatVfx.SLASH -> {
                double fx = b.x - a.x, fz = b.z - a.z;
                FxEngine.add((s, t, env) -> Blasts.slash(s, t, a.x, a.y, a.z, fx, fz, seed));
                if (dist < 4) shake(0.25f);
            }
            case CombatVfx.FALLOUT -> { falloutLevel = Math.max(falloutLevel, p.strength()); falloutTicks = 70; }
            default -> { }
        }
    }

    /** A gun was fired: the round or beam, the muzzle blast, the ejected case, and where it struck. */
    private static void shot(CombatVfx p, Minecraft mc, ClientLevel level, LocalPlayer me, int seed) {
        Vec3 a = p.start(), b = p.end();
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        Vec3 dir = d.scale(1 / len);
        boolean energy = p.kind() == CombatVfx.BEAM;
        int c = p.color();
        // the server starts my own tracer a little ahead of the eye; the view model draws my muzzle flash itself
        boolean mine = mc.options.getCameraType().isFirstPerson() && me.getMainHandItem().getItem() instanceof GunItem
                && a.distanceToSqr(me.getEyePosition()) < 9 && dir.dot(me.getLookAngle()) > 0.9;
        float calibre = Math.min(1.5f, 0.5f * p.strength());
        if (energy) {
            float width = 0.07f * (float) Math.sqrt(Math.max(0.3f, p.strength()));
            FxEngine.add((s, t, env) -> Blasts.beam(s, t, a.x, a.y, a.z, b.x, b.y, b.z, c, width, seed));
        } else {
            FxEngine.add((s, t, env) -> Blasts.tracer(s, t, a.x, a.y, a.z, b.x, b.y, b.z, c, calibre));
        }
        if (!mine) {
            float size = energy ? 0.7f : 0.55f + 0.5f * calibre;
            FxEngine.add((s, t, env) -> Blasts.muzzle(s, t, a.x, a.y, a.z, dir.x, dir.y, dir.z, energy ? c : 0xFFC878, size, energy, seed));
        }
        if (!energy) {
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            Vec3 right = side.normalize();
            Vec3 port = mine ? me.getEyePosition().add(dir.scale(0.45)).add(right.scale(0.22)).add(0, -0.16, 0) : a.subtract(dir.scale(0.45)).add(right.scale(0.08));
            double kick = 1.6 + level.random.nextDouble() * 1.2;
            Vec3 v = right.scale(kick).add(0, 2.2 + level.random.nextDouble(), 0).add(dir.scale(-0.4 + level.random.nextDouble() * 0.5));
            double floor = floorBelow(level, port);
            FxEngine.add((s, t, env) -> Blasts.casing(s, t, port.x, port.y, port.z, v.x, v.y, v.z, floor, seed));
        }
        // where it struck, if it struck a block
        BlockPos hit = BlockPos.containing(b.add(dir.scale(0.06)));
        BlockState state = level.getBlockState(hit);
        if (!state.isAir() && state.getFluidState().isEmpty()) {
            int dust = state.getMapColor(level, hit).col;
            Vec3 n = dir.scale(-1).add(0, 0.45, 0).normalize();
            float delay = energy ? 0 : (float) (len / Blasts.BULLET_SPEED);
            float size = energy ? 0.9f : 0.7f + 0.5f * calibre;
            FxEngine.add((s, t, env) -> Blasts.impact(s, t, b.x, b.y, b.z, n.x, n.y, n.z, dust, size, energy, c, seed), -delay);
        }
    }

    private static double floorBelow(ClientLevel level, Vec3 p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 6; i++) {
            m.set(Math.floor(p.x), Math.floor(p.y) - i, Math.floor(p.z));
            BlockState s = level.getBlockState(m);
            if (!s.getCollisionShape(level, m).isEmpty()) return m.getY() + s.getCollisionShape(level, m).max(net.minecraft.core.Direction.Axis.Y);
        }
        return p.y - 6;
    }

    /** Runs every client tick: ash in the air where fallout hangs, and what radiation sickness feels like. */
    private static void ambience(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || mc.isPaused()) return;
        if (falloutTicks > 0) {
            falloutTicks--;
            if (falloutTicks % 20 == 0) {
                Vec3 at = me.getEyePosition();
                float level = falloutLevel;
                int seed = ++serial * 6151;
                FxEngine.add((s, t, env) -> Blasts.ash(s, t, at.x, at.y, at.z, level, seed, 0.3f + 0.7f * env.daylight));
            }
        } else falloutLevel = 0;

        MobEffectInstance sick = me.getEffect(NukeContent.RADIATION);
        float rate = falloutLevel * 0.35f;                                   // the counter ticks in fallout even inside a suit
        if (sick != null) {
            int severity = Math.min(3, sick.getAmplifier());
            rate = Math.max(rate, 0.3f + 0.23f * severity);
            float pulse = 0.5f + 0.5f * (float) Math.sin(mc.level.getGameTime() * 0.12);
            FxEngine.tint(0.05f + 0.045f * severity + 0.03f * severity * pulse, 0x86C81E);
        }
        if (clickCooldown > 0) clickCooldown--;
        if (rate > 0.01f && clickCooldown == 0 && mc.level.random.nextFloat() < rate * 0.75f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(NukeContent.GEIGER, 0.9f + mc.level.random.nextFloat() * 0.3f, 0.35f + 0.3f * rate));
            clickCooldown = rate > 0.7f ? 0 : 1;
        }
    }
}
