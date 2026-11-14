package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MissileSpec;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * The missile camera, after the one in War Thunder: hold U and the view rides behind the last missile you fired
 * (your launcher, your nuke, or the air-defence vehicle you are aboard); drag the mouse to look round it, scroll to
 * move in or out. When it goes off, the view stays over the spot for a moment and then comes home; letting go of U
 * comes home at once. Your character stays where it is and can still be moved.
 */
public final class MissileCamera {
    private MissileCamera() {}

    private static KeyMapping key;
    /** Network id of the newest missile of ours, -1 none. */
    private static int tracked = -1;
    private static final Set<Integer> seen = new HashSet<>();
    private static boolean active;
    /** Mouse look round the missile, degrees from straight behind it. */
    private static float dragYaw, dragPitch;
    private static double distance = 7;
    /** After the missile is gone: where it was, which way it flew, and how much longer to watch. */
    private static Vec3 lastPos;
    private static float lastYaw, lastPitch;
    private static int linger;
    private static boolean wasDown;
    private static String spec = "";

    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.heliosphere.missile_camera", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, SamOperatorClient.CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(MissileCamera::tick);
        HudRenderCallback.EVENT.register((g, delta) -> hud(g, delta.getGameTimeDeltaPartialTick(true)));
    }

    /** Fired by this player: directly, or by the vehicle the player is aboard. */
    private static boolean ours(GuidedMissile m, Entity p) {
        int o = m.ownerId();
        return o != -1 && (o == p.getId() || p.getVehicle() != null && o == p.getVehicle().getId());
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { tracked = -1; seen.clear(); active = false; linger = 0; return; }
        Set<Integer> present = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof GuidedMissile m) || m.spec() == MissileSpec.ASTER_BOOSTER || !ours(m, mc.player)) continue;
            present.add(m.getId());
            if (seen.add(m.getId())) tracked = m.getId();               // a new one: the last fired is the one followed
        }
        seen.retainAll(present);
        boolean down = key.isDown() && mc.screen == null;
        Entity m = tracked == -1 ? null : mc.level.getEntity(tracked);
        boolean flying = m instanceof GuidedMissile && !m.isRemoved();
        if (flying) {
            lastPos = m.position(); lastYaw = m.getYRot(); lastPitch = m.getXRot();
            spec = ((GuidedMissile) m).spec().name();
            linger = 30;
        } else if (linger > 0 && active) linger--;
        if (down && !wasDown && !flying && linger == 0)
            mc.player.displayClientMessage(Component.translatable("hud.heliosphere.missile_cam_none"), true);
        if (!down) { dragYaw = 0; dragPitch = 0; if (!flying) linger = 0; }
        wasDown = down;
        active = down && (flying || linger > 0 && lastPos != null);
    }

    public static boolean active() { return active; }

    /** For tests: the key, the followed missile's network id, the view distance and the drag. */
    public static KeyMapping key() { return key; }
    public static int tracked() { return tracked; }
    public static double[] state() { return new double[]{distance, dragYaw, dragPitch, linger}; }

    /** Mouse movement while watching: look round the missile instead of turning the player. */
    public static void drag(double dx, double dy) {
        dragYaw += (float) dx * 0.15f;
        dragPitch = Mth.clamp(dragPitch + (float) dy * 0.15f, -80, 80);
    }

    /** Scroll wheel while watching: in and out. */
    public static void zoom(double dy) { distance = Mth.clamp(distance - dy * 1.2, 3, 40); }

    /** The view this frame: {x, y, z, yaw, pitch}, or null. */
    public static double[] pose(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.level == null) return null;
        Entity m = tracked == -1 ? null : mc.level.getEntity(tracked);
        Vec3 at;
        float yaw, pitch;
        double dist = distance;
        if (m instanceof GuidedMissile && !m.isRemoved()) {
            at = m.getPosition(partial);
            yaw = m.getViewYRot(partial); pitch = m.getViewXRot(partial);
        } else {                                                            // gone: watch the spot from further back
            at = lastPos; yaw = lastYaw; pitch = lastPitch;
            dist = distance + (30 - linger) * 0.4;
        }
        float camYaw = yaw + dragYaw;
        float camPitch = Mth.clamp(pitch * 0.6f + 12 + dragPitch, -85, 85);    // a little above the missile, looking down its line
        Vec3 look = Vec3.directionFromRotation(camPitch, camYaw);
        Vec3 want = at.subtract(look.scale(dist));
        var hit = mc.level.clip(new ClipContext(at, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        Vec3 eye = hit.getType() == HitResult.Type.MISS ? want : hit.getLocation().add(look.scale(0.4));   // never inside a hill
        return new double[]{eye.x, eye.y, eye.z, camYaw, camPitch};
    }

    // ---- what the view shows ----

    private static void hud(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.options.hideGui || mc.level == null) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        Entity m = tracked == -1 ? null : mc.level.getEntity(tracked);
        Component name = Component.translatable("missile.heliosphere." + spec.toLowerCase(java.util.Locale.ROOT));
        Component line;
        if (m instanceof GuidedMissile gm && !m.isRemoved()) {
            double speed = Math.max(m.getDeltaMovement().length(), m.position().subtract(m.xo, m.yo, m.zo).length()) * 20;
            Entity t = gm.targetId() == -1 ? null : mc.level.getEntity(gm.targetId());
            if (t != null) {
                line = Component.translatable("hud.heliosphere.missile_cam_target", name, (int) speed, (int) t.distanceTo(m), t.getDisplayName());
                marker(g, mc, t.getBoundingBox().getCenter(), partial);
            } else if (gm.targetId() != -1) line = Component.translatable("hud.heliosphere.missile_cam_far", name, (int) speed, gm.flightAge() / 20);
            else line = Component.translatable("hud.heliosphere.missile_cam_free", name, (int) speed, gm.flightAge() / 20);
        } else line = Component.translatable("hud.heliosphere.missile_cam_gone", name);
        Component keys = Component.translatable("hud.heliosphere.missile_cam_keys");
        int bw = Math.max(mc.font.width(line), mc.font.width(keys)) + 12, y = 36;         // top centre, under the countdown banner, clear of the chat
        g.fill(w / 2 - bw / 2, y, w / 2 + bw / 2, y + 26, 0x90101418);
        g.drawCenteredString(mc.font, line, w / 2, y + 4, 0xFFFFD060);
        g.drawCenteredString(mc.font, keys, w / 2, y + 15, 0xFFB0C0D0);
    }

    /** A red box round the missile's target on screen. */
    private static void marker(GuiGraphics g, Minecraft mc, Vec3 p, float partial) {
        double[] v = pose(partial);
        if (v == null) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double yaw = Math.toRadians(v[3]), pitch = Math.toRadians(v[4]);
        Vec3 f = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 r = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 u = r.cross(f);
        Vec3 d = p.subtract(v[0], v[1], v[2]);
        double z = d.dot(f);
        if (z < 0.5) return;
        double k = (h / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        int sx = (int) (w / 2.0 + d.dot(r) / z * k), sy = (int) (h / 2.0 - d.dot(u) / z * k);
        int s = 7, c = 0xFFFF4030;
        g.fill(sx - s, sy - s, sx + s, sy - s + 1, c); g.fill(sx - s, sy + s - 1, sx + s, sy + s, c);
        g.fill(sx - s, sy - s, sx - s + 1, sy + s, c); g.fill(sx + s - 1, sy - s, sx + s, sy + s, c);
    }
}
