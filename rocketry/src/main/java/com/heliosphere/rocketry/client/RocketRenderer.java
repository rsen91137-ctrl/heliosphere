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
        /** 0 = draw the parts, 1 = full skin, 2 = ship only, 3 = booster only */
        public int skin;
        public float scale = RocketSkin.SCALE;
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
        boolean capsule = false, decoupler = false, srb = false, deep = false;
        for (RocketStructure.Part p : e.structure().parts()) {
            String id = p.spec();
            if ("crew_capsule".equals(id)) capsule = true;
            if ("decoupler".equals(id)) decoupler = true;
            if ("srb_segment".equals(id)) srb = true;
            if (id.startsWith("engine_methalox") || "avionics_t2".equals(id) || "life_support".equals(id)) deep = true;
        }
        // Earth-Moon rockets fly as the ship alone (small); deep-space rockets get the full booster + ship stack
        if (srb || e.structure().parts().isEmpty()) s.skin = 0;
        else if (capsule) s.skin = deep && decoupler ? 1 : 2;
        else s.skin = deep ? 3 : 0;
        s.scale = deep ? RocketSkin.SCALE : RocketSkin.SCALE * 0.8f;
    }

    @Override
    protected AABB getBoundingBoxForCulling(RocketEntity e) {
        RocketStructure st = e.structure();
        int r = Math.max(st.radius() + 1, 4);
        return new AABB(e.getX() - r, e.getY() + st.minY() - 1, e.getZ() - r, e.getX() + r, e.getY() + Math.max(st.maxY() + 2, 42), e.getZ() + r);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState cam) {
        pose.pushPose();
        if (s.pitch != 0) pose.mulPose(Axis.ZP.rotationDegrees(-s.pitch));
        if (s.skin > 0 && !RocketSkin.get().isEmpty()) {
            int split = RocketSkin.split();
            float k = s.scale;
            pose.pushPose();
            pose.scale(k, k, k);
            if (s.skin == 2) pose.translate(0, -split, 0);
            for (RocketSkin.Voxel v : RocketSkin.get()) {
                if (s.skin == 2 && v.y() < split) continue;
                if (s.skin == 3 && v.y() >= split) continue;
                pose.pushPose();
                pose.translate(v.x() - 0.5f, v.y(), v.z() - 0.5f);
                out.submitBlock(pose, v.state(), s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            pose.popPose();
            pose.popPose();
            super.submit(s, pose, out, cam);
            return;
        }
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
