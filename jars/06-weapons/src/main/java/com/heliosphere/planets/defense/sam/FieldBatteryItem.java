package com.heliosphere.planets.defense.sam;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** The battery pack as an item: remembers its charge (a fresh one from the assembly station is full). */
public class FieldBatteryItem extends BlockItem {
    private static final String KEY = "heliosphere_energy";

    public FieldBatteryItem(Block block, Properties p) { super(block, p); }

    public static long stored(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return FieldBatteryBlockEntity.CAPACITY;
        CompoundTag t = d.copyTag();
        return t.contains(KEY) ? t.getLongOr(KEY, FieldBatteryBlockEntity.CAPACITY) : FieldBatteryBlockEntity.CAPACITY;
    }

    public static ItemStack withEnergy(ItemStack stack, long energy) {
        if (energy >= FieldBatteryBlockEntity.CAPACITY) return stack;          // full packs stack with fresh ones
        CompoundTag t = new CompoundTag();
        t.putLong(KEY, energy);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        long e = stored(stack);
        out.accept(Component.translatable("item.heliosphere.sam_battery.charge", String.format("%,d", e), (int) (100 * e / FieldBatteryBlockEntity.CAPACITY))
                .withStyle(ChatFormatting.GREEN));
        out.accept(Component.translatable("item.heliosphere.sam_battery.hint", (int) FieldBatteryBlockEntity.RANGE).withStyle(ChatFormatting.GRAY));
    }
}
