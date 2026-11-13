package com.heliosphere.planets.defense.sam;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Operating a vehicle by hand. Left-click a vehicle with an empty hand to take its hatch (one operator per
 * vehicle). The radar picture is drawn as boxes in the sky; R picks the next target, F fires (manual mode);
 * W/A/S/D drive (the launcher is stowed first); Shift leaves. Right-click a vehicle for its control panel.
 */
public final class SamOperation {
    private SamOperation() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(SamNet.Action.TYPE, SamNet.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(SamNet.Panel.TYPE, SamNet.Panel.CODEC);
        PayloadTypeRegistry.playS2C().register(SamNet.Picture.TYPE, SamNet.Picture.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SamNet.Action.TYPE, (a, ctx) -> ctx.server().execute(() -> act(ctx.player(), a)));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof SamVehicle v)) return InteractionResult.PASS;
            if (!player.getMainHandItem().isEmpty() || player.isSpectator()) return InteractionResult.FAIL;   // nothing to break
            if (player instanceof ServerPlayer sp) board(sp, v);
            return InteractionResult.SUCCESS;                                       // boarding, not attacking
        });
    }

    /** Where the operator sits: on top of the cab, in the vehicle frame. */
    static Vec3 hatch(SamKind k) { return new Vec3(0, Math.max(3.6, k.height + 1.2), k.length * 0.5 - 1.2); }   // above turret and masts

    public static void board(ServerPlayer p, SamVehicle v) {
        Entity on = v.getFirstPassenger();
        if (on != null && on != p) {
            p.displayClientMessage(Component.translatable("message.heliosphere.sam_taken").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (on == p) return;
        if (p.isPassenger()) p.stopRiding();
        if (!p.startRiding(v, true, true)) return;
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_boarded", Component.translatable("block.heliosphere." + v.kind().id),
                Component.translatable(v.manual() ? "message.heliosphere.sam_mode_manual" : "message.heliosphere.sam_mode_auto")).withStyle(ChatFormatting.AQUA), false);
    }

    /** The operator got off: take the station off their screen. */
    static void left(ServerPlayer p) { ServerPlayNetworking.send(p, SamNet.Picture.OFF); }

    /** The control panel (right-click on a vehicle). */
    static void panel(ServerPlayer p, SamVehicle v) {
        AmmoCrateBlockEntity crate = AmmoCrateBlockEntity.near(p.level(), v.position(), v.kind().system);
        ServerPlayNetworking.send(p, new SamNet.Panel(v.getId(), v.kind().ordinal(), v.manual(), v.ammo(), v.kind().magazine, v.energy(), v.capacity(),
                crate == null ? 0 : crate.count(v.kind().system), v.iff()));
    }

    private static void act(ServerPlayer p, SamNet.Action a) {
        ServerLevel level = (ServerLevel) p.level();
        boolean station = a.action() == SamNet.NEXT || a.action() == SamNet.FIRE;
        SamVehicle v = station ? (p.getVehicle() instanceof SamVehicle x ? x : null)
                : (level.getEntity(a.entity()) instanceof SamVehicle x ? x : null);
        if (v == null) return;
        if (!station && p.distanceToSqr(v) > 24 * 24) return;
        switch (a.action()) {
            case SamNet.NEXT -> {
                List<Entity> seen = v.sensed(level);
                if (seen.isEmpty()) { v.select(null); return; }
                int i = -1;
                for (int k = 0; k < seen.size(); k++) if (seen.get(k).getUUID().equals(v.selected())) i = k;
                v.select(seen.get((i + 1) % seen.size()).getUUID());
            }
            case SamNet.FIRE -> {
                if (!v.manual()) { p.displayClientMessage(Component.translatable("message.heliosphere.sam_fire_auto").withStyle(ChatFormatting.YELLOW), true); return; }
                if (!v.order()) p.displayClientMessage(Component.translatable(v.ammo() <= 0 ? "message.heliosphere.sam_fire_empty" : "message.heliosphere.sam_fire_none").withStyle(ChatFormatting.YELLOW), true);
            }
            case SamNet.AUTO -> v.setManual(false);
            case SamNet.MANUAL -> v.setManual(true);
            case SamNet.IFF_ON -> v.setIff(true);
            case SamNet.IFF_OFF -> v.setIff(false);
            case SamNet.BOARD -> board(p, v);
            default -> { }
        }
    }

    /** The operator's picture: every target the vehicle sees, its state and mode (a few times a second). */
    static void picture(ServerPlayer p, SamVehicle v, ServerLevel level) {
        SamKind k = v.kind();
        if (v.driving()) {
            ServerPlayNetworking.send(p, new SamNet.Picture(true, k.ordinal(), v.manual(), true, v.ammo(), k.magazine, -1, List.of(), v.iff(), 0, v.stowing()));
            return;
        }
        List<Entity> seen = v.sensed(level);
        if (v.manual() && v.selected() == null && !seen.isEmpty() && !k.radar) v.select(seen.get(0).getUUID());
        List<double[]> t = new ArrayList<>();
        int sel = -1;
        Vec3 site = v.position();
        for (Entity x : seen) {
            if (t.size() >= 24) break;
            Vec3 c = x.getBoundingBox().getCenter();
            if (x.getUUID().equals(v.selected())) sel = t.size();
            t.add(new double[]{c.x, c.y, c.z, c.distanceTo(site), x instanceof net.minecraft.world.entity.player.Player ? 1 : 0});
        }
        int range = k.radar ? k.radarRange : k.linkedRange;
        ServerPlayNetworking.send(p, new SamNet.Picture(true, k.ordinal(), v.manual(), false, v.ammo(), k.magazine, sel, t, v.iff(), range, false));
    }
}
