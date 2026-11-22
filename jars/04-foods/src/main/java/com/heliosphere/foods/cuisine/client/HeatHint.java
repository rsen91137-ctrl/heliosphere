package com.heliosphere.foods.cuisine.client;

import com.heliosphere.core.client.screen.MachineScreen;
import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.mixin.fx.ContainerScreenAccessor;
import com.heliosphere.foods.cuisine.Cuisine;
import com.heliosphere.foods.cuisine.Heat;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A line above the screen of a wok, cooking pot or steamer saying whether it stands on a heat source, read from the
 * block below in the client's world (the same test the machine makes).
 */
final class HeatHint {
    private HeatHint() {}

    static final String OK = "screen.heliosphere.cuisine.heat_ok", MISSING = "screen.heliosphere.cuisine.heat_missing";
    private static final int GREEN = 0xFF7CE07C, RED = 0xFFFF7A5C;

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof MachineScreen ms)) return;
            MachineMenu menu = ms.getMenu();
            if (menu.machine == null || !Cuisine.isHeatMachine(menu.machine.id)) return;
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> draw(ms, g));
        });
    }

    private static void draw(MachineScreen ms, GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        MachineMenu menu = ms.getMenu();
        boolean hot = mc.level != null && menu.pos != null && Heat.isHeatSource(mc.level, menu.pos.below());
        Component text = Component.translatable(hot ? OK : MISSING);
        Font font = mc.font;
        ContainerScreenAccessor panel = (ContainerScreenAccessor) ms;
        int w = font.width(text);
        int x = Math.max(2, Math.min(panel.heliosphere$left() + (panel.heliosphere$width() - w) / 2, ms.width - w - 2));
        int y = panel.heliosphere$top() - 11;                               // just above the panel
        if (y < 2) y = panel.heliosphere$top() + panel.heliosphere$height() + 3;   // or below it on a short window
        g.drawString(font, text, x, y, hot ? GREEN : RED, true);
    }
}
