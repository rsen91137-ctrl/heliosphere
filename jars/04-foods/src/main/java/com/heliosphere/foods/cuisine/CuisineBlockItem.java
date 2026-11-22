package com.heliosphere.foods.cuisine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Consumer;

/** The item of a cookware machine, the induction stove or the planter, with a line or two saying how it is used. */
public class CuisineBlockItem extends BlockItem {
    private final List<String> hints;

    /** {@code hints}: translation keys, one tooltip line each. */
    public CuisineBlockItem(Block block, Properties properties, List<String> hints) {
        super(block, properties);
        this.hints = List.copyOf(hints);
    }

    @Override
    @SuppressWarnings("deprecation")          // Item#appendHoverText: still how an item adds its own tooltip lines
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, out, flag);
        for (String key : hints) out.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
