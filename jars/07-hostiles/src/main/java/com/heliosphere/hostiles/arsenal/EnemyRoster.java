package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.tech.TechCombat.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The machine roster: the released eleven, rebalanced against the stronger player guns, and seven new ones.
 * Role fields: id, name, first day, health, armour, damage, weapon mode, ticks between attacks, range,
 * flying, width, height.
 */
public final class EnemyRoster {
    private EnemyRoster() {}

    /** Weapon modes added by this overlay (0-6 are the released ones). */
    public static final int FLAME = 7, LANCE = 8, MORTAR = 9, KAMIKAZE = 10, AEGIS = 11, TITAN = 12, BLADE = 13,
            PLASMA = 14, GRAVITY = 15, PHASE = 16, OVERSEER = 17, CARRIER = 18, COLOSSUS = 19,
            STANDOFF = 20, STRIKE = 21, DRILL = 22;

    /** First day that never comes: the bosses, met only in fortresses. */
    private static final int NEVER = 100000;

    /** Released id -> {health, damage}. Health about x1.5 and damage about x1.4, so the late guns still kill fast. */
    private static final Map<String, float[]> REBALANCE = Map.ofEntries(
            Map.entry("patrol_drone", new float[]{36, 4}),
            Map.entry("assault_drone", new float[]{66, 7}),
            Map.entry("siege_drone", new float[]{120, 17}),
            Map.entry("missile_drone", new float[]{160, 22}),
            Map.entry("hunter_drone", new float[]{220, 26}),
            Map.entry("security_walker", new float[]{85, 8}),
            Map.entry("repair_automaton", new float[]{60, 0}),
            Map.entry("arc_hound", new float[]{72, 11}),
            Map.entry("emp_spider", new float[]{100, 7}),
            Map.entry("siege_walker", new float[]{280, 21}),
            Map.entry("rail_sentinel", new float[]{360, 34}));

    /** Released id -> first day, where it changed: the Mk V hunter drone now turns up from day 50. */
    /**
     * First day of every machine on the 150-day road to the impact (Evolution: 15 stages of ten days, each bringing one
     * or two new machines). Released machines are moved here; new ones are listed by their Role below.
     */
    private static final Map<String, Integer> FIRST_DAY = Map.ofEntries(
            Map.entry("patrol_drone", 3), Map.entry("assault_drone", 10), Map.entry("security_walker", 14), Map.entry("repair_automaton", 18),
            Map.entry("arc_hound", 22), Map.entry("siege_drone", 32), Map.entry("emp_spider", 40), Map.entry("missile_drone", 52),
            Map.entry("siege_walker", 66), Map.entry("hunter_drone", 76), Map.entry("rail_sentinel", 92));

    public static final List<Role> NEW = List.of(
            new Role("swarm_drone", "VI型蜂群自爆机", 26, 12, 0, 16, KAMIKAZE, 10, 2.0f, true, 0.6f, 0.45f),
            new Role("mortar_walker", "迫击炮机甲", 36, 140, 12, 16, MORTAR, 80, 56, false, 1.5f, 2.0f),
            new Role("flame_walker", "焚化者步行机", 45, 150, 12, 5, FLAME, 5, 7, false, 1.3f, 2.2f),
            new Role("shield_bearer", "壁垒护盾机", 56, 170, 16, 0, AEGIS, 20, 12, false, 1.4f, 1.6f),
            new Role("laser_drone", "光矛狙击无人机", 62, 110, 8, 30, LANCE, 100, 80, true, 1.2f, 0.7f),
            new Role("stalker", "幽影刺客", 72, 90, 6, 18, BLADE, 18, 2.4f, false, 0.8f, 2.1f),
            // digs through rock to whoever hides from it (Underground)
            new Role("drill_walker", "穿岩钻掘机", 82, 300, 18, 22, DRILL, 16, 3.2f, false, 1.8f, 2.2f),
            new Role("titan_walker", "泰坦重装机甲", 112, 650, 22, 28, TITAN, 60, 64, false, 3.0f, 5.2f),
            // strike aircraft: they fight from beyond visual range (StrikeCraft); the range here is only for the cannon
            new Role("strike_drone", "VIII型察打无人机", 87, 180, 10, 30, STANDOFF, 40, 28, true, 2.2f, 0.9f),
            new Role("strike_jet", "鹞隼固定翼攻击机", 104, 320, 14, 36, STRIKE, 30, 28, true, 2.8f, 1.2f),
            // Mk VII and up: fortresses and the outer worlds
            new Role("plasma_drone", "VII型等离子无人机", 122, 260, 18, 30, PLASMA, 45, 64, true, 1.6f, 0.9f),
            new Role("graviton_walker", "引力碾压机甲", 142, 420, 22, 26, GRAVITY, 50, 24, false, 2.2f, 2.9f),
            new Role("phase_stalker", "相位刺客", 132, 200, 14, 30, PHASE, 40, 3.2f, false, 0.9f, 2.3f),
            // bosses
            new Role("fortress_overseer", "堡垒监管者", NEVER, 1500, 24, 34, OVERSEER, 70, 72, false, 2.4f, 4.2f),
            new Role("hive_carrier", "蜂巢母舰", NEVER, 2200, 20, 26, CARRIER, 90, 80, true, 4.5f, 2.2f),
            new Role("colossus", "末日巨像", NEVER, 4000, 28, 44, COLOSSUS, 80, 72, false, 4.0f, 8.0f));

