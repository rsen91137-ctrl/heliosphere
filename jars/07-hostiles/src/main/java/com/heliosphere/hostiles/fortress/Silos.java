package com.heliosphere.hostiles.fortress;

import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MissileSpec;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The machines' nuclear silos: the only machine weapon that carries a nuclear warhead, and never by surprise. A
 * fortress of difficulty 9 and up has one on its keep, and it wakes only from evolution stage 10 (day 216). When
 * a player is within 1500 blocks and the silo is ready, it picks the player's shelter (or where they stand) and fuels
 * for five minutes: everyone near the target and near the silo is warned at once, again at three minutes, one minute,
 * thirty seconds and every second of the last ten, and a red ring marks the target for the last minute. The missile
 * (MissileSpec.ICBM) climbs, turns and comes down at about 45 blocks a second, slow enough for air defence to shoot
 * it down; destroying the silo during the countdown stops the launch for good. After a launch it needs a day and a
 * half to reload. Its state lives with the fortress, so it works whether or not its chunks are loaded.
 */
final class Silos {
    private Silos() {}

    static final int TIER = 9, RANGE = 1500, ARM = 6000, COOLDOWN = 36000, EARTH_STAGE = 10;
    private static final TicketType TICKET = new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);

    /** Called every second for each fortress. Returns whether its saved state changed. */
    static boolean tick(ServerLevel level, Fortresses.Fort f) {
        if (f.conquered || f.siloDead || f.tier < TIER) return false;
        boolean dirty = false;
        if (f.silo == 0) { f.silo = new BlockPos(f.x, f.keepY + 1, f.z + 6).asLong(); dirty = true; }
        BlockPos pos = BlockPos.of(f.silo);
        if (!f.siloPlaced && level.isLoaded(pos)) { pos = spot(level, f); f.silo = pos.asLong(); place(level, pos); f.siloPlaced = true; dirty = true; }
        else if (f.siloPlaced && level.isLoaded(pos) && !level.getBlockState(pos).is(Fortresses.SILO)) {   // destroyed
            if (f.siloState == 1) tell(level, f, Component.translatable("event.heliosphere.silo.destroyed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            f.siloDead = true; f.siloState = 0;
            return true;
        }
        long now = level.getGameTime();
        follow(level, f);
        if (f.siloState == 0) {
            if (now < f.siloCool) return dirty;
            // nuclear weapons only in the last months (evolution stage 10, day 216), on every world
            if (com.heliosphere.hostiles.arsenal.Evolution.stage(level.getServer().overworld().getDayTime() / 24000L) < EARTH_STAGE) return dirty;
            ServerPlayer target = null;
            double best = (double) RANGE * RANGE;
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
                double d = p.distanceToSqr(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5);
                if (d < best) { best = d; target = p; }
            }
            if (target == null) return dirty;
            BlockPos aim = com.heliosphere.hostiles.siege.Sieges.shelterOf(target);
            if (aim == null || aim.distSqr(target.blockPosition()) > 300 * 300) aim = target.blockPosition();
            f.siloState = 1;
            f.siloAt = now + ARM;
            f.siloTarget = aim.asLong();
            f.siloStep = 0;
            tell(level, f, Component.translatable("event.heliosphere.silo.arming", pos.getX(), pos.getZ(), aim.getX(), aim.getZ(), ARM / 20)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            siren(level, f, true);
            return true;
        }
        // fuelling: warnings, steam at the silo, the target marked
        long left = f.siloAt - now;
        BlockPos aim = BlockPos.of(f.siloTarget);
        int[] at = {3600, 1200, 600};
        for (int i = 0; i < at.length; i++)
            if (f.siloStep <= i && left <= at[i]) {
                f.siloStep = i + 1; dirty = true;
                tell(level, f, Component.translatable("event.heliosphere.silo.countdown", left / 20, aim.getX(), aim.getZ()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                siren(level, f, i == 2);
            }
        if (left <= 400) {                                                         // the silo running before the launch, so the missile flies at once
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(pos), 2);
        }
        if (left <= 200 && left > 0) {
            tell(level, f, Component.translatable("event.heliosphere.silo.seconds", (left + 19) / 20).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            siren(level, f, false);
        }
        if (level.isLoaded(pos))
            level.sendParticles(left < 1200 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 0.02);
        if (left <= 1200) {
            int gy = level.isLoaded(aim) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, aim.getX(), aim.getZ()) : aim.getY();
            Vec3 mark = new Vec3(aim.getX() + 0.5, gy + 0.1, aim.getZ() + 0.5);
            CombatVfx.send(level, CombatVfx.WARNING, 30, mark, mark, 40f);
        }
        if (left <= 0) { launch(level, f, pos, aim); f.siloState = 0; f.siloCool = now + COOLDOWN; dirty = true; }
        return dirty;
    }

    /** A free place on the keep roof: open above, solid below (or on the ground beside it). */
    private static BlockPos spot(ServerLevel level, Fortresses.Fort f) {
        int[][] offs = {{0, 6}, {0, -6}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}, {0, 0}};
        for (int[] o : offs) {
            BlockPos at = new BlockPos(f.x + o[0], f.keepY + 1, f.z + o[1]);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && !level.getBlockState(at.below()).isAir()) return at;
        }
        int x = f.x, z = f.z + 6;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static void place(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos.below()).isAir()) level.setBlock(pos.below(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
        level.setBlock(pos, Fortresses.SILO.defaultBlockState(), 3);
    }

    private static void launch(ServerLevel level, Fortresses.Fort f, BlockPos pos, BlockPos aim) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);                       // the silo, and the target, running
        level.getChunk(aim.getX() >> 4, aim.getZ() >> 4);
        level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(pos), 2);
        level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(aim), 2);
        if (!f.siloPlaced) { pos = spot(level, f); f.silo = pos.asLong(); place(level, pos); f.siloPlaced = true; }
        int top = Math.max(pos.getY() + 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()));
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, aim.getX(), aim.getZ());
        Entity marker = EntityType.MARKER.create(level, EntitySpawnReason.TRIGGERED);
        if (marker == null) return;
        marker.setPos(aim.getX() + 0.5, gy + 0.5, aim.getZ() + 0.5);
        level.addFreshEntity(marker);
        GuidedMissile m = GuidedMissile.launch(level, MissileSpec.ICBM, null, GuidedMissile.Side.MACHINE, marker,
                new Vec3(pos.getX() + 0.5, top + 1.5, pos.getZ() + 0.5), new Vec3(0, 1, 0), 0, 10);
        lastLaunch = "from " + pos.getX() + "," + (top + 1.5) + "," + pos.getZ() + " to " + aim.getX() + "," + gy + "," + aim.getZ() + " missile alive " + m.isAlive();
        f.siloMissile = m.getUUID();
        f.siloMarker = marker.getUUID();
        launched++;
        CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFD0A0, Vec3.atCenterOf(pos).add(0, 1, 0), Vec3.atCenterOf(pos), 2.5f);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 8f, 0.3f);
        double eta = Math.sqrt(pos.distSqr(aim)) / 45 + 6;
        tell(level, f, Component.translatable("event.heliosphere.silo.launch", (int) eta, aim.getX(), aim.getZ()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        siren(level, f, true);
    }

    /** While a missile is in the air: keep its target running, and clear the marker once it is gone. */
    private static void follow(ServerLevel level, Fortresses.Fort f) {
        if (f.siloMissile == null) return;
        Entity m = level.getEntity(f.siloMissile);
        if (m != null && m.isAlive()) {
            level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(m.blockPosition()), 2);
            if (f.siloTarget != 0) level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(BlockPos.of(f.siloTarget)), 2);
            return;
        }
        if (f.siloMarker != null && level.getEntity(f.siloMarker) instanceof Entity mk) mk.discard();
        f.siloMissile = null; f.siloMarker = null;
    }

    /** Players near the target, near the silo, and the one it aims at. */
    private static void tell(ServerLevel level, Fortresses.Fort f, Component msg) {
        BlockPos aim = f.siloTarget == 0 ? null : BlockPos.of(f.siloTarget), pos = BlockPos.of(f.silo);
        for (ServerPlayer p : level.players())
            if ((aim != null && p.blockPosition().distSqr(aim) < 600 * 600) || p.blockPosition().distSqr(pos) < 300 * 300)
                p.displayClientMessage(msg, false);
    }

    private static void siren(ServerLevel level, Fortresses.Fort f, boolean loud) {
        BlockPos aim = f.siloTarget == 0 ? null : BlockPos.of(f.siloTarget);
        for (ServerPlayer p : level.players())
            if (aim != null && p.blockPosition().distSqr(aim) < 600 * 600)
                level.playSound(null, p.blockPosition(), loud ? SoundEvents.RAID_HORN.value() : SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, loud ? 4f : 1.5f, loud ? 0.5f : 0.6f);
    }

    static String lastLaunch = "";
    /** Silo launches since start (for tests). */
    static int launched;
}
