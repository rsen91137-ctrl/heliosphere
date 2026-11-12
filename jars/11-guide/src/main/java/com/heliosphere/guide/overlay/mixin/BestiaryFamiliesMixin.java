package com.heliosphere.guide.overlay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The bestiary's family tabs are a fixed row of three; this makes it a row of four and adds the Doomsday Cult,
 * whose entries come from the updated guide/enemies.json.
 */
@Mixin(targets = "com.heliosphere.guide.client.BestiaryScreen")
public abstract class BestiaryFamiliesMixin {
    private static final String CULT = "末日教团";

    @ModifyVariable(method = "init", at = @At("STORE"), ordinal = 0)
    private String[] heliosphere$families(String[] families) {
        if (families.length >= 4 && families[3] != null) return families;
        return new String[]{families[0], families[1], families[2], CULT};
    }

    @ModifyConstant(method = "init", constant = @Constant(intValue = 3))
    private int heliosphere$four(int three) { return 4; }
}
