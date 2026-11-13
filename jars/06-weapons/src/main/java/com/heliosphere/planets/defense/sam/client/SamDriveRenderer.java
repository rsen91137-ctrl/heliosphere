package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.planets.defense.sam.SamDriveEntity;
import com.heliosphere.planets.defense.sam.SamKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** A vehicle on the move: the same model as the parked one, weapons stowed, turned to its heading. */
public final class SamDriveRenderer extends EntityRenderer<SamDriveEntity, SamDriveRenderer.State> {
    public SamDriveRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    public static final class State extends EntityRenderState {
        final SamRenderer.State model = new SamRenderer.State();
        float yaw;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(SamDriveEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.getYRot(partial);
        s.model.kind = e.kind();
        s.model.facing = Direction.SOUTH;
        s.model.raise = 0; s.model.yaw = 0; s.model.elev = (float) Math.toRadians(5);
        s.model.ammo = e.ammo();
        s.model.active = false;
        s.model.age = e.tickCount + partial;
        s.model.sinceLaunch = 1000;
    }

    @Override protected AABB getBoundingBoxForCulling(SamDriveEntity e) { return e.getBoundingBox().inflate(6); }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.yaw)));
        s.model.lightCoords = s.lightCoords;
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> SamRenderer.build(new BoxMesh(p, vc, s.lightCoords, false), s.model));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> SamRenderer.build(new BoxMesh(p, vc, 0xF000F0, true), s.model));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
