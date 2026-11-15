package com.heliosphere.core.machine;

import com.heliosphere.core.env.HelioComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Precision machining (the CNC machine) gave every product a random tolerance, between 0.75 and 1: right for a
 * rocket engine (one of a kind, its tolerance sets its failure chance), wrong for plates and other materials, where
 * no two came out alike and none would stack (titanium plates, for one) - the machine's output slot was full after a
 * single plate. Anything that stacks now leaves the machine without a tolerance, and stacks already made are cleared
 * in players' inventories and machine slots, so they stack with each other and with plates made by hand.
 */
public final class StackFix implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 != 21) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) clean(inv.getItem(i));
                if (p.containerMenu != null) for (var slot : p.containerMenu.slots) clean(slot.getItem());
            }
        });
    }

    /** Clears the tolerance of a stackable item. Returns whether anything changed. */
    public static boolean clean(ItemStack st) {
        if (st.isEmpty() || st.getMaxStackSize() <= 1 || HelioComponents.QUALITY == null || !st.has(HelioComponents.QUALITY)) return false;
        st.remove(HelioComponents.QUALITY);
        return true;
    }

    public static void clean(Container c) {
        boolean changed = false;
        for (int i = 0; i < c.getContainerSize(); i++) changed |= clean(c.getItem(i));
        if (changed) c.setChanged();
    }
}
