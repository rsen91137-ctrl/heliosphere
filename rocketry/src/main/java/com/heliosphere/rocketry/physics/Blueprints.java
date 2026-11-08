package com.heliosphere.rocketry.physics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-designed rockets offered in the creative tab. Each layer is a square grid written north-to-south;
 * the first layer is the bottom of the rocket.
 */
public final class Blueprints {
    public record Blueprint(String id, String nameZh, String descZh, String[][] layers) {
        public List<RocketAnalyzer.Placed> parts(boolean fueled) {
            List<RocketAnalyzer.Placed> out = new ArrayList<>();
            for (int y = 0; y < layers.length; y++) {
                String[] rows = layers[y];
                int half = rows.length / 2;
                for (int zi = 0; zi < rows.length; zi++) {
                    String row = rows[zi];
                    for (int xi = 0; xi < row.length(); xi++) {
                        PartSpec s = CODES.get(row.charAt(xi));
                        if (s == null) continue;
                        double fill = fueled ? s.capacity : (s.category == PartSpec.Category.SOLID_SEGMENT ? s.capacity : 0);
                        out.add(new RocketAnalyzer.Placed(xi - half, y, zi - half, s, fill, true, 1.0));
                    }
                }
            }
            return out;
        }
    }

    public static final Map<Character, PartSpec> CODES = new LinkedHashMap<>();
    static {
        CODES.put('E', PartSpec.ENGINE_KEROLOX); CODES.put('H', PartSpec.ENGINE_HYDROLOX);
        CODES.put('M', PartSpec.ENGINE_METHALOX); CODES.put('V', PartSpec.ENGINE_METHALOX_VAC);
        CODES.put('R', PartSpec.TANK_RP1); CODES.put('O', PartSpec.TANK_LOX); CODES.put('L', PartSpec.TANK_LH2);
        CODES.put('C', PartSpec.TANK_CH4); CODES.put('D', PartSpec.DECOUPLER); CODES.put('F', PartSpec.FIN);
        CODES.put('S', PartSpec.FRAME); CODES.put('A', PartSpec.AVIONICS_T1); CODES.put('B', PartSpec.AVIONICS_T2);
        CODES.put('P', PartSpec.CAPSULE); CODES.put('K', PartSpec.HEAT_SHIELD); CODES.put('T', PartSpec.HEAT_SHIELD_T2);
        CODES.put('U', PartSpec.PARACHUTE); CODES.put('G', PartSpec.LANDING_LEG); CODES.put('N', PartSpec.NOSE_CONE);
        CODES.put('X', PartSpec.ESCAPE_TOWER); CODES.put('Z', PartSpec.BATTERY); CODES.put('W', PartSpec.LIFE_SUPPORT);
        CODES.put('s', PartSpec.SRB_SEGMENT); CODES.put('n', PartSpec.SRB_NOZZLE);
    }

    private static final Map<String, Blueprint> ALL = new LinkedHashMap<>();
    public static List<Blueprint> all() { return new ArrayList<>(ALL.values()); }
    public static Blueprint get(String id) { return ALL.get(id); }
    private static Blueprint reg(Blueprint b) { ALL.put(b.id, b); return b; }

    private static String[] L(String... rows) { return rows; }

    /** Sounding rocket: proves the solid motor, reaches space but cannot reach orbit. */
    public static final Blueprint SOUNDING = reg(new Blueprint("sounding", "探空一号", "固体探空火箭（教学用，无法入轨）", new String[][]{
            L("n"), L("s"), L("s"), L("s"), L("U"), L("P"), L("N")}));

    /** Earth-Moon rocket built entirely from Earth materials. */
    public static final Blueprint QIMING_1 = reg(new Blueprint("qiming_1", "启明一号", "地月往返火箭：煤油一级 + 氢氧登月返回级", new String[][]{
            L("FEF", "EEE", "FEF"),
            L("ORO", "ROR", "ORO"), L("ORO", "ROR", "ORO"), L("ORO", "ROR", "ORO"), L("ORO", "ROR", "ORO"),
            L("OOO", "OOO", "OOO"),
            L("DDD", "DDD", "DDD"),
            L("GHG", "HHH", "GHG"),
            L("LLL", "LLL", "LLL"), L("LLL", "LLL", "LLL"), L("LLL", "LLL", "LLL"),
            L("LOL", "OLO", "LOL"), L("OOO", "OLO", "OOO"),
            L("ZAZ", "UKU", "ZAZ"),
            L("...", ".P.", "..."),
            L("...", ".N.", "..."),
            L("...", ".X.", "...")}));

    /** Mars transfer vehicle: methalox engines, deep-space avionics and life support need lunar materials. */
    public static final Blueprint QIMING_2 = reg(new Blueprint("qiming_2", "启明二号", "火星往返飞船：甲烷液氧全流量发动机 + 深空生命维持", new String[][]{
            L("FMF", "MMM", "FMF"),
            L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"),
            L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"),
            L("DDD", "DDD", "DDD"),
            L("GVG", "VVV", "GVG"),
            L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"), L("OCO", "COC", "OCO"),
            L("ZBZ", "UTU", "ZBZ"),
            L("...", ".W.", "..."),
            L("...", ".P.", "..."),
            L("...", ".N.", "..."),
            L("...", ".X.", "...")}));

    private Blueprints() {}
}
