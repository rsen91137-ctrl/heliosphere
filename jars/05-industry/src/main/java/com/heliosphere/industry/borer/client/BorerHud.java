package com.heliosphere.industry.borer.client;

import com.heliosphere.industry.borer.TunnelBorer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * The driver's panel on screen while riding a borer: a compact box at the bottom centre, above the action-bar line
 * (so neither the hotbar, the hearts nor the machine's messages are covered): mode and heading, status, the energy
 * and bin bars with numbers, the odometers and slope, the settings as small labels, and the keys.
 */
final class BorerHud {
    private BorerHud() {}

    private static final int WIDTH = 300, PAD = 4;

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        TunnelBorer b = BorerClient.riding(mc);
        if (b == null || mc.options.hideGui || mc.screen != null || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int pw = Math.min(WIDTH, w - 16), inner = pw - 2 * PAD;
        Component keys = Component.translatable("hud.heliosphere.borer.keys", BorerClient.autoKey.getTranslatedKeyMessage());
        List<FormattedCharSequence> keyLines = font.split(keys, inner);
        if (keyLines.size() > 3) keyLines = keyLines.subList(0, 3);
        int ph = PAD + 10 + 11 + 14 + 11 + 13 + keyLines.size() * 9 + PAD - 2;
        int x0 = (w - pw) / 2, y0 = h - 76 - ph;                        // the action bar sits at h-72..h-63
        if (y0 < h / 2 + 14) y0 = 4;                                    // no room under the crosshair: top centre instead
        g.fill(x0, y0, x0 + pw, y0 + ph, 0xA0101418);
        g.fill(x0, y0, x0 + pw, y0 + 1, 0xFFF2B62C);
        int x = x0 + PAD, y = y0 + PAD, right = x0 + pw - PAD;

        // mode, heading and height
        Component mode = !b.auto() ? Component.translatable("hud.heliosphere.borer.mode.manual")
                : b.autoLeft() < 0 ? Component.translatable("hud.heliosphere.borer.mode.auto_unlimited")
                : Component.translatable("hud.heliosphere.borer.mode.auto", b.autoLeft());
        g.drawString(font, mode, x, y, b.auto() ? 0xFF7CFFB0 : 0xFFFFD060);
        Component facing = Component.translatable("hud.heliosphere.borer.facing",
                Component.translatable("hud.heliosphere.borer.dir." + b.facing().getName()), Mth.floor(b.getY()));
        g.drawString(font, facing, right - font.width(facing), y, 0xFFD0D8E0);
        y += 10;

        // status
        g.drawString(font, Component.translatable(b.statusKey()), x, y, statusColour(b.status()));
        y += 11;

        // energy and bin
        int half = (inner - PAD) / 2;
        float energy = b.flag(TunnelBorer.CREATIVE) ? 1f : b.energy() / (float) Math.max(1, b.capacity());
        bar(g, font, x, y, half, energy, 0xB030B8F0,
                Component.translatable("screen.heliosphere.borer.energy", compact(b.energy()), compact(b.capacity())));
        bar(g, font, x + half + PAD, y, inner - half - PAD, b.binUsed() / (float) Math.max(1, b.binSize()),
                b.binUsed() >= b.binSize() ? 0xB0E04030 : 0xB0E09A30,
                Component.translatable("screen.heliosphere.borer.bin", b.binUsed(), b.binSize()));
        y += 14;

        // odometers and slope
        Component dug = Component.translatable("screen.heliosphere.borer.dug", b.dug(), b.ores());
        g.drawString(font, dug, x, y, 0xFFC8CCD0);
        Component slope = Component.translatable("screen.heliosphere.borer.slope", slopeName(b.slope()));
        if (font.width(dug) + font.width(slope) + 8 <= inner) g.drawString(font, slope, right - font.width(slope), y, 0xFFC8CCD0);
        y += 11;

        // settings: lit when on
        chips(g, font, x, y, inner, b);
        y += 13;

        // keys
        for (FormattedCharSequence line : keyLines) {
            g.drawString(font, line, x, y, 0xFF9CA0A4);
            y += 9;
        }
    }

    /** A bar with its numbers written on it (shrunk to fit when the language is wordy). */
    private static void bar(GuiGraphics g, Font font, int x, int y, int w, float f, int argb, Component text) {
        g.fill(x, y, x + w, y + 11, 0xC0000000);
        int fw = Math.round((w - 2) * Mth.clamp(f, 0f, 1f));
        if (fw > 0) g.fill(x + 1, y + 1, x + 1 + fw, y + 10, argb);
        int tw = font.width(text);
        if (tw <= w - 4) {
            g.drawString(font, text, x + (w - tw) / 2, y + 2, 0xFFFFFFFF);
        } else {
            float k = Math.max(0.5f, (w - 4) / (float) tw);
            g.pose().pushMatrix();
            g.pose().translate(x + 2, y + 2 + 8 * (1 - k) / 2);
            g.pose().scale(k, k);
            g.drawString(font, text, 0, 0, 0xFFFFFFFF);
            g.pose().popMatrix();
        }
    }

    /** Lining, side mining, lights and junk as small labels; the row shrinks if it does not fit. */
    private static void chips(GuiGraphics g, Font font, int x, int y, int width, TunnelBorer b) {
        Component[] labels = {
                shortName("screen.heliosphere.borer.lining"), shortName("screen.heliosphere.borer.veins"),
                shortName("screen.heliosphere.borer.lights"),
                Component.translatable("screen.heliosphere.borer.junk", Component.translatable(b.flag(TunnelBorer.DISCARD_JUNK)
                        ? "screen.heliosphere.borer.junk.discard" : "screen.heliosphere.borer.junk.keep"))};
        boolean[] on = {b.flag(TunnelBorer.LINING), b.flag(TunnelBorer.VEINS), b.flag(TunnelBorer.LIGHTS), b.flag(TunnelBorer.DISCARD_JUNK)};
        int[] colour = {0xFF3A8A50, 0xFF3A70A8, 0xFF9A8030, 0xFF8A5A30};
        int total = -3;
        for (Component c : labels) total += font.width(c) + 6 + 3;
        float k = total > width ? Math.max(0.5f, width / (float) total) : 1f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(k, k);
        int cx = 0;
        for (int i = 0; i < labels.length; i++) {
            int cw = font.width(labels[i]) + 6;
            g.fill(cx, 0, cx + cw, 11, on[i] ? colour[i] : 0x60404448);
            g.drawString(font, labels[i], cx + 3, 2, on[i] ? 0xFFFFFFFF : 0xFF8A8E92, false);
            cx += cw + 3;
        }
        g.pose().popMatrix();
    }

    /** The name part of a "name: %s" panel line ("自动衬砌：%s" gives "自动衬砌"). */
    static Component shortName(String key) {
        String s = Component.translatable(key, "\u0001").getString();
        int i = s.indexOf('\u0001');
        String name = (i >= 0 ? s.substring(0, i) : s).replaceAll("[\\s:：]+$", "");
        return name.isEmpty() ? Component.translatable(key, "") : Component.literal(name);
    }

    static Component slopeName(int slope) {
        return Component.translatable(slope > 0 ? "screen.heliosphere.borer.slope.up"
                : slope < 0 ? "screen.heliosphere.borer.slope.down" : "screen.heliosphere.borer.slope.level");
    }

    /** Text colour of a status: green working, blue turning or reversing, red stopped by something, grey idle. */
    static int statusColour(int status) {
        return switch (status) {
            case TunnelBorer.S_BORING -> 0xFF7CFF8C;
            case TunnelBorer.S_DONE -> 0xFFB8FFB0;
            case TunnelBorer.S_TURNING, TunnelBorer.S_REVERSING -> 0xFF8CD8FF;
            case TunnelBorer.S_IDLE -> 0xFFC0C4C8;
            default -> 0xFFFF6A5A;
        };
    }

    /** 123456 as "123.5k", 200000 as "200k". */
    static String compact(int n) {
        if (Math.abs(n) < 10_000) return String.format(Locale.ROOT, "%,d", n);
        String s = String.format(Locale.ROOT, "%.1f", n / 1000.0);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + "k";
    }
}
