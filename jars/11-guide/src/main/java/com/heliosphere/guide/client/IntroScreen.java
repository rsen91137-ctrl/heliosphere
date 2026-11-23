package com.heliosphere.guide.client;

import com.heliosphere.guide.Guide;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * The opening cinematic shown on first join: a 60-second "emergency broadcast" that tells the story of
 * Heliosphere (asteroid 2097-ΩX, a year of escalating disasters, evolving machines) and how the pack plays
 * (mine, build industry and defences, fly to other worlds). It is composed in real time: parallax layers from
 * {@code textures/gui/intro2/}, eased camera moves, procedural effects (HUD, radar, tracers, explosions, smoke,
 * shockwaves), typewriter captions, cut flashes, glitches, scanlines and a pulsing red vignette, with alarm
 * beeps from vanilla sound events over the {@code heliosphere:intro_theme} score.
 *
 * <p>Coordinates: scenes are composed on a 640x360 design canvas centred on the screen ("du", design units,
 * {@code U} GUI pixels each), so the composition keeps its proportions at every window size and GUI scale;
 * backgrounds extend to the visible edges ({@code VX}, {@code VY}). "World" space is the design canvas seen
 * through the scene camera (zoom, pan, roll); "HUD" space is the plain canvas; text is drawn in screen space
 * at pixel-exact scales so it stays crisp.
 */
public final class IntroScreen extends Screen {

    // =========================================================================================== timeline

    private static final String KEY = "intro.heliosphere.v2.";
    private static final float DURATION = 60f;
    /** Skip keys are ignored this long after opening, so a held jump key does not skip by accident. */
    private static final float SKIP_GRACE = 0.6f;
    /** From here on (the title card) any key or click closes the screen. */
    private static final float ANY_KEY_FROM = 53.6f;
    private static final float[] SCENE_START = {0f, 4f, 12f, 20f, 28f, 36f, 45f, 52f};
    private static final float ORBIT_CUT = 40.5f;
    private static final float IMPACT = 47.2f;
    private static final float[] CUTS = {4f, 12f, 20f, 28f, 36f, ORBIT_CUT, 45f, 52f};
    private static final int[] CUT_COLOR = {0xFFFFFF, 0xFF2A1E, 0xFF2A1E, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFF2A1E, 0xFFFFFF};
    /** Strength of the red vignette pulse per scene. */
    private static final float[] URGENCY = {0.75f, 0.5f, 0.6f, 0.65f, 0.5f, 0.2f, 1f, 0.35f};
    /** Chance of a random glitch in each 0.45 s slot per scene. */
    private static final float[] GLITCH_RATE = {0.65f, 0.25f, 0.3f, 0.35f, 0.2f, 0.08f, 0.55f, 0.12f};

    private static final String[] SCENE_IDS = {"broadcast", "target", "forecast", "machines", "defence", "stars", "impact"};
    private static final String[] SCENE_FALLBACK = {"紧急广播", "目标锁定", "灾害预报", "机械进化", "守住基地", "飞向群星", "撞击"};
    private static final String[] SCENE_INDEX = {"01", "02", "03", "04", "05", "06", "07"};

    private static final String[] CAP_IDS = {"asteroid", "disasters", "machines", "defence", "stars", "impact"};
    private static final String[] CAP_FALLBACK = {
            "公元 2097 年。小行星 2097-ΩX 正冲向地球——撞击概率 99.7%。",
            "365 天后地球将不复存在。在那之前，酸雨、地震、冰封、洪水和山火会接连到来。",
            "更糟的是，失控的机械正在进化，一波又一波围攻你的避难所，越到后期越凶。",
            "开采矿石，建起工厂与电网，造防御塔、防空车和武器，守住基地。",
            "造出火箭，在月球、火星和更远的星球建立栖息地——那里是人类最后的家园。",
            "倒计时归零，留在地球上的一切都将化为火海。"};
    private static final float[] CAP_START = {4.7f, 12.6f, 20.6f, 28.6f, 36.9f, 48.1f};
    private static final float[] CAP_END = {11.75f, 19.75f, 27.75f, 35.75f, 44.75f, 51.85f};

    private static final String[] CHIP_IDS = {"mine", "smelt", "power", "factory", "turret", "airdef"};
    private static final String[] CHIP_FALLBACK = {"采矿", "冶炼", "电网", "工厂", "防御塔", "防空车"};
    private static final String[] ROW_IDS = {"target", "diameter", "velocity", "probability"};
    private static final String[] ROW_FALLBACK = {"目标", "直径", "速度", "撞击概率"};
    private static final String[] ROW_VALUE = {"2097-ΩX", "14 km", "31 km/s", ""};

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final int WHITE = 0xFFFFFFFF;

    // =========================================================================================== art

    /** The art contract: textures/gui/intro2/*.png with their pixel sizes and how they are sampled. */
    private enum Art {
        STARS("stars", 1024, 512, true, false, true),
        NEBULA("nebula", 1024, 512, true, false, true),
        EARTH("earth", 512, 512),
        EARTH_BURNING("earth_burning", 512, 512),
        ASTEROID("asteroid", 256, 256),
        COMA("coma", 512, 128),
        MOON("moon", 256, 256),
        MARS("mars", 256, 256),
        ROBOTS("robots", 1024, 256),
        BASE("base", 1024, 256),
        ROCKET("rocket", 128, 256),
        FLAME("flame", 128, 128),
        SHOCKWAVE("shockwave", 256, 256),
        GLOW("glow", 128, 128),
        NOISE("noise", 256, 256, true, true, false),
        LOGO("logo", 1024, 256);

        final Identifier id;
        final int w, h;
        final boolean repeatU, repeatV, linear;

        Art(String name, int w, int h) {
            this(name, w, h, false, false, true);
        }

        Art(String name, int w, int h, boolean repeatU, boolean repeatV, boolean linear) {
            this.id = Identifier.fromNamespaceAndPath("heliosphere", "textures/gui/intro2/" + name + ".png");
            this.w = w;
            this.h = h;
            this.repeatU = repeatU;
            this.repeatV = repeatV;
            this.linear = linear;
        }
    }

    private static final Art[] ARTS = Art.values();
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("heliosphere", "intro2/generated/vignette");
    private static final Identifier SCANLINES = Identifier.fromNamespaceAndPath("heliosphere", "intro2/generated/scanlines");

