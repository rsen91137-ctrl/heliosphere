package com.heliosphere.foods.cuisine.mixin;

import com.heliosphere.foods.cuisine.HydroponicPlanterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The hydroponic planter for every crop (wheat, carrots, potatoes, beetroot and the expansion's): planted right on it,
 * no farmland needed, and growing as on watered farmland since the tray holds the nutrient solution.
 */
@Mixin(CropBlock.class)
public abstract class CropBlockPlanterMixin {
    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void heliosphere$onPlanter(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof HydroponicPlanterBlock) cir.setReturnValue(true);
    }

    @Inject(method = "getGrowthSpeed", at = @At("RETURN"), cancellable = true)
    private static void heliosphere$planterWater(Block block, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float bonus = HydroponicPlanterBlock.growthBonus(level, pos);
        if (bonus > 0) cir.setReturnValue(cir.getReturnValueF() + bonus);
    }
}
