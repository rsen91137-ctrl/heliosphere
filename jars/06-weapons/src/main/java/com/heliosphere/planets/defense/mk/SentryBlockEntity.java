package com.heliosphere.planets.defense.mk;

import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One turret: draws power from the grid like any machine, picks the nearest enemy it can see (several points of
 * the target are tried, so a robot half behind a block is still engaged), turns and fires.
 */
public class SentryBlockEntity extends BlockEntity implements EnergyNode {
    private long power;
    private int cooldown, mk;
    private UUID owner;
    // shared with the client for drawing
    float yaw, pitch;
    long lastShot;

    public SentryBlockEntity(BlockPos pos, BlockState state) { super(Sentries.BLOCK_ENTITY, pos, state); }

    public TurretKind kind() { return getBlockState().getBlock() instanceof SentryBlock b ? b.kind : TurretKind.LASER; }
    public int mk() { return mk; }
    void owner(UUID id) { owner = id; setChanged(); }

    void upgrade(int level) { mk = Math.max(mk, Math.min(TurretKind.MAX_MK, level)); sync(); }

    /** Carries over an old turret: its power and its tier (tier 1-3 = Mk 0-2). */
    public void adopt(long power, int mk, UUID owner) { this.power = power; this.mk = mk; this.owner = owner; sync(); }

    Vec3 head() { return Vec3.atBottomCenterOf(worldPosition).add(0, 1.15, 0); }

