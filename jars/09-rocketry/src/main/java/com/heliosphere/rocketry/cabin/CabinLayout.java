package com.heliosphere.rocketry.cabin;

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
 * The crew cabin's room, block by block: deck, glass front and side windows, seats, consoles, lockers and lamps.
 * Room frame: x across, z along the deck (+z towards the front window), y up from the deck floor; the room spans
 * x -4..3, z -9..1, y -1..4 including its shell. Built for real as the flight room, and drawn on the rocket.
 */
public final class CabinLayout {
    private CabinLayout() {}

    public record Part(BlockPos at, BlockState state) {}

    private static List<Part> parts;

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
            boolean port = (x == -1 || x == 0) && z >= -6 && z <= -3;                    // a glass panel in the deck: look straight down
            put(out, x, -1, z, port ? Blocks.GLASS.defaultBlockState() : (x + z) % 3 == 0 ? strip : floor);   // deck
            put(out, x, 4, z, (x == -1 || x == 0) && z % 3 == 0 && !front && !back ? lamp : ceiling);
            for (int y = 0; y < 4; y++) {
                if (front) put(out, x, y, z, sideWall ? trim : glass);                    // the big front window, down to the deck
                else if (sideWall) put(out, x, y, z, y <= 2 && z % 3 != 0 && z > -8 ? glass : (z % 3 == 0 ? trim : wall));
                else if (back) put(out, x, y, z, (x == -1 || x == 0) && y < 2 ? Blocks.IRON_DOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoorBlock.HALF, y == 0 ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                        .setValue(net.minecraft.world.level.block.DoorBlock.HINGE, x == -1 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT)
                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.NORTH) : (x == -1 || x == 0) && y == 2 ? trim : wall);
            }
        }
        // seats facing the front window (furniture boxes 1-2)
        BlockState seat = Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        put(out, -2, 0, -1, seat);
        put(out, 1, 0, -1, seat);
        // consoles under the window and along the front sides (furniture 3-4)
        BlockState desk = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP);
        BlockState low = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();               // low consoles keep the view down free
        for (int x = -3; x <= 2; x++) put(out, x, 0, 0, x == -3 || x == 2 ? Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.ObserverBlock.FACING, Direction.UP)
                : x == -1 ? desk : low);
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
        put(out, -1, 1, 0, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.NORTH));
        return out;
    }

    /** Where the launch lever stands, in the room frame. */
    public static final BlockPos LEVER = new BlockPos(-1, 1, 0);
}
