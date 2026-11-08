"""Single source of truth for Heliosphere's simple content (ores, materials, terrain, parts, machines)."""

# ----------------------------------------------------------------------------------------------- ores
# id, ore zh, ore en, drop id, drop zh, drop en, colour, highlight, y min, y max, vein size, veins/chunk, tool
ORES = [
    ("bauxite", "铝土矿", "Bauxite Ore", "raw_bauxite", "铝土矿石", "Raw Bauxite", "#B8622F", "#E8A06A", 40, 140, 12, 10, "stone"),
    ("spodumene", "锂辉石矿", "Spodumene Ore", "raw_spodumene", "锂辉石", "Spodumene", "#C9A2C6", "#F4DDF0", -16, 96, 6, 5, "stone"),
    ("ilmenite", "钛铁矿", "Ilmenite Ore", "raw_ilmenite", "钛铁矿石", "Raw Ilmenite", "#2B2A2E", "#8C90A0", -48, 48, 8, 6, "iron"),
    ("pentlandite", "镍黄铁矿", "Pentlandite Ore", "raw_pentlandite", "镍黄铁矿石", "Raw Pentlandite", "#B39A44", "#E8D27C", -48, 48, 8, 6, "iron"),
    ("chromite", "铬铁矿", "Chromite Ore", "raw_chromite", "铬铁矿石", "Raw Chromite", "#3B2F2A", "#7E6E64", -32, 48, 7, 5, "iron"),
    ("pyrochlore", "烧绿石矿", "Pyrochlore Ore", "raw_pyrochlore", "烧绿石", "Pyrochlore", "#8B4A1E", "#D08040", -64, 16, 5, 3, "iron"),
    ("monazite", "独居石矿", "Monazite Ore", "raw_monazite", "独居石", "Monazite", "#C0602A", "#F0A060", -48, 24, 5, 3, "iron"),
    ("fluorite", "萤石矿", "Fluorite Ore", "fluorite", "萤石", "Fluorite", "#8E4FD0", "#5FE0B0", -16, 80, 8, 5, "stone"),
    ("uraninite", "沥青铀矿", "Uraninite Ore", "raw_uraninite", "沥青铀矿石", "Raw Uraninite", "#1E2A1E", "#86F060", -64, -16, 4, 2, "diamond"),
    ("halite", "岩盐矿", "Rock Salt Ore", "rock_salt", "岩盐", "Rock Salt", "#E6D0D0", "#FFFFFF", 0, 90, 12, 4, "stone"),
    ("cassiterite", "锡石矿", "Cassiterite Ore", "raw_cassiterite", "锡石", "Cassiterite", "#4A3020", "#8E6A48", -16, 72, 8, 6, "stone"),
    ("galena", "方铅矿", "Galena Ore", "raw_galena", "方铅矿石", "Raw Galena", "#737A85", "#C0C8D4", -32, 48, 8, 6, "stone"),
    ("sphalerite", "闪锌矿", "Sphalerite Ore", "raw_sphalerite", "闪锌矿石", "Raw Sphalerite", "#9A6A2A", "#DDA850", -32, 48, 8, 5, "stone"),
    ("molybdenite", "辉钼矿", "Molybdenite Ore", "raw_molybdenite", "辉钼矿石", "Raw Molybdenite", "#8893A6", "#D0D8E8", -48, 16, 6, 3, "iron"),
    ("wolframite", "黑钨矿", "Wolframite Ore", "raw_wolframite", "黑钨矿石", "Raw Wolframite", "#26262C", "#64646E", -64, 0, 6, 3, "iron"),
    ("graphite", "石墨矿", "Graphite Ore", "graphite", "石墨", "Graphite", "#38383A", "#7A7A82", -32, 64, 8, 5, "stone"),
    ("sulfur", "硫磺矿", "Sulfur Ore", "sulfur", "硫磺", "Sulfur", "#E5CC1E", "#FFF27A", -64, 32, 8, 5, "stone"),
    ("apatite", "磷灰石矿", "Apatite Ore", "apatite", "磷灰石", "Apatite", "#3AA8A0", "#8AE8E0", -16, 64, 7, 4, "stone"),
    ("cobaltite", "辉钴矿", "Cobaltite Ore", "raw_cobaltite", "辉钴矿石", "Raw Cobaltite", "#B8A0B8", "#E8D8F0", -48, 16, 5, 3, "iron"),
    ("sperrylite", "砷铂矿", "Sperrylite Ore", "raw_sperrylite", "砷铂矿石", "Raw Sperrylite", "#C8C8D4", "#FFFFFF", -64, -32, 3, 1, "diamond"),
    ("beryl", "绿柱石矿", "Beryl Ore", "beryl", "绿柱石", "Beryl", "#3FAF8A", "#A0FFE0", -32, 48, 4, 2, "iron"),
    ("titanomagnetite", "钒钛磁铁矿", "Titanomagnetite Ore", "raw_titanomagnetite", "钒钛磁铁矿石", "Raw Titanomagnetite", "#4A2E2E", "#9A6A6A", -32, 48, 7, 4, "iron"),
]

# ------------------------------------------------------------------------------------------- items
# module, id, zh, en, (shape, colour[, accent[, dark[, band]]])
ITEMS = []


def item(module, iid, zh, en, *icon):
    ITEMS.append((module, iid, zh, en, icon))


