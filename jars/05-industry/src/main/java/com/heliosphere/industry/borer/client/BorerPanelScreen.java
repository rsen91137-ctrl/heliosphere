package com.heliosphere.industry.borer.client;

import com.heliosphere.industry.borer.BorerNet;
import com.heliosphere.industry.borer.TunnelBorer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The borer's control panel: its state (status, energy, bin, fuel, odometers) and buttons for every order: auto run,
 * slope, run length, junk, lining, side mining, lights, the bin, boarding and packing it up. Every button sends an
 * order; the server checks it and answers with a fresh panel. While the machine is in sight the numbers and switches
 * follow its live state; the run length, fuel count and the right to pack it up come from the last panel received.
 */
public final class BorerPanelScreen extends Screen {
    /** Height of the text block above the buttons. */
    private static final int HEADER = 88;

    private BorerNet.Panel p;
    private Button auto, slope, distance, junk, lining, veins, lights, bin, board, pack, done;
    private int top;

    BorerPanelScreen(BorerNet.Panel p) {
        super(Component.translatable("screen.heliosphere.borer.title"));
        this.p = p;
    }

    // ---- opening and refreshing -------------------------------------------------------------------------------------

    /** A panel closed by the player keeps answers to the orders it sent just before from opening it again. */
    private static int closedEntity = -1;
    private static long closedUntil;

    /** The player asked for the panel (the inventory key while riding): its answer always opens it. */
    static void requested() { closedUntil = 0; }

