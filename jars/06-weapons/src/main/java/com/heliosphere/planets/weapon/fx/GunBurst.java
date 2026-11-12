package com.heliosphere.planets.weapon.fx;

import com.heliosphere.planets.weapon.GunItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** The gun whose shot is being resolved on the server thread, if any, and the effect its explosions get. */
public final class GunBurst {
    private GunBurst() {}

    private static final ThreadLocal<GunItem.Spec> CURRENT = new ThreadLocal<>();

    public static void begin(GunItem.Spec spec) { CURRENT.set(spec); }
    public static void end() { CURRENT.remove(); }

    /** Called for every explosion; does something only during a shot. */
    public static void exploded(ServerLevel level, Entity source, double x, double y, double z, float power) {
        GunItem.Spec spec = CURRENT.get();
        if (spec == null || spec.effect() == GunItem.Effect.NUKE) return;      // the nuke draws its own
        // draw the burst a block out of whatever it hit, so the wall does not hide half of it
        Vec3 at = new Vec3(x, y, z);
        if (source != null) at = at.add(source.getEyePosition().subtract(at).normalize());
        switch (spec.effect()) {
            case ANTIMATTER -> CombatVfx.send(level, CombatVfx.ANTIMATTER, 0xC8A0FF, at, at, power);
            case FIRE -> CombatVfx.send(level, CombatVfx.PLASMA, spec.ammo().contains("plasma") ? 0x78E6FF : 0xFFA050, at, at, power);
            default -> CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, at, at, power);
        }
    }
}