# Industry ---------------------------------------------------------------------------------------
INGOTS = [
    ("aluminum_ingot", "铝锭", "Aluminium Ingot", "#C8CED6"), ("lithium_ingot", "锂锭", "Lithium Ingot", "#D8D2C0"),
    ("titanium_ingot", "钛锭", "Titanium Ingot", "#9AA0AA"), ("nickel_ingot", "镍锭", "Nickel Ingot", "#C2BA98"),
    ("chromium_ingot", "铬锭", "Chromium Ingot", "#B8C4CC"), ("niobium_ingot", "铌锭", "Niobium Ingot", "#8E9AB0"),
    ("tin_ingot", "锡锭", "Tin Ingot", "#D8D8D0"), ("lead_ingot", "铅锭", "Lead Ingot", "#6E7088"),
    ("zinc_ingot", "锌锭", "Zinc Ingot", "#B0B8B8"), ("molybdenum_ingot", "钼锭", "Molybdenum Ingot", "#9098A8"),
    ("tungsten_ingot", "钨锭", "Tungsten Ingot", "#585A64"), ("cobalt_ingot", "钴锭", "Cobalt Ingot", "#5A70B8"),
    ("platinum_ingot", "铂锭", "Platinum Ingot", "#E4E4EC"), ("beryllium_ingot", "铍锭", "Beryllium Ingot", "#A8B8B0"),
    ("vanadium_ingot", "钒锭", "Vanadium Ingot", "#7A8A90"), ("silver_ingot", "银锭", "Silver Ingot", "#E0E4EA"),
    ("steel_ingot", "钢锭", "Steel Ingot", "#7C8088"), ("al_li_ingot", "铝锂合金锭", "Al-Li 2195 Alloy Ingot", "#B8C8D8"),
    ("ti64_ingot", "钛合金锭（Ti-6Al-4V）", "Ti-6Al-4V Ingot", "#8890A0"), ("inconel_ingot", "因科镍 718 合金锭", "Inconel 718 Ingot", "#A8A088"),
    ("grcop_ingot", "GRCop 铜合金锭", "GRCop-84 Copper Alloy Ingot", "#D08850"), ("stainless_steel_ingot", "不锈钢锭", "Stainless Steel Ingot", "#B0B4BC"),
]
for iid, zh, en, c in INGOTS:
    item("industry", iid, zh, en, "ingot", c)

PLATES = [("steel_plate", "钢板", "Steel Plate", "#7C8088"), ("al_li_plate", "铝锂合金板", "Al-Li Plate", "#B8C8D8"),
          ("ti64_plate", "钛合金板", "Titanium Alloy Plate", "#8890A0"), ("inconel_plate", "因科镍板", "Inconel Plate", "#A8A088"),
          ("stainless_steel_plate", "不锈钢板", "Stainless Steel Plate", "#B0B4BC"), ("copper_plate", "铜板", "Copper Plate", "#D0784A")]
for iid, zh, en, c in PLATES:
    item("industry", iid, zh, en, "plate", c)

CHEM = [
    ("alumina", "氧化铝", "Alumina", "dust", "#F0F0F0"), ("cryolite", "冰晶石", "Cryolite", "gem", "#E8F0F4"),
    ("sodium", "金属钠", "Sodium", "ingot", "#D8D8C8"), ("caustic_soda", "烧碱", "Caustic Soda", "dust", "#FAFAF0"),
    ("titanium_sponge", "海绵钛", "Titanium Sponge", "raw", "#7A7E88"), ("lithium_carbonate", "碳酸锂", "Lithium Carbonate", "dust", "#F4F0F4"),
    ("rare_earth_oxide", "稀土氧化物", "Rare Earth Oxide", "dust", "#D8C8A0"), ("neodymium", "钕", "Neodymium", "ingot", "#B0B0A0"),
    ("yttrium", "钇", "Yttrium", "ingot", "#C0C4C8"), ("yellowcake", "铀黄饼", "Yellowcake", "dust", "#E8C830"),
    ("enriched_uranium", "浓缩铀燃料芯块", "Enriched Uranium Pellet", "cyl", "#4A8A3A", "#90F070"),
    ("plutonium_238", "钚-238 热源", "Plutonium-238 Heat Source", "cyl", "#8A4A3A", "#FF8050"),
    ("ammonium_perchlorate", "高氯酸铵", "Ammonium Perchlorate", "dust", "#F0ECF4"),
    ("phenolic_resin", "酚醛树脂", "Phenolic Resin", "gem", "#A0582A"), ("carbon_fiber", "碳纤维", "Carbon Fiber", "plate", "#303034", "#505058"),
    ("pica", "PICA 烧蚀材料", "PICA Ablator", "plate", "#5A4030"), ("fluoroelastomer", "氟橡胶", "Fluoroelastomer", "gem", "#3A3A40"),
    ("plastic", "塑料", "Plastic", "plate", "#E8E8E0"), ("nylon", "尼龙纤维", "Nylon Fiber", "dust", "#F0F0F8"),
    ("bitumen", "沥青", "Bitumen", "raw", "#202022"), ("metallurgical_silicon", "冶金级硅", "Metallurgical Silicon", "raw", "#606878"),
    ("silicon_wafer", "单晶硅晶圆", "Silicon Wafer", "plate", "#404860", "#8090C0"), ("rhenium_powder", "铼粉", "Rhenium Powder", "dust", "#B8B8C0"),
    ("silicon_nitride", "氮化硅", "Silicon Nitride", "dust", "#C8C8D0"), ("triethylaluminium", "三乙基铝-三乙基硼", "TEA-TEB Igniter Fluid", "canister", "#70A060", "#30FF60"),
    ("sulfuric_acid", "硫酸", "Sulfuric Acid", "canister", "#B0B070", "#F0F040"),
]
for row in CHEM:
    item("industry", row[0], row[1], row[2], *row[3:])