    /** A panel from the server: refresh the open one, or open it, unless another screen (the bin) is up. */
    static void receive(BorerNet.Panel p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof BorerPanelScreen s) {
            if (s.p.entity() == p.entity()) s.update(p);
            else mc.setScreen(new BorerPanelScreen(p));
            return;
        }
        if (mc.screen != null) return;
        if (p.entity() == closedEntity && System.currentTimeMillis() < closedUntil) return;
        mc.setScreen(new BorerPanelScreen(p));
    }

    int entity() { return p.entity(); }

    void update(BorerNet.Panel np) {
        p = np;
        refresh();
    }

    @Override
    public void removed() {
        closedEntity = p.entity();
        closedUntil = System.currentTimeMillis() + 1000;
        super.removed();
    }

    // ---- widgets ----------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        auto = button(BorerNet.AUTO, "auto");
        slope = button(BorerNet.SLOPE, null);
        distance = button(BorerNet.DISTANCE, null);
        junk = button(BorerNet.JUNK, "junk");
        lining = button(BorerNet.LINING, "lining");
        veins = button(BorerNet.VEINS, "veins");
        lights = button(BorerNet.LIGHTS, "lights");
        bin = button(BorerNet.BIN, null);
        bin.setMessage(Component.translatable("screen.heliosphere.borer.open_bin"));
        board = addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.borer.board"), b -> {
            send(BorerNet.BOARD);
            onClose();
        }).build());
        pack = addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.borer.pack"), b -> {
            send(BorerNet.PACK);
            onClose();
        }).tooltip(Tooltip.create(Component.translatable("screen.heliosphere.borer.tip.pack"))).build());
        done = addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.borer.done"), b -> onClose()).build());
        refresh();
    }

    private Button button(int action, String tip) {
        Button.Builder b = Button.builder(Component.empty(), btn -> send(action));
        if (tip != null) b.tooltip(Tooltip.create(Component.translatable("screen.heliosphere.borer.tip." + tip)));
        return addRenderableWidget(b.build());
    }

    private void send(int action) { ClientPlayNetworking.send(new BorerNet.Action(action, p.entity())); }

    /** The machine, if this client has it. */
    private TunnelBorer borer() {
        if (minecraft == null || minecraft.level == null) return null;
        Entity e = minecraft.level.getEntity(p.entity());
        return e instanceof TunnelBorer b && !b.isRemoved() ? b : null;
    }

    private boolean riding() {
        return minecraft != null && minecraft.player != null && minecraft.player.getVehicle() != null
                && minecraft.player.getVehicle().getId() == p.entity();
    }

    /** Labels from the current state, which buttons show, and where they go. */
    private void refresh() {
        if (auto == null) return;
        TunnelBorer b = borer();
        int flags = b != null ? b.flags() : p.flags();
        auto.setMessage(onOff("screen.heliosphere.borer.auto", (flags & TunnelBorer.AUTO) != 0));
        slope.setMessage(Component.translatable("screen.heliosphere.borer.slope", BorerHud.slopeName(b != null ? b.slope() : p.slope())));
        distance.setMessage(Component.translatable("screen.heliosphere.borer.distance", p.distance() < 0
                ? Component.translatable("screen.heliosphere.borer.distance.unlimited")
                : Component.translatable("screen.heliosphere.borer.distance.blocks", p.distance())));
        junk.setMessage(Component.translatable("screen.heliosphere.borer.junk", Component.translatable((flags & TunnelBorer.DISCARD_JUNK) != 0
                ? "screen.heliosphere.borer.junk.discard" : "screen.heliosphere.borer.junk.keep")));
        lining.setMessage(onOff("screen.heliosphere.borer.lining", (flags & TunnelBorer.LINING) != 0));
        veins.setMessage(onOff("screen.heliosphere.borer.veins", (flags & TunnelBorer.VEINS) != 0));
        lights.setMessage(onOff("screen.heliosphere.borer.lights", (flags & TunnelBorer.LIGHTS) != 0));
        board.visible = !riding();
        board.active = b == null || b.getPassengers().isEmpty();
        pack.visible = p.owner();
        layout();
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "screen.heliosphere.borer.on" : "screen.heliosphere.borer.off"));
    }

    private void layout() {
        List<Button> grid = new ArrayList<>(List.of(auto, slope, distance, junk, lining, veins, lights, bin));
        if (board.visible) grid.add(board);
        if (pack.visible) grid.add(pack);
        int gap = 4, cols = width >= 3 * 126 + 2 * gap + 16 ? 3 : 2;
        int bw = cols == 3 ? 126 : Math.min(150, (width - 16 - gap) / 2);
        int rows = (grid.size() + cols - 1) / cols;
        int total = HEADER + rows * 22 + 26;
        top = Math.max(6, (height - total) / 2);
        int gy = top + HEADER;
        for (int i = 0; i < grid.size(); i++) {
            int r = i / cols, c = i % cols, inRow = Math.min(cols, grid.size() - r * cols);
            int rowX = (width - (inRow * bw + (inRow - 1) * gap)) / 2;
            Button btn = grid.get(i);
            btn.setX(rowX + c * (bw + gap));
            btn.setY(gy + r * 22);
            btn.setWidth(bw);
        }
        done.setWidth(100);
        done.setX((width - 100) / 2);
        done.setY(gy + rows * 22 + 4);
    }

    // ---- every tick ---------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        TunnelBorer b = borer();
        if (b == null || minecraft.player == null || (!riding() && minecraft.player.distanceToSqr(b) > 10 * 10)) {
            onClose();
            return;
        }
        refresh();
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key)) {   // the inventory key closes it, as it opened it
            onClose();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        TunnelBorer b = borer();
        int energy = b != null ? b.energy() : p.energy(), capacity = Math.max(1, p.capacity());
        if (b != null && b.flag(TunnelBorer.CREATIVE)) energy = capacity;
        int used = b != null ? b.binUsed() : p.binUsed(), size = Math.max(1, p.binSize());
        int status = b != null ? b.status() : p.status();
        int dug = b != null ? b.dug() : p.dug(), ores = b != null ? b.ores() : p.ores();
        String statusKey = "status.heliosphere.borer." + TunnelBorer.STATUS_KEYS[status >= 0 && status < TunnelBorer.STATUS_KEYS.length ? status : 0];
        Component title = b != null && b.hasCustomName() ? b.getDisplayName() : this.title;
        Component state = Component.translatable("screen.heliosphere.borer.status", Component.translatable(statusKey));
        Component power = Component.translatable("screen.heliosphere.borer.energy", grouped(energy), grouped(capacity));
        Component store = Component.translatable("screen.heliosphere.borer.bin", used, size);
        Component fuel = Component.translatable("screen.heliosphere.borer.fuel", p.fuel());
        Component odo = Component.translatable("screen.heliosphere.borer.dug", dug, ores);
        int half = 136;
        for (Component c : new Component[]{title, state, power, store, fuel, odo}) half = Math.max(half, font.width(c) / 2 + 10);
        half = Math.min(half, width / 2 - 4);
        int cx = width / 2, y = top;
        g.fill(cx - half, y - 5, cx + half, y + HEADER - 4, 0x90101418);
        g.fill(cx - half, y - 5, cx + half, y - 4, 0xFFF2B62C);
        g.drawCenteredString(font, title, cx, y, 0xFFFFFFFF);
        y += 14;
        g.drawCenteredString(font, state, cx, y, BorerHud.statusColour(status));
        y += 13;
        g.drawCenteredString(font, power, cx, y, 0xFFB0E0FF);
        y += 10;
        bar(g, cx - 100, y, 200, energy / (float) capacity, 0xFF38C0F0);
        y += 7;
        g.drawCenteredString(font, store, cx, y, 0xFFFFD890);
        y += 10;
        bar(g, cx - 100, y, 200, used / (float) size, used >= size ? 0xFFE04030 : 0xFFE09A30);
        y += 8;
        g.drawCenteredString(font, fuel, cx, y, 0xFFC8C8C8);
        y += 11;
        g.drawCenteredString(font, odo, cx, y, 0xFFC8C8C8);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float f, int argb) {
        g.fill(x, y, x + w, y + 4, 0xFF202428);
        int fw = Math.round(w * Mth.clamp(f, 0f, 1f));
        if (fw > 0) g.fill(x, y, x + fw, y + 4, argb);
    }

    private static String grouped(int n) { return String.format(Locale.ROOT, "%,d", n); }

    @Override public boolean isPauseScreen() { return false; }
}
