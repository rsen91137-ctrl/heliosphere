package com.heliosphere.rocketry.item;

import com.heliosphere.rocketry.part.RocketPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Recovery wrench. Right-click any part of an assembled rocket to take the whole vehicle apart, or a landed
 * rocket / spent booster to bring all of its parts home. Nothing is lost; propellant in the tanks is vented.
 */
public class RecoveryToolItem extends Item {
    public RecoveryToolItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos start = ctx.getClickedPos();
        if (!(level.getBlockState(start).getBlock() instanceof RocketPartBlock)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        int n = 0;
        while (!q.isEmpty() && n < 1024) {
            BlockPos p = q.poll();
            BlockState st = level.getBlockState(p);
            ItemStack drop = new ItemStack(st.getBlock());
            level.removeBlock(p, false);
            if (player == null || !player.getInventory().add(drop)) Block.popResource(level, p, drop);
            n++;
            for (Direction d : Direction.values()) {
                BlockPos nb = p.relative(d);
                if (seen.add(nb) && level.getBlockState(nb).getBlock() instanceof RocketPartBlock) q.add(nb);
            }
        }
        level.playSound(null, start, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.4f);
        if (player != null) player.displayClientMessage(Component.translatable("message.heliosphere.recovered", n), true);
        return InteractionResult.SUCCESS;
    }
}
