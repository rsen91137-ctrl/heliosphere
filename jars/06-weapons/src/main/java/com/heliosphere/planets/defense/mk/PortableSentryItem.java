package com.heliosphere.planets.defense.mk;

import com.heliosphere.planets.gear.ShoulderSentry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The portable sentry: a shoulder turret that clips onto a chestplate. In the inventory, pick it up and click it onto
 * any chestplate (or other chest armour) to fit it; right-click that armour with an empty cursor to take it off. Its
 * charge shows as the item's bar and stays with it on and off the armour. See {@link ShoulderSentry}.
 */
public class PortableSentryItem extends Item {
    private static final String KEY = "sentry_energy";

    public PortableSentryItem(Properties p) { super(p); }

    public static long energy(ItemStack st) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? ShoulderSentry.CAPACITY : d.copyTag().getLongOr(KEY, ShoulderSentry.CAPACITY);   // new from the bench: full
    }

    public static void setEnergy(ItemStack st, long e) {
        CompoundTag t = new CompoundTag();
        t.putLong(KEY, Mth.clamp(e, 0, ShoulderSentry.CAPACITY));
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** Carried onto chest armour in an inventory: it clips on. */
    @Override
    public boolean overrideStackedOnOther(ItemStack sentry, Slot slot, ClickAction action, Player player) {
        ItemStack armour = slot.getItem();
        if (!ShoulderSentry.fits(armour) || armour.getCount() != 1) return false;
        if (ShoulderSentry.charge(armour) >= 0) {
            player.displayClientMessage(Component.translatable("message.heliosphere.shoulder_sentry_occupied").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        ShoulderSentry.setCharge(armour, energy(sentry));
        sentry.shrink(1);
        slot.setChanged();
        player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 1.3f);
        return true;
    }

    @Override public boolean isBarVisible(ItemStack st) { return true; }
    @Override public int getBarWidth(ItemStack st) { return Math.round(13f * energy(st) / ShoulderSentry.CAPACITY); }
    @Override public int getBarColor(ItemStack st) { return 0x40C8FF; }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.heliosphere.portable_sentry.charge", energy(st), ShoulderSentry.CAPACITY).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("item.heliosphere.portable_sentry.hint", String.format("%.0f", ShoulderSentry.RANGE), String.format("%.0f", ShoulderSentry.DAMAGE)).withStyle(ChatFormatting.GRAY));
    }
}
