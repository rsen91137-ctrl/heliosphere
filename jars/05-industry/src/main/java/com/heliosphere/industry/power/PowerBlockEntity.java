package com.heliosphere.industry.power;

import com.heliosphere.core.env.Environment;
import com.heliosphere.core.machine.EnergyNet;
import com.heliosphere.core.machine.EnergyNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A generator or battery: generates (or stores), and every half second sends what it holds into the cable network
 * (machines first; a battery never feeds another battery). Fuel is loaded by hand and burnt one unit at a time.
 */
public class PowerBlockEntity extends BlockEntity implements EnergyNode {
    private long energy;
    private int fuel, burn, rate;
    /** Why it is not generating, as a translation key, or null. */
    private String why;
    /** Turbine rotor angle and speed, for drawing. */
    public float spin;
    public long spinAt;

    public PowerBlockEntity(BlockPos pos, BlockState state) { super(Power.BLOCK_ENTITY, pos, state); }

    public PowerKind kind() { return getBlockState().getBlock() instanceof PowerBlock b ? b.kind : PowerKind.WIND_TURBINE; }
    public int rate() { return rate; }

    // ---- energy ----
    @Override public long energy() { return energy; }
    @Override public long capacity() { return kind().capacity; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(capacity(), e)); setChanged(); }
    @Override public boolean canReceive() { return kind().battery; }
    @Override public boolean canExtract() { return true; }

    void tick(ServerLevel level, BlockPos pos, BlockState state) {
        PowerKind k = kind();
        int before = rate;
        if (k.battery) rate = 0;
        else {
            rate = generation(level, pos, k);
            if (rate > 0) energy = Math.min(k.capacity, energy + rate);
        }
        if (level.getGameTime() % 10 == (pos.asLong() & 7)) {
            long out = k.battery ? k.io * 10L : Math.max(2000, k.gen * 10L);
            EnergyNet.push(level, pos, this, out);
        }
        boolean lit = k.battery ? energy > k.capacity / 100 : rate > 0;
        if (state.getValue(PowerBlock.LIT) != lit) level.setBlock(pos, state.setValue(PowerBlock.LIT, lit), 3);
        if ((before > 0) != (rate > 0) || k == PowerKind.WIND_TURBINE && level.getGameTime() % 40 == 0) sync();
        setChanged();
    }

    /** This tick's output in FE, and why it is idle when it is. */
    private int generation(ServerLevel level, BlockPos pos, PowerKind k) {
        why = null;
        switch (k) {
            case WIND_TURBINE -> {
                double air = Environment.pressureBar(level);
                if (air < 0.05) { why = "message.heliosphere.power_no_air"; return 0; }
                double height = Math.max(0.15, Math.min(1.4, (pos.getY() - 50) / 90.0));
                double weather = level.isThundering() ? 1.6 : level.isRaining() ? 1.3 : 1.0;
                int blocked = 0;                                           // the rotor needs open air around it
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++)
                    if ((dx != 0 || dz != 0) && !level.getBlockState(pos.offset(dx, dy, dz)).isAir()) blocked++;
                double open = Math.max(0, 1 - blocked / 24.0);
                double gust = 0.85 + 0.15 * Math.sin(level.getGameTime() / 90.0 + pos.asLong() % 13);
                int g = (int) (k.gen * height * weather * open * gust * Math.min(1.6, Math.sqrt(air)));
                if (g <= 0) why = "message.heliosphere.power_sheltered";
                return g;
            }
            case GEOTHERMAL -> {
                int heat = 0;
                for (int dy = 1; dy <= 4; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    BlockState s = level.getBlockState(pos.offset(dx, -dy, dz));
                    if (s.getFluidState().is(FluidTags.LAVA)) heat += s.getFluidState().isSource() ? 2 : 1;
                    else if (s.is(Blocks.MAGMA_BLOCK)) heat += 1;
                }
                if (heat == 0) { why = "message.heliosphere.power_no_heat"; return 0; }
                return Math.min(k.gen, 30 + heat * 15);
            }
            default -> { }
        }
        if (k.fuel != null) {
            if (k == PowerKind.FISSION && !cooled(level, pos)) { why = "message.heliosphere.power_no_coolant"; return 0; }
            boolean onDemand = k != PowerKind.RTG;                     // an RTG's heat source decays whether used or not
            if (onDemand && energy >= k.capacity) return 0;
            if (burn <= 0) {
                if (fuel <= 0) { why = "message.heliosphere.power_no_fuel"; return 0; }
                fuel--; burn = k.fuelTicks;
            }
            burn--;
            return k.gen;
        }
        return 0;
    }

    private static boolean cooled(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) if (level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) return true;
        return false;
    }

    // ---- fuel ----
    boolean isFuel(ItemStack stack) {
        String f = kind().fuel;
        return f != null && !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(f);
    }

    void load(Player p, ItemStack stack) {
        PowerKind k = kind();
        int take = Math.min(stack.getCount(), k.maxFuel - fuel);
        if (take <= 0) { p.displayClientMessage(Component.translatable("message.heliosphere.power_fuel_full", k.maxFuel).withStyle(ChatFormatting.YELLOW), true); return; }
        fuel += take;
        if (!p.isCreative()) stack.shrink(take);
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.8f);
        p.displayClientMessage(Component.translatable("message.heliosphere.power_fueled", stack.getHoverName(), fuel, k.maxFuel).withStyle(ChatFormatting.GREEN), true);
        setChanged();
    }

    void status(Player p) {
        PowerKind k = kind();
        Component name = Component.translatable("block.heliosphere." + k.id);
        String stored = String.format("%,d/%,d", energy, k.capacity);
        if (k.battery) {
            p.displayClientMessage(Component.translatable("message.heliosphere.power_battery_status", name, stored, (int) (100 * energy / k.capacity), k.io), true);
            return;
        }
        Component state = why == null ? Component.translatable("message.heliosphere.power_running", rate) : Component.translatable(why);
        if (k.fuel != null) {
            double left = (burn + (long) fuel * k.fuelTicks) / 24000.0;
            p.displayClientMessage(Component.translatable("message.heliosphere.power_fuel_status", name, state, stored, fuel, k.maxFuel, String.format("%.1f", left)), true);
        } else p.displayClientMessage(Component.translatable("message.heliosphere.power_status", name, state, stored), true);
    }

    // ---- sync and save ----
    private void sync() { if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy = in.getLongOr("energy", 0);
        fuel = in.getIntOr("fuel", 0);
        burn = in.getIntOr("burn", 0);
        rate = in.getIntOr("rate", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("energy", energy);
        out.putInt("fuel", fuel);
        out.putInt("burn", burn);
        out.putInt("rate", rate);
    }
}
