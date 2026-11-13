package com.heliosphere.rocketry.cabin;

import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/** Opens the released cruise cabin for a seated crew member at once, instead of after its opening shot. */
final class CruiseCabinAccess {
    private CruiseCabinAccess() {}

    private static Method open;
    private static boolean failed;

    static void open(RocketEntity rocket, ServerPlayer p) {
        if (failed) return;
        try {
            if (open == null) {
                open = Class.forName("com.heliosphere.rocketry.flight.CruiseCabin").getDeclaredMethod("open", RocketEntity.class, ServerPlayer.class);
                open.setAccessible(true);
            }
            open.invoke(null, rocket, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
        }
    }
}
