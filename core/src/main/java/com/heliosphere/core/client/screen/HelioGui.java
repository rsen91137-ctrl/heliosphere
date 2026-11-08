package com.heliosphere.core.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Flat sci-fi panel style used by all Heliosphere screens (drawn with fills, no texture needed). */
public final class HelioGui {
    public static final int BG = 0xFF1B2230, BG2 = 0xFF232C3D, EDGE_LIGHT = 0xFF3F5272, EDGE_DARK = 0xFF0C1018;
    public static final int ACCENT = 0xFF4FC3F7, TEXT = 0xFFDDE6F3, DIM = 0xFF8A98AE, GOOD = 0xFF66E08A, WARN = 0xFFFFC857, BAD = 0xFFFF6B6B;

    private HelioGui() {}

    private static final Map<String, List<FormattedCharSequence>> WRAP_CACHE = new HashMap<>();
    private static final String NO_BREAK_BEFORE = "，。；：、）》」』！？…,.;:)!?%”’·~";
    private static final String NO_BREAK_AFTER = "（《「『“‘(•";

    /**
     * Word-wraps text that mixes Chinese and Latin script. Vanilla wrapping only breaks at spaces, which pushes
     * whole Chinese sentences onto the next line; here a line may break between any two CJK characters
     * (respecting punctuation rules) and section-sign formatting is carried across lines.
     */
    public static List<FormattedCharSequence> wrap(Font font, String text, int width) {
        String key = width + "|" + text;
        List<FormattedCharSequence> cached = WRAP_CACHE.get(key);
        if (cached != null) return cached;
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(FormattedCharSequence.EMPTY); continue; }
            String carry = "";
            int i = 0, n = para.length();
            while (i < n) {
                int j = i, lastBreak = -1;
                while (j < n) {
                    char ch = para.charAt(j);
                    if (ch == '\u00a7' && j + 1 < n) { j += 2; continue; }
                    int next = j + Character.charCount(para.codePointAt(j));
                    if (j > i && font.width(carry + para.substring(i, next)) > width) break;
                    j = next;
                    if (j < n && canBreak(para, j) && font.width(para.substring(i, j)) > width / 5) lastBreak = j;
                }
                int end = j >= n ? n : lastBreak > i ? lastBreak : j;
                String seg = para.substring(i, end);
                out.add(Component.literal(carry + seg.stripTrailing()).getVisualOrderText());
                carry = activeFormat(carry + seg);
                i = end;
                while (i < n && para.charAt(i) == ' ') i++;
            }
        }
        if (WRAP_CACHE.size() > 512) WRAP_CACHE.clear();
        WRAP_CACHE.put(key, out);
        return out;
    }

    private static boolean isCjk(char c) { return c >= 0x2E80 && !(c >= 0xFF01 && c <= 0xFF5E && Character.isLetterOrDigit(c)); }

    private static boolean canBreak(String s, int at) {
        char prev = s.charAt(at - 1), next = s.charAt(at);
        if (NO_BREAK_BEFORE.indexOf(next) >= 0 || NO_BREAK_AFTER.indexOf(prev) >= 0) return false;
        if (prev == ' ') return true;
        if (next == ' ') return false;
        return isCjk(prev) || isCjk(next);
    }

    private static String activeFormat(String s) {
        StringBuilder colour = new StringBuilder(), style = new StringBuilder();
        for (int k = 0; k + 1 < s.length(); k++) {
            if (s.charAt(k) != '\u00a7') continue;
            char c = Character.toLowerCase(s.charAt(k + 1));
            if ("0123456789abcdefr".indexOf(c) >= 0) { colour.setLength(0); style.setLength(0); if (c != 'r') colour.append('\u00a7').append(c); }
            else if ("klmno".indexOf(c) >= 0) style.append('\u00a7').append(c);
            k++;
        }
        return colour.toString() + style;
    }

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
