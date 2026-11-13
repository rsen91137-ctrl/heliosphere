package com.heliosphere.disasters.collapse.mixin;

import com.heliosphere.disasters.collapse.PortalCollapse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After the collapse, an open End portal shatters when anything falls into it, and never teleports. */
@Mixin(EndPortalBlock.class)
public class EndPortalCollapseMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void heliosphere$shatter(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects, CallbackInfo ci) {
        if (PortalCollapse.blocked(level, true)) {
            ci.cancel();
            PortalCollapse.touched((ServerLevel) level, pos, true);
        }
    }

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void heliosphere$nowhere(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
        if (PortalCollapse.blocked(level, true)) cir.setReturnValue(null);
    }
}
