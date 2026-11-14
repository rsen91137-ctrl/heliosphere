package com.heliosphere.planets.defense.mk;

import com.heliosphere.core.client.fx.BoxMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;

/** The portable sentry: folding tripod, a small solar panel, a turning head with a light gun and a charge lamp. */
public final class PortableSentryRenderer extends EntityRenderer<PortableSentry, PortableSentryRenderer.State> {
    public PortableSentryRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    public static final class State extends EntityRenderState {
        float yaw, pitch, sinceShot, age;
        int charge;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(PortableSentry e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.aimYaw(); s.pitch = e.aimPitch(); s.charge = e.chargePercent();
        s.age = e.tickCount + partial;
        s.sinceShot = (float) (e.level().getGameTime() - e.lastShot()) + partial;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, s.lightCoords, false), s));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, 0xF000F0, true), s));
        super.submit(s, pose, out, camera);
    }

    private static final int STEEL = 0x8C9096, DARK = 0x2E3136, SAND = 0xB89E6A, GREEN = 0x60FF90, AMBER = 0xFFB040, RED = 0xFF4030;

    static void build(BoxMesh m, State s) {
        for (int i = 0; i < 3; i++) {                                                   // the tripod
            m.push(); m.ry(i * Math.PI * 2 / 3 + 0.5); m.at(0, 0.27, 0.16); m.rx(0.5);
            m.cbox(0, 0, 0, 0.06, 0.6, 0.06, BoxMesh.JOINT, STEEL);
            m.cbox(0, -0.3, 0, 0.1, 0.04, 0.1, BoxMesh.HEAVY, DARK);
            m.pop();
        }
        m.cbox(0, 0.5, 0, 0.3, 0.14, 0.3, BoxMesh.HEAVY, DARK);                         // battery hub
        m.cbox(0, 0.42, 0.155, 0.2, 0.06, 0.01, BoxMesh.HAZARD, 0xE8B420);
        int lamp = s.charge > 50 ? GREEN : s.charge > 15 ? AMBER : RED;
        m.cglow(0.155, 0.5, 0, 0.01, 0.05, 0.05, BoxMesh.LAMP, lamp);
        m.push();
        m.at(0, 0.6, 0);
        m.ry(Math.toRadians(-s.yaw));
        m.cbox(0, 0.03, 0, 0.22, 0.06, 0.22, BoxMesh.JOINT, STEEL);                     // turntable
        m.at(0, 0.18, 0);
        m.rx(Math.toRadians(-Mth.clamp(s.pitch, -60, 60)));
        m.cbox(0, 0, -0.02, 0.26, 0.2, 0.32, BoxMesh.PLATE, SAND);                      // head
        m.cbox(0, 0.13, -0.08, 0.34, 0.02, 0.24, BoxMesh.CARBON, 0x2A3A5A);             // solar panel on its back
        float recoil = s.sinceShot < 3 ? (1 - s.sinceShot / 3f) * 0.05f : 0;
        m.cbox(0, 0, 0.3 - recoil, 0.06, 0.06, 0.34, BoxMesh.BARREL, STEEL);            // the gun
        m.cbox(0, 0, 0.47 - recoil, 0.09, 0.09, 0.04, BoxMesh.DARK, DARK);
        m.cbox(0.11, 0.03, 0.15, 0.05, 0.05, 0.08, BoxMesh.DARK, DARK);                 // sensor
        m.cglow(0.11, 0.03, 0.195, 0.035, 0.035, 0.01, BoxMesh.LENS, 0x60E0FF);
        if (s.sinceShot < 2) m.cglow(0, 0, 0.52, 0.12, 0.12, 0.06, BoxMesh.EMIT, 0xFFE080);   // muzzle flash
        m.pop();
    }
}
