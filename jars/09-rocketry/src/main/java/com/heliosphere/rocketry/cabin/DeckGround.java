package com.heliosphere.rocketry.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The real ground around the rocket, for the flight deck's windows: a height and a colour every {@code step} blocks
 * over a square {@code n} cells wide centred on ({@code ox}, {@code oz}), taken from the world the rocket stands in
 * (or comes down to). The deck draws it under the room, as far below as the rocket is above it, so the pad, the
 * trees and the coast fall away under you at liftoff and come up to meet you on landing. Cells in chunks that are not
 * loaded have no height ({@link #NONE}) and are left out.
 *
 * <p>{@code ax, ay, az} is where the rocket's axis meets the deck floor in the flight deck, {@code seat} how high
 * that floor is above the rocket's base.
 */
public record DeckGround(double ox, double oz, int step, int n, int ax, int ay, int az, float seat, short[] height, int[] rgb)
        implements CustomPacketPayload {
    public static final short NONE = Short.MIN_VALUE;
    public static final Type<DeckGround> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heliosphere", "deck_ground"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeckGround> CODEC = StreamCodec.of((b, g) -> {
        b.writeDouble(g.ox); b.writeDouble(g.oz); b.writeVarInt(g.step); b.writeVarInt(g.n);
        b.writeVarInt(g.ax); b.writeVarInt(g.ay); b.writeVarInt(g.az); b.writeFloat(g.seat);
        for (int i = 0; i < g.n * g.n; i++) {
            b.writeShort(g.height[i]);
            if (g.height[i] != NONE) b.writeMedium(g.rgb[i]);
        }
    }, b -> {
        double ox = b.readDouble(), oz = b.readDouble();
        int step = b.readVarInt(), n = b.readVarInt(), ax = b.readVarInt(), ay = b.readVarInt(), az = b.readVarInt();
        float seat = b.readFloat();
        short[] h = new short[n * n];
        int[] c = new int[n * n];
        for (int i = 0; i < n * n; i++) {
            h[i] = b.readShort();
            if (h[i] != NONE) c[i] = b.readUnsignedMedium();
        }
        return new DeckGround(ox, oz, step, n, ax, ay, az, seat, h, c);
    });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Radius of the snapshot (blocks) and its spacing. */
    static final int RADIUS = 144, STEP = 2;

    /** Takes the snapshot around ({@code x}, {@code z}) from chunks already in memory. Returns the share of cells filled. */
    static DeckGround take(ServerLevel level, double x, double z, BlockPos anchor, float seat) {
        int n = RADIUS * 2 / STEP + 1;
        short[] h = new short[n * n];
        int[] c = new int[n * n];
        int x0 = (int) Math.floor(x) - RADIUS, z0 = (int) Math.floor(z) - RADIUS;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        LevelChunk chunk = null;
        int ccx = Integer.MIN_VALUE, ccz = Integer.MIN_VALUE;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int bx = x0 + i * STEP, bz = z0 + j * STEP, k = j * n + i;
            int cx = bx >> 4, cz = bz >> 4;
            if (cx != ccx || cz != ccz) { chunk = level.getChunkSource().getChunkNow(cx, cz); ccx = cx; ccz = cz; }
            if (chunk == null) { h[k] = NONE; continue; }
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15);       // the highest solid or liquid block
            if (top <= level.getMinY()) { h[k] = NONE; continue; }
            at.set(bx, top, bz);
            BlockState s = chunk.getBlockState(at);
            h[k] = (short) (top + 1);
            c[k] = colour(level, s, at);
        }
        return new DeckGround(x, z, STEP, n, anchor.getX(), anchor.getY(), anchor.getZ(), seat, h, c);
    }

    /** Fraction of the snapshot's cells that came from loaded chunks. */
    public double coverage() {
        int filled = 0;
        for (short v : height) if (v != NONE) filled++;
        return filled / (double) height.length;
    }

    /** A natural colour for a block seen from above: the map colour, toned down where maps are loud. */
    private static int colour(ServerLevel level, BlockState s, BlockPos at) {
        if (s.is(Blocks.WATER) || s.getFluidState().isSource()) return 0x2F5D9A;
        MapColor m = s.getMapColor(level, at);
        if (m == MapColor.GRASS) return 0x5E8C3C;
        if (m == MapColor.PLANT) return 0x3F7A2E;
        if (m == MapColor.WATER) return 0x2F5D9A;
        if (m == MapColor.NONE) return 0x7A7A7A;
        int rgb = m.col;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        int grey = (r + g + b) / 3;                                      // a little less saturated than a map
        r = (r * 3 + grey) / 4; g = (g * 3 + grey) / 4; b = (b * 3 + grey) / 4;
        return r << 16 | g << 8 | b;
    }
}
