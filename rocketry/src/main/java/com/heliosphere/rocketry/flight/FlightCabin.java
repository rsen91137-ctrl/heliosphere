package com.heliosphere.rocketry.flight;

import com.heliosphere.core.env.Environment;
import com.heliosphere.rocketry.Rocketry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The crew compartment during a coast: a small pressurised room with windows onto space, spin gravity, a bunk,
 * lockers and the flight console. Built beside the rocket in the space dimension and removed on arrival.
 */
public final class FlightCabin {
    public static final int HALF = 4, HEIGHT = 5;
    /** Set by the client to open the telemetry screen. */
    public static Runnable openClient = () -> {};

    private FlightCabin() {}

    public static class ConsoleBlock extends Block {
        public ConsoleBlock(Properties p) { super(p); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide()) openClient.run();
            return InteractionResult.SUCCESS;
        }
    }

    public static BlockPos origin(BlockPos rocket) { return rocket.offset(24, -4, 0); }

    public static void build(ServerLevel l, BlockPos o) {
        BlockState hull = Blocks.WHITE_CONCRETE.defaultBlockState(), floor = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.TINTED_GLASS.defaultBlockState(), window = Blocks.GLASS.defaultBlockState(), light = Blocks.SEA_LANTERN.defaultBlockState();
        for (int dx = -HALF; dx <= HALF; dx++) for (int dz = -HALF; dz <= HALF; dz++) for (int dy = 0; dy <= HEIGHT; dy++) {
            BlockPos p = o.offset(dx, dy, dz);
            boolean wallX = Math.abs(dx) == HALF, wallZ = Math.abs(dz) == HALF;
            BlockState st;
            if (dy == 0) st = (dx + dz) % 2 == 0 ? floor : Blocks.GRAY_CONCRETE.defaultBlockState();
            else if (dy == HEIGHT) st = (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) ? light : hull;
            else if (wallX || wallZ) {
                boolean win = dy >= 2 && dy <= 3 && ((wallX && Math.abs(dz) <= 2) || (wallZ && Math.abs(dx) <= 2));
                st = win ? window : (dy == 1 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : hull);
            } else st = Blocks.AIR.defaultBlockState();
            l.setBlock(p, st, Block.UPDATE_CLIENTS);
        }
        l.setBlock(o.offset(0, 1, -HALF + 1), Rocketry.FLIGHT_CONSOLE.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(o.offset(-1, 1, -HALF + 1), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
        l.setBlock(o.offset(1, 1, -HALF + 1), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
        l.setBlock(o.offset(HALF - 1, 1, HALF - 1), Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(o.offset(HALF - 1, 1, HALF - 2), Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(o.offset(-HALF + 1, 1, HALF - 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(o.offset(-HALF + 1, 1, 0), Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_CLIENTS);
        l.setBlock(o.offset(-HALF + 1, 1, 1), Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_CLIENTS);
        Environment.addBubble(l, o.offset(0, 2, 0), HALF + 1);
    }

    public static void remove(ServerLevel l, BlockPos o) {
        Environment.removeBubble(l, o.offset(0, 2, 0));
        for (int dx = -HALF; dx <= HALF; dx++) for (int dz = -HALF; dz <= HALF; dz++) for (int dy = 0; dy <= HEIGHT; dy++)
            l.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
