package com.heliosphere.planets.defense.sam;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * The recovery wrench. Right-click an air-defence vehicle with an empty wrench and the vehicle goes into the wrench,
 * missiles, charge, mode and owner included; right-click the ground with the loaded wrench and it rolls out again,
 * facing the way you look. A loaded wrench glints and names what it carries. If the wrench is already carrying a
 * vehicle, the second one is packed into its own vehicle item instead. Battery packs and ammo crates are packed into
 * the inventory as before.
 */
public class SamWrenchItem extends Item {
    private static final String KIND = "stored_vehicle", CARRY = "stored_carry";

    public SamWrenchItem(Properties p) { super(p); }

    /** The vehicle this wrench carries, or null. */
    public static SamKind stored(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String k = data.copyTag().getStringOr(KIND, "");
        if (k.isEmpty()) return null;
        try { return SamKind.valueOf(k); } catch (IllegalArgumentException e) { return null; }
    }

    private static CompoundTag carried(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty(CARRY);
    }

    /** Right-click on a vehicle: into the wrench if it is empty, else into the vehicle's own item. */
    static void recover(Player p, ItemStack wrench, SamVehicle v) {
        SamKind held = stored(wrench);
        if (held != null) {
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_wrench_full", Component.translatable("block.heliosphere." + held.id)).withStyle(ChatFormatting.YELLOW), false);
            v.pack(p);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putString(KIND, v.kind().name());
        t.put(CARRY, v.carry());
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_wrench_stowed", Component.translatable("block.heliosphere." + v.kind().id), v.ammo(), v.energy())
                .withStyle(ChatFormatting.GREEN), true);
        v.ejectPassengers();
        v.discard();
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        ItemStack wrench = ctx.getItemInHand();
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
        } else {
            SamKind kind = stored(wrench);
            return kind == null ? InteractionResult.PASS : deploy(level, ctx, p, wrench, kind);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (!p.getInventory().add(out)) p.drop(out, false);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    /** The carried vehicle rolls out where the wrench points, facing the way the player looks. */
    private InteractionResult deploy(ServerLevel level, UseOnContext ctx, Player p, ItemStack wrench, SamKind kind) {
        BlockPos at = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        float yaw = Mth.wrapDegrees(p.getYRot());
        Vec3 pos = Vec3.atBottomCenterOf(at);
        if (!SamVehicleItem.room(level, kind, pos, yaw)) {
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_no_room", kind.length, kind.width), true);
            return InteractionResult.FAIL;
        }
        SamVehicle v = SamVehicle.place(level, kind, pos, yaw, p.getUUID(), carried(wrench), EntitySpawnReason.SPAWN_ITEM_USE);
        if (v == null) return InteractionResult.FAIL;
        if (p.isCreative()) v.creative(true);
        SamSites.markBuilt(level);
        wrench.remove(DataComponents.CUSTOM_DATA);
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_wrench_deployed", Component.translatable("block.heliosphere." + kind.id), v.ammo(), v.energy())
                .withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.2f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return stored(stack) != null || super.isFoil(stack); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        SamKind k = stored(stack);
        if (k != null) {
            CompoundTag t = carried(stack);
            out.accept(Component.translatable("item.heliosphere.sam_wrench.loaded", Component.translatable("block.heliosphere." + k.id),
                    t.getIntOr("sam_ammo", k.magazine), k.magazine, t.getLongOr("sam_power", 0)).withStyle(ChatFormatting.AQUA));
            out.accept(Component.translatable("item.heliosphere.sam_wrench.deploy_hint").withStyle(ChatFormatting.GRAY));
        } else out.accept(Component.translatable("item.heliosphere.sam_wrench.hint").withStyle(ChatFormatting.GRAY));
    }
}
