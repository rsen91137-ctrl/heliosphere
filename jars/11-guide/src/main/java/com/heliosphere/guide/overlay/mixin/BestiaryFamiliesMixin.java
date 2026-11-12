package com.heliosphere.guide.overlay.mixin;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The bestiary's family tabs are a fixed row of three; this narrows them to quarters and adds a fourth tab
 * for the Doomsday Cult, whose entries come from the updated guide/enemies.json.
 *
 * <p>The target is named by string (the class is package-private), which the build cannot remap, so the
 * method is listed under both its development and its production name. The released class has no local
 * variable table, so the tab is added after init rather than by editing its array of names.
 */
@Mixin(targets = "com.heliosphere.guide.client.BestiaryScreen", remap = false)
public abstract class BestiaryFamiliesMixin extends Screen {
    private static final String CULT = "末日教团";

    @Shadow private String family;
    @Shadow private JsonObject selected;
    @Shadow private int page;
    @Shadow private int x;
    @Shadow private int y;
    @Shadow private int w;

    @Shadow private void button(int bx, int by, int bw, String label, Runnable action) { }

    protected BestiaryFamiliesMixin() { super(Component.empty()); }

    /** In init, the third and fourth 3s are the divisors that lay the tabs out in thirds. */
    @ModifyConstant(method = {"init", "method_25426"}, constant = @Constant(intValue = 3, ordinal = 2))
    private int heliosphere$quarterStep(int three) { return 4; }

    @ModifyConstant(method = {"init", "method_25426"}, constant = @Constant(intValue = 3, ordinal = 3))
    private int heliosphere$quarterWidth(int three) { return 4; }

    @Inject(method = {"init", "method_25426"}, at = @At("TAIL"))
    private void heliosphere$cultTab(CallbackInfo ci) {
        button(x + 12 + 3 * (w - 24) / 4, y + 34, (w - 30) / 4, CULT, () -> {
            family = CULT;
            selected = null;
            page = 0;
            rebuildWidgets();
        });
    }
}
