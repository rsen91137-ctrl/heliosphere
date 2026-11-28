package com.heliosphere.planets.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A jetpack, worn on the chest. Its tank is kept on the item (shown as the bar); hydrogen canisters refill it. */
public class JetpackItem extends Item {
    /** Fuel is counted in quarter ticks of thrust; a full tank is two minutes. */
    public static final int TANK = 2400 * 4, CANISTER = 1200 * 4;
    public final JetpackKind kind;

    public JetpackItem(Properties p, JetpackKind kind) { super(p); this.kind = kind; }

    public static int fuel(ItemStack s) {
        var d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr("jet_fuel", 0);
    }

    public static void setFuel(ItemStack s, int f) {
        CompoundTag t = s.has(DataComponents.CUSTOM_DATA) ? s.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        t.putInt("jet_fuel", Math.max(0, Math.min(TANK, f)));
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** Whether an elytra is fitted to this pack: it then glides like an elytra (the pack's thrust does the climbing). */
    public static boolean winged(ItemStack s) { return s.has(DataComponents.GLIDER); }

    /**
     * An elytra clicked onto a jetpack in the inventory is fitted to it (the pack then glides); right-click the pack
     * with nothing in hand to take the elytra back off, with the wear it had.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack pack, ItemStack other, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction action,
                                            net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.SlotAccess carried) {
        if (action == net.minecraft.world.inventory.ClickAction.PRIMARY && canTake(other, pack) && slot.allowModification(player)) {   // a suit onto the pack
            fitTo(other, pack);
            slot.set(ItemStack.EMPTY);
            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.1f);
            return true;
        }
        if (action == net.minecraft.world.inventory.ClickAction.PRIMARY && other.is(net.minecraft.world.item.Items.ELYTRA) && !winged(pack) && slot.allowModification(player)) {
            fit(pack, other.getDamageValue());
            other.shrink(1);
            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 0.8f, 1.2f);
            return true;
        }
        if (action == net.minecraft.world.inventory.ClickAction.SECONDARY && other.isEmpty() && winged(pack) && slot.allowModification(player)) {
            ItemStack elytra = new ItemStack(net.minecraft.world.item.Items.ELYTRA);
            CompoundTag t = pack.has(DataComponents.CUSTOM_DATA) ? pack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
            elytra.setDamageValue(t.getIntOr("jet_elytra_damage", 0));
            t.remove("jet_elytra_damage");
            pack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            pack.remove(DataComponents.GLIDER);
            carried.set(elytra);
            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.0f);
            return true;
        }
        return false;
    }

    /** The same from the other side: the pack clicked onto an elytra in a slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack pack, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction action, net.minecraft.world.entity.player.Player player) {
        ItemStack other = slot.getItem();
        if (action == net.minecraft.world.inventory.ClickAction.PRIMARY && canTake(other, pack) && slot.allowModification(player)) {    // the pack onto a suit
            fitTo(other, pack);
            pack.shrink(1);
            slot.setChanged();
            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.1f);
            return true;
        }
        if (action != net.minecraft.world.inventory.ClickAction.PRIMARY || !other.is(net.minecraft.world.item.Items.ELYTRA) || winged(pack) || !slot.allowModification(player)) return false;
        fit(pack, other.getDamageValue());
        slot.set(ItemStack.EMPTY);
        player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 0.8f, 1.2f);
        return true;
    }

    // ---- worn on a space suit -------------------------------------------------------------------------------------

    /** Key of the jetpack fitted to a suit chestplate (its kind id); its fuel stays under jet_fuel like on the pack. */
    public static final String KIND = "jet_kind";

    /** The jetpack worn with this chest item: the pack itself, or one fitted to a space suit; null if none. */
    public static JetpackKind kindOn(ItemStack s) {
        if (s.isEmpty()) return null;
        if (s.getItem() instanceof JetpackItem j) return j.kind;
        var d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        String id = d.copyTag().getStringOr(KIND, "");
        if (id.isEmpty()) return null;
        for (JetpackKind k : JetpackKind.values()) if (k.id.equals(id)) return k;
        return null;
    }

    /** A space suit chestplate (any of the planet suits): it can carry a jetpack on its back. */
    public static boolean suit(ItemStack s) {
        if (s.isEmpty() || s.getItem() instanceof JetpackItem) return false;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
        return id.getNamespace().equals("heliosphere") && id.getPath().endsWith("suit_chestplate");
    }

    /** Whether the jetpack can go onto this suit: a bare suit, and not two portable sentries at once. */
    static boolean canTake(ItemStack suit, ItemStack pack) {
        if (!suit(suit) || kindOn(suit) != null || suit.getCount() != 1) return false;
        return ShoulderSentry.charge(suit) < 0 || ShoulderSentry.charge(pack) < 0;
    }

    /** Fits the pack to the suit: kind, fuel, wear, the elytra on it (the suit then glides) and a sentry it carried. */
    public static void fitTo(ItemStack suit, ItemStack pack) {
        JetpackKind k = ((JetpackItem) pack.getItem()).kind;
        CompoundTag p = pack.has(DataComponents.CUSTOM_DATA) ? pack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        CompoundTag t = suit.has(DataComponents.CUSTOM_DATA) ? suit.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        for (String key : p.keySet()) if (!t.contains(key)) t.put(key, p.get(key).copy());
        t.putString(KIND, k.id);
        t.putInt("jet_fuel", fuel(pack));
        t.putInt("jet_damage", pack.getDamageValue());
        if (winged(pack)) { t.putBoolean("jet_winged", true); suit.set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE); }
        suit.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** Takes the jetpack back off the suit, as it went on (fuel, wear, elytra). */
    public static ItemStack takeOff(ItemStack suit) {
        JetpackKind k = kindOn(suit);
        if (k == null || suit.getItem() instanceof JetpackItem) return ItemStack.EMPTY;
        CompoundTag t = suit.get(DataComponents.CUSTOM_DATA).copyTag();
        ItemStack pack = new ItemStack(Jetpacks.ITEMS.get(k));
        setFuel(pack, t.getIntOr("jet_fuel", 0));
        pack.setDamageValue(t.getIntOr("jet_damage", 0));
        if (t.getBooleanOr("jet_winged", false)) { fit(pack, t.getIntOr("jet_elytra_damage", 0)); suit.remove(DataComponents.GLIDER); }
        for (String key : new String[]{KIND, "jet_fuel", "jet_damage", "jet_winged", "jet_elytra_damage"}) t.remove(key);
        if (t.isEmpty()) suit.remove(DataComponents.CUSTOM_DATA); else suit.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return pack;
    }

    /** The suit carrying a jetpack is about to break: the pack comes off into the inventory, or drops at the feet. */
    public static void suitBreaking(ItemStack suit, net.minecraft.server.level.ServerPlayer p) {
        if (p == null || suit(suit) == false || kindOn(suit) == null) return;
        ItemStack pack = takeOff(suit.copy());
        if (!p.getInventory().add(pack)) p.drop(pack, false);
    }

    private static void fit(ItemStack pack, int elytraDamage) {
        pack.set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE);
        CompoundTag t = pack.has(DataComponents.CUSTOM_DATA) ? pack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        t.putInt("jet_elytra_damage", elytraDamage);
        pack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13f * fuel(s) / TANK); }
    @Override public int getBarColor(ItemStack s) { return 0x4FB4FF; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.heliosphere." + kind.id + ".desc").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.heliosphere.jetpack.fuel", fuel(stack) * 100 / TANK).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("item.heliosphere.jetpack.suit").withStyle(ChatFormatting.DARK_AQUA));
        out.accept(Component.translatable(winged(stack) ? "item.heliosphere.jetpack.winged" : "item.heliosphere.jetpack.wingless").withStyle(winged(stack) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("item.heliosphere.jetpack.keys", kind.hold ? Component.translatable("item.heliosphere.jetpack.keys_hold") : Component.empty())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