CANISTERS = [
    ("empty_canister", "空压力罐", "Empty Pressure Canister", "#9098A0"),
    ("canister_lox", "液氧罐", "LOX Canister", "#5090E0"), ("canister_lh2", "液氢罐", "LH2 Canister", "#E0E8F0"),
    ("canister_rp1", "RP-1 煤油罐", "RP-1 Canister", "#C08030"), ("canister_ln2", "液氮罐", "LN2 Canister", "#80C0C0"),
    ("canister_argon", "氩气罐", "Argon Canister", "#B070E0"), ("canister_ch4", "液甲烷罐", "Liquid Methane Canister", "#60C060"),
    ("canister_oxygen", "压缩氧气罐", "Breathing Oxygen Canister", "#40A0FF"), ("canister_chlorine", "氯气罐", "Chlorine Canister", "#C0E040"),
    ("canister_co2", "二氧化碳罐", "CO2 Canister", "#C0C0C0"), ("canister_hydrogen", "氢气罐", "Hydrogen Gas Canister", "#FF8080"),
    ("canister_ammonia", "液氨罐", "Ammonia Canister", "#A0A0FF"), ("canister_deuterium", "氘气罐", "Deuterium Canister", "#FF60C0"),
    ("canister_helium3", "氦-3 储罐", "Helium-3 Canister", "#FFE060"),
]
for iid, zh, en, band in CANISTERS:
    module = "industry"
    item(module, iid, zh, en, "canister", "#8890A0", None, None, band)

COMPONENTS = [
    ("copper_wire", "铜导线", "Copper Wire", "rod", "#D07848"), ("circuit_board", "电路板", "Circuit Board", "board", "#2A7A3A", "#E0C050"),
    ("processor", "处理器", "Processor", "chip", "#303038", "#7080FF"), ("imu", "惯性测量单元", "Inertial Measurement Unit", "chip", "#404050", "#FF6040"),
    ("guidance_computer", "制导计算机", "Guidance Computer", "board", "#203050", "#60C0FF"), ("ndfeb_magnet", "钕铁硼磁铁", "NdFeB Magnet", "plate", "#5A5A60", "#C04040"),
    ("electric_motor", "电动机", "Electric Motor", "cyl", "#707880", "#D07848"), ("turbine_blade", "单晶涡轮叶片", "Single-Crystal Turbine Blade", "rod", "#A8A088"),
    ("si3n4_bearing", "氮化硅陶瓷轴承", "Silicon Nitride Bearing", "gear", "#C8C8D0", None, "#505058"), ("inducer", "诱导轮", "Inducer", "gear", "#8890A0"),
    ("turbopump", "涡轮泵", "Turbopump", "gear", "#A8A088", None, "#404048"), ("injector", "喷注器", "Injector", "plate", "#8890A0", "#202020"),
    ("combustion_chamber", "燃烧室", "Combustion Chamber", "cyl", "#D08850", "#806040"), ("nozzle_bell", "喷管延伸段", "Nozzle Extension", "nozzle", "#707480"),
    ("valve", "低温阀门", "Cryogenic Valve", "gear", "#B0B4BC", None, "#3060A0"), ("igniter", "TEA-TEB 点火器", "TEA-TEB Igniter", "cyl", "#70A060", "#30FF60"),
    ("engine_controller", "发动机控制器", "Engine Controller", "chip", "#2A3A2A", "#60FF60"), ("gimbal", "推力矢量万向节", "Thrust Gimbal", "gear", "#7C8088", None, "#C0A040"),
    ("copv", "碳纤维缠绕高压气瓶", "COPV", "cyl", "#303034", "#808088"), ("heat_shield_tile", "PICA 隔热瓦", "PICA Heat Shield Tile", "plate", "#4A3428"),
    ("parachute_canopy", "降落伞伞衣", "Parachute Canopy", "dust", "#F07030", "#FFFFFF"), ("battery_cell", "锂电池芯", "Lithium Battery Cell", "cyl", "#3050A0", "#A0C0FF"),
    ("solar_cell", "太阳能电池片", "Solar Cell", "plate", "#1A2A5A", "#6080D0"),
]
for row in COMPONENTS:
    item("industry", row[0], row[1], row[2], *row[3:])

for ore in ORES:
    drop, dzh, den, c1, c2 = ore[3], ore[4], ore[5], ore[6], ore[7]
    shape = "gem" if not drop.startswith("raw_") and drop not in ("graphite", "sulfur", "rock_salt") else "raw"
    item("industry", drop, dzh, den, shape, c1, c2)

