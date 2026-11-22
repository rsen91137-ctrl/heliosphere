package com.heliosphere.core.mixin.fx;

import com.heliosphere.core.client.screen.MachineScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far a machine screen's recipe list is scrolled, to tell which entry was clicked (MachineButtons). */
@Mixin(value = MachineScreen.class, remap = false)
public interface MachineScreenAccessor {
    @Accessor(value = "scroll", remap = false) int heliosphere$scroll();
}
