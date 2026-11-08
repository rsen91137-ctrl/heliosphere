package com.heliosphere.rocketry.client;

import com.heliosphere.core.client.screen.HelioGui;
import com.heliosphere.rocketry.menu.RocketMenu;
import com.heliosphere.rocketry.net.RocketNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Mission control: destination, landing site, pre-flight checklist, refuelling and launch. */
public class RocketScreen extends AbstractContainerScreen<RocketMenu> {
    static RocketNet.Report latest;
    private RocketNet.Report shown;
    private final List<Button> dynamic = new ArrayList<>();
    private int scroll;
    private Button launch, force;

    public RocketScreen(RocketMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 360;
        imageHeight = 218;
        latest = null;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 280, y = topPos;
        addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.refuel"), b -> act(RocketNet.FUEL, ""))
                .bounds(x, y + 100, 74, 16).build());
        launch = addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.launch"), b -> act(RocketNet.LAUNCH, ""))
                .bounds(x, y + 118, 74, 18).build());
        force = addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.force_launch"), b -> act(RocketNet.FORCE_LAUNCH, ""))
                .bounds(x, y + 138, 74, 16).build());
        if (menu.isEntity()) addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.disassemble"), b -> act(RocketNet.DISASSEMBLE, ""))
                .bounds(x, y + 156, 74, 16).build());
        shown = null;
    }

    private void act(int a, String arg) { ClientPlayNetworking.send(new RocketNet.Action(a, arg)); }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (latest != shown) { shown = latest; rebuildDynamic(); }
    }

    private void rebuildDynamic() {
        for (Button b : dynamic) removeWidget(b);
        dynamic.clear();
        if (shown == null) return;
        int x = leftPos + 6, y = topPos + 26;
        for (String d : shown.destinations()) {
            String[] p = d.split("\\|");
            boolean sel = p[0].equals(shown.destination());
            Button b = Button.builder(Component.literal((sel ? "▶ " : "") + p[1]), bt -> act(RocketNet.SELECT_DEST, p[0])).bounds(x, y, 96, 13).build();
            dynamic.add(addRenderableWidget(b));
            y += 14;
        }
        y += 12;
        for (String s : shown.sites()) {
            String[] p = s.split("\\|");
            boolean sel = p[0].equals(shown.site());
            Button b = Button.builder(Component.literal((sel ? "▶ " : "") + p[1]), bt -> act(RocketNet.SELECT_SITE, p[0])).bounds(x, y, 96, 13)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(p.length > 2 ? p[2] : ""))).build();
            dynamic.add(addRenderableWidget(b));
            y += 14;
            if (y > topPos + imageHeight - 18) break;
        }
        launch.active = shown.canLaunch();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        HelioGui.panel(g, x, y, imageWidth, imageHeight);
        HelioGui.inset(g, x + 108, y + 16, 246, 60);
        HelioGui.inset(g, x + 108, y + 78, 246, 20);
        for (Slot s : menu.slots) HelioGui.slot(g, x + s.x, y + s.y);
        g.fill(x + 105, y + 16, x + 106, y + imageHeight - 6, 0xFF34425C);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 5, HelioGui.ACCENT, false);
        g.drawString(font, Component.translatable("gui.heliosphere.destination"), 8, 16, HelioGui.DIM, false);
        if (shown == null) { g.drawString(font, "…", 112, 20, HelioGui.DIM, false); return; }
        int sitesLabelY = 26 + shown.destinations().size() * 14 + 2;
        if (!shown.sites().isEmpty()) g.drawString(font, Component.translatable("gui.heliosphere.landing_site"), 8, sitesLabelY, HelioGui.DIM, false);
        // checklist
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < shown.lines().size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(shown.severity().get(b), shown.severity().get(a)));
        for (int i : order) {
            int sev = shown.severity().get(i);
            String mark = sev == 0 ? "✔ " : sev == 1 ? "⚠ " : "✘ ";
            int col = sev == 0 ? HelioGui.GOOD : sev == 1 ? HelioGui.WARN : HelioGui.BAD;
            for (FormattedCharSequence l : HelioGui.wrap(font, mark + shown.lines().get(i), 232)) { lines.add(l); colors.add(col); }
        }
        int visible = 5;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        for (int i = 0; i < visible && i + scroll < lines.size(); i++)
            g.drawString(font, lines.get(i + scroll), 112, 19 + i * 11, colors.get(i + scroll), false);
        if (lines.size() > visible) {
            String more = (scroll + visible < lines.size() ? "▼ " : "▲ ") + (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible) + "/" + lines.size();
            g.drawString(font, more, 352 - font.width(more), 5, HelioGui.DIM, false);
        }
        // stats
        for (int i = 0; i < Math.min(2, shown.stats().size()); i++) {
            String st = shown.stats().get(i);
            g.drawString(font, st, 112, 80 + i * 9, HelioGui.TEXT, false);
        }
        g.drawString(font, Component.translatable("gui.heliosphere.fuel", shown.fuel()), 110, 101, HelioGui.DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx > leftPos + 108 && mx < leftPos + 354 && my > topPos + 16 && my < topPos + 76) { scroll -= (int) Math.signum(dy); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        if (shown != null && mx > leftPos + 108 && mx < leftPos + 354 && my > topPos + 78 && my < topPos + 98 && shown.stats().size() > 2) {
            List<Component> tips = new ArrayList<>();
            for (String s : shown.stats()) tips.add(Component.literal(s));
            g.setComponentTooltipForNextFrame(font, tips, mx, my);
        }
    }
}
