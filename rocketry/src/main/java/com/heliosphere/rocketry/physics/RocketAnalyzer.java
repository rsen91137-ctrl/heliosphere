package com.heliosphere.rocketry.physics;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.OrbitMath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure physics check of an assembled rocket: staging, Tsiolkovsky delta-v, thrust-to-weight,
 * propellant plumbing, symmetry, structural loads and mission systems.
 */
public final class RocketAnalyzer {
    /** Mission delta-v requirements are real values multiplied by this to fit block-sized rockets. */
    public static double gameDeltaVScale = 0.4;

    private RocketAnalyzer() {}

    /** A part placed at a position relative to the rocket origin, with its current load. */
    public record Placed(int x, int y, int z, PartSpec spec, double fill, boolean tested, double quality) {
        double mass() { return spec.dryMass + fill; }
        long key() { return pack(x, y, z); }
    }

    public enum Severity { OK, WARN, FAIL }

    /** Failure that will happen if the player launches anyway. Ordered by when it strikes. */
    public enum Failure {
        NONE, NO_IGNITION, PAD_EXPLOSION, NO_LIFTOFF, TUMBLE, BREAKUP, FALL_BACK, ABORT_RETURN, BURN_UP,
        CRASH_LANDING, TIP_OVER, SUFFOCATION
    }

    public record Check(Severity severity, String text) {}

    public static final class Stage {
        public final int index;
        public final List<Placed> parts = new ArrayList<>();
        public double massFull, propellantUsable, propellantUnusable, thrustVac, thrustSea, ispVac, ispSea, deltaV;
        Stage(int index) { this.index = index; }
    }

    public static final class Report {
        public final List<Check> checks = new ArrayList<>();
        public final List<Stage> stages = new ArrayList<>();
        public double totalMass, totalDeltaV, twr, requiredDeltaV;
        public OrbitMath.Mission mission;
        public Failure predicted = Failure.NONE;
        public double failureChance;
        public boolean hasEscapeTower, hasCapsule;
        public int partCount;

        public boolean hasFail() { return checks.stream().anyMatch(c -> c.severity == Severity.FAIL); }
        void ok(String t) { checks.add(new Check(Severity.OK, t)); }
        void warn(String t) { checks.add(new Check(Severity.WARN, t)); }
        void fail(String t, Failure f) {
            checks.add(new Check(Severity.FAIL, t));
            if (predicted == Failure.NONE || f.ordinal() < predicted.ordinal()) predicted = f;
        }
    }

