package com.heliosphere.core.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Flat sci-fi panel style used by all Heliosphere screens (drawn with fills, no texture needed). */
public final class HelioGui {
    public static final int BG = 0xFF1B2230, BG2 = 0xFF232C3D, EDGE_LIGHT = 0xFF3F5272, EDGE_DARK = 0xFF0C1018;
    public static final int ACCENT = 0xFF4FC3F7, TEXT = 0xFFDDE6F3, DIM = 0xFF8A98AE, GOOD = 0xFF66E08A, WARN = 0xFFFFC857, BAD = 0xFFFF6B6B;

    private HelioGui() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 1, EDGE_LIGHT);
        g.fill(x, y, x + 1, y + h, EDGE_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_DARK);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_DARK);
        g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x3346A0D8);
        // corner brackets
        int c = ACCENT;
        g.fill(x + 2, y + 2, x + 8, y + 3, c); g.fill(x + 2, y + 2, x + 3, y + 8, c);
        g.fill(x + w - 8, y + 2, x + w - 2, y + 3, c); g.fill(x + w - 3, y + 2, x + w - 2, y + 8, c);
        g.fill(x + 2, y + h - 3, x + 8, y + h - 2, c); g.fill(x + 2, y + h - 8, x + 3, y + h - 2, c);
        g.fill(x + w - 8, y + h - 3, x + w - 2, y + h - 2, c); g.fill(x + w - 3, y + h - 8, x + w - 2, y + h - 2, c);
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF10151F);
        g.fill(x, y, x + w, y + 1, EDGE_DARK);
        g.fill(x, y, x + 1, y + h, EDGE_DARK);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF34425C);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF34425C);
    }

    public static void slot(GuiGraphics g, int x, int y) { inset(g, x - 1, y - 1, 18, 18); }

    public static void bigSlot(GuiGraphics g, int x, int y) {
        inset(g, x - 5, y - 5, 26, 26);
        g.fill(x - 5, y - 5, x + 21, y - 4, 0x6633CCFF);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        inset(g, x, y, w, h);
        int fh = Math.round((h - 2) * Math.max(0, Math.min(1, frac)));
        g.fill(x + 1, y + h - 1 - fh, x + w - 1, y + h - 1, color);
        for (int yy = y + h - 4; yy > y + 1; yy -= 4) g.fill(x + 1, yy, x + w - 1, yy + 1, 0x33000000);
    }

    public static void hbar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        inset(g, x, y, w, h);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, frac)));
        g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
    }

    public static void arrow(GuiGraphics g, int x, int y, float frac) {
        int w = 24;
        g.fill(x, y + 6, x + w - 6, y + 10, 0xFF2C3850);
        for (int i = 0; i < 8; i++) g.fill(x + w - 6 + i, y + i, x + w - 5 + i, y + 16 - i, 0xFF2C3850);
        int fw = Math.round(w * frac);
        if (fw > 0) {
            g.fill(x, y + 6, x + Math.min(fw, w - 6), y + 10, ACCENT);
            for (int i = 0; i < 8 && w - 6 + i < fw; i++) g.fill(x + w - 6 + i, y + i, x + w - 5 + i, y + 16 - i, ACCENT);
        }
    }
}
