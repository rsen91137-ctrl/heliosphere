package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.planets.defense.sam.SamVehicle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** An air-defence vehicle: the model turned to its heading, launcher and radar in their current pose. */
public final class SamVehicleRenderer extends EntityRenderer<SamVehicle, SamVehicleRenderer.State> {
    public SamVehicleRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 1.6f; }

    public static final class State extends EntityRenderState {
        final SamRenderer.State model = new SamRenderer.State();
        float heading;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(SamVehicle e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        float[] v = e.view();
        s.heading = e.getYRot(partial);
        s.model.kind = e.kind();
        s.model.raise = v[0]; s.model.yaw = v[1]; s.model.elev = v[2];
        s.model.ammo = e.ammo();
        s.model.active = e.active();
        long now = e.level().getGameTime();
        s.model.age = now + partial;
        s.model.sinceLaunch = now - e.lastLaunch() + partial;
    }

    @Override protected AABB getBoundingBoxForCulling(SamVehicle e) { return e.getBoundingBox().inflate(6, 8, 6); }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.heading)));
        s.model.lightCoords = s.lightCoords;
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> SamRenderer.build(new BoxMesh(p, vc, s.lightCoords, false), s.model));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> SamRenderer.build(new BoxMesh(p, vc, 0xF000F0, true), s.model));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
