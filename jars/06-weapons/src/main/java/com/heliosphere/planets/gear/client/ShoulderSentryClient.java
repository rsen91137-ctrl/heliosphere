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
            long e = ShoulderSentry.charge(stack);
            if (e >= 0) lines.add(Component.translatable("item.heliosphere.shoulder_sentry.fitted", e, ShoulderSentry.CAPACITY).withStyle(ChatFormatting.AQUA));
        });
    }

    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
            if (s.chestEquipment == null || ShoulderSentry.charge(s.chestEquipment) < 0 || s.isInvisible) return;
            float aim = aim(s);                                      // relative to the body, degrees
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            pose.translate(-0.33, -0.02, 0.02);                     // over the right shoulder (model space: x left, y down)
            pose.scale(-0.8f, -0.8f, 0.8f);                         // back to y up for the boxes
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, light, false), aim));
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(BoxMesh.KIT), (p, vc) -> build(new BoxMesh(p, vc, 0xF000F0, true), aim));
            pose.popPose();
        }

        /** Turn towards the nearest hostile near the wearer (on the client, just for the look). */
        private static float aim(AvatarRenderState s) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return 0;
            Player wearer = null;
            for (Player p : mc.level.players()) if (Math.abs(p.getX() - s.x) < 0.6 && Math.abs(p.getZ() - s.z) < 0.6 && Math.abs(p.getY() - s.y) < 1) { wearer = p; break; }
            if (wearer == null) return 0;
            Vec3 at = wearer.position().add(0, 1.5, 0);
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

        private static final int STEEL = 0x8C9096, DARK = 0x2E3136, SAND = 0xB89E6A;

        static void build(BoxMesh m, float aim) {
            m.cbox(0, 0.03, 0, 0.26, 0.06, 0.3, BoxMesh.HEAVY, DARK);                   // clamp plate on the pauldron
            m.cbox(0, 0.1, 0, 0.12, 0.1, 0.12, BoxMesh.JOINT, STEEL);                   // swivel
            m.push();
            m.at(0, 0.2, 0);
            m.ry(Math.toRadians(-aim));
            m.cbox(0, 0, 0, 0.2, 0.15, 0.24, BoxMesh.PLATE, SAND);                      // head
            m.cbox(0, 0.09, -0.04, 0.24, 0.02, 0.18, BoxMesh.CARBON, 0x2A3A5A);         // solar panel
            m.cbox(0, 0, 0.22, 0.05, 0.05, 0.24, BoxMesh.BARREL, STEEL);                // gun
            m.cbox(0, 0, 0.35, 0.07, 0.07, 0.03, BoxMesh.DARK, DARK);
            m.cbox(0.08, 0.02, 0.11, 0.04, 0.04, 0.05, BoxMesh.DARK, DARK);
            m.cglow(0.08, 0.02, 0.14, 0.028, 0.028, 0.01, BoxMesh.LENS, 0x60E0FF);
            m.pop();
        }
    }
}
