package com.heliosphere.core.machine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Released machine recipes that had exactly the same inputs as another recipe of the same machine (so one of the two
 * could never be made, whatever the order): the cheap steel versions of the COPV and the nose cone, which were both
 * the nozzle bell's three steel plates.
 */
public final class RecipeFixes {
    private RecipeFixes() {}

    private static boolean done;

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("heliosphere", id));
        return it == null ? Items.AIR : it;
    }

    public static synchronized void apply() {
        if (done) return;
        done = true;
        MachineType asm = MachineType.get("assembly_station");
        if (asm == null) return;
        // COPV: a steel liner wound with copper wire; nose cone: steel plates on an iron frame
        replace(asm, "copv", "steel_plate", 3, MachineRecipe.of().in(item("steel_plate"), 2).in(item("copper_wire"), 4));
        replace(asm, "nose_cone", "steel_plate", 3, MachineRecipe.of().in(item("steel_plate"), 3).in(Items.IRON_INGOT, 2));
    }

    /** The recipe of this machine making {@code out} from {@code n} of {@code only} alone, with new inputs. */
    private static void replace(MachineType type, String out, String only, int n, MachineRecipe with) {
        List<MachineRecipe> list = type.recipes();
        Item o = item(out), in = item(only);
        for (int i = 0; i < list.size(); i++) {
            MachineRecipe r = list.get(i);
            if (r.inputs.size() != 1 || r.inputs.get(0).item() != in || r.inputs.get(0).count() != n) continue;
            if (r.outputs.isEmpty() || r.outputs.get(0).stack().getItem() != o) continue;
            with.out(o, r.outputs.get(0).stack().getCount()).time(r.ticks).power(r.power);
            with.failChance = r.failChance; with.precision = r.precision; with.cond = r.cond;
            list.set(i, with);
            return;
        }
    }
}
