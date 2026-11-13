package com.heliosphere.hostiles.arsenal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/** The world's day, as the player sees it: the Overworld clock (sleeping and /time move it), not the raw tick count. */
public final class EnemyDays {
    private EnemyDays() {}

    private static MinecraftServer server;

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
    }

    public static long day() {
        return server == null ? 0 : server.overworld().getDayTime() / 24000L;
    }

    /** Machines that do not count against the natural deployment caps: fortress garrisons, fabricator output, sieges. */
    public static boolean exempt(net.minecraft.world.entity.Entity e) {
        var tags = e.getTags();
        return tags.contains("heliosphere.fortress") || tags.contains("heliosphere.siege");
    }
}
