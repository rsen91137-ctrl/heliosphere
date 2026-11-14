package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MachineWarheads;
import com.heliosphere.planets.defense.sam.MissileSpec;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Hiding underground is no longer safe.
 * <ul>
 *   <li><b>Digging.</b> A walking machine that cannot get at its target (no line of sight, not getting anywhere for
 *       three seconds) digs towards it: a stair-stepped tunnel as wide and tall as itself, block by block, cracks
 *       showing and a grinding noise giving it away. What it can break goes by its technology level, like its
 *       warheads: below level 5 not obsidian, crying obsidian or ancient debris; nothing breaks bedrock or
 *       reinforced deepslate.</li>
 *   <li><b>The rockbore drill walker</b> digs five times as fast and starts as soon as it loses sight of you; up
 *       close its drill tears into you.</li>
 *   <li><b>Bunker busters</b> ({@link MissileSpec#PENETRATOR}) fly on your position whatever is in between, bore
 *       through blocks and go off deep; a warning ring marks the ground above you.</li>
 *   <li><b>Infiltration</b>: when the home is underground, siege walkers come out of the caves around it.</li>
 *   <li><b>Sabotage</b>: siege machines that cannot get at you for fifteen seconds turn on your machines on the
 *       surface near the home.</li>
 * </ul>
 */
public final class Underground {
    private Underground() {}

    public static final String DRILL = "drill_walker";

    /** For the world test. */
    public static int blocksDug, sabotaged;

    private static final class Dig {
        Vec3 last;
        int still, progress, blocked, noSight, cooldown;
        BlockPos at, sabotage;
    }

    private static final Map<TechMob, Dig> DIG = new WeakHashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    // ---- where the target is ----------------------------------------------------------------------------------

    /** Something solid over the head (heightmap, which follows every block placed at once). */
    public static boolean covered(ServerLevel level, LivingEntity t) {
        return t.getEyeY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(t.getX()), Mth.floor(t.getZ()));
    }

    /** Underground: at least four blocks of something overhead. */
    public static boolean deep(ServerLevel level, LivingEntity t) {
        return t.getEyeY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(t.getX()), Mth.floor(t.getZ())) - 4;
    }

    private static boolean breakable(ServerLevel level, BlockPos p, BlockState s, int tier) {
        return s.getDestroySpeed(level, p) >= 0 && s.getBlock().getExplosionResistance() <= MachineWarheads.resistanceLimit(tier)
                && s.getFluidState().isEmpty();
    }

    // ---- every machine tick -------------------------------------------------------------------------------------

    public static void tick(TechMob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isNoAi() || StrikeCraft.is(mob)) return;
        TechCombat.Role r = mob.role();
        if (r == null) return;
        Dig d = DIG.computeIfAbsent(mob, m -> new Dig());
        if (d.cooldown > 0) d.cooldown--;
        LivingEntity t = mob.getTarget();
        if (!(t instanceof Player p) || !t.isAlive() || p.isCreative() || p.isSpectator() || t.distanceToSqr(mob) > 64 * 64) {
            if (d.at != null) level.destroyBlockProgress(mob.getId(), d.at, -1);
            d.still = 0; d.at = null; d.noSight = 0;
            return;
        }
        boolean sabotaging = mob.getTags().contains("heliosphere.siege") && sabotage(level, mob, r, t, d);
        if (!sabotaging && !r.flying()) dig(level, mob, r, t, d);
    }

    private static void dig(ServerLevel level, TechMob mob, TechCombat.Role r, LivingEntity t, Dig d) {
        boolean drill = DRILL.equals(r.id());
        if (mob.tickCount % 20 == 0) {
            Vec3 pos = mob.position();
            boolean sees = mob.hasLineOfSight(t);
            double moved = d.last == null ? 99 : pos.distanceTo(d.last);
            d.last = pos;
            if (sees || mob.distanceTo(t) < 2.2) { d.still = 0; d.blocked = 0; }
            else if (drill || moved < 1.2) d.still += 20;
            else d.still = 0;
        }
        if (d.still < (drill ? 20 : 60) || d.blocked > 40) {
            if (d.at != null) { level.destroyBlockProgress(mob.getId(), d.at, -1); d.at = null; }
            return;
        }
        int tier = EnemyRoster.tier(r.id());
        BlockPos feet = mob.blockPosition();
        Vec3 to = t.position().subtract(mob.position());
        boolean xMajor = Math.abs(to.x) >= Math.abs(to.z);
        BlockPos next = null;
        BlockPos cell = null;
        for (int attempt = 0; attempt < 2 && cell == null; attempt++) {
            boolean useX = attempt == 0 ? xMajor : !xMajor;
            double along = useX ? to.x : to.z;
            int hx = useX && Math.abs(along) > 0.8 ? (int) Math.signum(along) : 0;
            int hz = !useX && Math.abs(along) > 0.8 ? (int) Math.signum(along) : 0;
            int dy = to.y < -1.0 ? -1 : to.y > 1.5 ? 1 : 0;
            if (hx == 0 && hz == 0 && dy == 0) return;
            // the blocks the machine's own body would be in at the next step (and on the way there), so wide machines fit
            next = feet.offset(hx, dy, hz);
            Vec3 nextPos = new Vec3(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
            net.minecraft.world.phys.AABB here = mob.getBoundingBox().deflate(0.02);
            net.minecraft.world.phys.AABB there = here.move(nextPos.subtract(mob.position()));
            java.util.List<net.minecraft.world.phys.AABB> boxes = new java.util.ArrayList<>(java.util.List.of(there));
            if (dy > 0) boxes.add(here.move(0, 1, 0));                                  // room to rise first
            if (dy < 0 && (hx != 0 || hz != 0)) boxes.add(there.move(0, 1, 0));         // and to move over before dropping
            java.util.LinkedHashSet<BlockPos> cells = new java.util.LinkedHashSet<>();
            for (net.minecraft.world.phys.AABB box : boxes)
                for (BlockPos c : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ)))
                    cells.add(c.immutable());
            boolean stuck = false;
            for (BlockPos c : cells) {
                BlockState s = level.getBlockState(c);
                if (s.getCollisionShape(level, c).isEmpty()) continue;
                if (!breakable(level, c, s, tier)) { stuck = true; break; }
                cell = c;
                break;
            }
            if (stuck) { cell = null; next = null; continue; }
            if (cell == null) {                                                          // open: step on
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
                if (dy > 0) mob.getJumpControl().jump();
                d.blocked = 0;
                return;
            }
        }
        if (cell == null) { d.blocked++; return; }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        if (!cell.equals(d.at)) {
            if (d.at != null) level.destroyBlockProgress(mob.getId(), d.at, -1);
            d.at = cell; d.progress = 0;
        }
        BlockState s = level.getBlockState(cell);
        float need = 10 + s.getDestroySpeed(level, cell) * 22;
        d.progress += (int) Math.ceil((0.75 + 0.3 * tier) * (drill ? 3.5 : 1) * 10);
        int stage = (int) Math.min(9, d.progress / 10f / need * 10);
        level.destroyBlockProgress(mob.getId(), cell, stage);
        if (mob.tickCount % 6 == 0) {
            level.playSound(null, cell, drill ? SoundEvents.GRINDSTONE_USE : s.getSoundType().getHitSound(), SoundSource.HOSTILE, drill ? 1.4f : 1.0f, drill ? 0.6f : 0.8f);
            if (drill) level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
        }
        if (d.progress / 10f >= need) {
            level.destroyBlockProgress(mob.getId(), cell, -1);
            level.destroyBlock(cell, level.random.nextFloat() < 0.2f, mob);
            blocksDug++;
            d.at = null; d.progress = 0;
        }
    }

    /** The rockbore's drill: close in, it grinds into its target. */
    public static void drill(TechMob mob, ServerLevel level, LivingEntity target, TechCombat.Role r) {
        if (mob.distanceTo(target) > r.range()) return;
        Vec3 at = target.getEyePosition().add(0, -0.4, 0);
        CombatVfx.send(level, CombatVfx.SHOCK, 0xFFB050, at, at, 0.6f);
        level.playSound(null, mob.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.6f, 0.5f);
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        target.hurtServer(level, level.damageSources().mobAttack(mob), r.damage());
        Vec3 push = target.position().subtract(mob.position()).normalize().scale(0.5);
        target.push(push.x, 0.15, push.z);
        target.hurtMarked = true;
    }

    // ---- sabotage -----------------------------------------------------------------------------------------------

    /** Siege machines that cannot get at the player turn on the player's machines on the surface near the home. */
    private static boolean sabotage(ServerLevel level, TechMob mob, TechCombat.Role r, LivingEntity t, Dig d) {
        if (mob.tickCount % 20 == 0) {
            if (mob.hasLineOfSight(t)) { d.noSight = 0; d.sabotage = null; }
            else d.noSight += 20;
        }
        if (d.noSight < 300) return false;
        BlockPos home = com.heliosphere.hostiles.siege.Sieges.homeOf(mob);
        if (home == null) return false;
        if (d.sabotage != null && level.getBlockEntity(d.sabotage) == null) d.sabotage = null;
        if (d.sabotage == null && mob.tickCount % 40 == 0) d.sabotage = pick(level, home, mob);
        if (d.sabotage == null) return false;
        Vec3 aim = Vec3.atCenterOf(d.sabotage);
        double reach = Math.max(3, r.range() * 0.8);
        double dist = mob.getEyePosition().distanceTo(aim);
        if (r.flying()) {
            Vec3 want = aim.add(0, 6, 0).subtract(mob.position());
            mob.setDeltaMovement(want.length() > 4 ? want.normalize().scale(0.3) : mob.getDeltaMovement().scale(0.5));
        } else if (dist > reach) mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.1);
        else mob.getNavigation().stop();
        mob.getLookControl().setLookAt(aim.x, aim.y, aim.z);
        if (dist <= reach + 2 && d.cooldown <= 0) {
            var hit = level.clip(new ClipContext(mob.getEyePosition(), aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            boolean clear = hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(d.sabotage);
            if (clear) {
                d.cooldown = Math.max(20, r.interval());
                CombatVfx.send(level, CombatVfx.TRACER, 0xFF6040, mob.getEyePosition(), aim, 1.2f);
                CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, aim, aim, 1.2f);
                level.playSound(null, d.sabotage, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
                level.destroyBlock(d.sabotage, true, mob);
                sabotaged++;
                if (t instanceof ServerPlayer sp) {
                    long now = level.getGameTime();
                    Long last = WARNED.get(sp.getUUID());
                    if (last == null || now - last > 600) {
                        WARNED.put(sp.getUUID(), now);
                        sp.displayClientMessage(Component.translatable("message.heliosphere.sabotage", d.sabotage.getX(), d.sabotage.getY(), d.sabotage.getZ()).withStyle(ChatFormatting.RED), false);
                    }
                }
                d.sabotage = null;
            }
        }
        return true;
    }

    /** The nearest of the player's machines on the surface within 32 blocks of the home (not storage). */
    private static BlockPos pick(ServerLevel level, BlockPos home, TechMob mob) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int cx = (home.getX() >> 4) - 2; cx <= (home.getX() >> 4) + 2; cx++)
            for (int cz = (home.getZ() >> 4) - 2; cz <= (home.getZ() >> 4) + 2; cz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) continue;
                for (BlockEntity be : c.getBlockEntities().values()) {
                    var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                    if (key == null || !key.getNamespace().equals("heliosphere")) continue;
                    String path = key.getPath();
                    if (path.contains("crate") || path.contains("chest") || path.contains("storage") || path.contains("fabricator") || path.contains("point_defence")) continue;
                    BlockPos p = be.getBlockPos();
                    if (p.distSqr(home) > 32 * 32) continue;
                    if (p.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) - 2) continue;   // only what stands in the open
                    double dd = p.distSqr(mob.blockPosition());
                    if (dd < bestD) { bestD = dd; best = p; }
                }
            }
        return best;
    }

    // ---- caves and bunker busters (sieges, strike aircraft) --------------------------------------------------------

    /** An open cave spot near {@code around} at about its height, out of the sky, with room for a machine. */
    public static BlockPos caveSpot(ServerLevel level, BlockPos around, RandomSource r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 80; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 8 + r.nextDouble() * 22;
            int x = around.getX() + (int) (Math.cos(a) * dist), z = around.getZ() + (int) (Math.sin(a) * dist);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int dy = -6; dy <= 6; dy++) {
                int y = around.getY() + dy;
                if (y > top - 4) continue;
                m.set(x, y, z);
                if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty() || !level.getFluidState(m).isEmpty()) continue;
                if (!level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty()) continue;
                if (!level.getBlockState(m.above(2)).getCollisionShape(level, m.above(2)).isEmpty()) continue;
                BlockPos below = m.below();
                if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
                return m.immutable();
            }
        }
        return null;
    }

    /** A bunker buster from high above at the player: a warning first, then it bores down. */
    public static GuidedMissile penetrator(ServerLevel level, net.minecraft.world.entity.Entity owner, LivingEntity target, Vec3 from, Vec3 dir, float damage, int tier) {
        GuidedMissile m = GuidedMissile.launch(level, MissileSpec.PENETRATOR, owner, GuidedMissile.Side.MACHINE, target, from, dir, damage, tier).bore(8 + tier * 2);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 4f, 0.4f);
        if (target instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("message.heliosphere.penetrator_launch").withStyle(ChatFormatting.DARK_RED), false);
        return m;
    }

    /** A siege's answer to a player who will not come out: bunker busters from the sky above. */
    public static void bunkerStrike(ServerLevel level, ServerPlayer p, int count, int tier, float damage) {
        for (int i = 0; i < count; i++) {
            final int k = i;
            EnemyArsenal.later(level, 40 + i * 50, () -> {
                if (!p.isAlive() || p.level() != level) return;
                double a = level.random.nextDouble() * Math.PI * 2;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
                Vec3 from = new Vec3(p.getX() + Math.cos(a) * 30, Math.min(level.getMaxY() - 5, Math.max(top, p.getY()) + 70), p.getZ() + Math.sin(a) * 30);
                penetrator(level, null, p, from, p.position().subtract(from).normalize(), damage, tier);
            });
        }
    }
}
