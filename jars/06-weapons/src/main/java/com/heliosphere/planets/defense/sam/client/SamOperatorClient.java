package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.planets.defense.sam.SamKind;
import com.heliosphere.planets.defense.sam.SamNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The operator's station on screen: a box in the sky around every target the vehicle sees (the selected one
 * bracketed in red with its range), the vehicle's state, and the keys: R next target, F fire.
 */
public final class SamOperatorClient {
    private SamOperatorClient() {}

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("heliosphere", "air_defence"));
    private static KeyMapping next, fire;
    private static volatile SamNet.Picture picture = SamNet.Picture.OFF;
    private static volatile long stamp;

    static void init() {
        next = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.heliosphere.sam_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        fire = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.heliosphere.sam_fire", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(SamNet.Picture.TYPE, (p, ctx) -> { picture = p; stamp = System.currentTimeMillis(); });
        ClientPlayNetworking.registerGlobalReceiver(SamNet.Panel.TYPE, (p, ctx) -> ctx.client().execute(() -> ctx.client().setScreen(new SamPanelScreen(p))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!operating()) { while (next.consumeClick()) { } while (fire.consumeClick()) { } return; }
            while (next.consumeClick()) ClientPlayNetworking.send(new SamNet.Action(SamNet.NEXT, 0));
            while (fire.consumeClick()) ClientPlayNetworking.send(new SamNet.Action(SamNet.FIRE, 0));
        });
        HudRenderCallback.EVENT.register((g, delta) -> hud(g));
    }

    static boolean operating() { return picture.on() && System.currentTimeMillis() - stamp < 1500; }

    private static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!operating() || mc.options.hideGui || mc.player == null) return;
        SamNet.Picture p = picture;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (!p.driving()) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 eye = cam.position();
            double yaw = Math.toRadians(cam.yRot()), pitch = Math.toRadians(cam.xRot());
            Vec3 f = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3 r = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 u = r.cross(f);
            double k = (h / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
            for (int i = 0; i < p.targets().size(); i++) {
                double[] t = p.targets().get(i);
                Vec3 d = new Vec3(t[0], t[1], t[2]).subtract(eye);
                double z = d.dot(f);
                if (z < 0.5) continue;
                int sx = (int) (w / 2.0 + d.dot(r) / z * k), sy = (int) (h / 2.0 - d.dot(u) / z * k);
                boolean sel = i == p.selected();
                box(g, sx, sy, sel ? 9 : 6, sel ? 0xFFFF4030 : t[4] > 0 ? 0xFFFFD040 : 0xFF60FF80);
                if (sel) g.drawString(mc.font, String.format("%.0f m", t[3]), sx + 12, sy - 4, 0xFFFF6050);
            }
        }
        if (!p.driving()) scope(g, mc, p, w, h);
        // the panel: vehicle, mode, ammunition, keys
        SamKind kind = SamKind.values()[Math.max(0, Math.min(SamKind.values().length - 1, p.kind()))];
        int x = w / 2 - 115, y = 6;                                         // top centre: clear of the chat and the hotbar
        g.fill(x - 4, y - 4, x + 230, y + 46, 0x90101418);
        g.drawString(mc.font, Component.translatable("block.heliosphere." + kind.id), x, y, 0xFFFFFFFF);
        Component state = p.driving() ? Component.translatable(p.stowing() ? "hud.heliosphere.sam_stowing" : "hud.heliosphere.sam_driving")
                : kind.radar ? Component.translatable("hud.heliosphere.sam_tracks", p.targets().size())
                : Component.translatable("hud.heliosphere.sam_state", Component.translatable(p.manual() ? "message.heliosphere.sam_mode_manual" : "message.heliosphere.sam_mode_auto"),
                        p.ammo(), p.magazine(), p.targets().size());
        g.drawString(mc.font, state, x, y + 12, 0xFFFFD060);
        g.drawString(mc.font, Component.translatable(p.driving() ? "hud.heliosphere.sam_keys_drive" : "hud.heliosphere.sam_keys"), x, y + 26, 0xFFB0B0B0);
    }

    /**
     * The radar scope, heading up as in War Thunder: range rings, a rotating sweep, a blip for every contact
     * (green hostile, yellow a player, red the selected target), and the IFF state.
     */
    private static void scope(GuiGraphics g, Minecraft mc, SamNet.Picture p, int w, int h) {
        int r = 46, cx = w - r - 12, cy = h - r - 12;
        double range = Math.max(64, p.range());
        for (int y = -r; y <= r; y++) {                                  // disc
            int half = (int) Math.sqrt(r * r - y * y);
            g.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xA0081408);
        }
        for (int ring = 1; ring <= 3; ring++) circle(g, cx, cy, r * ring / 3, 0x6040C060);
        g.fill(cx - r, cy, cx + r, cy + 1, 0x4040C060); g.fill(cx, cy - r, cx + 1, cy + r, 0x4040C060);
        double sweep = (System.currentTimeMillis() % 2400) / 2400.0 * Math.PI * 2;
        for (int k = 0; k < r; k++) {
            int x = cx + (int) (Math.sin(sweep) * k), y = cy - (int) (Math.cos(sweep) * k);
            g.fill(x, y, x + 1, y + 1, 0xC060FF70);
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(cam.yRot());
        Vec3 eye = cam.position();
        for (int i = 0; i < p.targets().size(); i++) {
            double[] t = p.targets().get(i);
            double dx = t[0] - eye.x, dz = t[2] - eye.z;
            double fwd = -Math.sin(yaw) * dx + Math.cos(yaw) * dz, right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
            double dist = Math.hypot(dx, dz);
            if (dist > range) { double k = range / dist; fwd *= k; right *= k; }
            int bx = cx + (int) (right / range * r), by = cy - (int) (fwd / range * r);
            int c = i == p.selected() ? 0xFFFF4030 : t[4] > 0 ? 0xFFFFD040 : 0xFF70FF80;
            g.fill(bx - 1, by - 1, bx + 2, by + 2, c);
            if (i == p.selected()) { g.fill(bx - 3, by - 3, bx + 4, by - 2, c); g.fill(bx - 3, by + 3, bx + 4, by + 4, c); }
        }
        g.drawString(mc.font, String.format("%.0f m", range), cx - r, cy - r - 10, 0xFF70FF80);
        g.drawString(mc.font, Component.translatable(p.iff() ? "hud.heliosphere.sam_iff_on" : "hud.heliosphere.sam_iff_off"), cx - r, cy + r + 2, p.iff() ? 0xFF80FF90 : 0xFFFF7060);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int a = 0; a < 72; a++) {
            double t = a / 72.0 * Math.PI * 2;
            int x = cx + (int) Math.round(Math.sin(t) * r), y = cy + (int) Math.round(Math.cos(t) * r);
            g.fill(x, y, x + 1, y + 1, c);
        }
    }

    private static void box(GuiGraphics g, int x, int y, int s, int c) {
        int l = Math.max(3, s / 2);
        g.fill(x - s, y - s, x - s + l, y - s + 1, c); g.fill(x - s, y - s, x - s + 1, y - s + l, c);
        g.fill(x + s - l, y - s, x + s, y - s + 1, c); g.fill(x + s - 1, y - s, x + s, y - s + l, c);
        g.fill(x - s, y + s - 1, x - s + l, y + s, c); g.fill(x - s, y + s - l, x - s + 1, y + s, c);
        g.fill(x + s - l, y + s - 1, x + s, y + s, c); g.fill(x + s - 1, y + s - l, x + s, y + s, c);
        g.fill(x - 1, y, x + 2, y + 1, c); g.fill(x, y - 1, x + 1, y + 2, c);
    }
}
