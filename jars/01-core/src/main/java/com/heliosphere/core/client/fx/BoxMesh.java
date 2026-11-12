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
