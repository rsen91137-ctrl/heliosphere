package com.heliosphere.disasters.collapse.mixin;

import com.heliosphere.disasters.collapse.PortalCollapse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Completing an End portal frame after the collapse: the portal opens for an instant and shatters. */
@Mixin(EnderEyeItem.class)
public class EnderEyeCollapseMixin {
    @Inject(method = "useOn", at = @At("RETURN"))
    private void heliosphere$shatter(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !PortalCollapse.blocked(level, true)) return;
        BlockPos c = ctx.getClickedPos();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                if (level.getBlockState(p).is(Blocks.END_PORTAL)) { PortalCollapse.touched(level, p, true); return; }
            }
    }
}