# Planets ----------------------------------------------------------------------------------------
PLANET_ITEMS = [
    ("lunar_titanium_ingot", "月球钛锭", "Lunar Titanium Ingot", "ingot", "#A0A8C0"),
    ("lunar_ti_alloy_ingot", "月球高钛合金锭", "Lunar High-Ti Alloy Ingot", "ingot", "#7888B0"),
    ("lunar_iron", "月壤还原铁", "Lunar Reduced Iron", "raw", "#8A8A90"),
    ("meteoric_iron", "陨铁", "Meteoric Iron", "raw", "#6A6058", "#C0B8A8"),
    ("lunar_glass", "月壤玻璃", "Lunar Glass", "gem", "#B8C8C0"),
    ("lunar_glass_fiber", "月壤玻璃纤维", "Lunar Glass Fiber", "dust", "#D0E0D8"),
    ("lunar_ceramic", "月壤陶瓷", "Lunar Ceramic", "plate", "#D8D4C8"),
    ("kreep_concentrate", "KREEP 精矿", "KREEP Concentrate", "dust", "#8A6A9A"),
    ("thorium", "钍", "Thorium", "ingot", "#506050"),
    ("lunar_rare_earth", "月球稀土", "Lunar Rare Earth", "dust", "#C8A0D0"),
    ("lunar_ice_chunk", "月球冰块", "Lunar Ice Chunk", "gem", "#C0D8F0"),
    ("vacuum_wafer", "真空超纯晶圆", "Vacuum-Grown Wafer", "plate", "#304070", "#C0D0FF"),
    ("rad_hard_chip", "抗辐射芯片", "Radiation-Hardened Chip", "chip", "#402040", "#FF80FF"),
    ("deep_space_computer", "深空导航计算机核心", "Deep-Space Navigation Core", "board", "#302050", "#C080FF"),
    ("lunar_turbopump", "月钛涡轮泵", "Lunar Titanium Turbopump", "gear", "#7888B0", None, "#303040"),
    ("methalox_chamber", "全流量甲烷燃烧室", "Full-Flow Methalox Chamber", "cyl", "#7888B0", "#FF8040"),
    ("perchlorate_salt", "高氯酸盐", "Perchlorate Salt", "dust", "#E8D8D0"),
    ("hematite_nodule", "赤铁矿结核", "Hematite Nodule", "gem", "#5A3A3A", "#A07070"),
    ("gypsum_crystal", "石膏晶体", "Gypsum Crystal", "gem", "#F0ECE0"),
    ("smectite_clay", "蒙脱石黏土", "Smectite Clay", "raw", "#A88060"),
    ("microfossil", "疑似微化石", "Possible Microfossil", "raw", "#C8B898", "#704020"),
    ("mars_ice_chunk", "火星富氘冰块", "Deuterium-Rich Ice Chunk", "gem", "#D8E8F0"),
    ("heavy_water", "重水", "Heavy Water", "canister", "#8890A0", None, None, "#4060FF"),
    ("olivine", "橄榄石", "Olivine", "gem", "#7A9A30", "#C0E060"),
    ("science_suborbital", "亚轨道科学数据", "Suborbital Science Data", "chip", "#204060", "#40FFC0"),
    ("science_moon", "月球科学数据", "Lunar Science Data", "chip", "#404040", "#E0E0E0"),
    ("science_mars", "火星科学数据", "Martian Science Data", "chip", "#602010", "#FF8040"),
]
for row in PLANET_ITEMS:
    item("planets", row[0], row[1], row[2], *row[3:])

# ------------------------------------------------------------------------------------- terrain blocks
# id, zh, en, base colour, speck colour, speck density, hardness, tool level
TERRAIN = [
    ("moon_regolith", "月壤", "Lunar Regolith", "#8E8C88", "#B8B6B0", 0.12, 0.6, "none"),
    ("mare_regolith", "月海月壤", "Mare Regolith", "#5A5855", "#7A7874", 0.12, 0.6, "none"),
    ("helium3_regolith", "富氦-3 月壤", "Helium-3 Rich Regolith", "#5E5A50", "#E8D070", 0.06, 0.7, "none"),
    ("anorthosite", "斜长岩", "Anorthosite", "#B4B2AC", "#D8D6D0", 0.18, 2.0, "wood"),
    ("mare_basalt", "月海玄武岩", "Mare Basalt", "#4A4846", "#6A6866", 0.15, 2.2, "wood"),
    ("high_ti_basalt", "高钛玄武岩", "High-Titanium Basalt", "#38363A", "#8890A8", 0.15, 2.5, "stone"),
    ("kreep_rock", "KREEP 岩", "KREEP Rock", "#6A5A6E", "#B090C0", 0.15, 2.5, "iron"),
    ("moon_ice", "月球水冰", "Lunar Water Ice", "#A8BCC8", "#E0F0FF", 0.15, 1.0, "wood"),
    ("meteoric_iron_ore", "陨铁矿", "Meteoric Iron Ore", "#5A5654", "#A89888", 0.2, 3.0, "stone"),
    ("mars_regolith", "火星表土", "Martian Regolith", "#B0532A", "#D07040", 0.15, 0.6, "none"),
    ("mars_sand", "火星玄武岩砂", "Martian Basaltic Sand", "#5A3C30", "#7A5040", 0.2, 0.5, "none"),
    ("mars_rock", "火星岩", "Martian Rock", "#8A4A30", "#A86040", 0.15, 2.0, "wood"),
    ("mars_basalt", "火星玄武岩", "Martian Basalt", "#4A3430", "#6A4A40", 0.15, 2.4, "wood"),
    ("perchlorate_deposit", "高氯酸盐沉积", "Perchlorate Deposit", "#B07050", "#F0E8E0", 0.25, 1.0, "none"),
    ("hematite_deposit", "赤铁矿结核层", "Hematite Concretion Bed", "#6A4040", "#3A2A2A", 0.3, 2.5, "stone"),
    ("gypsum_deposit", "石膏沉积", "Gypsum Deposit", "#D8CCB8", "#F8F4EC", 0.2, 1.5, "wood"),
    ("smectite_clay_block", "蒙脱石黏土层", "Smectite Clay Bed", "#A87A5A", "#C89A78", 0.15, 0.8, "none"),
    ("mars_ice", "火星富氘冰", "Martian Deuterium Ice", "#C8D4DC", "#F0F8FF", 0.15, 1.0, "wood"),
    ("dry_ice", "干冰", "Dry Ice", "#E8ECF0", "#FFFFFF", 0.2, 0.8, "wood"),
    ("olivine_rock", "橄榄石岩", "Olivine Rock", "#5A6A30", "#A0C050", 0.25, 2.5, "stone"),
    ("moon_bricks", "月壤烧结砖", "Sintered Regolith Bricks", "#9A9894", "#7A7874", 0.0, 2.0, "wood"),
    ("mars_bricks", "火星烧结砖", "Martian Sintered Bricks", "#9A4A30", "#7A3A28", 0.0, 2.0, "wood"),
]

