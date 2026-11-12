package com.heliosphere.planets.defense.mk;

import com.heliosphere.core.client.fx.BoxMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Draws the turrets: a common armoured base, and per kind a head that turns and elevates towards its target. */
public final class SentryRenderer implements BlockEntityRenderer<SentryBlockEntity, SentryRenderer.State> {
    public SentryRenderer(BlockEntityRendererProvider.Context ctx) { }

    public static final class State extends BlockEntityRenderState {
        TurretKind kind = TurretKind.LASER;
        int mk;
        float yaw, pitch, sinceShot, age;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(SentryBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        s.kind = be.kind();
        s.mk = be.mk();
        s.yaw = be.yaw;
        s.pitch = be.pitch;
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        s.sinceShot = (now - be.lastShot) + partial;
        s.age = now + partial;
    }

    @Override public int getViewDistance() { return 128; }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, s.lightCoords, false), s));
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, 0xF000F0, true), s));
        pose.popPose();
    }

    private static final int STEEL = 0x8C9096, DARK = 0x34373C, OLIVE = 0x5E6A48;

    static void build(BoxMesh m, State s) {
        TurretKind k = s.kind;
        int paint = switch (k) {
            case LASER -> 0xC8CCD0; case SONIC -> 0x5A8CA8; case AUTOCANNON -> OLIVE; case FLAK -> 0x8A8060;
            case MISSILE -> 0x707A84; case RAIL -> 0xE6E8EC; case TESLA -> 0x3E4258;
        };
        int glow = k.colour;
        // base: plinth, ring, upgrade stripes (one per Mk)
        m.cbox(0, 0.12, 0, 0.92, 0.24, 0.92, BoxMesh.HEAVY, DARK);
        m.cbox(0, 0.3, 0, 0.78, 0.12, 0.78, BoxMesh.HAZARD, 0xE8B420);
        m.cbox(0, 0.48, 0, 0.6, 0.24, 0.6, BoxMesh.PLATE, paint);
        for (int i = 0; i < s.mk; i++) m.cglow(-0.31, 0.4 + i * 0.05, 0, 0.02, 0.03, 0.22, BoxMesh.LAMP, 0x60FF90);
        float recoil = s.sinceShot < 4 ? (1 - s.sinceShot / 4f) : 0;
        boolean firing = s.sinceShot < 3;
        m.push();
        m.at(0, 0.62, 0);
        m.ry(Math.toRadians(-s.yaw));
        m.cbox(0, 0.06, 0, 0.42, 0.12, 0.42, BoxMesh.JOINT, STEEL);                          // turntable
        m.at(0, 0.32, 0);
        float pitch = (float) Math.toRadians(-Math.max(-25, Math.min(80, s.pitch)));
        switch (k) {
            case LASER -> {
                m.cbox(0, 0, 0, 0.44, 0.34, 0.5, BoxMesh.HEAVY, paint);
                m.rx(pitch);
                m.cbox(0, 0.02, 0.38 - recoil * 0.05, 0.12, 0.12, 0.5, BoxMesh.BARREL, STEEL);
                m.cbox(0, 0.02, 0.64, 0.18, 0.18, 0.06, BoxMesh.DARK, DARK);
                m.cglow(0, 0.02, 0.68, 0.11, 0.11, 0.02, BoxMesh.LENS, firing ? 0xFFFFFF : glow);
                m.cglow(0, 0.18, 0, 0.3, 0.02, 0.2, BoxMesh.EMIT, glow);
            }
            case SONIC -> {
                m.cbox(0, 0, 0, 0.5, 0.3, 0.5, BoxMesh.PLATE, paint);
                m.rx(pitch * 0.5);
                for (int i = 0; i < 3; i++) m.cbox(0, 0.02, 0.28 + i * 0.07, 0.46 - i * 0.1, 0.46 - i * 0.1, 0.05, BoxMesh.MESH, STEEL);
                float pulse = firing ? 1 : 0.5f + 0.5f * (float) Math.sin(s.age * 0.3);
                m.cglow(0, 0.02, 0.5, 0.16 * pulse + 0.06, 0.16 * pulse + 0.06, 0.02, BoxMesh.THRUST, glow);
            }
            case AUTOCANNON -> {
                m.cbox(0, 0, -0.05, 0.5, 0.32, 0.56, BoxMesh.HEAVY, paint);
                m.cbox(-0.31, -0.02, -0.05, 0.12, 0.24, 0.36, BoxMesh.HAZARD, 0xE8B420);       // ammo drum
                m.rx(pitch);
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.09, 0.03, 0.42 - recoil * 0.08 * (sx > 0 ? 1 : 0.3), 0.07, 0.07, 0.6, BoxMesh.BARREL, STEEL);
                    m.cbox(sx * 0.09, 0.03, 0.74, 0.1, 0.1, 0.06, BoxMesh.VENT, DARK);
                }
                if (firing) m.cglow(0.09, 0.03, 0.8, 0.12, 0.12, 0.02, BoxMesh.LAMP, 0xFFD080);
            }
            case FLAK -> {
                m.cbox(0, 0, 0, 0.56, 0.3, 0.5, BoxMesh.HEAVY, paint);
                m.rx(pitch - 0.25);
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.17, 0.06, 0.4 - recoil * 0.1, 0.11, 0.11, 0.72, BoxMesh.BARREL, STEEL);
                    m.cbox(sx * 0.17, 0.06, 0.78, 0.15, 0.15, 0.08, BoxMesh.DARK, DARK);
                }
                m.cglow(0, 0.18, 0.1, 0.12, 0.03, 0.12, BoxMesh.LAMP, glow);
            }
            case MISSILE -> {
                m.cbox(0, 0, 0, 0.3, 0.3, 0.3, BoxMesh.JOINT, STEEL);
                m.rx(pitch - 0.35);
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.26, 0.04, 0.08, 0.24, 0.3, 0.56, BoxMesh.HEAVY, paint);       // pods
                    for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
                        double px = sx * (0.2 + c * 0.11), py = -0.04 + r * 0.14;
                        m.cbox(px, py, 0.37, 0.08, 0.08, 0.02, BoxMesh.DARK, DARK);
                        m.cbox(px, py, 0.375, 0.05, 0.05, 0.02, BoxMesh.PLATE, 0xD03028);
                    }
                }
                m.cglow(0, 0.1, 0.16, 0.08, 0.08, 0.02, BoxMesh.LENS, glow);
            }
            case RAIL -> {
                m.cbox(0, 0, -0.1, 0.42, 0.3, 0.6, BoxMesh.PLATE, paint);
                m.rx(pitch);
                m.cbox(0, 0.04, 0.45, 0.2, 0.14, 1.1, BoxMesh.HEAVY, DARK);
                for (int i = 0; i < 4; i++) m.cbox(0, 0.04, 0.05 + i * 0.25, 0.26, 0.2, 0.05, BoxMesh.JOINT, STEEL);
                m.cglow(0, 0.12, 0.45, 0.04, 0.02, 1.0, BoxMesh.EMIT, glow);
                if (firing) m.cglow(0, 0.04, 1.02, 0.18, 0.18, 0.02, BoxMesh.THRUST, 0xFFFFFF);
            }
            case TESLA -> {
                m.cbox(0, 0.05, 0, 0.36, 0.4, 0.36, BoxMesh.CABLE, paint);
                for (int i = 0; i < 3; i++) m.cbox(0, 0.3 + i * 0.12, 0, 0.3 - i * 0.06, 0.05, 0.3 - i * 0.06, BoxMesh.JOINT, 0xC08040);
                float pulse = firing ? 1.3f : 0.8f + 0.2f * (float) Math.sin(s.age * 0.5);
                m.cglow(0, 0.72, 0, 0.18 * pulse, 0.18 * pulse, 0.18 * pulse, BoxMesh.LAMP, glow);
            }
        }
        m.pop();
    }
}
