package com.heliosphere.planets;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.world.PlanetGeometry;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stargate: built from the core samples of all eight planets. Each jump burns a zero-point core and opens a
 * return gate at the destination, so the network grows as you travel.
 */
public final class Stargate {
    public record Open(List<String> bodies) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Heliosphere.id("stargate_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Open::bodies, Open::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Jump(String body) implements CustomPacketPayload {
        public static final Type<Jump> TYPE = new Type<>(Heliosphere.id("stargate_jump"));
        public static final StreamCodec<ByteBuf, Jump> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Jump::body, Jump::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static class GateBlock extends Block {
        public GateBlock(Properties p) { super(p); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer sp) {
                List<String> ids = new ArrayList<>();
                for (CelestialBody b : Planets.landable()) ids.add(b.id);
                ServerPlayNetworking.send(sp, new Open(ids));
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static final Identifier KEY = Heliosphere.id("stargates");

    private Stargate() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.playC2S().register(Jump.TYPE, Jump.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Jump.TYPE, (payload, ctx) -> ctx.server().execute(() -> jump(ctx.player(), payload.body())));
    }

    private static boolean nearGate(ServerPlayer p) {
        BlockPos c = p.blockPosition();
        Block gate = Reg.getBlock("stargate");
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5)))
            if (p.level().getBlockState(q).is(gate)) return true;
        return false;
    }

    private static void jump(ServerPlayer p, String bodyId) {
        CelestialBody body = SolarSystem.get(bodyId);
        if (body == null) return;
        if (!nearGate(p)) { p.displayClientMessage(Component.translatable("message.heliosphere.gate_far"), true); return; }
        ResourceKey<Level> key = Planets.dimensionOf(body);
        ServerLevel dest = key == null ? null : p.level().getServer().getLevel(key);
        if (dest == null) return;
        if (!p.isCreative()) {
            var inv = p.getInventory();
            boolean paid = false;
            for (int i = 0; i < inv.getContainerSize() && !paid; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(Reg.get("zero_point_cell"))) { s.shrink(1); paid = true; }
            }
            if (!paid) { p.displayClientMessage(Component.translatable("message.heliosphere.gate_need"), true); return; }
        }
        CompoundTag all = p.level().getServer().getCommandStorage().get(KEY);
        BlockPos target;
        if (all.contains(bodyId)) {
            int[] a = all.getIntArray(bodyId).orElse(new int[]{0, 100, 0});
            target = new BlockPos(a[0], a[1], a[2]);
        } else {
            double x = 0.5, z = 0.5;
            PlanetGeometry geo = Planets.geometry(dest);
            if (geo != null && !body.gasGiant) { x = geo.lonToX(0); z = geo.latToZ(0); }
            BlockPos col = BlockPos.containing(x, 0, z);
            dest.getChunk(col);
            int y = body.gasGiant ? 251 : dest.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
            target = new BlockPos(col.getX(), y, col.getZ());
            dest.setBlock(target.offset(2, 0, 0), Reg.getBlock("stargate").defaultBlockState(), Block.UPDATE_ALL);
            all.putIntArray(bodyId, new int[]{target.getX(), target.getY(), target.getZ()});
            p.level().getServer().getCommandStorage().set(KEY, all);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6f, 1.4f);
        p.teleportTo(dest, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
        dest.playSound(null, target, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.6f);
        // remember the gate the player came through as this world's return point
        String here = Planets.bodyOf(p.level()) == null ? null : Planets.bodyOf(p.level()).id;
        if (here != null) p.displayClientMessage(Component.literal("§b已抵达 " + body.nameZh + "。星门网络已记录此处。"), true);
    }

    public static Set<String> PLANETS = Set.of("mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune");
}
