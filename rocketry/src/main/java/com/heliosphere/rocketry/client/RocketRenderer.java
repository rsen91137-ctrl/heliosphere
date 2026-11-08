package com.heliosphere.rocketry.client;

import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.entity.RocketStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Renders the rocket's captured blocks, tilted by its current pitch. */
public class RocketRenderer extends EntityRenderer<RocketEntity, RocketRenderer.State> {
    public static class State extends EntityRenderState {
        public final List<BlockState> states = new ArrayList<>();
        public final List<int[]> positions = new ArrayList<>();
        public float pitch;
    }

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 1.0f;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(RocketEntity e, State s, float pt) {
        super.extractRenderState(e, s, pt);
        s.states.clear();
        s.positions.clear();
        for (RocketStructure.Part p : e.structure().parts()) {
            s.states.add(p.state());
            s.positions.add(new int[]{p.x(), p.y(), p.z()});
        }
        s.pitch = e.pitchDeg();
    }

    @Override
    protected AABB getBoundingBoxForCulling(RocketEntity e) {
        RocketStructure st = e.structure();
        int r = st.radius() + 1;
        return new AABB(e.getX() - r, e.getY() + st.minY() - 1, e.getZ() - r, e.getX() + r, e.getY() + st.maxY() + 2, e.getZ() + r);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState cam) {
        pose.pushPose();
        if (s.pitch != 0) pose.mulPose(Axis.ZP.rotationDegrees(-s.pitch));
        for (int i = 0; i < s.states.size(); i++) {
            int[] p = s.positions.get(i);
            pose.pushPose();
            pose.translate(p[0] - 0.5, p[1], p[2] - 0.5);
            out.submitBlock(pose, s.states.get(i), s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
        super.submit(s, pose, out, cam);
    }
}
