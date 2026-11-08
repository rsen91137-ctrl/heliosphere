package com.heliosphere.core.client.sky;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.DVec;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.mixin.client.SkyRendererAccessor;
import com.heliosphere.core.world.PlanetGeometry;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the real solar system in the sky of Heliosphere dimensions: the Sun as a textured photosphere
 * sphere (no particles), planets and moons as lit, phased spheres at their true positions and angular
 * sizes (exaggerated 3x so they read on screen), Saturn's rings, and the star field.
 */
public final class SpaceSky {
    public static RenderPipeline PLANET;
    public static RenderPipeline GLOW;
    public static RenderPipeline RINGS;
    private static final double DIST = 100.0;
    private static final double VIS = 3.0;
    private static DynamicTexture glowTex, ringTex;

    private SpaceSky() {}

    public static void initPipelines() {
        PLANET = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Heliosphere.id("pipeline/planet"))
                .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false).withCull(true)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES).build());
        GLOW = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Heliosphere.id("pipeline/sky_glow"))
                .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0").withBlend(BlendFunction.LIGHTNING).withDepthWrite(false).withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES).build());
        RINGS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Heliosphere.id("pipeline/rings"))
                .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false).withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES).build());
    }

    public static boolean active(ClientLevel level) { return level != null && Planets.isHeliosphereDimension(level); }

    /** Orthonormal mapping from the ecliptic (navigation) frame into the local sky frame. */
    private record Frame(DVec e1, DVec e2, DVec e3, Vector3f s, Vector3f t, Vector3f n, boolean identity) {
        Vector3f toSky(DVec v) {
            if (identity) return new Vector3f((float) v.x(), (float) v.y(), (float) v.z());
            float a = (float) v.dot(e1), b = (float) v.dot(e2), c = (float) v.dot(e3);
            return new Vector3f(s).mul(a).add(new Vector3f(t).mul(b)).add(new Vector3f(n).mul(c));
        }
    }

    public static void render(SkyRenderer vanilla, PoseStack pose, float sunAngle, float starAngle, float starBrightness) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double days = level.getGameTime() / 24000.0;
        CelestialBody here = Planets.bodyOf(level);
        boolean space = here == null;
        DVec obs = space ? ClientNav.observer(days) : OrbitMath.position(here, days);

        Frame frame;
        if (space) frame = new Frame(null, null, null, null, null, null, true);
        else {
            DVec sun = obs.scale(-1);
            DVec e1 = new DVec(sun.x(), 0, sun.z()).normalize();
            DVec e3 = new DVec(0, 1, 0);
            DVec e2 = e3.cross(e1);
            Vector3f s = new Vector3f((float) -Math.sin(sunAngle), (float) Math.cos(sunAngle), 0);
            Vector3f n = new Vector3f(0, 0, -1);
            Vector3f t = new Vector3f(n).cross(s);
            frame = new Frame(e1, e2, e3, s, t, n, false);
        }

        // Stars: always visible in vacuum, governed by the timeline elsewhere
        float stars = (here == null || here.pressureBar < 0.001) ? 1.0f : starBrightness;
        if (stars > 0) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.XP.rotation(starAngle));
            ((SkyRendererAccessor) vanilla).heliosphere$renderStars(stars, pose);
            pose.popPose();
        }

        List<Draw> draws = new ArrayList<>();
        for (CelestialBody b : SolarSystem.all()) {
            if (b == here) continue;
            DVec rel = OrbitMath.position(b, days).sub(obs);
            double dist = rel.length();
            if (dist < 1) continue;
            Vector3f dir = frame.toSky(rel.scale(1 / dist));
            // a tidally locked moon always shows its parent at the same place in the sky
            if (here != null && here.tidallyLocked && b.id.equals(here.parent)) dir = lockedParentDir(level, here, mc.player);
            DVec toSun = OrbitMath.position(SolarSystem.SUN, days).sub(OrbitMath.position(b, days));
            Vector3f light = b.kind == CelestialBody.Kind.STAR ? null : frame.toSky(toSun.normalize());
            double ang = Math.atan(b.radiusKm / dist) * (b.kind == CelestialBody.Kind.STAR ? 1.8 : VIS);
            draws.add(new Draw(b, dir.normalize(), light, ang, dist, frame.toSky(new DVec(0, 1, 0)), days));
        }
        draws.sort((a, c) -> Double.compare(c.dist, a.dist));
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(pose.last().pose());
        for (Draw d : draws) d.render();
        mv.popMatrix();
    }

    private static Vector3f lockedParentDir(ClientLevel level, CelestialBody here, Player player) {
        double lat = 0, lon = 0;
        if (player != null) {
            double[] ll = PlanetGeometry.of(here).latLon(player.getX(), player.getZ());
            lat = Math.toRadians(ll[0]); lon = Math.toRadians(ll[1]);
        }
        float e = (float) -Math.sin(lon);
        float n = (float) (-Math.sin(lat) * Math.cos(lon));
        float u = (float) (Math.cos(lat) * Math.cos(lon));
        return new Vector3f(e, u, -n);
    }

    private record Draw(CelestialBody body, Vector3f dir, Vector3f light, double angRadius, double dist, Vector3f north, double days) {
        void render() {
            float minAng = 0.0035f;
            boolean star = body.kind == CelestialBody.Kind.STAR;
            if (angRadius < minAng && !star) { drawPoint(); return; }
            float r = (float) (DIST * Math.tan(Math.min(angRadius, 1.2)));
            Vector3f c = new Vector3f(dir).mul((float) DIST);
            if (star) drawGlow(c, r * 5.5f, 1.0f, 0.93f, 0.75f, 0.9f);
            drawSphere(c, r);
            if (body.ringOuterKm > 0) drawRings(c, r);
        }

        /** Unresolved planets show as star-like points whose brightness follows reflected sunlight. */
        private void drawPoint() {
            DVec pos = OrbitMath.position(body, days);
            double sunAu = pos.length() / CelestialBody.AU_KM;
            double flux = body.radiusKm * body.radiusKm / (sunAu * sunAu * dist * dist);
            double ref = 69911.0 * 69911.0 / (27.0 * Math.pow(4.2 * CelestialBody.AU_KM, 2));
            float a = (float) Math.max(0.08, Math.min(1.0, 0.55 + 0.22 * Math.log10(flux / ref)));
            if (a <= 0.08f) return;
            int col = body.color;
            Vector3f c = new Vector3f(dir).mul((float) DIST);
            drawGlow(c, 0.9f, ((col >> 16) & 255) / 255f * 0.6f + 0.4f, ((col >> 8) & 255) / 255f * 0.6f + 0.4f, (col & 255) / 255f * 0.6f + 0.4f, a);
        }

        private void drawSphere(Vector3f c, float r) {
            Vector3f y = new Vector3f(north).normalize();
            Vector3f x = Math.abs(y.y) < 0.95f ? new Vector3f(0, 1, 0).cross(y).normalize() : new Vector3f(1, 0, 0).cross(y).normalize();
            Vector3f z = new Vector3f(x).cross(y).normalize();
            double spin = body.kind == CelestialBody.Kind.STAR ? days * 0.04 : days * 24.0 / Math.max(1, Math.abs(body.solarDayHours));
            float spinRad = (float) ((spin % 1.0) * Math.PI * 2);
            int lat = 24, lon = 48;
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            boolean any = false;
            for (int i = 0; i < lat; i++) {
                for (int j = 0; j < lon; j++) {
                    float[][] q = new float[4][];
                    q[0] = vert(c, r, x, y, z, i, j, lat, lon, spinRad);
                    q[1] = vert(c, r, x, y, z, i + 1, j, lat, lon, spinRad);
                    q[2] = vert(c, r, x, y, z, i + 1, j + 1, lat, lon, spinRad);
                    q[3] = vert(c, r, x, y, z, i, j + 1, lat, lon, spinRad);
                    tri(bb, c, q[0], q[1], q[2]);
                    tri(bb, c, q[0], q[2], q[3]);
                    any = true;
                }
            }
            if (any) draw(bb, PlanetTextures.get(body), PLANET);
        }

        /** Vertex: x y z u v shade. */
        private float[] vert(Vector3f c, float r, Vector3f ax, Vector3f ay, Vector3f az, int i, int j, int lat, int lon, float spin) {
            double th = Math.PI * i / lat, ph = 2 * Math.PI * j / lon;
            double lonAng = ph - Math.PI + spin;
            float sx = (float) (Math.sin(th) * Math.cos(lonAng)), sy = (float) Math.cos(th), sz = (float) (Math.sin(th) * -Math.sin(lonAng));
            Vector3f nrm = new Vector3f(ax).mul(sx).add(new Vector3f(ay).mul(sy)).add(new Vector3f(az).mul(sz));
            float shade = 1f;
            if (light != null) {
                float l = nrm.dot(light);
                shade = Math.max(0.035f, Math.min(1f, l * 1.15f + 0.04f));
                if (body.gasGiant) shade *= 0.85f + 0.15f * Math.abs(nrm.dot(new Vector3f(dir).negate()));
            }
            Vector3f p = new Vector3f(nrm).mul(r).add(c);
            return new float[]{p.x, p.y, p.z, (float) j / lon, (float) i / lat, shade};
        }

        private void tri(BufferBuilder bb, Vector3f c, float[] a, float[] b, float[] d) {
            // keep outward-facing counter-clockwise winding so back faces are culled
            Vector3f ab = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
            Vector3f ad = new Vector3f(d[0] - a[0], d[1] - a[1], d[2] - a[2]);
            Vector3f nn = ab.cross(ad);
            Vector3f out = new Vector3f(a[0] - c.x, a[1] - c.y, a[2] - c.z);
            if (nn.lengthSquared() < 1e-12f) return;
            if (nn.dot(out) < 0) { float[] t = b; b = d; d = t; }
            put(bb, a); put(bb, b); put(bb, d);
        }

        private void put(BufferBuilder bb, float[] v) {
            int s = (int) (v[5] * 255);
            bb.addVertex(v[0], v[1], v[2]).setUv(v[3], v[4]).setColor(s, s, s, 255);
        }

        private void drawRings(Vector3f c, float r) {
            float inner = (float) (r * body.ringInnerKm / body.radiusKm), outer = (float) (r * body.ringOuterKm / body.radiusKm);
            Vector3f y = new Vector3f(north).normalize();
            Vector3f x = Math.abs(y.y) < 0.95f ? new Vector3f(0, 1, 0).cross(y).normalize() : new Vector3f(1, 0, 0).cross(y).normalize();
            Vector3f z = new Vector3f(x).cross(y).normalize();
            int seg = 64;
            float lit = light == null ? 1 : Math.max(0.25f, Math.abs(light.dot(y)) * 0.6f + 0.4f);
            int s = (int) (lit * 255);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int k = 0; k < seg; k++) {
                double a0 = 2 * Math.PI * k / seg, a1 = 2 * Math.PI * (k + 1) / seg;
                Vector3f d0 = new Vector3f(x).mul((float) Math.cos(a0)).add(new Vector3f(z).mul((float) Math.sin(a0)));
                Vector3f d1 = new Vector3f(x).mul((float) Math.cos(a1)).add(new Vector3f(z).mul((float) Math.sin(a1)));
                Vector3f i0 = new Vector3f(d0).mul(inner).add(c), o0 = new Vector3f(d0).mul(outer).add(c);
                Vector3f i1 = new Vector3f(d1).mul(inner).add(c), o1 = new Vector3f(d1).mul(outer).add(c);
                bb.addVertex(i0.x, i0.y, i0.z).setUv(0, 0.5f).setColor(s, s, s, 200);
                bb.addVertex(o0.x, o0.y, o0.z).setUv(1, 0.5f).setColor(s, s, s, 200);
                bb.addVertex(o1.x, o1.y, o1.z).setUv(1, 0.5f).setColor(s, s, s, 200);
                bb.addVertex(i0.x, i0.y, i0.z).setUv(0, 0.5f).setColor(s, s, s, 200);
                bb.addVertex(o1.x, o1.y, o1.z).setUv(1, 0.5f).setColor(s, s, s, 200);
                bb.addVertex(i1.x, i1.y, i1.z).setUv(0, 0.5f).setColor(s, s, s, 200);
            }
            draw(bb, ringTexture(), RINGS);
        }

        private void drawGlow(Vector3f c, float size, float rr, float gg, float bbC, float alpha) {
            Vector3f d = new Vector3f(dir).normalize();
            Vector3f u = Math.abs(d.y) < 0.95f ? new Vector3f(0, 1, 0).cross(d).normalize() : new Vector3f(1, 0, 0).cross(d).normalize();
            Vector3f v = new Vector3f(d).cross(u).normalize();
            Vector3f p0 = new Vector3f(c).add(new Vector3f(u).mul(-size)).add(new Vector3f(v).mul(-size));
            Vector3f p1 = new Vector3f(c).add(new Vector3f(u).mul(size)).add(new Vector3f(v).mul(-size));
            Vector3f p2 = new Vector3f(c).add(new Vector3f(u).mul(size)).add(new Vector3f(v).mul(size));
            Vector3f p3 = new Vector3f(c).add(new Vector3f(u).mul(-size)).add(new Vector3f(v).mul(size));
            int R = (int) (rr * 255), G = (int) (gg * 255), B = (int) (bbC * 255), A = (int) (alpha * 255);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            bb.addVertex(p0.x, p0.y, p0.z).setUv(0, 0).setColor(R, G, B, A);
            bb.addVertex(p1.x, p1.y, p1.z).setUv(1, 0).setColor(R, G, B, A);
            bb.addVertex(p2.x, p2.y, p2.z).setUv(1, 1).setColor(R, G, B, A);
            bb.addVertex(p0.x, p0.y, p0.z).setUv(0, 0).setColor(R, G, B, A);
            bb.addVertex(p2.x, p2.y, p2.z).setUv(1, 1).setColor(R, G, B, A);
            bb.addVertex(p3.x, p3.y, p3.z).setUv(0, 1).setColor(R, G, B, A);
            draw(bb, glowTexture(), GLOW);
        }
    }

    private static void draw(BufferBuilder bb, AbstractTexture tex, RenderPipeline pipeline) {
        MeshData mesh = bb.build();
        if (mesh == null) return;
        try (mesh) {
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                    .writeTransform(RenderSystem.getModelViewStack(), new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            GpuBuffer vb = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
            GpuBuffer ib = seq.getBuffer(mesh.drawState().indexCount());
            GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
            GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "Heliosphere sky", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transform);
                pass.bindTexture("Sampler0", tex.getTextureView(), tex.getSampler());
                pass.setVertexBuffer(0, vb);
                pass.setIndexBuffer(ib, seq.type());
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        }
    }

    private static DynamicTexture glowTexture() {
        if (glowTex == null) {
            int n = 64;
            NativeImage img = new NativeImage(n, n, false);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1, d = Math.sqrt(dx * dx + dy * dy);
                double a = Math.max(0, 1 - d);
                a = Math.pow(a, 2.2);
                int al = (int) (a * 255);
                img.setPixel(x, y, (al << 24) | 0xFFFFFF);
            }
            glowTex = new DynamicTexture(() -> "heliosphere glow", img);
            Minecraft.getInstance().getTextureManager().register(Heliosphere.id("dynamic/glow"), glowTex);
        }
        return glowTex;
    }

    private static DynamicTexture ringTexture() {
        if (ringTex == null) {
            int n = 128;
            NativeImage img = new NativeImage(n, 4, false);
            java.util.Random r = new java.util.Random(7);
            for (int x = 0; x < n; x++) {
                double f = x / (double) n;
                double a = 0.55 + 0.35 * Math.sin(f * 40 + r.nextDouble()) * Math.sin(f * 7);
                if (f > 0.62 && f < 0.68) a = 0.05; // Cassini division
                int al = (int) (Math.max(0, Math.min(1, a)) * 255);
                int col = (al << 24) | (0xD8 << 16) | (0xC8 << 8) | 0xA8;
                for (int y = 0; y < 4; y++) img.setPixel(x, y, col);
            }
            ringTex = new DynamicTexture(() -> "heliosphere rings", img);
            Minecraft.getInstance().getTextureManager().register(Heliosphere.id("dynamic/rings"), ringTex);
        }
        return ringTex;
    }
}
