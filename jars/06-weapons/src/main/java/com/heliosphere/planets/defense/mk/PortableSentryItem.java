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
    private static final String KEY = "sentry_energy", MK = "sentry_mk";

    public static int mk(ItemStack st) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr(MK, 0);
    }

    public static void setMk(ItemStack st, int mk) {
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putInt(MK, Mth.clamp(mk, 0, 5)));
    }

    /** A turret upgrade kit carried onto a sentry, or onto armour carrying one: up to the kit's Mk. Returns whether it was used. */
    public static boolean upgrade(ItemStack kitStack, Player player, int current, java.util.function.IntConsumer apply) {
        int kit = Sentries.kitLevel(kitStack);
        if (kit <= 0) return false;
        if (current >= kit) {
            player.displayClientMessage(Component.translatable("message.heliosphere.shoulder_sentry_has_mk", current).withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        apply.accept(kit);
        kitStack.shrink(1);
        player.playSound(SoundEvents.SMITHING_TABLE_USE, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.heliosphere.shoulder_sentry_upgraded", kit).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /** A kit clicked onto this sentry in an inventory upgrades it. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack sentry, ItemStack other, Slot slot, ClickAction action, Player player, net.minecraft.world.entity.SlotAccess access) {
        if (Sentries.kitLevel(other) <= 0) return false;
        long e = energy(sentry);
        boolean used = upgrade(other, player, mk(sentry), k -> setMk(sentry, k));
        setEnergy(sentry, e);
        slot.setChanged();
        return used;
    }

    public PortableSentryItem(Properties p) { super(p); }

    public static long energy(ItemStack st) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        long cap = ShoulderSentry.capacity(mk(st));
        return d == null ? cap : Math.min(cap, d.copyTag().getLongOr(KEY, cap));   // new from the bench: full
    }

    public static void setEnergy(ItemStack st, long e) {
        long cap = ShoulderSentry.capacity(mk(st));
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putLong(KEY, Mth.clamp(e, 0, cap)));
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
        ShoulderSentry.setMk(armour, mk(sentry));
        ShoulderSentry.setCharge(armour, energy(sentry));
        sentry.shrink(1);
        slot.setChanged();
        player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 1.3f);
        return true;
    }

    @Override public boolean isBarVisible(ItemStack st) { return true; }
    @Override public int getBarWidth(ItemStack st) { return Math.round(13f * energy(st) / ShoulderSentry.capacity(mk(st))); }
    @Override public int getBarColor(ItemStack st) { return 0x40C8FF; }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        int mk = mk(st);
        out.accept(Component.translatable("item.heliosphere.portable_sentry.charge", energy(st), ShoulderSentry.capacity(mk)).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("item.heliosphere.portable_sentry.stats", mk == 0 ? "-" : roman(mk), String.format("%.0f", ShoulderSentry.damage(mk)),
                String.format("%.0f", ShoulderSentry.range(mk)), String.format("%.2f", ShoulderSentry.interval(mk) / 20f), ShoulderSentry.targets(mk)).withStyle(ChatFormatting.GOLD));
        out.accept(Component.translatable("item.heliosphere.portable_sentry.hint", String.format("%.0f", ShoulderSentry.range(mk)), String.format("%.1f", ShoulderSentry.damage(mk))).withStyle(ChatFormatting.GRAY));
    }

    public static String roman(int mk) { return new String[]{"-", "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ"}[Mth.clamp(mk, 0, 5)];
    }
}
