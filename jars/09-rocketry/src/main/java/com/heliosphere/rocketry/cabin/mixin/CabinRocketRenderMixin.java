package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.RealCabin;
import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.flight.LaunchCabin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The rocket you are walking in is drawn from inside: not its hull (which would wall you in), but the block cabin,
 * placed and turned exactly as the deck is.
 */
@Mixin(com.heliosphere.rocketry.client.RocketRenderer.class)
public abstract class CabinRocketRenderMixin {
    private static final Map<EntityRenderState, float[]> CABIN = new WeakHashMap<>();

    // production names: these override EntityRenderer through generic bridges, which the mappings do not cover
    @Inject(method = "method_62354(Lcom/heliosphere/rocketry/entity/RocketEntity;Lcom/heliosphere/rocketry/client/RocketRenderer$State;F)V", at = @At("RETURN"), remap = false)
    private void heliosphere$mark(RocketEntity rocket, @org.spongepowered.asm.mixin.injection.Coerce EntityRenderState state, float partial, CallbackInfo ci) {
        if (RealCabin.active() && RealCabin.rocket() == rocket)
            CABIN.put(state, new float[]{rocket.getYRot(partial), (float) LaunchCabin.floor(rocket)});
        else CABIN.remove(state);
    }

    @Inject(method = "method_3936", at = @At("HEAD"), cancellable = true, remap = false)
    private void heliosphere$cabin(@org.spongepowered.asm.mixin.injection.Coerce EntityRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, CallbackInfo ci) {
        float[] c = CABIN.get(state);
        if (c == null) return;
        ci.cancel();
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-c[0])));
        for (RealCabin.Part part : RealCabin.parts()) {
            pose.pushPose();
            pose.translate(part.at().getX(), c[1] + part.at().getY(), part.at().getZ());
            out.submitBlock(pose, part.state(), 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
    }
}
