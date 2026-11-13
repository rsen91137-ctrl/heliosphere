package com.heliosphere.planets.gear.client;

import com.heliosphere.core.env.Environment;
import com.heliosphere.planets.gear.JetpackItem;
import com.heliosphere.planets.gear.JetpackKind;
import com.heliosphere.planets.gear.Jetpacks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * The flight, computed by the wearer's own game before the player moves each tick (so it is as smooth as walking):
 * jump held in the air thrusts up (gravity of the world is taken off the pack's thrust), with side thrusters the
 * movement keys push you about, and sneaking in the air holds you still.
 */
public final class JetpackClient {
    private JetpackClient() {}

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(JetpackClient::tick);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        JetpackItem j = Jetpacks.worn(p);
        if (j == null || p.getAbilities().flying || p.isPassenger() || p.isInWater() || p.isFallFlying()) return;
        if (JetpackItem.fuel(p.getItemBySlot(EquipmentSlot.CHEST)) <= 0) return;
        JetpackKind k = j.kind;
        var keys = p.input.keyPresses;
        boolean air = !p.onGround();
        double g = 0.08 * Environment.gravity(mc.level);
        Vec3 v = p.getDeltaMovement();
        double vx = v.x, vy = v.y, vz = v.z;
        if (keys.jump() && air) {
            double a = k.thrust(g / 0.08);
            if (vy < k.maxRise) vy = Math.min(k.maxRise + g, vy + a);
        } else if (k.hold && keys.shift() && air) {               // hold still: cancel the fall and most drift
            vy = vy * 0.6 + g;
            vx *= 0.85; vz *= 0.85;
        }
        if (k.side > 0 && air) {
            double f = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0), s = (keys.left() ? 1 : 0) - (keys.right() ? 1 : 0);
            if (f != 0 || s != 0) {
                double yaw = Math.toRadians(p.getYRot());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
                double len = Math.hypot(f, s);
                vx += (fx * f + sx * s) / len * k.side;
                vz += (fz * f + sz * s) / len * k.side;
                double h = Math.hypot(vx, vz), cap = 0.6;
                if (h > cap) { vx *= cap / h; vz *= cap / h; }
            }
        }
        if (vx != v.x || vy != v.y || vz != v.z) { p.setDeltaMovement(vx, vy, vz); p.resetFallDistance(); }
    }
}
