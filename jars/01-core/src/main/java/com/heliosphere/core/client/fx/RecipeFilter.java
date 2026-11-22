package com.heliosphere.core.client.fx;

import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.Startable;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The recipe list of a machine with a start button, narrowed to what its ingredients allow: once something is in the
 * input slots, only recipes all of whose ingredients are among them are listed (any amount), so the dish to choose is
 * easy to find among dozens. With nothing in, or nothing matching, the whole list.
 */
public final class RecipeFilter {
    private RecipeFilter() {}

    public static List<MachineRecipe> visible(MachineMenu menu) {
        List<MachineRecipe> all = menu.machine.recipes();
        if (!Startable.gated(menu.machine)) return all;
        List<ItemStack> present = new ArrayList<>();
        for (int i = 0; i < menu.machine.inputs && i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty()) present.add(s);
        }
        if (present.isEmpty()) return all;
        List<MachineRecipe> out = new ArrayList<>();
        for (MachineRecipe r : all) {
            boolean ok = true;
            for (MachineRecipe.Input in : r.inputs) {
                boolean found = false;
                for (ItemStack s : present) if (in.test(s)) { found = true; break; }
                if (!found) { ok = false; break; }
            }
            if (ok) out.add(r);
        }
        return out.isEmpty() ? all : out;
    }
}
