package com.heliosphere.rocketry.launch;

import com.heliosphere.core.Reg;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Planets;
import com.heliosphere.rocketry.entity.RocketStructure;
import com.heliosphere.rocketry.part.RocketPartBlock;
import com.heliosphere.rocketry.physics.PartSpec;
import com.heliosphere.rocketry.physics.Propellant;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared mission logic for launch consoles and landed rockets: analysis, refuelling, propellant burn-down. */
public final class MissionControl {
    private MissionControl() {}

    /** Destinations reachable from a body (the body itself means a suborbital hop). */
    public static List<String> destinations(CelestialBody from) {
        List<String> l = new ArrayList<>();
        for (CelestialBody b : Planets.landable()) l.add(b.id);
        if (from != null && !l.contains(from.id)) l.add(0, from.id);
        return l;
    }

    public static String defaultDestination(CelestialBody from) {
        if (from == null) return "earth";
        return from == SolarSystem.EARTH ? "moon" : "earth";
    }

    public static RocketAnalyzer.Report analyze(RocketStructure s, CelestialBody from, String dest) {
        CelestialBody to = SolarSystem.get(dest);
        if (from == null || to == null) {
            RocketAnalyzer.Report r = new RocketAnalyzer.Report();
            r.checks.add(new RocketAnalyzer.Check(RocketAnalyzer.Severity.FAIL, "此处无法发射（不在行星表面）"));
            return r;
        }
        return RocketAnalyzer.analyze(s.placed(), from, to);
    }

    /** Pumps canisters from the container into matching tanks. Returns the updated structure. */
    public static RocketStructure refuel(RocketStructure s, Container slots, String emptyId) {
        List<RocketStructure.Part> parts = new ArrayList<>(s.parts());
        Map<String, Propellant> byCanister = new HashMap<>();
        for (Propellant p : Propellant.values()) { String c = RocketPartBlock.canisterFor(p); if (c != null) byCanister.put(c, p); }
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack st = slots.getItem(i);
            if (st.isEmpty()) continue;
            String id = st.getItem().builtInRegistryHolder().key().identifier().getPath();
            Propellant prop = byCanister.get(id);
            if (prop == null) continue;
            int used = 0;
            for (int k = 0; k < parts.size() && used < st.getCount(); k++) {
                RocketStructure.Part p = parts.get(k);
                PartSpec spec = p.specObj();
                if (spec == null || spec.category != PartSpec.Category.TANK || spec.stores != prop) continue;
                if (p.fill() >= spec.capacity - 1e-4) continue;
                parts.set(k, p.withFill((float) spec.capacity));
                used++;
            }
            if (used > 0) {
                st.shrink(used);
                ItemStack empty = new ItemStack(Reg.get(emptyId), used);
                if (st.isEmpty()) slots.setItem(i, empty);
                else {
                    for (int j = 0; j < slots.getContainerSize() && !empty.isEmpty(); j++) {
                        ItemStack o = slots.getItem(j);
                        if (o.isEmpty()) { slots.setItem(j, empty.copy()); empty.setCount(0); }
                        else if (ItemStack.isSameItemSameComponents(o, empty) && o.getCount() < o.getMaxStackSize()) {
                            int mv = Math.min(empty.getCount(), o.getMaxStackSize() - o.getCount()); o.grow(mv); empty.shrink(mv);
                        }
                    }
                }
                slots.setChanged();
            }
        }
        return new RocketStructure(parts);
    }

    /** Summary like "LOX 12/41 · RP-1 16/16". */
    public static String fuelSummary(RocketStructure s) {
        Map<Propellant, int[]> m = new java.util.EnumMap<>(Propellant.class);
        for (RocketStructure.Part p : s.parts()) {
            PartSpec spec = p.specObj();
            if (spec == null || spec.category != PartSpec.Category.TANK) continue;
            int[] c = m.computeIfAbsent(spec.stores, k -> new int[2]);
            c[1]++;
            if (p.fill() >= spec.capacity - 1e-4) c[0]++;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Propellant, int[]> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getKey().nameZh).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
        }
        return sb.length() == 0 ? "无贮箱" : sb.toString();
    }

    /** Result of burning a mission's delta-v: what is left of the rocket and whether it was enough. */
    public record Burn(RocketStructure remaining, double shortfall, int firstStageTop) {}

    /** Burns {@code dv} m/s through the stages bottom-up, jettisoning spent stages. */
    public static Burn burn(RocketStructure s, CelestialBody from, double dv) {
        RocketAnalyzer.Report r = RocketAnalyzer.analyze(s.placed(), from, from);
        Map<Long, Integer> index = new HashMap<>();
        List<RocketStructure.Part> parts = new ArrayList<>(s.parts());
        for (int i = 0; i < parts.size(); i++) index.put(key(parts.get(i).x(), parts.get(i).y(), parts.get(i).z()), i);
        double remaining = dv;
        int jettisonTop = Integer.MIN_VALUE;
        int firstTop = Integer.MIN_VALUE;
        List<RocketAnalyzer.Stage> stages = r.stages;
        for (int si = 0; si < stages.size(); si++) {
            RocketAnalyzer.Stage st = stages.get(si);
            int top = Integer.MIN_VALUE;
            for (RocketAnalyzer.Placed p : st.parts) top = Math.max(top, p.y());
            if (si == 0) firstTop = top;
            if (remaining <= 1e-6) break;
            boolean last = si == stages.size() - 1;
            double isp = (si == 0 && from.pressureBar > 0.1) ? (st.ispSea + st.ispVac) / 2 : st.ispVac;
            double frac;
            if (st.deltaV <= remaining) {
                remaining -= st.deltaV;
                frac = 1;
            } else {
                double m1 = st.massFull / Math.exp(remaining / (isp * OrbitMath.G0));
                frac = st.propellantUsable <= 0 ? 0 : Math.min(1, (st.massFull - m1) / st.propellantUsable);
                remaining = 0;
            }
            for (RocketAnalyzer.Placed p : st.parts) {
                PartSpec spec = p.spec();
                if (spec.category != PartSpec.Category.TANK && spec.category != PartSpec.Category.SOLID_SEGMENT) continue;
                int idx = index.get(key(p.x(), p.y(), p.z()));
                RocketStructure.Part part = parts.get(idx);
                parts.set(idx, part.withFill((float) Math.max(0, part.fill() * (1 - frac))));
            }
            if (frac >= 1 && !last) jettisonTop = top;
        }
        RocketStructure out = new RocketStructure(parts);
        if (jettisonTop != Integer.MIN_VALUE) out = out.above(jettisonTop);
        return new Burn(out, remaining, firstTop);
    }

    private static long key(int x, int y, int z) { return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF); }

    public static double requiredGameDv(RocketAnalyzer.Report r) { return r.requiredDeltaV; }

    public static CelestialBody bodyHere(Level level) { return Planets.bodyOf(level); }
}
