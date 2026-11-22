package com.heliosphere.industry.habitat.mixin;

import com.heliosphere.industry.habitat.Containment;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player held in a bio capsule cannot use the spectator menu to teleport away. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CaptiveTeleportMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void heliosphere$held(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (Containment.captive(player)) ci.cancel();
    }
}
