package com.heliosphere.planets.defense.sam;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/**
 * The recovery wrench: right-click an air-defence vehicle, battery pack or ammo crate and it is packed
 * up into your inventory with what it held (missiles, charge, rounds), ready to be set down somewhere else.
 */
public class SamWrenchItem extends Item {
    public SamWrenchItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        var be = level.getBlockEntity(pos);
        ItemStack out;
        if (be instanceof FieldBatteryBlockEntity b) {
            out = FieldBatteryItem.withEnergy(new ItemStack(SamSites.BATTERY), b.energy());
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_packed_simple", out.getHoverName()).withStyle(ChatFormatting.GREEN), true);
        } else if (be instanceof AmmoCrateBlockEntity c) {
            out = new ItemStack(SamSites.CRATE);
            CompoundTag t = new CompoundTag();
            for (String s : SamSites.SYSTEMS) if (c.count(s) > 0) t.putInt("rounds_" + s, c.count(s));
            c.clearForPacking();
            if (!t.isEmpty()) out.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_packed_simple", out.getHoverName()).withStyle(ChatFormatting.GREEN), true);
        } else return InteractionResult.PASS;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (!p.getInventory().add(out)) p.drop(out, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.heliosphere.sam_wrench.hint").withStyle(ChatFormatting.GRAY));
    }
}