    public static Report analyze(List<Placed> parts, CelestialBody from, CelestialBody to) {
        Report r = new Report();
        r.partCount = parts.size();
        if (parts.isEmpty()) { r.fail("发射台上没有火箭部件", Failure.NO_IGNITION); return r; }
        Map<Long, Placed> byPos = new HashMap<>();
        for (Placed p : parts) byPos.put(p.key(), p);

        // ---------------------------------------------------------- staging by decoupler layers
        TreeSet<Integer> decouplerLayers = new TreeSet<>();
        for (Placed p : parts) if (p.spec.category == PartSpec.Category.DECOUPLER) decouplerLayers.add(p.y);
        List<Integer> bounds = new ArrayList<>(decouplerLayers);
        int stageCount = bounds.size() + 1;
        for (int i = 0; i < stageCount; i++) r.stages.add(new Stage(i + 1));
        for (Placed p : parts) {
            int s = 0;
            while (s < bounds.size() && p.y > bounds.get(s)) s++;
            r.stages.get(s).parts.add(p);
        }
        r.stages.removeIf(s -> s.parts.isEmpty());

        // ---------------------------------------------------------- systems inventory
        Stage top = r.stages.get(r.stages.size() - 1);
        int navTier = 0, legs = 0, batteries = 0;
        double life = 0, shield = 0;
        boolean parachute = false, nose = false, fins = false;
        for (Placed p : parts) {
            switch (p.spec.category) {
                case AVIONICS -> navTier = Math.max(navTier, p.spec.navTier);
                case CAPSULE -> { r.hasCapsule = true; life += p.spec.lifeSupportDays; }
                case LIFE_SUPPORT -> life += p.spec.lifeSupportDays;
                case BATTERY -> batteries++;
                case NOSE_CONE -> nose = true;
                case FIN -> fins = true;
                case ESCAPE_TOWER -> r.hasEscapeTower = true;
                default -> {}
            }
        }
        for (Placed p : top.parts) {
            switch (p.spec.category) {
                case HEAT_SHIELD -> shield = Math.max(shield, p.spec.shieldRatingKmS);
                case PARACHUTE -> parachute = true;
                case LANDING_LEG -> legs++;
                default -> {}
            }
        }

        boolean suborbital = from == to;
        OrbitMath.Mission m = suborbital
                ? new OrbitMath.Mission(from, to, from.pressureBar > 0.1 ? 2.0 : 0.8, 0, 0, parachute ? 0 : 0.4, 0, false, 2.0)
                : OrbitMath.plan(from, to, shield > 0, parachute);
        r.mission = m;
        double airPenalty = (from.pressureBar > 0.1 && !nose) ? 1.06 : 1.0;
        r.requiredDeltaV = m.totalKmS() * 1000 * gameDeltaVScale * airPenalty;

        // ---------------------------------------------------------- per-stage propulsion
        boolean anyIgnition = false;
        double massAbove = 0;
        for (int i = r.stages.size() - 1; i >= 0; i--) {
            Stage s = r.stages.get(i);
            double own = 0;
            for (Placed p : s.parts) own += p.mass();
            s.massFull = own + massAbove;
            massAbove = s.massFull;
        }
        r.totalMass = r.stages.get(0).massFull;

        for (int i = 0; i < r.stages.size(); i++) {
            Stage s = r.stages.get(i);
            boolean firstStage = i == 0;
            evaluateStage(s, byPos, r, firstStage && from.pressureBar > 0.1);
            if (s.thrustVac > 0 && s.propellantUsable > 0) anyIgnition = true;
            double m0 = s.massFull, m1 = m0 - s.propellantUsable;
            double isp = (firstStage && from.pressureBar > 0.1) ? (s.ispSea + s.ispVac) / 2 : s.ispVac;
            s.deltaV = (s.propellantUsable > 0 && isp > 0) ? isp * OrbitMath.G0 * Math.log(m0 / m1) : 0;
            r.totalDeltaV += s.deltaV;
        }

        // ---------------------------------------------------------- checks, in launch order
        double g = from.surfaceGravity * OrbitMath.G0;
        Stage first = r.stages.get(0);
        double liftThrust = from.pressureBar > 0.1 ? first.thrustSea : first.thrustVac;
        r.twr = liftThrust / (first.massFull * g);

        if (!anyIgnition) r.fail("没有可工作的发动机和推进剂组合", Failure.NO_IGNITION);
        if (first.thrustVac <= 0) r.fail("第一级没有发动机", Failure.NO_IGNITION);
        else if (first.propellantUsable <= 0) r.fail("第一级发动机没有可用推进剂（检查贮箱是否加注、是否与发动机相连）", Failure.NO_IGNITION);

        if (r.twr < 1.0) r.fail(String.format("起飞推重比 %.2f < 1.0，火箭无法离开发射台", r.twr), Failure.NO_LIFTOFF);
        else if (r.twr < 1.15) r.warn(String.format("起飞推重比 %.2f 偏低（建议 ≥ 1.15），重力损失大", r.twr));
        else r.ok(String.format("起飞推重比 %.2f", r.twr));

        checkSymmetry(r, first, fins);
        checkStructure(r, parts, from);
        checkEngineQuality(r, parts);

        // systems
        if (!r.hasCapsule) r.fail("缺少载人指令舱（玩家需要乘坐）", Failure.NO_IGNITION);
        boolean sub = from == to;
        if (navTier == 0 && sub) r.ok("无制导探空飞行（依靠尾翼/自旋稳定）");
        else if (navTier == 0) r.fail("缺少航电舱：没有制导系统，火箭会失控", Failure.TUMBLE);
        else if (navTier < requiredNav(to)) r.fail("航电导航等级不足：目的地需要 " + navName(requiredNav(to)), Failure.ABORT_RETURN);
        else r.ok("导航：" + navName(navTier));
        if (batteries == 0 && !sub) r.fail("缺少电池组：航电与阀门没有电源", Failure.TUMBLE);
        if (life < m.transferDays()) r.fail(String.format("生命维持不足：航程 %.0f 天，仅支持 %.0f 天", m.transferDays(), life), Failure.SUFFOCATION);
        else r.ok(String.format("生命维持 %.0f 天（航程 %.0f 天）", life, m.transferDays()));
        if (!nose && from.pressureBar > 0.1) r.warn("没有鼻锥整流罩：大气阻力使所需 Δv 增加 6%");

        if (to.pressureBar >= 0.001 && !suborbital) {
            if (shield <= 0) r.fail("目的地 " + to.nameZh + " 有大气，末级缺少隔热盾：再入时将烧毁", Failure.BURN_UP);
            else if (shield < m.entrySpeedKmS()) r.fail(String.format("隔热盾额定 %.1f km/s，再入速度 %.1f km/s：隔热盾会被烧穿", shield, m.entrySpeedKmS()), Failure.BURN_UP);
            else r.ok(String.format("隔热盾可承受 %.1f km/s 再入", m.entrySpeedKmS()));
            if (!parachute) r.warn("末级没有降落伞：需要动力减速着陆，消耗更多 Δv");
        }
        if (to.pressureBar < 0.001 && legs < 3) r.fail("末级着陆腿少于 3 个：在无大气天体上着陆会翻倒", Failure.TIP_OVER);
        else if (to.pressureBar >= 0.001 && legs < 3 && !parachute) r.fail("末级既无降落伞也无着陆腿：会硬着陆", Failure.TIP_OVER);
        if (suborbital) r.ok("亚轨道飞行：飞越卡门线后返回发射场附近");

        // delta-v budget
        double ascent = m.ascent() * 1000 * gameDeltaVScale * airPenalty;
        double toTransfer = ascent + (m.transfer() + m.capture()) * 1000 * gameDeltaVScale;
        String budget = String.format("Δv %.0f / 需要 %.0f m/s（上升 %.0f + 转移 %.0f + 入轨 %.0f + 着陆 %.0f，已按方块尺度 ×%.1f）",
                r.totalDeltaV, r.requiredDeltaV, m.ascent() * 1000 * gameDeltaVScale, m.transfer() * 1000 * gameDeltaVScale,
                m.capture() * 1000 * gameDeltaVScale, m.descent() * 1000 * gameDeltaVScale, gameDeltaVScale);
        if (r.totalDeltaV < ascent) r.fail(budget + "：连入轨都不够，将坠回地面", Failure.FALL_BACK);
        else if (r.totalDeltaV < toTransfer) r.fail(budget + "：无法完成转移，任务中止返航", Failure.ABORT_RETURN);
        else if (r.totalDeltaV < r.requiredDeltaV) r.fail(budget + "：着陆燃料不足，将坠毁", Failure.CRASH_LANDING);
        else if (r.totalDeltaV < r.requiredDeltaV * 1.05) r.warn(budget + "：余量不足 5%");
        else r.ok(budget);

        if (!r.hasEscapeTower) r.warn("没有逃逸塔：发生事故时乘员无法逃生");
        return r;
    }

