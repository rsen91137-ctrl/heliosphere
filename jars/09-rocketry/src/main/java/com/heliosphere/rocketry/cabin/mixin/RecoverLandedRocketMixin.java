package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.PackedRocketItem;
import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The wrench on a rocket standing on its pad (the entity form) also packs the whole rocket; sneak for parts. */
@Mixin(RocketEntity.class)
public abstract class RecoverLandedRocketMixin {
    @Shadow(remap = false) private void eject(boolean b) {}

    @Inject(method = "recover", at = @At("HEAD"), cancellable = true, remap = false)
    private void heliosphere$packWhole(ServerLevel level, Player player, CallbackInfoReturnable<Integer> cir) {
        if (player != null && player.isShiftKeyDown()) return;
        RocketEntity self = (RocketEntity) (Object) this;
        ItemStack packed = PackedRocketItem.pack(self.structure());
        if (packed == null) return;
        int n = self.structure().parts().size();
        if (player == null || !player.getInventory().add(packed)) Block.popResource(level, self.blockPosition(), packed);
        if (player != null) player.displayClientMessage(Component.translatable("message.heliosphere.rocket_packed", packed.getHoverName()), false);
        eject(false);
        self.discard();
        cir.setReturnValue(n);
    }
}
