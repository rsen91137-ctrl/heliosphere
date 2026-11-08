package com.heliosphere.rocketry.client;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.DVec;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.client.screen.HelioGui;
import com.heliosphere.core.client.sky.ClientNav;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Flight telemetry during a coast: speed, heliocentric position, distance to target and time to arrival. */
public final class Telemetry {
    static String from, to;
    static long start;
    static int duration;
    static double days;

    private Telemetry() {}

    static void set(String f, String t, long s, int d) {
        from = f; to = t; start = s; duration = d;
        CelestialBody a = SolarSystem.get(f), b = SolarSystem.get(t);
        days = (a != null && b != null) ? OrbitMath.plan(a, b, true, true).transferDays() : 0;
    }

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return duration > 0 && mc.level != null && com.heliosphere.core.env.Planets.isSpace(mc.level);
    }

    /** lines: speed, position, distance, eta-real, eta-days, progress */
    static String[] lines() {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level.getGameTime();
        double p = Math.max(0, Math.min(1, (now - start) / (double) duration));
        double gameDays = now / 24000.0;
        CelestialBody a = SolarSystem.get(from), b = SolarSystem.get(to);
        DVec pa = OrbitMath.position(a, gameDays), pb = OrbitMath.position(b, gameDays);
        double span = pa.sub(pb).length();
        double s = p * p * (3 - 2 * p), ds = 6 * p * (1 - p);
        double kms = days > 0 ? span * Math.max(0.15, ds) / (days * 86400.0) : 0;
        DVec obs = ClientNav.observer(gameDays);
        double au = CelestialBody.AU_KM;
        double remainKm = span * (1 - s);
        int secs = (int) Math.max(0, (duration - (now - start)) / 20);
        return new String[]{
                String.format("速度  %.2f km/s", kms),
                String.format("日心坐标  X %.3f  Y %.3f  Z %.3f AU", obs.x() / au, obs.z() / au, obs.y() / au),
                String.format("距 %s  %s", b.nameZh, remainKm > au * 0.05 ? String.format("%.3f AU", remainKm / au) : String.format("%,.0f km", remainKm)),
                String.format("到达倒计时  %d:%02d  （航程第 %.0f / %.0f 天）", secs / 60, secs % 60, days * s, days),
                String.format("%.1f", p * 100)};
    }

    static void hud(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || mc.player == null || mc.player.getVehicle() != null) return;
        String[] l = lines();
        int w = 230, x = g.guiWidth() - w - 6, y = g.guiHeight() - 74;
        HelioGui.panel(g, x - 3, y - 3, w + 6, 66);
        g.drawString(mc.font, "§b航行遥测 · " + SolarSystem.get(from).nameZh + " → " + SolarSystem.get(to).nameZh, x + 2, y + 1, HelioGui.TEXT, false);
        for (int i = 0; i < 4; i++) g.drawString(mc.font, "§f" + l[i], x + 2, y + 13 + i * 10, HelioGui.TEXT, false);
        HelioGui.hbar(g, x + 2, y + 54, w - 4, 4, Float.parseFloat(l[4]) / 100f, 0xFF4FC3F7);
    }

    public static class ConsoleScreen extends Screen {
        public ConsoleScreen() { super(Component.literal("飞行控制台")); }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            int w = 300, h = 150, x = (width - w) / 2, y = (height - h) / 2;
            HelioGui.panel(g, x, y, w, h);
            if (!active()) { g.drawString(font, "§7当前没有进行中的航行", x + 12, y + 14, HelioGui.TEXT, false); return; }
            String[] l = lines();
            g.drawString(font, "§b飞行控制台 · " + SolarSystem.get(from).nameZh + " → " + SolarSystem.get(to).nameZh, x + 12, y + 12, HelioGui.TEXT, false);
            for (int i = 0; i < 4; i++) {
                g.pose().pushMatrix();
                g.pose().translate(x + 14, y + 32 + i * 22);
                g.pose().scale(1.25f, 1.25f);
                g.drawString(font, "§f" + l[i], 0, 0, HelioGui.TEXT, false);
                g.pose().popMatrix();
            }
            HelioGui.hbar(g, x + 14, y + h - 18, w - 28, 6, Float.parseFloat(l[4]) / 100f, 0xFF4FC3F7);
            g.drawString(font, "§7航程 " + l[4] + "%", x + w - 14 - font.width("航程 " + l[4] + "%"), y + h - 30, HelioGui.TEXT, false);
        }

        @Override public boolean isPauseScreen() { return false; }
    }
}
