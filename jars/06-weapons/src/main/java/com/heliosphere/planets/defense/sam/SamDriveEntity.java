package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An air-defence vehicle on the move. Its weapons and radar are stowed while it drives (as on the real ones);
 * W/S drive, A/D steer. Stand still for two seconds and it deploys again as a working vehicle where it stopped.
 */
public class SamDriveEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SamDriveEntity.class, EntityDataSerializers.INT);
    private CompoundTag carry = new CompoundTag();
    private int still, blocked;

    public SamDriveEntity(EntityType<? extends SamDriveEntity> type, Level level) { super(type, level); }

    void setup(SamKind kind, CompoundTag carry, float yaw) {
        entityData.set(KIND, kind.ordinal());
        this.carry = carry;
        setYRot(yaw);
        yRotO = yaw;
    }

    public SamKind kind() { int k = entityData.get(KIND); return SamKind.values()[Math.max(0, Math.min(SamKind.values().length - 1, k))]; }
    public int ammo() { return carry.getIntOr("sam_ammo", kind().magazine); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(KIND, 0); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public float maxUpStep() { return 1.05f; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty(); }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float scale) {
        return SamOperation.hatch(kind()).yRot((float) Math.toRadians(-getYRot()));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        ServerPlayer p = getFirstPassenger() instanceof ServerPlayer sp ? sp : null;
        Input in = p == null ? Input.EMPTY : p.getLastClientInput();
        float fwd = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        float turn = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        if (fwd != 0 || turn != 0) {
            float yaw = getYRot() + turn * 2.4f * (fwd < 0 ? -1 : 1);
            setYRot(yaw);
        }
        double speed = fwd > 0 ? 0.22 : fwd < 0 ? -0.09 : 0;
        double r = Math.toRadians(getYRot());
        Vec3 v = new Vec3(-Math.sin(r) * speed, onGround() ? -0.04 : getDeltaMovement().y - 0.08, Math.cos(r) * speed);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        still = fwd == 0 && turn == 0 ? still + 1 : 0;
        if ((still > 40 && onGround()) || (p == null && tickCount > 20)) deploy(p);
    }

    /** Back into a working vehicle where it stands, facing the nearest of the four directions. */
    boolean deploy(ServerPlayer p) {
        ServerLevel level = (ServerLevel) level();
        SamKind kind = kind();
        Direction f = Direction.fromYRot(getYRot());
        BlockPos master = BlockPos.containing(getX(), getY() + 0.01, getZ());
        for (BlockPos q : kind.footprint(master, f))
            if (!level.getBlockState(q).canBeReplaced()) {
                if (p != null && blocked++ % 40 == 0) p.displayClientMessage(Component.translatable("message.heliosphere.sam_no_room", kind.length, kind.width), true);
                still = 0;
                return false;
            }
        SamBlock b = SamSites.BLOCKS.get(kind);
        BlockState st = b.defaultBlockState().setValue(SamBlock.FACING, f);
        level.setBlock(master, st, 3);
        b.setPlacedBy(level, master, st, p, ItemStack.EMPTY);
        if (level.getBlockEntity(master) instanceof SamBlockEntity be) be.restore(carry);
        ejectPassengers();
        discard();
        if (p != null) SamOperation.board(p, master);
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(KIND, in.getIntOr("kind", 0));
        carry = in.read("carry", CompoundTag.CODEC).orElse(new CompoundTag());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("kind", entityData.get(KIND));
        out.store("carry", CompoundTag.CODEC, carry);
    }
}
