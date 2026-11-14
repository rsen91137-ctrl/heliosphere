package com.heliosphere.core.mixin.machine;

import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.core.machine.GridNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heat recovery: the furnace, the smoker and the blast furnace give off power while they burn fuel, about a quarter of
 * a coal generator (furnace 10 FE/t, smoker 8, blast furnace 15). They are power sources on the grid like any
 * generator: a cable laid against one connects to it, and what it makes goes to the machines and batteries on that
 * cable. Nothing changes in how they smelt; the heat would otherwise go to waste.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceHeatMixin implements EnergyNode {
    @Unique private static final long HELIOSPHERE$CAPACITY = 2000;
    @Unique private long heliosphere$energy;

    @Override public long energy() { return heliosphere$energy; }
    @Override public long capacity() { return HELIOSPHERE$CAPACITY; }
    @Override public void setEnergy(long e) { heliosphere$energy = Math.max(0, Math.min(HELIOSPHERE$CAPACITY, e)); }
    @Override public boolean canReceive() { return false; }
    @Override public boolean canExtract() { return true; }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void heliosphere$heat(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be, CallbackInfo ci) {
        if (!state.hasProperty(AbstractFurnaceBlock.LIT) || !state.getValue(AbstractFurnaceBlock.LIT)) return;
        EnergyNode node = (EnergyNode) be;
        long rate = be instanceof BlastFurnaceBlockEntity ? 15 : be instanceof SmokerBlockEntity ? 8 : 10;
        node.setEnergy(node.energy() + rate);
        if ((level.getGameTime() + pos.asLong()) % 4 == 0) GridNet.push(level, pos, node, node.energy());
    }
}
