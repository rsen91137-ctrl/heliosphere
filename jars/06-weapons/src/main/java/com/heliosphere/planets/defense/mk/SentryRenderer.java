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

/**
 * Draws the turrets: a common armoured base, and per kind a head that turns and elevates towards its target. Each
 * upgrade (Mk I-V) shows: the head grows (x1.06 a Mk, x1.3 at Mk V), the paint and trim change (gunmetal, desert tan,
 * navy grey, black with gold, white chrome), the body material changes (plate, armour, carbon fibre, chrome), and
 * parts are added: side armour from Mk II, a sensor mast from Mk III, heat-sink fins from Mk IV, glowing energy
 * seams and a halo ring at Mk V.
 */
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

    /** Per Mk 0-V: paint tint (mixed into the kind's colour), trim colour, body material, stripe colour. */
    private static final int[] TINT = {-1, 0x4A5058, 0xB89E6A, 0x3C4A64, 0x1E2024, 0xECEEF2};
    private static final int[] TRIM = {0xE8B420, 0xD07A28, 0xC8B080, 0x4A88D0, 0xD8A830, 0x60E8FF};
    private static final int[] BODY = {BoxMesh.PLATE, BoxMesh.PLATE, BoxMesh.HEAVY, BoxMesh.CARBON, BoxMesh.CARBON, BoxMesh.PLATE};
    private static final int[] STRIPE = {0x60FF90, 0x60FF90, 0xA0FF60, 0x60C0FF, 0xFFC040, 0x80FFFF};

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static void build(BoxMesh m, State s) {
        TurretKind k = s.kind;
        int mk = Math.max(0, Math.min(5, s.mk));
        int base = switch (k) {
            case LASER -> 0xC8CCD0; case SONIC -> 0x5A8CA8; case AUTOCANNON -> OLIVE; case FLAK -> 0x8A8060;
            case MISSILE -> 0x707A84; case RAIL -> 0xE6E8EC; case TESLA -> 0x3E4258;
        };
        int paint = mk == 0 ? base : mix(base, TINT[mk], mk >= 4 ? 0.75f : 0.6f);
        int trim = TRIM[mk], body = BODY[mk];
        int glow = mk >= 5 ? mix(k.colour, 0xFFFFFF, 0.35f) : k.colour;
        int metal = mk >= 4 ? (mk >= 5 ? 0xD8DCE2 : 0x2A2C30) : STEEL;            // barrels: steel, blackened, chrome
        // base: plinth, ring in the Mk's trim, upgrade stripes (one per Mk) on all four sides
        m.cbox(0, 0.12, 0, 0.92, 0.24, 0.92, BoxMesh.HEAVY, mk >= 4 ? 0x202226 : DARK);
        if (mk >= 5) m.cglow(0, 0.3, 0, 0.8, 0.12, 0.8, BoxMesh.EMIT, trim);
        else m.cbox(0, 0.3, 0, 0.78, 0.12, 0.78, mk >= 3 ? BoxMesh.JOINT : BoxMesh.HAZARD, trim);
        m.cbox(0, 0.48, 0, 0.6 + mk * 0.02, 0.24, 0.6 + mk * 0.02, body, paint);
        double side = 0.31 + mk * 0.01;
        for (int i = 0; i < mk; i++) for (int f = 0; f < 4; f++) {
            m.push();
            m.ry(f * Math.PI / 2);
            m.cglow(-side, 0.4 + i * 0.05, 0, 0.02, 0.03, 0.22, BoxMesh.LAMP, STRIPE[mk]);
            m.pop();
        }
        if (mk >= 2) for (int f = 0; f < 4; f++) {                                   // corner armour blocks on the plinth
            m.push();
            m.ry(f * Math.PI / 2 + Math.PI / 4);
            m.cbox(0, 0.3, 0.52, 0.18, 0.2, 0.08, BoxMesh.HEAVY, paint);
            if (mk >= 4) m.cbox(0, 0.41, 0.52, 0.2, 0.02, 0.1, BoxMesh.JOINT, trim);
            m.pop();
        }
        float recoil = s.sinceShot < 4 ? (1 - s.sinceShot / 4f) : 0;
        boolean firing = s.sinceShot < 3;
        m.push();
        m.at(0, 0.62, 0);
        m.ry(Math.toRadians(-s.yaw));
        m.cbox(0, 0.06, 0, 0.42 + mk * 0.03, 0.12, 0.42 + mk * 0.03, BoxMesh.JOINT, mk >= 4 ? trim : STEEL);   // turntable
        m.at(0, 0.32, 0);
        m.scale(1 + mk * 0.06);                                                         // the head grows with each Mk
        float pitch = (float) Math.toRadians(-Math.max(-75, Math.min(85, s.pitch)));          // depression down to 75 degrees
        switch (k) {
            case LASER -> {
                m.cbox(0, 0, 0, 0.44, 0.34, 0.5, body, paint);
                m.rx(pitch);
                m.cbox(0, 0.02, 0.38 - recoil * 0.05, 0.12, 0.12, 0.5, BoxMesh.BARREL, metal);
                m.cbox(0, 0.02, 0.64, 0.18, 0.18, 0.06, BoxMesh.DARK, DARK);
                m.cglow(0, 0.02, 0.68, 0.11, 0.11, 0.02, BoxMesh.LENS, firing ? 0xFFFFFF : glow);
                m.cglow(0, 0.18, 0, 0.3, 0.02, 0.2, BoxMesh.EMIT, glow);
            }
            case SONIC -> {
                m.cbox(0, 0, 0, 0.5, 0.3, 0.5, body, paint);
                m.rx(pitch * 0.5);
                for (int i = 0; i < 3; i++) m.cbox(0, 0.02, 0.28 + i * 0.07, 0.46 - i * 0.1, 0.46 - i * 0.1, 0.05, BoxMesh.MESH, metal);
                float pulse = firing ? 1 : 0.5f + 0.5f * (float) Math.sin(s.age * 0.3);
                m.cglow(0, 0.02, 0.5, 0.16 * pulse + 0.06, 0.16 * pulse + 0.06, 0.02, BoxMesh.THRUST, glow);
            }
            case AUTOCANNON -> {
                m.cbox(0, 0, -0.05, 0.5, 0.32, 0.56, body, paint);
                m.cbox(-0.31, -0.02, -0.05, 0.12, 0.24, 0.36, BoxMesh.HAZARD, 0xE8B420);       // ammo drum
                m.rx(pitch);
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.09, 0.03, 0.42 - recoil * 0.08 * (sx > 0 ? 1 : 0.3), 0.07, 0.07, 0.6, BoxMesh.BARREL, metal);
                    m.cbox(sx * 0.09, 0.03, 0.74, 0.1, 0.1, 0.06, BoxMesh.VENT, DARK);
                }
                if (firing) m.cglow(0.09, 0.03, 0.8, 0.12, 0.12, 0.02, BoxMesh.LAMP, 0xFFD080);
            }
            case FLAK -> {
                m.cbox(0, 0, 0, 0.56, 0.3, 0.5, body, paint);
                m.rx(pitch - 0.25);
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.17, 0.06, 0.4 - recoil * 0.1, 0.11, 0.11, 0.72, BoxMesh.BARREL, metal);
                    m.cbox(sx * 0.17, 0.06, 0.78, 0.15, 0.15, 0.08, BoxMesh.DARK, DARK);
                }
                m.cglow(0, 0.18, 0.1, 0.12, 0.03, 0.12, BoxMesh.LAMP, glow);
            }
            case MISSILE -> {
                m.cbox(0, 0, 0, 0.3, 0.3, 0.3, BoxMesh.JOINT, metal);
                m.rx(pitch - 0.35);
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.26, 0.04, 0.08, 0.24, 0.3, 0.56, body, paint);       // pods
                    for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
                        double px = sx * (0.2 + c * 0.11), py = -0.04 + r * 0.14;
                        m.cbox(px, py, 0.37, 0.08, 0.08, 0.02, BoxMesh.DARK, DARK);
                        m.cbox(px, py, 0.375, 0.05, 0.05, 0.02, BoxMesh.PLATE, 0xD03028);
                    }
                }
                m.cglow(0, 0.1, 0.16, 0.08, 0.08, 0.02, BoxMesh.LENS, glow);
            }
            case RAIL -> {
                m.cbox(0, 0, -0.1, 0.42, 0.3, 0.6, body, paint);
                m.rx(pitch);
                m.cbox(0, 0.04, 0.45, 0.2, 0.14, 1.1, BoxMesh.HEAVY, DARK);
                for (int i = 0; i < 4; i++) m.cbox(0, 0.04, 0.05 + i * 0.25, 0.26, 0.2, 0.05, BoxMesh.JOINT, metal);
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
        // the upgrades' parts, in the head's frame (they turn with it)
        if (mk >= 2) for (int sx = -1; sx <= 1; sx += 2) {                           // Mk II: side armour skirts
            m.cbox(sx * 0.3, -0.06, 0, 0.05, 0.26, 0.46, BoxMesh.HEAVY, paint);
            m.cbox(sx * 0.33, 0.06, 0, 0.02, 0.03, 0.4, BoxMesh.JOINT, trim);
        }
        if (mk >= 3) {                                                               // Mk III: sensor mast with a lens
            m.cbox(-0.14, 0.24, -0.16, 0.04, 0.22, 0.04, BoxMesh.JOINT, metal);
            m.cbox(-0.14, 0.37, -0.16, 0.12, 0.08, 0.1, BoxMesh.DARK, DARK);
            m.cglow(-0.14, 0.37, -0.105, 0.06, 0.04, 0.01, BoxMesh.LENS, mk >= 5 ? trim : 0xFF4030);
        }
        if (mk >= 4) for (int i = 0; i < 4; i++)                                     // Mk IV: heat-sink fins at the back
            m.cbox(0, -0.04 + i * 0.07, -0.3, 0.34, 0.025, 0.12, BoxMesh.VENT, mk >= 5 ? 0xB8C0C8 : 0x3A3C40);
        if (mk >= 5) {                                                               // Mk V: energy seams and a halo
            for (int sx = -1; sx <= 1; sx += 2) m.cglow(sx * 0.335, 0.0, 0, 0.012, 0.012, 0.42, BoxMesh.EMIT, trim);
            float spin = s.age * 0.05f;
            m.push();
            m.at(0, 0.3, 0);
            m.ry(spin);
            for (int i = 0; i < 8; i++) {
                m.push();
                m.ry(i * Math.PI / 4);
                m.cglow(0, 0, 0.34, 0.14, 0.015, 0.02, BoxMesh.EMIT, trim);
                m.pop();
            }
            m.pop();
        }
        m.pop();
    }
}
