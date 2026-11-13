package com.heliosphere.planets.defense.sam;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * An air-defence vehicle in the inventory. Use it on the ground to set it down facing the way you look; it keeps
 * the missiles and charge it had when it was packed up with the recovery wrench.
 */
public class SamVehicleItem extends Item {
    public final SamKind kind;

    public SamVehicleItem(Properties p, SamKind kind) { super(p); this.kind = kind; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos at = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        float yaw = p != null ? Mth.wrapDegrees(p.getYRot()) : 0;
        Vec3 pos = Vec3.atBottomCenterOf(at);
        if (!room(level, pos, yaw)) {
            if (p != null) p.displayClientMessage(Component.translatable("message.heliosphere.sam_no_room", kind.length, kind.width), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        var data = stack.get(DataComponents.CUSTOM_DATA);
        SamVehicle v = SamVehicle.place(level, kind, pos, yaw, p == null ? null : p.getUUID(), data == null ? null : data.copyTag(), EntitySpawnReason.SPAWN_ITEM_USE);
        if (v == null) return InteractionResult.FAIL;
        if (p != null && p.isCreative()) v.creative(true);
        SamSites.markBuilt(level);
        if (p == null || !p.isCreative()) stack.shrink(1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.2f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    /** Whether the hull fits here: nothing solid where it would stand, from the ground up to its roof. */
    private boolean room(ServerLevel level, Vec3 pos, float yaw) {
        double hl = kind.length * 0.5 - 0.3, hw = kind.width * 0.5 - 0.3;
        float r = (float) Math.toRadians(-yaw);
        for (double z = -hl; z <= hl + 1e-6; z += 0.9)
            for (double x = -hw; x <= hw + 1e-6; x += 0.9)
                for (int y = 0; y < kind.height; y++) {
                    Vec3 w = pos.add(new Vec3(x, y + 0.5, z).yRot(r));
                    BlockPos b = BlockPos.containing(w);
                    if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) return false;
                }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var t = data.copyTag();
            if (t.contains("sam_ammo"))
                out.accept(Component.translatable("item.heliosphere.sam_vehicle.carried", t.getIntOr("sam_ammo", 0), kind.magazine, t.getLongOr("sam_power", 0)).withStyle(ChatFormatting.AQUA));
        }
        out.accept(Component.translatable("item.heliosphere.sam_vehicle.hint").withStyle(ChatFormatting.GRAY));
    }
}
