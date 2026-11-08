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
        imageWidth = 340;
        imageHeight = 262;
        inventoryLabelY = 170;
        latest = null;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.refuel"), b -> act(RocketNet.FUEL, ""))
                .bounds(x + 272, y + 172, 62, 16).build());
        launch = addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.launch"), b -> act(RocketNet.LAUNCH, ""))
                .bounds(x + 272, y + 192, 62, 18).build());
        force = addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.force_launch"), b -> act(RocketNet.FORCE_LAUNCH, ""))
                .bounds(x + 272, y + 214, 62, 16).build());
        if (menu.isEntity()) addRenderableWidget(Button.builder(Component.translatable("gui.heliosphere.disassemble"), b -> act(RocketNet.DISASSEMBLE, ""))
                .bounds(x + 272, y + 234, 62, 16).build());
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
        int x = leftPos + 6, y = topPos + 28;
        for (String d : shown.destinations()) {
            String[] p = d.split("\\|");
            boolean sel = p[0].equals(shown.destination());
            Button b = Button.builder(Component.literal((sel ? "▶ " : "") + p[1]), bt -> act(RocketNet.SELECT_DEST, p[0])).bounds(x, y, 92, 14).build();
            dynamic.add(addRenderableWidget(b));
            y += 15;
        }
        y += 14;
        for (String s : shown.sites()) {
            String[] p = s.split("\\|");
            boolean sel = p[0].equals(shown.site());
            Button b = Button.builder(Component.literal((sel ? "▶ " : "") + p[1]), bt -> act(RocketNet.SELECT_SITE, p[0])).bounds(x, y, 92, 14)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(p.length > 2 ? p[2] : ""))).build();
            dynamic.add(addRenderableWidget(b));
            y += 15;
            if (y > topPos + 160) break;
        }
        launch.active = shown.canLaunch();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        HelioGui.panel(g, x, y, imageWidth, imageHeight);
        HelioGui.inset(g, x + 102, y + 18, 232, 96);
        HelioGui.inset(g, x + 102, y + 116, 232, 30);
        for (Slot s : menu.slots) HelioGui.slot(g, x + s.x, y + s.y);
        g.fill(x + 100, y + 18, x + 101, y + 166, 0xFF34425C);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, HelioGui.ACCENT, false);
        g.drawString(font, Component.translatable("gui.heliosphere.destination"), 8, 18, HelioGui.DIM, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, HelioGui.DIM, false);
        if (shown == null) { g.drawString(font, "…", 108, 24, HelioGui.DIM, false); return; }
        int sitesLabelY = 28 + shown.destinations().size() * 15 + 2;
        if (!shown.sites().isEmpty()) g.drawString(font, Component.translatable("gui.heliosphere.landing_site"), 8, sitesLabelY, HelioGui.DIM, false);
        // checklist
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < shown.lines().size(); i++) {
            int sev = shown.severity().get(i);
            String mark = sev == 0 ? "✔ " : sev == 1 ? "⚠ " : "✘ ";
            int col = sev == 0 ? HelioGui.GOOD : sev == 1 ? HelioGui.WARN : HelioGui.BAD;
            for (FormattedCharSequence l : com.heliosphere.core.client.screen.HelioGui.wrap(font, mark + shown.lines().get(i), 224)) { lines.add(l); colors.add(col); }
        }
        int visible = 9;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        for (int i = 0; i < visible && i + scroll < lines.size(); i++)
            g.drawString(font, lines.get(i + scroll), 106, 22 + i * 10, colors.get(i + scroll), false);
        if (lines.size() > visible) g.drawString(font, "↕", 324, 104, HelioGui.DIM, false);
        // stats
        int sy = 119;
        for (int i = 0; i < Math.min(2, shown.stats().size()); i++) { g.drawString(font, shown.stats().get(i), 106, sy, HelioGui.TEXT, false); sy += 10; }
        g.drawString(font, Component.translatable("gui.heliosphere.fuel", shown.fuel()), 102, 140, HelioGui.DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx > leftPos + 102 && mx < leftPos + 334 && my > topPos + 18 && my < topPos + 114) { scroll -= (int) Math.signum(dy); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        if (shown != null && mx > leftPos + 102 && mx < leftPos + 334 && my > topPos + 116 && my < topPos + 146 && shown.stats().size() > 2) {
            List<Component> tips = new ArrayList<>();
            for (String s : shown.stats()) tips.add(Component.literal(s));
            g.setComponentTooltipForNextFrame(font, tips, mx, my);
        }
    }
}
