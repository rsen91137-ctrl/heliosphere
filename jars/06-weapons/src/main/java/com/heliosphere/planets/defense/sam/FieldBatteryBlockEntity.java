package com.heliosphere.planets.defense.sam;

import com.heliosphere.core.machine.EnergyNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Charge held by an air-defence battery pack, handed out to the vehicles around it. Radars draw 3 FE a tick
 * (72 000 a day); a launcher draws its reload cost per missile. A full pack runs a radar for about two weeks, or
 * reloads several hundred missiles; in daylight the solar lid gives back about as much as one radar uses.
 */
public class FieldBatteryBlockEntity extends BlockEntity implements EnergyNode {
    public static final long CAPACITY = 1_000_000;
    /** Vehicles whose centre lies within this many blocks are powered. */
    public static final double RANGE = 20;
    private static final int SOLAR = 8, STEP = 10, PER_VEHICLE = 4000;

    private long energy = CAPACITY;
    private int fed;
    private boolean sun;

    public FieldBatteryBlockEntity(BlockPos pos, BlockState state) { super(SamSites.BATTERY_ENTITY, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    void tick(ServerLevel level) {
        if (level.getGameTime() % STEP != 0) return;
        sun = level.canSeeSky(worldPosition.above()) && level.isBrightOutside();
        if (sun && energy < CAPACITY) energy = Math.min(CAPACITY, energy + (long) SOLAR * STEP);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        int n = 0;
        for (SamVehicle v : SamVehicle.near(level, c, RANGE)) {
            n++;
            long want = Math.min(PER_VEHICLE, v.capacity() - v.energy());
            long give = Math.min(want, energy);
            if (give <= 0) continue;
            v.setEnergy(v.energy() + give);
            energy -= give;
            if (level.getRandom().nextInt(6) == 0) {                        // a faint spark at the vehicle being fed
                Vec3 at = v.position().add(0, 1.4, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.4, 0.3, 0.4, 0.05);
            }
        }
        if (n != fed) { fed = n; }
        setChanged();
    }

    void status(Player p) {
        p.displayClientMessage(Component.translatable("message.heliosphere.sam_battery_status",
                String.format("%,d", energy), String.format("%,d", CAPACITY), (int) (100 * energy / CAPACITY), fed, (int) RANGE,
                Component.translatable(sun ? "message.heliosphere.sam_battery_sun" : "message.heliosphere.sam_battery_dark")), true);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy = in.getLongOr("energy", CAPACITY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("energy", energy);
    }
}
