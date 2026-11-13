package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.client.FlightCinematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside the cabin the mouse looks around directly, like walking anywhere else; there is no need to hold the
 * button and drag the view. (With the button held the released handler still drags, so nothing is doubled.)
 */
@Mixin(MouseHandler.class)
public abstract class CockpitFreeLookMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void heliosphere$freeLook(double movementTime, CallbackInfo ci) {
        if (!FlightCinematic.windowView() || minecraft.screen != null || !minecraft.isWindowActive()) return;
        if (GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) return;
        double s = minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double k = s * s * s * 8.0;
        double dx = accumulatedDX * k, dy = accumulatedDY * k * (minecraft.options.invertMouseY().get() ? -1 : 1);
        if (dx == 0 && dy == 0) return;
        // in the block cabin the camera is your eyes, so the mouse turns you; otherwise it turns the window view
        if (com.heliosphere.rocketry.cabin.client.RealCabin.active() && minecraft.player != null) minecraft.player.turn(dx, dy);
        else FlightCinematic.dragWindow(dx, dy);
    }
}