    // ---- energy ----
    @Override public long energy() { return power; }
    @Override public long capacity() { return kind().capacity(mk); }
    @Override public void setEnergy(long e) { power = Math.max(0, Math.min(capacity(), e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    /** Set down in creative mode: runs without power, from the moment it is placed. */
    private boolean creative;
    public void creative(boolean on) { creative = on; if (on) power = capacity(); setChanged(); }
    public boolean creative() { return creative; }

    // ---- combat ----
    void tick(ServerLevel level) {
        if (creative && power < capacity()) power = capacity();
        if (cooldown > 0) { cooldown--; return; }
        TurretKind k = kind();
        if (power < k.energy) { cooldown = 10; unpowered(level, k); return; }
        double range = k.range(mk);
        Vec3 head = head();
        if (k == TurretKind.FLAK && shootShell(level, k, head, range)) return;   // artillery in the air comes first
        List<Mob> targets = targets(level, head, range, k);
        if (targets.isEmpty()) { cooldown = 6; return; }
        Mob first = targets.getFirst();
        Vec3 aim = first.getBoundingBox().getCenter();
        yaw = (float) Math.toDegrees(Math.atan2(-(aim.x - head.x), aim.z - head.z));
        pitch = (float) Math.toDegrees(Math.atan2(aim.y - head.y, Math.hypot(aim.x - head.x, aim.z - head.z)));
        power -= k.energy;
        cooldown = k.cooldown(mk);
        lastShot = level.getGameTime();
        fire(level, k, head, targets);
        sync();
    }

    /** Out of power with an enemy in range: tell the owner (if near) once every ten seconds, and puff smoke. */
    private void unpowered(ServerLevel level, TurretKind k) {
        if (level.getGameTime() % 200 >= 10) return;
        Vec3 head = head();
        if (targets(level, head, k.range(mk), k).isEmpty()) return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, head.x, head.y + 0.3, head.z, 6, 0.2, 0.1, 0.2, 0.01);
        Player p = owner == null ? null : level.getPlayerByUUID(owner);
        if (p != null && p.distanceToSqr(head) < 64 * 64)
            p.displayClientMessage(Component.translatable("message.heliosphere.turret_no_power", Component.translatable("block.heliosphere." + k.id), power, k.energy)
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    private List<Mob> targets(ServerLevel level, Vec3 head, double range, TurretKind k) {
        List<Mob> out = new ArrayList<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(head, head).inflate(range))) {
            if (!(m instanceof Enemy) || !m.isAlive() || m.isSpectator()) continue;
            Vec3 c = m.getBoundingBox().getCenter();
            if (c.distanceToSqr(head) > range * range) continue;
            if (k.target == TurretKind.Target.AIR && !airborne(level, m)) continue;
            if (k == TurretKind.MISSILE && com.heliosphere.planets.defense.sam.GuidedMissile.hunted(level, m.getUUID())) continue;   // another missile has it
            if (!sees(level, head, m)) continue;
            out.add(m);
        }
        if (k == TurretKind.MISSILE) out.sort(Comparator.comparingDouble(m -> -m.getHealth()));       // missiles go for the biggest
        else if (k == TurretKind.FLAK)                                                             // flak: aircraft first, then the ground
            out.sort(Comparator.comparingInt((Mob m) -> airborne(level, m) ? 0 : 1).thenComparingDouble(m -> m.distanceToSqr(head)));
        else out.sort(Comparator.comparingDouble(m -> m.distanceToSqr(head)));
        return out;
    }

    /**
     * The flak turret against artillery: mortar rounds and siege shells in the air within its range are engaged before
     * anything else, a burst of flak at each; a burst brings the round down a little under half the time (+7% a Mk),
     * and a round shot down does not go off where it was aimed. Not the machines' nuclear missile: only air-defence
     * vehicles can stop that.
     */
    private boolean shootShell(ServerLevel level, TurretKind k, Vec3 head, double range) {
        Object shell = Incoming.nearest(level, head, range);
        if (shell == null) return false;
        Vec3 at = Incoming.position(shell, level.getGameTime());
        if (!clearTo(level, head, at)) return false;
        Vec3 aim = at.subtract(head);
        yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        pitch = (float) Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z)));
        Vec3 muzzle = head.add(aim.normalize().scale(0.7));
        power -= k.energy;
        cooldown = k.cooldown(mk);
        lastShot = level.getGameTime();
        CombatVfx.send(level, CombatVfx.TRACER, k.colour, muzzle, at, 2.4f);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.08, 0.08, 0.08, 0.02);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.9f, 1.8f);
        if (level.random.nextFloat() < 0.45f + mk * 0.07f) Incoming.kill(level, shell);
        else CombatVfx.send(level, CombatVfx.EXPLOSION, k.colour, at.add(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()), at, 0.8f);
        sync();
        return true;
    }

    /** Line of sight from the gun to a point (starting a little out from the head, so it can fire down past its own block). */
    private static boolean clearTo(ServerLevel level, Vec3 head, Vec3 p) {
        Vec3 d = p.subtract(head);
        Vec3 flat = new Vec3(d.x, 0, d.z);
        Vec3 from = flat.lengthSqr() > 1e-4 ? head.add(flat.normalize().scale(0.6)) : head;
        return level.clip(new ClipContext(from, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    static boolean airborne(ServerLevel level, Mob m) {
        return m.isNoGravity() || m.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, m.getBlockX(), m.getBlockZ()) + 2.5;
    }

    /**
     * Several points of the target are tried, so a robot half behind a block is still engaged. The ray starts a little
     * out from the head towards the target, so a turret on a wall or a roof can fire down at what is below it
     * (depression) instead of its own block hiding everything under it.
     */
    private static boolean sees(ServerLevel level, Vec3 head, Mob m) {
        AABB b = m.getBoundingBox();
        Vec3[] points = {b.getCenter(), m.getEyePosition(), new Vec3(b.getCenter().x, b.minY + 0.2, b.getCenter().z), new Vec3(b.getCenter().x, b.maxY - 0.1, b.getCenter().z)};
        Vec3 flat = new Vec3(b.getCenter().x - head.x, 0, b.getCenter().z - head.z);
        Vec3 from = flat.lengthSqr() > 1e-4 ? head.add(flat.normalize().scale(0.6)) : head;
        for (Vec3 p : points)
            if (level.clip(new ClipContext(from, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m)).getType() == HitResult.Type.MISS) return true;
        return false;
    }

    private DamageSource source(ServerLevel level) {
        Player p = owner == null ? null : level.getPlayerByUUID(owner);
        return p != null ? level.damageSources().playerAttack(p) : level.damageSources().magic();
    }

    private void hit(ServerLevel level, Mob m, float damage) {
        m.invulnerableTime = 0;
        m.hurtServer(level, source(level), damage);
    }

    /** A small burst and sparks where a shot lands. */
    private static void impact(ServerLevel level, Vec3 at, net.minecraft.core.particles.SimpleParticleType sparks, float burst) {
        CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFC070, at, at, burst);
        level.sendParticles(sparks, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
    }

    private void fire(ServerLevel level, TurretKind k, Vec3 head, List<Mob> targets) {
        float dmg = k.damage(mk);
        Mob t = targets.getFirst();
        Vec3 at = t.getBoundingBox().getCenter();
        Vec3 muzzle = head.add(at.subtract(head).normalize().scale(0.7));
        // at the muzzle: smoke for the guns, sparks for the energy weapons
        boolean energy = k == TurretKind.LASER || k == TurretKind.TESLA || k == TurretKind.SONIC || k == TurretKind.RAIL;
        level.sendParticles(energy ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, energy ? 6 : 4, 0.08, 0.08, 0.08, energy ? 0.15 : 0.02);
        if (!energy && k != TurretKind.MISSILE) level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.04, 0.04, 0.04, 0.01);
        switch (k) {
            case LASER -> {
                CombatVfx.send(level, CombatVfx.BEAM, k.colour, muzzle, at, 2.4f + mk * 0.2f);
                impact(level, at, ParticleTypes.ELECTRIC_SPARK, 0.25f);
                hit(level, t, dmg);
                t.igniteForSeconds(3);
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.9f);
            }
            case SONIC -> {
                CombatVfx.send(level, CombatVfx.RING, k.colour, head.add(0, -0.6, 0), head, (float) k.range(mk) * 0.6f);
                for (Mob m : targets.subList(0, Math.min(8, targets.size()))) {
                    Vec3 c = m.getBoundingBox().getCenter();
                    level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                }
                int n = 0;
                for (Mob m : targets) {
                    if (n++ >= 8) break;
                    hit(level, m, dmg);
                    Vec3 push = m.position().subtract(head).normalize().scale(0.9);
                    m.push(push.x, 0.35, push.z);
                    m.hurtMarked = true;
                }
                level.playSound(null, worldPosition, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 0.8f, 1.6f);
            }
            case AUTOCANNON -> {
                Vec3 spread = at.add(level.random.nextGaussian() * 0.25, level.random.nextGaussian() * 0.25, level.random.nextGaussian() * 0.25);
                CombatVfx.send(level, CombatVfx.TRACER, k.colour, muzzle, spread, 2.2f + mk * 0.15f);
                impact(level, spread, ParticleTypes.CRIT, 0.22f);
                hit(level, t, dmg);
                level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 0.8f, 1.6f + level.random.nextFloat() * 0.2f);
            }
            case FLAK -> {
                // an airburst round: scattered round an aircraft, close over a ground target (time fuze set short)
                double s = airborne(level, t) ? 1.0 : 0.35;
                Vec3 burst = at.add(level.random.nextGaussian() * s, level.random.nextGaussian() * 0.5 * s + (s < 1 ? 0.4 : 0), level.random.nextGaussian() * s);
                CombatVfx.send(level, CombatVfx.TRACER, k.colour, muzzle, burst, 2.4f);
                CombatVfx.send(level, CombatVfx.EXPLOSION, k.colour, burst, burst, 1.3f);
                for (Mob m : targets) if (m.getBoundingBox().getCenter().distanceTo(burst) < 3.5) hit(level, m, dmg);
                level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.9f, 1.7f);
            }
            case MISSILE -> {
                // a real guided missile (see GuidedMissile, TURRET): it climbs out of the pod and chases the target
                Player p = owner == null ? null : level.getPlayerByUUID(owner);
                Vec3 up = at.subtract(muzzle).normalize().add(0, 0.5, 0).normalize();
                com.heliosphere.planets.defense.sam.GuidedMissile.launch(level, com.heliosphere.planets.defense.sam.MissileSpec.TURRET, p,
                        com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE, t, head.add(0, 0.5, 0), up, dmg, 0);
                level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 1.4f, 0.7f);
            }
            case RAIL -> {
                Vec3 dir = at.subtract(muzzle).normalize();
                Vec3 end = muzzle.add(dir.scale(k.range(mk)));
                end = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t)).getLocation();
                CombatVfx.send(level, CombatVfx.BEAM, k.colour, muzzle, end, 2.6f);
                CombatVfx.send(level, CombatVfx.EXPLOSION, k.colour, end, end, 1.0f);
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(muzzle, end).inflate(1)))
                    if (m instanceof Enemy && m.isAlive() && m.getBoundingBox().inflate(0.4).clip(muzzle, end).isPresent()) hit(level, m, dmg);
                level.playSound(null, worldPosition, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.2f, 1.4f);
            }
            case TESLA -> {
                Vec3 from = head.add(0, 0.4, 0);
                float d = dmg;
                int jumps = Math.min(5, targets.size());
                for (int i = 0; i < jumps; i++) {
                    Mob m = targets.get(i);
                    Vec3 to = m.getBoundingBox().getCenter();
                    CombatVfx.send(level, CombatVfx.BEAM, k.colour, from, to, 1.5f);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 12, 0.3, 0.4, 0.3, 0.3);
                    hit(level, m, d);
                    from = to;
                    d *= 0.8f;
                }
                level.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.7f, 1.8f);
            }
        }
    }

    void status(Player p) {
        TurretKind k = kind();
        p.displayClientMessage(Component.translatable("message.heliosphere.turret_status",
                Component.translatable("block.heliosphere." + k.id), mk, power, capacity(), String.format("%.0f", k.range(mk)),
                String.format("%.1f", k.damage(mk)), String.format("%.1f", 20.0 / k.cooldown(mk)), k.energy), true);
    }

    // ---- sync and save ----
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        power = in.getLongOr("power", 0);
        creative = in.getBooleanOr("creative", false);
        mk = in.getIntOr("mk", 0);
        yaw = in.getFloatOr("yaw", 0);
        pitch = in.getFloatOr("pitch", 0);
        lastShot = in.getLongOr("shot", 0);
        String o = in.getStringOr("owner", "");
        owner = o.isEmpty() ? null : UUID.fromString(o);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("power", power);
        if (creative) out.putBoolean("creative", true);
        out.putInt("mk", mk);
        out.putFloat("yaw", yaw);
        out.putFloat("pitch", pitch);
        out.putLong("shot", lastShot);
        if (owner != null) out.putString("owner", owner.toString());
    }
}
