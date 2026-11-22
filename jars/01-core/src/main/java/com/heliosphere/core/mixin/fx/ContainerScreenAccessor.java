package com.heliosphere.core.mixin.fx;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel sits, for buttons added to it (MachineButtons). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int heliosphere$left();
    @Accessor("topPos") int heliosphere$top();
    @Accessor("imageWidth") int heliosphere$width();
    @Accessor("imageHeight") int heliosphere$height();
}
