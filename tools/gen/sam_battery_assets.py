#!/usr/bin/env python3
"""The air-defence battery pack (overlay 06-weapons): 16x16 textures, block model with a solar lid, blockstate,
item definition and language entries. Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'
T = f'{A}/textures/block/sam_battery'
rng = np.random.default_rng(7)


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


OLIVE = np.array([86, 96, 62]); OLIVE_D = np.array([58, 66, 42]); OLIVE_L = np.array([112, 122, 82])
STEEL = np.array([150, 154, 156]); STEEL_D = np.array([88, 92, 96]); BLACK = np.array([30, 32, 30])
YELLOW = np.array([214, 172, 48]); RED = np.array([178, 44, 36]); GREEN = np.array([90, 220, 110])
CELL = np.array([34, 52, 92]); CELL_L = np.array([62, 92, 150])


def canvas(base, noise=7):
    img = np.zeros((16, 16, 3), float) + base
    img += rng.normal(0, noise, (16, 16, 1))
    return img


def frame(img, dark, light):
    img[0, :] = light; img[:, 0] = light
    img[15, :] = dark; img[:, 15] = dark


def save(img, name):
    os.makedirs(T, exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f'{T}/{name}.png')


def front():
    # the case front (rows 6..15 show): hazard band, a charge gauge with green bars, a stencilled bolt, rivets;
    # the two terminal posts stand out of the face at the left and right
    img = canvas(OLIVE)
    frame(img, OLIVE_D, OLIVE_L)
    for x in range(1, 15):
        img[6, x] = YELLOW if (x // 2) % 2 == 0 else BLACK
    img[8:11, 5:11] = BLACK
    for x, on in zip(range(6, 10), (1, 1, 1, 0)):
        img[9, x] = GREEN if on else np.array([40, 70, 44])
    for y, x in ((11, 8), (12, 7), (12, 8), (13, 8), (13, 9), (14, 8)): img[y, x] = YELLOW
    for p in ((7, 1), (7, 14), (14, 1), (14, 14)): img[p] = STEEL
    save(img, 'front')


def side():
    img = canvas(OLIVE)
    frame(img, OLIVE_D, OLIVE_L)
    for x in (4, 8, 12):                                           # pressed ribs
        img[7:15, x] = OLIVE_D; img[7:15, x - 1] = OLIVE_L
    img[6, 1:15] = OLIVE_D
    img[9:11, 5:11] = BLACK                                         # stencil plate
    img[9, 6] = img[9, 8] = img[10, 7] = img[10, 9] = OLIVE_L
    for p in ((7, 1), (7, 14), (14, 1), (14, 14)): img[p] = STEEL
    save(img, 'side')


def bottom():
    img = canvas(OLIVE_D, 5)
    frame(img, BLACK, OLIVE)
    save(img, 'bottom')


def solar():
    # the lid: 4x3 blue cells with silver busbars in an aluminium frame
    img = canvas(STEEL, 4)
    frame(img, STEEL_D, np.array([190, 194, 196]))
    for cy in range(3):
        for cx in range(4):
            y0, x0 = 1 + cy * 5, 1 + cx * 3 + (cx > 1)
            cell = CELL + rng.normal(0, 6, 3)
            img[y0:y0 + 4, x0:x0 + 3] = cell
            img[y0, x0:x0 + 3] = CELL_L
            img[y0 + 2, x0:x0 + 3] = cell * 0.85 + STEEL * 0.15
    save(img, 'solar')


def edge():
    img = canvas(STEEL, 4)
    img[:, :] = STEEL + rng.normal(0, 5, (16, 16, 1))
    img[0:2, :] = np.array([190, 194, 196]); img[14:16, :] = STEEL_D
    save(img, 'edge')


def parts():
    # terminals: red (0..3) and black (4..7) caps
    img = canvas(STEEL_D, 3)
    img[0:4, 0:4] = RED; img[0, 0:4] = RED * 1.3
    img[0:4, 4:8] = BLACK; img[0, 4:8] = BLACK + 30
    save(img, 'parts')


def model():
    t = lambda n: f'heliosphere:block/sam_battery/{n}'
    def box(f, to, faces): return {'from': f, 'to': to, 'faces': faces}
    case = box([1, 0, 2], [15, 10, 14], {
        'north': {'texture': '#front', 'uv': [1, 6, 15, 16]}, 'south': {'texture': '#side', 'uv': [1, 6, 15, 16]},
        'east': {'texture': '#side', 'uv': [2, 6, 14, 16]}, 'west': {'texture': '#side', 'uv': [2, 6, 14, 16]},
        'down': {'texture': '#bottom', 'cullface': 'down'}, 'up': {'texture': '#bottom'}})
    lid = box([0, 10, 1], [16, 12, 15], {
        'up': {'texture': '#solar', 'uv': [0, 1, 16, 15]}, 'down': {'texture': '#edge'},
        'north': {'texture': '#edge', 'uv': [0, 0, 16, 2]}, 'south': {'texture': '#edge', 'uv': [0, 0, 16, 2]},
        'east': {'texture': '#edge', 'uv': [1, 0, 15, 2]}, 'west': {'texture': '#edge', 'uv': [1, 0, 15, 2]}})
    def term(x0, u):
        uv = [u, 0, u + 2, 2]
        return box([x0, 7, 1], [x0 + 2, 9, 2], {d: {'texture': '#parts', 'uv': uv} for d in ('north', 'east', 'west', 'up', 'down')})
    write(f'{A}/models/block/sam_battery.json', {
        'parent': 'minecraft:block/block',
        'textures': {'particle': t('side'), 'front': t('front'), 'side': t('side'), 'bottom': t('bottom'),
                     'solar': t('solar'), 'edge': t('edge'), 'parts': t('parts')},
        'elements': [case, lid, term(2, 0), term(12, 4)]})
    write(f'{A}/blockstates/sam_battery.json', {'variants': {
        f'facing={f}': ({'model': 'heliosphere:block/sam_battery', 'y': y} if y else {'model': 'heliosphere:block/sam_battery'})
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    write(f'{A}/items/sam_battery.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:block/sam_battery'}})


def lang():
    merge('zh_cn', {
        'block.heliosphere.sam_battery': '防空电池组',
        'item.heliosphere.sam_battery.charge': '电量 %s FE（%s%%）',
        'item.heliosphere.sam_battery.hint': '放在防空车 %s 格内即可无线供电，无需电缆；白天顶部太阳能板会慢慢充电',
        'message.heliosphere.sam_battery_status': '防空电池组 · 电量 %s/%s FE（%s%%）· 正在供电 %s 辆防空车（%s 格内）· %s',
        'message.heliosphere.sam_battery_sun': '太阳能充电中',
        'message.heliosphere.sam_battery_dark': '无日照',
        'message.heliosphere.sam_no_power': '§e没有电。在 %s 格内放一个「防空电池组」即可无线供电（装配站制造，出厂满电），也可以用电缆接到车辆中心方块。',
    })
    merge('zh_tw', {
        'block.heliosphere.sam_battery': '防空電池組',
        'item.heliosphere.sam_battery.charge': '電量 %s FE（%s%%）',
        'item.heliosphere.sam_battery.hint': '放在防空車 %s 格內即可無線供電，無需電纜；白天頂部太陽能板會慢慢充電',
        'message.heliosphere.sam_battery_status': '防空電池組 · 電量 %s/%s FE（%s%%）· 正在供電 %s 輛防空車（%s 格內）· %s',
        'message.heliosphere.sam_battery_sun': '太陽能充電中',
        'message.heliosphere.sam_battery_dark': '無日照',
        'message.heliosphere.sam_no_power': '§e沒有電。在 %s 格內放一個「防空電池組」即可無線供電（裝配站製造，出廠滿電），也可以用電纜接到車輛中心方塊。',
    })
    merge('en_us', {
        'block.heliosphere.sam_battery': 'Air Defence Battery Pack',
        'item.heliosphere.sam_battery.charge': 'Charge %s FE (%s%%)',
        'item.heliosphere.sam_battery.hint': 'Powers air-defence vehicles within %s blocks without cables; the solar lid recharges it in daylight',
        'message.heliosphere.sam_battery_status': 'Battery pack · %s/%s FE (%s%%) · powering %s vehicles (within %s blocks) · %s',
        'message.heliosphere.sam_battery_sun': 'solar charging',
        'message.heliosphere.sam_battery_dark': 'no sunlight',
        'message.heliosphere.sam_no_power': '§eNo power. Put an Air Defence Battery Pack within %s blocks to power it without cables (made at the assembly station, comes fully charged), or run a cable to the vehicle\'s centre block.',
    })


if __name__ == '__main__':
    front(); side(); bottom(); solar(); edge(); parts(); model(); lang()
    print('sam battery assets written')
