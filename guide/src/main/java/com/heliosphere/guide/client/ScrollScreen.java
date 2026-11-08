package com.heliosphere.guide.client;

import com.heliosphere.core.client.screen.HelioGui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heliosphere.core.Heliosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The Star Scroll: chapters unlock one after another as the player gathers each chapter's key items. */
public class ScrollScreen extends Screen {
    private static final int W = 380, H = 240;
    private static final int PAPER = 0xFFEADBB6, PAPER_DARK = 0xFFD9C597, INK = 0xFF3B2A17, INK_LIGHT = 0xFF7A5C38, ROD = 0xFF5A3A1E, DONE = 0xFF2E7D32, LOCK = 0xFF9E8B6A;

    private record Chapter(String title, String intro, List<String> objectives, List<String> tips) {}

    private final Set<String> done;
    private final List<Chapter> chapters = new ArrayList<>();
    private int selected, scroll, unlocked;

    public ScrollScreen(List<String> done) {
        super(Component.translatable("item.heliosphere.star_scroll"));
        this.done = Set.copyOf(done);
        load();
        unlocked = 0;
        for (int i = 0; i < chapters.size() - 1; i++) {
            if (progress(chapters.get(i)) >= 0.7f) unlocked = i + 1; else break;
        }
        selected = unlocked;
    }

    private void load() {
        try (Reader r = Minecraft.getInstance().getResourceManager().getResourceOrThrow(Heliosphere.id("guide/chapters.json")).openAsReader()) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                List<String> obj = new ArrayList<>(), tips = new ArrayList<>();
                o.getAsJsonArray("objectives").forEach(x -> obj.add(x.getAsString()));
                o.getAsJsonArray("tips").forEach(x -> tips.add(x.getAsString()));
                chapters.add(new Chapter(o.get("title").getAsString(), o.get("intro").getAsString(), obj, tips));
            }
        } catch (Exception ex) {
            chapters.add(new Chapter("?", "无法读取卷轴内容：" + ex.getMessage(), List.of(), List.of()));
        }
    }

    private float progress(Chapter c) {
        if (c.objectives.isEmpty()) return 1;
        int n = 0;
        for (String o : c.objectives) if (done.contains(o)) n++;
        return n / (float) c.objectives.size();
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 30;
        int step = Math.max(14, Math.min(21, (H - 46) / Math.max(1, chapters.size())));
        for (int i = 0; i < chapters.size(); i++) {
            final int idx = i;
            boolean open = i <= unlocked;
            String full = chapters.get(i).title;
            String part = full.startsWith("第二部") ? "Ⅱ" : "Ⅰ";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("第(\\d+)章 · (.+)$").matcher(full);
            String label = m.find() ? part + "-" + m.group(1) + " " + m.group(2) : full;
            Button b = Button.builder(Component.literal(open ? label : part + " ？？？"), bt -> { selected = idx; scroll = 0; }).bounds(x, y, 104, step - 2).build();
            b.active = open;
            addRenderableWidget(b);
            y += step;
        }
        addRenderableWidget(Button.builder(Component.literal("§e配方百科 (H)"), bt -> minecraft.setScreen(new EncyclopediaScreen()))
                .bounds(left() + W - 92, top() + 10, 80, 14).build());
        addRenderableWidget(Button.builder(Component.literal("§7重看开场"), bt -> minecraft.setScreen(new IntroScreen()))
                .bounds(left() + W - 176, top() + 10, 80, 14).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int x = left(), y = top();
        // parchment with rolled rods top and bottom
        g.fill(x, y + 8, x + W, y + H - 8, PAPER);
        for (int i = 0; i < 40; i++) {
            int sx = x + 4 + (i * 97) % (W - 8), sy = y + 12 + (i * 53) % (H - 24);
            g.fill(sx, sy, sx + 2, sy + 1, PAPER_DARK);
        }
        g.fill(x - 6, y, x + W + 6, y + 9, ROD);
        g.fill(x - 6, y + H - 9, x + W + 6, y + H, ROD);
        g.fill(x - 6, y + 2, x + W + 6, y + 3, 0xFF7A5232);
        g.fill(x - 6, y + H - 7, x + W + 6, y + H - 6, 0xFF7A5232);
        g.fill(x + 122, y + 26, x + 123, y + H - 16, INK_LIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = left(), y = top();
        g.drawString(font, Component.translatable("gui.heliosphere.scroll_title"), x + 12, y + 14, INK, false);
        if (chapters.isEmpty()) return;
        Chapter c = chapters.get(Math.min(selected, chapters.size() - 1));
        int cx = x + 132, cw = W - 144;
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(HelioGui.wrap(font, "§l" + c.title, cw), INK));
        for (FormattedCharSequence s : HelioGui.wrap(font, c.intro, cw)) lines.add(new Line(List.of(s), INK_LIGHT));
        lines.add(Line.SPACER);
        lines.add(new Line(font.split(Component.translatable("gui.heliosphere.objectives", Math.round(progress(c) * 100)), cw), INK));
        lines.add(Line.ITEMS);
        lines.add(Line.SPACER);
        lines.add(new Line(font.split(Component.translatable("gui.heliosphere.tips"), cw), INK));
        for (String t : c.tips) {
            List<FormattedCharSequence> tl = HelioGui.wrap(font, t, cw - 8);
            for (int k = 0; k < tl.size(); k++) lines.add(new Line(List.of(k == 0 ? FormattedCharSequence.composite(Component.literal("• ").getVisualOrderText(), tl.get(k)) : FormattedCharSequence.composite(Component.literal("  ").getVisualOrderText(), tl.get(k))), INK));
        }

        int itemRows = (c.objectives.size() + 8) / 9;
        int ly = y + 28 - scroll;
        int bottom = y + H - 14;
        for (Line l : lines) {
            if (l == Line.ITEMS) {
                for (int i = 0; i < c.objectives.size(); i++) {
                    int ix = cx + (i % 9) * 26, iy = ly + (i / 9) * 22;
                    if (iy < y + 24 || iy > bottom - 18) continue;
                    String id = c.objectives.get(i);
                    Item item = BuiltInRegistries.ITEM.getValue(Heliosphere.id(id));
                    ItemStack st = new ItemStack(item == Items.AIR ? Items.BARRIER : item);
                    boolean ok = done.contains(id);
                    g.fill(ix - 1, iy - 1, ix + 17, iy + 17, ok ? 0x5538A040 : 0x33000000);
                    g.renderItem(st, ix, iy);
                    if (ok) g.drawString(font, "✔", ix + 11, iy + 9, DONE, false);
                    if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) g.setTooltipForNextFrame(font, st, mx, my);
                }
                ly += itemRows * 22;
                continue;
            }
            for (FormattedCharSequence s : l.text) {
                if (ly >= y + 24 && ly < bottom - 8) g.drawString(font, s, cx, ly, l.color, false);
                ly += 10;
            }
            if (l == Line.SPACER) ly += 4;
        }
        maxScroll = Math.max(0, ly + scroll - bottom);
        if (selected > unlocked) g.drawString(font, "？", cx, y + 30, LOCK, false);
    }

    private int maxScroll;

    private record Line(List<FormattedCharSequence> text, int color) {
        static final Line SPACER = new Line(List.of(), 0);
        static final Line ITEMS = new Line(List.of(), 0);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - dy * 12));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
