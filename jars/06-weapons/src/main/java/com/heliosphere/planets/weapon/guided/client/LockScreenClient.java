package com.heliosphere.planets.weapon.guided.client;

import com.heliosphere.planets.weapon.guided.SmartLauncher;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The L-7's lock screen: the seeker's field ring in the middle, brackets closing on the target while the lock
 * builds, a red box with LOCK, range and guidance once it holds; an arrow at the edge when the target is off screen.
 */
public final class LockScreenClient {
    private LockScreenClient() {}

    private static volatile SmartLauncher.Seeker seeker = SmartLauncher.Seeker.OFF;
    private static volatile long stamp;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SmartLauncher.Seeker.TYPE, (k, ctx) -> { seeker = k; stamp = System.currentTimeMillis(); });
        HudRenderCallback.EVENT.register((g, d) -> draw(g));
    }

    private static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        SmartLauncher.Seeker k = seeker;
        if (!k.on() || System.currentTimeMillis() - stamp > 600 || mc.player == null || mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight(), cx = w / 2, cy = h / 2;
        double fov = Math.toRadians(mc.options.fov().get());
        double f = (h / 2.0) / Math.tan(fov / 2);
        int ring = (int) (Math.tan(Math.toRadians(5)) * f);                  // the 5-degree lock cone
        for (int a = 0; a < 48; a += 2) {
            double t = a / 48.0 * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(t) * ring), y = cy + (int) Math.round(Math.sin(t) * ring);
            g.fill(x, y, x + 1, y + 1, 0xA0FFFFFF);
        }
        if (k.name().isEmpty()) {
            g.drawCenteredString(mc.font, Component.translatable("hud.heliosphere.l7_search"), cx, cy + ring + 6, 0xFFB0B0B0);
            return;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.position();
        double yaw = Math.toRadians(cam.yRot()), pitch = Math.toRadians(cam.xRot());
        Vec3 fw = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 r = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 u = r.cross(fw);
        Vec3 d = new Vec3(k.x(), k.y(), k.z()).subtract(eye);
        double z = d.dot(fw);
        int color = k.locked() ? 0xFFFF3020 : 0xFFFFC030;
        if (z < 0.5) {                                                       // behind: an arrow on the ring
            double ang = Math.atan2(-d.dot(u), d.dot(r));
            int ax = cx + (int) (Math.cos(ang) * (ring + 10)), ay = cy + (int) (Math.sin(ang) * (ring + 10));
            g.fill(ax - 2, ay - 2, ax + 3, ay + 3, color);
            return;
        }
        int sx = (int) (cx + d.dot(r) / z * f), sy = (int) (cy - d.dot(u) / z * f);
        int size = k.locked() ? 10 : (int) (10 + (1 - k.progress()) * 22);  // the brackets close in as the lock builds
        int l = Math.max(3, size / 2);
        g.fill(sx - size, sy - size, sx - size + l, sy - size + 1, color); g.fill(sx - size, sy - size, sx - size + 1, sy - size + l, color);
        g.fill(sx + size - l, sy - size, sx + size, sy - size + 1, color); g.fill(sx + size - 1, sy - size, sx + size, sy - size + l, color);
        g.fill(sx - size, sy + size - 1, sx - size + l, sy + size, color); g.fill(sx - size, sy + size - l, sx - size + 1, sy + size, color);
        g.fill(sx + size - l, sy + size - 1, sx + size, sy + size, color); g.fill(sx + size - 1, sy + size - l, sx + size, sy + size, color);
        if (k.locked()) { g.fill(sx - 1, sy - 1, sx + 2, sy + 2, color); }
        String label = k.locked() ? Component.translatable("hud.heliosphere.l7_lock").getString() : Component.translatable("hud.heliosphere.l7_locking", (int) (k.progress() * 100)).getString();
        g.drawString(mc.font, label, sx + size + 4, sy - size, color);
        g.drawString(mc.font, k.name() + " · " + k.range() + " m", sx + size + 4, sy - size + 10, 0xFFFFFFFF);
        if (k.locked()) g.drawString(mc.font, Component.translatable(k.fast() ? "message.heliosphere.guidance_pn" : "message.heliosphere.guidance_pursuit"), sx + size + 4, sy - size + 20, 0xFFB0E0FF);
        // lock progress bar under the ring
        int bw = 60, bx = cx - bw / 2, by = cy + ring + 6;
        g.fill(bx, by, bx + bw, by + 3, 0x80000000);
        g.fill(bx, by, bx + (int) (bw * (k.locked() ? 1 : k.progress())), by + 3, color);
    }
}
