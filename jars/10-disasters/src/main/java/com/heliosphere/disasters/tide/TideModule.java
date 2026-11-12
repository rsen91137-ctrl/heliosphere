package com.heliosphere.disasters.tide;

import net.fabricmc.api.ModInitializer;

/** Entry point of the disasters overlay: the global magma tide and the impact effect. */
public final class TideModule implements ModInitializer {
    @Override
    public void onInitialize() {
        ImpactFx.init();
        RemoteImpact.init();
        MagmaTide.init();
        ImpactBlast.init();
    }
}
