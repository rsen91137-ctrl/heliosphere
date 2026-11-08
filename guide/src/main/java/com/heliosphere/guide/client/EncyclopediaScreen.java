package com.heliosphere.guide.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.client.screen.HelioGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-game recipe encyclopedia (default key H): search, categories, every way to make an item, a crafting grid,
 * where to find raw materials, and what an item is used for. Every icon is clickable.
 */
public class EncyclopediaScreen extends Screen {
    record Rec(String machine, List<String[]> in, List<String[]> out, List<List<String>> grid, double time, int power, String note) {}
    record Entry(String id, String cat, List<Rec> recs, List<String> sources, List<String> uses) {}
    record Hit(int x, int y, int w, int h, String id) {}

    private static final Map<String, Entry> DATA = new LinkedHashMap<>();
    private static final String[] CATS = {"机器", "武器与弹药", "星门与行星核心", "火箭部件", "发射设施", "零部件", "金属与合金", "化工产品", "燃料与气罐", "矿石与原料", "宇航服",
            "太空食物", "月球与火星资源", "外太阳系资源", "星际方舟与剧情", "天体方块", "其他物品", "原版物品"};
    private static final int W = 420, H = 232, COLS = 8, ROWS = 11;

    private static String lastSelected;
    private static String lastCat = "机器";

    private EditBox search;
    private String cat = lastCat, selected = lastSelected;
    private int recIndex, gridScroll;
    private final List<String> shown = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    public EncyclopediaScreen() { super(Component.literal("配方百科")); }

    public EncyclopediaScreen(String select) { this(); selected = select; }

