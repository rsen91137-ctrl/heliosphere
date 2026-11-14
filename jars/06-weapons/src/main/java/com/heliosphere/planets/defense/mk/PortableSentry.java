package com.heliosphere.planets.defense.mk;

import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The portable sentry: a knee-high tripod turret you carry as an item and set down wherever you are. It shoots
 * every hostile creature and machine within 20 blocks that it can see (7 damage, a shot every 0.6 seconds, 25 FE a
 * shot), from an 8000 FE battery that stays charged when you pick it up again. It charges from any power block it
 * stands next to (battery banks, generators, machines that give power), and slowly from its solar panel by day.
 * Sneak and right-click it, or hit it if it is yours, to pick it up; right-click shows its state. It has 40 health:
 * machines that shoot it down leave it on the ground as an item, its charge kept.
 */
public class PortableSentry extends Entity {
    public static final long CAPACITY = 8000;
    public static final int COST = 25, INTERVAL = 12;
    public static final float DAMAGE = 7, RANGE = 20, HEALTH = 40;
    private static final int SOLAR = 20;                 // FE every half second by day under the open sky (2 FE/t)

    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(PortableSentry.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(PortableSentry.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> SHOT = SynchedEntityData.defineId(PortableSentry.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(PortableSentry.class, EntityDataSerializers.INT);

    private long energy;
    private float hp = HEALTH;
    private UUID owner;
    private boolean creative;
    private int cooldown;
    /** Shots fired since start (for tests). */
    public static int shots;

    public PortableSentry(EntityType<? extends PortableSentry> type, Level level) { super(type, level); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(YAW, 0f); b.define(PITCH, 0f); b.define(SHOT, -100L); b.define(CHARGE, 0);
    }

    public float aimYaw() { return entityData.get(YAW); }
    public float aimPitch() { return entityData.get(PITCH); }
    public long lastShot() { return entityData.get(SHOT); }
    /** Charge in percent (synchronised for the model's lamp). */
    public int chargePercent() { return entityData.get(CHARGE); }
    public long energy() { return energy; }

    void setup(UUID owner, long energy, boolean creative, float yaw) {
        this.owner = owner; this.creative = creative;
        this.energy = creative ? CAPACITY : Mth.clamp(energy, 0, CAPACITY);
        entityData.set(YAW, yaw);
        setYRot(yaw);
        sync();
    }

    private void sync() { entityData.set(CHARGE, (int) (energy * 100 / CAPACITY)); }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith(Entity other) { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 96 * 96; }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0, onGround() ? -0.04 : Math.max(-2, v.y - 0.08), 0);
        move(MoverType.SELF, getDeltaMovement());
        if (!(level() instanceof ServerLevel level)) return;
        if (creative) energy = CAPACITY;
        if (tickCount % 10 == 0) { charge(level); sync(); }
        if (cooldown > 0) { cooldown--; return; }
        if (energy < COST) return;
        Vec3 muzzle = muzzle();
        Mob target = null;
        double best = RANGE * RANGE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(muzzle, muzzle).inflate(RANGE))) {
            if (!(m instanceof Enemy) || !m.isAlive() || m instanceof OwnableEntity || m instanceof TamableAnimal) continue;
            Vec3 c = m.getBoundingBox().getCenter();
            double d = c.distanceToSqr(muzzle);
            if (d > best) continue;
            if (level.clip(new ClipContext(muzzle, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            best = d; target = m;
        }
        if (target == null) { cooldown = 5; return; }
        Vec3 c = target.getBoundingBox().getCenter(), d = c.subtract(muzzle);
        entityData.set(YAW, (float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        entityData.set(PITCH, (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        Player p = owner == null ? null : level.getPlayerByUUID(owner);
        DamageSource src = p != null ? level.damageSources().playerAttack(p) : level.damageSources().magic();
        target.hurtServer(level, src, DAMAGE);
        target.invulnerableTime = 0;                                      // a stream of shots, not one hit a second
        CombatVfx.send(level, CombatVfx.TRACER, 0xFFD070, muzzle, c, 0.9f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 0.9f, 1.6f);
        energy -= COST;
        cooldown = INTERVAL;
        shots++;
        entityData.set(SHOT, level.getGameTime());
    }

    Vec3 muzzle() { return position().add(0, 0.78, 0); }

    /** From any power block it stands next to, and a trickle from the sun. */
    private void charge(ServerLevel level) {
        if (energy >= CAPACITY) return;
        BlockPos at = blockPosition();
        for (BlockPos q : new BlockPos[]{at.below(), at.north(), at.south(), at.east(), at.west(), at.above()}) {
            if (!(level.getBlockEntity(q) instanceof EnergyNode n) || !n.canExtract() || n.energy() <= 0) continue;
            long take = Math.min(Math.min(400, CAPACITY - energy), n.energy());
            n.setEnergy(n.energy() - take);
            level.getBlockEntity(q).setChanged();
            energy += take;
            if (energy >= CAPACITY) return;
        }
        if (level.isBrightOutside() && level.canSeeSky(at.above())) energy = Math.min(CAPACITY, energy + SOLAR);
    }

    // ---- hands on ----

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level() instanceof ServerLevel)) return InteractionResult.SUCCESS;
        if (p.isShiftKeyDown()) { pickUp(p); return InteractionResult.SUCCESS; }
        p.displayClientMessage(Component.translatable("message.heliosphere.portable_sentry", energy, CAPACITY, (int) hp, (int) HEALTH,
                String.format("%.0f", RANGE), String.format("%.0f", DAMAGE)).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved()) return false;
        if (source.getEntity() instanceof Player p && (owner == null || owner.equals(p.getUUID()) || p.isCreative())) { pickUp(p); return true; }
        hp -= amount;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.7f, 1.4f);
        if (hp <= 0) {
            CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, position().add(0, 0.5, 0), position(), 0.8f);
            spawnAtLocation(level, asItem());
            discard();
        }
        return true;
    }

    ItemStack asItem() {
        ItemStack st = new ItemStack(Sentries.PORTABLE_ITEM);
        PortableSentryItem.setEnergy(st, creative ? CAPACITY : energy);
        return st;
    }

    private void pickUp(Player p) {
        ItemStack st = asItem();
        if (!p.getInventory().add(st)) p.drop(st, false);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1f, 1.2f);
        p.displayClientMessage(Component.translatable("message.heliosphere.portable_sentry_packed", energy, CAPACITY).withStyle(ChatFormatting.GREEN), true);
        discard();
    }

    // ---- saving ----

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        energy = in.getLongOr("energy", 0);
        hp = in.getFloatOr("hp", HEALTH);
        creative = in.getBooleanOr("creative", false);
        String o = in.getStringOr("owner", "");
        owner = o.isEmpty() ? null : UUID.fromString(o);
        entityData.set(YAW, in.getFloatOr("aim", 0));
        sync();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putLong("energy", energy);
        out.putFloat("hp", hp);
        out.putBoolean("creative", creative);
        if (owner != null) out.putString("owner", owner.toString());
        out.putFloat("aim", aimYaw());
    }
}
