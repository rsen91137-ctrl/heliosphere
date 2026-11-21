package com.heliosphere.industry.kitchen.mixin;

import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import com.heliosphere.industry.kitchen.Kitchen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The empty bucket or bottle of an ingredient leaves the food synthesizer once, on the right with the meal. A
 * machine also leaves the container of the last milk bucket or honey bottle of a slot among the ingredients (what
 * vanilla calls the crafting remainder), so with the synthesizer's recipes handing the same container back as a
 * product, every milk bucket came back as two buckets: free iron. The copy left among the ingredients is removed.
 */
@Mixin(value = MachineBlockEntity.class, remap = false)
public abstract class SynthesizerContainerMixin {
    /** What each ingredient slot of the synthesizer held when the meal now being finished was started on. */
    @Unique private Item[] heliosphere$ingredients;

    @Inject(method = "craft", at = @At("HEAD"), remap = false)
    private void heliosphere$remember(Level level, MachineType type, MachineRecipe recipe, CallbackInfo ci) {
        heliosphere$ingredients = null;
        if (!Kitchen.MACHINE_ID.equals(type.id)) return;
        MachineBlockEntity self = (MachineBlockEntity) (Object) this;
        Item[] before = new Item[Math.min(type.inputs, self.getContainerSize())];
        for (int i = 0; i < before.length; i++) before[i] = self.getItem(i).getItem();
        heliosphere$ingredients = before;
    }

    @Inject(method = "craft", at = @At("RETURN"), remap = false)
    private void heliosphere$oneContainerBack(Level level, MachineType type, MachineRecipe recipe, CallbackInfo ci) {
        Item[] before = heliosphere$ingredients;
        if (before == null) return;
        heliosphere$ingredients = null;
        MachineBlockEntity self = (MachineBlockEntity) (Object) this;
        for (int i = 0; i < before.length; i++) {
            ItemStack container = before[i].getCraftingRemainder(), now = self.getItem(i);
            // only a container the machine has just put where its full ingredient was, never one the player put in
            if (container.isEmpty() || now.getItem() == before[i] || !now.is(container.getItem())) continue;
            for (MachineRecipe.Output out : recipe.outputs)
                if (out.stack().is(container.getItem())) { self.setItem(i, ItemStack.EMPTY); break; }
        }
    }
}
