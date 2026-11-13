package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The operator's hatch on a parked vehicle: an invisible seat that goes away when it is left empty. */
public class SamSeat extends Entity {
    BlockPos vehicle;

    public SamSeat(EntityType<? extends SamSeat> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override protected void readAdditionalSaveData(ValueInput in) { }
    @Override protected void addAdditionalSaveData(ValueOutput out) { }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && (getPassengers().isEmpty() && tickCount > 5 || vehicle != null && !(level().getBlockEntity(vehicle) instanceof SamBlockEntity))) discard();
    }
}
