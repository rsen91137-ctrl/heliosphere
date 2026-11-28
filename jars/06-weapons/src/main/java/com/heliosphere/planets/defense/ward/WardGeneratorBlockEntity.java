package com.heliosphere.planets.defense.ward;

import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The ward generator. While powered (40 FE a tick from the grid, 80 000 FE kept) it holds a field 64 blocks across
 * (32 each way, and 24 up and down) round itself, checked every second:
 * <ul>
 *   <li>players inside get Regeneration I and Resistance I (refreshed while they stay);</li>
 *   <li>machines inside are slowed (Slowness II, and drones lose a fifth of their speed), have a 10 % chance each
 *       second of a malfunction that stops them dead for 1.5 seconds (no moving, no firing), and lose 1.5 % of their
 *       health a second, at most 4 points (a boss barely notices).</li>
 * </ul>
 * Several generators do not stack: a machine is slowed or stopped once.
 */
public class WardGeneratorBlockEntity extends BlockEntity implements EnergyNode {
    public static final int REACH = 32, HEIGHT = 24, USE = 40;
    public static final long CAPACITY = 80_000;
    public static final float MALFUNCTION_CHANCE = 0.10f, DRAIN = 0.015f, DRAIN_MAX = 4f;
    public static final int MALFUNCTION_TICKS = 30;

    private long energy;
    /** Machines hindered since start (for tests). */
    public static int malfunctions, drained;

    public WardGeneratorBlockEntity(BlockPos pos, BlockState state) { super(Wards.GENERATOR_BE, pos, state); }

    @Override public long energy() { return energy; }
    @Override public long capacity() { return CAPACITY; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(CAPACITY, e)); setChanged(); }
    @Override public boolean canReceive() { return true; }
    @Override public boolean canExtract() { return false; }

    void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean running = energy >= USE;
        if (running) { energy -= USE; setChanged(); }
        if (state.getValue(WardGeneratorBlock.ACTIVE) != running) level.setBlock(pos, state.setValue(WardGeneratorBlock.ACTIVE, running), 3);
        if (!running) return;
        long now = level.getGameTime();
        if ((now + (pos.asLong() & 15)) % 20 != 0) return;
        AABB box = new AABB(pos).inflate(REACH, HEIGHT, REACH);
        for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isSpectator())) {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false, true));
        }
        for (TechMob m : level.getEntitiesOfClass(TechMob.class, box, TechMob::isAlive)) {
            Wards.slow(m, now + 30);
            m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, true, false, false));
            var c = m.getBoundingBox().getCenter();
            if (!Wards.malfunctioning(m) && level.random.nextFloat() < MALFUNCTION_CHANCE) {
                Wards.malfunction(m, now + MALFUNCTION_TICKS);
                malfunctions++;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 24, 0.4, 0.5, 0.4, 0.35);
                level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.02);
                level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.6f, 1.8f);
            } else level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.3, 0.4, 0.3, 0.1);
            float bite = Math.min(DRAIN_MAX, m.getMaxHealth() * DRAIN);
            if (bite > 0 && m.getHealth() > bite) {                       // it wears them down; the kill is left to the guns
                m.setHealth(m.getHealth() - bite);
                drained++;
            }
        }
        if (now % 40 == 0) level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.5f, 1.3f);
    }

    void status(Player p) {
        String charge = String.format("%,d/%,d", energy, CAPACITY);
        if (energy < USE) {
            p.displayClientMessage(Component.translatable("message.heliosphere.ward_unpowered", USE, charge).withStyle(ChatFormatting.RED), false);
            return;
        }
        p.displayClientMessage(Component.translatable("message.heliosphere.ward_on", REACH * 2, charge, USE).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy = Math.max(0, Math.min(CAPACITY, in.getLongOr("energy", 0)));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("energy", energy);
    }
}
