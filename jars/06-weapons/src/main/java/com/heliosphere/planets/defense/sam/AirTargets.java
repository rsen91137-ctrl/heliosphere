package com.heliosphere.planets.defense.sam;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.Enemy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * What an air-defence battery shoots at: hostile things in the air. Zombies, skeletons, spiders and walking
 * machines are left to the guns; drones, phantoms, ghasts, blazes, vexes, the Wither and the dragon are fair game.
 * Other modules add their own flyers ({@link #register}), e.g. the flying machines.
 */
public final class AirTargets {
    private AirTargets() {}

    private static final Set<EntityType<?>> VANILLA = Set.of(EntityType.PHANTOM, EntityType.GHAST, EntityType.BLAZE, EntityType.VEX,
            EntityType.WITHER, EntityType.ENDER_DRAGON);
    private static final List<Predicate<Mob>> FLYERS = new CopyOnWriteArrayList<>();

    /** Adds a rule naming further flying hostiles. */
    public static void register(Predicate<Mob> flyer) { FLYERS.add(flyer); }

    public static boolean is(Mob m) {
        if (!(m instanceof Enemy) || !m.isAlive()) return false;
        if (VANILLA.contains(m.getType()) || m.getNavigation() instanceof FlyingPathNavigation) return true;
        for (Predicate<Mob> p : FLYERS) if (p.test(m)) return true;
        return false;
    }
}
