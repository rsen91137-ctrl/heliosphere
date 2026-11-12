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
    public static final int FLAME = 7, LANCE = 8, MORTAR = 9, KAMIKAZE = 10, AEGIS = 11, TITAN = 12, BLADE = 13;

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

    public static final List<Role> NEW = List.of(
            new Role("swarm_drone", "VI型蜂群自爆机", 20, 12, 0, 16, KAMIKAZE, 10, 2.0f, true, 0.6f, 0.45f),
            new Role("mortar_walker", "迫击炮机甲", 28, 140, 12, 16, MORTAR, 80, 56, false, 1.5f, 2.0f),
            new Role("flame_walker", "焚化者步行机", 35, 150, 12, 5, FLAME, 5, 7, false, 1.3f, 2.2f),
            new Role("shield_bearer", "壁垒护盾机", 42, 170, 16, 0, AEGIS, 20, 12, false, 1.4f, 1.6f),
            new Role("laser_drone", "光矛狙击无人机", 50, 110, 8, 30, LANCE, 100, 80, true, 1.2f, 0.7f),
            new Role("stalker", "幽影刺客", 55, 90, 6, 18, BLADE, 18, 2.4f, false, 0.8f, 2.1f),
            new Role("titan_walker", "泰坦重装机甲", 90, 650, 22, 28, TITAN, 60, 64, false, 3.0f, 5.2f));

    public static List<Role> extend(List<Role> released) {
        List<Role> out = new ArrayList<>();
        for (Role r : released) {
            float[] b = REBALANCE.get(r.id());
            out.add(b == null ? r : new Role(r.id(), r.name(), r.day(), b[0], r.armor(), b[1], r.mode(), r.interval(), r.range(), r.flying(), r.width(), r.height()));
        }
        for (Role r : NEW) if (out.stream().noneMatch(o -> o.id().equals(r.id()))) out.add(r);
        return List.copyOf(out);
    }
}
