package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.defense.DefenseTurrets;
import com.heliosphere.planets.defense.TurretBlockEntity;
import com.heliosphere.planets.defense.mk.Sentries;
import com.heliosphere.planets.defense.mk.SentryBlockEntity;
import com.heliosphere.planets.defense.mk.TurretKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * The released laser and sonic turrets become the new ones the first time they tick: same place, same stored
 * power and owner, their tier (1-3) carried over as Mk 0-2.
 */
@Mixin(value = TurretBlockEntity.class, remap = false)
public abstract class OldTurretMixin {
    @Shadow private long power;
    @Shadow private UUID owner;
    @Shadow public abstract int tier();

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true, remap = true)          // remap the descriptor's Minecraft types
    private void heliosphere$replace(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci) {
        ci.cancel();
        TurretKind kind = state.getBlock() == DefenseTurrets.SONIC ? TurretKind.SONIC : TurretKind.LASER;
        long stored = power;
        UUID who = owner;
        int mk = Math.max(0, tier() - 1);
        level.setBlock(pos, Sentries.BLOCKS.get(kind).defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof SentryBlockEntity be) be.adopt(stored, mk, who);
    }
}
