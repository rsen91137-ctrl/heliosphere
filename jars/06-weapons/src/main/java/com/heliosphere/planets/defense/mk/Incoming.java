package com.heliosphere.planets.defense.mk;

import com.heliosphere.planets.tech.TechProjectile;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Artillery in the air, for the flak turret to shoot down: the machines' mortar rounds (drawn as an arc, they exist
 * only here: from, landing point, launch time, flight) and the shells of the siege drones and siege walkers (real
 * projectiles, weapon mode 2). The machines' nuclear missile is not artillery: only air-defence vehicles can stop it.
 */
public final class Incoming {
    private Incoming() {}

    /** A mortar round in flight. Its blast is skipped once {@link #cut} is set. */
    public static final class Shell {
        public final ServerLevel level;
        public final Vec3 from, land;
        public final long launched;
        public final int flight;
        public volatile boolean cut;

        Shell(ServerLevel level, Vec3 from, Vec3 land, long launched, int flight) {
            this.level = level; this.from = from; this.land = land; this.launched = launched; this.flight = flight;
        }

        /** Where it is now, on the same arc the players see (Blasts.shell). */
        public Vec3 at(long now) {
            double u = Math.max(0, Math.min(1, (now - launched) / (double) flight));
            double dx = land.x - from.x, dz = land.z - from.z;
            double apex = 8 + Math.sqrt(dx * dx + dz * dz) * 0.35;
            return new Vec3(from.x + dx * u, from.y + (land.y - from.y) * u + 4 * apex * u * (1 - u), from.z + dz * u);
        }

        double u(long now) { return (now - launched) / (double) flight; }
    }

    private static final List<Shell> SHELLS = new ArrayList<>();
    /** Rounds shot down since start (for tests). */
    public static int shotDown;

    /** Mortar rounds fired since start (for tests). */
    public static int fired;

    public static synchronized Shell mortar(ServerLevel level, Vec3 from, Vec3 land, int flight) {
        fired++;
        Shell s = new Shell(level, from, land, level.getGameTime(), flight);
        SHELLS.add(s);
        return s;
    }

    /** The round or shell in flight nearest to {@code at} within {@code range}, high enough to be engaged; null if none. */
    public static synchronized Object nearest(ServerLevel level, Vec3 at, double range) {
        long now = level.getGameTime();
        Object best = null;
        double bd = range * range;
        for (Iterator<Shell> it = SHELLS.iterator(); it.hasNext(); ) {
            Shell s = it.next();
            if (s.cut || s.u(now) > 1.05) { it.remove(); continue; }
            if (s.level != level || s.u(now) < 0.12 || s.u(now) > 0.92) continue;     // not on the rail, not in the last moment
            double d = s.at(now).distanceToSqr(at);
            if (d < bd) { bd = d; best = s; }
        }
        for (TechProjectile p : level.getEntitiesOfClass(TechProjectile.class, new AABB(at, at).inflate(range), x -> x.isAlive() && x.mode() == 2)) {
            double d = p.position().distanceToSqr(at);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    public static Vec3 position(Object o, long now) {
        return o instanceof Shell s ? s.at(now) : ((Entity) o).position();
    }

    /** Shot down: an airburst where it was, and no blast where it would have landed. */
    public static void kill(ServerLevel level, Object o) {
        Vec3 at = position(o, level.getGameTime());
        if (o instanceof Shell s) {
            s.cut = true;
            CombatVfx.send(level, CombatVfx.MORTAR, -1, s.from, s.land, -1f);          // the players' arc stops here
        } else ((Entity) o).discard();
        CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFC070, at, at, 1.2f);
        shotDown++;
    }
}
