package com.heliosphere.planets.defense.sam;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operating a vehicle by hand. Left-click a vehicle with an empty hand to take its hatch (one vehicle at a time).
 * The radar picture is drawn as boxes in the sky; R picks the next target, F fires (manual mode); W/A/S/D drive
 * off (the vehicle stows its weapons and becomes a moving vehicle), and after two seconds standing still it
 * deploys again. Shift leaves. Right-click a vehicle for its control panel (automatic / manual).
 */
public final class SamOperation {
    private SamOperation() {}

    private static final Map<UUID, BlockPos> OPERATING = new HashMap<>();

    public static void init() {
        PayloadTypeRegistry.playC2S().register(SamNet.Action.TYPE, SamNet.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(SamNet.Panel.TYPE, SamNet.Panel.CODEC);
        PayloadTypeRegistry.playS2C().register(SamNet.Picture.TYPE, SamNet.Picture.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SamNet.Action.TYPE, (a, ctx) -> ctx.server().execute(() -> act(ctx.player(), a)));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (!player.getMainHandItem().isEmpty() || player.isSpectator()) return InteractionResult.PASS;
            BlockPos m = masterOf(level, pos);
            if (m == null) return InteractionResult.PASS;
            if (player instanceof ServerPlayer sp) board(sp, m);
            return InteractionResult.SUCCESS;                                       // boarding, not breaking
        });
        ServerTickEvents.END_SERVER_TICK.register(SamOperation::tick);
    }

    /** The player operating the vehicle at {@code master}, if any (seated at its hatch). */
    static net.minecraft.world.entity.player.Player operatorOf(ServerLevel level, BlockPos master) {
        for (Map.Entry<UUID, BlockPos> e : OPERATING.entrySet()) {
            if (!master.equals(e.getValue())) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.getVehicle() instanceof SamSeat) return p;
        }
        return null;
    }

    /** Where the operator sits: on top of the cab, in the vehicle frame. */
    static Vec3 hatch(SamKind k) { return new Vec3(0, Math.max(3.6, k.height + 1.2), k.length * 0.5 - 1.2); }   // above turret and masts

    static BlockPos masterOf(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof SamBlock) return pos;
        if (level.getBlockState(pos).is(SamSites.HULL)) return SamHullBlock.master(level, pos);
        return null;
    }

    public static void board(ServerPlayer p, BlockPos master) {
        ServerLevel level = (ServerLevel) p.level();
        if (!(level.getBlockEntity(master) instanceof SamBlockEntity be)) return;
        for (Map.Entry<UUID, BlockPos> e : OPERATING.entrySet())
            if (master.equals(e.getValue()) && !e.getKey().equals(p.getUUID())) {
                p.displayClientMessage(Component.translatable("message.heliosphere.sam_taken").withStyle(ChatFormatting.RED), true);
                return;
            }
        if (p.isPassenger()) p.stopRiding();
        SamSeat seat = new SamSeat(SamSites.SEAT, level);
        seat.vehicle = master;
        Vec3 at = be.world(hatch(be.kind()));
        seat.setPos(at.x, at.y, at.z);
        level.addFreshEntity(seat);
        p.startRiding(seat, true, true);
        OPERATING.put(p.getUUID(), master);
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_boarded", Component.translatable("block.heliosphere." + be.kind().id),
                Component.translatable(be.manual() ? "message.heliosphere.sam_mode_manual" : "message.heliosphere.sam_mode_auto")).withStyle(ChatFormatting.AQUA), false);
    }

    /** The control panel (right-click on a vehicle). */
    static void panel(ServerPlayer p, SamBlockEntity be) {
        AmmoCrateBlockEntity crate = AmmoCrateBlockEntity.near(p.level(), Vec3.atBottomCenterOf(be.getBlockPos()), be.kind().system);
        ServerPlayNetworking.send(p, new SamNet.Panel(be.getBlockPos(), be.kind().ordinal(), be.manual(), be.ammo(), be.kind().magazine, be.energy(), be.capacity(),
                crate == null ? 0 : crate.count(be.kind().system), be.iff()));
    }

    private static void act(ServerPlayer p, SamNet.Action a) {
        ServerLevel level = (ServerLevel) p.level();
        BlockPos op = OPERATING.get(p.getUUID());
        BlockPos pos = a.action() == SamNet.NEXT || a.action() == SamNet.FIRE ? op : a.pos();
        if (pos == null || !(level.getBlockEntity(pos) instanceof SamBlockEntity be)) return;
        if (a.action() != SamNet.NEXT && a.action() != SamNet.FIRE && p.distanceToSqr(Vec3.atCenterOf(pos)) > 24 * 24) return;
        switch (a.action()) {
            case SamNet.NEXT -> {
                List<Entity> seen = be.sensed(level);
                if (seen.isEmpty()) { be.select(null); return; }
                int i = -1;
                for (int k = 0; k < seen.size(); k++) if (seen.get(k).getUUID().equals(be.selected())) i = k;
                be.select(seen.get((i + 1) % seen.size()).getUUID());
            }
            case SamNet.FIRE -> {
                if (!be.manual()) { p.displayClientMessage(Component.translatable("message.heliosphere.sam_fire_auto").withStyle(ChatFormatting.YELLOW), true); return; }
                if (!be.order()) p.displayClientMessage(Component.translatable(be.ammo() <= 0 ? "message.heliosphere.sam_fire_empty" : "message.heliosphere.sam_fire_none").withStyle(ChatFormatting.YELLOW), true);
            }
            case SamNet.AUTO -> be.setManual(false);
            case SamNet.MANUAL -> be.setManual(true);
            case SamNet.IFF_ON -> be.setIff(true);
            case SamNet.IFF_OFF -> be.setIff(false);
            case SamNet.BOARD -> board(p, pos);
            default -> { }
        }
    }

    private static void tick(MinecraftServer server) {
        if (OPERATING.isEmpty()) return;
        var it = OPERATING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            Entity v = p.getVehicle();
            if (v instanceof SamDriveEntity d) {
                if (server.getTickCount() % 3 == 0)
                    ServerPlayNetworking.send(p, new SamNet.Picture(true, d.kind().ordinal(), false, true, d.ammo(), d.kind().magazine, -1, List.of(), true, 0));
                continue;
            }
            ServerLevel level = (ServerLevel) p.level();
            if (!(v instanceof SamSeat) || !(level.getBlockEntity(e.getValue()) instanceof SamBlockEntity be)) {
                it.remove();
                ServerPlayNetworking.send(p, SamNet.Picture.OFF);
                continue;
            }
            Input in = p.getLastClientInput();
            if (in.forward() || in.backward() || in.left() || in.right()) { drive(p, be); continue; }
            if (server.getTickCount() % 3 != 0) continue;
            List<Entity> seen = be.sensed(level);
            if (be.manual() && be.selected() == null && !seen.isEmpty() && !be.kind().radar) be.select(seen.get(0).getUUID());
            List<double[]> t = new ArrayList<>();
            int sel = -1;
            Vec3 site = Vec3.atBottomCenterOf(be.getBlockPos());
            for (Entity x : seen) {
                if (t.size() >= 24) break;
                Vec3 c = x.getBoundingBox().getCenter();
                if (x.getUUID().equals(be.selected())) sel = t.size();
                t.add(new double[]{c.x, c.y, c.z, c.distanceTo(site), x instanceof net.minecraft.world.entity.player.Player ? 1 : 0});
            }
            int range = be.kind().radar ? be.kind().radarRange : be.kind().linkedRange;
            ServerPlayNetworking.send(p, new SamNet.Picture(true, be.kind().ordinal(), be.manual(), false, be.ammo(), be.kind().magazine, sel, t, be.iff(), range));
        }
    }

    /** Off we go: the vehicle stows its weapons and becomes a moving vehicle with the operator aboard. */
    private static void drive(ServerPlayer p, SamBlockEntity be) {
        ServerLevel level = (ServerLevel) p.level();
        BlockPos master = be.getBlockPos();
        SamKind kind = be.kind();
        var carry = be.carry();
        float yaw = be.facing().toYRot();
        p.stopRiding();
        level.setBlock(master, Blocks.AIR.defaultBlockState(), 3);
        SamDriveEntity d = SamSites.DRIVE.create(level, EntitySpawnReason.TRIGGERED);
        if (d == null) return;
        d.setup(kind, carry, yaw);
        d.setPos(master.getX() + 0.5, master.getY(), master.getZ() + 0.5);
        level.addFreshEntity(d);
        p.startRiding(d, true, true);
        OPERATING.put(p.getUUID(), master);
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_driving").withStyle(ChatFormatting.AQUA), true);
    }
}
