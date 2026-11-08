package com.heliosphere.guide.client;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.client.screen.HelioGui;
import com.heliosphere.guide.Guide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Opening cinematic: four key-art plates with slow push-ins and cross-fades, an orchestral score and subtitles. */
public class IntroScreen extends Screen {
    private record Plate(String tex, float start, float end, float panX, float panY) {}
    private record Line(float start, float end, String text) {}

    private static final List<Plate> PLATES = List.of(
            new Plate("intro_earth", 0, 31, -0.02f, 0.015f),
            new Plate("intro_sun", 31, 40.5f, 0.015f, 0),
            new Plate("intro_proxima", 40.5f, 64.5f, -0.015f, -0.01f),
            new Plate("intro_title", 64.5f, 84, 0, 0.02f));
    private static final List<Line> LINES = List.of(
            new Line(1.5f, 6, "公元 2097 年。"),
            new Line(6.5f, 12, "地球的矿脉已被掘尽，八十亿人挤在一颗日渐干涸的星球上。"),
            new Line(12.5f, 19, "联合航天署启动「日球层计划」——走出地球，把整个太阳系变成人类文明的矿场。"),
            new Line(19.5f, 25.5f, "百年前，一支前代探索队消失在冥王星轨道之外。他们留下的最后一句话是："),
            new Line(26.3f, 30.5f, "§c「它们在听。」"),
            new Line(31.5f, 39.5f, "日球层——太阳风所及的疆域。它是人类的摇篮，也是人类的牢笼。"),
            new Line(41, 46, "4.24 光年外，比邻星。"),
            new Line(46.5f, 52, "某种比人类古老得多的东西，早已把目光投向太阳系。"),
            new Line(52.5f, 57.5f, "它们的侦察机潜伏在地底，等待人类工业苏醒的那一刻。"),
            new Line(58, 63.8f, "反对太空扩张的「末日教团」四处集结；一颗编号 2097-ΩX 的小行星，正朝地球坠来。"),
            new Line(65.5f, 71, "你是第一批工程师。从一块铝土矿开始——"),
            new Line(71.5f, 78, "§e把人类送往群星。"));
    private static final float LENGTH = 82;

    private final long started = System.currentTimeMillis();
    private SoundInstance music;

    public IntroScreen() { super(Component.literal("Heliosphere")); }

    @Override
    protected void init() {
        if (music == null) {
            Minecraft mc = Minecraft.getInstance();
            mc.getMusicManager().stopPlaying();
            music = SimpleSoundInstance.forMusic(Guide.INTRO_THEME);
            mc.getSoundManager().play(music);
        }
    }

    private float time() { return (System.currentTimeMillis() - started) / 1000f; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        float t = time();
        if (t > LENGTH) { onClose(); return; }
        // plates: cover the screen, slow push-in, 2 s cross-fades
        for (Plate p : PLATES) {
            if (t < p.start - 2 || t > p.end + 2) continue;
            float a = Mth.clamp(Math.min((t - p.start + 2) / 2f, (p.end + 2 - t) / 2f), 0, 1);
            if (p.start == 0) a = Mth.clamp(Math.min(t / 3f, (p.end + 2 - t) / 2f), 0, 1);
            float k = Mth.clamp((t - p.start) / (p.end - p.start), 0, 1);
            float scale = 1.0f + 0.09f * k;
            float cover = Math.max(width / 960f, height / 540f) * scale;
            int w = Math.round(960 * cover), h = Math.round(540 * cover);
            int x = (width - w) / 2 + Math.round(p.panX * k * width), y = (height - h) / 2 + Math.round(p.panY * k * height);
            int col = ((int) (a * 255) << 24) | 0xFFFFFF;
            g.blit(RenderPipelines.GUI_TEXTURED, Heliosphere.id("textures/gui/intro/" + p.tex + ".png"), x, y, 0, 0, w, h, 960, 540, 960, 540, col);
        }
        // cinematic letterbox
        int bar = height / 9;
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);
        // subtitles
        for (Line l : LINES) {
            if (t < l.start || t > l.end) continue;
            float a = Mth.clamp(Math.min((t - l.start) / 0.6f, (l.end - t) / 0.6f), 0, 1);
            int alpha = Math.max(8, (int) (a * 255));
            List<FormattedCharSequence> wrapped = HelioGui.wrap(font, l.text, Math.min(width - 60, 420));
            int y = height - bar - 14 - wrapped.size() * 11;
            for (FormattedCharSequence s : wrapped) {
                int w = font.width(s);
                g.drawString(font, s, (width - w) / 2, y, (alpha << 24) | 0xF0F0F0, true);
                y += 11;
            }
        }
        // title card
        if (t > 72) {
            float a = Mth.clamp(Math.min((t - 72) / 2f, (LENGTH - t) / 1.5f), 0, 1);
            int alpha = Math.max(8, (int) (a * 255));
            g.pose().pushMatrix();
            g.pose().translate(width / 2f, height / 2f - 30);
            g.pose().scale(3.0f, 3.0f);
            String title = "HELIOSPHERE";
            g.drawString(font, title, -font.width(title) / 2, -4, (alpha << 24) | 0xFFD070, true);
            g.pose().popMatrix();
            String sub = "日 球 层";
            g.pose().pushMatrix();
            g.pose().translate(width / 2f, height / 2f + 2);
            g.pose().scale(1.6f, 1.6f);
            g.drawString(font, sub, -font.width(sub) / 2, -4, (alpha << 24) | 0xE0E8FF, true);
            g.pose().popMatrix();
        }
        String hint = "ESC 跳过";
        g.drawString(font, hint, width - font.width(hint) - 8, height - bar / 2 - 4, 0x80A0A0A0, false);
    }

    @Override
    public void onClose() {
        if (music != null) Minecraft.getInstance().getSoundManager().stop(music);
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }
}
