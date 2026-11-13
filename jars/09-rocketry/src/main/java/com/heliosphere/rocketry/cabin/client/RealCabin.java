package com.heliosphere.rocketry.cabin.client;

import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * The cabin you walk in during launch and landing: a room of real blocks (floor, walls, glass windows, seats,
 * consoles, lockers, lamps) drawn where the rocket's crew deck is, instead of the old drawn cockpit. You look out
 * through real glass at the real world as the rocket climbs or comes down, with your own eyes and your own mouse;
 * the room matches the deck the server walks you on (LaunchCabin: x -2..2, z -7.1..1, its furniture), so walls and
 * furniture stop you where you see them.
 *
 * <p>Room frame: x across, z along the deck (+z towards the front window), y up from the deck floor; it turns
 * with the rocket's heading like the deck does.
 */
public final class RealCabin {
    private RealCabin() {}

    public record Part(BlockPos at, BlockState state) {}

    private static List<Part> parts;

    /** Whether the local player is walking a rocket's crew deck right now (first person). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof RocketEntity r)) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        int phase = r.phase();
        if (phase != RocketEntity.COUNTDOWN && phase != RocketEntity.ASCENT && phase != RocketEntity.DESCENT && phase != RocketEntity.SUBORBITAL) return false;
        return r.structure().parts().stream().anyMatch(p -> "crew_capsule".equals(p.spec()));
    }

    public static RocketEntity rocket() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof RocketEntity r ? r : null;
    }

    public static synchronized List<Part> parts() {
        if (parts == null) parts = build();
        return parts;
    }

    private static void put(List<Part> out, int x, int y, int z, BlockState s) { out.add(new Part(new BlockPos(x, y, z), s)); }

    private static List<Part> build() {
        List<Part> out = new ArrayList<>();
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), strip = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState(), trim = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState(), ceiling = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        // the room: inner x -3..2 (blocks), z -8..0, height 4; walls one block outside
        for (int x = -4; x <= 3; x++) for (int z = -9; z <= 1; z++) {
            boolean sideWall = x == -4 || x == 3, front = z == 1, back = z == -9;
            put(out, x, -1, z, (x + z) % 3 == 0 ? strip : floor);                       // deck
            put(out, x, 4, z, (x == -1 || x == 0) && z % 3 == 0 && !front && !back ? lamp : ceiling);
            for (int y = 0; y < 4; y++) {
                if (front) put(out, x, y, z, y == 0 || sideWall ? trim : glass);         // the big front window
                else if (sideWall) put(out, x, y, z, (y == 1 || y == 2) && z % 3 != 0 && z > -8 ? glass : (z % 3 == 0 ? trim : wall));
                else if (back) put(out, x, y, z, (x == -1 || x == 0) && y < 3 ? Blocks.IRON_DOOR.defaultBlockState() : wall);
            }
        }
        // seats facing the front window (furniture boxes 1-2)
        BlockState seat = Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        put(out, -2, 0, -1, seat);
        put(out, 1, 0, -1, seat);
        // consoles under the window and along the front sides (furniture 3-4)
        BlockState desk = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP);
        for (int x = -3; x <= 2; x++) put(out, x, 0, 0, x == -3 || x == 2 ? Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.ObserverBlock.FACING, Direction.UP) : desk);
        put(out, -3, 1, 0, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        put(out, 2, 1, 0, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        put(out, -3, 0, -1, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        put(out, 2, 0, -1, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        // equipment racks in the middle (furniture 7-8) and lockers at the back (5-6)
        put(out, -3, 0, -3, Blocks.IRON_BLOCK.defaultBlockState());
        put(out, -3, 1, -3, Blocks.IRON_BARS.defaultBlockState());
        put(out, -3, 0, -2, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.EAST));
        put(out, 2, 0, -2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.BlastFurnaceBlock.FACING, Direction.WEST));
        put(out, 2, 1, -2, Blocks.IRON_BARS.defaultBlockState());
        for (int z = -7; z <= -4; z++) {
            put(out, -3, 0, z, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.EAST));
            put(out, 2, 0, z, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.WEST));
            if (z % 2 == 0) { put(out, -3, 1, z, Blocks.CHISELED_BOOKSHELF.defaultBlockState()); put(out, 2, 1, z, Blocks.CHISELED_BOOKSHELF.defaultBlockState()); }
        }
        put(out, -1, 3, -8, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        put(out, 0, 3, -8, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        return out;
    }
}
