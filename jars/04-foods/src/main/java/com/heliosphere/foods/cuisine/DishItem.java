package com.heliosphere.foods.cuisine;

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
 * A dish of the expansion: always edible, and leaves its buffs behind. The tooltip lists the buffs with strength and
 * duration the way a potion does, and says in a line what each buff of our own does.
 */
public class DishItem extends Item {
    private final String id;
    private final List<MobEffectInstance> buffs;
    private final List<String> notes;

    /** {@code notes}: translation keys of the lines explaining the expansion's own buffs (effect.heliosphere.<id>.desc). */
    public DishItem(Properties properties, String id, List<MobEffectInstance> buffs, List<String> notes) {
        super(properties);
        this.id = id;
        this.buffs = List.copyOf(buffs);
        this.notes = List.copyOf(notes);
    }

    /** The table id of the dish. */
    public String id() { return id; }

    /** The effects eating it gives (the same as its consumable applies). */
    public List<MobEffectInstance> buffs() { return buffs; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        if (buffs.isEmpty()) return;
        PotionContents.addPotionTooltip(buffs, out, 1f, context.tickRate());
        for (String key : notes) out.accept(Component.translatable(key).withStyle(ChatFormatting.DARK_GREEN));
    }
}
