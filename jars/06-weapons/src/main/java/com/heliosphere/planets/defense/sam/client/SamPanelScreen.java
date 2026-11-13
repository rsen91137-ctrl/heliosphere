package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.planets.defense.sam.SamKind;
import com.heliosphere.planets.defense.sam.SamNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A vehicle's control panel: automatic or manual fire, and taking its hatch. */
public final class SamPanelScreen extends Screen {
    private final SamNet.Panel p;
    private boolean manual, iff;

    SamPanelScreen(SamNet.Panel p) {
        super(Component.translatable("block.heliosphere." + SamKind.values()[p.kind()].id));
        this.p = p;
        this.manual = p.manual();
        this.iff = p.iff();
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 4;
        boolean radar = SamKind.values()[p.kind()].radar;
        if (!radar) {
            addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.sam_auto"), b -> set(false)).bounds(cx - 154, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.sam_manual"), b -> set(true)).bounds(cx - 50, y, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.sam_board"), b -> {
            ClientPlayNetworking.send(new SamNet.Action(SamNet.BOARD, p.entity()));
            onClose();
        }).bounds(radar ? cx - 50 : cx + 54, y, 100, 20).build());
        if (!radar) addRenderableWidget(Button.builder(Component.translatable("screen.heliosphere.sam_iff_toggle"), b -> {
            iff = !iff;
            ClientPlayNetworking.send(new SamNet.Action(iff ? SamNet.IFF_ON : SamNet.IFF_OFF, p.entity()));
        }).bounds(cx - 154, y + 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 50, y + 28, 100, 20).build());
    }

    private void set(boolean on) {
        manual = on;
        ClientPlayNetworking.send(new SamNet.Action(on ? SamNet.MANUAL : SamNet.AUTO, p.entity()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int cx = width / 2, y = height / 2 - 74;
        g.drawCenteredString(font, title, cx, y, 0xFFFFFFFF);
        boolean radar = SamKind.values()[p.kind()].radar;
        if (!radar) {
            g.drawCenteredString(font, Component.translatable("screen.heliosphere.sam_mode", Component.translatable(manual ? "message.heliosphere.sam_mode_manual" : "message.heliosphere.sam_mode_auto")), cx, y + 16, 0xFFFFD060);
            g.drawCenteredString(font, Component.translatable("screen.heliosphere.sam_ammo", p.ammo(), p.magazine(), p.crate()), cx, y + 30, 0xFFE0E0E0);
        }
        g.drawCenteredString(font, Component.translatable("screen.heliosphere.sam_power", p.power(), p.capacity()), cx, y + 44, 0xFFB0E0FF);
        if (!radar) g.drawCenteredString(font, Component.translatable(iff ? "screen.heliosphere.sam_iff_on" : "screen.heliosphere.sam_iff_off"), cx, y + 58, iff ? 0xFF80FF90 : 0xFFFF7060);
        g.drawCenteredString(font, Component.translatable("screen.heliosphere.sam_help"), cx, height / 2 + 60, 0xFFA0A0A0);
    }

    @Override public boolean isPauseScreen() { return false; }
}