    private static int requiredNav(CelestialBody to) {
        return switch (to.id) {
            case "earth", "moon" -> 1;
            default -> 2;
        };
    }

    private static String navName(int tier) {
        return tier <= 1 ? "地月导航（T1）" : "深空导航（T2）";
    }

    private static void evaluateStage(Stage s, Map<Long, Placed> byPos, Report r, boolean atmosphere) {
        List<Placed> engines = new ArrayList<>();
        List<Placed> solids = new ArrayList<>();
        for (Placed p : s.parts) {
            if (p.spec.category == PartSpec.Category.ENGINE) engines.add(p);
            else if (p.spec.category == PartSpec.Category.SOLID_NOZZLE) solids.add(p);
        }
        Set<Long> stageKeys = new HashSet<>();
        for (Placed p : s.parts) stageKeys.add(p.key());

        // exhaust must be clear: nothing from the same stage directly below an engine
        for (Placed e : engines) {
            Placed below = byPos.get(pack(e.x, e.y - 1, e.z));
            if (below != null && stageKeys.contains(below.key())) {
                r.fail("第 " + s.index + " 级发动机喷口被下方部件堵住", Failure.PAD_EXPLOSION);
            }
        }

        // liquid engines: all must burn the same pair
        Propellant fuel = null, ox = null;
        double of = 0;
        for (Placed e : engines) {
            if (fuel == null) { fuel = e.spec.fuel; ox = e.spec.oxidizer; of = e.spec.mixtureRatio; }
            else if (fuel != e.spec.fuel || ox != e.spec.oxidizer) {
                r.fail("第 " + s.index + " 级混用了不同推进剂的发动机", Failure.PAD_EXPLOSION);
            }
        }

        // plumbing: tanks reachable from an engine through tanks/structure of this stage
        Set<Long> reachable = new HashSet<>();
        ArrayDeque<Placed> queue = new ArrayDeque<>(engines);
        for (Placed e : engines) reachable.add(e.key());
        while (!queue.isEmpty()) {
            Placed cur = queue.poll();
            for (int[] d : DIRS) {
                Placed n = byPos.get(pack(cur.x + d[0], cur.y + d[1], cur.z + d[2]));
                if (n == null || !stageKeys.contains(n.key()) || reachable.contains(n.key())) continue;
                if (n.spec.category == PartSpec.Category.TANK || n.spec.category == PartSpec.Category.STRUCTURE) {
                    reachable.add(n.key());
                    queue.add(n);
                }
            }
        }
        double fuelMass = 0, oxMass = 0, stranded = 0, wrongType = 0;
        for (Placed p : s.parts) {
            if (p.spec.category != PartSpec.Category.TANK) continue;
            if (!reachable.contains(p.key())) { stranded += p.fill; continue; }
            if (p.spec.stores == fuel) fuelMass += p.fill;
            else if (p.spec.stores == ox) oxMass += p.fill;
            else wrongType += p.fill;
        }
        double solidMass = 0;
        for (Placed p : s.parts) if (p.spec.category == PartSpec.Category.SOLID_SEGMENT) solidMass += p.fill;
        if (!solids.isEmpty() && solidMass <= 0) r.warn("第 " + s.index + " 级固体喷管上方没有装药段");

        double liquidUsable = 0;
        if (!engines.isEmpty()) {
            double burnableFuel = Math.min(fuelMass, oxMass / of);
            liquidUsable = burnableFuel * (1 + of);
            double leftover = fuelMass + oxMass - liquidUsable;
            if (leftover > Math.max(0.05, liquidUsable * 0.06)) r.warn(String.format("第 %d 级推进剂配比偏离 O/F=%.1f：%.1f 吨无法燃烧", s.index, of, leftover));
        }
        if (stranded > 0.05) r.warn(String.format("第 %d 级有 %.1f 吨推进剂所在贮箱未与发动机连通", s.index, stranded));
        if (wrongType > 0.05) r.warn(String.format("第 %d 级有 %.1f 吨推进剂不适用于本级发动机", s.index, wrongType));
        s.propellantUnusable = stranded + wrongType + (fuelMass + oxMass - liquidUsable);

        double tLiq = 0, tSol = 0, mdotLiq = 0, mdotSol = 0, tLiqSea = 0, tSolSea = 0;
        for (Placed e : engines) {
            tLiq += e.spec.thrustVac; tLiqSea += e.spec.thrustSea();
            mdotLiq += e.spec.thrustVac / (e.spec.ispVac * OrbitMath.G0);
        }
        if (solidMass > 0) for (Placed n : solids) {
            tSol += n.spec.thrustVac; tSolSea += n.spec.thrustSea();
            mdotSol += n.spec.thrustVac / (n.spec.ispVac * OrbitMath.G0);
        }
        if (liquidUsable <= 0) { tLiq = 0; tLiqSea = 0; mdotLiq = 0; }
        s.propellantUsable = liquidUsable + solidMass;
        s.thrustVac = tLiq + tSol;
        s.thrustSea = tLiqSea + tSolSea;
        double mdot = mdotLiq + mdotSol;
        s.ispVac = mdot > 0 ? s.thrustVac / (mdot * OrbitMath.G0) : 0;
        s.ispSea = mdot > 0 ? s.thrustSea / (mdot * OrbitMath.G0) : 0;
    }

