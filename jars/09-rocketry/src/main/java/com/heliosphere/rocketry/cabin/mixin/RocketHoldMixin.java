package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.Boarding;
import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-click boards the cabin; a boarded rocket holds its countdown until the crew starts it. */
@Mixin(RocketEntity.class)
public abstract class RocketHoldMixin implements Boarding.RocketPhaseAccess {
    @Shadow(remap = false) private int ticks;
    @Shadow(remap = false) abstract void setPhase(int phase);

    @Inject(method = "countdown", at = @At("HEAD"), cancellable = true, remap = false)
    private void heliosphere$hold(CallbackInfo ci) {
        if (((RocketEntity) (Object) this).getTags().contains(Boarding.HOLD)) { ticks = 1; ci.cancel(); }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void heliosphere$board(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        RocketEntity self = (RocketEntity) (Object) this;
        if (player.isShiftKeyDown() || self.phase() != 0) return;
        if (player instanceof ServerPlayer sp && Boarding.board(self, sp)) cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @Override
    public void heliosphere$stand() { ticks = 0; setPhase(0); }
}
