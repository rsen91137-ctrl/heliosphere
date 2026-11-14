package com.heliosphere.planets.tech.client;

import com.heliosphere.planets.tech.TechMob;
import com.heliosphere.planets.tech.TechProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws every Earth security machine and their rounds. Each machine has its own model, built here from boxes,
 * and its own paint scheme; the surface detail comes from one greyscale kit atlas (tools/gen/robot_kit.py)
 * tinted per part, with a second, full-bright pass for lenses, visors, thrusters and energy cells.
 *
 * <p>Models are built facing +Z with the feet at the origin, in blocks.
 */
public final class TechRenderer<T extends Entity> extends EntityRenderer<T, TechRenderer.State> {
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("heliosphere", "textures/entity/tech_kit.png");

    public TechRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    public static final class State extends EntityRenderState {
        float age, yaw, width, height, speed, hurt;
        String id = "";
        int mode;
        float pitch, travelYaw;
        /** Strike aircraft: nose up (radians) from the climb, and bank into the turn. */
        float climb, bank;
        boolean aircraft;
    }

    private static boolean aircraft(String id) { return id.equals("strike_drone") || id.equals("strike_jet"); }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(T e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.age = e.tickCount + partial;
        s.yaw = e.getYRot();                                    // machines turn their whole body (head yaw), not a separate torso
        s.width = e.getBbWidth();
        s.height = e.getBbHeight();
        Vec3 v = e.getDeltaMovement();
        s.speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        s.hurt = e instanceof LivingEntity le && le.hurtTime > 0 ? le.hurtTime / 10f : 0;
        if (e instanceof TechMob mob && mob.role() != null) s.id = mob.role().id();
        s.aircraft = aircraft(s.id);
        if (s.aircraft) {
            s.yaw = net.minecraft.util.Mth.rotLerp(partial, e.yRotO, e.getYRot());
            double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
            double h = Math.sqrt(dx * dx + dz * dz);
            s.climb = h < 0.05 ? 0 : (float) net.minecraft.util.Mth.clamp(Math.atan2(dy, h), -0.6, 0.6);
            s.bank = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(e.getYRot() - e.yRotO) * 0.16f, -0.95f, 0.95f);
            s.speed = (float) h;
        }
        else if (e instanceof TechProjectile p) {
            s.id = "projectile";
            s.mode = p.mode();
            s.travelYaw = (float) Math.atan2(v.x, v.z);
            s.pitch = (float) Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z));
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(T e) { return e.getBoundingBox().inflate(2.5); }

    /** Strike aircraft are drawn as far as the server sends them (the size rule would hide them past ~145 blocks). */
    @Override
    public boolean shouldRender(T e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (e instanceof TechMob mob && mob.role() != null && aircraft(mob.role().id()))
            return e.distanceToSqr(x, y, z) < 400 * 400 && frustum.isVisible(getBoundingBoxForCulling(e).inflate(0.5));
        return super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        if (s.id.equals("projectile")) {
            pose.mulPose(new Quaternionf().rotateY(s.travelYaw).rotateX(-s.pitch));
        } else if (s.aircraft) {
            pose.translate(0, s.height * 0.5, 0);
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.yaw)).rotateX(-s.climb).rotateZ(s.bank));
        } else {
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-s.yaw)));
        }
        Paint paint = Paint.of(s.id);
        boolean ghost = s.isInvisible;
        if (!ghost) {
            // lights are drawn solid at full brightness: a depth-writing pass cannot be painted over by the body
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(TEX), (p, vc) -> build(new Mesh(p, vc, s.lightCoords, false, paint, s), s));
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(TEX), (p, vc) -> build(new Mesh(p, vc, 0xF000F0, true, paint, s), s));
        } else {
            out.submitCustomGeometry(pose, RenderTypes.eyes(TEX), (p, vc) -> build(new Mesh(p, vc, 0xF000F0, true, paint, s), s));   // a cloaked stalker: only a shimmer
        }
        pose.popPose();
        super.submit(s, pose, out, camera);
    }

    // ---- paint ----------------------------------------------------------------------------------------------

    /** Colours of one machine: main armour, secondary, accent and the colour of its lights. */
    record Paint(int primary, int secondary, int accent, int glow) {
        static Paint of(String id) {
            return switch (id) {
                case "patrol_drone" -> new Paint(0xD8DCE0, 0x40454C, 0xF07A20, 0x40E0FF);
                case "assault_drone" -> new Paint(0x7C8450, 0x2E3226, 0xE8C030, 0xFF6A30);
                case "siege_drone" -> new Paint(0xC8B080, 0x4A3C2A, 0xC03028, 0xFFB040);
                case "missile_drone" -> new Paint(0x34486C, 0x30343A, 0xE8E8E8, 0xFF3838);
                case "hunter_drone" -> new Paint(0x2A2C30, 0x18191C, 0xB01828, 0xFF2030);
                case "security_walker" -> new Paint(0xE6E8EC, 0x26304A, 0x2860D0, 0x50A0FF);
                case "repair_automaton" -> new Paint(0xE8C430, 0x3A3C40, 0xF0F0F0, 0x50FF80);
                case "arc_hound" -> new Paint(0x5A6068, 0x202226, 0x30C8E0, 0x60F0FF);
                case "emp_spider" -> new Paint(0x6A5E7C, 0x1E1A24, 0x9A50E0, 0xC070FF);
                case "siege_walker" -> new Paint(0x5A6440, 0x22261C, 0xE8B420, 0xFF8A20);
                case "rail_sentinel" -> new Paint(0xEEF0F2, 0x34383E, 0x30C0E0, 0xA8F4FF);
                case "swarm_drone" -> new Paint(0xC02A20, 0x1C1C1E, 0xF0D020, 0xFF3020);
                case "mortar_walker" -> new Paint(0xC4A874, 0x585A3A, 0xE07020, 0xFFB040);
                case "flame_walker" -> new Paint(0x3A3430, 0x7A3C1E, 0xE87018, 0xFF7A18);
                case "shield_bearer" -> new Paint(0x6A88B0, 0xE0E6EE, 0x30B0E0, 0x70C8FF);
                case "laser_drone" -> new Paint(0xF0F0F0, 0x1A1A1C, 0xD02028, 0xFF2838);
                case "stalker" -> new Paint(0x5C6470, 0x23262C, 0x9CC8E8, 0x9FD8FF);
                case "titan_walker" -> new Paint(0x4A4E56, 0x18191C, 0xD8A030, 0xFF8A28);
                case "plasma_drone" -> new Paint(0x2E3A4A, 0x101418, 0x40D8FF, 0x78E6FF);
                case "graviton_walker" -> new Paint(0x3A3048, 0x141018, 0x9050E0, 0xB070FF);
                case "phase_stalker" -> new Paint(0x4A4258, 0x18141E, 0xC080FF, 0xD8A0FF);
                case "fortress_overseer" -> new Paint(0x2A2E36, 0x0E1014, 0xD02030, 0xFF3040);
                case "hive_carrier" -> new Paint(0x3C4434, 0x161A12, 0xE0B020, 0xFFC840);
                case "colossus" -> new Paint(0x1E1E22, 0x0A0A0C, 0xE05010, 0xFF5018);
                case "drill_walker" -> new Paint(0xD0A032, 0x2E2E32, 0xDADDE0, 0xFF8A20);
                case "strike_drone" -> new Paint(0x9AA0A6, 0x3A3E44, 0xD8DCE0, 0xFF4030);
                case "strike_jet" -> new Paint(0x4E565E, 0x23272C, 0xC83A28, 0xFF8A30);
                default -> new Paint(0xA0A4A8, 0x303236, 0xE0A020, 0xFF6040);
            };
        }
    }

    /** Atlas tiles (tools/gen/robot_kit.py). */
    static final int PLATE = 0, HEAVY = 1, CARBON = 2, VENT = 3, HAZARD = 4, JOINT = 5, TREAD = 6, CABLE = 7,
            LENS = 8, EMIT = 9, BARREL = 10, DECAL = 11, ROTOR = 12, THRUST = 13, DARK = 14, MESH = 15, VISOR = 16, LAMP = 17;

    /** Material slots a box can be painted with. */
    enum Slot { PRIMARY, SECONDARY, ACCENT, METAL, GLOW, WHITE }

    // ---- mesh -----------------------------------------------------------------------------------------------

    /** Builds boxes into one of the two passes; glow parts only go to the bright pass, everything else only to the solid one. */
    static final class Mesh {
        final PoseStack.Pose pose;
        final VertexConsumer out;
        final int light;
        final boolean bright;
        final Paint paint;
        final State st;
        final Deque<Matrix4f> stack = new ArrayDeque<>();
        Matrix4f m = new Matrix4f();
        private final Vector3f tmp = new Vector3f();

        Mesh(PoseStack.Pose pose, VertexConsumer out, int light, boolean bright, Paint paint, State st) {
            this.pose = pose; this.out = out; this.light = light; this.bright = bright; this.paint = paint; this.st = st;
        }

        void push() { stack.push(new Matrix4f(m)); }
        void pop() { m = stack.pop(); }
        void at(double x, double y, double z) { m.translate((float) x, (float) y, (float) z); }
        void rx(double a) { m.rotateX((float) a); }
        void ry(double a) { m.rotateY((float) a); }
        void rz(double a) { m.rotateZ((float) a); }

        private int colour(Slot slot) {
            int c = switch (slot) {
                case PRIMARY -> paint.primary; case SECONDARY -> paint.secondary; case ACCENT -> paint.accent;
                case METAL -> 0x8A8E94; case GLOW -> paint.glow; case WHITE -> 0xFFFFFF;
            };
            if (!bright && st.hurt > 0) {                                     // flash red when hit
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                float k = st.hurt * 0.6f;
                c = (int) (r + (255 - r) * k) << 16 | (int) (g * (1 - k)) << 8 | (int) (b * (1 - k));
            }
            return 0xFF000000 | c;
        }

        /** A box from (x0,y0,z0) to (x1,y1,z1) in the current frame. */
        void box(double x0, double y0, double z0, double x1, double y1, double z1, int tile, Slot slot) {
            boolean glow = slot == Slot.GLOW;
            if (glow != bright) return;
            int c = colour(slot);
            float u0 = (tile % 8) / 8f, v0 = (tile / 8) / 8f, s = 1 / 8f;
            float w = (float) Math.abs(x1 - x0), h = (float) Math.abs(y1 - y0), d = (float) Math.abs(z1 - z0);
            // each face takes a window of the tile as large as the face, so detail keeps one scale
            face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, u0, v0, s * Math.min(1, w), s * Math.min(1, h), c);   // front +Z
            face(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, u0, v0, s * Math.min(1, w), s * Math.min(1, h), c); // back
            face(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, u0, v0, s * Math.min(1, d), s * Math.min(1, h), c); // left
            face(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, u0, v0, s * Math.min(1, d), s * Math.min(1, h), c);  // right
            face(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, u0, v0, s * Math.min(1, w), s * Math.min(1, d), c);  // top
            face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u0, v0, s * Math.min(1, w), s * Math.min(1, d), c); // bottom
        }

        /** Box centred on (cx, cy, cz). */
        void cbox(double cx, double cy, double cz, double w, double h, double d, int tile, Slot slot) {
            box(cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, tile, slot);
        }

        /** A flat quad in the XZ plane (rotor discs, thruster rings), double-sided. */
        void disc(double cx, double cy, double cz, double r, int tile, Slot slot) {
            if ((slot == Slot.GLOW) != bright) return;
            int c = colour(slot);
            float u0 = (tile % 8) / 8f, v0 = (tile / 8) / 8f, s = 1 / 8f;
            face(cx - r, cy, cz - r, cx + r, cy, cz - r, cx + r, cy, cz + r, cx - r, cy, cz + r, 0, 1, 0, u0, v0, s, s, c);
            face(cx - r, cy, cz + r, cx + r, cy, cz + r, cx + r, cy, cz - r, cx - r, cy, cz - r, 0, -1, 0, u0, v0, s, s, c);
        }

        /** A flat quad facing +Z (lenses, visors, nozzle glows). */
        void plate(double cx, double cy, double cz, double w, double h, int tile, Slot slot) {
            if ((slot == Slot.GLOW) != bright) return;
            int c = colour(slot);
            float u0 = (tile % 8) / 8f, v0 = (tile / 8) / 8f, s = 1 / 8f;
            double z = cz + 0.008;                                            // stand clear of the face behind it
            face(cx - w / 2, cy - h / 2, z, cx + w / 2, cy - h / 2, z, cx + w / 2, cy + h / 2, z, cx - w / 2, cy + h / 2, z, 0, 0, 1, u0, v0, s, s, c);
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
            Vector3f n = m.transformDirection(nx, ny, nz, new Vector3f()).normalize();
            out.addVertex(pose, tmp.x, tmp.y, tmp.z).setColor(c).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
        }
    }

    // ---- models ---------------------------------------------------------------------------------------------

    static void build(Mesh m, State s) {
        float t = s.age;
        switch (s.id) {
            case "projectile" -> projectile(m, s);
            case "patrol_drone" -> quadcopter(m, t, 0.55, 0.22, 0.6, 0.38, false);
            case "assault_drone" -> { quadcopter(m, t, 0.7, 0.28, 0.75, 0.46, false); sideGuns(m, 0.5, 0.36, 0.55); }
            case "siege_drone" -> siegeDrone(m, t);
            case "missile_drone" -> missileDrone(m, t);
            case "hunter_drone" -> hunter(m, t);
            case "swarm_drone" -> swarm(m, t);
            case "laser_drone" -> lanceDrone(m, t);
            case "security_walker" -> humanoid(m, s, 1.0, false);
            case "stalker" -> stalker(m, s);
            case "flame_walker" -> flameWalker(m, s);
            case "siege_walker" -> heavyWalker(m, s, 1.0, "cannon");
            case "rail_sentinel" -> heavyWalker(m, s, 0.95, "rail");
            case "mortar_walker" -> heavyWalker(m, s, 0.72, "mortar");
            case "titan_walker" -> titan(m, s);
            case "plasma_drone" -> { hunter(m, t); plasmaOrbs(m, t); }
            case "graviton_walker" -> { heavyWalker(m, s, 1.15, "none"); gravRing(m, s); }
            case "phase_stalker" -> { stalker(m, s); phaseShimmer(m, s); }
            case "fortress_overseer" -> overseer(m, s);
            case "hive_carrier" -> carrier(m, t);
            case "colossus" -> colossus(m, s);
            case "arc_hound" -> hound(m, s);
            case "emp_spider" -> spider(m, s);
            case "repair_automaton" -> tracked(m, s, false);
            case "shield_bearer" -> tracked(m, s, true);
            case "drill_walker" -> drillWalker(m, s);
            case "strike_drone" -> strikeDrone(m, s);
            case "strike_jet" -> strikeJet(m, s);
            default -> m.cbox(0, s.height / 2, 0, s.width, s.height, s.width, PLATE, Slot.PRIMARY);
        }
    }

    /** Rockbore drill walker: a tracked mining machine with a spinning cone drill on its nose. */
    private static void drillWalker(Mesh m, State s) {
        float t = s.age;
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 0.68, 0.32, -0.05, 0.38, 0.64, 2.0, TREAD, Slot.SECONDARY);              // tracks
            for (int k = -2; k <= 2; k++) m.cbox(sx * 0.9, 0.32, k * 0.38, 0.06, 0.3, 0.3, JOINT, Slot.METAL);
            m.cbox(sx * 0.4, 1.45, -0.75, 0.12, 0.55, 0.12, DARK, Slot.SECONDARY);               // exhaust stacks
            m.cbox(sx * 0.4, 1.74, -0.75, 0.08, 0.04, 0.08, THRUST, Slot.GLOW);
        }
        m.cbox(0, 0.82, -0.1, 1.05, 0.56, 1.7, HEAVY, Slot.PRIMARY);                              // hull
        m.cbox(0, 1.1, 0.55, 0.95, 0.06, 0.35, HAZARD, Slot.ACCENT);
        m.cbox(0, 1.32, -0.35, 0.78, 0.48, 0.95, PLATE, Slot.PRIMARY);                            // cab
        m.plate(0, 1.38, 0.131, 0.5, 0.16, VISOR, Slot.GLOW);
        m.cbox(0, 1.6, -0.35, 0.3, 0.08, 0.5, VENT, Slot.SECONDARY);
        m.cbox(0.3, 1.6, 0.05, 0.06, 0.06, 0.06, LAMP, Slot.GLOW);
        m.push(); m.at(0, 0.92, 0.85);                                                            // the drill
        m.cbox(0, 0, 0, 0.74, 0.74, 0.28, JOINT, Slot.METAL);
        m.rz(t * 0.7);
        for (int k = 0; k < 6; k++) {
            double w = 0.82 - k * 0.13;
            m.cbox(0, 0, 0.26 + k * 0.17, w, w, 0.18, k % 2 == 0 ? BARREL : HEAVY, k % 2 == 0 ? Slot.METAL : Slot.ACCENT);
        }
        for (int i = 0; i < 3; i++) {                                                             // carbide teeth
            m.push(); m.rz(i * Math.PI * 2 / 3);
            m.cbox(0, 0.36, 0.34, 0.1, 0.12, 0.12, PLATE, Slot.WHITE);
            m.cbox(0, 0.22, 0.75, 0.08, 0.1, 0.1, PLATE, Slot.WHITE);
            m.pop();
        }
        m.cbox(0, 0, 1.33, 0.09, 0.09, 0.14, BARREL, Slot.METAL);
        m.pop();
    }

    // ---- strike aircraft (drawn centred on the origin: the renderer lifts them and applies climb and bank) -----

    /** VIII strike drone: a long slender body, straight high wings, a V tail, a pusher propeller and a sensor ball. */
    private static void strikeDrone(Mesh m, State s) {
        float t = s.age;
        m.cbox(0, 0, -0.05, 0.34, 0.32, 2.5, PLATE, Slot.PRIMARY);                        // fuselage
        m.cbox(0, 0.12, 0.8, 0.4, 0.24, 0.75, PLATE, Slot.PRIMARY);                       // satellite hump over the nose
        m.cbox(0, 0.02, 1.32, 0.26, 0.24, 0.2, PLATE, Slot.PRIMARY);
        m.cbox(0, 0.17, 0.2, 0.2, 0.06, 0.9, VENT, Slot.SECONDARY);
        m.cbox(0, -0.2, 1.0, 0.24, 0.24, 0.24, DARK, Slot.SECONDARY);                     // sensor ball
        m.plate(0, -0.2, 1.121, 0.12, 0.12, LENS, Slot.GLOW);
        m.cbox(0, -0.2, -0.2, 0.16, 0.08, 0.7, DECAL, Slot.ACCENT);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 0.75, 0.1, -0.05, 1.2, 0.06, 0.46, PLATE, Slot.PRIMARY);            // inner wing
            m.cbox(sx * 1.95, 0.12, -0.08, 1.2, 0.05, 0.34, PLATE, Slot.PRIMARY);           // outer wing
            m.cbox(sx * 1.95, 0.155, -0.04, 0.9, 0.02, 0.12, DECAL, Slot.ACCENT);
            m.cbox(sx * 2.56, 0.12, -0.08, 0.04, 0.08, 0.3, LAMP, Slot.GLOW);               // wingtip light
            for (int k = 0; k < 2; k++) {                                                  // pylons and missiles
                double x = sx * (0.85 + k * 0.6);
                m.cbox(x, 0.0, 0.0, 0.05, 0.14, 0.22, JOINT, Slot.METAL);
                m.cbox(x, -0.12, 0.05, 0.1, 0.1, 0.85, PLATE, Slot.WHITE);
                m.cbox(x, -0.12, 0.5, 0.07, 0.07, 0.06, HAZARD, Slot.ACCENT);
                m.cbox(x, -0.12, -0.32, 0.22, 0.02, 0.1, CARBON, Slot.SECONDARY);
            }
            m.push(); m.at(sx * 0.12, 0.14, -1.12); m.rz(-sx * 0.75);                       // V tail
            m.cbox(0, 0.32, 0, 0.04, 0.64, 0.38, PLATE, Slot.PRIMARY);
            m.pop();
        }
        m.cbox(0, -0.3, -1.1, 0.04, 0.38, 0.3, PLATE, Slot.PRIMARY);                      // ventral fin
        m.cbox(0, 0, -1.36, 0.22, 0.22, 0.14, HEAVY, Slot.SECONDARY);                     // engine and pusher propeller
        m.cbox(0, 0, -1.47, 0.1, 0.1, 0.08, JOINT, Slot.METAL);
        m.push(); m.at(0, 0, -1.5); m.rz(t * 1.7);
        for (int i = 0; i < 3; i++) { m.push(); m.rz(i * Math.PI * 2 / 3); m.cbox(0, 0.32, 0, 0.07, 0.62, 0.02, CARBON, Slot.SECONDARY); m.pop(); }
        m.pop();
        m.cbox(0, -0.12, -1.25, 0.08, 0.06, 0.12, THRUST, Slot.GLOW);
        if ((int) (t / 6) % 3 == 0) m.cbox(0, 0.17, -0.6, 0.06, 0.04, 0.06, LAMP, Slot.GLOW);   // strobe
    }

    /** Falcon attack jet: cranked delta wing, twin canted tails, side intakes, twin engines with afterburner. */
    private static void strikeJet(Mesh m, State s) {
        float t = s.age;
        m.cbox(0, 0, 0, 0.72, 0.56, 3.6, HEAVY, Slot.PRIMARY);                            // fuselage
        m.cbox(0, -0.02, 2.1, 0.56, 0.46, 0.6, PLATE, Slot.PRIMARY);                      // nose
        m.cbox(0, -0.04, 2.5, 0.4, 0.34, 0.4, PLATE, Slot.PRIMARY);
        m.cbox(0, -0.05, 2.82, 0.22, 0.2, 0.3, PLATE, Slot.SECONDARY);
        m.cbox(0, -0.05, 3.05, 0.04, 0.04, 0.25, BARREL, Slot.METAL);                     // probe
        m.cbox(0, 0.34, 1.35, 0.38, 0.2, 1.0, DARK, Slot.SECONDARY);                      // sensor canopy (no pilot)
        m.cbox(0, 0.445, 1.35, 0.2, 0.02, 0.7, VISOR, Slot.GLOW);
        m.cbox(0, 0.3, -0.3, 0.3, 0.1, 1.6, VENT, Slot.SECONDARY);                        // spine
        m.cbox(0, -0.3, 0.2, 0.3, 0.06, 1.4, DECAL, Slot.ACCENT);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 0.5, -0.08, 0.55, 0.32, 0.42, 1.5, PLATE, Slot.SECONDARY);          // intakes
            m.plate(sx * 0.5, -0.08, 1.301, 0.26, 0.34, DARK, Slot.SECONDARY);
            m.cbox(sx * 0.5, 0.15, 1.27, 0.34, 0.04, 0.08, HAZARD, Slot.ACCENT);
            for (int k = 0; k < 7; k++) {                                                  // cranked delta: swept leading edge, straight trailing edge
                double x = sx * (0.5 + k * 0.3), le = 0.9 - k * 0.3, te = -1.55 + k * 0.05;
                m.cbox(x, -0.02, (le + te) / 2, 0.31, 0.07, le - te, PLATE, Slot.PRIMARY);
            }
            m.cbox(sx * 1.9, 0.02, -1.1, 0.5, 0.02, 0.18, DECAL, Slot.ACCENT);
            m.cbox(sx * 2.42, -0.02, -1.2, 0.05, 0.1, 0.34, LAMP, Slot.GLOW);
            for (int k = 0; k < 3; k++) {                                                  // tailplanes
                double x = sx * (0.5 + k * 0.28), le = -1.65 - k * 0.18;
                m.cbox(x, -0.05, (le - 2.35) / 2, 0.29, 0.05, le + 2.35, PLATE, Slot.PRIMARY);
            }
            m.push(); m.at(sx * 0.42, 0.28, -1.75); m.rz(-sx * 0.28);                       // canted twin tails
            for (int k = 0; k < 4; k++) {
                double y = 0.12 + k * 0.22, le = 0.25 - k * 0.2;
                m.cbox(0, y, (le - 0.55 + k * 0.04) / 2, 0.05, 0.23, le + 0.55 - k * 0.04, PLATE, Slot.PRIMARY);
            }
            m.cbox(0, 0.85, -0.55, 0.06, 0.06, 0.2, LAMP, Slot.GLOW);
            m.pop();
            m.cbox(sx * 0.2, -0.02, -1.95, 0.34, 0.34, 0.36, JOINT, Slot.METAL);       // nozzles
            m.cbox(sx * 0.2, -0.02, -2.135, 0.26, 0.26, 0.02, THRUST, Slot.GLOW);
            double burn = 0.35 + s.speed * 0.5 + Math.sin(t * 2.3 + sx) * 0.08;
            m.cbox(sx * 0.2, -0.02, -2.15 - burn / 2, 0.16, 0.16, burn, THRUST, Slot.GLOW);   // afterburner
            for (int k = 0; k < 2; k++) {                                                  // stand-off missiles under the wings
                double x = sx * (1.0 + k * 0.55);
                m.cbox(x, -0.12, -0.35, 0.05, 0.1, 0.4, JOINT, Slot.METAL);
                m.cbox(x, -0.24, -0.3, 0.13, 0.13, 1.5, PLATE, Slot.WHITE);
                m.cbox(x, -0.24, 0.47, 0.09, 0.09, 0.06, HAZARD, Slot.ACCENT);
                m.cbox(x, -0.24, -0.98, 0.32, 0.02, 0.16, CARBON, Slot.SECONDARY);
                m.cbox(x, -0.24, -0.98, 0.02, 0.32, 0.16, CARBON, Slot.SECONDARY);
            }
            m.cbox(sx * 0.4, 0.05, 1.7, 0.05, 0.05, 0.5, BARREL, Slot.METAL);               // cannon
        }
    }

    private static double bob(float t, double amp) { return Math.sin(t * 0.12) * amp; }

    private static void rotor(Mesh m, double x, double y, double z, double r, float t) {
        m.push(); m.at(x, y, z);
        m.cbox(0, -0.04, 0, 0.08, 0.1, 0.08, JOINT, Slot.METAL);
        m.ry(t * 2.1);
        m.disc(0, 0.02, 0, r, ROTOR, Slot.SECONDARY);
        m.pop();
    }

    private static void quadcopter(Mesh m, float t, double w, double h, double d, double rotor, boolean heavy) {
        m.push(); m.at(0, 0.35 + bob(t, 0.04), 0);
        m.cbox(0, 0, 0, w, h, d, heavy ? HEAVY : PLATE, Slot.PRIMARY);
        m.cbox(0, h / 2 + 0.03, -0.05, w * 0.6, 0.06, d * 0.5, VENT, Slot.SECONDARY);
        m.cbox(0, -h / 2 - 0.04, 0, w * 0.5, 0.08, d * 0.6, DECAL, Slot.ACCENT);
        m.cbox(0, 0, d / 2 + 0.03, w * 0.45, h * 0.6, 0.06, DARK, Slot.SECONDARY);
        m.plate(0, 0, d / 2 + 0.065, h * 0.5, h * 0.5, LENS, Slot.GLOW);
        double ax = w / 2 + rotor * 0.6, az = d / 2 + rotor * 0.4;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            m.push(); m.at(sx * ax * 0.5, 0.02, sz * az * 0.5);
            m.cbox(0, 0, 0, ax, 0.06, 0.07, CARBON, Slot.SECONDARY);
            m.pop();
            rotor(m, sx * ax, h * 0.3, sz * az, rotor, t + sx * 3 + sz);
            m.cbox(sx * ax, h * 0.3 - 0.06, sz * az, 0.04, 0.04, 0.04, LAMP, Slot.GLOW);
        }
        m.pop();
    }

    private static void sideGuns(Mesh m, double y, double x, double len) {
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * x, y, 0.1, 0.1, 0.12, 0.3, HEAVY, Slot.SECONDARY);
            m.cbox(sx * x, y, 0.1 + len / 2, 0.05, 0.05, len, BARREL, Slot.METAL);
        }
    }

    private static void siegeDrone(Mesh m, float t) {
        m.push(); m.at(0, 0.5 + bob(t, 0.05), 0);
        m.cbox(0, 0, 0, 0.95, 0.45, 1.1, HEAVY, Slot.PRIMARY);
        m.cbox(0, 0.26, -0.1, 0.6, 0.1, 0.6, VENT, Slot.SECONDARY);
        m.cbox(0, -0.3, 0.1, 0.3, 0.2, 0.5, HEAVY, Slot.SECONDARY);                  // belly turret
        m.cbox(0, -0.32, 0.75, 0.12, 0.12, 0.9, BARREL, Slot.METAL);
        m.cbox(0, -0.32, 1.2, 0.18, 0.18, 0.08, DARK, Slot.SECONDARY);
        m.cbox(-0.48, 0.05, 0, 0.04, 0.35, 0.9, HAZARD, Slot.ACCENT);
        m.cbox(0.48, 0.05, 0, 0.04, 0.35, 0.9, HAZARD, Slot.ACCENT);
        m.plate(0, 0.05, 0.56, 0.22, 0.22, LENS, Slot.GLOW);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            m.cbox(sx * 0.62, 0.1, sz * 0.5, 0.36, 0.1, 0.36, HEAVY, Slot.SECONDARY);      // ducted fans
            rotor(m, sx * 0.62, 0.17, sz * 0.5, 0.16, t * 1.3f + sx + sz);
            m.disc(sx * 0.62, 0.04, sz * 0.5, 0.17, THRUST, Slot.GLOW);
        }
        m.pop();
    }

    private static void missileDrone(Mesh m, float t) {
        m.push(); m.at(0, 0.5 + bob(t, 0.04), 0);
        m.cbox(0, 0, 0, 0.7, 0.4, 1.2, PLATE, Slot.PRIMARY);
        m.cbox(0, 0.22, 0.2, 0.4, 0.08, 0.5, VENT, Slot.SECONDARY);
        m.plate(0, 0.02, 0.61, 0.25, 0.2, LENS, Slot.GLOW);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 0.62, 0, 0.05, 0.4, 0.34, 0.85, HEAVY, Slot.SECONDARY);              // missile pods
            for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
                double px = sx * (0.53 + c * 0.18), py = -0.08 + r * 0.17;
                m.cbox(px, py, 0.5, 0.11, 0.11, 0.06, DARK, Slot.SECONDARY);
                m.cbox(px, py, 0.53, 0.07, 0.07, 0.04, PLATE, Slot.ACCENT);                  // warhead tips
            }
            m.cbox(sx * 0.62, -0.22, -0.35, 0.3, 0.08, 0.2, HAZARD, Slot.ACCENT);
        }
        m.cbox(0, 0, -0.65, 0.3, 0.25, 0.1, DARK, Slot.SECONDARY);
        m.plate(0, 0, -0.71, 0.24, 0.2, THRUST, Slot.GLOW);
        rotor(m, 0, 0.3, -0.2, 0.42, t);
        m.pop();
    }

    private static void hunter(Mesh m, float t) {
        m.push(); m.at(0, 0.55 + bob(t, 0.06), 0);
        m.rx(0.08);
        m.cbox(0, 0, 0, 0.55, 0.28, 1.5, CARBON, Slot.PRIMARY);
        m.cbox(0, 0.16, 0.2, 0.32, 0.08, 0.6, DARK, Slot.SECONDARY);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.push(); m.at(sx * 0.55, -0.02, -0.15); m.ry(sx * 0.35);
            m.cbox(0, 0, 0, 0.75, 0.06, 0.8, PLATE, Slot.PRIMARY);                           // swept wings
            m.cbox(sx * 0.2, 0.04, 0.1, 0.25, 0.02, 0.5, DECAL, Slot.ACCENT);
            m.pop();
            m.cbox(sx * 0.36, 0, -0.62, 0.24, 0.24, 0.5, HEAVY, Slot.SECONDARY);            // engines
            m.plate(sx * 0.36, 0, -0.875, 0.2, 0.2, THRUST, Slot.GLOW);
            m.cbox(sx * 0.18, -0.12, 0.85, 0.04, 0.08, 0.45, BARREL, Slot.METAL);           // blades
            m.cbox(sx * 0.18, -0.12, 1.1, 0.02, 0.1, 0.2, PLATE, Slot.ACCENT);
        }
        m.plate(0, 0.02, 0.751, 0.3, 0.1, VISOR, Slot.GLOW);
        m.pop();
    }

    private static void swarm(Mesh m, float t) {
        m.push(); m.at(0, 0.2 + bob(t * 2, 0.05), 0);
        m.cbox(0, 0, 0, 0.3, 0.22, 0.36, PLATE, Slot.PRIMARY);
        m.cbox(0, -0.1, 0.05, 0.22, 0.08, 0.22, HAZARD, Slot.ACCENT);
        m.cbox(0, 0.13, 0, 0.12, 0.05, 0.12, DARK, Slot.SECONDARY);
        boolean blink = (int) (t / 4) % 2 == 0;
        if (blink) m.cbox(0, 0.17, 0, 0.07, 0.04, 0.07, LAMP, Slot.GLOW);
        m.plate(0, 0, 0.181, 0.12, 0.08, VISOR, Slot.GLOW);
        rotor(m, -0.25, 0.08, 0, 0.13, t * 1.6f);
        rotor(m, 0.25, 0.08, 0, 0.13, t * 1.6f + 1);
        m.pop();
    }

    private static void lanceDrone(Mesh m, float t) {
        m.push(); m.at(0, 0.4 + bob(t, 0.03), 0);
        m.cbox(0, 0, -0.1, 0.42, 0.3, 0.8, PLATE, Slot.PRIMARY);
        m.cbox(0, 0.17, -0.1, 0.2, 0.05, 0.5, DECAL, Slot.ACCENT);
        m.cbox(0, -0.05, 0.6, 0.12, 0.12, 0.9, BARREL, Slot.SECONDARY);                    // the lance
        for (int i = 0; i < 3; i++) m.cbox(0, -0.05, 0.4 + i * 0.25, 0.18, 0.18, 0.05, JOINT, Slot.METAL);
        m.cbox(0, -0.05, 1.07, 0.16, 0.16, 0.04, DARK, Slot.SECONDARY);
        m.plate(0, -0.05, 1.095, 0.13, 0.13, LAMP, Slot.GLOW);
        m.plate(0, 0.06, 0.301, 0.2, 0.08, VISOR, Slot.GLOW);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 0.42, 0, -0.2, 0.3, 0.2, 0.42, HEAVY, Slot.SECONDARY);              // side ducts
            m.plate(sx * 0.42, 0, -0.415, 0.22, 0.14, THRUST, Slot.GLOW);
        }
        m.pop();
    }

    /** Leg swing for walkers: zero when standing still. */
    private static double stride(State s, double rate) {
        double moving = Math.min(1, s.speed * 12);
        return Math.sin(s.age * rate) * 0.55 * moving;
    }

    private static void leg(Mesh m, double x, double hip, double swing, double thigh, double shin, double width, boolean reverse, Slot slot) {
        m.push(); m.at(x, hip, 0);
        m.rx(swing);
        m.cbox(0, -thigh / 2, 0, width, thigh, width * 1.1, PLATE, slot);
        m.cbox(width * 0.52, -thigh / 2, 0, 0.03, thigh * 0.7, width * 0.5, HAZARD, Slot.ACCENT);
        m.at(0, -thigh, 0);
        m.cbox(0, 0, 0, width * 0.9, width * 0.9, width * 0.9, JOINT, Slot.METAL);
        m.rx((reverse ? 0.7 : -0.35) - swing * 0.6);
        m.cbox(0, -shin / 2, 0, width * 0.8, shin, width * 0.8, HEAVY, Slot.SECONDARY);
        m.at(0, -shin, 0);
        m.rx(-((reverse ? 0.7 : -0.35) - swing * 0.6) - swing);
        m.cbox(0, 0.05, 0.08, width * 1.25, 0.12, width * 1.8, TREAD, Slot.SECONDARY);
        m.pop();
    }

    private static void humanoid(Mesh m, State s, double k, boolean heavy) {
        double sw = stride(s, 0.5);
        double hip = 0.95 * k;
        leg(m, -0.17 * k, hip, sw, 0.5 * k, 0.47 * k, 0.2 * k, false, Slot.SECONDARY);
        leg(m, 0.17 * k, hip, -sw, 0.5 * k, 0.47 * k, 0.2 * k, false, Slot.SECONDARY);
        m.cbox(0, hip + 0.05, 0, 0.5 * k, 0.15 * k, 0.3 * k, JOINT, Slot.METAL);
        m.cbox(0, hip + 0.42 * k, 0, 0.62 * k, 0.6 * k, 0.38 * k, PLATE, Slot.PRIMARY);
        m.cbox(0, hip + 0.45 * k, 0.2 * k, 0.4 * k, 0.3 * k, 0.04, DECAL, Slot.ACCENT);
        m.cbox(0, hip + 0.42 * k, -0.24 * k, 0.4 * k, 0.45 * k, 0.12 * k, VENT, Slot.SECONDARY);
        m.cbox(0, hip + 0.88 * k, 0.02, 0.32 * k, 0.26 * k, 0.32 * k, HEAVY, Slot.PRIMARY);      // head
        m.plate(0, hip + 0.9 * k, 0.181 * k + 0.02, 0.26 * k, 0.07 * k, VISOR, Slot.GLOW);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.push(); m.at(sx * 0.4 * k, hip + 0.62 * k, 0);
            m.rx(sx == 1 ? -1.4 : -sw * 0.5 - 0.2);                                         // right arm raised to fire
            m.cbox(0, -0.25 * k, 0, 0.17 * k, 0.5 * k, 0.17 * k, PLATE, Slot.SECONDARY);
            m.cbox(0, 0, 0, 0.22 * k, 0.18 * k, 0.22 * k, HEAVY, Slot.PRIMARY);
            if (sx == 1) {
                m.cbox(0, -0.5 * k, 0, 0.16 * k, 0.28 * k, 0.16 * k, HEAVY, Slot.SECONDARY);
                m.cbox(0, -0.72 * k, 0, 0.06 * k, 0.3 * k, 0.06 * k, BARREL, Slot.METAL);
            }
            m.pop();
        }
    }

    private static void stalker(Mesh m, State s) {
        double sw = stride(s, 0.6);
        double hip = 1.05;
        leg(m, -0.12, hip, sw, 0.58, 0.55, 0.13, true, Slot.SECONDARY);
        leg(m, 0.12, hip, -sw, 0.58, 0.55, 0.13, true, Slot.SECONDARY);
        m.push(); m.at(0, hip, 0); m.rx(0.25);                                              // hunched forward
        m.cbox(0, 0.4, 0, 0.42, 0.7, 0.24, CARBON, Slot.PRIMARY);
        m.cbox(0, 0.45, -0.14, 0.3, 0.5, 0.06, CABLE, Slot.SECONDARY);
        m.cbox(0, 0.9, 0.06, 0.2, 0.22, 0.26, DARK, Slot.PRIMARY);
        m.plate(-0.05, 0.92, 0.191, 0.05, 0.05, LAMP, Slot.GLOW);
        m.plate(0.05, 0.92, 0.191, 0.05, 0.05, LAMP, Slot.GLOW);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.push(); m.at(sx * 0.28, 0.68, 0); m.rx(-0.6 + Math.sin(s.age * 0.3 + sx) * 0.15); m.rz(sx * 0.15);
            m.cbox(0, -0.3, 0, 0.1, 0.6, 0.1, PLATE, Slot.SECONDARY);
            m.at(0, -0.6, 0); m.rx(-0.6);
            m.cbox(0, -0.15, 0, 0.08, 0.3, 0.08, JOINT, Slot.METAL);
            m.cbox(0, -0.62, 0.02, 0.02, 0.7, 0.12, PLATE, Slot.WHITE);                     // blade
            m.cbox(0, -0.62, 0.08, 0.015, 0.68, 0.02, EMIT, Slot.GLOW);
            m.pop();
        }
        m.pop();
    }

    private static void flameWalker(Mesh m, State s) {
        humanoid(m, s, 1.1, true);
        double hip = 1.05;
        m.cbox(-0.17, hip + 0.55, -0.42, 0.22, 0.6, 0.22, HAZARD, Slot.ACCENT);             // fuel tanks
        m.cbox(0.17, hip + 0.55, -0.42, 0.22, 0.6, 0.22, HAZARD, Slot.ACCENT);
        m.cbox(0, hip + 0.88, -0.42, 0.5, 0.06, 0.06, CABLE, Slot.SECONDARY);
        m.cbox(0, hip + 0.5, 0.25, 0.7, 0.2, 0.06, DARK, Slot.SECONDARY);
        m.push(); m.at(0.44, hip + 0.68, 0); m.rx(-1.4);
        m.cbox(0, -0.85, 0, 0.14, 0.14, 0.14, DARK, Slot.SECONDARY);                         // nozzle
        double flick = 0.7 + 0.3 * Math.sin(s.age * 1.7);
        m.cbox(0, -0.95, 0, 0.06 * flick, 0.08, 0.06 * flick, LAMP, Slot.GLOW);             // pilot light
        m.pop();
    }

    private static void heavyWalker(Mesh m, State s, double k, String gun) {
        double sw = stride(s, 0.35) * 0.7;
        double hip = 1.35 * k;
        leg(m, -0.55 * k, hip, sw, 0.75 * k, 0.75 * k, 0.32 * k, true, Slot.SECONDARY);
        leg(m, 0.55 * k, hip, -sw, 0.75 * k, 0.75 * k, 0.32 * k, true, Slot.SECONDARY);
        double sway = Math.sin(s.age * 0.35) * 0.04 * Math.min(1, s.speed * 12);
        m.push(); m.at(0, hip + 0.1 * k, 0); m.rz(sway);
        m.cbox(0, 0.4 * k, 0, 1.3 * k, 0.8 * k, 1.1 * k, HEAVY, Slot.PRIMARY);
        m.cbox(0, 0.83 * k, -0.1 * k, 0.9 * k, 0.1 * k, 0.7 * k, VENT, Slot.SECONDARY);
        m.cbox(0, 0.2 * k, 0.56 * k, 0.9 * k, 0.3 * k, 0.04, HAZARD, Slot.ACCENT);
        m.cbox(0, 0.55 * k, 0.56 * k, 0.5 * k, 0.22 * k, 0.06, DARK, Slot.SECONDARY);
        m.plate(0, 0.55 * k, 0.6 * k, 0.42 * k, 0.12 * k, VISOR, Slot.GLOW);
        m.cbox(0, 0.4 * k, -0.6 * k, 0.6 * k, 0.5 * k, 0.12 * k, MESH, Slot.SECONDARY);
        switch (gun) {
            case "cannon" -> {
                for (int sx = -1; sx <= 1; sx += 2) {
                    m.cbox(sx * 0.8 * k, 0.45 * k, 0.1, 0.3 * k, 0.36 * k, 0.6 * k, HEAVY, Slot.SECONDARY);
                    m.cbox(sx * 0.8 * k, 0.45 * k, 0.75 * k, 0.14 * k, 0.14 * k, 0.9 * k, BARREL, Slot.METAL);
                    m.cbox(sx * 0.8 * k, 0.45 * k, 1.2 * k, 0.2 * k, 0.2 * k, 0.12 * k, DARK, Slot.SECONDARY);
                }
            }
            case "rail" -> {
                m.cbox(0, 1.05 * k, 0.3 * k, 0.36 * k, 0.24 * k, 2.2 * k, PLATE, Slot.SECONDARY);
                for (int i = 0; i < 5; i++) m.cbox(0, 1.05 * k, -0.4 * k + i * 0.4 * k, 0.44 * k, 0.32 * k, 0.08 * k, JOINT, Slot.METAL);
                m.cbox(0, 1.2 * k, 0.3 * k, 0.08 * k, 0.04, 2.0 * k, EMIT, Slot.GLOW);
                m.plate(0, 1.05 * k, 1.41 * k, 0.2 * k, 0.12 * k, LAMP, Slot.GLOW);
            }
            case "mortar" -> {
                m.push(); m.at(0, 0.9 * k, -0.2 * k); m.rx(-0.75);
                m.cbox(0, 0.5 * k, 0, 0.36 * k, 1.1 * k, 0.36 * k, BARREL, Slot.SECONDARY);
                m.cbox(0, 1.05 * k, 0, 0.44 * k, 0.1 * k, 0.44 * k, HEAVY, Slot.ACCENT);
                m.pop();
                m.cbox(-0.6 * k, 0.55 * k, -0.2 * k, 0.25 * k, 0.5 * k, 0.6 * k, HAZARD, Slot.ACCENT);   // shell racks
                m.cbox(0.6 * k, 0.55 * k, -0.2 * k, 0.25 * k, 0.5 * k, 0.6 * k, HAZARD, Slot.ACCENT);
            }
            default -> { }
        }
        m.pop();
    }

    private static void titan(Mesh m, State s) {
        heavyWalker(m, s, 1.9, "none");
        double k = 1.9, hip = 1.35 * k + 0.1 * k;
        m.push(); m.at(0, hip, 0);
        m.cbox(0, 0.4 * k, 0.58 * k, 0.36 * k, 0.36 * k, 0.04, EMIT, Slot.GLOW);               // reactor core
        for (int sx = -1; sx <= 1; sx += 2) {
            m.push(); m.at(sx * 0.95 * k, 0.6 * k, 0); m.rx(-0.25);
            m.cbox(0, -0.1 * k, 0.1 * k, 0.5 * k, 0.5 * k, 0.9 * k, HEAVY, Slot.SECONDARY);       // plasma arms
            m.cbox(0, -0.1 * k, 0.8 * k, 0.26 * k, 0.26 * k, 0.6 * k, BARREL, Slot.METAL);
            m.plate(0, -0.1 * k, 1.11 * k, 0.2 * k, 0.2 * k, THRUST, Slot.GLOW);
            m.pop();
            m.cbox(sx * 0.55 * k, 1.0 * k, -0.25 * k, 0.45 * k, 0.32 * k, 0.6 * k, HEAVY, Slot.PRIMARY);  // missile pods
            for (int i = 0; i < 3; i++) m.cbox(sx * (0.42 + i * 0.13) * k, 1.17 * k, -0.25 * k, 0.09 * k, 0.04, 0.09 * k, PLATE, Slot.ACCENT);
        }
        m.cbox(0, 0.9 * k, 0, 1.4 * k, 0.06 * k, 1.0 * k, HAZARD, Slot.ACCENT);
        m.pop();
    }

    private static void plasmaOrbs(Mesh m, float t) {
        for (int sx = -1; sx <= 1; sx += 2) {
            double pulse = 0.85 + 0.15 * Math.sin(t * 0.5 + sx);
            m.cbox(sx * 0.62, 0.55, 0.15, 0.3 * pulse, 0.3 * pulse, 0.3 * pulse, LAMP, Slot.GLOW);
            m.cbox(sx * 0.62, 0.55, 0.15, 0.36, 0.06, 0.36, JOINT, Slot.METAL);
        }
    }

    private static void gravRing(Mesh m, State s) {
        double k = 1.15, y = 1.35 * k + 0.1 * k + 0.4 * k;
        m.push(); m.at(0, y, 0); m.ry(s.age * 0.08);
        for (int i = 0; i < 8; i++) {
            m.push(); m.ry(i * Math.PI / 4); m.at(0, 0, 1.1);
            m.cbox(0, 0, 0, 0.18, 0.18, 0.18, LAMP, Slot.GLOW);
            m.pop();
        }
        m.pop();
        m.cbox(0, 1.35 * k + 0.6 * k, 0.66 * k, 0.5, 0.5, 0.04, EMIT, Slot.GLOW);
    }

    private static void phaseShimmer(Mesh m, State s) {
        double pulse = 0.6 + 0.4 * Math.sin(s.age * 0.4);
        m.cbox(0, 1.4, 0, 0.5 * pulse, 0.05, 0.5 * pulse, EMIT, Slot.GLOW);
        m.cbox(0, 0.9, 0, 0.6 * pulse, 0.05, 0.6 * pulse, EMIT, Slot.GLOW);
    }

    /** A tall command walker: armoured torso, a crown of lenses, a lance on one arm and a shield emitter on the other. */
    private static void overseer(Mesh m, State s) {
        double k = 1.7;
        humanoid(m, s, k, true);
        double hip = 0.95 * k;
        for (int i = 0; i < 5; i++) {
            double a = (i - 2) * 0.35;
            m.push(); m.at(0, hip + 1.08 * k, 0.05); m.ry(a);
            m.cbox(0, 0.1, 0.18, 0.12, 0.3, 0.06, HEAVY, Slot.SECONDARY);
            m.plate(0, 0.2, 0.215, 0.08, 0.08, LAMP, Slot.GLOW);
            m.pop();
        }
        m.cbox(0, hip + 0.45 * k, 0.21 * k, 0.5 * k, 0.12, 0.04, EMIT, Slot.GLOW);
        m.cbox(-0.62 * k, hip + 0.6 * k, 0.1, 0.12 * k, 0.6 * k, 0.5 * k, HEAVY, Slot.ACCENT);      // shield
        m.cbox(-0.69 * k, hip + 0.6 * k, 0.1, 0.02, 0.45 * k, 0.3 * k, EMIT, Slot.GLOW);
    }

    /** A flying hull with hangar bays along its belly and engine banks at the stern. */
    private static void carrier(Mesh m, float t) {
        m.push(); m.at(0, 1.0 + bob(t, 0.12), 0);
        m.cbox(0, 0, 0, 2.6, 0.9, 4.2, HEAVY, Slot.PRIMARY);
        m.cbox(0, 0.55, -0.2, 1.4, 0.3, 2.6, VENT, Slot.SECONDARY);
        m.cbox(0, 0.85, 1.0, 0.7, 0.4, 0.9, PLATE, Slot.SECONDARY);                               // bridge
        m.plate(0, 0.88, 1.456, 0.55, 0.12, VISOR, Slot.GLOW);
        for (int i = 0; i < 4; i++) {
            double z = -1.4 + i * 0.95;
            for (int sx = -1; sx <= 1; sx += 2) {
                m.cbox(sx * 0.7, -0.5, z, 0.7, 0.12, 0.7, DARK, Slot.SECONDARY);                   // bay doors
                m.cbox(sx * 0.7, -0.57, z, 0.5, 0.02, 0.5, EMIT, Slot.GLOW);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 1.6, 0, -1.6, 0.6, 0.6, 1.2, HEAVY, Slot.SECONDARY);
            m.plate(sx * 1.6, 0, -2.21, 0.5, 0.5, THRUST, Slot.GLOW);
            m.cbox(sx * 1.35, 0.1, 0.6, 0.8, 0.12, 1.6, HAZARD, Slot.ACCENT);
        }
        m.pop();
    }

    /** A giant: the titan's frame scaled up, spined, with a molten core showing through its chest. */
    private static void colossus(Mesh m, State s) {
        heavyWalker(m, s, 2.9, "none");
        double k = 2.9, hip = 1.35 * k + 0.1 * k;
        m.push(); m.at(0, hip, 0);
        double pulse = 0.8 + 0.2 * Math.sin(s.age * 0.15);
        m.cbox(0, 0.42 * k, 0.56 * k, 0.5 * k * pulse, 0.4 * k * pulse, 0.04, EMIT, Slot.GLOW);
        for (int i = 0; i < 5; i++) {
            m.push(); m.at((i - 2) * 0.28 * k, 0.85 * k, -0.2 * k); m.rx(-0.3);
            m.cbox(0, 0.3 * k, 0, 0.12 * k, 0.6 * k, 0.12 * k, HEAVY, Slot.ACCENT);                // spines
            m.pop();
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            m.push(); m.at(sx * 0.95 * k, 0.55 * k, 0); m.rx(-0.2);
            m.cbox(0, -0.15 * k, 0.15 * k, 0.55 * k, 0.55 * k, 1.0 * k, HEAVY, Slot.SECONDARY);
            m.cbox(0, -0.15 * k, 0.8 * k, 0.3 * k, 0.3 * k, 0.6 * k, BARREL, Slot.METAL);
            m.plate(0, -0.15 * k, 1.11 * k, 0.24 * k, 0.24 * k, THRUST, Slot.GLOW);
            m.pop();
        }
        m.pop();
    }

    private static void hound(Mesh m, State s) {
        double sw = stride(s, 0.8);
        m.cbox(0, 0.55, 0, 0.42, 0.32, 0.95, PLATE, Slot.PRIMARY);
        m.cbox(0, 0.73, -0.1, 0.24, 0.06, 0.6, CABLE, Slot.SECONDARY);
        for (int i = 0; i < 2; i++) {                                                             // tesla coils
            m.cbox(0, 0.85, -0.25 + i * 0.3, 0.1, 0.22, 0.1, JOINT, Slot.METAL);
            m.cbox(0, 0.98, -0.25 + i * 0.3, 0.14, 0.05, 0.14, LAMP, Slot.GLOW);
        }
        m.push(); m.at(0, 0.62, 0.5); m.rx(Math.sin(s.age * 0.25) * 0.1);
        m.cbox(0, 0.05, 0.18, 0.3, 0.24, 0.38, HEAVY, Slot.PRIMARY);                             // head
        m.cbox(0, -0.1, 0.25, 0.26, 0.06, 0.3, BARREL, Slot.METAL);                              // jaw
        m.plate(-0.08, 0.1, 0.371, 0.06, 0.05, LAMP, Slot.GLOW);
        m.plate(0.08, 0.1, 0.371, 0.06, 0.05, LAMP, Slot.GLOW);
        m.pop();
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            double phase = sx * sz > 0 ? sw : -sw;
            m.push(); m.at(sx * 0.2, 0.45, sz * 0.35); m.rx(phase);
            m.cbox(0, -0.18, 0, 0.11, 0.36, 0.11, PLATE, Slot.SECONDARY);
            m.at(0, -0.34, 0); m.rx(sz > 0 ? -0.4 : 0.4);
            m.cbox(0, -0.06, 0, 0.09, 0.14, 0.09, JOINT, Slot.METAL);
            m.pop();
        }
    }

    private static void spider(Mesh m, State s) {
        double moving = Math.min(1, s.speed * 12);
        m.cbox(0, 0.42, 0, 0.75, 0.22, 0.75, HEAVY, Slot.PRIMARY);
        m.cbox(0, 0.56, 0, 0.45, 0.1, 0.45, MESH, Slot.SECONDARY);
        double pulse = 0.8 + 0.2 * Math.sin(s.age * 0.4);
        m.cbox(0, 0.66, 0, 0.26 * pulse, 0.16 * pulse, 0.26 * pulse, LAMP, Slot.GLOW);             // EMP emitter
        m.plate(0, 0.42, 0.376, 0.3, 0.08, VISOR, Slot.GLOW);
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) / 8 * Math.PI * 2;
            double lift = Math.max(0, Math.sin(s.age * 0.7 + i * Math.PI / 2)) * 0.25 * moving;
            m.push(); m.at(Math.sin(a) * 0.35, 0.45, Math.cos(a) * 0.35); m.ry(a);
            m.rx(-0.6 - lift);
            m.cbox(0, 0, 0.3, 0.07, 0.07, 0.6, PLATE, Slot.SECONDARY);
            m.at(0, 0, 0.6); m.rx(1.5 + lift);
            m.cbox(0, 0, 0.25, 0.06, 0.06, 0.5, JOINT, Slot.METAL);
            m.pop();
        }
    }

    private static void tracked(Mesh m, State s, boolean shield) {
        double roll = s.age * s.speed * 4;
        for (int sx = -1; sx <= 1; sx += 2) {
            m.cbox(sx * 0.38, 0.18, 0, 0.22, 0.36, 0.95, TREAD, Slot.SECONDARY);
            for (int i = 0; i < 3; i++) {
                m.push(); m.at(sx * 0.5, 0.18, -0.3 + i * 0.3); m.rx(roll);
                m.cbox(0, 0, 0, 0.04, 0.2, 0.2, JOINT, Slot.METAL);
                m.pop();
            }
        }
        m.cbox(0, 0.45, 0, 0.6, 0.22, 0.85, HEAVY, Slot.PRIMARY);
        m.cbox(0, 0.45, 0.43, 0.5, 0.12, 0.04, HAZARD, Slot.ACCENT);
        if (shield) {
            m.cbox(0, 0.85, -0.05, 0.4, 0.6, 0.4, PLATE, Slot.PRIMARY);
            m.cbox(0, 1.25, -0.05, 0.12, 0.3, 0.12, JOINT, Slot.METAL);                                // emitter pylon
            double spin = s.age * 0.15;
            m.push(); m.at(0, 1.45, -0.05); m.ry(spin);
            m.cbox(0, 0, 0, 0.5, 0.05, 0.05, PLATE, Slot.ACCENT);
            m.cbox(0, 0, 0, 0.05, 0.05, 0.5, PLATE, Slot.ACCENT);
            m.pop();
            m.cbox(0, 1.45, -0.05, 0.16, 0.16, 0.16, LAMP, Slot.GLOW);
            m.push(); m.at(0, 0.85, 0.55); m.rx(-0.15);
            m.cbox(0, 0, 0, 1.1, 0.9, 0.08, HEAVY, Slot.SECONDARY);                                   // front shield plate
            m.cbox(0, 0, 0.05, 0.8, 0.04, 0.02, EMIT, Slot.GLOW);
            m.pop();
        } else {
            m.cbox(0, 0.78, 0, 0.42, 0.45, 0.38, PLATE, Slot.PRIMARY);
            m.cbox(0, 1.05, 0.05, 0.26, 0.16, 0.26, HEAVY, Slot.PRIMARY);
            m.plate(0, 1.06, 0.181, 0.2, 0.05, VISOR, Slot.GLOW);
            m.cbox(0, 0.86, 0.2, 0.18, 0.16, 0.04, DECAL, Slot.ACCENT);
            for (int sx = -1; sx <= 1; sx += 2) {                                                      // tool arms
                m.push(); m.at(sx * 0.27, 0.9, 0.05); m.rx(-1.0 + Math.sin(s.age * 0.2 + sx) * 0.3);
                m.cbox(0, -0.2, 0, 0.08, 0.4, 0.08, JOINT, Slot.METAL);
                m.at(0, -0.4, 0);
                m.cbox(0, -0.08, 0, 0.12, 0.16, 0.12, PLATE, Slot.SECONDARY);
                if (sx == 1) m.cbox(0, -0.2, 0, 0.04, 0.06, 0.04, LAMP, Slot.GLOW);                    // welding tip
                m.pop();
            }
        }
    }

    private static void projectile(Mesh m, State s) {
        switch (s.mode) {
            case 1 -> {                                                                                   // bullet
                m.cbox(0, 0.1, 0, 0.06, 0.06, 0.22, PLATE, Slot.ACCENT);
                m.cbox(0, 0.1, -0.2, 0.05, 0.05, 0.5, EMIT, Slot.GLOW);
            }
            case 2 -> {                                                                                   // cannon shell
                m.cbox(0, 0.15, 0, 0.18, 0.18, 0.32, HEAVY, Slot.SECONDARY);
                m.cbox(0, 0.15, 0.19, 0.12, 0.12, 0.08, PLATE, Slot.ACCENT);
                m.cbox(0, 0.15, -0.2, 0.14, 0.14, 0.1, LAMP, Slot.GLOW);
            }
            case 3 -> {                                                                                   // missile
                m.cbox(0, 0.15, 0, 0.14, 0.14, 0.6, PLATE, Slot.WHITE);
                m.cbox(0, 0.15, 0.33, 0.1, 0.1, 0.08, PLATE, Slot.ACCENT);
                for (int i = 0; i < 4; i++) {
                    m.push(); m.at(0, 0.15, -0.22); m.rz(i * Math.PI / 2);
                    m.cbox(0.11, 0, 0, 0.1, 0.02, 0.14, PLATE, Slot.ACCENT);
                    m.pop();
                }
                double f = 0.8 + 0.2 * Math.sin(s.age * 2.3);
                m.cbox(0, 0.15, -0.38, 0.1 * f, 0.1 * f, 0.14, LAMP, Slot.GLOW);
            }
            default -> m.cbox(0, 0.15, 0, 0.22, 0.22, 0.22, LAMP, Slot.GLOW);
        }
    }
}
