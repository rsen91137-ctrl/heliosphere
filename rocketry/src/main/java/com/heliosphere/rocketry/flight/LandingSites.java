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
        S("mercury", new Site("caloris", "卡洛里斯盆地", 30.5, 170.2, "巨型撞击盆地，低反照率岩"), new Site("rachmaninoff", "拉赫玛尼诺夫坑", 27.6, 57.6, "凹陷亮斑：挥发性硫化物"),
                new Site("north_pole", "北极永久阴影坑", 87.5, 0, "撞击坑底的水冰"));
        S("venus", new Site("maxwell", "麦克斯韦山脉山顶", 65.2, 3.3, "金属霜：辉铋矿（等离子推进器工质）"), new Site("ishtar", "伊什塔尔高地", 70, 27, "镶嵌地块"),
                new Site("maat", "玛阿特火山", 0.5, -165.2, "金星最高火山之一"));
        S("phobos", new Site("stickney", "斯蒂克尼陨石坑", 1, -49, "碳质球粒陨石：水与有机物"));
        S("deimos", new Site("swift", "斯威夫特平原", 10, 0, "细腻的碳质表土"));
        S("ceres", new Site("occator", "欧卡托陨石坑", 19.8, -120.7, "亮斑：碳酸钠"), new Site("ahuna", "阿胡纳山", -10.5, -44.1, "冰火山"));
        S("vesta", new Site("rheasilvia", "雷亚希尔维亚中央峰", -75, -59, "太阳系第二高峰，古铜辉石岩"), new Site("marcia", "玛西亚坑", 10, -170, "年轻陨坑，陨铁"));
        S("io", new Site("loki", "洛基火山口湖畔", 15, 51, "熔岩湖：小心岩浆！硫磺壳"), new Site("boosaule", "博萨乌莱山", -5.4, -91.7, "比珠峰高一倍的山"),
                new Site("prometheus", "普罗米修斯火山", -1.5, -153, "二氧化硫霜"));
        S("europa", new Site("conamara", "科纳马拉混沌区", 9.7, -86.6, "条纹带盐沉积；向下钻 110 格可抵达冰下海洋"), new Site("pwyll", "皮威尔坑", -25.2, -88.8, "年轻冰坑"));
        S("ganymede", new Site("galileo", "伽利略区", 35, -145, "古老暗色地体"), new Site("tros", "特罗斯坑", 11, -27, "明亮沟槽冰"));
        S("callisto", new Site("valhalla", "瓦尔哈拉多环盆地", 14.7, -56, "太阳系最密集的陨坑地带"), new Site("asgard", "阿斯加德", 32.2, -140, "多环盆地"));
        S("enceladus", new Site("baghdad", "巴格达虎纹裂隙", -85, 0, "喷发羽流的冰晶"), new Site("sarandib", "萨兰迪布平原", 5, -60, "雪白平原"));
        S("titan", new Site("kraken", "克拉肯海岸", 62, -50, "液态甲烷海：空罐右键直接灌装甲烷燃料"), new Site("shangri_la", "香格里拉沙丘海", -10, 165, "托林有机沙丘"),
                new Site("xanadu", "仙那度高地", -10, -100, "冰质基岩"));
        S("miranda", new Site("verona", "维罗纳断崖", -18, -12, "太阳系最高悬崖（数百格垂直落差）"), new Site("inverness", "因弗内斯冕状区", -66, -35, "条带岩"));
        S("triton", new Site("cantaloupe", "哈密瓜地形区", 20, -30, "氮冰与喷泉"), new Site("ruach", "鲁阿赫平原", 25, 25, "冻结的氮冰湖"));
        S("pluto", new Site("sputnik", "斯普特尼克平原（心形）", 25, 175, "氮冰冰川"), new Site("tartarus", "塔尔塔罗斯刃脊", 20, -135, "甲烷冰刃脊"),
                new Site("cthulhu", "克苏鲁斑", -5, 90, "暗红托林地壳"), new Site("wright", "赖特山", -21, 173, "冰火山"));
        S("charon", new Site("mordor", "魔多斑", 80, 0, "红色托林极冠"), new Site("serenity", "宁静峡谷", 15, 30, "深谷"));
        S("eris", new Site("dysnomia", "阋神星赤道", 0, 0, "甲烷霜原野"));
        S("jupiter", new Site("equator", "赤道浮空站", 0, 0, "浮空平台：风暴极强，必须开启风暴护盾"), new Site("grs", "大红斑边缘浮空站", -22, 60, "风暴核心！危险"),
                new Site("oval", "白色卵形风暴站", -15, 75, "大红斑旁的气旋"), new Site("north_belt", "北赤道带浮空站", 40, -100, "高空氨冰云"));
        S("saturn", new Site("equator", "赤道浮空站", 0, 0, "浮空平台：抬头可见光环"), new Site("hexagon", "北极六边形风暴站", 78, 0, "六边形急流"),
                new Site("south", "南半球浮空站", -35, 120, "带状云"));
        S("uranus", new Site("equator", "赤道浮空站", 0, 0, "甲烷冰云"), new Site("pole", "南极浮空站", -70, 40, "侧躺自转的极区"));
        S("neptune", new Site("equator", "赤道浮空站", 0, 0, "太阳系最快的风"), new Site("gds", "大暗斑浮空站", -22, -120, "暗色风暴"));
    }

    private static void S(String body, Site... sites) { BY_BODY.put(body, List.of(sites)); }

    private LandingSites() {}

    public static List<Site> of(String body) { return BY_BODY.getOrDefault(body, List.of()); }

    public static Site get(String body, String id) {
        for (Site s : of(body)) if (s.id.equals(id)) return s;
        List<Site> l = of(body);
        return l.isEmpty() ? null : l.get(0);
    }
}
