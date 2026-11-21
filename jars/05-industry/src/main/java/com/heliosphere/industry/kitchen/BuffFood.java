package com.heliosphere.industry.kitchen;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * A meal from the food synthesizer: it fills the hunger bar and leaves the player with its buffs for a few minutes.
 * The tooltip says what it is and lists the buffs with their strength and duration, the way a potion does.
 */
public final class BuffFood extends Item {
    private final String hintKey;
    private final List<MobEffectInstance> buffs;

    public BuffFood(Properties properties, String id, List<MobEffectInstance> buffs) {
        super(properties);
        this.hintKey = "item.heliosphere." + id + ".hint";
        this.buffs = List.copyOf(buffs);
    }

    /** The effects eating it gives (the same list its consumable applies). */
    public List<MobEffectInstance> buffs() { return buffs; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable(hintKey).withStyle(ChatFormatting.GRAY));
        PotionContents.addPotionTooltip(buffs, out, 1f, ctx.tickRate());
    }
}
