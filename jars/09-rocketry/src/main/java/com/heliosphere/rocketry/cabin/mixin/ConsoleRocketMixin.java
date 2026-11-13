package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.launch.LaunchConsoleBlock;
import com.heliosphere.rocketry.launch.RocketScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A pre-assembled rocket stands on the pad as a whole (an entity), not as part blocks, so the console's block scan
 * found nothing. When the scan finds no rocket of blocks, the console now takes the rocket standing on its pad.
 */
@Mixin(LaunchConsoleBlock.class)
public class ConsoleRocketMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void heliosphere$rocketOnPad(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) return;
        RocketScanner.Scan scan = RocketScanner.scan(level, pos);
        if (scan != null && scan.valid()) return;
        AABB area = new AABB(pos).inflate(12, 4, 12);
        if (scan != null) for (BlockPos p : scan.pad()) area = area.minmax(new AABB(p).inflate(3, 0, 3));
        area = area.expandTowards(0, 64, 0);
        RocketEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (RocketEntity r : level.getEntitiesOfClass(RocketEntity.class, area)) {
            if (r.phase() != RocketEntity.LANDED) continue;
            double d = r.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (d < bestD) { bestD = d; best = r; }
        }
        if (best == null) return;
        player.openMenu(best);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