# ------------------------------------------------------------------------------------------ machines
# module, id, zh, en, casing colour, accent, glyph
MACHINES = [
    ("industry", "coal_generator", "燃煤发电机", "Coal Generator", "#5A5E64", "#FF8030", "flame"),
    ("industry", "solar_panel", "太阳能板", "Solar Panel", "#4A5058", "#4060D0", "sun"),
    ("industry", "battery_bank", "储能电池组", "Battery Bank", "#4A5058", "#40E060", "bolt"),
    ("industry", "electric_arc_furnace", "电弧炉", "Electric Arc Furnace", "#6A6E74", "#FFB040", "bolt"),
    ("industry", "alloy_furnace", "合金冶炼炉", "Alloy Furnace", "#6A5E58", "#FF6020", "flame"),
    ("industry", "electrolyzer", "电解槽", "Electrolyzer", "#5A6A74", "#60C0FF", "drop"),
    ("industry", "chemical_reactor", "化工反应釜", "Chemical Reactor", "#5E6A5E", "#80FF60", "flask"),
    ("industry", "refinery", "炼油厂", "Refinery", "#5A5450", "#E0A040", "drop"),
    ("industry", "air_separator", "空气分离装置", "Air Separation Unit", "#5A6470", "#80D0FF", "wave"),
    ("industry", "vacuum_furnace", "真空熔炼炉", "Vacuum Induction Furnace", "#4E5258", "#FF5070", "flame"),
    ("industry", "cnc_machine", "精密数控加工中心", "CNC Machining Center", "#6A7078", "#E0E040", "gear"),
    ("industry", "metal_3d_printer", "金属 3D 打印机", "Metal 3D Printer", "#5A5E70", "#40FFE0", "print"),
    ("industry", "electronics_assembler", "电子装配台", "Electronics Assembler", "#4A5A50", "#60FF90", "chip"),
    ("industry", "assembly_station", "总装台", "Assembly Station", "#6A6050", "#FFD040", "arm"),
    ("industry", "engine_test_stand", "发动机试车台", "Engine Test Stand", "#5A5A5A", "#FF4020", "rocket"),
    ("industry", "centrifuge", "离心机", "Centrifuge", "#5A6060", "#A0FF40", "spin"),
    ("planets", "oxygen_compressor", "氧气压缩机", "Oxygen Compressor", "#5A6470", "#40A0FF", "o2"),
    ("planets", "oxygen_distributor", "氧气扩散器", "Oxygen Distributor", "#5A6470", "#80E0FF", "o2"),
    ("planets", "regolith_processor", "月壤处理器", "Regolith Processor", "#6A6A66", "#E0E0A0", "grid"),
    ("planets", "ilmenite_reducer", "钛铁矿氢还原炉", "Ilmenite Hydrogen Reducer", "#5A5A64", "#A0A8FF", "flame"),
    ("planets", "vacuum_deposition", "真空沉积炉", "Vacuum Deposition Chamber", "#3E4250", "#C080FF", "atom"),
    ("planets", "sabatier_reactor", "萨巴蒂尔反应器", "Sabatier Reactor", "#6A5A50", "#60E060", "flask"),
    ("planets", "atmosphere_extractor", "大气提取器", "Atmosphere Extractor", "#6A5048", "#FFA070", "wave"),
    ("planets", "isru_electrolyzer", "冰水电解站", "Ice Electrolysis Station", "#5A6A74", "#C0F0FF", "drop"),
    ("planets", "suit_fabricator", "宇航服制造台", "Spacesuit Fabricator", "#5A6070", "#F0F0F0", "arm"),
    ("planets", "storm_shield_generator", "风暴护盾发生器", "Storm Shield Generator", "#3A4A6A", "#80C0FF", "wave"),
]