    private static void checkSymmetry(Report r, Stage first, boolean fins) {
        double mx = 0, mz = 0, mt = 0;
        for (Stage s : r.stages) for (Placed p : s.parts) { mx += p.mass() * p.x; mz += p.mass() * p.z; mt += p.mass(); }
        double tx = 0, tz = 0, tt = 0;
        for (Placed p : first.parts) if (p.spec.isEngine()) { tx += p.spec.thrustVac * p.x; tz += p.spec.thrustVac * p.z; tt += p.spec.thrustVac; }
        if (mt <= 0 || tt <= 0) return;
        double off = Math.hypot(mx / mt - tx / tt, mz / mt - tz / tt);
        double limit = fins ? 0.35 : 0.2;
        if (off > limit) r.fail(String.format("推力线偏离质心 %.2f 格：起飞后会失控翻滚", off), Failure.TUMBLE);
        else r.ok(String.format("质心与推力线偏差 %.2f 格", off));
    }

    private static void checkStructure(Report r, List<Placed> parts, CelestialBody from) {
        double loadFactor = from.pressureBar > 0.1 ? 3.0 : 1.8;
        double g = from.surfaceGravity;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Placed p : parts) { minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
        double worst = 0; int worstY = 0;
        for (int y = minY; y <= maxY; y++) {
            double above = 0, strength = 0;
            for (Placed p : parts) {
                if (p.y > y) above += p.mass();
                else if (p.y == y) strength += p.spec.strength;
            }
            if (strength <= 0) continue;
            double ratio = above * Math.max(g, 0.3) * loadFactor / strength;
            if (ratio > worst) { worst = ratio; worstY = y; }
        }
        if (worst > 1) r.fail(String.format("第 %d 层结构在最大动压时超载 %.0f%%：飞行中会解体", worstY - minY + 1, (worst - 1) * 100), Failure.BREAKUP);
        else r.ok(String.format("结构余量 %.0f%%", (1 - worst) * 100));
    }

    private static void checkEngineQuality(Report r, List<Placed> parts) {
        double survive = 1;
        int untested = 0;
        for (Placed p : parts) {
            if (!p.spec.isEngine()) continue;
            double pf = p.tested ? 0.004 : 0.04;
            pf *= 1.5 - p.quality;
            survive *= 1 - pf;
            if (!p.tested) untested++;
        }
        r.failureChance = 1 - survive;
        r.failureChance = 0;
        if (untested > 0) r.warn(untested + " 台发动机未经试车：不影响安全，但试车后才能确认推力（建议试车）");
        else r.ok("发动机已全部试车");
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
    }

    /** Parts sorted bottom to top, used by renderers and the flight sequence. */
    public static List<Placed> sortedByHeight(List<Placed> parts) {
        List<Placed> l = new ArrayList<>(parts);
        l.sort(Comparator.comparingInt(Placed::y));
        return Collections.unmodifiableList(l);
    }
}
