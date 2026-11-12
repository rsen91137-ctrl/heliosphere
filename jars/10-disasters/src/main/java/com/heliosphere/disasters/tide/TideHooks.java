package com.heliosphere.disasters.tide;

import net.minecraft.server.level.ServerLevel;

/** Called by the mixins; kept apart so the mixin classes stay trivial. */
public final class TideHooks {
    private TideHooks() {}

    public static void recorded(ServerLevel level, int x, int y, int z, long tick) {
        long age = level.getGameTime() - tick;
        if (age < 0 || age > ImpactFx.LASTS) return;
        // the ledger ignores repeats of an impact it already holds; so do we
        for (MagmaTide.Impact i : MagmaTide.impacts(level)) if (i.started() == tick) return;
        float scale = MagmaTide.impacts(level).isEmpty() ? 1f : 0.45f;
        ImpactFx.broadcast(level, x, y, z, (int) age, scale);
        RemoteImpact.broadcast(level, x, z, scale);
    }
}
