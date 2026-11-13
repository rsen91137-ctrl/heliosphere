package com.heliosphere.planets.defense.sam.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MissileSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** Draws a guided missile along its flight path, nose first. */
public final class MissileRenderer extends EntityRenderer<GuidedMissile, MissileRenderer.State> {
    public MissileRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    public static final class State extends EntityRenderState {
        MissileSpec.Model model = MissileSpec.Model.PLAYER;
        boolean booster;
        int motor;
        float yaw, pitch, age;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(GuidedMissile e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.model = e.spec().model;
        s.booster = e.boosterAttached();
        s.motor = e.motor();
        s.yaw = e.getYRot(partial);
        s.pitch = e.getXRot(partial);
        s.age = e.tickCount + partial;
    }

    @Override protected AABB getBoundingBoxForCulling(GuidedMissile e) { return e.getBoundingBox().inflate(6); }
    @Override protected boolean affectedByCulling(GuidedMissile e) { return false; }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.yaw)).rotateX((float) Math.toRadians(s.pitch)));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> MissileModels.draw(new BoxMesh(p, vc, s.lightCoords, false), s.model, s.booster, s.motor, s.age));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> MissileModels.draw(new BoxMesh(p, vc, 0xF000F0, true), s.model, s.booster, s.motor, s.age));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
