import com.heliosphere.core.fx.sim.AsteroidImpact;
import com.heliosphere.core.fx.sim.FxAtlas;
import com.heliosphere.core.fx.sim.MushroomCloud;
import com.heliosphere.core.fx.sim.SpriteSink;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline look-dev for the effects: a small software renderer that draws the same sprites, with the same
 * atlas and the same blending as the game, to PNG files. Run through tools/fxpreview/run.sh.
 */
public final class FxPreview implements SpriteSink {
    final int w, h;
    final float[] fb;
    final double[] eye, fwd = new double[3], right = new double[3], up = new double[3];
    final double focal;
    final double groundY;
    final List<double[]> sprites = new ArrayList<>();
    static final float[] ATLAS = FxAtlas.pixels();

    FxPreview(int w, int h, double[] eye, double[] target, double fovDeg, double groundY) {
        this.w = w; this.h = h; this.fb = new float[w * h * 3]; this.eye = eye; this.groundY = groundY;
        double fx = target[0] - eye[0], fy = target[1] - eye[1], fz = target[2] - eye[2];
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fwd[0] = fx / fl; fwd[1] = fy / fl; fwd[2] = fz / fl;
        // right = fwd x worldUp
        double rx = -fwd[2], rz = fwd[0], rl = Math.sqrt(rx * rx + rz * rz);
        right[0] = rx / rl; right[1] = 0; right[2] = rz / rl;
        up[0] = right[1] * fwd[2] - right[2] * fwd[1];
        up[1] = right[2] * fwd[0] - right[0] * fwd[2];
        up[2] = right[0] * fwd[1] - right[1] * fwd[0];
        focal = h * 0.5 / Math.tan(Math.toRadians(fovDeg) * 0.5);
    }