# ---------------------------------------------------------------------------------------- rocket parts
# id, zh, en, model kind, colour, accent
PARTS = [
    ("engine_kerolox", "RD-1 煤油液氧发动机", "RD-1 Kerolox Engine", "engine", "#B0A080", "#3A3A3A"),
    ("engine_hydrolox", "RL-1 氢氧真空发动机", "RL-1 Hydrolox Vacuum Engine", "engine_vac", "#C8CCD4", "#3A3A3A"),
    ("engine_methalox", "猛禽式全流量甲烷发动机", "Raptor-class Methalox Engine", "engine", "#7888B0", "#2A2A30"),
    ("engine_methalox_vac", "真空甲烷发动机", "Vacuum Methalox Engine", "engine_vac", "#8898C0", "#2A2A30"),
    ("srb_segment", "固体火箭装药段", "Solid Rocket Segment", "tank", "#E8E4DC", "#C03020"),
    ("srb_nozzle", "固体火箭喷管", "Solid Rocket Nozzle", "engine", "#E8E4DC", "#404040"),
    ("tank_rp1", "煤油贮箱", "RP-1 Tank", "tank", "#E8E8E0", "#C08030"),
    ("tank_lox", "液氧贮箱", "LOX Tank", "tank", "#E8ECF0", "#4080E0"),
    ("tank_lh2", "液氢低温贮箱", "LH2 Cryogenic Tank", "tank", "#E0A050", "#F8F8F8"),
    ("tank_ch4", "液甲烷贮箱", "Liquid Methane Tank", "tank", "#B8C0C8", "#50B050"),
    ("decoupler", "级间分离环", "Stage Decoupler", "band", "#3A3A40", "#E0C040"),
    ("rocket_frame", "结构桁架", "Structural Truss", "truss", "#A0A4AC", "#606468"),
    ("avionics_t1", "航电舱（地月导航）", "Avionics Bay (Earth-Moon)", "box", "#E0E0E0", "#40A0FF"),
    ("avionics_t2", "深空导航航电舱", "Deep-Space Avionics Bay", "box", "#D0D0E0", "#C080FF"),
    ("crew_capsule", "载人指令舱", "Crew Capsule", "capsule", "#E8E8E8", "#202830"),
    ("heat_shield", "PICA 隔热盾", "PICA Heat Shield", "slab", "#5A4030", "#3A2A20"),
    ("heat_shield_t2", "月壤陶瓷复合隔热盾", "Lunar Ceramic Heat Shield", "slab", "#D8D4C8", "#8A8478"),
    ("parachute_pack", "降落伞舱", "Parachute Pack", "box", "#E8E8E8", "#F07030"),
    ("landing_leg", "着陆腿", "Landing Leg", "leg", "#B0B4BC", "#404448"),
    ("nose_cone", "鼻锥整流罩", "Nose Cone", "cone", "#F0F0F0", "#C0C0C0"),
    ("rocket_fin", "尾翼", "Rocket Fin", "fin", "#D0D0D0", "#C03020"),
    ("escape_tower", "逃逸塔", "Launch Escape Tower", "tower", "#D04020", "#E0E0E0"),
    ("rocket_battery", "火箭电池组", "Rocket Battery Pack", "box", "#3050A0", "#A0C0FF"),
    ("life_support", "深空生命维持舱", "Deep-Space Life Support Module", "box", "#E0E0D8", "#40C080"),
]

# Plain functional blocks with custom Java classes but generated assets
OTHER_BLOCKS = [
    ("rocketry", "launch_pad", "发射台", "Launch Pad", "#5A5A5E", "#E0C040"),
    ("rocketry", "launch_console", "发射控制台", "Launch Control Console", "#4A5058", "#40FF80"),
    ("industry", "cable", "电缆", "Power Cable", "#303034", "#D07848"),
    ("core", "stellar_plasma", "恒星等离子体", "Stellar Plasma", "#FFC040", "#FFFFFF"),
]

ARMOR = [
    ("planets", "space_suit", "基础宇航服", "EVA Suit", "#F0F0F0", "#3060C0"),
    ("planets", "mars_suit", "火星宇航服", "Mars Surface Suit", "#E8E0D0", "#D06030"),
    ("planets", "thermal_suit", "高温隔热服", "Thermal Protection Suit", "#E0C060", "#F0F0F0"),
    ("planets", "cryo_suit", "深冷宇航服", "Cryogenic Suit", "#D8E8F0", "#2080C0"),
    ("planets", "solar_suit", "日冕防护服", "Coronal Suit", "#F0D040", "#202020"),
]
ARMOR_PIECES = [("helmet", "头盔", "Helmet"), ("chestplate", "躯干", "Torso"), ("leggings", "护腿", "Leggings"), ("boots", "靴", "Boots")]

