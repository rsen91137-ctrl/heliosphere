package com.heliosphere.planets.defense.sam;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A vehicle saved on the move by an earlier version: it becomes a {@link SamVehicle} where it is. */
public class SamDriveEntity extends Entity {
    private int kind;
    private CompoundTag carry = new CompoundTag();

    public SamDriveEntity(EntityType<? extends SamDriveEntity> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) { }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        SamVehicle.place(sl, SamKind.values()[Math.max(0, Math.min(SamKind.values().length - 1, kind))], position(), getYRot(), null, carry, EntitySpawnReason.LOAD);
        ejectPassengers();
        discard();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        kind = in.getIntOr("kind", 0);
        carry = in.read("carry", CompoundTag.CODEC).orElse(new CompoundTag());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("kind", kind);
        out.store("carry", CompoundTag.CODEC, carry);
    }
}
