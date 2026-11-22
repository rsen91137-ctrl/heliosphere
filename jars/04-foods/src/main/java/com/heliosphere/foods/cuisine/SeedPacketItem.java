package com.heliosphere.foods.cuisine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A seed packet: right-click to open it, for three random crop seeds of the expansion (as likely as from grass). */
public class SeedPacketItem extends Item {
    public static final String TOOLTIP = "tooltip.heliosphere.seed_packet";
    /** Seeds in one packet. */
    public static final int SEEDS = 3;

    public SeedPacketItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack packet = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            for (int i = 0; i < SEEDS; i++) {
                Item seed = Cuisine.randomSeed(level.getRandom());
                if (seed == null) break;
                ItemStack s = new ItemStack(seed);
                if (!player.getInventory().add(s) && !s.isEmpty()) player.drop(s, false);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            packet.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9f, 1.3f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")          // Item#appendHoverText: still how an item adds its own tooltip lines
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
