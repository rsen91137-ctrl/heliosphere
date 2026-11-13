package com.heliosphere.industry.power.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.industry.power.Power;
import com.heliosphere.industry.power.PowerBlock;
import com.heliosphere.industry.power.PowerBlockEntity;
import com.heliosphere.industry.power.PowerKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** The wind turbine's rotor: three blades on a hub in front of the nacelle, turning with the power it makes. */
public final class PowerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(Power.BLOCK_ENTITY, ctx -> new Rotor());
    }

    static final class State extends BlockEntityRenderState {
        boolean turbine;
        Direction facing = Direction.NORTH;
        float angle;
    }

    static final class Rotor implements BlockEntityRenderer<PowerBlockEntity, State> {
        @Override public State createRenderState() { return new State(); }
        @Override public boolean shouldRenderOffScreen() { return true; }

        @Override
        public void extractRenderState(PowerBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderState.extractBase(be, s, crumbling);
            s.turbine = be.kind() == PowerKind.WIND_TURBINE;
            if (!s.turbine) return;
            s.facing = be.getBlockState().getValue(PowerBlock.FACING);
            // turn at a pace set by the output; the angle runs on between frames
            float speed = 0.02f + 0.3f * Math.min(1.5f, be.rate() / 90f);
            long now = System.nanoTime();
            float dt = be.spinAt == 0 ? 0 : Math.min(5f, (now - be.spinAt) / 5.0e7f);
            be.spinAt = now;
            be.spin += speed * dt;
            s.angle = be.spin;
        }

        @Override
        public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
            if (!s.turbine) return;
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.facing.toYRot())));
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> {
                BoxMesh m = new BoxMesh(p, vc, s.lightCoords, false);
                m.box(-0.09, 0.08, 0.5, 0.09, 0.26, 0.66, BoxMesh.PLATE, 0x8C9096);      // shaft
                m.push();
                m.at(0, 0.17, 0.72);
                m.rz(s.angle);
                m.cbox(0, 0, 0, 0.26, 0.26, 0.2, BoxMesh.PLATE, 0xD8DADC);                // hub
                for (int i = 0; i < 3; i++) {
                    m.push();
                    m.rz(i * Math.PI * 2 / 3);
                    m.box(-0.07, 0.1, -0.03, 0.07, 1.45, 0.03, BoxMesh.PLATE, 0xEEEFF0);  // blade
                    m.box(-0.045, 1.45, -0.025, 0.045, 1.6, 0.025, BoxMesh.PLATE, 0xC83A2E);  // red tip
                    m.pop();
                }
                m.pop();
            });
            pose.popPose();
        }
    }
}
