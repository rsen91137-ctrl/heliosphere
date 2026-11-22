package com.heliosphere.industry.borer.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.industry.borer.TunnelBorer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * The tunnel borer, turned to its heading: drawn in two passes (lit, then its lamps and screens at full brightness).
 * For the rider looking out in first person only the cockpit is drawn ({@link BorerModel}).
 */
public final class TunnelBorerRenderer extends EntityRenderer<TunnelBorer, TunnelBorerRenderer.State> {
    public TunnelBorerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 1.8f;
    }

    public static final class State extends EntityRenderState {
        final BorerModel.State model = new BorerModel.State();
        float heading;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(TunnelBorer e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        BorerModel.State m = s.model;
        s.heading = e.getYRot(partial);
        m.cutter = e.cutterAngle(partial);
        m.age = s.ageInTicks;
        m.boring = e.boring();
        m.moving = e.moving();
        m.auto = e.auto();
        m.status = e.status();
        m.ores = e.ores();
        m.energy = e.flag(TunnelBorer.CREATIVE) ? 1f : Mth.clamp(e.energy() / (float) Math.max(1, e.capacity()), 0f, 1f);
        m.fill = Mth.clamp(e.binUsed() / (float) Math.max(1, e.binSize()), 0f, 1f);
        // the tracks: the distance along the facing (the machine stands on block centres, so this is the same mod one
        // cleat whichever way it faces) plus, for turning on the spot, the yaw times the track's distance from the middle
        Direction f = e.facing();
        double along = s.x * f.getStepX() + s.z * f.getStepZ(), turn = BorerModel.TRACK_X * Math.toRadians(s.heading);
        m.trackLeft = BorerModel.mod(along + turn, BorerModel.LOOP);
        m.trackRight = BorerModel.mod(along - turn, BorerModel.LOOP);
        m.belt = m.cutter * 0.12;
        Minecraft mc = Minecraft.getInstance();                // the camera rides in this machine, looking out
        m.firstPerson = mc.getCameraEntity() != null && mc.getCameraEntity().getVehicle() == e && mc.options.getCameraType().isFirstPerson();
    }

    @Override protected AABB getBoundingBoxForCulling(TunnelBorer e) { return e.getBoundingBox().inflate(1.6, 0.4, 1.6); }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        BorerModel.State m = s.model;
        if (m.firstPerson && camera.pos != null) {             // where the eye is, in the machine's frame
            double dx = camera.pos.x - s.x, dz = camera.pos.z - s.z, a = Math.toRadians(s.heading);
            m.eyeX = dx * Math.cos(a) + dz * Math.sin(a);
            m.eyeZ = -dx * Math.sin(a) + dz * Math.cos(a);
            m.eyeY = camera.pos.y - s.y;
        }
        // its own work lights keep the machine from going black in an unlit tunnel
        int light = LightTexture.pack(Math.max(LightTexture.block(s.lightCoords), 6), LightTexture.sky(s.lightCoords));
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.heading)));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> BorerModel.build(new BoxMesh(p, vc, light, false), m));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> BorerModel.build(new BoxMesh(p, vc, LightTexture.FULL_BRIGHT, true), m));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
