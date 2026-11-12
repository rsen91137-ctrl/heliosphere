package com.heliosphere.disasters.giant;

import com.heliosphere.core.client.fx.FxEngine;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.fx.sim.FxAtlas;
import com.heliosphere.core.fx.sim.MushroomCloud;
import com.heliosphere.core.fx.sim.SpriteSink;
import com.heliosphere.core.storm.StormField;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The weather of the giant planets, redrawn: lightning is born inside the cloud decks (in Jupiter mostly in the
 * deep water clouds), lights the clouds around it from inside and rolls its thunder in after the light; the storm
 * eyes are great rotating cyclones with spiral arms and a dark eye.
 */
public final class GiantSkyClient implements ClientModInitializer {
    private static ClientLevel world;
    private static final RandomSource R = RandomSource.create();

    private record Thunder(long at, Vec3 pos, float volume) {}
    private static final List<Thunder> THUNDER = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(GiantSkyClient::tick);
    }

    /** Heights of the cloud decks where lightning is born. */
    static int[] decks(String planet) {
        return switch (planet) {
            case "jupiter" -> new int[]{-620, -120, 318};
            case "saturn" -> new int[]{-900, -260, 318};
            default -> new int[]{-1250, -700, -80, 318};
        };
    }

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        boolean giant = Planets.isGasGiant(level);
        if (level != world) {
            world = level;
            THUNDER.clear();
            if (giant) FxEngine.add(new Cyclones(level));
        }
        if (!giant) return;
        var body = Planets.bodyOf(level);
        String planet = body == null ? "jupiter" : body.id;
        StormField.Climate c = StormField.climate(level);
        long t = level.getGameTime();
        float activity = StormField.activity(c, t);
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        // lightning: several strikes a second somewhere on the visible decks when the storms are up
        float rate = (0.15f + 0.85f * activity) * c.lightning() / 60f;
        int strikes = R.nextFloat() < rate % 1 ? (int) rate + 1 : (int) rate;
        for (int i = 0; i < strikes; i++) strike(level, planet, c, cam, activity);
        for (Iterator<Thunder> it = THUNDER.iterator(); it.hasNext(); ) {
            Thunder th = it.next();
            if (t < th.at) continue;
            it.remove();
            level.playLocalSound(th.pos.x, th.pos.y, th.pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, th.volume, 0.55f + R.nextFloat() * 0.3f, false);
        }
    }

    private static void strike(ClientLevel level, String planet, StormField.Climate c, Vec3 cam, float activity) {
        int[] decks = decks(planet);
        // the deck nearest the viewer's height is the one that matters; others light up as distant glows
        int deck = decks[0];
        double best = Double.MAX_VALUE;
        for (int d : decks) { double dd = Math.abs(cam.y - d) + R.nextInt(200); if (dd < best) { best = dd; deck = d; } }
        double a = R.nextDouble() * Math.PI * 2, dist = 40 + Math.pow(R.nextDouble(), 0.7) * 520;
        Vec3 at = new Vec3(cam.x + Math.cos(a) * dist, deck + R.nextGaussian() * 10, cam.z + Math.sin(a) * dist);
        boolean bolt = R.nextFloat() < 0.55f;                 // the rest is sheet lightning inside the cloud
        float length = 40 + R.nextFloat() * (bolt ? 160 : 40);
        int rgb = c.bolt();
        FxEngine.add(new Lightning(at, length, bolt, rgb, R.nextLong()));
        double d = at.distanceTo(cam);
        if (d < 260) FxEngine.flash((float) (0.25 * (1 - d / 260)) * (bolt ? 1 : 0.5f), rgb, 0.25f);
        if (d < 700) THUNDER.add(new Thunder(level.getGameTime() + (long) (d / 343.0 * 20), at, (float) Math.max(0.3, 6 * (1 - d / 700))));
    }

    // ---- lightning --------------------------------------------------------------------------------------------

    /** One discharge: a few return strokes along a jagged channel with branches, lighting its cloud. */
    static final class Lightning implements FxEngine.Effect {
        private final Vec3 at;
        private final float length;
        private final boolean bolt;
        private final float r, g, b;
        private final long seed;
        private final double[][] path;
        private final List<double[][]> branches = new ArrayList<>();
        private final float[] strokes;

        Lightning(Vec3 at, float length, boolean bolt, int rgb, long seed) {
            this.at = at; this.length = length; this.bolt = bolt; this.seed = seed;
            r = (rgb >> 16 & 255) / 255f; g = (rgb >> 8 & 255) / 255f; b = (rgb & 255) / 255f;
            RandomSource rs = RandomSource.create(seed);
            path = channel(rs, at, new Vec3(rs.nextGaussian() * 0.5, -1, rs.nextGaussian() * 0.5).normalize(), length, 18);
            for (int i = 0; i < 4; i++) {
                double[] from = path[2 + rs.nextInt(path.length - 4)];
                Vec3 dir = new Vec3(rs.nextGaussian(), -0.6, rs.nextGaussian()).normalize();
                branches.add(channel(rs, new Vec3(from[0], from[1], from[2]), dir, length * (0.2f + rs.nextFloat() * 0.3f), 7));
            }
            int n = 1 + rs.nextInt(4);
            strokes = new float[n];
            for (int i = 0; i < n; i++) strokes[i] = i == 0 ? 0 : strokes[i - 1] + 0.05f + rs.nextFloat() * 0.12f;
        }

        private static double[][] channel(RandomSource rs, Vec3 start, Vec3 dir, double len, int n) {
            double[][] p = new double[n + 1][];
            Vec3 cur = start;
            double step = len / n;
            for (int i = 0; i <= n; i++) {
                p[i] = new double[]{cur.x, cur.y, cur.z};
                Vec3 jitter = new Vec3(rs.nextGaussian(), rs.nextGaussian() * 0.4, rs.nextGaussian()).scale(step * 0.55);
                cur = cur.add(dir.scale(step)).add(jitter);
            }
            return p;
        }

        @Override
        public boolean emit(SpriteSink sink, float age, MushroomCloud.Env env) {
            float end = strokes[strokes.length - 1] + 0.18f;
            if (age > end + 0.5f) return false;
            float on = 0;
            for (float s : strokes) { float d = age - s; if (d >= 0 && d < 0.16f) on = Math.max(on, 1 - d / 0.16f); }
            float after = Math.max(0, 1 - (age - end) / 0.5f) * 0.25f;          // the cloud keeps glowing a moment
            float k = Math.max(on, after);
            if (k <= 0.01f) return true;
            // the cloud lit from inside
            double cy = path[path.length / 3][1];
            sink.billboard(at.x, cy, at.z, length * 1.6f, 0, FxAtlas.GLOW, 0, 0, 0, 0, r * k * 0.9f, g * k * 0.9f, b * k);
            sink.billboard(at.x, cy, at.z, length * 0.8f, 0, FxAtlas.GLOW, 0, 0, 0, 0, k, k, k);
            if (!bolt || on <= 0) return true;
            draw(sink, path, length * 0.035f, on);
            for (double[][] br : branches) draw(sink, br, length * 0.02f, on * 0.7f);
            return true;
        }

        private void draw(SpriteSink sink, double[][] p, float width, float k) {
            for (int i = 0; i + 1 < p.length; i++) {
                double[] a = p[i], c = p[i + 1];
                sink.ribbon(a[0], a[1], a[2], c[0], c[1], c[2], width * 5, FxAtlas.BEAM, 0, 0, 0, 0, r * k * 0.6f, g * k * 0.6f, b * k * 0.8f);
                sink.ribbon(a[0], a[1], a[2], c[0], c[1], c[2], width, FxAtlas.BEAM, 0, 0, 0, 0, k, k, k);
            }
        }
    }

    // ---- cyclones ---------------------------------------------------------------------------------------------

    /** The storm eye nearest the viewer drawn as a rotating cyclone: spiral arms of cloud around a dark eye. */
    static final class Cyclones implements FxEngine.Effect {
        private final ClientLevel level;

        Cyclones(ClientLevel level) { this.level = level; }

        @Override
        public boolean emit(SpriteSink sink, float age, MushroomCloud.Env env) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != level) return false;
            StormField.Climate c = StormField.climate(level);
            Vec3 cam = mc.gameRenderer.getMainCamera().position();
            StormField.Eye eye = StormField.nearest(c, cam.x, cam.z, level.getGameTime());
            if (eye == null) return true;
            float activity = StormField.activity(c, level.getGameTime());
            double rad = Math.max(60, eye.radius());
            if (eye.distance() > rad * 6) return true;
            int mist = c.mist();
            float mr = (mist >> 16 & 255) / 255f, mg = (mist >> 8 & 255) / 255f, mb = (mist & 255) / 255f;
            double spin = eye.spin() + age * 0.04 * (0.5 + activity);
            double y = 322;
            int arms = 3;
            for (int arm = 0; arm < arms; arm++) {
                for (int i = 0; i < 46; i++) {
                    double f = i / 45.0;
                    double r = rad * (0.18 + 1.5 * f);
                    double ang = spin + arm * Math.PI * 2 / arms + Math.log(1 + 4 * f) * 2.3;
                    double x = eye.x() + Math.cos(ang) * r, z = eye.z() + Math.sin(ang) * r;
                    float size = (float) (rad * (0.22 + 0.25 * f));
                    float shade = (float) (0.55 + 0.45 * f);
                    float alpha = (float) (0.55 * (1 - f * 0.6));
                    sink.billboard(x, y + Math.sin(i * 1.7 + arm) * 6, z, size, (float) ang, FxAtlas.SMOKE + (i + arm) % FxAtlas.SMOKE_COUNT,
                            mr * shade * alpha, mg * shade * alpha, mb * shade * alpha, alpha, 0, 0, 0);
                }
            }
            // eye wall and the dark eye
            for (int i = 0; i < 24; i++) {
                double ang = spin * 1.6 + i * Math.PI * 2 / 24;
                double r = rad * 0.2;
                sink.billboard(eye.x() + Math.cos(ang) * r, y + 10, eye.z() + Math.sin(ang) * r, (float) (rad * 0.16), (float) ang,
                        FxAtlas.SMOKE + i % FxAtlas.SMOKE_COUNT, mr * 0.5f * 0.7f, mg * 0.5f * 0.7f, mb * 0.5f * 0.7f, 0.7f, 0, 0, 0);
            }
            sink.ground(eye.x(), y - 30, eye.z(), (float) (rad * 0.35), (float) spin, FxAtlas.GLOW, 0, 0, 0, 0.6f, 0, 0, 0);
            return true;
        }
    }
}
