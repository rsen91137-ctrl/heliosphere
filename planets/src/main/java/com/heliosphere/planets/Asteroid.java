package com.heliosphere.planets;

import com.heliosphere.core.Reg;
import com.heliosphere.core.story.Story;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Asteroid 2097-ΩX: the first deadline. Fire a kinetic interceptor from a planetary-defence silo before the
 * countdown ends, or it strikes near the world spawn, blasting a crater lined with meteoric iron and platinum.
 */
public final class Asteroid {
    private Asteroid() {}

    public static class SiloBlock extends Block {
        public SiloBlock(Properties p) { super(p); }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
            if (!stack.is(Reg.get("kinetic_interceptor"))) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            MinecraftServer s = sl.getServer();
            if (Story.asteroidState(s) != 1) { player.displayClientMessage(Component.literal("§7天上没有需要拦截的目标。"), true); return InteractionResult.SUCCESS; }
            if (level.dimension() != Level.OVERWORLD || !level.canSeeSky(pos.above())) {
                player.displayClientMessage(Component.literal("§c导弹井必须建在地球上、上方露天。"), true);
                return InteractionResult.SUCCESS;
            }
            if (!player.isCreative()) stack.shrink(1);
            for (int i = 0; i < 40; i++)
                sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + 0.5, pos.getY() + 1 + i * 0.8, pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 0.02);
            sl.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 0.5, 0.3, 0.5, 0.05);
            sl.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 6f, 0.4f);
            sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4f, 0.5f);
            Story.setAsteroid(s, 2);
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("§a§l【行星防御】§r§a动能拦截弹命中！灾星 2097-ΩX 的轨道已被推离地球。"));
                p.sendSystemMessage(Component.literal("§7撞击残骸散落在近地轨道……碎片中探测到前代探索队的信标信号。"));
            }
            ItemStack reward = new ItemStack(Reg.get("meteoric_iron"), 16);
            if (!player.getInventory().add(reward)) player.drop(reward, false);
            return InteractionResult.SUCCESS;
        }
    }

    public static void impact(MinecraftServer s) {
        ServerLevel level = s.overworld();
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        var r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2, d = 120 + r.nextInt(220);
        int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("§4§l【警报】灾星 2097-ΩX 已进入大气层！§r§c撞击点：X " + x + "  Z " + z));
        }
        level.explode(null, x, y, z, 18f, Level.ExplosionInteraction.TNT);
        for (int k = 0; k < 12; k++) {
            double b = k * Math.PI * 2 / 12;
            level.explode(null, x + Math.cos(b) * 14, y - 3, z + Math.sin(b) * 14, 12f, Level.ExplosionInteraction.TNT);
        }
        BlockState iron = Reg.getBlock("meteoric_iron_ore").defaultBlockState();
        for (int i = 0; i < 160; i++) {
            BlockPos q = new BlockPos(x + r.nextInt(17) - 8, y - 14 + r.nextInt(8), z + r.nextInt(17) - 8);
            if (!level.getBlockState(q).isAir()) level.setBlock(q, i % 9 == 0 ? Reg.getBlock("sperrylite_ore").defaultBlockState() : iron, Block.UPDATE_ALL);
        }
        level.setBlock(new BlockPos(x, y - 15, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 2, z, 20, 8, 4, 8, 0);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 40f, 0.3f);
    }
}
