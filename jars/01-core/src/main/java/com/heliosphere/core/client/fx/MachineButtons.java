package com.heliosphere.core.client.fx;

import com.heliosphere.core.client.screen.MachineScreen;
import com.heliosphere.core.machine.Startable;
import com.heliosphere.core.mixin.fx.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Start and stop buttons on the screen of a machine that waits for them ({@link Startable}: the assembly station). */
public final class MachineButtons {
    private MachineButtons() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof MachineScreen ms) || !Startable.gated(ms.getMenu().machine)) return;
            ContainerScreenAccessor a = (ContainerScreenAccessor) ms;
            int x = a.heliosphere$left() + a.heliosphere$width() - 62, y = a.heliosphere$top() + 58;
            Screens.getButtons(screen).add(Button.builder(Component.translatable("button.heliosphere.machine_start"), b -> press(ms, 0))
                    .bounds(x, y, 38, 13).tooltip(Tooltip.create(Component.translatable("button.heliosphere.machine_start.tip"))).build());
            Screens.getButtons(screen).add(Button.builder(Component.translatable("button.heliosphere.machine_stop"), b -> press(ms, 1))
                    .bounds(x + 40, y, 15, 13).tooltip(Tooltip.create(Component.translatable("button.heliosphere.machine_stop.tip"))).build());
        });
    }

    private static void press(MachineScreen ms, int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(ms.getMenu().containerId, id);
    }
}
