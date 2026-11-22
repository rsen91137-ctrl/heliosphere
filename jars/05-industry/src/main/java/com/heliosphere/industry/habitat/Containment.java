package com.heliosphere.industry.habitat;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Players in a bio capsule. Only the player whiteflame1 can take another player into a capsule. A captive becomes a
 * spectator who cannot move, fly off or interact: they float above whoever carries the capsule (or where the capsule
 * lies), free to look around and to chat. Letting the capsule out on the ground frees them where it is used, in the
 * game mode they had. Safety: a capsule destroyed as an item on the ground (lava, fire, the void, despawning) frees its
 * captive there; an operator can free anyone with /hsrelease; everything survives a restart (kept with the overworld).
 */
public final class Containment {
    private Containment() {}

    /** The only player who can take players into a capsule. */
    public static final String KEEPER = "whiteflame1";
    static final String PLAYER = "captive", CAPSULE_ID = "capsule_id";

    /** Captives by player UUID: capsule id, previous game mode, last anchor (dimension and position), a pending release. */
    private static AttachmentType<CompoundTag> RECORDS;

    public static void init() {
        RECORDS = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("heliosphere", "capsule_captives"), b -> b.persistent(CompoundTag.CODEC));
        ServerTickEvents.END_SERVER_TICK.register(Containment::tick);
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> rejoin(h.player)));
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> { if (e instanceof ItemEntity it) itemGone(it, level); });
        UseBlockCallback.EVENT.register((p, l, hand, hit) -> held(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((p, l, hand) -> held(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((p, l, hand, e, hit) -> held(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((p, l, hand, e, hit) -> held(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((p, l, hand, pos, dir) -> held(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("hsrelease").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(c -> {
                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
                            if (!captive(target)) { c.getSource().sendFailure(Component.translatable("message.heliosphere.capsule_not_captive", target.getName())); return 0; }
                            free(target, target.level(), target.position());
                            c.getSource().sendSuccess(() -> Component.translatable("message.heliosphere.capsule_freed", target.getName()), true);
                            return 1;
                        }))));
    }

    private static CompoundTag records(MinecraftServer server) {
        CompoundTag t = server.overworld().getAttached(RECORDS);
        return t == null ? new CompoundTag() : t.copy();
    }

    private static void save(MinecraftServer server, CompoundTag t) { server.overworld().setAttached(RECORDS, t.isEmpty() ? null : t); }

    public static boolean captive(Player p) {
        return p instanceof ServerPlayer sp && sp.level().getServer() != null && records(sp.level().getServer()).contains(sp.getStringUUID());
    }

    /** A captive, for the interaction callbacks (on the server; the client side passes). */
    private static boolean held(Player p) { return p instanceof ServerPlayer sp && !sp.isRemoved() && captive(sp); }

    /** Whether {@code keeper} may take {@code target} into a capsule. */
    public static boolean mayTake(Player keeper, Player target) {
        return keeper != target && KEEPER.equalsIgnoreCase(keeper.getScoreboardName()) && target instanceof ServerPlayer && target.isAlive()
                && !target.isSpectator() && !captive(target);
    }

    /** Takes {@code target} into the capsule {@code stack} held by {@code keeper}. */
    public static void take(ServerPlayer keeper, ServerPlayer target, ItemStack stack) {
        MinecraftServer server = keeper.level().getServer();
        String id = UUID.randomUUID().toString();
        CompoundTag rec = new CompoundTag();
        rec.putString(CAPSULE_ID, id);
        rec.putString("mode", target.gameMode().getName());
        rec.putString("keeper", keeper.getScoreboardName());
        anchor(rec, keeper.level(), keeper.position().add(0, 1.0, 0));
        CompoundTag all = records(server);
        all.put(target.getStringUUID(), rec);
        save(server, all);
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.putString(PLAYER, target.getStringUUID());
            t.putString(CAPSULE_ID, id);
        });
        target.stopRiding();
        target.ejectPassengers();
        target.closeContainer();
        target.setGameMode(GameType.SPECTATOR);
        target.getAbilities().setFlyingSpeed(0f);
        target.onUpdateAbilities();
        target.displayClientMessage(Component.translatable("message.heliosphere.capsule_you_are_held", keeper.getName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** Lets the captive of {@code stack} out at {@code at}; true when the stack held a player. */
    public static boolean release(ItemStack stack, ServerLevel level, Vec3 at) {
        var d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null || !d.copyTag().contains(PLAYER)) return false;
        String who = d.copyTag().getStringOr(PLAYER, "");
        MinecraftServer server = level.getServer();
        ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(who));
        if (p != null) free(p, level, at);
        else {                                                      // offline: freed here when they come back
            CompoundTag all = records(server);
            CompoundTag rec = all.getCompoundOrEmpty(who);
            if (!rec.isEmpty()) { rec.putBoolean("free", true); anchor(rec, level, at); all.put(who, rec); save(server, all); }
        }
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> { t.remove(PLAYER); t.remove(CAPSULE_ID); });
        return true;
    }

    /** Frees a captive at {@code at}: the game mode they had, normal flying speed, the record gone. */
    static void free(ServerPlayer p, ServerLevel level, Vec3 at) {
        MinecraftServer server = level.getServer();
        CompoundTag all = records(server);
        CompoundTag rec = all.getCompoundOrEmpty(p.getStringUUID());
        all.remove(p.getStringUUID());
        save(server, all);
        GameType mode = GameType.byName(rec.getStringOr("mode", "survival"), GameType.SURVIVAL);
        p.setCamera(p);
        p.getAbilities().setFlyingSpeed(0.05f);
        p.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
        p.onUpdateAbilities();
        p.teleportTo(level, at.x, at.y, at.z, EnumSet.noneOf(Relative.class), p.getYRot(), p.getXRot(), true);
        p.displayClientMessage(Component.translatable("message.heliosphere.capsule_you_are_free").withStyle(ChatFormatting.GREEN), false);
    }

    private static void anchor(CompoundTag rec, Level level, Vec3 at) {
        rec.putString("dim", level.dimension().identifier().toString());
        rec.putDouble("x", at.x); rec.putDouble("y", at.y); rec.putDouble("z", at.z);
    }

    private static void rejoin(ServerPlayer p) {
        CompoundTag rec = records(p.level().getServer()).getCompoundOrEmpty(p.getStringUUID());
        if (rec.isEmpty()) return;
        if (rec.getBooleanOr("free", false)) { free(p, level(p.level().getServer(), rec), new Vec3(rec.getDoubleOr("x", p.getX()), rec.getDoubleOr("y", p.getY()), rec.getDoubleOr("z", p.getZ()))); return; }
        p.setGameMode(GameType.SPECTATOR);
        p.getAbilities().setFlyingSpeed(0f);
        p.onUpdateAbilities();
    }

    private static ServerLevel level(MinecraftServer server, CompoundTag rec) {
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(rec.getStringOr("dim", "minecraft:overworld"))));
        return l == null ? server.overworld() : l;
    }

    /** A capsule lost as an item on the ground (burnt, in the void, despawned) frees its captive where it was. */
    private static void itemGone(ItemEntity it, ServerLevel level) {
        if (it.getRemovalReason() == null || !it.getRemovalReason().shouldDestroy()) return;
        ItemStack stack = it.getItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof BioCapsuleItem)) return;     // picked up: the stack was emptied first
        Vec3 at = it.getY() < level.getMinY() ? Vec3.atBottomCenterOf(level.getRespawnData().pos()) : it.position();
        release(stack, level, at);
    }

    /** Keeps every captive above their capsule, a spectator who cannot move. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 2 != 0) return;
        CompoundTag all = records(server);
        if (all.isEmpty()) return;
        boolean changed = false;
        for (String who : all.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(who));
            if (p == null) continue;
            CompoundTag rec = all.getCompoundOrEmpty(who);
            String id = rec.getStringOr(CAPSULE_ID, "");
            Entity carrier = null;
            for (ServerPlayer q : server.getPlayerList().getPlayers()) if (q != p && holds(q, id)) { carrier = q; break; }
            if (carrier == null && server.getTickCount() % 20 == 0) {          // on the ground near where it was last seen
                ServerLevel l = level(server, rec);
                Vec3 last = new Vec3(rec.getDoubleOr("x", 0), rec.getDoubleOr("y", 0), rec.getDoubleOr("z", 0));
                for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(BlockPos.containing(last)).inflate(48)))
                    if (holds(it.getItem(), id)) { carrier = it; break; }
            }
            if (carrier != null) {
                anchor(rec, carrier.level(), carrier.position().add(0, carrier instanceof Player ? 1.0 : 0.4, 0));
                all.put(who, rec);
                changed = true;
            }
            if (!p.isSpectator()) p.setGameMode(GameType.SPECTATOR);
            if (p.getAbilities().getFlyingSpeed() != 0f) { p.getAbilities().setFlyingSpeed(0f); p.onUpdateAbilities(); }
            if (p.getCamera() != p) p.setCamera(p);
            ServerLevel l = level(server, rec);
            Vec3 at = new Vec3(rec.getDoubleOr("x", p.getX()), rec.getDoubleOr("y", p.getY()), rec.getDoubleOr("z", p.getZ()));
            if (p.level() != l || p.position().distanceToSqr(at) > 0.04)              // keep their own view direction
                p.teleportTo(l, at.x, at.y, at.z, EnumSet.of(Relative.Y_ROT, Relative.X_ROT), 0, 0, false);
        }
        if (changed) save(server, all);
    }

    private static boolean holds(ServerPlayer q, String id) {
        var inv = q.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (holds(inv.getItem(i), id)) return true;
        return holds(q.containerMenu.getCarried(), id);
    }

    private static boolean holds(ItemStack s, String id) {
        if (s.isEmpty() || !(s.getItem() instanceof BioCapsuleItem)) return false;
        var d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && id.equals(d.copyTag().getStringOr(CAPSULE_ID, ""));
    }
}