    static void load() {
        if (!DATA.isEmpty()) return;
        try (Reader r = Minecraft.getInstance().getResourceManager().getResourceOrThrow(Heliosphere.id("guide/recipes.json")).openAsReader()) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                List<Rec> recs = new ArrayList<>();
                for (JsonElement re : o.getAsJsonArray("r")) {
                    JsonObject ro = re.getAsJsonObject();
                    List<String[]> in = new ArrayList<>(), out = new ArrayList<>();
                    for (JsonElement x : ro.getAsJsonArray("i")) { JsonArray a = x.getAsJsonArray(); in.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()}); }
                    for (JsonElement x : ro.getAsJsonArray("o")) {
                        JsonArray a = x.getAsJsonArray();
                        out.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString(), a.size() > 2 ? a.get(2).getAsString() : "1"});
                    }
                    List<List<String>> grid = null;
                    if (ro.has("g")) {
                        grid = new ArrayList<>();
                        for (JsonElement row : ro.getAsJsonArray("g")) {
                            List<String> cells = new ArrayList<>();
                            for (JsonElement c : row.getAsJsonArray()) cells.add(c.getAsString());
                            grid.add(cells);
                        }
                    }
                    recs.add(new Rec(ro.get("m").getAsString(), in, out, grid, ro.has("t") ? ro.get("t").getAsDouble() : 0,
                            ro.has("p") ? ro.get("p").getAsInt() : 0, ro.has("x") ? ro.get("x").getAsString() : ""));
                }
                List<String> src = new ArrayList<>(), uses = new ArrayList<>();
                o.getAsJsonArray("s").forEach(x -> src.add(x.getAsString()));
                o.getAsJsonArray("u").forEach(x -> uses.add(x.getAsString()));
                DATA.put(o.get("id").getAsString(), new Entry(o.get("id").getAsString(), o.get("c").getAsString(), recs, src, uses));
            }
        } catch (Exception ex) {
            Heliosphere.LOG.warn("Encyclopedia data missing: {}", ex.toString());
        }
    }

    private static ItemStack stack(String id) {
        if (id == null || id.isEmpty() || id.startsWith("#")) return ItemStack.EMPTY;
        Identifier key = Identifier.tryParse(id);
        if (key == null) return ItemStack.EMPTY;
        Item it = BuiltInRegistries.ITEM.getValue(key);
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static String nameOf(String id) {
        ItemStack s = stack(id);
        return s.isEmpty() ? id : s.getHoverName().getString();
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    @Override
    protected void init() {
        load();
        int x = x0(), y = y0();
        search = new EditBox(font, x + 96, y + 4, 150, 13, Component.literal("搜索"));
        search.setHint(Component.literal("§7搜索：名称或 id"));
        search.setResponder(s -> { gridScroll = 0; refresh(); });
        addRenderableWidget(search);
        int by = y + 20;
        for (String c : CATS) {
            addRenderableWidget(Button.builder(Component.literal(c), b -> { cat = c; lastCat = c; search.setValue(""); gridScroll = 0; refresh(); })
                    .bounds(x + 4, by, 88, 11).build());
            by += 11;
        }
        refresh();
        if (selected == null && !shown.isEmpty()) selected = shown.get(0);
    }

    private void refresh() {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Entry e : DATA.values()) {
            if (q.isEmpty()) { if (e.cat.equals(cat)) shown.add(e.id); }
            else if (nameOf(e.id).toLowerCase(Locale.ROOT).contains(q) || e.id.contains(q)) shown.add(e.id);
        }
        shown.sort((a, b) -> Integer.compare(DATA.get(b).recs.isEmpty() ? 0 : 1, DATA.get(a).recs.isEmpty() ? 0 : 1));
    }

    private void select(String id) {
        if (!DATA.containsKey(id)) return;
        selected = id; lastSelected = id; recIndex = 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();
        int x = x0(), y = y0();
        g.drawString(font, "§b配方百科", x + 6, y + 7, HelioGui.TEXT, false);
        String hint = "§8点击任意图标查看它的做法";
        g.drawString(font, hint, x + W - 6 - font.width(hint), y + 7, HelioGui.TEXT, false);
        // item grid
        int gx = x + 96, gy = y + 20;
        HelioGui.inset(g, gx - 1, gy - 1, COLS * 18 + 2, ROWS * 18 + 2);
        int maxScroll = Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
        gridScroll = Math.max(0, Math.min(gridScroll, maxScroll));
        ItemStack tip = ItemStack.EMPTY;
        for (int i = 0; i < ROWS * COLS; i++) {
            int idx = i + gridScroll * COLS;
            if (idx >= shown.size()) break;
            String id = shown.get(idx);
            int sx = gx + (i % COLS) * 18, sy = gy + (i / COLS) * 18;
            if (id.equals(selected)) g.fill(sx, sy, sx + 18, sy + 18, 0x6040A0FF);
            ItemStack s = stack(id);
            if (!s.isEmpty()) g.renderItem(s, sx + 1, sy + 1);
            hits.add(new Hit(sx, sy, 18, 18, id));
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) tip = s;
        }
        if (maxScroll > 0) g.drawString(font, "§8" + (gridScroll + 1) + "/" + (maxScroll + 1), gx, gy + ROWS * 18 + 2, HelioGui.TEXT, false);
        // details
        int dx = gx + COLS * 18 + 6, dy = y + 20, dw = x + W - 4 - dx;
        HelioGui.inset(g, dx - 1, dy - 1, dw + 2, H - 24);
        if (selected != null && DATA.containsKey(selected)) {
            Entry e = DATA.get(selected);
            ItemStack s = stack(e.id);
            if (!s.isEmpty()) g.renderItem(s, dx + 2, dy + 2);
            List<FormattedCharSequence> nm = HelioGui.wrap(font, "§e" + nameOf(e.id), dw - 24);
            g.drawString(font, nm.get(0), dx + 22, dy + 2, HelioGui.TEXT, false);
            g.drawString(font, "§8" + e.cat, dx + 22, dy + 12, HelioGui.TEXT, false);
            int cy = dy + 24;
            if (!e.recs.isEmpty()) {
                recIndex = Math.floorMod(recIndex, e.recs.size());
                Rec r = e.recs.get(recIndex);
                String head = "§b做法 " + (recIndex + 1) + "/" + e.recs.size() + (e.recs.size() > 1 ? "  §7◀ ▶" : "");
                g.drawString(font, head, dx + 2, cy, HelioGui.TEXT, false);
                if (e.recs.size() > 1) {
                    int ax = dx + 2 + font.width("做法 " + (recIndex + 1) + "/" + e.recs.size() + "  ");
                    hits.add(new Hit(ax, cy - 1, 8, 10, "<prev>"));
                    hits.add(new Hit(ax + 10, cy - 1, 8, 10, "<next>"));
                }
                cy += 11;
                ItemStack mach = r.machine.startsWith("heliosphere:") ? stack(r.machine) : ItemStack.EMPTY;
                if (!mach.isEmpty()) { g.renderItem(mach, dx + 2, cy - 2); hits.add(new Hit(dx + 2, cy - 2, 16, 16, r.machine)); }
                g.drawString(font, "§6" + (mach.isEmpty() ? r.machine : mach.getHoverName().getString()), dx + (mach.isEmpty() ? 2 : 20), cy + 2, HelioGui.TEXT, false);
                cy += 17;
                int sx = dx + 2;
                if (r.grid != null) {
                    for (int row = 0; row < r.grid.size(); row++) for (int col = 0; col < r.grid.get(row).size(); col++) {
                        int px = sx + col * 18, py = cy + row * 18;
                        HelioGui.slot(g, px + 1, py + 1);
                        String id = r.grid.get(row).get(col);
                        ItemStack is = stack(id);
                        if (!is.isEmpty()) { g.renderItem(is, px + 1, py + 1); hits.add(new Hit(px, py, 18, 18, id)); if (in(mx, my, px, py)) tip = is; }
                    }
                    sx += 3 * 18 + 4;
                } else {
                    for (int k = 0; k < r.in.size(); k++) {
                        int px = sx + (k % 3) * 18, py = cy + (k / 3) * 18;
                        HelioGui.slot(g, px + 1, py + 1);
                        ItemStack is = stack(r.in.get(k)[0]);
                        if (!is.isEmpty()) {
                            g.renderItem(is, px + 1, py + 1);
                            g.renderItemDecorations(font, is, px + 1, py + 1, r.in.get(k)[1].equals("1") ? null : r.in.get(k)[1]);
                            hits.add(new Hit(px, py, 18, 18, r.in.get(k)[0]));
                            if (in(mx, my, px, py)) tip = is;
                        } else g.drawString(font, "§7?", px + 6, py + 5, HelioGui.TEXT, false);
                    }
                    sx += Math.min(3, Math.max(1, r.in.size())) * 18 + 4;
                }
                int rowsUsed = r.grid != null ? r.grid.size() : (r.in.size() + 2) / 3;
                g.drawString(font, "§f→", sx, cy + 5, HelioGui.TEXT, false);
                sx += 10;
                for (int k = 0; k < r.out.size(); k++) {
                    int px = sx + k * 18, py = cy;
                    HelioGui.slot(g, px + 1, py + 1);
                    ItemStack is = stack(r.out.get(k)[0]);
                    if (!is.isEmpty()) {
                        g.renderItem(is, px + 1, py + 1);
                        g.renderItemDecorations(font, is, px + 1, py + 1, r.out.get(k)[1].equals("1") ? null : r.out.get(k)[1]);
                        hits.add(new Hit(px, py, 18, 18, r.out.get(k)[0]));
                        if (in(mx, my, px, py)) tip = is;
                    }
                }
                cy += Math.max(1, rowsUsed) * 18 + 3;
                StringBuilder meta = new StringBuilder();
                if (r.time > 0) meta.append("耗时 ").append(r.time % 1 == 0 ? String.valueOf((int) r.time) : String.valueOf(r.time)).append(" 秒  ");
                if (r.power > 0) meta.append("耗电 ").append(r.power).append(" EU/t");
                if (!meta.isEmpty()) { g.drawString(font, "§7" + meta, dx + 2, cy, HelioGui.TEXT, false); cy += 10; }
                if (!r.note.isEmpty()) for (FormattedCharSequence l : HelioGui.wrap(font, "§6" + r.note, dw - 4)) { g.drawString(font, l, dx + 2, cy, HelioGui.TEXT, false); cy += 10; }
            }
            if (!e.sources.isEmpty()) {
                g.drawString(font, "§a天然来源", dx + 2, cy + 2, HelioGui.TEXT, false);
                cy += 12;
                for (String src : e.sources) for (FormattedCharSequence l : HelioGui.wrap(font, "§7· " + src, dw - 4)) { g.drawString(font, l, dx + 2, cy, HelioGui.TEXT, false); cy += 10; }
            }
            if (e.recs.isEmpty() && e.sources.isEmpty()) { g.drawString(font, "§7原版物品，或在世界中直接获取", dx + 2, cy, HelioGui.TEXT, false); cy += 10; }
            if (!e.uses.isEmpty() && cy < dy + H - 46) {
                g.drawString(font, "§d用途（" + e.uses.size() + "）", dx + 2, cy + 3, HelioGui.TEXT, false);
                cy += 14;
                int per = Math.max(1, (dw - 4) / 18);
                for (int k = 0; k < e.uses.size() && cy + (k / per) * 18 < dy + H - 42; k++) {
                    int px = dx + 2 + (k % per) * 18, py = cy + (k / per) * 18;
                    ItemStack is = stack(e.uses.get(k));
                    if (!is.isEmpty()) { g.renderItem(is, px + 1, py + 1); hits.add(new Hit(px, py, 18, 18, e.uses.get(k))); if (in(mx, my, px, py)) tip = is; }
                }
            }
        }
        if (!tip.isEmpty()) g.setTooltipForNextFrame(font, tip, mx, my);
    }

    private static boolean in(int mx, int my, int x, int y) { return mx >= x && mx < x + 18 && my >= y && my < y + 18; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        HelioGui.panel(g, x0(), y0(), W, H);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        for (Hit h : hits) {
            if (ev.x() >= h.x && ev.x() < h.x + h.w && ev.y() >= h.y && ev.y() < h.y + h.h) {
                if (h.id.equals("<prev>")) recIndex--;
                else if (h.id.equals("<next>")) recIndex++;
                else select(h.id);
                return true;
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int gx = x0() + 96, gy = y0() + 20;
        if (mx >= gx && mx < gx + COLS * 18 && my >= gy && my < gy + ROWS * 18) { gridScroll -= (int) Math.signum(dy); return true; }
        if (selected != null && mx > gx + COLS * 18) { recIndex -= (int) Math.signum(dy); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override public boolean isPauseScreen() { return false; }
}
