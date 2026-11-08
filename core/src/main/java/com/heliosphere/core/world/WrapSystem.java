package com.heliosphere.core.world;

import com.heliosphere.core.env.Planets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keeps players on the planet's surface map: walking past the east/west edge brings you back on the
 * other side, and crossing a pole puts you on the far meridian facing the other way.
 */
public final class WrapSystem {
    private WrapSystem() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            PlanetGeometry geo = Planets.geometry(level);
            if (geo == null) continue;
            List<ServerPlayer> players = new ArrayList<>(level.players());
            for (ServerPlayer p : players) {
                Entity mover = p.getRootVehicle();
                if (mover != p && !(mover instanceof net.minecraft.world.entity.vehicle.VehicleEntity)
                        && !(mover instanceof net.minecraft.world.entity.LivingEntity)) continue; // rockets handle themselves
                double[] w = geo.wrap(mover.getX(), mover.getZ());
                if (w == null) continue;
                boolean pole = w[2] != 0;
                float yaw = mover.getYRot() + (float) w[2];
                if (mover == p) {
                    p.teleportTo(level, w[0], p.getY(), w[1], Set.<Relative>of(), yaw, p.getXRot(), false);
                } else {
                    p.stopRiding();
                    mover.teleportTo(level, w[0], mover.getY(), w[1], Set.<Relative>of(), yaw, mover.getXRot(), false);
                    p.teleportTo(level, w[0], p.getY(), w[1], Set.<Relative>of(), yaw, p.getXRot(), false);
                    p.startRiding(mover);
                }
                if (pole) p.displayClientMessage(Component.translatable("message.heliosphere.crossed_pole"), true);
                else p.displayClientMessage(Component.translatable("message.heliosphere.crossed_meridian"), true);
            }
        }
    }
}
