package com.heliosphere.disasters.collapse.mixin;

import com.heliosphere.disasters.collapse.PortalCollapse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Lighting a Nether portal frame after the collapse: no portal forms, the frame shatters instead. */
@Mixin(PortalShape.class)
public class PortalShapeCollapseMixin {
    @Shadow @Final private Direction rightDir;
    @Shadow @Final private BlockPos bottomLeft;
    @Shadow @Final private int height;
    @Shadow @Final private int width;

    @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true)
    private void heliosphere$shatter(LevelAccessor level, CallbackInfo ci) {
        if (!(level instanceof ServerLevel sl) || !PortalCollapse.blocked(sl, false)) return;
        ci.cancel();
        List<BlockPos> inside = new ArrayList<>();
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++) inside.add(bottomLeft.relative(rightDir, i).above(j).immutable());
        PortalCollapse.forming(sl, inside, false);
    }
}
