package com.heliosphere.rocketry.flight;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Named landing sites at their real coordinates, each chosen for a resource the tech tree needs. */
public final class LandingSites {
    public record Site(String id, String nameZh, double lat, double lon, String hintZh) {}

    public static final Map<String, List<Site>> BY_BODY = new LinkedHashMap<>();

    static {
        BY_BODY.put("earth", List.of(
                new Site("home", "原发射场 / 世界出生点", Double.NaN, Double.NaN, "返回出发地附近，降落伞着陆")));
        BY_BODY.put("moon", List.of(
                new Site("tranquility", "静海基地", 0.67, 23.47, "高钛月海：钛铁矿与富氦-3 月壤"),
                new Site("shackleton", "沙克尔顿坑边缘", -89.4, 130.0, "南极永久阴影区：水冰（返程燃料）"),
                new Site("aristarchus", "阿里斯塔克高原", 24.0, -50.0, "风暴洋 KREEP 地体：稀土与钍"),
                new Site("tycho", "第谷坑", -43.0, -11.0, "年轻撞击坑：陨铁与新鲜斜长岩"),
                new Site("imbrium", "雨海平原", 32.0, -16.0, "广阔平坦的月海，适合建基地")));
        BY_BODY.put("mars", List.of(
                new Site("jezero", "杰泽罗陨石坑", 18.4, 77.6, "古三角洲：蒙脱石黏土与疑似微化石"),
                new Site("meridiani", "子午线高原", -1.9, -5.5, "赤铁矿结核层（蓝莓）"),
                new Site("utopia", "乌托邦平原", 45.0, 118.0, "中纬度地下冰：富氘水"),
                new Site("olympus", "奥林匹斯山山脚", 18.0, -129.0, "太阳系最高火山，悬崖高达数百格"),
                new Site("valles", "水手谷·梅拉斯深谷", -10.0, -73.0, "深达 210 格的峡谷，石膏沉积"),
                new Site("gale", "盖尔陨石坑", -5.4, 137.8, "中央峰夏普山，层状沉积"),
                new Site("north_cap", "北极冠边缘", 79.0, 0.0, "水冰与干冰")));
    }

    private LandingSites() {}

    public static List<Site> of(String body) { return BY_BODY.getOrDefault(body, List.of()); }

    public static Site get(String body, String id) {
        for (Site s : of(body)) if (s.id.equals(id)) return s;
        List<Site> l = of(body);
        return l.isEmpty() ? null : l.get(0);
    }
}