    private static final RenderPipeline PLAIN = RenderPipelines.GUI_TEXTURED;
    /** Textured quads blended additively (src alpha, one): glows, flames, tracers, shockwaves. */
    private static final RenderPipeline ADD_TEX = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("heliosphere", "pipeline/intro_additive_textured"))
            .withBlend(BlendFunction.LIGHTNING)
            .build());
    /** Untextured quads blended additively. */
    private static final RenderPipeline ADD_FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("heliosphere", "pipeline/intro_additive"))
            .withBlend(BlendFunction.LIGHTNING)
            .build());

    // =========================================================================================== sound cues

    private static final int S_BIT = 0, S_PLING = 1, S_BASS = 2, S_DRUM = 3, S_HAT = 4, S_SNARE = 5, S_CHIME = 6,
            S_EXPLODE = 7, S_THUNDER = 8, S_LAUNCH = 9, S_BLAST = 10, S_BLAST_FAR = 11, S_CLICK = 12, S_BELL = 13;

    /** {time, sound, pitch, volume}, sorted by time. */
    private static final float[][] CUES;

    // =========================================================================================== procedural tables

    private static long seed = 0x2097_0365L;
    private static final int WARP = 40, HOT = 34, BLIPS = 44, MDRONES = 8, SMOKE = 6, DN = 10, FLAK = 14, PUFFS = 24, DEBRIS = 16, FSTARS = 90;
    private static final float[] WS_A = new float[WARP], WS_Z = new float[WARP], WS_V = new float[WARP];
    private static final float[] HOT_X = new float[HOT], HOT_Y = new float[HOT], HOT_D = new float[HOT], HOT_S = new float[HOT];
    private static final float[] BL_A = new float[BLIPS], BL_R = new float[BLIPS], BL_T = new float[BLIPS];
    private static final float[] MD_Y = new float[MDRONES], MD_V = new float[MDRONES], MD_DIR = new float[MDRONES], MD_PH = new float[MDRONES], MD_S = new float[MDRONES];
    private static final float[] SM_X = new float[SMOKE], SM_Y = new float[SMOKE], SM_R = new float[SMOKE], SM_V = new float[SMOKE];
    private static final float[] DR_S = new float[DN], DR_SIDE = new float[DN], DR_FY = new float[DN], DR_TX = new float[DN], DR_TY = new float[DN], DR_DUR = new float[DN], DR_KILL = new float[DN];
    private static final float[] FL_T = new float[FLAK], FL_X = new float[FLAK], FL_Y = new float[FLAK];
    private static final float[] PF_E = new float[PUFFS], PF_DIR = new float[PUFFS], PF_V = new float[PUFFS], PF_S = new float[PUFFS];
    private static final float[] DB_A = new float[DEBRIS], DB_V = new float[DEBRIS];
    private static final float[] FS_X = new float[FSTARS], FS_Y = new float[FSTARS], FS_B = new float[FSTARS];
    private static final float JET_T0 = 2.3f, JET_SPEED = 230f, MISSILE_T0 = 3.05f, JET_KILL = 4.45f;
    /** Where earth_burning.png has its crater, as a fraction of the sprite's half size from its centre. */
    private static final float CRATER_X = -0.008f, CRATER_Y = -0.148f;
    /** The base's own guns and its missile launcher, as fractions of base.png. */
    private static final float[] GUN_FX = {0.098f, 0.635f, 0.908f}, GUN_FY = {0.49f, 0.45f, 0.48f};
    private static final float SAM_FX = 0.835f, SAM_FY = 0.58f;

    static {
        for (int i = 0; i < WARP; i++) {
            WS_A[i] = rnd() * TAU;
            WS_Z[i] = rnd();
            WS_V[i] = 0.12f + rnd() * 0.14f;
        }
        for (int i = 0; i < HOT; i++) {
            float x, y;
            do {
                x = rnd() * 2f - 1f;
                y = rnd() * 2f - 1f;
            } while (x * x + y * y > 0.66f);
            HOT_X[i] = x;
            HOT_Y[i] = y;
            HOT_D[i] = 268f - i * 7.6f - rnd() * 4f;
            HOT_S[i] = rnd();
        }
        for (int i = 0; i < BLIPS; i++) {
            BL_A[i] = rnd() * TAU;
            BL_R[i] = 0.25f + rnd() * 0.7f;
            BL_T[i] = 0.3f + (float) Math.pow(i / (float) BLIPS, 1.25) * 7.2f;
        }
        for (int i = 0; i < MDRONES; i++) {
            MD_Y[i] = -158f + rnd() * 118f;
            MD_V[i] = 45f + rnd() * 70f;
            MD_DIR[i] = rnd() < 0.5f ? -1f : 1f;
            MD_PH[i] = rnd();
            MD_S[i] = 0.6f + rnd() * 0.5f;
        }
        for (int i = 0; i < SMOKE; i++) {
            SM_X[i] = rnd() * 2f - 1f;
            SM_Y[i] = -40f + rnd() * 45f;
            SM_R[i] = 70f + rnd() * 50f;
            SM_V[i] = 3f + rnd() * 5f;
        }
        for (int i = 0; i < DN; i++) {
            DR_S[i] = 0.1f + i * 0.62f + rnd() * 0.2f;
            DR_SIDE[i] = rnd() < 0.5f ? -1f : 1f;
            DR_FY[i] = -152f + rnd() * 70f;
            DR_TX[i] = -170f + rnd() * 340f;
            DR_TY[i] = -32f + rnd() * 40f;
            DR_DUR[i] = 2.3f + rnd() * 0.8f;
            DR_KILL[i] = DR_S[i] + DR_DUR[i] * (0.56f + rnd() * 0.24f);
        }
        for (int i = 0; i < FLAK; i++) {
            FL_T[i] = 0.5f + i * 0.52f + rnd() * 0.2f;
            FL_X[i] = (rnd() * 2f - 1f) * 0.8f;
            FL_Y[i] = -132f + rnd() * 78f;
        }
        for (int i = 0; i < PUFFS; i++) {
            PF_E[i] = 1.25f + rnd() * 2.0f;
            PF_DIR[i] = (i & 1) == 0 ? -1f : 1f;
            PF_V[i] = 50f + rnd() * 110f;
            PF_S[i] = 0.8f + rnd() * 0.6f;
        }
        for (int i = 0; i < DEBRIS; i++) {
            DB_A[i] = (rnd() - 0.5f) * 2.4f;
            DB_V[i] = 60f + rnd() * 130f;
        }
        for (int i = 0; i < FSTARS; i++) {
            FS_X[i] = rnd() * 2f - 1f;
            FS_Y[i] = rnd() * 2f - 1f;
            FS_B[i] = rnd();
        }

        List<float[]> c = new ArrayList<>();
        // 0-4 cold open: power-on click, two-tone alert, decoding ticks, the countdown settling
        cue(c, 0.08f, S_CLICK, 0.6f, 0.25f);
        cue(c, 0.55f, S_BASS, 0.6f, 0.5f);
        float[] alert = {0.55f, 0.85f, 1.15f, 1.45f, 2.75f, 3.05f, 3.35f, 3.65f};
        for (int i = 0; i < alert.length; i++) cue(c, alert[i], S_BIT, (i & 1) == 0 ? 1.5f : 1.12f, 0.3f);
        for (float t = 1.2f; t < 2.15f; t += 0.1f) cue(c, t, S_HAT, 1.6f, 0.12f);
        cue(c, 2.2f, S_PLING, 1.0f, 0.45f);
        cue(c, 2.2f, S_BASS, 0.5f, 0.4f);
        cue(c, 4.0f, S_BASS, 0.7f, 0.35f);
        // 4-12 target lock, readouts, probability alarm
        float[] acq = {0.9f, 1.0f, 1.12f, 1.26f};
        for (int i = 0; i < 4; i++) cue(c, 5.0f + i * 0.3f, S_PLING, acq[i], 0.22f);
        cue(c, 6.2f, S_PLING, 1.5f, 0.35f);
        cue(c, 6.32f, S_PLING, 2.0f, 0.25f);
        for (int i = 0; i < 4; i++) cue(c, 6.75f + i * 0.35f, S_BIT, 1.9f, 0.12f);
        for (int i = 0; i < 4; i++) cue(c, 8.25f + i * 0.25f, S_BIT, (i & 1) == 0 ? 0.9f : 0.7f, 0.33f);
        cue(c, 12.0f, S_BASS, 0.6f, 0.35f);
        // 12-20 the day counter falls: ticks, a stamp at day 270, a last tone at day 1
        for (float t = 12.6f; t < 14.4f; t += 0.12f) cue(c, t, S_HAT, 1.2f + 0.6f * (t - 12.6f) / 1.8f, 0.1f);
        cue(c, 14.4f, S_BASS, 0.5f, 0.5f);
        cue(c, 14.4f, S_BIT, 0.8f, 0.35f);
        cue(c, 14.65f, S_BIT, 0.8f, 0.35f);
        for (float t = 15.3f; t < 19.2f; t += 0.11f) cue(c, t, S_HAT, 1.0f + (t - 15.3f) / 3.9f, 0.1f);
        cue(c, 19.2f, S_PLING, 0.6f, 0.4f);
        cue(c, 19.2f, S_BASS, 0.5f, 0.4f);
        cue(c, 20.0f, S_BASS, 0.5f, 0.4f);
        // 20-28 the machines march; the radar pings once per sweep
        for (float t = 20.3f; t < 27.8f; t += 0.6f) cue(c, t, S_DRUM, 0.55f, 0.32f);
        for (float t = 20.4f; t < 28f; t += 2f) cue(c, t, S_BIT, 0.55f, 0.12f);
        cue(c, 28.0f, S_BASS, 0.7f, 0.3f);
        // 28-36 the base fights back
        for (int i = 0; i < DN; i++) {
            float k = SCENE_START[4] + DR_KILL[i];
            if (k > 35.9f) continue;
            for (float t = k - 1.2f; t < k; t += 0.26f) cue(c, t, S_SNARE, 1.6f + 0.3f * hash(i * 31 + (int) (t * 10f)), 0.07f);
            cue(c, k, S_BLAST, 1.0f, 0.25f);
        }
        for (int i = 0; i < FLAK; i++) cue(c, SCENE_START[4] + FL_T[i], S_BLAST_FAR, 0.8f, 0.2f);
        cue(c, SCENE_START[4] + MISSILE_T0, S_LAUNCH, 0.7f, 0.4f);
        cue(c, SCENE_START[4] + JET_KILL, S_EXPLODE, 1.1f, 0.35f);
        // 36-45 launch countdown, ignition, liftoff, the habitat light
        cue(c, 36.0f, S_PLING, 1.0f, 0.3f);
        cue(c, 36.4f, S_PLING, 1.0f, 0.3f);
        cue(c, 36.8f, S_PLING, 1.0f, 0.3f);
        cue(c, 37.2f, S_LAUNCH, 0.5f, 0.55f);
        cue(c, 37.2f, S_EXPLODE, 0.5f, 0.18f);
        cue(c, 37.6f, S_LAUNCH, 0.6f, 0.4f);
        cue(c, ORBIT_CUT, S_CHIME, 0.8f, 0.25f);
        cue(c, 42.2f, S_CHIME, 1.4f, 0.3f);
        cue(c, 42.6f, S_CHIME, 1.9f, 0.2f);
        // 45-52 accelerating alarm, then the impact
        cue(c, 45.0f, S_BASS, 0.5f, 0.45f);
        float at = 45.1f;
        for (int i = 0; at < IMPACT - 0.04f; i++) {
            cue(c, at, S_BIT, (i & 1) == 0 ? 1.6f : 1.2f, 0.3f);
            at += Math.max(0.08f, 0.3f - (at - 45.1f) * 0.11f);
        }
        cue(c, IMPACT, S_EXPLODE, 0.5f, 0.7f);
        cue(c, IMPACT, S_THUNDER, 0.5f, 0.45f);
        cue(c, IMPACT + 0.5f, S_EXPLODE, 0.6f, 0.35f);
        cue(c, IMPACT + 1.4f, S_THUNDER, 0.6f, 0.25f);
        // 52-60 the title: a deep hit, then the countdown line pulses
        cue(c, 52.25f, S_BASS, 0.5f, 0.6f);
        cue(c, 52.25f, S_BELL, 0.5f, 0.4f);
        for (float t = 55.3f; t < 59f; t += 1f) cue(c, t, S_PLING, 0.75f, 0.15f);
        c.sort((x, y) -> Float.compare(x[0], y[0]));
        CUES = c.toArray(new float[0][]);
    }

    private static void cue(List<float[]> list, float t, int sound, float pitch, float volume) {
        list.add(new float[]{t, sound, pitch, volume});
    }

    private static float rnd() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return ((seed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    // =========================================================================================== state

    private IntroMusic music;
    private long lastMillis = -1L;
    private float time;
    private int nextCue;
    private boolean closing;
    private final boolean[] present = new boolean[ARTS.length];
    private boolean texturesLoaded;

    // localized text, resolved in init()
    private Component bannerMain = Component.empty();
    private int bannerMainW;
    private String bannerSub = "", live = "", notDrill = "", countdownLabel = "", daysUnit = "", hudAnalysis = "", hudAcquiring = "",
            hudLocked = "", hudEarth = "", forecastTag = "", tickerLoop = "", forecastImpact = "", forecastBegin = "", radarLabel = "",
            radarHostiles = "", evolutionLabel = "", ignition = "", liftoff = "", habitat = "", habitatMars = "", impactAlert = "",
            titleSub = "", tagline = "", titleCountdown = "", skipText = "", anyKey = "";
    private final String[] sceneLabel = new String[SCENE_IDS.length];
    private final String[] chips = new String[CHIP_IDS.length];
    private final String[] rowLabel = new String[ROW_IDS.length];

    // captions, wrapped in init()
    private final String[][] capLines = new String[CAP_IDS.length][];
    private final int[][] capLineW = new int[CAP_IDS.length][];
    private final int[] capChars = new int[CAP_IDS.length];
    private final int[] capMaxW = new int[CAP_IDS.length];
    private float capK = 1f;

    // small string caches so the per-frame text does not allocate every frame
    private int cacheDay = -1, cacheStage = -1, cacheHostiles = -1, cacheProb = -1, cacheSec = -1, cacheRandom = -1, cacheCount = -1;
    private String dayStr = "", stageStr = "", hostilesStr = "", probStr = "", timecodeStr = "", randomStr = "", countStr = "";

    // per frame
    private GuiGraphics g;
    private float W, H, CX, CY, U = 1f, VX = 320f, VY = 180f, gs = 2f;
    private float camZoom = 1f, camPanX, camPanY, camRoll, cosR = 1f, sinR;
    private float shX, shY;
    private int frame;
    private boolean hud;

    public IntroScreen() {
        super(Component.literal("Heliosphere"));
    }

    /** Seconds since the cinematic started (for tests that screenshot each scene). */
    public float elapsedSeconds() {
        return time;
    }

    /** Jumps to a moment of the cinematic (for tests); cues before it are not played. */
    public void seek(float seconds) {
        time = Math.max(0f, Math.min(DURATION, seconds));
        lastMillis = Util.getMillis();
        nextCue = 0;
        while (nextCue < CUES.length && CUES[nextCue][0] < time) nextCue++;
    }

    // =========================================================================================== lifecycle

    @Override
    protected void init() {
        if (!texturesLoaded) loadTextures();
        gs = Math.max(1, minecraft.getWindow().getGuiScale());
        loadStrings();
        layoutCaptions();
        if (music == null) {
            minecraft.getMusicManager().stopPlaying();
            music = new IntroMusic();
            minecraft.getSoundManager().play(music);
        }
        if (lastMillis < 0L) lastMillis = Util.getMillis();
    }

    @Override
    public void removed() {
        if (music != null) {
            music.fadeOut();
            music = null;
        }
        releaseTextures();
        super.removed();
    }

    @Override
    public void tick() {
        update();
        if (time >= DURATION) onClose();
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean function = key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25;
        if (time >= ANY_KEY_FROM && !function) {
            onClose();
            return true;
        }
        if (event.isEscape() || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (time >= SKIP_GRACE) onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (time >= ANY_KEY_FROM) onClose();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        minecraft.gui.renderDeferredSubtitles();
    }

    /** Advances the clock (real time; a long stall such as world loading counts as a quarter second) and plays due cues. */
    private void update() {
        long now = Util.getMillis();
        if (lastMillis < 0L) lastMillis = now;
        long dt = now - lastMillis;
        lastMillis = now;
        if (dt < 0L) dt = 0L;
        if (dt > 1000L) dt = 250L;
        time = Math.min(DURATION, time + dt / 1000f);
        while (nextCue < CUES.length && CUES[nextCue][0] <= time) {
            float[] c = CUES[nextCue++];
            if (time - c[0] > 0.4f) continue;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundOf((int) c[1]), c[2], c[3]));
        }
    }

    private static SoundEvent soundOf(int sound) {
        return switch (sound) {
            case S_PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
            case S_BASS -> SoundEvents.NOTE_BLOCK_BASS.value();
            case S_DRUM -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case S_HAT -> SoundEvents.NOTE_BLOCK_HAT.value();
            case S_SNARE -> SoundEvents.NOTE_BLOCK_SNARE.value();
            case S_CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case S_EXPLODE -> SoundEvents.GENERIC_EXPLODE.value();
            case S_THUNDER -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case S_LAUNCH -> SoundEvents.FIREWORK_ROCKET_LAUNCH;
            case S_BLAST -> SoundEvents.FIREWORK_ROCKET_BLAST;
            case S_BLAST_FAR -> SoundEvents.FIREWORK_ROCKET_BLAST_FAR;
            case S_CLICK -> SoundEvents.UI_BUTTON_CLICK.value();
            case S_BELL -> SoundEvents.BELL_BLOCK;
            default -> SoundEvents.NOTE_BLOCK_BIT.value();
        };
    }

    // =========================================================================================== text and layout

    private static String tr(String id, String fallback) {
        return Component.translatableWithFallback(KEY + id, fallback).getString();
    }

    private void loadStrings() {
        String banner = tr("banner", "联合航天署 · 紧急广播");
        bannerMain = Component.literal(banner).withStyle(ChatFormatting.BOLD);
        bannerMainW = font.width(bannerMain);
        bannerSub = tr("banner_sub", "UNITED SPACE AGENCY · EMERGENCY BROADCAST");
        live = tr("live", "直播");
        notDrill = tr("notdrill", "这不是演习");
        countdownLabel = tr("countdown", "撞击倒计时");
        daysUnit = tr("days", "天");
        for (int i = 0; i < SCENE_IDS.length; i++) sceneLabel[i] = tr("scene." + SCENE_IDS[i], SCENE_FALLBACK[i]);
        for (int i = 0; i < CHIP_IDS.length; i++) chips[i] = tr("chip." + CHIP_IDS[i], CHIP_FALLBACK[i]);
        for (int i = 0; i < ROW_IDS.length; i++) rowLabel[i] = tr("hud." + ROW_IDS[i], ROW_FALLBACK[i]);
        hudAnalysis = tr("hud.analysis", "目标分析");
        hudAcquiring = tr("hud.acquiring", "捕获目标…");
        hudLocked = tr("hud.locked", "已锁定");
        hudEarth = tr("hud.earth", "地球");
        forecastTag = tr("forecast.tag", "灾害预警");
        tickerLoop = tr("forecast.ticker", "酸雨 · 雷暴 · 地震 · 冰封 · 洪水 · 山火") + "   ·   ";
        forecastImpact = tr("forecast.impact", "预计撞击点");
        forecastBegin = tr("forecast.begin", "灾害开始");
        radarLabel = tr("radar", "雷达");
        radarHostiles = tr("radar.hostiles", "敌对目标");
        evolutionLabel = tr("evolution", "进化阶段");
        ignition = tr("launch.ignition", "点火");
        liftoff = tr("launch.liftoff", "起飞");
        habitat = tr("habitat", "月面栖息地");
        habitatMars = tr("habitat.mars", "火星");
        impactAlert = tr("impact.alert", "撞击警报");
        titleSub = tr("title.sub", "日球层");
        tagline = tr("title.tagline", "工程师，倒计时已经开始。");
        titleCountdown = tr("title.countdown", "撞击倒计时：365 天");
        skipText = tr("skip", "跳过");
        anyKey = tr("anykey", "按任意键继续");
        cacheDay = cacheStage = cacheHostiles = cacheProb = cacheSec = cacheRandom = cacheCount = -1;
    }

    /** Wraps the captions for the current width; called again on every resize. */
    private void layoutCaptions() {
        capK = crispK(clamp(height / 300f, 1f, 4f));
        float maxW = Math.min(width - 2f * margin() - 16f, width * 0.72f) / capK;
        int wrap = Math.max(60, (int) maxW);
        for (int i = 0; i < CAP_IDS.length; i++) {
            String s = tr("cap." + CAP_IDS[i], CAP_FALLBACK[i]);
            List<String> lines = new ArrayList<>();
            for (String para : s.split("\n")) wrap(para, wrap, lines);
            capLines[i] = lines.toArray(new String[0]);
            capLineW[i] = new int[capLines[i].length];
            int max = 0, chars = 0;
            for (int j = 0; j < capLines[i].length; j++) {
                capLineW[i][j] = font.width(capLines[i][j]);
                max = Math.max(max, capLineW[i][j]);
                chars += capLines[i][j].codePointCount(0, capLines[i][j].length());
            }
            capMaxW[i] = max;
            capChars[i] = chars;
        }
    }

    /**
     * Balanced line wrapping that also works for Chinese: lines may break at spaces or between CJK characters,
     * never before closing punctuation or after opening punctuation, and the lines come out about equally long.
     */
    private void wrap(String s, int maxW, List<String> out) {
        s = s.strip();
        if (s.isEmpty()) return;
        int total = font.width(s);
        int count = Math.max(1, (total + maxW - 1) / maxW);
        // aim for equal lines; widen the aim when word boundaries would spill onto an extra line
        List<String> lines = new ArrayList<>();
        for (int target = Math.min(maxW, total / count + 6); ; target = Math.min(maxW, target + Math.max(4, target / 12))) {
            lines.clear();
            wrapAt(s, target, lines);
            if (lines.size() <= count || target >= maxW) break;
        }
        out.addAll(lines);
    }

    private void wrapAt(String s, int target, List<String> out) {
        int start = 0;
        while (start < s.length()) {
            int w = 0, i = start, brk = -1, end = s.length();
            while (i < s.length()) {
                int cp = s.codePointAt(i);
                if (i > start && canBreakBefore(s, i)) brk = i;
                w += font.width(s.substring(i, i + Character.charCount(cp)));
                if (w > target && i > start) {
                    end = brk > start ? brk : i;
                    break;
                }
                i += Character.charCount(cp);
            }
            String line = s.substring(start, end).strip();
            if (!line.isEmpty()) out.add(line);
            start = end;
            while (start < s.length() && s.charAt(start) == ' ') start++;
        }
    }

    private static boolean canBreakBefore(String s, int i) {
        int prev = s.codePointBefore(i), cp = s.codePointAt(i);
        if (cp == ' ') return false;
        if (prev == ' ') return true;
        if ("。，、；：？！）」』〉》】”’…—%％·,.;:!?)".indexOf(cp) >= 0) return false;
        if ("（「『〈《【“‘(".indexOf(prev) >= 0) return false;
        return isCjk(prev) || isCjk(cp);
    }

    private static boolean isCjk(int cp) {
        return cp >= 0x2E80 && cp <= 0x9FFF || cp >= 0xF900 && cp <= 0xFAFF || cp >= 0xFF00 && cp <= 0xFFEF;
    }

    private float margin() {
        return clamp(Math.min(width, height) * 0.035f, 6f, 22f);
    }

    /** The closest text scale (GUI px per font px) that maps font pixels onto whole screen pixels. */
    private float crispK(float k) {
        return Math.max(1, Math.round(k * gs)) / gs;
    }

    private float crispDown(float k) {
        return Math.max(1, (int) Math.floor(k * gs + 0.001f)) / gs;
    }

    private float snap(float v) {
        return Math.round(v * gs) / gs;
    }

    // =========================================================================================== textures

    private void loadTextures() {
        TextureManager tm = minecraft.getTextureManager();
        for (Art a : ARTS) {
            boolean ok = minecraft.getResourceManager().getResource(a.id).isPresent();
            if (ok) {
                try {
                    tm.registerAndLoad(a.id, new ArtTexture(a));
                } catch (RuntimeException e) {
                    ok = false;
                }
            }
            present[a.ordinal()] = ok;
        }
        tm.register(VIGNETTE, makeVignette());
        tm.register(SCANLINES, makeScanlines());
        texturesLoaded = true;
    }

    private void releaseTextures() {
        if (!texturesLoaded) return;
        TextureManager tm = minecraft.getTextureManager();
        for (Art a : ARTS) if (present[a.ordinal()]) tm.release(a.id);
        tm.release(VIGNETTE);
        tm.release(SCANLINES);
        texturesLoaded = false;
    }

    /** A packaged texture with the sampler the cinematic needs (smooth scaling; tiling only where the art tiles). */
    private static final class ArtTexture extends SimpleTexture {
        private final Art art;

        ArtTexture(Art art) {
            super(art.id);
            this.art = art;
        }

        @Override
        public void apply(TextureContents contents) {
            super.apply(contents);
            FilterMode f = art.linear ? FilterMode.LINEAR : FilterMode.NEAREST;
            this.sampler = RenderSystem.getSamplerCache().getSampler(art.repeatU ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE,
                    art.repeatV ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE, f, f, false);
        }
    }

    private static final class GenTexture extends DynamicTexture {
        GenTexture(String label, NativeImage image, boolean repeat, boolean linear) {
            super(() -> label, image);
            FilterMode f = linear ? FilterMode.LINEAR : FilterMode.NEAREST;
            AddressMode m = repeat ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
            this.sampler = RenderSystem.getSamplerCache().getSampler(m, m, f, f, false);
        }
    }

    /** White with alpha rising from the centre to the corners; tinted black or red when drawn. */
    private static GenTexture makeVignette() {
        int n = 128;
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                float r = (float) Math.sqrt(dx * dx + dy * dy) / 1.41421f;
                float a = smooth(range(r, 0.36f, 1.0f));
                img.setPixelABGR(x, y, Math.round(a * 255f) << 24 | 0xFFFFFF);
            }
        }
        return new GenTexture("heliosphere intro vignette", img, false, true);
    }

    /** One texel per screen pixel: two clear rows, a dark row, a half-dark row. */
    private static GenTexture makeScanlines() {
        NativeImage img = new NativeImage(1, 4, false);
        int[] alpha = {0, 0, 150, 60};
        for (int y = 0; y < 4; y++) img.setPixelABGR(0, y, alpha[y] << 24);
        return new GenTexture("heliosphere intro scanlines", img, true, false);
    }

    /** The score, with a short fade when the cinematic is skipped. */
    private static final class IntroMusic extends AbstractTickableSoundInstance {
        private boolean fading;

        IntroMusic() {
            super(Guide.INTRO_THEME, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = false;
            this.delay = 0;
            this.volume = 1f;
            this.pitch = 1f;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public void tick() {
            if (!fading) return;
            volume -= 0.12f;
            if (volume <= 0f) {
                volume = 0f;
                stop();
            }
        }
    }

    // =========================================================================================== frame

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        update();
        g = graphics;
        try {
            W = width;
            H = height;
            CX = W * 0.5f;
            CY = H * 0.5f;
            U = Math.min(W / 640f, H / 360f);
            VX = CX / U;
            VY = CY / U;
            gs = Math.max(1, minecraft.getWindow().getGuiScale());
            float t = time;
            frame = (int) (t * 30f);
            int scene = sceneAt(t);
            float lt = t - SCENE_START[scene];
            float glitch = glitchAt(t, scene);
            shX = glitch > 0f ? (hash(frame * 7 + 3) - 0.5f) * 7f * glitch : 0f;
            shY = 0f;
            if (t >= IMPACT) {
                float amp = 12f * (float) Math.exp(-(t - IMPACT) * 1.4f);
                shX += (hash(frame * 3 + 1) - 0.5f) * 2f * amp;
                shY += (hash(frame * 3 + 2) - 0.5f) * 2f * amp;
            } else if (t > IMPACT - 1.2f) {
                float amp = 2.2f * range(t, IMPACT - 1.2f, IMPACT);
                shX += (hash(frame * 3 + 5) - 0.5f) * 2f * amp;
                shY += (hash(frame * 3 + 6) - 0.5f) * 2f * amp;
            }
            cam(1f, 0f, 0f, 0f);

            g.fill(0, 0, width, height, 0xFF000000);
            // the picture, then the screen effects over it, then the scene's HUD on top so it stays legible
            hud = false;
            drawScene(scene, lt);
            float keepX = shX, keepY = shY;
            shX = 0f;
            shY = 0f;
            glitchBands(glitch);
            scanlines();
            vignette(scene, t);
            shX = keepX;
            shY = keepY;
            hud = true;
            drawScene(scene, lt);
            hud = false;
            shX = 0f;
            shY = 0f;
            drawCaption(t);
            drawSceneLabel(scene, lt);
            drawTimecode(scene, t);
            flashes(t);
            drawControls(t);
        } finally {
            g = null;
        }
    }

    private static int sceneAt(float t) {
        int s = 0;
        while (s + 1 < SCENE_START.length && t >= SCENE_START[s + 1]) s++;
        return s;
    }

    /** Runs a scene; called twice per frame, first for the picture ({@code hud} false), then for its HUD. */
    private void drawScene(int scene, float lt) {
        switch (scene) {
            case 0 -> sceneBroadcast(lt);
            case 1 -> sceneAsteroid(lt);
            case 2 -> sceneDisasters(lt);
            case 3 -> sceneMachines(lt);
            case 4 -> sceneDefence(lt);
            case 5 -> sceneStars(lt);
            case 6 -> sceneImpact(lt);
            default -> sceneTitle(lt);
        }
    }

    // =========================================================================================== 0-4 cold open

    private void sceneBroadcast(float lt) {
        if (hud) return;
        // a CRT powering on: a bright line that opens into the picture
        if (lt < 0.2f) {
            float w = W * easeOutCubic(range(lt, 0.05f, 0.2f));
            if (w > 0.5f) {
                rectAdd(CX - w / 2f, CY - 1f, w, 2f, col(0xFFFFFF, 0.95f));
                glow(CX, CY, w * 0.35f, 0xBFD6FF, 0.3f);
            }
            return;
        }
        float open = easeOutCubic(range(lt, 0.2f, 0.38f));
        boolean clip = open < 1f;
        int half = (int) Math.ceil(H * 0.5f * Math.max(open, 0.01f));
        if (clip) g.enableScissor(0, (int) CY - half, width, (int) CY + half);
        beginHud();
        gradV(-VX - 8f, -VY - 8f, 2f * VX + 16f, 2f * VY + 16f, 0xFF1A0406, 0xFF040001);
        end();
        float noiseA = 0.62f * (1f - easeOutCubic(range(lt, 0.2f, 1.4f))) + 0.15f + 0.05f * hash(frame);
        noise(noiseA, 0xD8D8D8);
        float rb = ((lt * 0.38f) % 1.25f) - 0.15f;
        gradV(0f, rb * H - H * 0.07f, W, H * 0.07f, col(0xFFFFFF, 0f), col(0xFFFFFF, 0.07f));
        gradV(0f, rb * H, W, H * 0.03f, col(0xFFFFFF, 0.07f), col(0xFFFFFF, 0f));
        if (clip) {
            g.disableScissor();
            rectAdd(0f, CY - half - 1f, W, 2f, col(0xFFFFFF, 0.8f));
            rectAdd(0f, CY + half - 1f, W, 2f, col(0xFFFFFF, 0.8f));
        }

        // a warning sign pulsing with the alert tones
        if (lt >= 0.5f) {
            float wa = range(lt, 0.5f, 0.62f);
            float beat = (float) Math.exp(-frac((lt - 0.55f) / 0.3f) * 5f);
            beginHud();
            glow(0f, -104f, 48f + 10f * beat, 0xFF3A10, (0.25f + 0.35f * beat) * wa);
            warningSign(0f, -104f, 40f * (1f + 0.06f * beat), ((int) (lt / 0.3f) & 1) == 0 ? 0xFFC21A : 0xFF3A20, wa);
            end();
        }

        // the emergency banner slams in from the left
        if (lt >= 0.5f) {
            float slide = (1f - easeOutBack(range(lt, 0.5f, 0.85f))) * (2f * VX + 140f);
            float ba = hash(frame * 13 + 5) < 0.06f ? 0.55f : 1f;
            float by = -42f, bh = 44f;
            beginHud();
            g.pose().translate(-slide, 0f);
            float bx0 = -VX - 60f, bw = 2f * VX + 120f;
            gradV(bx0, by - bh / 2f, bw, bh, col(0xD8161E, ba), col(0x86080F, ba));
            rect(bx0, by - bh / 2f, bw, 1.5f, col(0xFF7A6E, ba));
            rect(bx0, by + bh / 2f - 1.2f, bw, 1.2f, col(0x2A0000, ba));
            hazard(-VX - 60f, by - bh / 2f, 82f, bh, ba);
            hazard(VX - 22f, by - bh / 2f, 82f, bh, ba);
            end();
            float k = Math.min(15f / 9f, (2f * VX - 200f) / Math.max(1, bannerMainW));
            ctext(bannerMain, bannerMainW, sx(-slide), sy(by - 13f), crispDown(k * U), col(0xFFFFFF, ba), 0, true);
            hudText(bannerSub, -slide, by + 7f, 7f, 2f * VX - 200f, col(0xFFD2CC, ba), 0, false);
        }
        if (lt >= 1.0f && ((int) (lt * 3.2f)) % 3 != 2) hudText(notDrill, 0f, -11f, 8.5f, 0f, col(0xFFD23A, 0.95f), 0, true);

        // the glitching countdown decodes, then settles on 365
        if (lt >= 1.15f) {
            float ca = range(lt, 1.15f, 1.4f);
            hudText(countdownLabel, 0f, 20f, 9f, 0f, col(0xFF8E84, ca), 0, true);
            String digits;
            if (lt < 2.2f) {
                int v = 100 + (int) (hash((int) (lt * 20f) * 31 + 7) * 899f);
                if (v != cacheRandom) {
                    cacheRandom = v;
                    randomStr = Integer.toString(v);
                }
                digits = randomStr;
            } else digits = "365";
            float settle = 1f - range(lt, 2.2f, 2.9f);
            float gl = glitchAt(time, 0);
            beginHud();
            glow(0f, 62f, 90f, 0xFF2010, 0.22f + 0.55f * (lt >= 2.2f ? settle : 0f));
            end();
            float split = 0.8f + 3.5f * gl + (lt < 2.2f ? 1.6f : 0f);
            bigCounter(digits, daysUnit, 0f, 36f, 54f, ca, split, gl > 0.3f || lt < 2.2f && hash(frame * 5 + 1) < 0.25f ? 1f : 0f,
                    lt >= 2.2f && settle > 0.6f ? 0xFFF0C0 : 0xFFFFFF, 0);
        }
    }

    /** A filled warning triangle with an exclamation mark (current pose units). */
    private void warningSign(float cx, float cy, float s, int fill, float a) {
        int rows = 36;
        float h = s * 0.88f, top = cy - h / 2f;
        for (int i = 0; i < rows; i++) {
            float w = s * (i + 0.5f) / rows;
            rect(cx - w / 2f, top + i * h / rows, w, h / rows + 0.3f, col(fill, a));
        }
        rect(cx - s * 0.055f, cy - h * 0.12f, s * 0.11f, h * 0.36f, col(0x140808, a));
        rect(cx - s * 0.055f, cy + h * 0.3f, s * 0.11f, h * 0.11f, col(0x140808, a));
    }

    /** Yellow end cap with black diagonal hazard stripes, clipped to its box (current pose units). */
    private void hazard(float x, float y, float w, float h, float a) {
        rect(x, y, w, h, col(0xFFC21A, a));
        g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
        Matrix3x2fStack p = g.pose();
        for (float s = x - h; s < x + w + h; s += 13f) {
            p.pushMatrix();
            p.translate(s, y + h / 2f);
            p.rotate(0.6f);
            rect(-3.2f, -h, 6.4f, 2f * h, col(0x140808, a));
            p.popMatrix();
        }
        g.disableScissor();
    }

    // =========================================================================================== 4-12 the asteroid

    private void sceneAsteroid(float lt) {
        float a = clamp01(lt / 8f);
        cam(1f + 0.1f * smooth(a), 0f, 0f, -0.035f + 0.025f * smooth(a));
        float size = 38f / (1f - 0.82f * a);
        float e = smooth(a);
        float ax = lerp(170f, 88f, e), ay = lerp(-74f, -14f, e);
        float ex = -232f, ey = 50f, er = 31f;
        if (hud) {
            targetHud(lt, ax, ay, size, ex, ey, er);
            return;
        }
        bgLayer(Art.STARS, 0.25f, 6f, 1f, 0xFFFFFF);
        bgLayer(Art.NEBULA, 0.45f, 11f, 0.75f, 0xFFFFFF);
        warpStars(hx(ax, ay), hy(ax, ay), lt);
        beginWorld();
        glow(ex, ey, er * 2.0f, 0x3F86FF, 0.35f);
        sprite(Art.EARTH, PLAIN, ex, ey, er * 2f, er * 2f, 0.2f, WHITE);
        float tail = -0.5f, ct = (float) Math.cos(tail), st = (float) Math.sin(tail);
        float len = size * 3.1f * (1f + 0.05f * (float) Math.sin(lt * 17f) + 0.03f * hash(frame)), thick = size * 1.08f;
        spriteFrom(Art.COMA, ADD_TEX, ax - ct * size * 0.18f, ay - st * size * 0.18f, len, thick, tail, col(0xFFFFFF, 0.92f));
        glow(ax, ay, size * 0.95f, 0xFF7A2A, 0.5f);
        sprite(Art.ASTEROID, PLAIN, ax, ay, size, size, 0.5f + lt * 0.42f, WHITE);
        glow(ax + ct * size * 0.22f, ay + st * size * 0.22f, size * 0.5f, 0xFF5A1A, 0.3f);
        end();
    }

    /** Hyperspace-like streaks flowing out of the asteroid's direction: the camera is rushing in. */
    private void warpStars(float vx, float vy, float lt) {
        beginHud();
        for (int i = 0; i < WARP; i++) {
            float z = 1f - frac(WS_Z[i] + lt * WS_V[i]);
            float zz = Math.max(z, 0.04f), zp = Math.max(z + 0.035f, 0.05f);
            float dx = (float) Math.cos(WS_A[i]), dy = (float) Math.sin(WS_A[i]);
            float r = 14f / zz, rp = 14f / zp;
            float alpha = smooth((1f - z) * 3f) * 0.75f;
            if (alpha < 0.02f || r > VX * 1.6f) continue;
            streak(vx + dx * rp, vy + dy * rp, vx + dx * r, vy + dy * r, 0.7f + (1f - z) * 1.8f, 0xCFE0FF, alpha);
        }
        end();
    }

    private void targetHud(float lt, float ax, float ay, float size, float ex, float ey, float er) {
        float hax = hx(ax, ay), hay = hy(ax, ay), hs = size * camZoom;
        float hex = hx(ex, ey), hey = hy(ex, ey);
        boolean locked = lt >= 2.2f;
        float half = hs * 0.6f;
        if (lt >= 1.0f) {
            half += locked ? (1f - range(lt, 2.2f, 2.45f)) * 10f : (1f - easeOutCubic(range(lt, 1.0f, 2.2f))) * 80f;
            int c = locked ? 0xFF3A2E : 0xFFB21E;
            float al = locked ? 1f : (((int) (lt * 9f)) & 1) == 0 ? 1f : 0.3f;
            beginHud();
            brackets(hax, hay, half, half, Math.max(8f, half * 0.3f), 1.6f, col(c, al));
            if (locked) {
                dashedCircle(hax, hay, half * 1.22f, lt * 0.8f, 1.2f, col(c, 0.75f));
                float tick = half * 0.25f;
                rect(hax - half - tick, hay - 0.6f, tick, 1.2f, col(c, 0.9f));
                rect(hax + half, hay - 0.6f, tick, 1.2f, col(c, 0.9f));
                rect(hax - 0.6f, hay - half - tick, 1.2f, tick, col(c, 0.9f));
                rect(hax - 0.6f, hay + half, 1.2f, tick, col(c, 0.9f));
            }
            end();
            hudText(locked ? hudLocked : hudAcquiring, hax - half, hay - half - 12f, 8.2f, 0f, col(c, al), -1, true);
            if (locked) hudText("2097-ΩX", hax + half, hay + half + 4f, 8.2f, 0f, col(0xFFFFFF, 1f), 1, true);
        }
        if (lt >= 2.6f) {
            float pr = easeOutCubic(range(lt, 2.6f, 3.4f));
            float dx = hex - hax, dy = hey - hay, d = (float) Math.sqrt(dx * dx + dy * dy);
            float ux = dx / d, uy = dy / d, s0 = half * 0.95f, s1 = d - er - 4f;
            beginHud();
            streak(hax + ux * s0, hay + uy * s0, hax + ux * lerp(s0, s1, pr), hay + uy * lerp(s0, s1, pr), 5f, 0xFF2A1E, 0.18f);
            float end = lerp(s0, s1, pr), off = (lt * 30f) % 12f;
            for (float s = s0 - 12f + off; s < end; s += 12f) {
                float a0 = Math.max(s0, s), a1 = Math.min(end, s + 7f);
                if (a1 > a0) line(hax + ux * a0, hay + uy * a0, hax + ux * a1, hay + uy * a1, 1.3f, col(0xFF3A2E, 0.9f), false);
            }
            if (pr >= 1f) {
                for (int j = 0; j < 2; j++) {
                    float ph = frac(lt * 0.9f + j * 0.5f);
                    ring(hex, hey, er + 3f + ph * 22f, 0xFF2A1E, (1f - ph) * 0.85f, true);
                }
                rect(hex - 6f, hey - 0.6f, 12f, 1.2f, col(0xFF3A2E, 0.9f));
                rect(hex - 0.6f, hey - 6f, 1.2f, 12f, col(0xFF3A2E, 0.9f));
            }
            end();
            if (pr >= 1f) hudText(hudEarth, hex, hey - er - 34f, 8.2f, 0f, col(0xFF8A7C, 1f), 0, true);
        }
        if (lt >= 2.4f) readouts(lt);
    }

    private void readouts(float lt) {
        float pIn = easeOutCubic(range(lt, 2.4f, 2.75f));
        float x0 = -302f, w = 160f, y0 = -132f, rowH = 16f, h = 24f + 4f * rowH;
        beginHud();
        rect(x0, y0, w, h * pIn, col(0x0A0204, 0.62f * pIn));
        rect(x0, y0, w * pIn, 1.2f, col(0xFF4A3A, pIn));
        rect(x0, y0 + h * pIn - 1f, w * pIn, 1f, col(0xFF4A3A, 0.6f * pIn));
        rect(x0, y0, 1.2f, h * pIn, col(0xFF4A3A, pIn));
        end();
        hudText(hudAnalysis, x0 + 6f, y0 + 6f, 8.5f, w - 12f, col(0xFF8A7C, pIn), -1, true);
        for (int i = 0; i < 4; i++) {
            float rt = 2.75f + i * 0.35f;
            if (lt < rt) break;
            float ra = range(lt, rt, rt + 0.12f), ry = y0 + 24f + i * rowH;
            hudText(rowLabel[i], x0 + 6f, ry, 8.2f, w * 0.55f, col(0xD8B8B4, ra), -1, true);
            String v;
            int vc = 0xFFFFFF;
            if (i == 3) {
                float pt = lt - rt;
                int tenths = Math.round(997f * easeOutCubic(pt / 0.9f));
                if (tenths != cacheProb) {
                    cacheProb = tenths;
                    probStr = tenths / 10 + "." + tenths % 10 + "%";
                }
                v = probStr;
                if (pt > 0.9f) vc = (((int) (lt * 4f)) & 1) == 0 ? 0xFF3A2E : 0xFFFFFF;
            } else v = typed(ROW_VALUE[i], (lt - rt) * 28f);
            hudText(v, x0 + w - 6f, ry, 8.2f, 0f, col(vc, ra), 1, true);
        }
    }

    // =========================================================================================== 12-20 the year of disasters

    private static float dayAt(float lt) {
        if (lt < 0.6f) return 365f;
        if (lt < 2.4f) return lerp(365f, 270f, easeInOut(range(lt, 0.6f, 2.4f)));
        if (lt < 3.3f) return 270f;
        if (lt < 7.2f) {
            float p = range(lt, 3.3f, 7.2f);
            return lerp(270f, 1f, p * (0.35f + 0.65f * p));
        }
        return 1f;
    }

    private void sceneDisasters(float lt) {
        float a = clamp01(lt / 8f);
        cam(1f + 0.1f * smooth(a), 0f, 0f, 0f);
        float day = dayAt(lt);
        float D = Math.max(430f, VY * 2.35f), er = D / 2f;
        float ex = 78f, ey = 34f, spin = -0.05f + lt * 0.012f;
        float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        float ix = CRATER_X * er, iy = CRATER_Y * er;
        if (hud) {
            float wx = ex + ix * cs - iy * sn, wy = ey + ix * sn + iy * cs;
            float lx = hx(wx, wy) + er * 0.13f * camZoom, ly = hy(wx, wy);
            beginHud();
            line(lx, ly, lx + 24f, ly - 24f, 1f, col(0xFF3A2E, 0.9f), false);
            rect(lx + 24f, ly - 24.5f, 70f, 1f, col(0xFF3A2E, 0.9f));
            end();
            hudText(forecastImpact, lx + 27f, ly - 35f, 8.2f, 0f, col(0xFF7A6A, 1f), -1, true);
            dayCounter(lt, day);
            ticker(lt);
            return;
        }
        bgLayer(Art.STARS, 0.2f, 3f, 0.6f, 0xFFFFFF);

        beginWorld();
        glow(ex, ey, er * 1.25f, 0x3A7BFF, 0.42f);
        sprite(Art.EARTH, PLAIN, ex, ey, D, D, spin, WHITE);
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(ex, ey);
        p.rotate(spin);
        // wildfires spread as the days run out
        for (int i = 0; i < HOT; i++) {
            if (day > HOT_D[i]) continue;
            float in = range(HOT_D[i] - day, 0f, 12f);
            float fl = 0.7f + 0.3f * (float) Math.sin(lt * 9f + i * 1.7f);
            float fx = HOT_X[i] * er, fy = HOT_Y[i] * er;
            glow(fx, fy, er * (0.03f + 0.025f * HOT_S[i]), 0xFF5A14, 0.85f * fl * in);
            glow(fx, fy, er * 0.01f, 0xFFE6A0, 0.9f * in);
        }
        // thunderstorms flash across the dark side
        if (lt > 2.4f) {
            int slot = (int) (lt * 7f);
            for (int s = slot - 2; s <= slot; s++) {
                if (s < 0) continue;
                float age = lt - (s / 7f + hash(s * 13 + 1) * 0.1f);
                if (age < 0f || age > 0.18f) continue;
                float f = 1f - age / 0.18f;
                float lx = (hash(s * 5 + 2) * 2f - 1f) * er * 0.7f, ly = (hash(s * 5 + 3) * 2f - 1f) * er * 0.7f;
                glow(lx, ly, er * 0.07f, 0xBFD8FF, f * f);
                glow(lx, ly, er * 0.02f, 0xFFFFFF, f);
            }
        }
        // the predicted impact point: a red stain, expanding rings and a crosshair
        glowN(ix, iy, er * 0.24f, 0xFF1A10, 0.26f + 0.08f * (float) Math.sin(lt * 6f));
        for (int j = 0; j < 3; j++) {
            float ph = frac(lt * 0.55f + j / 3f);
            ring(ix, iy, er * 0.7f * ph, 0xFF3A1E, (1f - ph) * 0.8f, true);
        }
        float c0 = er * 0.035f, c1 = er * 0.12f;
        rect(ix - c1, iy - 0.8f, c1 - c0, 1.6f, col(0xFF3A2E, 0.95f));
        rect(ix + c0, iy - 0.8f, c1 - c0, 1.6f, col(0xFF3A2E, 0.95f));
        rect(ix - 0.8f, iy - c1, 1.6f, c1 - c0, col(0xFF3A2E, 0.95f));
        rect(ix - 0.8f, iy + c0, 1.6f, c1 - c0, col(0xFF3A2E, 0.95f));
        p.popMatrix();
        // a scan line sweeps the globe
        float sy = ey - er + frac(lt * 0.45f) * 2f * er, dy = sy - ey;
        float chord = (float) Math.sqrt(Math.max(0f, er * er - dy * dy));
        if (chord > 2f) streak(ex - chord, sy, ex + chord, sy, 3f, 0xFF3A2E, 0.35f);
        end();
    }

    private void dayCounter(float lt, float day) {
        int d = Math.max(1, Math.round(day));
        float a = range(lt, 0.15f, 0.5f);
        float x0 = -292f, top = -66f;
        beginHud();
        gradH(-VX - 4f, -112f, VX - 70f, 196f, col(0x000000, 0.72f * a), col(0x000000, 0f));
        float flash = lt >= 2.4f && lt < 3.3f ? 1f - range(lt, 2.4f, 2.9f) : 0f;
        glow(x0 + 64f, top + 42f, 75f, 0xFF2010, (0.22f + 0.5f * flash) * a);
        end();
        hudText(countdownLabel, x0, top, 9f, 0f, col(0xFF9A90, a), -1, true);
        if (d != cacheDay) {
            cacheDay = d;
            dayStr = Integer.toString(d);
        }
        boolean blinkOne = lt >= 7.2f && (((int) (lt * 4f)) & 1) == 0;
        int nc = d <= 1 ? (blinkOne ? 0xFF2A1E : 0xFFFFFF) : flash > 0f ? 0xFF4A3A : 0xFFFFFF;
        bigCounter(dayStr, daysUnit, x0, top + 14f, 52f, a, 0.8f, 0f, nc, -1);
        beginHud();
        rect(x0, top + 76f, 170f, 4f, col(0x3A0806, 0.8f * a));
        rect(x0, top + 76f, 170f * d / 365f, 4f, col(0xFF3A2E, a));
        end();
        if (lt >= 2.4f) {
            float sp = range(lt, 2.4f, 2.58f);
            float sc = 1f + 0.8f * (1f - easeOutCubic(sp));
            float sa = sp * (lt > 3.3f ? 0.75f : 1f);
            stamp(forecastBegin, sx(x0 + 86f), sy(top + 98f), sc, sa);
        }
    }

    /** A tilted red stamp (screen space). */
    private void stamp(String s, float x, float y, float scale, float a) {
        if (a <= 0.01f) return;
        float k = Math.max(8f / 9f * U * 1.2f, 0.75f) * scale;
        float tw = font.width(s) * k, th = 9f * k;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.rotate(-0.12f);
        float pad = 4f * U;
        rect(-tw / 2f - pad, -th / 2f - pad, tw + 2f * pad, th + 2f * pad, col(0x200000, 0.55f * a));
        float b = Math.max(1f, 1.4f * U);
        rect(-tw / 2f - pad, -th / 2f - pad, tw + 2f * pad, b, col(0xFF2A1E, a));
        rect(-tw / 2f - pad, th / 2f + pad - b, tw + 2f * pad, b, col(0xFF2A1E, a));
        rect(-tw / 2f - pad, -th / 2f - pad, b, th + 2f * pad, col(0xFF2A1E, a));
        rect(tw / 2f + pad - b, -th / 2f - pad, b, th + 2f * pad, col(0xFF2A1E, a));
        p.translate(-tw / 2f, -th / 2f + k);
        p.scale(k, k);
        g.drawString(font, s, 0, 0, col(0xFF3A2E, a), false);
        p.popMatrix();
    }

    private void ticker(float lt) {
        float a = easeOutCubic(range(lt, 0.7f, 1.0f));
        if (a <= 0f) return;
        float m = margin(), k = crispK(1f);
        float h = 13f * k, y = m + 16f * k - (1f - a) * (h + m + 16f * k);
        rect(0f, y, W, h, col(0x4A0606, 0.85f));
        rect(0f, y, W, 1f, col(0xFF3A2E, 0.9f));
        rect(0f, y + h - 1f, W, 1f, col(0xFF3A2E, 0.6f));
        float tw = font.width(tickerLoop) * k;
        if (tw > 1f) {
            float off = (lt * 48f * k) % tw;
            g.enableScissor(0, (int) y, width, (int) Math.ceil(y + h));
            for (float x = -off; x < W; x += tw) text(tickerLoop, x, y + 2.5f * k, k, col(0xFFE8E0, 1f), -1, false);
            g.disableScissor();
        }
        float tagW = font.width(forecastTag) * k + 12f * k;
        rect(0f, y, m + tagW, h, col(0xE0201A, 1f));
        text(forecastTag, m + 6f * k, y + 2.5f * k, k, col(0xFFFFFF, 1f), -1, false);
    }

    // =========================================================================================== 20-28 the machines

    private void sceneMachines(float lt) {
        float a = clamp01(lt / 8f);
        float step = lt / 0.6f;
        if (!hud) shY += (float) Math.exp(-(step - (float) Math.floor(step)) * 7f) * 0.9f;
        cam(1f + 0.08f * smooth(a), 0f, 0f, 0f);
        if (hud) {
            radar(lt);
            evolution(lt);
            return;
        }
        float hz = 30f;
        float L = -VX * 1.4f - 40f, R = VX * 1.4f + 40f, T = -VY * 1.4f - 40f, B = VY * 1.4f + 40f;
        beginWorld();
        gradV(L, T, R - L, hz - 70f - T, 0xFF030102, 0xFF1E0504);
        gradV(L, hz - 70f, R - L, 70f, 0xFF1E0504, 0xFF62140A);
        gradV(L, hz, R - L, B - hz, 0xFF2A0705, 0xFF040101);
        sprite(Art.GLOW, ADD_TEX, 0f, hz, (R - L) * 1.1f, 120f, 0f, col(0xFF3A14, 0.45f + 0.08f * (float) Math.sin(lt * 2.3f)));
        // distant fires flare along the horizon
        int slot = (int) (lt * 3f);
        for (int s = slot - 3; s <= slot; s++) {
            if (s < 0) continue;
            float age = lt - (s / 3f + hash(s * 11 + 4) * 0.3f);
            if (age < 0f || age > 0.9f) continue;
            float f = 1f - age / 0.9f, fx = (hash(s * 11 + 5) * 2f - 1f) * VX;
            glow(fx, hz - 3f, 18f + 14f * hash(s * 11 + 6), 0xFF7A2A, 0.7f * f * f);
        }
        for (int i = 0; i < SMOKE; i++) {
            float x = wrap(SM_X[i] * VX + lt * SM_V[i], -VX - 160f, VX + 160f);
            glowN(x, hz + SM_Y[i], SM_R[i], 0x160303, 0.38f);
        }
        for (int i = 0; i < MDRONES; i++) {
            float span = 2f * VX + 140f;
            float x = -VX - 70f + frac(MD_PH[i] + lt * MD_V[i] / span) * span;
            if (MD_DIR[i] < 0f) x = -x;
            float s = MD_S[i] * (0.7f + 0.6f * (MD_Y[i] + 160f) / 120f);
            drone(x, MD_Y[i] + 3f * (float) Math.sin(lt * 2f + i), s, MD_DIR[i], lt, i);
        }
        robotRow(hz + 8f + a * 4f, 62f * (1f + 0.1f * a), lt * 5f, 0x2C0C0A, 0.92f, 0.55f, 0f, lt);
        gradV(L, hz - 26f, R - L, 56f, col(0x3A0806, 0f), col(0x3A0806, 0.35f));
        robotRow(hz + 72f + a * 10f, 118f * (1f + 0.16f * a), lt * 10f + 300f, 0x150606, 1f, 0.8f, 1.8f, lt);
        robotRow(VY + 44f + a * 12f, 250f * (1f + 0.22f * a), lt * 22f + 700f, 0x060202, 1f, 1f, 4f, lt);
        end();
    }

    /** One parallax row of the machine army; a second additive pass makes the red eyes glow. */
    private void robotRow(float baseline, float h, float drift, int tint, float alpha, float eye, float bob, float lt) {
        float w = h * 4f;
        float left = -VX * 1.4f - 60f, right = VX * 1.4f + 60f;
        float start = left + (drift % w) - w;
        int i = 0;
        for (float x = start; x < right; x += w, i++) {
            float yo = bob * Math.abs((float) Math.sin(lt * 2.6f + i * 1.7f));
            spriteTL(Art.ROBOTS, PLAIN, x, baseline - h - yo, w, h, col(tint, alpha));
            if (eye > 0f && present[Art.ROBOTS.ordinal()])
                spriteTL(Art.ROBOTS, ADD_TEX, x, baseline - h - yo, w, h, col(0xFF3020, eye * (0.75f + 0.25f * (float) Math.sin(lt * 4f + i))));
        }
    }

    private void drone(float x, float y, float s, float dir, float lt, int i) {
        streak(x - dir * 22f * s, y + 0.6f * s, x - dir * 5f * s, y + 0.3f * s, 2.4f * s, 0xFF4A1A, 0.35f);
        rect(x - 7f * s, y - 1.3f * s, 14f * s, 2.6f * s, 0xFF0B0B0E);
        rect(x - 2.5f * s, y - 2.6f * s, 5f * s, 1.4f * s, 0xFF0E0E12);
        rect(x - 11f * s, y - 3.4f * s, 8f * s, 0.9f * s, 0xFF15151A);
        rect(x + 3f * s, y - 3.4f * s, 8f * s, 0.9f * s, 0xFF15151A);
        boolean on = (((int) (lt * 4f + i * 0.37f)) & 1) == 0;
        glow(x + dir * 5f * s, y, (on ? 4.2f : 2.4f) * s, 0xFF2010, on ? 0.95f : 0.5f);
    }

    private void radar(float lt) {
        float a = easeOutCubic(range(lt, 0.4f, 0.8f));
        if (a <= 0f) return;
        float m = margin(), k = crispK(1f);
        float R = clamp(Math.min(W, H) * 0.12f, 30f, 64f);
        float cx = W - m - R - 6f, cy = m + 16f * k + R + 6f;
        float px = cx - R - 6f, py = cy - R - 6f, pw = 2f * R + 12f, ph = 2f * R + 12f + 13f * k;
        rect(px, py, pw, ph, col(0x080102, 0.62f * a));
        rect(px, py, pw, 1f, col(0xFF3A2A, 0.6f * a));
        rect(px, py + ph - 1f, pw, 1f, col(0xFF3A2A, 0.6f * a));
        rect(px, py, 1f, ph, col(0xFF3A2A, 0.6f * a));
        rect(px + pw - 1f, py, 1f, ph, col(0xFF3A2A, 0.6f * a));
        ring(cx, cy, R, 0xFF3A2A, 0.45f * a, false);
        ring(cx, cy, R * 0.66f, 0xFF3A2A, 0.32f * a, false);
        ring(cx, cy, R * 0.33f, 0xFF3A2A, 0.25f * a, false);
        rect(cx - R, cy - 0.5f, 2f * R, 1f, col(0xFF3A2A, 0.22f * a));
        rect(cx - 0.5f, cy - R, 1f, 2f * R, col(0xFF3A2A, 0.22f * a));
        float th = lt * (float) Math.PI;
        for (int i = 9; i >= 1; i--) {
            float an = th - i * 0.075f, f = 1f - i / 10f;
            line(cx, cy, cx + R * (float) Math.cos(an), cy + R * (float) Math.sin(an), 1.6f, col(0xFF3A1E, 0.4f * f * f * a), true);
        }
        line(cx, cy, cx + R * (float) Math.cos(th), cy + R * (float) Math.sin(th), 1.4f, col(0xFF8A6A, a), true);
        int count = 0;
        for (int i = 0; i < BLIPS; i++) {
            if (lt < BL_T[i]) continue;
            count++;
            float age = lt - BL_T[i];
            float rr = R * BL_R[i] * (1f - 0.04f * age), an = BL_A[i];
            float d = th - an;
            d -= TAU * (float) Math.floor(d / TAU);
            float br = (float) Math.exp(-d * 1.4f);
            float bx = cx + rr * (float) Math.cos(an), by = cy + rr * (float) Math.sin(an);
            float in = range(age, 0f, 0.3f);
            glow(bx, by, 3.2f + br * 2f, 0xFF2A1A, (0.22f + 0.78f * br) * a * in);
            rect(bx - 0.75f, by - 0.75f, 1.5f, 1.5f, col(0xFF9080, (0.4f + 0.6f * br) * a * in));
        }
        text(radarLabel, px + 3f * k, py + 3f * k, k, col(0xFF8A7C, a), -1, false);
        if (count != cacheHostiles) {
            cacheHostiles = count;
            hostilesStr = radarHostiles + "  " + count;
        }
        text(hostilesStr, cx, cy + R + 6f + 2f * k, k, col(0xFF5A4A, a), 0, true);
    }

    private void evolution(float lt) {
        float a = range(lt, 0.8f, 1.1f);
        if (a <= 0f) return;
        int stage = 1 + Math.min(14, (int) (Math.max(0f, lt - 1.0f) / 0.42f));
        if (stage != cacheStage) {
            cacheStage = stage;
            stageStr = evolutionLabel + "  " + (stage < 10 ? "0" : "") + stage + " / 15";
        }
        float m = margin(), k = crispK(1f), y = m + 16f * k;
        text(stageStr, m, y, k, col(0xFFB0A8, a), -1, true);
        float sw = 7f * k, sh = 4f * k, gap = 2f * k;
        float since = Math.max(0f, lt - 1.0f) - (stage - 1) * 0.42f;
        for (int i = 0; i < 15; i++) {
            boolean on = i < stage;
            int c = on ? (i == stage - 1 && since < 0.2f ? 0xFFFFFF : 0xFF3A2E) : 0x3A0A08;
            rect(m + i * (sw + gap), y + 11f * k, sw, sh, col(c, a * (on ? 1f : 0.7f)));
        }
    }

    // =========================================================================================== 28-36 hold the line

    private float droneX(int i, float lt) {
        float u = (lt - DR_S[i]) / DR_DUR[i];
        return lerp(DR_SIDE[i] * (VX + 30f), DR_TX[i], u) + 6f * (float) Math.sin(lt * 2.3f + i);
    }

    private float droneY(int i, float lt) {
        float u = (lt - DR_S[i]) / DR_DUR[i];
        return lerp(DR_FY[i], DR_TY[i], u) + 4f * (float) Math.sin(lt * 3.1f + i * 2f);
    }

    private float jetX(float lt) {
        return VX + 70f - (lt - JET_T0) * JET_SPEED;
    }

    private float jetY(float lt) {
        return -126f + 6f * (float) Math.sin(lt * 1.3f);
    }

    private void sceneDefence(float lt) {
        float a = clamp01(lt / 8f);
        float kickAge = lt - JET_KILL;
        if (!hud && kickAge >= 0f && kickAge < 1.2f) {
            float amp = 5f * (1f - kickAge / 1.2f);
            shX += (hash(frame * 3 + 9) - 0.5f) * 2f * amp;
            shY += (hash(frame * 3 + 10) - 0.5f) * 2f * amp;
        }
        cam(1.0f + 0.05f * smooth(a), 0f, 0f, 0f);
        float bw = 2f * VX * 1.12f + 40f, bh = bw / 4f, bx = -bw / 2f, baseTop = 92f - 0.8f * bh;
        if (hud) {
            droneMarks(lt);
            chipRow(lt);
            return;
        }
        float L = -VX * 1.35f - 40f, R = VX * 1.35f + 40f, T = -VY * 1.35f - 40f, B = VY * 1.35f + 40f;
        beginHud();
        gradV(-VX - 8f, -VY - 8f, 2f * VX + 16f, 2f * VY + 16f, 0xFF02040C, 0xFF1B2B4C);
        end();
        bgLayer(Art.STARS, 0.1f, 2f, 0.35f, 0xDDE6FF);
        beginWorld();
        // the glow of the fight behind the walls, smoke banks drifting over the sky
        sprite(Art.GLOW, ADD_TEX, 0f, baseTop + bh * 0.6f, (R - L) * 1.05f, bh * 1.2f, 0f, col(0xFF7A30, 0.2f + 0.05f * (float) Math.sin(lt * 7f)));
        for (int i = 0; i < 3; i++)
            glowN(wrap(-VX + i * VX * 0.9f + lt * (6f + 3f * i), -VX - 200f, VX + 200f), -96f + i * 34f, 110f, 0x0A1424, 0.3f);
        // searchlights sweep the sky from behind the base
        for (int i = 0; i < 2; i++) {
            float sx = bx + bw * (i == 0 ? 0.2f : 0.77f), sy = baseTop + bh * 0.62f;
            float an = -HALF_PI + 0.42f * (float) Math.sin(lt * 0.55f + i * 2.1f) + (i == 0 ? -0.18f : 0.18f);
            Matrix3x2fStack p = g.pose();
            p.pushMatrix();
            p.translate(sx, sy);
            p.rotate(an - HALF_PI);
            gradV(-14f, 0f, 28f, 460f, col(0xBFD8FF, 0.2f), col(0xBFD8FF, 0f));
            p.popMatrix();
        }
        // flak bursting high above
        for (int i = 0; i < FLAK; i++) {
            float age = lt - FL_T[i];
            if (age < 0f || age > 1.8f) continue;
            float fx = FL_X[i] * VX, fy = FL_Y[i];
            glowN(fx + age * 4f, fy - age * 6f, 9f + age * 16f, 0x26221F, 0.6f * (1f - age / 1.8f));
            if (age < 0.16f) {
                float f = 1f - age / 0.16f;
                glow(fx, fy, 16f + age * 160f, 0xFFB060, f);
                glow(fx, fy, 5f + age * 40f, 0xFFF4D8, f);
            }
            if (age < 0.35f) ring(fx, fy, 4f + age * 42f, 0xFFD8A0, (1f - age / 0.35f) * 0.4f, true);
        }
        // the interceptor from the base's launcher and the strike jet it brings down
        missile(lt, bx + bw * SAM_FX, baseTop + bh * SAM_FY);
        if (lt >= JET_T0 && lt < JET_KILL) jet(jetX(lt), jetY(lt), 2.2f);
        // drones dive at the base
        for (int i = 0; i < DN; i++) {
            if (lt < DR_S[i] || lt >= DR_KILL[i]) continue;
            float dir = Math.signum(DR_TX[i] - DR_SIDE[i] * (VX + 30f));
            drone(droneX(i, lt), droneY(i, lt), 1.75f, dir == 0f ? 1f : dir, lt, i);
        }
        for (int i = 0; i < DN; i++) {
            float age = lt - DR_KILL[i];
            if (age >= 0f && age < 2.2f) explosion(droneX(i, DR_KILL[i]), droneY(i, DR_KILL[i]), age, 22f, i + 3);
        }
        if (kickAge >= 0f && kickAge < 2.6f) explosion(jetX(JET_KILL), jetY(JET_KILL), kickAge, 44f, 99);
        // the base, its windows lit by a second additive pass, the ground below it
        spriteTL(Art.BASE, PLAIN, bx, baseTop, bw, bh, WHITE);
        if (present[Art.BASE.ordinal()]) spriteTL(Art.BASE, ADD_TEX, bx, baseTop, bw, bh, col(0xFFC070, 0.35f + 0.08f * (float) Math.sin(lt * 5f)));
        rect(L, baseTop + bh - 0.5f, R - L, B - baseTop - bh + 0.5f, 0xFF05070D);
        // the base's own guns track the drones and fire
        for (int k = 0; k < GUN_FX.length; k++) gunFire(bx + bw * GUN_FX[k], baseTop + bh * GUN_FY[k], lt, k, bw);
        if (kickAge >= 0f && kickAge < 1f) rectAdd(L, T, R - L, B - T, col(0xFFB070, 0.28f * (float) Math.exp(-kickAge * 4f)));
        end();
    }

    /** The base gun nearest to where drone i is shot down. */
    private int gunOf(int i, float bw) {
        float kx = droneX(i, DR_KILL[i]);
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < GUN_FX.length; k++) {
            float d = Math.abs(kx - (-bw / 2f + bw * GUN_FX[k]));
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    /** Bursts of tracers from one base gun at the drones it engages, with a muzzle flash (world space). */
    private void gunFire(float mx, float my, float lt, int gun, float bw) {
        boolean flash = false;
        for (int i = 0; i < DN; i++) {
            float k = DR_KILL[i], w0 = k - 1.25f;
            if (lt < w0 || lt > k + 0.7f || gunOf(i, bw) != gun) continue;
            for (int e = 0; e < 15; e++) {
                float te = w0 + e * 0.085f;
                if (te > lt || te > k) break;
                if (lt - te < 0.04f) flash = true;
                float tt = Math.min(te + 0.2f, k);
                float px = droneX(i, tt), py = droneY(i, tt);
                float dx = px - mx, dy = py - my, dist = (float) Math.sqrt(dx * dx + dy * dy);
                float s = (lt - te) * 900f / dist;
                if (s > 1f) continue;
                float ux = dx / dist, uy = dy / dist, tx = mx + dx * s, ty = my + dy * s;
                streak(tx - ux * 30f, ty - uy * 30f, tx, ty, 6f, 0xFF9A40, 0.35f);
                streak(tx - ux * 22f, ty - uy * 22f, tx, ty, 2.2f, 0xFFF0B0, 1f);
            }
        }
        if (flash) {
            glow(mx, my, 14f, 0xFFC060, 0.9f);
            glow(mx, my, 4f, 0xFFFFFF, 1f);
        }
    }

    /** Tracking brackets on the incoming drones: amber while tracked, red once a gun engages (HUD). */
    private void droneMarks(float lt) {
        beginHud();
        for (int i = 0; i < DN; i++) {
            if (lt < DR_S[i] + 0.25f || lt >= DR_KILL[i]) continue;
            float wx = droneX(i, lt), wy = droneY(i, lt), x = hx(wx, wy), y = hy(wx, wy);
            boolean engaged = lt >= DR_KILL[i] - 1.25f;
            float s = engaged ? 13f : 17f - 4f * range(lt, DR_S[i] + 0.25f, DR_S[i] + 0.6f);
            brackets(x, y, s, s * 0.75f, 4f, 1f, col(engaged ? 0xFF3A2E : 0xFFB21E, engaged ? 0.95f : 0.7f));
        }
        end();
    }

    private void missile(float lt, float lx, float ly) {
        if (lt < MISSILE_T0) return;
        float jx = jetX(JET_KILL), jy = jetY(JET_KILL);
        float cx = lx - 10f, cy = ly - 210f;
        float uNow = (float) Math.pow(range(lt, MISSILE_T0, JET_KILL), 1.35);
        // smoke laid along the path, oldest first
        int n = 64;
        for (int j = 0; j <= n; j++) {
            float u = j / (float) n;
            if (u > uNow) break;
            float tj = MISSILE_T0 + (JET_KILL - MISSILE_T0) * (float) Math.pow(u, 1f / 1.35f);
            float age = lt - tj;
            if (age > 2.8f) continue;
            float bx = bez(lx, cx, jx, u), by = bez(ly, cy, jy, u);
            glowN(bx + age * 4f, by - age * 3f, 4f + age * 13f, age < 0.25f ? 0xC0A890 : 0x8A8E98, 0.55f * (1f - age / 2.8f));
        }
        if (lt >= JET_KILL) return;
        float mx = bez(lx, cx, jx, uNow), my = bez(ly, cy, jy, uNow);
        float u2 = Math.min(1f, uNow + 0.02f);
        float dx = bez(lx, cx, jx, u2) - mx, dy = bez(ly, cy, jy, u2) - my, d = (float) Math.sqrt(dx * dx + dy * dy) + 1e-4f;
        dx /= d;
        dy /= d;
        streak(mx - dx * 34f, my - dy * 34f, mx, my, 7f, 0xFF9A40, 0.8f);
        glow(mx, my, 11f, 0xFFF0C0, 1f);
        line(mx - dx * 8f, my - dy * 8f, mx + dx * 4f, my + dy * 4f, 2.2f, 0xFFDADDE2, false);
    }

    private static float bez(float a, float b, float c, float u) {
        float v = 1f - u;
        return v * v * a + 2f * v * u * b + u * u * c;
    }

    /** A dark strike jet flying left with its afterburner glowing (world space). */
    private void jet(float x, float y, float s) {
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(s, s);
        glow(16f, 0f, 9f, 0xFFA040, 0.9f);
        streak(16f, 0f, 40f, 0f, 4f, 0xFF7030, 0.5f);
        rect(-14f, -1.6f, 28f, 3.2f, 0xFF0A0B0F);
        rect(-17f, -0.9f, 4f, 1.8f, 0xFF0A0B0F);
        p.pushMatrix();
        p.translate(-1f, 0f);
        p.rotate(0.55f);
        rect(0f, -1f, 12f, 2f, 0xFF0C0D12);
        p.popMatrix();
        p.pushMatrix();
        p.translate(-1f, 0f);
        p.rotate(-0.55f);
        rect(0f, -1f, 12f, 2f, 0xFF0C0D12);
        p.popMatrix();
        rect(9f, -5f, 3f, 4f, 0xFF0C0D12);
        p.popMatrix();
        boolean on = (((int) (time * 3f)) & 1) == 0;
        if (on) glow(x - 2f * s, y + 3f * s, 3f * s, 0xFF2010, 0.9f);
    }

    /** Flash, fireball, shock ring, sparks and smoke (world space). */
    private void explosion(float x, float y, float age, float s, int seed) {
        if (age < 0f || age > 2.2f) return;
        glowN(x + age * 4f, y - age * 7f, s * (0.7f + age * 0.9f), 0x1A1714, 0.55f * (1f - age / 2.2f));
        if (age < 0.9f) glow(x, y, s * (0.45f + age * 1.1f), 0xFF7A20, 0.9f * (1f - age / 0.9f));
        if (age < 0.25f) glow(x, y, s * (0.3f + age * 2.5f), 0xFFF4D0, 1f - age / 0.25f);
        if (age < 0.45f) ring(x, y, s * (0.3f + age * 2.6f), 0xFFE0B0, (1f - age / 0.45f) * 0.6f, true);
        if (age < 0.8f) {
            for (int i = 0; i < 7; i++) {
                float an = hash(seed * 31 + i) * TAU, v = s * (2.2f + hash(seed * 17 + i) * 2f);
                float cx = (float) Math.cos(an), cy = (float) Math.sin(an);
                float px = x + cx * v * age, py = y + cy * v * age + 40f * age * age;
                streak(px - cx * v * 0.05f, py - cy * v * 0.05f, px, py, 1.6f, 0xFFC060, 1f - age / 0.8f);
            }
        }
    }

    /** The gameplay loop, one chip at a time (screen space). */
    private void chipRow(float lt) {
        float m = margin(), k = crispK(1f);
        float gap = 10f * k, pad = 4f * k;
        float total = -gap;
        for (String c : chips) total += font.width(c) * k + 2f * pad + gap;
        float scale = 1f;
        if (total > W - 2f * m) {
            scale = crispDown(k * (W - 2f * m) / total) / k;
            total *= scale;
        }
        float kk = k * scale, x = CX - total / 2f, y = m + 16f * k;
        for (int i = 0; i < chips.length; i++) {
            float at = 0.4f + i * 0.75f;
            float cw = font.width(chips[i]) * kk + 2f * pad * scale;
            if (lt >= at) {
                float ca = range(lt, at, at + 0.15f);
                boolean fresh = lt < at + 0.6f;
                int border = fresh ? 0xFF3A2E : 0x6A7A90;
                rect(x, y, cw, 12f * kk, col(0x05070C, 0.7f * ca));
                rect(x, y, cw, Math.max(1f / gs, kk * 0.75f), col(border, ca));
                rect(x, y + 12f * kk - Math.max(1f / gs, kk * 0.75f), cw, Math.max(1f / gs, kk * 0.75f), col(border, ca));
                text(chips[i], x + pad * scale, y + 2f * kk, kk, col(fresh ? 0xFFFFFF : 0xC8D2E0, ca), -1, false);
                if (i < chips.length - 1 && lt >= at + 0.3f) text("›", x + cw + gap * scale / 2f, y + 2f * kk, kk, col(0xFF5A4A, ca), 0, false);
            }
            x += cw + gap * scale;
        }
    }

    // =========================================================================================== 36-45 to the stars

    private void sceneStars(float lt) {
        if (lt < ORBIT_CUT - SCENE_START[5]) launch(lt);
        else orbit(lt - (ORBIT_CUT - SCENE_START[5]));
    }

    private static float altitude(float lt) {
        float lift = Math.max(0f, lt - 1.6f);
        return 0.5f * 46f * lift * lift;
    }

    private void launch(float lt) {
        float alt = altitude(lt), lift = Math.max(0f, lt - 1.6f);
        float camUp = Math.max(0f, alt - 30f) * 0.85f;
        if (!hud) {
            float rumble = range(lt, 1.2f, 1.6f) * (1f - range(lt, 2.6f, 4.2f));
            shX += (hash(frame * 3 + 11) - 0.5f) * 2.4f * rumble;
            shY += (hash(frame * 3 + 12) - 0.5f) * 2.4f * rumble;
        }
        cam(1f, 0f, -camUp, 0f);
        if (hud) {
            countdownWords(lt);
            return;
        }
        float up = clamp01(camUp / 260f);
        beginHud();
        gradV(-VX - 8f, -VY - 8f, 2f * VX + 16f, 2f * VY + 16f, lerpColor(0xFF0A1530, 0xFF02040C, up), lerpColor(0xFF4A3048, 0xFF0A1028, up));
        end();
        bgLayer(Art.STARS, 0.05f, 2f, 0.25f + 0.6f * up, 0xFFFFFF);
        float padY = 72f, ig = easeOutCubic(range(lt, 1.2f, 1.6f));
        float nozzle = padY - 4f - alt;
        beginWorld();
        sprite(Art.GLOW, ADD_TEX, 0f, padY + 4f, VX * 3f, 110f, 0f, col(0xFF8A4A, 0.38f));
        // the rest of the base along the horizon, hazy with distance
        float dh = 58f, dw = dh * 4f;
        for (float x = -VX * 1.45f; x < VX * 1.45f; x += dw) spriteTL(Art.BASE, PLAIN, x, padY + 1f - dh, dw, dh, WHITE);
        gradV(-VX * 1.5f, padY - dh, VX * 3f, dh + 1f, col(0x3A3048, 0f), col(0x4A3A50, 0.45f));
        gradV(-VX * 1.5f, padY, VX * 3f, VY * 3f, 0xFF0A0C12, 0xFF030305);
        tower(-58f, padY, 172f, lt);
        // the smoke column left behind the climbing rocket
        for (int j = 0; ; j++) {
            float tau = 1.6f + j * 0.06f;
            if (tau > lt) break;
            float age = lt - tau;
            if (age > 3.4f) continue;
            float py = padY - 4f - altitude(tau) + 12f;
            float px = (hash(j * 3 + 1) - 0.5f) * 8f + age * 5f;
            glowN(px, py, 9f + age * 17f, age < 0.35f ? 0xD8A070 : 0x707078, 0.45f * (1f - age / 3.4f));
        }
        padSmoke(lt, padY, true);
        if (ig > 0f) {
            float fl = 0.88f + 0.24f * hash(frame * 5 + 1);
            float fw = 42f * ig * (0.92f + 0.16f * fl), flen = (70f + 80f * clamp01(lift / 1.6f)) * ig * fl;
            if (lift > 0f) streak(0f, nozzle + 24f, 0f, nozzle + 24f + Math.min(340f, alt * 1.3f + 30f), 20f, 0xFF9040, 0.38f * ig);
            spriteTL(Art.FLAME, ADD_TEX, -fw / 2f, nozzle - 6f, fw, flen, col(0xFFFFFF, 0.95f));
            glow(0f, nozzle + 10f, 66f * ig, 0xFFB45A, 0.65f * ig * fl);
            glow(0f, padY, 230f, 0xFF9A50, 0.42f * ig * Math.max(0f, 1f - alt / 260f));
            sparks(lt, padY);
        }
        sprite(Art.ROCKET, PLAIN, 0f, nozzle - 66f, 64f, 128f, 0f, WHITE);
        padSmoke(lt, padY, false);
        end();
    }

    /** Exhaust clouds billowing out sideways from the pad, lit by the flame while they are young. */
    private void padSmoke(float lt, float padY, boolean back) {
        for (int i = 0; i < PUFFS; i++) {
            if (((i & 2) == 0) != back) continue;
            float age = lt - PF_E[i];
            if (age < 0f || age > 4.2f) continue;
            float x = PF_DIR[i] * PF_V[i] * 1.25f * (1f - (float) Math.exp(-1.3f * age)) / 1.3f;
            float y = padY - 8f - age * (6f + 9f * PF_S[i]);
            float r = Math.min(110f, (16f + age * 34f) * PF_S[i]);
            glowN(x, y, r, lerpColor(0xFFE0A878, 0xFF5C5C64, clamp01(age / 1.6f)), 0.68f * (1f - age / 4.2f));
        }
    }

    /** Embers thrown off the pad at ignition (world space). */
    private void sparks(float lt, float padY) {
        for (int i = 0; i < 14; i++) {
            float age = lt - 1.25f - hash(i * 7 + 3) * 0.5f;
            if (age < 0f || age > 0.8f) continue;
            float an = (float) Math.PI * (1f + hash(i * 7 + 4)), v = 90f + hash(i * 7 + 5) * 140f;
            float cx = (float) Math.cos(an), cy = (float) Math.sin(an);
            float px = cx * v * age, py = padY - 6f + cy * v * age * 0.6f + 120f * age * age;
            streak(px - cx * 7f, py - cy * 4f, px, py, 1.6f, 0xFFC060, 1f - age / 0.8f);
        }
    }

    /** The launch tower: rails, cross braces, a service arm that swings away at ignition, warning lights. */
    private void tower(float x, float base, float h, float lt) {
        int c = 0xFF0C0E14;
        rect(x - 7f, base - h, 2f, h, c);
        rect(x + 5f, base - h, 2f, h, c);
        for (float y = base - h; y < base - 4f; y += 14f) {
            line(x - 6f, y, x + 6f, y + 14f, 1.2f, c, false);
            line(x + 6f, y, x - 6f, y + 14f, 1.2f, c, false);
        }
        rect(x - 10f, base - h - 3f, 20f, 3f, c);
        float arm = 1f - easeOutCubic(range(lt, 1.1f, 1.5f));
        if (arm > 0.01f) rect(x + 7f, base - 104f, 26f * arm, 3f, c);
        boolean on = (((int) (time * 2.5f)) & 1) == 0;
        glow(x, base - h - 4f, on ? 5f : 3f, 0xFF2010, on ? 0.95f : 0.4f);
        glow(x, base - h * 0.55f, on ? 3f : 2f, 0xFF2010, on ? 0.7f : 0.3f);
    }

    private void countdownWords(float lt) {
        String w;
        float t0, size;
        if (lt < 0.4f) {
            w = "3";
            t0 = 0f;
            size = 64f;
        } else if (lt < 0.8f) {
            w = "2";
            t0 = 0.4f;
            size = 64f;
        } else if (lt < 1.2f) {
            w = "1";
            t0 = 0.8f;
            size = 64f;
        } else if (lt < 2.0f) {
            w = ignition;
            t0 = 1.2f;
            size = 22f;
        } else if (lt < 2.8f) {
            w = liftoff;
            t0 = 2.0f;
            size = 22f;
        } else return;
        boolean digit = size > 30f;
        float p = lt - t0, dur = digit ? 0.4f : 0.8f;
        float a = range(p, 0f, 0.05f) * (1f - range(p, dur - 0.1f, dur));
        float sc = 1f + 0.45f * (1f - easeOutCubic(range(p, 0f, 0.16f)));
        float k = size / 9f * U * sc;
        beginHud();
        glow(-190f, -40f + size * 0.38f * sc, size * 1.1f, digit ? 0xFF2A10 : 0xFF8030, 0.3f * a);
        end();
        text(w, sx(-190f), sy(-40f), k, col(digit ? 0xFFFFFF : 0xFFB060, a), 0, false);
    }

    private void orbit(float lb) {
        float e = smooth(lb / 4.5f);
        cam(1f + 0.07f * e, 12f * e, 0f, 0.01f);
        float mx = -24f, my = -26f, ms = 180f;
        float hbx = mx - ms * 0.14f, hby = my + ms * 0.17f;
        if (hud) {
            if (lb >= 1.7f) {
                float la = range(lb, 1.7f, 2.0f);
                float hx0 = hx(hbx, hby), hy0 = hy(hbx, hby), lx = hx(mx - ms * 0.5f - 18f, hby);
                beginHud();
                ring(hx0, hy0, 12f + 2f * (float) Math.sin(lb * 4f), 0x8AF0E0, 0.85f * la, true);
                rect(lx, hy0 - 0.5f, Math.max(0f, hx0 - 12f - lx) * la, 1f, col(0x8AF0E0, 0.8f * la));
                end();
                hudText(habitat, lx - 4f, hy0 - 4.5f, 8.5f, 0f, col(0xBFFFF0, la), 1, true);
                hudText(habitatMars, hx(232f, -104f), hy(232f, -104f) + 40f, 8.2f, 0f, col(0xFFB090, la * 0.85f), 0, true);
            }
            return;
        }
        bgLayer(Art.STARS, 0.2f, 5f, 1f, 0xFFFFFF);
        bgLayer(Art.NEBULA, 0.35f, 9f, 0.7f, 0xFFFFFF);
        beginWorld();
        glow(-262f, VY + 170f, 300f, 0x3F86FF, 0.35f);
        sprite(Art.EARTH, PLAIN, -262f, VY + 170f, 520f, 520f, 0.4f, WHITE);
        glow(232f, -104f, 46f, 0xFF7040, 0.18f);
        sprite(Art.MARS, PLAIN, 232f, -104f, 64f, 64f, 0f, WHITE);
        float mb = blinkPulse(lb + 0.4f, 1.3f);
        glow(238f, -98f, 3f + 5f * mb, 0xFFB070, 0.55f + 0.45f * mb);
        glow(mx, my, ms * 0.7f, 0xBFC8D8, 0.12f);
        sprite(Art.MOON, PLAIN, mx, my, ms, ms, 0f, WHITE);
        // the habitat: a breathable field around a blinking beacon
        glow(hbx, hby, 26f, 0x6AF0E0, 0.3f + 0.08f * (float) Math.sin(lb * 3f));
        float bl = blinkPulse(lb, 0.9f);
        glow(hbx, hby, 5f + 11f * bl, 0xC8FFE8, 0.6f + 0.4f * bl);
        rect(hbx - 1.2f, hby - 1.2f, 2.4f, 2.4f, WHITE);
        // our rocket heading out
        float rp = easeOutCubic(clamp01(lb / 4.5f));
        float sx0 = -VX - 30f, sy0 = 140f, ex = -128f, ey = 74f;
        float rx = lerp(sx0, ex, rp), ry = lerp(sy0, ey, rp);
        float heading = (float) Math.atan2(ey - sy0, ex - sx0);
        streak(lerp(sx0, rx, 0.4f), lerp(sy0, ry, 0.4f), rx, ry, 1.5f, 0xFFB070, 0.18f);
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(rx, ry);
        p.rotate(heading + HALF_PI);
        float fl = 0.85f + 0.3f * hash(frame * 5 + 2);
        spriteTL(Art.FLAME, ADD_TEX, -6f, 16f, 12f, 18f * fl, col(0xFFFFFF, 0.95f));
        glow(0f, 20f, 9f, 0xFFB45A, 0.6f);
        sprite(Art.ROCKET, PLAIN, 0f, 0f, 18f, 36f, 0f, WHITE);
        p.popMatrix();
        end();
    }

    // =========================================================================================== 45-52 impact

    private void sceneImpact(float lt) {
        float ia = IMPACT - SCENE_START[6];
        float post = lt - ia;
        float zoom = post < 0f ? 1f + 0.07f * smooth(lt / ia) : 1.07f - 0.1f * smooth(post / 4.8f);
        cam(zoom, 0f, 0f, 0f);
        float ex = 0f, ey = 38f, R = Math.min(150f, VY * 0.82f);
        float spin = 0.12f + lt * 0.01f, cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        // the impact point is the crater painted on the burning Earth, turning with it
        float ix = ex + (CRATER_X * cs - CRATER_Y * sn) * R, iy = ey + (CRATER_X * sn + CRATER_Y * cs) * R;
        if (hud) {
            if (post < 0f) impactAlert(lt, ia, ix, iy);
            return;
        }
        bgLayer(Art.STARS, 0.2f, 4f, 0.9f, 0xFFFFFF);
        float burn = smooth(range(post, 0.1f, 1.7f));
        beginWorld();
        glow(ex, ey, R * 1.28f, lerpColor(0x3A7BFF, 0xFF4A14, burn), 0.42f + 0.25f * burn);
        if (burn < 1f) sprite(Art.EARTH, PLAIN, ex, ey, 2f * R, 2f * R, spin, WHITE);
        if (burn > 0f) sprite(Art.EARTH_BURNING, PLAIN, ex, ey, 2f * R, 2f * R, spin, col(0xFFFFFF, burn));
        if (post < 0f) {
            // the fireball dives in from the corner, faster and hotter every moment
            float pr = (float) Math.pow(clamp01(lt / ia), 1.6);
            float sx0 = VX - 46f, sy0 = -VY + 26f;
            float fx = lerp(sx0, ix, pr), fy = lerp(sy0, iy, pr);
            float dx = ix - sx0, dy = iy - sy0, dl = (float) Math.sqrt(dx * dx + dy * dy);
            dx /= dl;
            dy /= dl;
            float tail = (float) Math.atan2(-dy, -dx);
            float heat = clamp01((pr - 0.5f) / 0.5f), s = 15f + 10f * pr;
            spriteFrom(Art.COMA, ADD_TEX, fx + dx * s * 0.2f, fy + dy * s * 0.2f, 120f + 300f * pr, 28f + 26f * pr, tail, col(0xFFFFFF, 0.95f));
            glow(fx, fy, s * (1.7f + 2.4f * heat), 0xFFC070, 0.55f + 0.4f * heat);
            sprite(Art.ASTEROID, PLAIN, fx, fy, s, s, lt * 3f, WHITE);
            if (heat > 0f) {
                glow(fx, fy, s * 0.9f, 0xFFFFFF, heat);
                glow(ix, iy, R * 0.28f * heat, 0xFFD090, 0.5f * heat);
            }
        } else {
            float fd = 1f - (float) Math.exp(-post * 2.2f), fade = 1f - range(post, 1.5f, 4.8f) * 0.7f;
            glow(ix, iy, R * 0.55f * fd, 0xFF8A2A, 0.85f * fade);
            glow(ix, iy, R * 0.22f * fd, 0xFFF0C0, 1f - range(post, 0f, 1.6f));
            if (post < 1.6f) ring(ix, iy, R * 1.9f * easeOutCubic(post / 1.6f), 0xFFF0D0, (1f - post / 1.6f) * 0.9f, true);
            if (post < 2.8f) ring(ix, iy, R * 2.6f * easeOutCubic(post / 2.8f), 0xFF7A30, (1f - post / 2.8f) * 0.6f, true);
            float base = (float) Math.atan2(iy - ey, ix - ex);
            if (post < 2f) {
                for (int i = 0; i < DEBRIS; i++) {
                    float an = base + DB_A[i], v = DB_V[i];
                    float cx = (float) Math.cos(an), cy = (float) Math.sin(an);
                    float px = ix + cx * v * post, py = iy + cy * v * post + 30f * post * post;
                    streak(px - cx * v * 0.06f, py - cy * v * 0.06f, px, py, 2f, 0xFFB050, 1f - post / 2f);
                }
            }
            glowN(ex, ey, R * 1.15f, 0x1A0A06, 0.3f * range(post, 0.5f, 3f));
        }
        end();
    }

    /** Before the impact: the alert, the last seconds counting down and a reticle on the impact point. */
    private void impactAlert(float lt, float ia, float ix, float iy) {
        float px = hx(ix, iy), py = hy(ix, iy), pulse = frac(lt * 2f);
        beginHud();
        ring(px, py, 10f + pulse * 18f, 0xFF2A1E, (1f - pulse) * 0.9f, true);
        brackets(px, py, 9f, 9f, 3.5f, 1f, col(0xFF3A2E, 0.95f));
        end();
        hudText(forecastImpact, px + 14f, py - 17f, 8.2f, 0f, col(0xFF6A5A, 1f), -1, true);
        float m = margin(), k = crispK(1f);
        boolean on = (((int) (lt * 4f)) & 1) == 0;
        float y = m + 16f * k;
        float kk = crispK(1.5f);
        float tw = font.width(impactAlert) * kk;
        if (on) {
            rect(CX - tw / 2f - 8f * k, y, tw + 16f * k, 12f * kk, col(0xD0141C, 0.9f));
            text(impactAlert, CX, y + 2f * kk, kk, col(0xFFFFFF, 1f), 0, false);
        }
        float rem = Math.max(0f, ia - lt);
        int cs = Math.round(rem * 100f);
        if (cs != cacheCount) {
            cacheCount = cs;
            countStr = "T-00:00:0" + cs / 100 + "." + (cs % 100 < 10 ? "0" : "") + cs % 100;
        }
        float kc = crispK(2f);
        text(countStr, CX, y + 12f * kk + 6f * k, kc, col(on ? 0xFF3A2E : 0xFFFFFF, 1f), 0, true);
    }

    // =========================================================================================== 52-60 title

    private void sceneTitle(float lt) {
        if (hud) return;
        cam(1f + 0.025f * lt / 8f, 0f, 0f, 0f);
        bgLayer(Art.STARS, 0.3f, 3f, 0.95f, 0xFFFFFF);
        bgLayer(Art.NEBULA, 0.4f, 6f, 0.4f, 0xFFFFFF);
        float li = range(lt, 0.25f, 0.9f), ie = easeOutCubic(li);
        float lw = Math.min(2f * VX * 0.84f, 600f), lh = lw / 4f, ly = -46f;
        float sc = 1.22f - 0.22f * ie;
        beginHud();
        sprite(Art.GLOW, ADD_TEX, 0f, VY + 30f, 3.4f * VX, 400f, 0f, col(0xFF4A14, 0.42f + 0.08f * (float) Math.sin(lt * 1.7f)));
        sprite(Art.GLOW, ADD_TEX, 0f, ly, lw * 1.25f, lh * 2.4f, 0f, col(0xFFB060, 0.22f * ie + 0.05f * (float) Math.sin(lt * 2f)));
        float split = (1f - ie) * 16f + (glitchAt(time, 7) > 0.3f ? 4f : 0f);
        if (present[Art.LOGO.ordinal()]) {
            if (split > 0.3f && li > 0f) {
                sprite(Art.LOGO, ADD_TEX, -split, ly, lw * sc, lh * sc, 0f, col(0xFF2020, 0.6f * li));
                sprite(Art.LOGO, ADD_TEX, split, ly, lw * sc, lh * sc, 0f, col(0x20E8FF, 0.5f * li));
            }
            sprite(Art.LOGO, PLAIN, 0f, ly, lw * sc, lh * sc, 0f, col(0xFFFFFF, li));
            if (lt > 1.5f && lt < 2.5f) {
                g.enableScissor((int) (-lw / 2f), (int) (ly - lh / 2f), (int) (lw / 2f), (int) (ly + lh / 2f));
                float swx = lerp(-lw / 2f - 60f, lw / 2f + 60f, easeInOut(lt - 1.5f));
                sprite(Art.GLOW, ADD_TEX, swx, ly, lh * 0.5f, lh * 2.6f, 0.35f, col(0xFFFFFF, 0.55f));
                g.disableScissor();
            }
        }
        end();
        if (!present[Art.LOGO.ordinal()] && li > 0f) hudText("HELIOSPHERE", 0f, ly - 18f * sc, 36f * sc, lw, col(0xFFE6B0, li), 0, true);

        float ca = range(lt, 0.9f, 1.4f);
        float subY = ly + lh / 2f + 12f - (1f - ca) * 6f;
        if (ca > 0f) {
            float half = spacedText(titleSub, 0f, subY, 20f, col(0xF2C46A, ca));
            beginHud();
            float lineW = 70f * easeOutCubic(range(lt, 1.0f, 1.6f));
            rect(-half - 14f - lineW, subY + 9f, lineW, 1f, col(0xF2C46A, 0.6f * ca));
            rect(half + 14f, subY + 9f, lineW, 1f, col(0xF2C46A, 0.6f * ca));
            end();
        }
        if (lt >= 1.9f) {
            String s = typed(tagline, (lt - 1.9f) * 14f);
            float k = crispK(11f / 9f * U);
            float full = font.width(tagline) * k;
            text(s, sx(0f) - full / 2f, sy(ly + lh / 2f + 46f), k, col(0xFFFFFF, 1f), -1, true);
        }
        if (lt >= 3.3f) {
            float pulse = 0.5f + 0.5f * (float) Math.cos((lt - 3.3f) * TAU);
            float pa = range(lt, 3.3f, 3.6f) * (0.55f + 0.45f * pulse);
            beginHud();
            glow(0f, ly + lh / 2f + 72f, 90f, 0xFF2010, 0.18f * pulse * range(lt, 3.3f, 3.6f));
            end();
            hudText(titleCountdown, 0f, ly + lh / 2f + 66f, 12f, 2f * VX - 40f, col(0xFF3A2E, pa), 0, true);
        }
        if (lt >= 4.2f) {
            float k = crispK(1f);
            float pa = range(lt, 4.2f, 4.6f) * (0.45f + 0.35f * (float) Math.sin(lt * 3f));
            text(anyKey, CX, H - 3f / gs - margin() * 0.5f - 12f * k, k, col(0xD0D0D0, pa), 0, true);
        }
    }

    /** Letter-spaced title text centred at x (HUD); returns its half width in design units. */
    private float spacedText(String s, float x, float y, float size, int argb) {
        float k = crispK(size / 9f * U);
        float spacing = size * 0.45f * U;
        int n = s.codePointCount(0, s.length());
        float total = font.width(s) * k + spacing * Math.max(0, n - 1);
        float cx = sx(x) - total / 2f, ty = sy(y);
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            String ch = s.substring(i, i + Character.charCount(cp));
            text(ch, cx, ty, k, argb, -1, true);
            cx += font.width(ch) * k + spacing;
            i += Character.charCount(cp);
        }
        return total / 2f / U;
    }

    // =========================================================================================== overlays

    private float glitchAt(float t, int scene) {
        float gl = 0f;
        for (float c : CUTS) {
            float d = t - c;
            if (d > -0.07f && d < 0.13f) gl = Math.max(gl, 1f - Math.abs(d - 0.02f) / 0.12f);
        }
        int slot = (int) (t / 0.45f);
        float st = slot * 0.45f + hash(slot * 7 + 1) * 0.32f, dur = 0.05f + hash(slot * 7 + 2) * 0.1f;
        if (hash(slot * 7 + 3) < GLITCH_RATE[scene] && t >= st && t < st + dur) gl = Math.max(gl, 0.55f + 0.4f * hash(slot * 7 + 4));
        if (t < 0.4f) gl = 0f;
        return clamp01(gl);
    }

    private void glitchBands(float gl) {
        if (gl <= 0.02f) return;
        int n = 1 + (int) (hash(frame * 3 + 11) * 3f);
        for (int i = 0; i < n; i++) {
            float y = hash(frame * 13 + i * 7) * H, h = (2f + hash(frame * 17 + i * 3) * 14f) * gl;
            if (present[Art.NOISE.ordinal()]) {
                float tex = Math.max(1f, 2f / gs);
                Matrix3x2fStack p = g.pose();
                p.pushMatrix();
                p.translate(0f, y);
                p.scale(tex, tex);
                int uw = (int) Math.ceil(W / tex), vh = Math.max(1, (int) Math.ceil(h / tex));
                g.blit(PLAIN, Art.NOISE.id, 0, 0, hash(frame * 23 + i) * 256f, hash(frame * 29 + i) * 256f, uw, vh, uw, vh, 256, 256, col(0xFFFFFF, 0.4f * gl));
                p.popMatrix();
            }
            float off = (hash(frame * 19 + i) - 0.5f) * 30f * gl;
            rect(off, y, W, Math.max(1f, h * 0.35f), col((i & 1) == 0 ? 0xFF2030 : 0x20E0FF, 0.3f * gl));
            rect(0f, y + h, W, Math.max(1f / gs, 0.5f), col(0xFFFFFF, 0.35f * gl));
        }
    }

    private void scanlines() {
        g.blit(PLAIN, SCANLINES, 0, 0, 0f, 0f, width, height, 1, Math.max(1, Math.round(height * gs)), 1, 4, col(0xFFFFFF, 0.5f));
    }

    private void vignette(int scene, float t) {
        g.blit(PLAIN, VIGNETTE, 0, 0, 0f, 0f, width, height, 128, 128, 128, 128, col(0x000000, 0.82f));
        float p = frac(t / 0.95f);
        float beat = (float) (Math.exp(-sq(p / 0.06f)) + Math.exp(-sq((p - 1f) / 0.06f)) + 0.55 * Math.exp(-sq((p - 0.22f) / 0.07f)));
        float red = URGENCY[scene] * (0.35f + 0.65f * Math.min(1f, beat)) * range(t, 0.3f, 0.7f);
        if (t > 45f && t < IMPACT) red = Math.min(1f, red + 0.4f * (t - 45f) / (IMPACT - 45f));
        g.blit(PLAIN, VIGNETTE, 0, 0, 0f, 0f, width, height, 128, 128, 128, 128, col(0xFF1A10, 0.55f * red));
    }

    private void flashes(float t) {
        float a = 0f;
        int c = 0xFFFFFF;
        for (int i = 0; i < CUTS.length; i++) {
            float d = t - CUTS[i];
            if (d >= 0f && d < 0.16f) {
                float f = 0.85f * (1f - d / 0.16f);
                if (f > a) {
                    a = f;
                    c = CUT_COLOR[i];
                }
            }
        }
        if (a > 0.01f) rect(0f, 0f, W, H, col(c, a));
        float d = t - IMPACT;
        if (d >= 0f) {
            float f = d < 0.14f ? 1f : (float) Math.exp(-(d - 0.14f) * 2.4f);
            if (f > 0.01f) rect(0f, 0f, W, H, col(lerpColor(0xFFFFFF, 0xFFE2B0, clamp01(d)), f));
        }
        float fo = range(t, 59.15f, 60f);
        if (fo > 0f) rect(0f, 0f, W, H, col(0x000000, fo));
    }

    private void drawSceneLabel(int scene, float lt) {
        if (scene >= SCENE_IDS.length) return;
        float a = scene == 0 ? range(lt, 0.4f, 0.7f) : range(lt, 0.15f, 0.45f);
        if (a <= 0f) return;
        float m = margin(), k = crispK(1f);
        boolean on = (((int) (time * 2.5f)) & 1) == 0;
        rect(m, m + k, 5f * k, 5f * k, col(0xFF2A20, on ? a : a * 0.25f));
        float ix = m + 9f * k;
        text(SCENE_INDEX[scene], ix, m, k, col(0xFF7A70, a), -1, true);
        float lx = ix + (font.width(SCENE_INDEX[scene]) + 6) * k;
        text(sceneLabel[scene], lx, m, k, col(0xFFF0F0, a), -1, true);
        float full = lx - m + font.width(sceneLabel[scene]) * k;
        rect(m, m + 11f * k, full * easeOutCubic(range(lt, 0.15f, 0.6f)), Math.max(1f / gs, 0.5f * k), col(0xFF2A20, 0.7f * a));
    }

    private void drawTimecode(int scene, float t) {
        if (scene >= SCENE_IDS.length) return;
        int sec = (int) t;
        if (sec != cacheSec) {
            cacheSec = sec;
            int s = 7 + sec, mm = 14 + s / 60;
            s %= 60;
            timecodeStr = live + "  2097-06-21 03:" + mm + ":" + (s < 10 ? "0" : "") + s;
        }
        float m = margin(), k = crispK(1f), a = range(t, 0.4f, 0.7f);
        if (a <= 0f) return;
        float tw = font.width(timecodeStr) * k;
        boolean on = (((int) (t * 2f)) & 1) == 0;
        rect(W - m - tw - 8f * k, m + 2f * k, 4f * k, 4f * k, col(0xFF2A20, (on ? 0.95f : 0.3f) * a));
        text(timecodeStr, W - m, m, k, col(0xD8D0D0, 0.85f * a), 1, true);
    }

    private void drawCaption(float t) {
        int c = -1;
        for (int i = 0; i < CAP_START.length; i++) {
            if (t >= CAP_START[i] && t < CAP_END[i]) {
                c = i;
                break;
            }
        }
        if (c < 0 || capLines[c] == null || capLines[c].length == 0) return;
        float a = range(t, CAP_START[c], CAP_START[c] + 0.15f) * (1f - range(t, CAP_END[c] - 0.3f, CAP_END[c]));
        if (a <= 0.01f) return;
        float k = capK, m = margin(), hk = crispK(1f);
        String[] lines = capLines[c];
        float lineH = 11f * k, padX = 7f * k, padY = 5f * k;
        float boxW = capMaxW[c] * k + 2f * padX, boxH = lines.length * lineH + 2f * padY - 2f * k;
        float bottom = H - 3f / gs - m * 0.5f - 14f * hk;
        float bx = CX - boxW / 2f, by = bottom - boxH;
        rect(bx, by, boxW, boxH, col(0x050102, 0.66f * a));
        rect(bx, by, Math.max(1f, 1.5f * k), boxH, col(0xE8352C, 0.95f * a));
        float cps = Math.max(16f, capChars[c] / 2.3f);
        int remaining = (int) ((t - CAP_START[c]) * cps);
        boolean typing = remaining < capChars[c];
        float y = by + padY;
        for (int i = 0; i < lines.length && remaining > 0; i++) {
            String line = lines[i];
            int n = line.codePointCount(0, line.length());
            String vis = remaining >= n ? line : line.substring(0, line.offsetByCodePoints(0, remaining));
            float x = CX - capLineW[c][i] * k / 2f;
            text(vis, x, y, k, col(0xF4F0F0, a), -1, true);
            if (typing && remaining <= n) rect(snap(x) + font.width(vis) * k + k, y, 5f * k, 8f * k, col(0xE8352C, a));
            remaining -= n;
            y += lineH;
        }
    }

    private void drawControls(float t) {
        float bh = 3f / gs;
        rect(0f, H - bh, W, bh, col(0xFFFFFF, 0.14f));
        rect(0f, H - bh, W * clamp01(t / DURATION), bh, col(0xE8352C, 0.95f));
        if (t < SKIP_GRACE || t >= ANY_KEY_FROM) return;
        float k = crispK(1f), m = margin();
        float a = 0.8f * range(t, SKIP_GRACE, SKIP_GRACE + 0.4f);
        float y = H - bh - m * 0.5f - 10f * k;
        float ws = font.width(skipText) * k, we = font.width("ESC") * k;
        float bx = W - m - ws - 6f * k - we - 6f * k;
        rect(bx, y - 2f * k, we + 6f * k, 12f * k, col(0x000000, 0.5f * a));
        rect(bx, y - 2f * k, we + 6f * k, Math.max(1f / gs, 0.5f * k), col(0xFFFFFF, 0.4f * a));
        rect(bx, y + 10f * k - Math.max(1f / gs, 0.5f * k), we + 6f * k, Math.max(1f / gs, 0.5f * k), col(0xFFFFFF, 0.4f * a));
        text("ESC", bx + 3f * k, y, k, col(0xFFFFFF, a), -1, false);
        text(skipText, W - m, y, k, col(0xD8D8D8, a), 1, true);
    }

    // =========================================================================================== layers and effects

    /** A full-screen tiling layer (stars, nebula) with parallax: it follows the camera by {@code depth}. */
    private void bgLayer(Art art, float depth, float scroll, float alpha, int tint) {
        if (alpha <= 0.01f) return;
        Matrix3x2fStack p = g.pose();
        beginHud();
        float z = 1f + (camZoom - 1f) * depth;
        p.scale(z, z);
        if (camRoll != 0f) p.rotate(camRoll * depth);
        p.translate(-camPanX * depth, -camPanY * depth);
        float hw = VX * 1.3f + 30f, hh = VY * 1.3f + 30f;
        if (!present[art.ordinal()]) {
            if (art == Art.STARS) {
                for (int i = 0; i < FSTARS; i++) {
                    float s = 0.6f + FS_B[i] * 1.2f;
                    rect(FS_X[i] * hw, FS_Y[i] * hh, s, s, col(tint, alpha * (0.3f + 0.7f * FS_B[i])));
                }
            }
            end();
            return;
        }
        float ts = 2f * hh / art.h;
        int uw = (int) Math.ceil(2f * hw / ts);
        float u0 = (time * scroll) % art.w;
        p.translate(-hw, -hh);
        p.scale(ts, ts);
        g.blit(PLAIN, art.id, 0, 0, u0, 0f, uw, art.h, uw, art.h, art.w, art.h, col(tint, alpha));
        end();
    }

    /** Full-screen TV static from the noise texture, re-rolled 24 times a second (screen space). */
    private void noise(float alpha, int tint) {
        if (alpha <= 0.01f) return;
        int f = (int) (time * 24f);
        if (!present[Art.NOISE.ordinal()]) {
            for (int i = 0; i < 120; i++) {
                float s = 1f + hash(f * 131 + i * 3) * 3f;
                rect(hash(f * 131 + i * 3 + 1) * W, hash(f * 131 + i * 3 + 2) * H, s, s, col(tint, alpha));
            }
            return;
        }
        float tex = Math.max(1f / gs, 2f / gs);
        int uw = (int) Math.ceil(W / tex), vh = (int) Math.ceil(H / tex);
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.scale(tex, tex);
        g.blit(PLAIN, Art.NOISE.id, 0, 0, hash(f * 3 + 1) * 256f, hash(f * 3 + 2) * 256f, uw, vh, uw, vh, 256, 256, col(tint, alpha));
        p.popMatrix();
    }

    /** A big number with a smaller unit after it, with optional RGB split and sliced glitch (HUD position). */
    private void bigCounter(String digits, String unit, float x, float y, float size, float a, float split, float slices, int color, int align) {
        if (a <= 0.01f) return;
        float k = crispK(size / 9f * U), ku = crispK(size * 0.34f / 9f * U);
        float wn = font.width(digits) * k, wu = font.width(unit) * ku, gap = 4f * U;
        float total = wn + gap + wu;
        float x0 = align < 0 ? sx(x) : sx(x) - total / 2f, y0 = sy(y);
        float off = split * U;
        if (off > 0.3f) {
            text(digits, x0 - off, y0, k, col(0xFF2020, 0.55f * a), -1, false);
            text(digits, x0 + off, y0, k, col(0x20E0FF, 0.45f * a), -1, false);
        }
        // a thin dark edge instead of the vanilla shadow, whose offset would grow with the glyphs
        float sh = Math.max(1f / gs, 0.9f * U);
        text(digits, x0 + sh, y0 + sh, k, col(0x200404, 0.7f * a), -1, false);
        if (slices > 0f) {
            float hgt = 8f * k;
            for (int i = 0; i < 3; i++) {
                int ys0 = (int) Math.floor(y0 + i * hgt / 3f), ys1 = (int) Math.ceil(y0 + (i + 1) * hgt / 3f);
                if (i == 2) ys1 = (int) Math.ceil(y0 + 9f * k);
                g.enableScissor(0, ys0, width, ys1);
                float dx = (hash(frame * 17 + i * 5) - 0.5f) * slices * 14f * U;
                text(digits, x0 + dx, y0, k, col(color, a), -1, false);
                g.disableScissor();
            }
        } else text(digits, x0, y0, k, col(color, a), -1, false);
        text(unit, x0 + wn + gap, y0 + 7f * k - 7f * ku, ku, col(0xFFB0A8, a), -1, true);
    }

    // =========================================================================================== camera and spaces

    private void cam(float zoom, float panX, float panY, float roll) {
        camZoom = zoom;
        camPanX = panX;
        camPanY = panY;
        camRoll = roll;
        cosR = (float) Math.cos(roll);
        sinR = (float) Math.sin(roll);
    }

    /** World position to HUD position (design units from the screen centre). */
    private float hx(float x, float y) {
        return ((x - camPanX) * cosR - (y - camPanY) * sinR) * camZoom;
    }

    private float hy(float x, float y) {
        return ((x - camPanX) * sinR + (y - camPanY) * cosR) * camZoom;
    }

    /** HUD position to screen position (GUI pixels). */
    private float sx(float x) {
        return CX + (x + shX) * U;
    }

    private float sy(float y) {
        return CY + (y + shY) * U;
    }

    private void beginWorld() {
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(CX + shX * U, CY + shY * U);
        p.scale(U * camZoom, U * camZoom);
        if (camRoll != 0f) p.rotate(camRoll);
        p.translate(-camPanX, -camPanY);
    }

    private void beginHud() {
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(CX + shX * U, CY + shY * U);
        p.scale(U, U);
    }

    private void end() {
        g.pose().popMatrix();
    }

    // =========================================================================================== primitives (current pose units)

    private void rect(float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w <= 0f || h <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(w, h);
        g.fill(0, 0, 1, 1, argb);
        p.popMatrix();
    }

    private void rectAdd(float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w <= 0f || h <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(w, h);
        g.fill(ADD_FILL, 0, 0, 1, 1, argb);
        p.popMatrix();
    }

    private void gradV(float x, float y, float w, float h, int top, int bottom) {
        if (((top | bottom) >>> 24) == 0 || w <= 0f || h <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(w, h);
        g.fillGradient(0, 0, 1, 1, top, bottom);
        p.popMatrix();
    }

    private void gradH(float x, float y, float w, float h, int left, int right) {
        if (((left | right) >>> 24) == 0 || w <= 0f || h <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y + h);
        p.rotate(-HALF_PI);
        p.scale(h, w);
        g.fillGradient(0, 0, 1, 1, left, right);
        p.popMatrix();
    }

    private void line(float x0, float y0, float x1, float y1, float th, int argb, boolean additive) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f || (argb >>> 24) == 0) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x0, y0);
        p.rotate((float) Math.atan2(dy, dx));
        p.translate(0f, -th / 2f);
        p.scale(len, th);
        g.fill(additive ? ADD_FILL : RenderPipelines.GUI, 0, 0, 1, 1, argb);
        p.popMatrix();
    }

    private void brackets(float cx, float cy, float hw, float hh, float len, float th, int argb) {
        rect(cx - hw, cy - hh, len, th, argb);
        rect(cx - hw, cy - hh, th, len, argb);
        rect(cx + hw - len, cy - hh, len, th, argb);
        rect(cx + hw - th, cy - hh, th, len, argb);
        rect(cx - hw, cy + hh - th, len, th, argb);
        rect(cx - hw, cy + hh - len, th, len, argb);
        rect(cx + hw - len, cy + hh - th, len, th, argb);
        rect(cx + hw - th, cy + hh - len, th, len, argb);
    }

    private void dashedCircle(float cx, float cy, float r, float rot, float th, int argb) {
        int n = 24;
        for (int i = 0; i < n; i += 1) {
            if ((i & 3) == 3) continue;
            float a0 = rot + i * TAU / n, a1 = a0 + TAU / n * 0.6f;
            line(cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0), cx + r * (float) Math.cos(a1), cy + r * (float) Math.sin(a1), th, argb, false);
        }
    }

    /** An additive soft glow of radius r (the glow texture, tinted). */
    private void glow(float x, float y, float r, int rgb, float intensity) {
        if (intensity <= 0.01f || r <= 0.05f) return;
        sprite(Art.GLOW, ADD_TEX, x, y, 2f * r, 2f * r, 0f, col(rgb, intensity));
    }

    /**
     * A normally blended soft blob of about radius r: smoke, haze, stains. The glow texture falls off like a
     * gaussian (half alpha at a sixth of its radius), so the sprite is drawn larger to give the blob body.
     */
    private void glowN(float x, float y, float r, int rgb, float alpha) {
        if (alpha <= 0.01f || r <= 0.05f) return;
        sprite(Art.GLOW, PLAIN, x, y, 4.4f * r, 4.4f * r, 0f, col(rgb, alpha));
    }

    /** The glow stretched along a segment: tracers, trails, beams. */
    private void streak(float x0, float y0, float x1, float y1, float th, int rgb, float intensity) {
        if (intensity <= 0.01f) return;
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        sprite(Art.GLOW, ADD_TEX, (x0 + x1) / 2f, (y0 + y1) / 2f, len + th, th, (float) Math.atan2(dy, dx), col(rgb, intensity));
    }

    /** A ring whose bright line has radius r (the shockwave texture peaks at 0.81 of its half size). */
    private void ring(float x, float y, float r, int rgb, float intensity, boolean additive) {
        if (intensity <= 0.01f || r <= 0.2f) return;
        sprite(Art.SHOCKWAVE, additive ? ADD_TEX : PLAIN, x, y, 2.47f * r, 2.47f * r, 0f, col(rgb, intensity));
    }

    /** A sprite centred at (cx, cy) with size (w, h) and rotation. */
    private void sprite(Art a, RenderPipeline pipe, float cx, float cy, float w, float h, float rot, int argb) {
        if ((argb >>> 24) == 0 || w <= 0f || h <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(cx, cy);
        if (rot != 0f) p.rotate(rot);
        if (present[a.ordinal()]) {
            p.scale(w / a.w, h / a.h);
            g.blit(pipe, a.id, -a.w / 2, -a.h / 2, 0f, 0f, a.w, a.h, a.w, a.h, argb);
        } else missing(a, pipe, -w / 2f, -h / 2f, w, h, argb);
        p.popMatrix();
    }

    /** A sprite with its top-left corner at (x, y). */
    private void spriteTL(Art a, RenderPipeline pipe, float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w <= 0f || h <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        if (present[a.ordinal()]) {
            p.scale(w / a.w, h / a.h);
            g.blit(pipe, a.id, 0, 0, 0f, 0f, a.w, a.h, a.w, a.h, argb);
        } else missing(a, pipe, 0f, 0f, w, h, argb);
        p.popMatrix();
    }

    /** A sprite anchored at the middle of its left edge, pointing along {@code rot} (the coma: head left, tail right). */
    private void spriteFrom(Art a, RenderPipeline pipe, float x, float y, float len, float th, float rot, int argb) {
        if ((argb >>> 24) == 0 || len <= 0f || th <= 0f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.rotate(rot);
        if (present[a.ordinal()]) {
            p.scale(len / a.w, th / a.h);
            g.blit(pipe, a.id, 0, -a.h / 2, 0f, 0f, a.w, a.h, a.w, a.h, argb);
        } else missing(a, pipe, 0f, -th / 2f, len, th, argb);
        p.popMatrix();
    }

    /** Stand-in shapes when an art file is missing, so the cinematic still reads (local box x, y, w, h). */
    private void missing(Art a, RenderPipeline pipe, float x, float y, float w, float h, int argb) {
        float al = (argb >>> 24) / 255f;
        boolean add = pipe == ADD_TEX;
        float cx = x + w / 2f, cy = y + h / 2f, r = Math.min(w, h) / 2f;
        switch (a) {
            case GLOW -> {
                int c = col(argb, al * 0.45f);
                if (add) rectAdd(cx - w * 0.2f, cy - h * 0.2f, w * 0.4f, h * 0.4f, c);
                else rect(cx - w * 0.2f, cy - h * 0.2f, w * 0.4f, h * 0.4f, c);
            }
            case SHOCKWAVE -> {
                for (int i = 0; i < 16; i++) {
                    float an = i * TAU / 16f, d = Math.max(0.8f, r * 0.04f);
                    rect(cx + (float) Math.cos(an) * r * 0.9f - d, cy + (float) Math.sin(an) * r * 0.9f - d, 2f * d, 2f * d, argb);
                }
            }
            case FLAME, COMA -> {
                int c = col(0xFFA040, al * 0.8f);
                if (a == Art.FLAME) rectAdd(x + w * 0.3f, y, w * 0.4f, h * 0.8f, c);
                else rectAdd(x, y + h * 0.3f, w * 0.7f, h * 0.4f, c);
            }
            case ROBOTS, BASE -> {
                if (add) return;
                int c = col(a == Art.ROBOTS ? 0x0A0606 : 0x080A10, al);
                for (int i = 0; i < 12; i++) {
                    float bw = w / 12f, bh = h * (0.3f + 0.6f * hash(i * 7 + (a == Art.ROBOTS ? 1 : 2)));
                    rect(x + i * bw + bw * 0.15f, y + h - bh, bw * 0.7f, bh, c);
                }
            }
            case ROCKET -> {
                if (add) return;
                rect(x + w * 0.3f, y + h * 0.15f, w * 0.4f, h * 0.8f, col(0xE8E8EC, al));
                rect(x + w * 0.3f, y + h * 0.55f, w * 0.4f, h * 0.06f, col(0xC03020, al));
                rect(x + w * 0.4f, y + h * 0.04f, w * 0.2f, h * 0.12f, col(0xD0D0D8, al));
            }
            case EARTH, EARTH_BURNING, MOON, MARS, ASTEROID -> {
                if (add) return;
                int c = switch (a) {
                    case EARTH -> 0x2E66C4;
                    case EARTH_BURNING -> 0x8A2A10;
                    case MOON -> 0x9A9AA0;
                    case MARS -> 0xB85A30;
                    default -> 0x5A5048;
                };
                rect(cx - r, cy - r * 0.42f, 2f * r, r * 0.84f, col(c, al));
                rect(cx - r * 0.42f, cy - r, r * 0.84f, r * 0.58f, col(c, al));
                rect(cx - r * 0.42f, cy + r * 0.42f, r * 0.84f, r * 0.58f, col(c, al));
                rect(cx - r * 0.78f, cy - r * 0.78f, r * 0.36f, r * 0.36f, col(c, al));
                rect(cx + r * 0.42f, cy - r * 0.78f, r * 0.36f, r * 0.36f, col(c, al));
                rect(cx - r * 0.78f, cy + r * 0.42f, r * 0.36f, r * 0.36f, col(c, al));
                rect(cx + r * 0.42f, cy + r * 0.42f, r * 0.36f, r * 0.36f, col(c, al));
            }
            default -> {
            }
        }
    }

    // =========================================================================================== text (screen space)

    /** Draws text with its top at (x, y); k is GUI px per font px; align -1 left, 0 centre, 1 right. */
    private void text(String s, float x, float y, float k, int argb, int align, boolean shadow) {
        if (s == null || s.isEmpty() || (argb >>> 24) < 6) return;
        float w = font.width(s) * k;
        float x0 = align < 0 ? x : align == 0 ? x - w / 2f : x - w;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(snap(x0), snap(y));
        p.scale(k, k);
        g.drawString(font, s, 0, 0, argb, shadow);
        p.popMatrix();
    }

    private void ctext(Component c, int fontW, float x, float y, float k, int argb, int align, boolean shadow) {
        if ((argb >>> 24) < 6) return;
        float w = fontW * k;
        float x0 = align < 0 ? x : align == 0 ? x - w / 2f : x - w;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(snap(x0), snap(y));
        p.scale(k, k);
        g.drawString(font, c, 0, 0, argb, shadow);
        p.popMatrix();
    }

    /** Text at a HUD position, {@code size} design units per font line, shrunk to {@code maxW} design units when given. */
    private void hudText(String s, float x, float y, float size, float maxW, int argb, int align, boolean shadow) {
        if (s.isEmpty()) return;
        float kdu = size / 9f;
        int fw = font.width(s);
        boolean fit = maxW > 0f && fw * kdu > maxW;
        if (fit) kdu = maxW / fw;
        // in small windows the design scale would shrink labels below legibility: keep them at least 3/4 of GUI size
        float k = fit ? crispDown(kdu * U) : Math.max(crispK(kdu * U), crispK(0.75f));
        text(s, sx(x), sy(y), k, argb, align, shadow);
    }

    private static String typed(String s, float chars) {
        int n = (int) chars;
        int total = s.codePointCount(0, s.length());
        if (n >= total) return s;
        if (n <= 0) return "";
        return s.substring(0, s.offsetByCodePoints(0, n));
    }

    // =========================================================================================== math

    private static float hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7fffffff) / 2147483648f;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static float range(float v, float a, float b) {
        return clamp01((v - a) / (b - a));
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float wrap(float v, float lo, float hi) {
        float span = hi - lo;
        return lo + (v - lo - span * (float) Math.floor((v - lo) / span));
    }

    private static float sq(float v) {
        return v * v;
    }

    private static float smooth(float v) {
        v = clamp01(v);
        return v * v * (3f - 2f * v);
    }

    private static float easeOutCubic(float v) {
        v = 1f - clamp01(v);
        return 1f - v * v * v;
    }

    private static float easeInOut(float v) {
        v = clamp01(v);
        return v < 0.5f ? 4f * v * v * v : 1f - (float) Math.pow(-2f * v + 2f, 3) / 2f;
    }

    private static float easeOutBack(float v) {
        v = clamp01(v);
        float c1 = 1.70158f, c3 = c1 + 1f, x = v - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    /** Sharp periodic flashes: 1 at the start of each period, decaying fast. */
    private static float blinkPulse(float t, float period) {
        float p = frac(t / period);
        return (float) Math.exp(-p * 9f);
    }

    private static int col(int rgb, float a) {
        return Math.round(clamp01(a) * 255f) << 24 | (rgb & 0xFFFFFF);
    }

    private static int lerpColor(int a, int b, float f) {
        f = clamp01(f);
        int r = Math.round(lerp((a >> 16) & 255, (b >> 16) & 255, f));
        int gg = Math.round(lerp((a >> 8) & 255, (b >> 8) & 255, f));
        int bb = Math.round(lerp(a & 255, b & 255, f));
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }
}
