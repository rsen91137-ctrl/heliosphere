package com.heliosphere.core.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Builds models from boxes for block-entity and entity renderers, textured from the machine kit atlas
 * (textures/entity/tech_kit.png, 8x8 tiles of greyscale detail) and tinted per part. Each model is drawn in two
 * passes: once solid with the world's light, once with only its lights at full brightness ({@code bright}).
 */
public final class BoxMesh {
    public static final Identifier KIT = Identifier.fromNamespaceAndPath("heliosphere", "textures/entity/tech_kit.png");
    public static final int PLATE = 0, HEAVY = 1, CARBON = 2, VENT = 3, HAZARD = 4, JOINT = 5, TREAD = 6, CABLE = 7,
            LENS = 8, EMIT = 9, BARREL = 10, DECAL = 11, ROTOR = 12, THRUST = 13, DARK = 14, MESH = 15, VISOR = 16, LAMP = 17;

    private final PoseStack.Pose pose;
    private final VertexConsumer out;
    private final int light;
    private final boolean bright;
    private final Deque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f m = new Matrix4f();
    private final Vector3f tmp = new Vector3f(), n = new Vector3f();

    public BoxMesh(PoseStack.Pose pose, VertexConsumer out, int light, boolean bright) {
        this.pose = pose; this.out = out; this.light = light; this.bright = bright;
    }

    public boolean bright() { return bright; }
    public void push() { stack.push(new Matrix4f(m)); }
    public void pop() { m = stack.pop(); }
    public void at(double x, double y, double z) { m.translate((float) x, (float) y, (float) z); }
    public void rx(double a) { m.rotateX((float) a); }
    public void ry(double a) { m.rotateY((float) a); }
    public void rz(double a) { m.rotateZ((float) a); }

    /** A solid box in colour {@code rgb}; skipped in the bright pass. */
    public void box(double x0, double y0, double z0, double x1, double y1, double z1, int tile, int rgb) { box(x0, y0, z0, x1, y1, z1, tile, rgb, false); }

    /** A light: drawn only in the bright pass. */
    public void glow(double x0, double y0, double z0, double x1, double y1, double z1, int tile, int rgb) { box(x0, y0, z0, x1, y1, z1, tile, rgb, true); }

    public void cbox(double cx, double cy, double cz, double w, double h, double d, int tile, int rgb) {
        box(cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, tile, rgb, false);
    }

    public void cglow(double cx, double cy, double cz, double w, double h, double d, int tile, int rgb) {
        box(cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, tile, rgb, true);
    }

    /**
     * A round body along the local Z axis from z0 to z1: radius r0 at z0 and r1 at z1 (r1 = 0 makes a cone), centred
     * on (x, y), with {@code sides} facets; ends are capped where the radius is not zero.
     */
    public void cyl(double x, double y, double z0, double z1, double r0, double r1, int sides, int tile, int rgb) { cyl(x, y, z0, z1, r0, r1, sides, tile, rgb, false); }

    public void cylGlow(double x, double y, double z0, double z1, double r0, double r1, int sides, int tile, int rgb) { cyl(x, y, z0, z1, r0, r1, sides, tile, rgb, true); }