# ---------------------------------------------------------------------------------------- outer solar system terrain
# id, zh, en, base, speck, density, hardness, tool, style, drop (item, min, max) or None
WORLD_TERRAIN = [
    ("mercury_regolith", "水星表土", "Mercurian Regolith", "#6A6660", "#8E8880", 0.12, 0.6, "none", "regolith", None),
    ("mercury_crust", "水星低反照率岩", "Low-Reflectance Mercurian Rock", "#3A3836", "#55524E", 0.15, 2.2, "wood", "basalt", None),
    ("hollows_deposit", "水星凹陷亮斑", "Mercurian Hollows Deposit", "#8A96A8", "#DCE8F8", 0.3, 1.2, "stone", "deposit", ("volatile_sulfide", 1, 3)),
    ("venus_regolith", "金星风化层", "Venusian Regolith", "#8A6A48", "#A88460", 0.15, 0.8, "none", "soil", None),
    ("venus_basalt", "金星玄武岩", "Venusian Basalt", "#5A4436", "#7A5C48", 0.15, 2.5, "wood", "basalt", None),
    ("tessera_rock", "金星镶嵌地块岩", "Tessera Rock", "#7A6A5A", "#9A8670", 0.2, 3.0, "stone", "layered", None),
    ("metal_frost", "高山金属霜", "Highland Metal Frost", "#B8B4A8", "#F0ECE0", 0.35, 2.0, "stone", "frost", ("bismuth_sulfide", 1, 3)),
    ("phobos_regolith", "碳质表土", "Carbonaceous Regolith", "#4A4240", "#6A5E58", 0.15, 0.5, "none", "regolith", None),
    ("carbonaceous_rock", "碳质球粒陨石岩", "Carbonaceous Chondrite Rock", "#2E2A28", "#5A504A", 0.25, 1.8, "wood", "deposit", ("carbonaceous_chondrite", 1, 2)),
    ("ceres_regolith", "谷神星含水黏土", "Cererian Hydrated Clay", "#5A5854", "#7A7670", 0.15, 0.6, "none", "regolith", None),
    ("ammoniated_clay", "氨化层状硅酸盐", "Ammoniated Phyllosilicate", "#6A6660", "#8A8478", 0.2, 1.0, "none", "layered", None),
    ("natron_deposit", "欧卡托亮斑碳酸钠", "Occator Natron Deposit", "#D8D8D0", "#FFFFFF", 0.3, 1.0, "stone", "deposit", ("sodium_carbonate", 2, 4)),
    ("vesta_regolith", "灶神星表土", "Vestan Regolith", "#8A8478", "#A8A296", 0.12, 0.6, "none", "regolith", None),
    ("eucrite", "钙长辉长无球粒陨石", "Eucrite", "#6A6458", "#B0A890", 0.25, 2.5, "wood", "crystal", None),
    ("diogenite", "古铜辉石岩", "Diogenite", "#6A6A40", "#A8A050", 0.25, 2.5, "stone", "crystal", ("olivine", 1, 2)),
    ("sulfur_crust", "木卫一硫磺壳", "Ionian Sulfur Crust", "#D8C040", "#F0E070", 0.2, 0.8, "none", "deposit", ("sulfur", 2, 4)),
    ("so2_frost", "二氧化硫霜", "Sulfur Dioxide Frost", "#E8E8D0", "#FFFFF0", 0.2, 0.6, "none", "frost", None),
    ("io_silicate", "木卫一硅酸盐火山岩", "Ionian Silicate Lava Rock", "#3A3020", "#6A5030", 0.2, 2.5, "wood", "basalt", None),
    ("europa_ice", "木卫二冰壳", "Europan Ice Shell", "#C8C0B0", "#E8E4DC", 0.15, 1.2, "wood", "ice", None),
    ("lineae_salt", "条纹带盐沉积", "Lineae Salt Deposit", "#8A5A3A", "#B07848", 0.3, 1.0, "stone", "layered", ("europan_salt", 2, 3)),
    ("ganymede_dark", "木卫三暗色地体", "Ganymede Dark Terrain", "#4A4440", "#6A625A", 0.15, 1.5, "wood", "soil", None),
    ("grooved_ice", "木卫三沟槽冰", "Grooved Terrain Ice", "#A8A8A8", "#D0D4D8", 0.2, 1.5, "wood", "ice", None),
    ("callisto_regolith", "木卫四暗色风化层", "Callistan Dark Regolith", "#3A342E", "#5A5048", 0.12, 0.6, "none", "regolith", None),
    ("enceladus_snow", "土卫二冰雪", "Enceladan Snow", "#F0F4F8", "#FFFFFF", 0.1, 0.4, "none", "frost", None),
    ("tiger_stripe_ice", "虎纹裂隙冰", "Tiger Stripe Ice", "#9AB8D0", "#E0F0FF", 0.25, 1.2, "wood", "ice", ("plume_crystal", 1, 2)),
    ("tholin_sand", "土卫六有机沙丘", "Titan Tholin Sand", "#5A3A20", "#7A5030", 0.2, 0.5, "none", "sand", None),
    ("titan_ice_bedrock", "土卫六冰质基岩", "Titan Ice Bedrock", "#8A8070", "#A8A090", 0.15, 2.0, "wood", "ice", None),
    ("tholin_deposit", "托林有机质沉积", "Tholin Deposit", "#7A4A20", "#B07030", 0.3, 1.0, "none", "deposit", ("tholin", 2, 3)),
    ("miranda_ice", "天卫五冰", "Mirandan Ice", "#9A9A98", "#C0C0BC", 0.15, 1.5, "wood", "ice", None),
    ("corona_ridge", "冕状区条带岩", "Corona Banded Rock", "#6A6A68", "#B8B8B0", 0.3, 2.0, "wood", "layered", None),
    ("nitrogen_ice", "氮冰", "Nitrogen Ice", "#E8E0D8", "#FFFFFF", 0.15, 0.6, "none", "frost", ("solid_nitrogen", 2, 3)),
    ("cantaloupe_terrain", "哈密瓜地形冰", "Cantaloupe Terrain Ice", "#B8A898", "#D8C8B8", 0.25, 1.2, "wood", "layered", None),
    ("methane_ice", "甲烷冰（刃脊）", "Bladed Methane Ice", "#D8C8C0", "#F4ECE8", 0.2, 1.0, "wood", "ice", None),
    ("tholin_crust", "冥王星托林红壳", "Plutonian Tholin Crust", "#6A3020", "#A05030", 0.25, 0.8, "none", "soil", ("tholin", 1, 2)),
    ("water_ice_bedrock", "水冰基岩", "Water Ice Bedrock", "#B0B0B8", "#D8D8E0", 0.15, 2.5, "wood", "ice", None),
    ("charon_regolith", "卡戎冰质表土", "Charon Icy Regolith", "#8A8682", "#A8A4A0", 0.15, 0.6, "none", "regolith", None),
    ("eris_frost", "阋神星甲烷霜", "Eridian Methane Frost", "#F4F4F4", "#FFFFFF", 0.1, 0.6, "none", "frost", None),
    ("ammonia_cloud", "氨冰云", "Ammonia Ice Cloud", "#E8DCC0", "#FFF8E8", 0.2, 0.2, "none", "frost", "none"),
    ("nh4sh_cloud", "硫氢化铵云", "Ammonium Hydrosulfide Cloud", "#A86040", "#D08868", 0.2, 0.2, "none", "frost", "none"),
    ("methane_cloud", "甲烷冰云", "Methane Ice Cloud", "#90C8E0", "#C8ECF8", 0.2, 0.2, "none", "frost", "none"),
    ("liquid_methane", "液态甲烷", "Liquid Methane", "#1A1410", "#3A2A20", 0.2, 100.0, "none", "sand", "none"),
    ("aerostat_deck", "浮空平台甲板", "Aerostat Deck", "#8A8E96", "#5A5E66", 0.0, 4.0, "stone", "bricks", None),
]

