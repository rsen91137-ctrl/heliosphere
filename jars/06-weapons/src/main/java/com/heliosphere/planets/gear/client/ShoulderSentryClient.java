package com.heliosphere.planets.gear.client;

import com.heliosphere.core.client.fx.BoxMesh;
import com.heliosphere.planets.gear.ShoulderSentry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The sentry on the shoulder of anyone wearing it, turning towards the nearest hostile; and its line in the armour's tooltip. */
public final class ShoulderSentryClient {
    private ShoulderSentryClient() {}

    public static void init() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new Layer(avatar));
        });
        ItemTooltipCallback.EVENT.register((stack, tctx, ttype, lines) -> {
            long e = ShoulderSentry.bare(stack) ? -1 : ShoulderSentry.charge(stack);
            if (e >= 0) {
                int mk = ShoulderSentry.mk(stack);
                lines.add(Component.translatable("item.heliosphere.shoulder_sentry.fitted", e, ShoulderSentry.capacity(mk)).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("item.heliosphere.portable_sentry.stats", com.heliosphere.planets.defense.mk.PortableSentryItem.roman(mk),
                        String.format("%.0f", ShoulderSentry.damage(mk)), String.format("%.0f", ShoulderSentry.range(mk)), String.format("%.2f", ShoulderSentry.interval(mk) / 20f), ShoulderSentry.targets(mk)).withStyle(ChatFormatting.GOLD));
            }
        });
    }

    /** Recent tracers, heard from the combat effects: a shoulder sentry flashes, recoils and points where it fired. */
    private record Shot(Vec3 from, Vec3 to, long time) {}
    private static final java.util.ArrayDeque<Shot> SHOTS = new java.util.ArrayDeque<>();

    public static void heard(Vec3 from, Vec3 to, long time) {
        SHOTS.addLast(new Shot(from, to, time));
        while (SHOTS.size() > 64 || (!SHOTS.isEmpty() && time - SHOTS.peekFirst().time > 40)) SHOTS.pollFirst();
    }

    /** The newest shot fired from this wearer's shoulder within the last second, or null. */
    private static Shot lastShot(AvatarRenderState s, long now) {
        Shot best = null;
        for (Shot sh : SHOTS) {
            if (now - sh.time > 20) continue;
            double dx = sh.from.x - s.x, dy = sh.from.y - (s.y + 1.5), dz = sh.from.z - s.z;
            if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6 && (best == null || sh.time >= best.time)) best = sh;
        }
        return best;
    }

    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
            if (s.chestEquipment == null || ShoulderSentry.charge(s.chestEquipment) < 0 || s.isInvisible) return;
            Minecraft mc = Minecraft.getInstance();
            long now = mc.level == null ? 0 : mc.level.getGameTime();
            Shot shot = lastShot(s, now);
            float aim, pitch = 0, since = 99;
            if (shot != null) {                                        // point where it fired
                Vec3 d = shot.to.subtract(shot.from);
                aim = Mth.clamp(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)) - s.bodyRot), -150, 150);
                pitch = (float) Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
                since = now - shot.time;
            } else aim = aim(s);                                       // otherwise towards the nearest hostile
            int mk = ShoulderSentry.mk(s.chestEquipment);
            float fAim = aim, fPitch = Mth.clamp(pitch, -70, 70), recoil = since < 3 ? 1 - since / 3f : 0;
            boolean flash = since < 2;
            float spin = s.ageInTicks;
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            pose.translate(-0.33, -0.02, 0.02);                     // over the right shoulder (model space: x left, y down)
            pose.scale(-0.8f, -0.8f, 0.8f);                         // back to y up for the boxes
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, light, false), fAim, fPitch, mk, recoil, flash, spin));
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, 0xF000F0, true), fAim, fPitch, mk, recoil, flash, spin));
            pose.popPose();
        }

        /** Turn towards the nearest hostile near the wearer (on the client, just for the look). */
        private static float aim(AvatarRenderState s) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return 0;
            Vec3 at = new Vec3(s.x, s.y + 1.5, s.z);
            Mob best = null; double bd = ShoulderSentry.RANGE * ShoulderSentry.RANGE;
            for (Mob m : mc.level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(ShoulderSentry.RANGE))) {
                if (!(m instanceof Enemy) || !m.isAlive()) continue;
                double d = m.distanceToSqr(at);
                if (d < bd) { bd = d; best = m; }
            }
            if (best == null) return 0;
            Vec3 d = best.position().subtract(at);
            float world = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            return Mth.clamp(Mth.wrapDegrees(world - s.bodyRot), -150, 150);
        }

        private static final int STEEL = 0x8C9096, DARK = 0x2E3136, GUN = 0x3A3D42;
        /** Per Mk 0-V, as on the turrets: paint, trim, body material, stripe colour. */
        private static final int[] PAINT = {0xB89E6A, 0x6A7078, 0xC8A870, 0x4A5A74, 0x26282C, 0xE6E8EC};
        private static final int[] TRIM = {0xE8B420, 0xD07A28, 0xC8B080, 0x4A88D0, 0xD8A830, 0x60E8FF};
        private static final int[] BODY = {BoxMesh.PLATE, BoxMesh.PLATE, BoxMesh.HEAVY, BoxMesh.CARBON, BoxMesh.CARBON, BoxMesh.PLATE};
        private static final int[] STRIPE = {0x60FF90, 0x60FF90, 0xA0FF60, 0x60C0FF, 0xFFC040, 0x80FFFF};

        static void build(BoxMesh m, float aim, int mk) { build(m, aim, 0, mk, 0, false, 0); }

        static void build(BoxMesh m, float aim, float pitch, int mk, float recoil, boolean flash, float age) {
            mk = Math.max(0, Math.min(5, mk));
            int paint = PAINT[mk], trim = TRIM[mk], body = BODY[mk];
            int metal = mk >= 5 ? 0xD0D4DA : mk >= 4 ? 0x24262A : GUN;
            // the clamp on the pauldron: base plate, bolts, a short rail
            m.cbox(0, 0.03, 0, 0.28, 0.05, 0.32, BoxMesh.HEAVY, DARK);
            for (int bx = -1; bx <= 1; bx += 2) for (int bz = -1; bz <= 1; bz += 2) m.cbox(bx * 0.1, 0.065, bz * 0.12, 0.03, 0.02, 0.03, BoxMesh.JOINT, STEEL);
            m.cbox(0, 0.07, 0, 0.06, 0.03, 0.26, BoxMesh.JOINT, trim);
            // the post and the swivel ring
            m.cbox(0, 0.12, 0, 0.07, 0.08, 0.07, BoxMesh.JOINT, STEEL);
            m.cbox(0, 0.165, 0, 0.15, 0.02, 0.15, BoxMesh.JOINT, mk >= 4 ? trim : STEEL);
            m.push();
            m.at(0, 0.24, 0);
            m.ry(Math.toRadians(-aim));
            m.scale(1 + mk * 0.04);
            // the housing: a main block, a sloped glacis, armoured cheeks
            m.cbox(0, 0, -0.02, 0.2, 0.13, 0.22, body, paint);
            m.cbox(0, 0.025, 0.1, 0.17, 0.08, 0.05, body, paint);
            m.cbox(0, 0.075, -0.02, 0.18, 0.02, 0.2, BoxMesh.JOINT, trim);                  // trim along the roof
            for (int sx = -1; sx <= 1; sx += 2) {
                m.cbox(sx * 0.11, -0.01, 0, 0.025, 0.1, 0.18, BoxMesh.HEAVY, paint);
                for (int k = 0; k < mk; k++) m.cglow(sx * 0.124, -0.04 + k * 0.022, -0.05, 0.004, 0.012, 0.07, BoxMesh.LAMP, STRIPE[mk]);   // one stripe a Mk
            }
            // the solar panel on top (folding wings from Mk II) and the battery pack behind, with its cable
            m.cbox(0, 0.095, -0.03, 0.22, 0.015, 0.17, BoxMesh.CARBON, 0x22345A);
            if (mk >= 2) for (int sx = -1; sx <= 1; sx += 2) m.cbox(sx * 0.17, 0.095, -0.03, 0.11, 0.012, 0.15, BoxMesh.CARBON, 0x22345A);
            m.cbox(0, -0.01, -0.16, 0.14, 0.09, 0.07, BoxMesh.HEAVY, DARK);
            m.cglow(0, 0.02, -0.196, 0.06, 0.012, 0.004, BoxMesh.LAMP, 0x40C8FF);
            m.cbox(0.05, -0.07, -0.16, 0.025, 0.06, 0.025, BoxMesh.CABLE, DARK);
            // sensors: a rangefinder with a lens, an antenna (Mk III: a second sensor)
            m.cbox(0.075, 0.06, 0.08, 0.05, 0.045, 0.06, BoxMesh.DARK, DARK);
            m.cglow(0.075, 0.06, 0.112, 0.03, 0.025, 0.004, BoxMesh.LENS, mk >= 5 ? 0x60E8FF : 0xFF4030);
            m.cbox(-0.08, 0.15, -0.14, 0.012, 0.12, 0.012, BoxMesh.JOINT, STEEL);
            m.cglow(-0.08, 0.215, -0.14, 0.02, 0.02, 0.02, BoxMesh.LAMP, flash ? 0xFF5040 : trim);
            if (mk >= 3) {
                m.cbox(-0.075, 0.06, 0.08, 0.05, 0.045, 0.06, BoxMesh.DARK, DARK);
                m.cglow(-0.075, 0.06, 0.112, 0.03, 0.025, 0.004, BoxMesh.LENS, 0x60E0FF);
            }
            // the guns, elevated together: one, twin from Mk III, a third on top at Mk V
            m.push();
            m.at(0, 0.0, 0.12);
            m.rx(Math.toRadians(-pitch));
            int guns = mk >= 3 ? 2 : 1;
            double len = 0.26 + mk * 0.02, back = recoil * 0.04;
            for (int g = 0; g < guns; g++) {
                double gx = guns == 1 ? 0 : (g == 0 ? -0.045 : 0.045);
                barrel(m, gx, 0, len, back, metal, flash, mk);
            }
            if (mk >= 5) barrel(m, 0, 0.055, len * 0.9, back * 0.6, metal, flash, mk);
            m.cbox(0, 0, 0.02, guns == 1 ? 0.07 : 0.14, 0.07, 0.06, BoxMesh.HEAVY, DARK);  // the mantlet
            m.pop();
            if (mk >= 5) {                                                                  // Mk V: a slow halo round the housing
                m.push();
                m.at(0, 0.03, -0.02);
                m.ry(age * 0.06);
                for (int i = 0; i < 6; i++) { m.push(); m.ry(i * Math.PI / 3); m.cglow(0, 0, 0.17, 0.07, 0.008, 0.01, BoxMesh.EMIT, trim); m.pop(); }
                m.pop();
            }
            m.pop();
        }

        /** One gun along +z from the mantlet: barrel, cooling jacket, muzzle brake, and the flash when it fires. */
        private static void barrel(BoxMesh m, double x, double y, double len, double back, int metal, boolean flash, int mk) {
            double z0 = 0.03 - back, z1 = z0 + len;
            m.cyl(x, y, z0, z1, 0.02, 0.018, 8, BoxMesh.BARREL, metal);
            m.cyl(x, y, z0 + 0.02, z0 + len * 0.55, 0.032, 0.032, 8, BoxMesh.VENT, mk >= 4 ? 0x1C1E22 : DARK);
            m.cbox(x, y, z1 + 0.012, 0.05, 0.05, 0.03, BoxMesh.DARK, DARK);
            for (int sx = -1; sx <= 1; sx += 2) m.cbox(x + sx * 0.03, y, z1 + 0.012, 0.012, 0.03, 0.02, BoxMesh.VENT, DARK);   // brake ports
            if (flash) {
                m.cglow(x, y, z1 + 0.06, 0.11, 0.11, 0.04, BoxMesh.THRUST, 0xFFD070);
                m.cglow(x, y, z1 + 0.12, 0.05, 0.05, 0.08, BoxMesh.THRUST, 0xFFF0C0);
            }
        }
    }
}