    private void cyl(double x, double y, double z0, double z1, double r0, double r1, int sides, int tile, int rgb, boolean lit) {
        if (lit != bright) return;
        int c = 0xFF000000 | rgb;
        float u0 = (tile % 8) / 8f, v0 = (tile / 8) / 8f, s = 1 / 8f;
        double len = z1 - z0, slope = (r0 - r1) / Math.max(1e-6, Math.abs(len));
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides, am = (a0 + a1) / 2;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            float nx = (float) Math.cos(am), ny = (float) Math.sin(am), nz = (float) slope;
            float ua = u0 + s * i / sides, ub = u0 + s * (i + 1) / sides;
            vertex(x + r0 * c0, y + r0 * s0, z0, ua, v0 + s, nx, ny, nz, c);
            vertex(x + r0 * c1, y + r0 * s1, z0, ub, v0 + s, nx, ny, nz, c);
            vertex(x + r1 * c1, y + r1 * s1, z1, ub, v0, nx, ny, nz, c);
            vertex(x + r1 * c0, y + r1 * s0, z1, ua, v0, nx, ny, nz, c);
            if (r0 > 0) {          // cap at z0, facing -Z
                vertex(x, y, z0, u0 + s / 2, v0 + s / 2, 0, 0, -1, c);
                vertex(x, y, z0, u0 + s / 2, v0 + s / 2, 0, 0, -1, c);
                vertex(x + r0 * c1, y + r0 * s1, z0, u0 + s * (float) (0.5 + 0.5 * c1), v0 + s * (float) (0.5 + 0.5 * s1), 0, 0, -1, c);
                vertex(x + r0 * c0, y + r0 * s0, z0, u0 + s * (float) (0.5 + 0.5 * c0), v0 + s * (float) (0.5 + 0.5 * s0), 0, 0, -1, c);
            }
            if (r1 > 0) {          // cap at z1, facing +Z
                vertex(x, y, z1, u0 + s / 2, v0 + s / 2, 0, 0, 1, c);
                vertex(x + r1 * c0, y + r1 * s0, z1, u0 + s * (float) (0.5 + 0.5 * c0), v0 + s * (float) (0.5 + 0.5 * s0), 0, 0, 1, c);
                vertex(x + r1 * c1, y + r1 * s1, z1, u0 + s * (float) (0.5 + 0.5 * c1), v0 + s * (float) (0.5 + 0.5 * s1), 0, 0, 1, c);
                vertex(x, y, z1, u0 + s / 2, v0 + s / 2, 0, 0, 1, c);
            }
        }
    }

    /** A thin flat fin in the local plane through the Z axis at angle {@code roll}: root chord at r0, tip chord at r1. */
    public void fin(double roll, double r0, double r1, double rootFront, double rootBack, double tipFront, double tipBack, double thick, int tile, int rgb) {
        if (bright) return;
        int c = 0xFF000000 | rgb;
        float u0 = (tile % 8) / 8f, v0 = (tile / 8) / 8f, s = 1 / 8f;
        double cr = Math.cos(roll), sr = Math.sin(roll), px = -sr * thick / 2, py = cr * thick / 2;
        double[][] q = {{r0, rootBack}, {r0, rootFront}, {r1, tipFront}, {r1, tipBack}};
        for (int side = -1; side <= 1; side += 2) {
            float nx = (float) (-sr * side), ny = (float) (cr * side);
            double ox = px * side, oy = py * side;
            int[] order = side > 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
            for (int k : order) {
                double r = q[k][0], z = q[k][1];
                vertex(cr * r + ox, sr * r + oy, z, u0 + s * (float) ((r - r0) / Math.max(1e-6, r1 - r0)), v0 + s * (k < 2 ? 1 : 0), nx, ny, 0, c);
            }
        }
    }

    private void box(double x0, double y0, double z0, double x1, double y1, double z1, int tile, int rgb, boolean lit) {
        if (lit != bright) return;
        int c = 0xFF000000 | rgb;
        float u0 = (tile % 8) / 8f, v0 = (tile / 8) / 8f, s = 1 / 8f;
        float w = (float) Math.abs(x1 - x0), h = (float) Math.abs(y1 - y0), d = (float) Math.abs(z1 - z0);
        face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, u0, v0, s * Math.min(1, w), s * Math.min(1, h), c);
        face(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, u0, v0, s * Math.min(1, w), s * Math.min(1, h), c);
        face(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, u0, v0, s * Math.min(1, d), s * Math.min(1, h), c);
        face(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, u0, v0, s * Math.min(1, d), s * Math.min(1, h), c);
        face(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, u0, v0, s * Math.min(1, w), s * Math.min(1, d), c);
        face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u0, v0, s * Math.min(1, w), s * Math.min(1, d), c);
    }

    private void face(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                      double dx, double dy, double dz, float nx, float ny, float nz, float u0, float v0, float su, float sv, int c) {
        vertex(ax, ay, az, u0, v0 + sv, nx, ny, nz, c);
        vertex(bx, by, bz, u0 + su, v0 + sv, nx, ny, nz, c);
        vertex(cx, cy, cz, u0 + su, v0, nx, ny, nz, c);
        vertex(dx, dy, dz, u0, v0, nx, ny, nz, c);
    }

    private void vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz, int c) {
        m.transformPosition((float) x, (float) y, (float) z, tmp);
        m.transformDirection(nx, ny, nz, n).normalize();
        out.addVertex(pose, tmp.x, tmp.y, tmp.z).setColor(c).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
    }
}
