package com.heliosphere.planets.weapon;

/**
 * How much harder player weapons hit everything but other players (see fxmixin.PlayerWeaponDamageMixin). Automatic
 * devices that strike in their owner's name (turrets, the shoulder sentry) are not weapons in hand and keep their own
 * numbers: they call {@link #automatic} around the hit.
 */
public final class PlayerDamage {
    private PlayerDamage() {}
    public static final float MULTIPLIER = 2.5f;

    /** Above 0 while an automatic device is dealing damage (server thread only). */
    private static int automatic;

    public static boolean automaticNow() { return automatic > 0; }

    public static boolean automatic(java.util.function.BooleanSupplier hit) {
        automatic++;
        try { return hit.getAsBoolean(); } finally { automatic--; }
    }
}
