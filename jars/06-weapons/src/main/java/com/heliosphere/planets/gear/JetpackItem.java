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

    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13f * fuel(s) / TANK); }
    @Override public int getBarColor(ItemStack s) { return 0x4FB4FF; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.heliosphere." + kind.id + ".desc").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.heliosphere.jetpack.fuel", fuel(stack) * 100 / TANK).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("item.heliosphere.jetpack.keys", kind.hold ? Component.translatable("item.heliosphere.jetpack.keys_hold") : Component.empty())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
