package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.PackedRocketItem;
import com.heliosphere.rocketry.item.RecoveryToolItem;
import com.heliosphere.rocketry.part.RocketPartBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The recovery wrench takes the whole rocket as one item (PackedRocketItem) instead of a pile of parts. Sneaking
 * with it still takes the rocket apart into its parts, as before.
 */
@Mixin(RecoveryToolItem.class)
public abstract class RecoveryWholeRocketMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void heliosphere$wholeRocket(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown()) return;                      // sneak: the old part-by-part recovery
        if (!(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof RocketPartBlock)) return;
        if (ctx.getLevel().isClientSide()) { cir.setReturnValue(InteractionResult.SUCCESS); return; }
        ItemStack packed = PackedRocketItem.pack(ctx.getLevel(), ctx.getClickedPos());
        if (packed == null) return;
        if (player == null || !player.getInventory().add(packed)) Block.popResource(ctx.getLevel(), ctx.getClickedPos(), packed);
        ctx.getLevel().playSound(null, ctx.getClickedPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.4f);
        if (player != null) player.displayClientMessage(Component.translatable("message.heliosphere.rocket_packed", packed.getHoverName()), true);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
