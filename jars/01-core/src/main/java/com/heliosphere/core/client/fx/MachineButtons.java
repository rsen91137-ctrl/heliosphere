package com.heliosphere.core.client.fx;

import com.heliosphere.core.client.screen.MachineScreen;
import com.heliosphere.core.machine.Startable;
import com.heliosphere.core.mixin.fx.ContainerScreenAccessor;
import com.heliosphere.core.mixin.fx.MachineScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Start and stop buttons on the screen of a machine that waits for them ({@link Startable}: the assembly station, the
 * food synthesizer), and its recipe list made clickable: clicking an entry chooses that recipe and starts it.
 */
public final class MachineButtons {
    private MachineButtons() {}

    /** The list entry chosen on the screen now open (index into the machine's recipes), -1 for none. */
    private static int chosen = -1;

    /** Layout of the released machine screen's recipe list (MachineScreen.renderRecipePanel). */
    private static final int RECIPE_W = 132, ROW_H = 20, LIST_TOP = 18;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof MachineScreen ms) || !Startable.gated(ms.getMenu().machine)) return;
            chosen = -1;
            ContainerScreenAccessor a = (ContainerScreenAccessor) ms;
            int x = a.heliosphere$left() + a.heliosphere$width() - 62, y = a.heliosphere$top() + 58;
            Screens.getButtons(screen).add(Button.builder(Component.translatable("button.heliosphere.machine_start"), b -> press(ms, 0))
                    .bounds(x, y, 38, 13).tooltip(Tooltip.create(Component.translatable("button.heliosphere.machine_start.tip"))).build());
            Screens.getButtons(screen).add(Button.builder(Component.translatable("button.heliosphere.machine_stop"), b -> { chosen = -1; press(ms, 1); })
                    .bounds(x + 40, y, 15, 13).tooltip(Tooltip.create(Component.translatable("button.heliosphere.machine_stop.tip"))).build());
            ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                int entry = entryAt(ms, click.x(), click.y());
                if (entry < 0 || click.button() != 0) return true;
                chosen = entry;
                press(ms, Startable.FIRST_RECIPE + entry);
                return false;
            });
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> outline(ms, g, mouseX, mouseY));
        });
    }

    /** Index (into the machine's recipes) of the recipe-list entry under the mouse, or -1. The list may be narrowed ({@link RecipeFilter}). */
    private static int entryAt(MachineScreen ms, double mx, double my) {
        ContainerScreenAccessor a = (ContainerScreenAccessor) ms;
        var shown = RecipeFilter.visible(ms.getMenu());
        int size = shown.size();
        if (size == 0) return -1;
        int px = a.heliosphere$left() + a.heliosphere$width() + 4, py = a.heliosphere$top();
        int rows = (a.heliosphere$height() - 22) / ROW_H;
        if (mx < px + 4 || mx >= px + RECIPE_W - 4 || my < py + LIST_TOP) return -1;
        int row = (int) Math.floor((my - py - LIST_TOP) / ROW_H);
        if (row < 0 || row >= rows || (my - py - LIST_TOP) - row * ROW_H >= ROW_H - 2) return -1;
        int index = row + scroll(ms, size, rows);
        return index < size ? ms.getMenu().machine.recipes().indexOf(shown.get(index)) : -1;
    }

    private static int scroll(MachineScreen ms, int size, int rows) {
        return Math.max(0, Math.min(((MachineScreenAccessor) ms).heliosphere$scroll(), Math.max(0, size - rows)));
    }

    /** A frame round the entry under the mouse, and a green one with a tick round the chosen entry. */
    private static void outline(MachineScreen ms, GuiGraphics g, int mouseX, int mouseY) {
        ContainerScreenAccessor a = (ContainerScreenAccessor) ms;
        var shown = RecipeFilter.visible(ms.getMenu());
        var all = ms.getMenu().machine.recipes();
        int size = shown.size();
        int rows = (a.heliosphere$height() - 22) / ROW_H, first = scroll(ms, size, rows);
        int px = a.heliosphere$left() + a.heliosphere$width() + 4, py = a.heliosphere$top();
        int hover = entryAt(ms, mouseX, mouseY);
        for (int i = 0; i < rows && first + i < size; i++) {
            int index = all.indexOf(shown.get(first + i)), top = py + LIST_TOP + i * ROW_H;
            boolean on = index == chosen;
            if (!on && index != hover) continue;
            int colour = on ? 0xFF55FF55 : 0x80FFFFFF;
            int x0 = px + 4, x1 = px + RECIPE_W - 4, y0 = top, y1 = top + ROW_H - 2;
            g.fill(x0, y0, x1, y0 + 1, colour); g.fill(x0, y1 - 1, x1, y1, colour);
            g.fill(x0, y0, x0 + 1, y1, colour); g.fill(x1 - 1, y0, x1, y1, colour);
            if (on) g.drawString(Minecraft.getInstance().font, "✔", x1 - 8, y0 + 5, 0xFF55FF55, true);
        }
    }

    private static void press(MachineScreen ms, int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(ms.getMenu().containerId, id);
    }
}