    /**
     * Technology level of each machine, 1 to 10: what a fortress of a given difficulty fields, and what its
     * weapons can break (level 5 and up cut through obsidian and ancient debris).
     */
    public static final Map<String, Integer> TIER = Map.ofEntries(
            Map.entry("patrol_drone", 1), Map.entry("assault_drone", 2), Map.entry("swarm_drone", 2), Map.entry("security_walker", 2),
            Map.entry("repair_automaton", 2), Map.entry("siege_drone", 3), Map.entry("arc_hound", 3), Map.entry("emp_spider", 3),
            Map.entry("missile_drone", 4), Map.entry("mortar_walker", 4), Map.entry("flame_walker", 4), Map.entry("shield_bearer", 5),
            Map.entry("siege_walker", 5), Map.entry("hunter_drone", 5), Map.entry("laser_drone", 5), Map.entry("stalker", 5),
            Map.entry("rail_sentinel", 6), Map.entry("drill_walker", 6), Map.entry("titan_walker", 7), Map.entry("plasma_drone", 7),
            Map.entry("strike_drone", 7), Map.entry("strike_jet", 8), Map.entry("graviton_walker", 8),
            Map.entry("phase_stalker", 8), Map.entry("fortress_overseer", 9), Map.entry("hive_carrier", 9), Map.entry("colossus", 10));

    public static final java.util.Set<String> BOSSES = java.util.Set.of("fortress_overseer", "hive_carrier", "colossus");

    public static int tier(String id) { return TIER.getOrDefault(id, 3); }

    /** Machines a fortress of difficulty 1-15 can field (bosses excluded). */
    public static List<String> pool(int difficulty) {
        int max = Math.min(8, (int) Math.ceil(difficulty * 0.6) + 1), min = Math.max(1, difficulty / 3);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : TIER.entrySet())
            if (!BOSSES.contains(e.getKey()) && e.getValue() <= max && e.getValue() >= min) out.add(e.getKey());
        out.sort(String::compareTo);
        return out;
    }

    /** Machines that stand guard inside a fortress or step out of its fabricators: the strike aircraft are not among them. */
    public static List<String> groundPool(int difficulty) {
        List<String> out = new ArrayList<>(pool(difficulty));
        out.removeIf(StrikeCraft::isId);
        return out;
    }

    /** The boss in the keep of a fortress of this difficulty, or null. */
    public static String boss(int difficulty) {
        if (difficulty >= 14) return "colossus";
        if (difficulty >= 12) return "hive_carrier";
        if (difficulty >= 10) return "fortress_overseer";
        if (difficulty >= 8) return "titan_walker";
        if (difficulty >= 6) return "rail_sentinel";
        return null;
    }

    public static List<Role> extend(List<Role> released) {
        List<Role> out = new ArrayList<>();
        for (Role r : released) {
            float[] b = REBALANCE.get(r.id());
            int day = FIRST_DAY.getOrDefault(r.id(), r.day());
            out.add(b == null && day == r.day() ? r : new Role(r.id(), r.name(), day, b == null ? r.hp() : b[0], r.armor(), b == null ? r.damage() : b[1],
                    r.mode(), r.interval(), r.range(), r.flying(), r.width(), r.height()));
        }
        for (Role r : NEW) if (out.stream().noneMatch(o -> o.id().equals(r.id()))) out.add(r);
        return List.copyOf(out);
    }
}
