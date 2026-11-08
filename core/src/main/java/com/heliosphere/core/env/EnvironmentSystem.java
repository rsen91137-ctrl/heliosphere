package com.heliosphere.core.env;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Applies vacuum, oxygen and temperature effects to players once per second. */
public final class EnvironmentSystem {
    private EnvironmentSystem() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            ServerLevel level = p.level();
            SpaceSuits.Protection suit = SpaceSuits.of(p);
            // Microgravity: suited players may manoeuvre freely in orbit
            boolean fly = Planets.isSpace(level) && suit.sealed();
            if (p.getAbilities().mayfly != fly) {
                p.getAbilities().mayfly = fly;
                if (!fly) p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
            if (!Planets.isHeliosphereDimension(level) || Environment.sealedCabin.test(p)) continue;
            BlockPos eye = BlockPos.containing(p.getEyePosition());

            if (!Environment.breathable(level, eye)) {
                ItemStack src = SpaceSuits.oxygenSource(p);
                HelioComponents.Oxygen ox = src.get(HelioComponents.OXYGEN);
                if (!suit.sealed()) {
                    p.hurtServer(level, level.damageSources().drown(), 3.0f);
                    p.displayClientMessage(Component.translatable("message.heliosphere.no_suit"), true);
                } else if (ox == null || ox.amount() <= 0) {
                    p.hurtServer(level, level.damageSources().drown(), 2.0f);
                    p.displayClientMessage(Component.translatable("message.heliosphere.no_oxygen"), true);
                } else {
                    int use = 20;
                    if (level.dimension() == Planets.MARS && suit.tier() < 2) {
                        use = 60; // perchlorate dust erodes first-generation seals
                        if (server.getTickCount() % 200 == 0) p.displayClientMessage(Component.translatable("message.heliosphere.suit_leak"), true);
                    }
                    src.set(HelioComponents.OXYGEN, ox.withAmount(ox.amount() - use));
                    if (ox.amount() - 20 < 1200 && ox.amount() >= 1200)
                        p.displayClientMessage(Component.translatable("message.heliosphere.low_oxygen"), true);
                }
            }

            double t = Environment.temperatureC(level, eye);
            if (t > suit.maxTempC()) {
                float dmg = (float) Math.min(6, 1 + (t - suit.maxTempC()) / 60.0);
                p.hurtServer(level, level.damageSources().onFire(), dmg);
                p.displayClientMessage(Component.translatable("message.heliosphere.too_hot", (int) t), true);
            } else if (t < suit.minTempC()) {
                float dmg = (float) Math.min(6, 1 + (suit.minTempC() - t) / 60.0);
                p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 60));
                p.hurtServer(level, level.damageSources().freeze(), dmg);
                p.displayClientMessage(Component.translatable("message.heliosphere.too_cold", (int) t), true);
            }
        }
    }
}
