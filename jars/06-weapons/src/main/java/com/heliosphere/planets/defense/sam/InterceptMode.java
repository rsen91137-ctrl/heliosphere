package com.heliosphere.planets.defense.sam;

import net.minecraft.world.entity.Entity;

/**
 * What a launcher's operator has told it to intercept ("拦截对象" on the control panel): everything it can see,
 * aircraft and drones only, incoming missiles only, or nothing but the machines' nuclear missile. A launcher set to
 * nuclear missiles keeps its fire and its rounds for those and ignores every other contact, by hand as well.
 */
public enum InterceptMode {
    ALL, AIRCRAFT, MISSILES, NUKES;

    private static final InterceptMode[] VALUES = values();

    /** Translation key of the mode's name; {@code key() + ".desc"} is the sentence explaining it. */
    public String key() { return "screen.heliosphere.sam_intercept." + name().toLowerCase(java.util.Locale.ROOT); }

    /** The mode the panel's button switches to next. */
    public InterceptMode next() { return VALUES[(ordinal() + 1) % VALUES.length]; }

    /** Whether a launcher in this mode takes {@code e} as a target at all (IFF and range are checked elsewhere). */
    public boolean accepts(Entity e) {
        return switch (this) {
            case ALL -> true;
            case AIRCRAFT -> !(e instanceof GuidedMissile);
            case MISSILES -> e instanceof GuidedMissile;
            case NUKES -> nuclear(e);
        };
    }

    /** The machines' nuclear missile: the first target of every launcher that may engage missiles. */
    public static boolean nuclear(Entity e) { return e instanceof GuidedMissile m && m.spec() == MissileSpec.ICBM; }

    /** The mode with this number (as sent over the network); anything else is {@link #ALL}. */
    public static InterceptMode byIndex(int i) { return i >= 0 && i < VALUES.length ? VALUES[i] : ALL; }

    /** The mode saved under this name; anything else (an old save) is {@code fallback}. */
    public static InterceptMode byName(String name, InterceptMode fallback) {
        for (InterceptMode m : VALUES) if (m.name().equals(name)) return m;
        return fallback;
    }
}
