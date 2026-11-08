package com.heliosphere.core.client.sky;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Equirectangular surface textures for every body. Earth, Moon and Mars come from the server's baked
 * terrain; the rest are painted procedurally (banded gas giants, cratered rock, the Sun's granulation).
 */
public final class PlanetTextures {
    private static final Map<String, DynamicTexture> TEXTURES = new HashMap<>();
    public static final int W = 256, H = 128;

    private PlanetTextures() {}

    public static DynamicTexture get(CelestialBody b) {
        return TEXTURES.computeIfAbsent(b.id, k -> upload(k, procedural(b)));
    }

    /** Replace a body's texture with a baked map received from the server. */
    public static void setBaked(String body, int w, int h, byte[] rgb) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 3;
            img.setPixel(x, y, 0xFF000000 | ((rgb[i] & 0xFF) << 16) | ((rgb[i + 1] & 0xFF) << 8) | (rgb[i + 2] & 0xFF));
        }
        DynamicTexture old = TEXTURES.remove(body);
        if (old != null) old.close();
        TEXTURES.put(body, upload(body, img));
    }

    private static DynamicTexture upload(String body, NativeImage img) {
        DynamicTexture t = new DynamicTexture(() -> "heliosphere planet " + body, img);
        Identifier id = Heliosphere.id("dynamic/planet/" + body);
        Minecraft.getInstance().getTextureManager().register(id, t);
        return t;
    }

    private static NativeImage procedural(CelestialBody b) {
        NativeImage img = new NativeImage(W, H, false);
        Random r = new Random(b.id.hashCode());
        int base = b.color;
        float br = ((base >> 16) & 255) / 255f, bg = ((base >> 8) & 255) / 255f, bb = (base & 255) / 255f;
        double[] bands = new double[24];
        for (int i = 0; i < bands.length; i++) bands[i] = 0.75 + r.nextDouble() * 0.5;
        double[][] craters = new double[60][3];
        for (double[] c : craters) { c[0] = r.nextDouble() * W; c[1] = r.nextDouble() * H; c[2] = 1 + r.nextDouble() * 6; }
        for (int y = 0; y < H; y++) {
            double lat = 90 - (y + 0.5) * 180.0 / H;
            for (int x = 0; x < W; x++) {
                double f;
                if (b.kind == CelestialBody.Kind.STAR) {
                    double g = Math.sin(x * 0.9 + r.nextDouble()) * Math.sin(y * 1.1) * 0.08;
                    f = 1.0 + g + r.nextDouble() * 0.06;
                    int c = color(1.0f, 0.86f, 0.55f, (float) f);
                    img.setPixel(x, y, c);
                    continue;
                } else if (b.gasGiant) {
                    double t = (lat + 90) / 180 * bands.length;
                    int i0 = Mth.clamp((int) t, 0, bands.length - 1), i1 = Math.min(bands.length - 1, i0 + 1);
                    double frac = t - (int) t;
                    double turb = Math.sin(x * 0.15 + lat * 0.3) * 0.04 + Math.sin(x * 0.05 - lat * 0.7) * 0.03;
                    f = bands[i0] * (1 - frac) + bands[i1] * frac + turb;
                    if (b == SolarSystem.JUPITER) {
                        // Great Red Spot at about 22°S
                        double dx = (x - W * 0.62) / 14.0, dy = (lat + 22) / 6.0;
                        double d = dx * dx + dy * dy;
                        if (d < 1) { img.setPixel(x, y, color(0.75f, 0.35f, 0.22f, (float) (1.05 - d * 0.2))); continue; }
                    }
                } else {
                    f = 0.85 + r.nextDouble() * 0.1 + Math.sin(x * 0.08) * Math.cos(y * 0.11) * 0.08;
                    for (double[] c : craters) {
                        double dx = x - c[0], dy = y - c[1], d = Math.sqrt(dx * dx + dy * dy) / c[2];
                        if (d < 1) f *= 0.85 + 0.15 * d;
                        else if (d < 1.3) f *= 1.08;
                    }
                    if (Math.abs(lat) > 80 && (b == SolarSystem.EUROPA || b.tempDayC < -150)) f *= 1.15;
                }
                img.setPixel(x, y, color(br, bg, bb, (float) f));
            }
        }
        return img;
    }

    private static int color(float r, float g, float b, float f) {
        int ri = Mth.clamp((int) (r * f * 255), 0, 255), gi = Mth.clamp((int) (g * f * 255), 0, 255), bi = Mth.clamp((int) (b * f * 255), 0, 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