    void background(float day) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5 - w * 0.5) / focal, dy = -(y + 0.5 - h * 0.5) / focal;
                double rx = fwd[0] + right[0] * dx + up[0] * dy, ry = fwd[1] + right[1] * dx + up[1] * dy, rz = fwd[2] + right[2] * dx + up[2] * dy;
                double l = Math.sqrt(rx * rx + ry * ry + rz * rz);
                ry /= l; rx /= l; rz /= l;
                float r, g, b;
                if (ry < 0 && eye[1] > groundY) {
                    double tHit = (groundY - eye[1]) / ry;
                    double gx = eye[0] + rx * tHit, gz = eye[2] + rz * tHit;
                    float n = com.heliosphere.core.fx.sim.Rng.fbm((float) gx * 0.03f, 0.5f, (float) gz * 0.03f, 7, 4);
                    float fog = (float) Math.min(1, tHit / 900);
                    r = (0.26f + 0.12f * n) * day; g = (0.42f + 0.16f * n) * day; b = (0.20f + 0.08f * n) * day;
                    r += (0.62f * day - r) * fog; g += (0.74f * day - g) * fog; b += (0.92f * day - b) * fog;
                } else {
                    float k = (float) Math.pow(Math.max(0, ry), 0.5);
                    r = (0.66f - 0.36f * k) * day; g = (0.78f - 0.30f * k) * day; b = (0.95f - 0.14f * k) * day;
                }
                int i = (y * w + x) * 3;
                fb[i] = r; fb[i + 1] = g; fb[i + 2] = b;
            }
        }
    }

    static float q8(float v) { return Math.round(Math.max(0, Math.min(1, v)) * 255f) / 255f; }

    @Override public void billboard(double x, double y, double z, float size, float roll, int tile, float r, float g, float b, float a, float er, float eg, float eb) {
        sprites.add(new double[]{0, x, y, z, size, roll, tile, q8(r * a + er), q8(g * a + eg), q8(b * a + eb), q8(Math.max(a, 1 / 255f)), 0});
    }

    @Override public void ribbon(double ax, double ay, double az, double bx, double by, double bz, float width, int tile, float r, float g, float b, float a, float er, float eg, float eb) {}

    @Override public void ground(double x, double y, double z, float size, float yaw, int tile, float r, float g, float b, float a, float er, float eg, float eb) {
        sprites.add(new double[]{1, x, y, z, size, yaw, tile, q8(r * a + er), q8(g * a + eg), q8(b * a + eb), q8(Math.max(a, 1 / 255f)), 0});
    }

    static void sample(int tile, float u, float v, float[] out) {
        float fx = u * (FxAtlas.TILE - 1), fy = v * (FxAtlas.TILE - 1);
        int x0 = (int) fx, y0 = (int) fy, x1 = Math.min(FxAtlas.TILE - 1, x0 + 1), y1 = Math.min(FxAtlas.TILE - 1, y0 + 1);
        float tx = fx - x0, ty = fy - y0;
        int ox = (tile % FxAtlas.GRID) * FxAtlas.TILE, oy = (tile / FxAtlas.GRID) * FxAtlas.TILE;
        for (int c = 0; c < 4; c++) {
            float p00 = ATLAS[((oy + y0) * FxAtlas.SIZE + ox + x0) * 4 + c], p10 = ATLAS[((oy + y0) * FxAtlas.SIZE + ox + x1) * 4 + c];
            float p01 = ATLAS[((oy + y1) * FxAtlas.SIZE + ox + x0) * 4 + c], p11 = ATLAS[((oy + y1) * FxAtlas.SIZE + ox + x1) * 4 + c];
            out[c] = (p00 * (1 - tx) + p10 * tx) * (1 - ty) + (p01 * (1 - tx) + p11 * tx) * ty;
        }
    }

    void flush() {
        for (double[] s : sprites) {
            double dx = s[1] - eye[0], dy = s[2] - eye[1], dz = s[3] - eye[2];
            s[11] = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
        }
        sprites.sort((p, q) -> Double.compare(q[11], p[11]));
        float[] tex = new float[4];
        for (double[] s : sprites) {
            double depth = s[11];
            if (depth < 1) continue;
            double dx = s[1] - eye[0], dy = s[2] - eye[1], dz = s[3] - eye[2];
            double sxc = w * 0.5 + (dx * right[0] + dy * right[1] + dz * right[2]) / depth * focal;
            double syc = h * 0.5 - (dx * up[0] + dy * up[1] + dz * up[2]) / depth * focal;
            int tile = (int) s[6];
            float cr = (float) s[7], cg = (float) s[8], cb = (float) s[9], ca = (float) s[10];
            if (s[0] == 0) {
                double half = s[4] * 0.5 / depth * focal;
                if (half < 0.3) continue;
                double cos = Math.cos(s[5]), sin = Math.sin(s[5]);
                double ext = half * 1.42;
                int x0 = (int) Math.max(0, sxc - ext), x1 = (int) Math.min(w - 1, sxc + ext), y0 = (int) Math.max(0, syc - ext), y1 = (int) Math.min(h - 1, syc + ext);
                // sprites below the ground plane are hidden by it
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                    double px = (x + 0.5 - sxc) / half, py = (y + 0.5 - syc) / half;
                    double u = px * cos + py * sin, v = -px * sin + py * cos;
                    if (u < -1 || u > 1 || v < -1 || v > 1) continue;
                    // world height of this texel, to clip against the ground
                    double wy = s[2] - py * s[4] * 0.5 * up[1];
                    if (wy < groundY - 0.01 && eye[1] > groundY) continue;
                    sample(tile, (float) (u * 0.5 + 0.5), (float) (v * 0.5 + 0.5), tex);
                    blend(x, y, cr * tex[0], cg * tex[1], cb * tex[2], ca * tex[3]);
                }
            } else {
                // horizontal quad: cast each pixel's ray onto the plane y = s[2]
                double half = s[4] * 0.5;
                double ext = half * 1.5 / depth * focal;
                int x0 = (int) Math.max(0, sxc - ext * 1.2), x1 = (int) Math.min(w - 1, sxc + ext * 1.2), y0 = (int) Math.max(0, syc - ext), y1 = (int) Math.min(h - 1, syc + ext);
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                    double ddx = (x + 0.5 - w * 0.5) / focal, ddy = -(y + 0.5 - h * 0.5) / focal;
                    double rx = fwd[0] + right[0] * ddx + up[0] * ddy, ry = fwd[1] + right[1] * ddx + up[1] * ddy, rz = fwd[2] + right[2] * ddx + up[2] * ddy;
                    if (Math.abs(ry) < 1e-6) continue;
                    double tt = (s[2] - eye[1]) / ry;
                    if (tt <= 0) continue;
                    double u = (eye[0] + rx * tt - s[1]) / half, v = (eye[2] + rz * tt - s[3]) / half;
                    if (u < -1 || u > 1 || v < -1 || v > 1) continue;
                    sample(tile, (float) (u * 0.5 + 0.5), (float) (v * 0.5 + 0.5), tex);
                    blend(x, y, cr * tex[0], cg * tex[1], cb * tex[2], ca * tex[3]);
                }
            }
        }
        sprites.clear();
    }

    void blend(int x, int y, float r, float g, float b, float a) {
        if (a <= 0) return;
        int i = (y * w + x) * 3;
        fb[i] = Math.min(1, r + fb[i] * (1 - a));
        fb[i + 1] = Math.min(1, g + fb[i + 1] * (1 - a));
        fb[i + 2] = Math.min(1, b + fb[i + 2] * (1 - a));
    }

    void whiteout(float f) {
        for (int i = 0; i < fb.length; i++) fb[i] = fb[i] + (1 - fb[i]) * f;
    }

    void blit(BufferedImage img, int ox, int oy) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 3;
            int r = (int) (Math.min(1, fb[i]) * 255), g = (int) (Math.min(1, fb[i + 1]) * 255), b = (int) (Math.min(1, fb[i + 2]) * 255);
            img.setRGB(ox + x, oy + y, 0xff000000 | r << 16 | g << 8 | b);
        }
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "nuke";
        String out = args.length > 1 ? args[1] : "preview.png";
        if (mode.equals("atlas")) {
            BufferedImage img = new BufferedImage(FxAtlas.SIZE, FxAtlas.SIZE, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < FxAtlas.SIZE; y++) for (int x = 0; x < FxAtlas.SIZE; x++) {
                int i = (y * FxAtlas.SIZE + x) * 4;
                float a = ATLAS[i + 3], bg = ((x / 16 + y / 16) & 1) == 0 ? 0.10f : 0.16f;
                int r = (int) (Math.min(1, ATLAS[i] + bg * (1 - a)) * 255);
                img.setRGB(x, y, 0xff000000 | r << 16 | r << 8 | r);
            }
            ImageIO.write(img, "png", new File(out));
            return;
        }
        if (mode.equals("impact")) { impact(args, out); return; }
        float day = args.length > 2 ? Float.parseFloat(args[2]) : 1f;
        double dist = args.length > 3 ? Double.parseDouble(args[3]) : 330;
        float[] times = {0.12f, 0.6f, 1.4f, 3f, 6f, 10f, 16f, 26f, 45f, 70f};
        if (args.length > 4) { String[] p = args[4].split(","); times = new float[p.length]; for (int i = 0; i < p.length; i++) times[i] = Float.parseFloat(p[i]); }
        int cols = Math.min(5, times.length), rows = (times.length + cols - 1) / cols;
        int cw = 480, ch = 400;
        BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_ARGB);
        MushroomCloud.Env env = new MushroomCloud.Env();
        env.daylight = day;
        for (int i = 0; i < times.length; i++) {
            FxPreview p = new FxPreview(cw, ch, new double[]{0, 28, -dist}, new double[]{0, 105, 0}, 62, 0);
            p.background(0.25f + 0.75f * day);
            MushroomCloud.emit(p, 0, 0, 0, times[i], 1f, 1234, env);
            p.flush();
            p.whiteout(MushroomCloud.flash(times[i]) * 0.9f);
            p.blit(sheet, (i % cols) * cw, (i / cols) * ch);
            java.awt.Graphics2D g = sheet.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.drawString(String.format("t = %.1f s", times[i]), (i % cols) * cw + 8, (i / cols) * ch + 16);
            g.dispose();
        }
        ImageIO.write(sheet, "png", new File(out));
    }

    /** impact out.png [daylight] [distance] [t1,t2,...] */
    static void impact(String[] args, String out) throws Exception {
        float day = args.length > 2 ? Float.parseFloat(args[2]) : 1f;
        double dist = args.length > 3 ? Double.parseDouble(args[3]) : 9000;
        float[] times = {0.1f, 1.5f, 6f, 20f, 45f, 90f, 160f, 260f};
        if (args.length > 4) { String[] p = args[4].split(","); times = new float[p.length]; for (int i = 0; i < p.length; i++) times[i] = Float.parseFloat(p[i]); }
        int cols = Math.min(4, times.length), rows = (times.length + cols - 1) / cols;
        int cw = 520, ch = 380;
        BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_ARGB);
        MushroomCloud.Env env = new MushroomCloud.Env();
        env.daylight = day;
        for (int i = 0; i < times.length; i++) {
            FxPreview p = new FxPreview(cw, ch, new double[]{0, 60, -dist}, new double[]{0, dist * 0.22, 0}, 75, 0);
            p.background(0.25f + 0.75f * day);
            AsteroidImpact.emit(p, 0, 0, 0, times[i], 1f, 99, 0.62f, 0.78f, 0.6f, env);
            p.flush();
            p.whiteout(AsteroidImpact.flash(times[i], (float) dist, 1f) * 0.95f);
            p.blit(sheet, (i % cols) * cw, (i / cols) * ch);
            java.awt.Graphics2D g = sheet.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.drawString(String.format("t = %.1f s  (%.0f blocks away)", times[i], dist), (i % cols) * cw + 8, (i / cols) * ch + 16);
            g.dispose();
        }
        ImageIO.write(sheet, "png", new File(out));
    }
}
