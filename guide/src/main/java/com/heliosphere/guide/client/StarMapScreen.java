package com.heliosphere.guide.client;

import com.heliosphere.core.client.screen.HelioGui;
import com.heliosphere.core.story.Story;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Star map of the solar neighbourhood, plotted from real right ascension / declination and distance
 * (galactic-plane-free equatorial projection). Only Proxima is open in this volume; the rest are unknown.
 */
public class StarMapScreen extends Screen {
    /** name, RA hours, Dec degrees, distance ly, colour, info, open */
    private record Star(String name, double ra, double dec, double ly, int color, String info, boolean open) {}

    private static final List<Star> STARS = List.of(
            new Star("太阳", 0, 0, 0, 0xFFF4D6, "G2V 黄矮星 · 我们的家园\n八大行星与无数小天体", true),
            new Star("比邻星", 14.495, -62.68, 4.246, 0xFF7050, "M5.5Ve 红矮星 · 半人马α C\n行星：比邻星 b（宜居带，潮汐锁定）、比邻星 d、比邻星 c（候选）\n§c信号源：半人马文明前哨\n§e第一目标", true),
            new Star("半人马α A", 14.660, -60.83, 4.37, 0xFFF0C0, "G2V · 与太阳相似的恒星\n§7尚未探明", false),
            new Star("半人马α B", 14.660, -60.84, 4.37, 0xFFC080, "K1V 橙矮星\n§7尚未探明", false),
            new Star("巴纳德星", 17.963, 4.69, 5.96, 0xFF6040, "M4V 红矮星 · 自行最快的恒星\n§7尚未探明", false),
            new Star("沃尔夫 359", 10.941, 7.01, 7.86, 0xFF5030, "M6V 耀星\n§7尚未探明", false),
            new Star("拉兰德 21185", 11.055, 35.97, 8.31, 0xFF7040, "M2V · 至少两颗行星\n§7尚未探明", false),
            new Star("天狼星", 6.752, -16.72, 8.6, 0xA0C0FF, "A1V 全天最亮的恒星 + 白矮星伴星\n§7尚未探明", false),
            new Star("鲸鱼座 UV", 1.651, -17.95, 8.73, 0xFF6030, "M5.5V 双星耀星\n§7尚未探明", false),
            new Star("罗斯 154", 18.83, -23.84, 9.7, 0xFF6040, "M3.5V\n§7尚未探明", false),
            new Star("天苑四", 3.549, -9.46, 10.5, 0xFFC890, "K2V · 有尘埃盘与疑似行星\n§7尚未探明", false),
            new Star("格利泽 876", 22.888, -14.26, 15.2, 0xFF6040, "M4V · 四颗行星\n§7尚未探明", false),
            new Star("天仓五", 1.734, -15.94, 11.9, 0xFFF0B0, "G8V 类太阳恒星 · 多颗行星\n§7尚未探明", false),
            new Star("TRAPPIST-1", 23.107, -5.04, 40.7, 0xFF3020, "M8V 超冷红矮星 · 七颗类地行星\n§7尚未探明", false));

    private Star hover;

    public StarMapScreen() { super(Component.translatable("item.heliosphere.star_map")); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int w = Math.min(width - 20, 420), h = Math.min(height - 20, 240);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        HelioGui.panel(g, x0, y0, w, h);
        g.fill(x0 + 6, y0 + 18, x0 + w - 6, y0 + h - 6, 0xFF04060C);
        // faint background stars
        long seed = 12345;
        for (int i = 0; i < 260; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = x0 + 7 + (int) ((seed >>> 33) % (w - 14)), sy = y0 + 19 + (int) ((seed >>> 13) % (h - 26));
            int a = 40 + (int) ((seed >>> 50) % 120);
            g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xC8D0FF);
        }
        g.drawString(font, "§b星图 · 太阳邻域（距离按真实光年，方向按赤经赤纬）", x0 + 8, y0 + 6, HelioGui.TEXT, false);
        Story.Payload st = Story.client;
        String cd = st.active() ? "§c舰队抵达：" + Story.format(st.remainingDays()) : "§7尚未截获信号";
        g.drawString(font, cd, x0 + w - 8 - font.width(cd), y0 + 6, HelioGui.TEXT, false);

        double cx = x0 + w / 2.0, cy = y0 + 18 + (h - 24) / 2.0;
        double scale = (Math.min(w, h) / 2.0 - 22) / Math.sqrt(41);   // sqrt distance scale so TRAPPIST-1 fits
        // range rings
        for (int ly : new int[]{5, 10, 20, 40}) {
            double r = Math.sqrt(ly) * scale;
            for (int k = 0; k < 96; k++) {
                double a = k * Math.PI * 2 / 96;
                g.fill((int) (cx + Math.cos(a) * r), (int) (cy + Math.sin(a) * r), (int) (cx + Math.cos(a) * r) + 1, (int) (cy + Math.sin(a) * r) + 1, 0x40486080);
            }
            g.drawString(font, "§8" + ly + " 光年", (int) (cx + r) + 2, (int) cy - 4, 0xFF607090, false);
        }
        hover = null;
        long t = System.currentTimeMillis();
        for (Star s : STARS) {
            double ang = s.ra / 24.0 * Math.PI * 2;
            double r = Math.sqrt(s.ly) * scale * Math.cos(Math.toRadians(s.dec) * 0.5);
            int sx = (int) (cx + Math.cos(ang) * r), sy = (int) (cy - Math.sin(ang) * r - Math.sin(Math.toRadians(s.dec)) * s.ly * 0.0);
            int size = s.name.equals("太阳") ? 3 : 2;
            float tw = 0.75f + 0.25f * (float) Math.sin(t / 300.0 + s.ra * 7);
            int a = (int) (255 * (s.open ? tw : 0.45f));
            g.fill(sx - size, sy - size, sx + size, sy + size, (a << 24) | s.color);
            if (s.open && !s.name.equals("太阳") && (t / 500) % 2 == 0) {
                g.fill(sx - 6, sy - 6, sx + 6, sy - 5, 0xFFFF4060); g.fill(sx - 6, sy + 5, sx + 6, sy + 6, 0xFFFF4060);
                g.fill(sx - 6, sy - 6, sx - 5, sy + 6, 0xFFFF4060); g.fill(sx + 5, sy - 6, sx + 6, sy + 6, 0xFFFF4060);
            }
            String label = s.open ? s.name : "未知";
            g.drawString(font, (s.open ? "§f" : "§8") + label, sx + 5, sy - 3, HelioGui.TEXT, false);
            if (Math.abs(mx - sx) < 7 && Math.abs(my - sy) < 7) hover = s;
        }
        if (hover != null) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(Component.literal("§e" + (hover.open ? hover.name : "未知恒星")).getVisualOrderText());
            tip.add(Component.literal(String.format("§7距离 %.2f 光年", hover.ly)).getVisualOrderText());
            if (hover.ly > 0) {
                double years = hover.ly / 0.9, ship = years * Math.sqrt(1 - 0.81);
                tip.add(Component.literal(String.format("§7以 0.9c 航行：地球时间 %.1f 年，船上 %.1f 年", years, ship)).getVisualOrderText());
            }
            for (String line : (hover.open ? hover.info : "§7情报不足——需要更强的射电阵或亲自前往").split("\n"))
                tip.addAll(HelioGui.wrap(font, line, 220));
            g.setTooltipForNextFrame(tip, mx, my);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