WORLD_ITEMS = [
    ("volatile_sulfide", "挥发性硫化物", "Volatile Sulfides", "dust", "#A8B8D0"),
    ("bismuth_sulfide", "辉铋矿精矿", "Bismuthinite Concentrate", "raw", "#9898A0", "#E0E0F0"),
    ("bismuth_ingot", "铋锭", "Bismuth Ingot", "ingot", "#D0B8D8"),
    ("carbonaceous_chondrite", "碳质球粒陨石", "Carbonaceous Chondrite", "raw", "#3A3430", "#7A6A60"),
    ("sodium_carbonate", "碳酸钠", "Sodium Carbonate", "dust", "#F0F0EC"),
    ("europan_salt", "木卫二海盐", "Europan Sea Salt", "dust", "#C08860"),
    ("plume_crystal", "羽流冰晶", "Plume Ice Crystal", "gem", "#C0E0FF"),
    ("tholin", "托林", "Tholin", "dust", "#A05028"),
    ("solid_nitrogen", "氮冰块", "Solid Nitrogen", "gem", "#F0ECE8"),
]

# Dimensions for every landable body: id, sky, fog, ambient, night light, sunset colour or None
BODY_DIMS = [
    ("mercury", "#000000", "#000000", 0.0, 0.08, None),
    ("venus", "#C8A060", "#B88A48", 0.15, 0.45, "#d09040ff"),
    ("phobos", "#000000", "#000000", 0.0, 0.25, None),
    ("deimos", "#000000", "#000000", 0.0, 0.25, None),
    ("ceres", "#000000", "#000000", 0.0, 0.06, None),
    ("vesta", "#000000", "#000000", 0.0, 0.06, None),
    ("jupiter", "#C8A880", "#B89870", 0.1, 0.25, "#e0a070ff"),
    ("io", "#000000", "#000000", 0.0, 0.3, None),
    ("europa", "#000000", "#000000", 0.0, 0.25, None),
    ("ganymede", "#000000", "#000000", 0.0, 0.2, None),
    ("callisto", "#000000", "#000000", 0.0, 0.15, None),
    ("saturn", "#E0CFA0", "#D0BC88", 0.1, 0.25, "#e8c890ff"),
    ("enceladus", "#000000", "#000000", 0.0, 0.3, None),
    ("titan", "#B07830", "#9A6828", 0.1, 0.3, "#c08040ff"),
    ("uranus", "#A0D8E0", "#88C8D8", 0.1, 0.2, None),
    ("miranda", "#000000", "#000000", 0.0, 0.15, None),
    ("neptune", "#4870E0", "#3858C8", 0.1, 0.2, None),
    ("triton", "#000000", "#000000", 0.0, 0.25, None),
    ("pluto", "#101418", "#0A0C10", 0.0, 0.1, None),
    ("charon", "#000000", "#000000", 0.0, 0.2, None),
    ("eris", "#000000", "#000000", 0.0, 0.05, None),
]

for row in WORLD_ITEMS:
    item("planets", row[0], row[1], row[2], *row[3:])

SOLAR_DAY_HOURS = {"mercury": 4222.6, "venus": 2802.0, "phobos": 7.65, "deimos": 30.3, "ceres": 9.074, "vesta": 5.342, "jupiter": 9.925,
                   "io": 42.46, "europa": 85.2, "ganymede": 171.7, "callisto": 400.5, "saturn": 10.656, "enceladus": 32.9, "titan": 382.7,
                   "uranus": 17.24, "miranda": 33.9, "neptune": 16.11, "triton": 141.0, "pluto": 153.3, "charon": 153.3, "eris": 379.0}
