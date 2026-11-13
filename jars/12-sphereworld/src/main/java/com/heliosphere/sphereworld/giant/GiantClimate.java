package com.heliosphere.sphereworld.giant;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.env.Planets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temperature with depth in the giants, and the reports a falling explorer gets: which layer they are in, the
 * pressure and the heat. Ice giants also rain diamonds in their depths.
 */
public final class GiantClimate {
    private GiantClimate() {}

    private static final Map<UUID, Integer> LAST_LAYER = new HashMap<>();

    static void init() { ServerTickEvents.END_WORLD_TICK.register(GiantClimate::tick); }

    public static String planet(Level level) {
        if (!Planets.isGasGiant(level)) return null;
        CelestialBody b = Planets.bodyOf(level);
        return b == null ? null : b.id;
    }

    /**
     * Temperature in °C at height y. Above the cloud deck it falls to the cold of the tropopause; below the deck
     * it climbs steeply, to thousands of degrees above the ocean (Jupiter: about 10 000 °C at the metallic
     * hydrogen ocean).
     */
    public static double celsius(String planet, double y, double released) {
        boolean ice = GiantDepths.ice(planet);
        double top = ice ? -218 : planet.equals("saturn") ? -185 : -150;
        double bottom = ice ? 4800 : planet.equals("saturn") ? 7500 : 9700;
        if (y >= 400) return released + (top - released) * Math.min(1, (y - 400) / 700.0);
        if (y >= 200) return released;
        double t = Math.min(1, (200 - y) / (200.0 - GiantDepths.OCEAN_TOP));      // 0 at the deck, 1 at the ocean
        // exponential: still freezing in the upper decks, about 0 °C in the water clouds, then a furnace
        return released + (bottom - released) * (Math.exp(7 * t) - 1) / (Math.exp(7) - 1);
    }

    /** Pressure in bar at height y (about 1 bar at the deck). */
    public static double bar(String planet, double y) {
        double scale = GiantDepths.ice(planet) ? 260 : planet.equals("saturn") ? 300 : 240;
        return Math.exp((250 - y) / scale);
    }

    static int layer(String planet, double y) {
        if (y > 700) return 0;            // stratosphere and top haze
        if (y > 180) return 1;            // the main cloud deck
        if (y > -400) return 2;           // second cloud deck
        if (y > -1000) return 3;          // water clouds
        if (y > GiantDepths.OCEAN_TOP + 120) return 4;   // supercritical depths
        return 5;                         // above the ocean
    }

    private static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) return;
        String planet = planet(level);
        if (planet == null) return;
        for (ServerPlayer p : level.players()) {
            int layer = layer(planet, p.getY());
            Integer last = LAST_LAYER.put(p.getUUID(), layer);
            if (last != null && last != layer) {
                p.displayClientMessage(Component.translatable("message.heliosphere.giant_layer." + (GiantDepths.ice(planet) ? "ice." : "gas.") + layer,
                        (int) Math.round(bar(planet, p.getY())), (int) Math.round(celsius(planet, p.getY(), -110))).withStyle(layer >= 4 ? ChatFormatting.RED : ChatFormatting.GOLD), false);
            }
            if (layer == 5 && planet.equals("jupiter")) com.heliosphere.core.progress.Achievements.grant(p, "jupiter_ocean");
            if (layer >= 4 && level.random.nextFloat() < 0.5f)
                level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1, p.getZ(), 6, 3, 2, 3, 0.02);
            // diamond rain in the depths of the ice giants
            if (GiantDepths.ice(planet) && p.getY() < -900 && level.random.nextFloat() < 0.12f) {
                ItemEntity d = new ItemEntity(level, p.getX() + level.random.nextGaussian() * 6, p.getY() + 14, p.getZ() + level.random.nextGaussian() * 6,
                        new ItemStack(Items.DIAMOND));
                d.setDeltaMovement(0, -0.6, 0);
                level.addFreshEntity(d);
                com.heliosphere.core.progress.Achievements.grant(p, "diamond_rain");
            }
        }
    }
}
