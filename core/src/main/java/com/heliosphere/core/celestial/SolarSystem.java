package com.heliosphere.core.celestial;

import com.heliosphere.core.celestial.CelestialBody.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The real solar system. Radii in km, planet orbits in AU, moon orbits in km,
 * periods in days, J2000 orbital planes. Values from NASA planetary fact sheets.
 */
public final class SolarSystem {
    private static final Map<String, CelestialBody> BODIES = new LinkedHashMap<>();

    public static final CelestialBody SUN = add(CelestialBody.builder("sun", "太阳", "Sun", Kind.STAR)
            .radius(695_700).day(609.12).gravity(27.94).temp(5500, 5500).color(0xFFF4D6).build());

    public static final CelestialBody MERCURY = add(CelestialBody.builder("mercury", "水星", "Mercury", Kind.PLANET).parent("sun")
            .radius(2_439.7).orbitAu(0.387098, 87.969).plane(7.005, 48.331, 252.251).day(4_222.6).gravity(0.38)
            .temp(430, -180).color(0x8C8680).build());
    public static final CelestialBody VENUS = add(CelestialBody.builder("venus", "金星", "Venus", Kind.PLANET).parent("sun")
            .radius(6_051.8).orbitAu(0.723332, 224.701).plane(3.3947, 76.680, 181.980).day(-2_802.0).gravity(0.904)
            .air(92, false).temp(464, 464).color(0xE6CFA0).build());
    public static final CelestialBody EARTH = add(CelestialBody.builder("earth", "地球", "Earth", Kind.PLANET).parent("sun")
            .radius(6_371.0).orbitAu(1.0, 365.256).plane(0.0, 0.0, 100.464).day(24.0).gravity(1.0)
            .air(1.013, true).temp(15, 15).color(0x3A6EA5).build());
    public static final CelestialBody MOON = add(CelestialBody.builder("moon", "月球", "Moon", Kind.MOON).parent("earth")
            .radius(1_737.4).orbitKm(384_400, 27.3217).plane(5.145, 125.08, 218.316).day(708.7).gravity(0.1654)
            .temp(120, -170).color(0x9E9E9A).locked().build());
    public static final CelestialBody MARS = add(CelestialBody.builder("mars", "火星", "Mars", Kind.PLANET).parent("sun")
            .radius(3_389.5).orbitAu(1.523679, 686.980).plane(1.850, 49.558, 355.433).day(24.6597).gravity(0.3794)
            .air(0.00636, false).temp(-10, -80).color(0xC1440E).build());
    public static final CelestialBody PHOBOS = add(CelestialBody.builder("phobos", "火卫一", "Phobos", Kind.MOON).parent("mars")
            .radius(11.08).orbitKm(9_376, 0.31891).plane(1.08, 0, 30).day(7.65).gravity(0.0006).temp(-4, -112).color(0x6B5D52).locked().build());
    public static final CelestialBody DEIMOS = add(CelestialBody.builder("deimos", "火卫二", "Deimos", Kind.MOON).parent("mars")
            .radius(6.2).orbitKm(23_463, 1.26244).plane(1.79, 0, 200).day(30.3).gravity(0.0003).temp(-4, -112).color(0x8A7B6C).locked().build());
    public static final CelestialBody CERES = add(CelestialBody.builder("ceres", "谷神星", "Ceres", Kind.DWARF_PLANET).parent("sun")
            .radius(469.7).orbitAu(2.7675, 1_680.0).plane(10.59, 80.33, 153.0).day(9.074).gravity(0.029).temp(-38, -105).color(0x7D7A75).build());
    public static final CelestialBody VESTA = add(CelestialBody.builder("vesta", "灶神星", "Vesta", Kind.ASTEROID).parent("sun")
            .radius(262.7).orbitAu(2.362, 1_325.75).plane(7.14, 103.85, 300.0).day(5.342).gravity(0.025).temp(-20, -190).color(0xA39E94).build());
    public static final CelestialBody JUPITER = add(CelestialBody.builder("jupiter", "木星", "Jupiter", Kind.PLANET).parent("sun")
            .radius(69_911).orbitAu(5.2044, 4_332.59).plane(1.303, 100.464, 34.396).day(9.925).gravity(2.528)
            .air(1000, false).temp(-108, -108).color(0xC99B6A).gasGiant().build());
    public static final CelestialBody IO = add(CelestialBody.builder("io", "木卫一", "Io", Kind.MOON).parent("jupiter")
            .radius(1_821.6).orbitKm(421_700, 1.769).plane(0.05, 0, 10).day(42.46).gravity(0.183).temp(-143, -183).color(0xD9C25A).locked().build());
    public static final CelestialBody EUROPA = add(CelestialBody.builder("europa", "木卫二", "Europa", Kind.MOON).parent("jupiter")
            .radius(1_560.8).orbitKm(671_034, 3.551).plane(0.47, 0, 120).day(85.2).gravity(0.134).temp(-160, -220).color(0xC9B89A).locked().build());
    public static final CelestialBody GANYMEDE = add(CelestialBody.builder("ganymede", "木卫三", "Ganymede", Kind.MOON).parent("jupiter")
            .radius(2_634.1).orbitKm(1_070_412, 7.155).plane(0.2, 0, 240).day(171.7).gravity(0.146).temp(-113, -193).color(0x9C9387).locked().build());
    public static final CelestialBody CALLISTO = add(CelestialBody.builder("callisto", "木卫四", "Callisto", Kind.MOON).parent("jupiter")
            .radius(2_410.3).orbitKm(1_882_709, 16.689).plane(0.2, 0, 300).day(400.5).gravity(0.126).temp(-108, -193).color(0x6E6559).locked().build());
    public static final CelestialBody SATURN = add(CelestialBody.builder("saturn", "土星", "Saturn", Kind.PLANET).parent("sun")
            .radius(58_232).orbitAu(9.5826, 10_759.22).plane(2.485, 113.665, 49.954).day(10.656).gravity(1.065)
            .air(1000, false).temp(-139, -139).color(0xE3CF9A).gasGiant().rings(74_500, 136_775).build());
    public static final CelestialBody ENCELADUS = add(CelestialBody.builder("enceladus", "土卫二", "Enceladus", Kind.MOON).parent("saturn")
            .radius(252.1).orbitKm(237_948, 1.370).plane(0.02, 0, 60).day(32.9).gravity(0.0113).temp(-198, -240).color(0xF2F4F5).locked().build());
    public static final CelestialBody TITAN = add(CelestialBody.builder("titan", "土卫六", "Titan", Kind.MOON).parent("saturn")
            .radius(2_574.73).orbitKm(1_221_870, 15.945).plane(0.35, 0, 200).day(382.7).gravity(0.138)
            .air(1.467, false).temp(-179, -179).color(0xD6A44C).locked().build());
    public static final CelestialBody URANUS = add(CelestialBody.builder("uranus", "天王星", "Uranus", Kind.PLANET).parent("sun")
            .radius(25_362).orbitAu(19.2184, 30_688.5).plane(0.773, 74.006, 313.232).day(-17.24).gravity(0.886)
            .air(1000, false).temp(-197, -197).color(0x9FD7E0).gasGiant().rings(41_837, 51_149).build());
    public static final CelestialBody MIRANDA = add(CelestialBody.builder("miranda", "天卫五", "Miranda", Kind.MOON).parent("uranus")
            .radius(235.8).orbitKm(129_390, 1.413).plane(4.2, 0, 90).day(33.9).gravity(0.0079).temp(-187, -213).color(0xB4B4B0).locked().build());
    public static final CelestialBody NEPTUNE = add(CelestialBody.builder("neptune", "海王星", "Neptune", Kind.PLANET).parent("sun")
            .radius(24_622).orbitAu(30.110, 60_195).plane(1.770, 131.784, 304.880).day(16.11).gravity(1.14)
            .air(1000, false).temp(-201, -201).color(0x3E66F0).gasGiant().build());
    public static final CelestialBody TRITON = add(CelestialBody.builder("triton", "海卫一", "Triton", Kind.MOON).parent("neptune")
            .radius(1_353.4).orbitKm(354_759, -5.877).plane(157, 0, 45).day(141.0).gravity(0.0794)
            .air(0.000014, false).temp(-235, -235).color(0xC8B8B0).locked().build());
    public static final CelestialBody PLUTO = add(CelestialBody.builder("pluto", "冥王星", "Pluto", Kind.DWARF_PLANET).parent("sun")
            .radius(1_188.3).orbitAu(39.482, 90_560).plane(17.16, 110.299, 238.929).day(-153.3).gravity(0.063)
            .air(0.00001, false).temp(-230, -230).color(0xD8C3A5).build());
    public static final CelestialBody CHARON = add(CelestialBody.builder("charon", "卡戎", "Charon", Kind.MOON).parent("pluto")
            .radius(606).orbitKm(19_591, 6.387).plane(0.08, 0, 0).day(153.3).gravity(0.0294).temp(-220, -220).color(0x9E9289).locked().build());
    public static final CelestialBody ERIS = add(CelestialBody.builder("eris", "阋神星", "Eris", Kind.DWARF_PLANET).parent("sun")
            .radius(1_163).orbitAu(67.86, 203_830).plane(44.04, 35.95, 204.0).day(379.0).gravity(0.084).temp(-243, -243).color(0xEDEDED).build());

    private SolarSystem() {}

    private static CelestialBody add(CelestialBody b) { BODIES.put(b.id, b); return b; }

    public static CelestialBody get(String id) { return BODIES.get(id); }
    public static List<CelestialBody> all() { return Collections.unmodifiableList(new ArrayList<>(BODIES.values())); }

    public static List<CelestialBody> childrenOf(String parent) {
        List<CelestialBody> out = new ArrayList<>();
        for (CelestialBody b : BODIES.values()) if (parent.equals(b.parent)) out.add(b);
        return out;
    }
}
