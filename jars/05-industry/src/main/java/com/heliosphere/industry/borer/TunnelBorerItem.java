package com.heliosphere.industry.borer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/**
 * The tunnel borer as an item. Right-click a block to set the machine down facing the way you look, in a clear
 * 3×3×3 space next to the clicked face; a packed-up machine comes back with its bin, charge, settings and odometers.
 * Whoever sets it down owns it; set down in creative mode it never runs out of energy.
 */
public class TunnelBorerItem extends Item {
    public TunnelBorerItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        Direction facing = p != null ? p.getDirection() : ctx.getHorizontalDirection();
        BlockPos base = TunnelBorer.findPlace(level, ctx.getClickedPos(), ctx.getClickedFace(), facing, p);
        if (base == null) {
            if (p != null) p.displayClientMessage(Component.translatable("message.heliosphere.borer.no_space").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        boolean creative = p != null && p.isCreative();
        TunnelBorer b = TunnelBorer.place(level, base, facing, p, creative, stack);
        if (b == null) return InteractionResult.FAIL;
        if (!creative) stack.shrink(1);
        level.playSound(null, b.getX(), b.getY() + 1, b.getZ(), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.2f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = data == null ? null : data.copyTag().getCompound(TunnelBorer.ITEM_TAG).orElse(null);
        if (t != null) {
            ItemContainerContents bin = stack.get(DataComponents.CONTAINER);
            int items = 0;
            if (bin != null) for (ItemStack s : bin.nonEmptyItems()) items += s.getCount();
            out.accept(Component.translatable("tooltip.heliosphere.tunnel_borer.stored", items, t.getIntOr("energy", 0), t.getIntOr("dug", 0))
                    .withStyle(ChatFormatting.AQUA));
        }
        out.accept(Component.translatable("tooltip.heliosphere.tunnel_borer.hint").withStyle(ChatFormatting.GRAY));
    }
}
