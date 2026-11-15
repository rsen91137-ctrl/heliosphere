package com.heliosphere.disasters.acid.mixin;

import com.heliosphere.core.Reg;
import com.heliosphere.core.disaster.ImpactWinter;
import com.heliosphere.disasters.acid.AcidClothBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/** Registers the acid-proof roof cloth as an {@link AcidClothBlock} that lets the light through. */
@Mixin(value = ImpactWinter.class, remap = false)
public abstract class AcidClothMixin {
    @Redirect(method = "onInitialize", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/heliosphere/core/Reg;blockWithItem(Ljava/lang/String;Ljava/util/function/Function;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)Lnet/minecraft/world/level/block/Block;"))
    private Block heliosphere$translucentCloth(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        if (!"acid_roof_cloth".equals(name)) return Reg.blockWithItem(name, factory, props);
        return Reg.blockWithItem(name, AcidClothBlock::new, props.noOcclusion()
                .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    }
}
