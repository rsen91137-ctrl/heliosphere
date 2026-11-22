package com.heliosphere.foods.cuisine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A hand tool of the kitchen (kitchen knife, rolling pin, mortar and pestle): used in crafting-table recipes, it stays
 * in the grid and loses one point of durability each time, until it breaks.
 */
public class KitchenToolItem extends Item {
    public static final String TOOLTIP = "tooltip.heliosphere.cuisine.tool";

    public KitchenToolItem(Properties properties) { super(properties); }

    /** The tool itself, one point more worn; nothing once that wears it out. */
    @Override
    public ItemStack getRecipeRemainder(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack left = stack.copyWithCount(1);
        if (!left.isDamageableItem()) return left;
        int damage = left.getDamageValue() + 1;
        if (damage >= left.getMaxDamage()) return ItemStack.EMPTY;
        left.setDamageValue(damage);
        return left;
    }

    @Override
    @SuppressWarnings("deprecation")          // Item#appendHoverText: still how an item adds its own tooltip lines
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
