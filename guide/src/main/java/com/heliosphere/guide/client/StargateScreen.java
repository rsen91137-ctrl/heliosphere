package com.heliosphere.guide.client;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.client.screen.HelioGui;
import com.heliosphere.planets.Stargate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Dialling computer of the stargate: pick a world, the gate burns one zero-point core. */
public class StargateScreen extends Screen {
    private final List<String> bodies;

    public StargateScreen(List<String> bodies) { super(Component.literal("星际之门")); this.bodies = bodies; }

    @Override
    protected void init() {
        int cols = 4, bw = 92, bh = 16;
        int w = cols * (bw + 4), x0 = (width - w) / 2, y0 = (height - 220) / 2 + 28;
        for (int i = 0; i < bodies.size(); i++) {
            String id = bodies.get(i);
            CelestialBody b = SolarSystem.get(id);
            String label = (b == null ? id : b.nameZh) + (b != null && b.gasGiant ? " §7(浮空站)" : "");
            addRenderableWidget(Button.builder(Component.literal(label), bt -> {
                ClientPlayNetworking.send(new Stargate.Jump(id));
                onClose();
            }).bounds(x0 + (i % cols) * (bw + 4), y0 + (i / cols) * (bh + 3), bw, bh).build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int w = 4 * 96 + 16, h = 220, x = (width - w) / 2, y = (height - h) / 2;
        HelioGui.panel(g, x, y, w, h);
        float t = (System.currentTimeMillis() % 4000) / 4000f;
        for (int k = 0; k < 3; k++) {
            int a = (int) (40 + 30 * Math.sin((t + k / 3f) * Math.PI * 2));
            g.fill(x + 6 + k * 2, y + 6 + k * 2, x + w - 6 - k * 2, y + 7 + k * 2, (a << 24) | 0x60C0FF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int w = 4 * 96 + 16, x = (width - w) / 2, y = (height - 220) / 2;
        g.drawString(font, "§b星际之门 · 拨号", x + 10, y + 12, HelioGui.TEXT, false);
        String cost = "§7每次跃迁消耗 1 个零点能量核 · 首次抵达会在目的地自动建立返程星门";
        g.drawString(font, cost, x + w - 10 - font.width(cost), y + 12, HelioGui.TEXT, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
