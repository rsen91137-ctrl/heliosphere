package com.heliosphere.core.mixin.machine;

import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.machine.Startable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The start (0) and stop (1) buttons of a machine screen ({@link Startable}) and the entries of its recipe list
 * ({@link Startable#FIRST_RECIPE} + index), clicked on the client, handled here.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MachineButtonMixin {
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void heliosphere$start(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MachineMenu menu) || !Startable.gated(menu.machine)) return;
        if (player.level().getBlockEntity(menu.pos) instanceof Startable s) {
            if (id == 0) {
                s.heliosphere$lock(-1);
                s.heliosphere$arm(true);
                player.displayClientMessage(Component.translatable("message.heliosphere.machine_started").withStyle(ChatFormatting.GREEN), true);
            } else if (id == 1) {
                s.heliosphere$arm(false);
                player.displayClientMessage(Component.translatable("message.heliosphere.machine_stopped").withStyle(ChatFormatting.YELLOW), true);
            } else if (id >= Startable.FIRST_RECIPE && id - Startable.FIRST_RECIPE < menu.machine.recipes().size()) {   // a recipe clicked in the list
                int index = id - Startable.FIRST_RECIPE;
                s.heliosphere$arm(true);
                s.heliosphere$lock(Startable.CHOSEN + index);
                var outputs = menu.machine.recipes().get(index).outputs;
                Component what = outputs.isEmpty() ? Component.literal("?") : outputs.get(0).stack().getHoverName();
                player.displayClientMessage(Component.translatable("message.heliosphere.machine_chosen", what).withStyle(ChatFormatting.GREEN), true);
            }
        }
        cir.setReturnValue(true);
    }
}
