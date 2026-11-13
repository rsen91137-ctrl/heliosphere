package com.heliosphere.planets.gear.mixin;

import com.heliosphere.planets.gear.Jetpacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Someone on jetpack thrust is not cheating: no "flying is not enabled" kick. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class JetpackFlightCheckMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void heliosphere$jetpack(CallbackInfo ci) {
        if (player != null && Jetpacks.THRUSTING.contains(player.getUUID())) { clientIsFloating = false; aboveGroundTickCount = 0; }
    }
}
