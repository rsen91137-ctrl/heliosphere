package com.heliosphere.planets.defense.mk;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** The portable sentry folded up: right-click the ground to set it down; its charge shows as the item's bar. */
public class PortableSentryItem extends Item {
    private static final String KEY = "sentry_energy";

    public PortableSentryItem(Properties p) { super(p); }

    public static long energy(ItemStack st) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? PortableSentry.CAPACITY : d.copyTag().getLongOr(KEY, PortableSentry.CAPACITY);   // new from the bench: full
    }

    public static void setEnergy(ItemStack st, long e) {
        CompoundTag t = new CompoundTag();
        t.putLong(KEY, Mth.clamp(e, 0, PortableSentry.CAPACITY));
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos at = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return InteractionResult.FAIL;
        PortableSentry s = Sentries.PORTABLE.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (s == null) return InteractionResult.FAIL;
        ItemStack st = ctx.getItemInHand();
        Vec3 pos = Vec3.atBottomCenterOf(at);
        float yaw = p == null ? 0 : p.getYRot();
        s.snapTo(pos.x, pos.y, pos.z, yaw, 0);
        s.setup(p == null ? null : p.getUUID(), energy(st), p != null && p.isCreative(), yaw);
        level.addFreshEntity(s);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1f, 0.8f);
        if (p == null || !p.isCreative()) st.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override public boolean isBarVisible(ItemStack st) { return true; }
    @Override public int getBarWidth(ItemStack st) { return Math.round(13f * energy(st) / PortableSentry.CAPACITY); }
    @Override public int getBarColor(ItemStack st) { return 0x40C8FF; }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.heliosphere.portable_sentry.charge", energy(st), PortableSentry.CAPACITY).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("item.heliosphere.portable_sentry.hint", String.format("%.0f", PortableSentry.RANGE), String.format("%.0f", PortableSentry.DAMAGE)).withStyle(ChatFormatting.GRAY));
    }
}
