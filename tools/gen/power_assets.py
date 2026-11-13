#!/usr/bin/env python3
"""Generators and batteries of the power grid (overlay 05-industry): 16x16 textures, orientable block models
(off and on), blockstates, item definitions, loot tables, crafting recipes and language entries.
Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/05-industry/src/main/resources/assets/heliosphere'
D = 'jars/05-industry/src/main/resources/data/heliosphere'
T = f'{A}/textures/block/power'
rng = np.random.default_rng(11)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def merge(code, entries):
    path = f'{A}/lang/{code}.json'
    have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    have.update(entries)
    write(path, have)


def C(*v): return np.array(v, float)


STEEL = C(132, 138, 144); STEEL_D = C(84, 88, 94); STEEL_L = C(176, 182, 188); DARK = C(36, 38, 42)
WHITE = C(218, 222, 224); YELLOW = C(222, 182, 52); RED = C(186, 52, 40); GREEN = C(80, 210, 110)
COPPER = C(184, 110, 64); LEAD = C(92, 96, 108); BLUE = C(60, 120, 210); ORANGE = C(240, 130, 40)


def canvas(base, noise=6):
    return np.zeros((16, 16, 3)) + base + rng.normal(0, noise, (16, 16, 1))


def frame(img, dark=STEEL_D, light=STEEL_L):
    img[0, :] = light; img[:, 0] = light; img[15, :] = dark; img[:, 15] = dark


def rivets(img, col=STEEL_L):
    for p in ((1, 1), (1, 14), (14, 1), (14, 14)): img[p] = col


def save(img, name):
    os.makedirs(T, exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f'{T}/{name}.png')


def panel(img, y0, y1, x0, x1, col):
    img[y0:y1, x0:x1] = col
    img[y0, x0:x1] = col * 0.7; img[y0:y1, x0] = col * 0.7


def gauge(img, y, x0, n, on, lit=True):
    for i in range(n):
        img[y, x0 + i] = (GREEN if i < on else C(40, 70, 44)) if lit else C(40, 50, 44)


# ---- textures: <id>_front, <id>_front_on, <id>_side, <id>_top -------------------------------------------------------

def tex_wind():
    for on in (False, True):
        img = canvas(WHITE, 4); frame(img, C(170, 174, 176), C(240, 242, 244))
        panel(img, 5, 11, 5, 11, STEEL)                                   # shaft bearing
        img[7:9, 7:9] = DARK
        gauge(img, 13, 4, 8, 6 if on else 0, on)
        save(img, 'wind_turbine_front' + ('_on' if on else ''))
    img = canvas(WHITE, 4); frame(img, C(170, 174, 176), C(240, 242, 244))
    for x in range(2, 14, 3): img[3:13, x] = C(196, 200, 202)            # cooling louvres
    img[12, 2:14] = RED                                                   # red band
    save(img, 'wind_turbine_side')
    img = canvas(WHITE, 4); frame(img, C(170, 174, 176), C(240, 242, 244))
    img[6:10, 6:10] = RED; img[7:9, 7:9] = C(255, 90, 70)                 # aviation light
    save(img, 'wind_turbine_top')


def tex_geo():
    for on in (False, True):
        img = canvas(STEEL_D, 6); frame(img); rivets(img)
        panel(img, 3, 12, 3, 13, DARK)                                     # heat exchanger window
        for y in range(4, 12, 2): img[y, 4:12] = (ORANGE if on else C(80, 50, 40)) * (0.8 + 0.2 * (y % 4 == 0))
        gauge(img, 13, 4, 8, 7 if on else 0, on)
        save(img, 'geothermal_generator_front' + ('_on' if on else ''))
    img = canvas(STEEL_D, 6); frame(img); rivets(img)
    for y in range(3, 14, 3): img[y, 2:14] = STEEL
    img[2:14, 7:9] = COPPER                                                # steam pipe
    save(img, 'geothermal_generator_side')
    img = canvas(STEEL, 5); frame(img)
    for r in range(2, 7):
        for a in np.linspace(0, 2 * np.pi, 40):
            y, x = int(7.5 + r * np.sin(a)), int(7.5 + r * np.cos(a))
            img[y, x] = DARK if r % 2 else STEEL_L                         # vent grille
    save(img, 'geothermal_generator_top')


def tex_rtg():
    for on in (False, True):
        img = canvas(LEAD, 5); frame(img, C(60, 62, 70), C(130, 134, 144))
        for x in range(1, 15, 2): img[1:15, x] = LEAD * 0.82               # radiator fins
        panel(img, 5, 11, 4, 12, YELLOW)                                    # radiation trefoil plate
        img[7:9, 7:9] = DARK; img[6, 6] = img[6, 9] = img[9, 7] = img[9, 8] = DARK
        img[12, 5:11] = GREEN if on else C(40, 60, 44)
        save(img, 'rtg_front' + ('_on' if on else ''))
    img = canvas(LEAD, 5); frame(img, C(60, 62, 70), C(130, 134, 144))
    for x in range(1, 15, 2): img[1:15, x] = LEAD * 0.8
    save(img, 'rtg_side')
    img = canvas(LEAD, 5); frame(img, C(60, 62, 70), C(130, 134, 144))
    img[5:11, 5:11] = C(70, 72, 80); img[7:9, 7:9] = C(200, 80, 40)
    save(img, 'rtg_top')


def tex_fuel_cell():
    for on in (False, True):
        img = canvas(C(208, 212, 216), 4); frame(img, C(150, 154, 158), C(236, 238, 240))
        for y in range(3, 12, 2): img[y, 3:13] = BLUE * (1 if on else 0.55)   # membrane stack
        img[3:12, 2] = img[3:12, 13] = STEEL_D
        gauge(img, 13, 4, 8, 5 if on else 0, on)
        img[1, 6:10] = C(40, 120, 200)                                      # H2 label
        save(img, 'hydrogen_fuel_cell_front' + ('_on' if on else ''))
    img = canvas(C(208, 212, 216), 4); frame(img, C(150, 154, 158), C(236, 238, 240))
    img[4:12, 4:12] = C(190, 196, 202); img[7, 4:12] = BLUE
    save(img, 'hydrogen_fuel_cell_side')
    img = canvas(C(208, 212, 216), 4); frame(img, C(150, 154, 158), C(236, 238, 240))
    img[6:10, 2:14] = STEEL_D; img[7:9, 3:13] = STEEL_L                    # manifold
    save(img, 'hydrogen_fuel_cell_top')


def tex_fission():
    for on in (False, True):
        img = canvas(C(196, 200, 204), 4); frame(img, STEEL_D, C(232, 234, 236))
        for y in range(2, 14, 4):
            for x in range(2, 14):
                img[y, x] = YELLOW if (x // 2) % 2 == 0 else DARK            # hazard stripes
        panel(img, 4, 12, 4, 12, C(40, 60, 90))                              # core viewport
        if on: img[6:10, 6:10] = C(90, 190, 255); img[7:9, 7:9] = C(200, 240, 255)
        else: img[6:10, 6:10] = C(40, 60, 80)
        save(img, 'fission_reactor_front' + ('_on' if on else ''))
    img = canvas(C(196, 200, 204), 4); frame(img, STEEL_D, C(232, 234, 236)); rivets(img, STEEL_D)
    img[2:14, 3] = img[2:14, 12] = BLUE * 0.8                              # coolant lines
    img[7:9, 3:13] = STEEL
    save(img, 'fission_reactor_side')
    img = canvas(C(196, 200, 204), 4); frame(img, STEEL_D, C(232, 234, 236))
    for a in np.linspace(0, 2 * np.pi, 48):
        img[int(7.5 + 5 * np.sin(a)), int(7.5 + 5 * np.cos(a))] = STEEL_D   # containment ring
    img[6:10, 6:10] = YELLOW; img[7:9, 7:9] = DARK
    save(img, 'fission_reactor_top')


def tex_battery(kid, body, accent, cells):
    for on in (False, True):
        img = canvas(body, 5); frame(img, body * 0.6, np.minimum(body * 1.3, 255)); rivets(img, body * 1.2)
        for i in range(cells):
            x0 = 2 + i * (12 // cells)
            img[3:11, x0:x0 + 12 // cells - 1] = accent
            img[3, x0:x0 + 12 // cells - 1] = accent * 1.25
        gauge(img, 13, 3, 10, 8 if on else 1, True)
        save(img, f'{kid}_front' + ('_on' if on else ''))
    img = canvas(body, 5); frame(img, body * 0.6, np.minimum(body * 1.3, 255))
    for y in range(3, 14, 3): img[y, 2:14] = body * 0.75
    save(img, f'{kid}_side')
    img = canvas(body, 5); frame(img, body * 0.6, np.minimum(body * 1.3, 255))
    img[4:6, 3:6] = RED; img[4:6, 10:13] = DARK                            # terminals
    save(img, f'{kid}_top')


BLOCKS = ['wind_turbine', 'geothermal_generator', 'rtg', 'hydrogen_fuel_cell', 'fission_reactor',
          'lithium_battery_cabinet', 'supercapacitor_bank', 'grid_storage_array']


def models():
    for b in BLOCKS:
        t = lambda n: f'heliosphere:block/power/{b}_{n}'
        for on in (False, True):
            write(f'{A}/models/block/{b}{"_on" if on else ""}.json', {'parent': 'minecraft:block/orientable', 'textures': {
                'top': t('top'), 'front': t('front_on' if on else 'front'), 'side': t('side')}})
        write(f'{A}/blockstates/{b}.json', {'variants': {
            f'facing={f},lit={str(on).lower()}': ({'model': f'heliosphere:block/{b}{"_on" if on else ""}', 'y': y} if y else {'model': f'heliosphere:block/{b}{"_on" if on else ""}'})
            for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for on in (False, True)}})
        write(f'{A}/items/{b}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{b}'}})
        write(f'{D}/loot_table/blocks/{b}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{b}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'heliosphere:blocks/{b}'})


RECIPES = {
    # blades, generator (magnet and coil), controller
    'wind_turbine': (['PPP', 'WMW', 'SCS'], {'P': 'steel_plate', 'W': 'copper_wire', 'M': 'ndfeb_magnet', 'S': 'steel_ingot', 'C': 'circuit_board'}),
    'geothermal_generator': (['IBI', 'WEW', 'IPI'], {'I': 'inconel_plate', 'B': 'turbine_blade', 'W': 'copper_wire', 'E': 'electric_motor', 'P': 'steel_plate'}),
    'rtg': (['LLL', 'TCT', 'LWL'], {'L': 'lead_ingot', 'T': 'tungsten_ingot', 'C': 'circuit_board', 'W': 'copper_wire'}),
    'hydrogen_fuel_cell': (['SGS', 'WCW', 'SBS'], {'S': 'stainless_steel_plate', 'G': 'graphite', 'W': 'copper_wire', 'C': 'circuit_board', 'B': 'battery_cell'}),
    'fission_reactor': (['SLS', 'GPG', 'IBI'], {'S': 'stainless_steel_plate', 'L': 'lead_ingot', 'G': 'graphite', 'P': 'processor', 'I': 'inconel_plate', 'B': 'turbine_blade'}),
    'lithium_battery_cabinet': (['BBB', 'BCB', 'SWS'], {'B': 'battery_cell', 'C': 'circuit_board', 'S': 'steel_plate', 'W': 'copper_wire'}),
    'supercapacitor_bank': (['GAG', 'WCW', 'GFG'], {'G': 'graphite', 'A': 'al_li_plate', 'W': 'copper_wire', 'C': 'circuit_board', 'F': 'carbon_fiber'}),
    'grid_storage_array': (['SLS', 'LPL', 'SLS'], {'S': 'stainless_steel_plate', 'L': 'lithium_battery_cabinet', 'P': 'processor'}),
}


def recipes():
    for b, (pattern, key) in RECIPES.items():
        write(f'{D}/recipe/{b}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                                       'key': {k: f'heliosphere:{v}' for k, v in key.items()}, 'result': {'id': f'heliosphere:{b}', 'count': 1}})


def lang():
    zh = {
        'block.heliosphere.wind_turbine': '风力发电机',
        'block.heliosphere.geothermal_generator': '地热发电机',
        'block.heliosphere.rtg': '放射性同位素热电机（RTG）',
        'block.heliosphere.hydrogen_fuel_cell': '氢燃料电池',
        'block.heliosphere.fission_reactor': '小型裂变反应堆',
        'block.heliosphere.lithium_battery_cabinet': '锂电池柜',
        'block.heliosphere.supercapacitor_bank': '超级电容组',
        'block.heliosphere.grid_storage_array': '大型储能阵列',
        'message.heliosphere.power_running': '发电中 %s FE/t',
        'message.heliosphere.power_no_air': '没有大气：风力发电机只能在有空气的地方工作',
        'message.heliosphere.power_sheltered': '四周被挡住了：放在高处、周围 2 格内留空',
        'message.heliosphere.power_no_heat': '下方 4 格内没有岩浆或岩浆块',
        'message.heliosphere.power_no_coolant': '没有冷却水：反应堆旁边需要有水',
        'message.heliosphere.power_no_fuel': '没有燃料：手持燃料右键装填',
        'message.heliosphere.power_fuel_full': '燃料已满（最多 %s 份）',
        'message.heliosphere.power_fueled': '已装填 %s · 燃料 %s/%s',
        'message.heliosphere.power_status': '%s · %s · 储能 %s FE',
        'message.heliosphere.power_fuel_status': '%s · %s · 储能 %s FE · 燃料 %s/%s（约可用 %s 天）',
        'message.heliosphere.power_battery_status': '%s · 储能 %s FE（%s%%）· 最大输出 %s FE/t',
    }
    tw = {k: v.replace('风力发电机', '風力發電機').replace('地热发电机', '地熱發電機').replace('放射性同位素热电机', '放射性同位素熱電機')
          .replace('氢燃料电池', '氫燃料電池').replace('小型裂变反应堆', '小型裂變反應堆').replace('锂电池柜', '鋰電池櫃')
          .replace('超级电容组', '超級電容組').replace('大型储能阵列', '大型儲能陣列').replace('发电', '發電').replace('储能', '儲能')
          .replace('没有', '沒有').replace('燃料已满', '燃料已滿').replace('装填', '裝填').replace('冷却', '冷卻').replace('反应堆', '反應堆')
          .replace('岩浆', '岩漿').replace('块', '塊').replace('挡住', '擋住').replace('周围', '周圍').replace('输出', '輸出').replace('约', '約')
          .replace('边', '邊').replace('气', '氣').replace('只能在有空气的地方工作', '只能在有空氣的地方工作').replace('处', '處').replace('留空', '留空')
          for k, v in zh.items()}
    en = {
        'block.heliosphere.wind_turbine': 'Wind Turbine',
        'block.heliosphere.geothermal_generator': 'Geothermal Generator',
        'block.heliosphere.rtg': 'Radioisotope Thermoelectric Generator',
        'block.heliosphere.hydrogen_fuel_cell': 'Hydrogen Fuel Cell',
        'block.heliosphere.fission_reactor': 'Small Fission Reactor',
        'block.heliosphere.lithium_battery_cabinet': 'Lithium Battery Cabinet',
        'block.heliosphere.supercapacitor_bank': 'Supercapacitor Bank',
        'block.heliosphere.grid_storage_array': 'Grid Storage Array',
        'message.heliosphere.power_running': 'generating %s FE/t',
        'message.heliosphere.power_no_air': 'no atmosphere: a wind turbine needs air',
        'message.heliosphere.power_sheltered': 'sheltered: put it up high with 2 blocks of open air around it',
        'message.heliosphere.power_no_heat': 'no lava or magma within 4 blocks below',
        'message.heliosphere.power_no_coolant': 'no coolant: the reactor needs water next to it',
        'message.heliosphere.power_no_fuel': 'out of fuel: right-click with fuel to load it',
        'message.heliosphere.power_fuel_full': 'Fuel full (%s at most)',
        'message.heliosphere.power_fueled': 'Loaded %s · fuel %s/%s',
        'message.heliosphere.power_status': '%s · %s · stored %s FE',
        'message.heliosphere.power_fuel_status': '%s · %s · stored %s FE · fuel %s/%s (about %s days)',
        'message.heliosphere.power_battery_status': '%s · stored %s FE (%s%%) · output up to %s FE/t',
    }
    merge('zh_cn', zh); merge('zh_tw', tw); merge('en_us', en)


if __name__ == '__main__':
    tex_wind(); tex_geo(); tex_rtg(); tex_fuel_cell(); tex_fission()
    tex_battery('lithium_battery_cabinet', C(70, 92, 120), C(120, 200, 230), 4)
    tex_battery('supercapacitor_bank', C(64, 64, 72), C(200, 170, 60), 6)
    tex_battery('grid_storage_array', C(60, 96, 72), C(110, 220, 140), 3)
    models(); recipes(); lang()
    print('power assets written')
