package com.heliosphere.core.progress;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the "War & Exploration" advancements (data/heliosphere/advancement/war/). They are ordinary advancements
 * with one {@code minecraft:impossible} criterion named "done", awarded from code when the deed is done, so the
 * game shows them exactly like its own: the toast, the chat line and the advancement screen.
 */
public final class Achievements {
    private Achievements() {}

    public static void grant(ServerPlayer p, String path) {
        if (p == null || p.level().getServer() == null) return;
        AdvancementHolder adv = p.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("heliosphere", "war/" + path));
        if (adv == null) return;
        if (!p.getAdvancements().getOrStartProgress(adv).isDone()) p.getAdvancements().award(adv, "done");
    }
}
