#!/usr/bin/env python3
"""Air-defence ammunition (overlay 06-weapons): the ammo crate block and the three missile rounds - 16x16 textures,
models, blockstate, item definitions, loot table and language entries. Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'
D = 'jars/06-weapons/src/main/resources/data/heliosphere'
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


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(path)


OLIVE = np.array([84, 94, 58]); OLIVE_D = np.array([54, 62, 38]); OLIVE_L = np.array([108, 118, 76])
YELLOW = np.array([212, 172, 52]); BLACK = np.array([28, 30, 28]); STEEL = np.array([150, 154, 156]); ROPE = np.array([70, 58, 40])


def canvas(base, noise=6):
    return np.zeros((16, 16, 3), float) + base + rng.normal(0, noise, (16, 16, 1))


def crate_textures():
    T = f'{A}/textures/block/sam_ammo_crate'
    # side: planked steel crate, end frames, a yellow band with a missile silhouette, rope handles
    side = canvas(OLIVE)
    for y in (5, 15): side[y, :] = OLIVE_D
    side[5:16, 0] = OLIVE_L; side[5:16, 15] = OLIVE_D
    side[5:16, 1] = OLIVE_D; side[5:16, 14] = OLIVE_L
    for y in range(6, 15, 3): side[y, 2:14] = side[y, 2:14] * 0.85                       # board seams
    side[8:11, 3:13] = YELLOW
    for x in range(4, 12): side[9, x] = BLACK                                              # missile body
    side[9, 12] = BLACK; side[8, 4] = side[10, 4] = BLACK                                  # nose, fins
    side[12, 6:10] = ROPE                                                                  # handle
    save(side, f'{T}/side.png')
    # top: the lid with two latches and a stencilled arrow
    top = canvas(OLIVE)
    top[0, :] = OLIVE_L; top[:, 0] = OLIVE_L; top[15, :] = OLIVE_D; top[:, 15] = OLIVE_D
    for x in (4, 11): top[1:15, x] = OLIVE_D
    for y in (3, 12): top[y, 6:10] = STEEL
    top[6:10, 7:9] = YELLOW; top[5, 7:9] = YELLOW; top[4, 7:9] = YELLOW
    save(top, f'{T}/top.png')
    end = canvas(OLIVE_D, 4)
    end[5:16, 0] = OLIVE; end[5:16, 15] = BLACK
    end[9:12, 5:11] = YELLOW; end[10, 6:10] = BLACK
    save(end, f'{T}/end.png')


def crate_model():
    t = lambda n: f'heliosphere:block/sam_ammo_crate/{n}'
    box = {'from': [0, 0, 1], 'to': [16, 11, 15], 'faces': {
        'north': {'texture': '#side', 'uv': [0, 5, 16, 16]}, 'south': {'texture': '#side', 'uv': [0, 5, 16, 16]},
        'east': {'texture': '#end', 'uv': [1, 5, 15, 16]}, 'west': {'texture': '#end', 'uv': [1, 5, 15, 16]},
        'up': {'texture': '#top', 'uv': [0, 1, 16, 15]}, 'down': {'texture': '#end', 'cullface': 'down'}}}
    write(f'{A}/models/block/sam_ammo_crate.json', {'parent': 'minecraft:block/block',
        'textures': {'particle': t('side'), 'side': t('side'), 'top': t('top'), 'end': t('end')}, 'elements': [box]})
    write(f'{A}/blockstates/sam_ammo_crate.json', {'variants': {
        f'facing={f}': ({'model': 'heliosphere:block/sam_ammo_crate', 'y': y} if y else {'model': 'heliosphere:block/sam_ammo_crate'})
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    write(f'{A}/items/sam_ammo_crate.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:block/sam_ammo_crate'}})
    write(f'{D}/loot_table/blocks/sam_ammo_crate.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
        'entries': [{'type': 'minecraft:item', 'name': 'heliosphere:sam_ammo_crate'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


ROUNDS = {
    # id: (body, nose, band, fin colour, booster)
    'missile_9m317ma': ((236, 236, 230), (150, 152, 146), (30, 30, 30), (210, 210, 200), False),
    'missile_aster30': ((238, 238, 232), (238, 238, 232), (30, 30, 30), (200, 200, 192), True),
    'missile_iris_t_sl': ((232, 232, 226), (60, 76, 92), (214, 176, 48), (205, 205, 196), False),
}


def round_icon(rid, body, nose, band, fin, booster):
    img = np.zeros((16, 16, 4), float)
    def px(x, y, c, a=255):
        if 0 <= x < 16 and 0 <= y < 16: img[y, x] = (*c, a)
    # a missile lying diagonally, nose at the top right
    for i in range(2, 13):
        x, y = i, 15 - i
        shade = 0.82 if i % 2 else 1.0
        c = tuple(int(v * shade) for v in body)
        px(x, y, c); px(x + 1, y, tuple(int(v * 0.7) for v in body)); px(x, y - 1, tuple(min(255, int(v * 1.05)) for v in body))
    for i in (13, 14):
        px(i, 15 - i, nose); px(i, 14 - i, nose)
    px(14, 0, nose)
    px(10, 5, band); px(11, 5, band); px(10, 4, band)
    if booster:                                                     # Aster: the fat booster at the tail
        for i in range(1, 5):
            px(i, 15 - i, (200, 200, 194)); px(i + 1, 15 - i, (150, 150, 146)); px(i, 14 - i, (220, 220, 214)); px(i - 1, 15 - i, (230, 230, 224))
        px(3, 12, (30, 30, 30)); px(4, 11, (30, 30, 30))
    fc = fin
    for (x, y) in ((1, 12), (0, 13), (4, 15), (3, 15), (2, 15), (1, 14)):
        px(x, y, fc)
    px(1, 15, (255, 200, 120)); px(0, 15, (255, 140, 60), 200)     # nozzle glow
    os.makedirs(f'{A}/textures/item', exist_ok=True)
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(f'{A}/textures/item/{rid}.png')
    write(f'{A}/models/item/{rid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{rid}'}})
    write(f'{A}/items/{rid}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{rid}'}})


def lang():
    merge('zh_cn', {
        'block.heliosphere.sam_ammo_crate': '防空弹药箱',
        'item.heliosphere.missile_9m317ma': '9M317MA 防空导弹（山毛榉）',
        'item.heliosphere.missile_aster30': '紫菀-30 防空导弹',
        'item.heliosphere.missile_iris_t_sl': 'IRIS-T SL 防空导弹',
        'message.heliosphere.sam_crate_line': '%s ×%s',
        'message.heliosphere.sam_crate_status': '防空弹药箱 %2$s/%3$s 枚 · %4$s（%5$s 格内补弹）',
        'message.heliosphere.sam_crate_empty': '空', 'message.heliosphere.sam_round.buk': '9M317MA', 'message.heliosphere.sam_round.samp': '紫菀-30', 'message.heliosphere.sam_round.iris': 'IRIS-T',
        'message.heliosphere.sam_crate_loaded': '已装入 %1$s 枚 · 箱内 %2$s/%3$s · %4$s',
        'message.heliosphere.sam_no_crate': '§e弹药未满：在 %2$s 格内放一个「防空弹药箱」并装入 %1$s 才能补弹。',
        'message.heliosphere.sam_rearming': '§a正在从弹药箱补弹（箱内还有 %s 枚）',
    })
    merge('zh_tw', {
        'block.heliosphere.sam_ammo_crate': '防空彈藥箱',
        'item.heliosphere.missile_9m317ma': '9M317MA 防空導彈（山毛櫸）',
        'item.heliosphere.missile_aster30': '紫菀-30 防空導彈',
        'item.heliosphere.missile_iris_t_sl': 'IRIS-T SL 防空導彈',
        'message.heliosphere.sam_crate_line': '%s ×%s',
        'message.heliosphere.sam_crate_status': '防空彈藥箱 %2$s/%3$s 枚 · %4$s（%5$s 格內補彈）',
        'message.heliosphere.sam_crate_empty': '空', 'message.heliosphere.sam_round.buk': '9M317MA', 'message.heliosphere.sam_round.samp': '紫菀-30', 'message.heliosphere.sam_round.iris': 'IRIS-T',
        'message.heliosphere.sam_crate_loaded': '已裝入 %1$s 枚 · 箱內 %2$s/%3$s · %4$s',
        'message.heliosphere.sam_no_crate': '§e彈藥未滿：在 %2$s 格內放一個「防空彈藥箱」並裝入 %1$s 才能補彈。',
        'message.heliosphere.sam_rearming': '§a正在從彈藥箱補彈（箱內還有 %s 枚）',
    })
    merge('en_us', {
        'block.heliosphere.sam_ammo_crate': 'Air Defence Ammo Crate',
        'item.heliosphere.missile_9m317ma': '9M317MA Missile (Buk)',
        'item.heliosphere.missile_aster30': 'Aster 30 Missile',
        'item.heliosphere.missile_iris_t_sl': 'IRIS-T SL Missile',
        'message.heliosphere.sam_crate_line': '%s x%s',
        'message.heliosphere.sam_crate_status': 'Ammo crate %2$s/%3$s · %4$s (rearms within %5$s blocks)',
        'message.heliosphere.sam_crate_empty': 'empty', 'message.heliosphere.sam_round.buk': '9M317MA', 'message.heliosphere.sam_round.samp': 'Aster 30', 'message.heliosphere.sam_round.iris': 'IRIS-T',
        'message.heliosphere.sam_crate_loaded': 'Loaded %1$s · crate %2$s/%3$s · %4$s',
        'message.heliosphere.sam_no_crate': '§eNot full: put an Air Defence Ammo Crate within %2$s blocks and load it with %1$s to rearm.',
        'message.heliosphere.sam_rearming': '§aRearming from the ammo crate (%s left in it)',
    })


def part_icons():
    """Icons of the four vehicle assemblies (16x16)."""
    def icon(rid, draw):
        img = np.zeros((16, 16, 4), float)
        def px(x, y, c, a=255):
            if 0 <= x < 16 and 0 <= y < 16: img[y, x] = (*[max(0, min(255, v)) for v in c], a)
        draw(px)
        Image.fromarray(img.astype(np.uint8), 'RGBA').save(f'{A}/textures/item/{rid}.png')
        write(f'{A}/models/item/{rid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{rid}'}})
        write(f'{A}/items/{rid}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{rid}'}})

    def chassis(px):                                     # tracked hull from the side
        for x in range(1, 15):
            for y in range(6, 10): px(x, y, (88, 98, 62) if y > 6 else (112, 122, 80))
        for x in range(3, 11): px(x, 5, (100, 110, 70))
        for x in range(0, 16):
            px(x, 11, (40, 40, 38)); px(x, 13, (40, 40, 38))
        px(0, 12, (40, 40, 38)); px(15, 12, (40, 40, 38))
        for x in range(1, 15): px(x, 12, (70, 70, 66) if x % 3 else (120, 120, 112))
        px(13, 7, (30, 30, 30)); px(14, 7, (30, 30, 30))

    def antenna(px):                                     # a tilted phased-array face on a mast
        for y in range(2, 12):
            for x in range(3 + (12 - y) // 4, 13 - (12 - y) // 4):
                c = (70, 78, 70) if (x + y) % 2 else (96, 106, 92)
                px(x, y, c)
        for y in range(2, 12, 3):
            for x in range(4, 12): px(x, y, (150, 160, 140))
        for y in range(12, 15): px(7, y, (60, 60, 58)); px(8, y, (90, 90, 86))
        for x in range(5, 11): px(x, 15, (60, 60, 58))

    def computer(px):                                    # a rugged box with a screen and LEDs
        for x in range(2, 14):
            for y in range(3, 13): px(x, y, (70, 76, 64))
        for x in range(2, 14): px(x, 3, (100, 108, 90)); px(x, 12, (44, 48, 40))
        for x in range(4, 12):
            for y in range(5, 9): px(x, y, (30, 60, 40) if y % 2 else (60, 160, 90))
        for x in (4, 6, 8): px(x, 10, (220, 60, 40) if x == 4 else (240, 200, 60))
        px(11, 10, (120, 200, 255))

    def rack(px):                                        # four launch tubes on a frame
        for i, y0 in enumerate((3, 6)):
            for x in range(2, 15):
                for y in range(y0, y0 + 3):
                    c = (90, 100, 64) if y == y0 + 1 else (70, 78, 50)
                    px(x - i, y, c)
            px(14 - i, y0 + 1, (200, 40, 30))
        for x in range(3, 13, 3):
            for y in range(9, 14): px(x, y, (60, 60, 58))
        for x in range(1, 15): px(x, 14, (50, 50, 48))

    icon('sam_chassis', chassis); icon('phased_array_antenna', antenna); icon('fire_control_computer', computer); icon('launcher_module', rack)
    merge('zh_cn', {'item.heliosphere.sam_chassis': '防空车辆底盘', 'item.heliosphere.phased_array_antenna': '相控阵雷达天线',
                    'item.heliosphere.fire_control_computer': '防空火控计算机', 'item.heliosphere.launcher_module': '导弹发射架'})
    merge('zh_tw', {'item.heliosphere.sam_chassis': '防空車輛底盤', 'item.heliosphere.phased_array_antenna': '相控陣雷達天線',
                    'item.heliosphere.fire_control_computer': '防空火控電腦', 'item.heliosphere.launcher_module': '導彈發射架'})
    merge('en_us', {'item.heliosphere.sam_chassis': 'Air Defence Vehicle Chassis', 'item.heliosphere.phased_array_antenna': 'Phased-Array Radar Antenna',
                    'item.heliosphere.fire_control_computer': 'Air Defence Fire-Control Computer', 'item.heliosphere.launcher_module': 'Missile Launcher Rack'})


def wrench_and_drops():
    img = np.zeros((16, 16, 4), float)
    def px(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16: img[y, x] = (*c, 255)
    steel, dark, grip = (170, 176, 180), (96, 100, 104), (200, 150, 40)
    for i in range(3, 12):                                   # the handle, diagonal
        px(i, 15 - i, steel); px(i + 1, 15 - i, dark)
    for i in range(1, 5): px(i, 15 - i + 1, grip); px(i + 1, 15 - i + 1, (150, 110, 30))
    for (x, y) in ((11, 2), (12, 1), (13, 1), (14, 2), (14, 3), (13, 4), (12, 3), (11, 4), (10, 4), (12, 5), (13, 5)):
        px(x, y, steel)
    px(12, 2, (0, 0, 0)); px(13, 2, (0, 0, 0)); px(13, 3, (0, 0, 0))
    img[2, 12] = img[2, 13] = img[3, 13] = (0, 0, 0, 0)        # the open jaw
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(f'{A}/textures/item/sam_wrench.png')
    write(f'{A}/models/item/sam_wrench.json', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'heliosphere:item/sam_wrench'}})
    write(f'{A}/items/sam_wrench.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:item/sam_wrench'}})
    for v in ('buk_telar', 'buk_tads', 'samp_tel', 'samp_tads', 'iris_tel', 'iris_tads'):
        write(f'{D}/loot_table/blocks/{v}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{v}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    merge('zh_cn', {'item.heliosphere.sam_wrench': '防空车回收扳手', 'item.heliosphere.sam_wrench.hint': '右键防空车、防空电池组或弹药箱：连同弹药、电量一起收回背包，方便换地方放置',
                    'message.heliosphere.sam_packed': '已回收 %s（导弹 %s 枚，电量 %s）', 'message.heliosphere.sam_packed_simple': '已回收 %s'})
    merge('zh_tw', {'item.heliosphere.sam_wrench': '防空車回收扳手', 'item.heliosphere.sam_wrench.hint': '右鍵防空車、防空電池組或彈藥箱：連同彈藥、電量一起收回背包，方便換地方放置',
                    'message.heliosphere.sam_packed': '已回收 %s（導彈 %s 枚，電量 %s）', 'message.heliosphere.sam_packed_simple': '已回收 %s'})
    merge('en_us', {'item.heliosphere.sam_wrench': 'Air Defence Recovery Wrench', 'item.heliosphere.sam_wrench.hint': 'Right-click a vehicle, battery pack or ammo crate to pack it up with its missiles, charge or rounds',
                    'message.heliosphere.sam_packed': 'Packed up %s (%s missiles, %s FE)', 'message.heliosphere.sam_packed_simple': 'Packed up %s'})


def operation_lang():
    merge('zh_cn', {
        'message.heliosphere.sam_taken': '这辆车已经有人在操作了', 'message.heliosphere.sam_mode_manual': '手动', 'message.heliosphere.sam_mode_auto': '自动',
        'message.heliosphere.sam_boarded': '已登上 %s（%s 模式）· R 切换目标 · F 发射 · W/A/S/D 开动 · Shift 下车',
        'message.heliosphere.sam_fire_auto': '自动模式下由车辆自行开火：右键车辆打开面板切换到手动', 'message.heliosphere.sam_fire_empty': '没有导弹了：去弹药箱补弹',
        'message.heliosphere.sam_fire_none': '没有选中目标：按 R 选择', 'message.heliosphere.sam_driving': '行进中：武器与雷达收起，停稳 2 秒自动展开',
        'screen.heliosphere.sam_auto': '自动模式', 'screen.heliosphere.sam_manual': '手动模式', 'screen.heliosphere.sam_board': '上车操控',
        'screen.heliosphere.sam_mode': '当前：%s', 'screen.heliosphere.sam_ammo': '导弹 %s/%s · 附近弹药箱 %s 枚', 'screen.heliosphere.sam_power': '电量 %s/%s',
        'screen.heliosphere.sam_help': '空手左键车辆也能直接上车 · 手动模式下 R 切换目标、F 发射',
        'hud.heliosphere.sam_driving': '行进中（武器收起）', 'hud.heliosphere.sam_tracks': '雷达跟踪 %s 个目标',
        'hud.heliosphere.sam_state': '%s · 导弹 %s/%s · 目标 %s', 'hud.heliosphere.sam_keys': 'R 切换目标 · F 发射（山毛榉手动：炮塔跟随视角）· WASD 开动 · Shift 下车',
        'hud.heliosphere.sam_keys_drive': 'W/S 前进后退 · A/D 转向 · 停稳 2 秒展开 · Shift 下车',
        'key.heliosphere.sam_next': '防空：切换目标', 'key.heliosphere.sam_fire': '防空：发射', 'key.category.heliosphere.air_defence': '日球层 · 防空车',
        'entity.heliosphere.sam_seat': '防空车舱口', 'entity.heliosphere.sam_drive': '行进中的防空车',
        'screen.heliosphere.sam_iff_toggle': '敌我识别开/关', 'screen.heliosphere.sam_iff_on': '敌我识别：开（只打敌对目标）', 'screen.heliosphere.sam_iff_off': '敌我识别：关（可以锁定空中的玩家）',
        'hud.heliosphere.sam_iff_on': 'IFF 开', 'hud.heliosphere.sam_iff_off': 'IFF 关'})
    merge('zh_tw', {
        'message.heliosphere.sam_taken': '這輛車已經有人在操作了', 'message.heliosphere.sam_mode_manual': '手動', 'message.heliosphere.sam_mode_auto': '自動',
        'message.heliosphere.sam_boarded': '已登上 %s（%s 模式）· R 切換目標 · F 發射 · W/A/S/D 開動 · Shift 下車',
        'message.heliosphere.sam_fire_auto': '自動模式下由車輛自行開火：右鍵車輛打開面板切換到手動', 'message.heliosphere.sam_fire_empty': '沒有導彈了：去彈藥箱補彈',
        'message.heliosphere.sam_fire_none': '沒有選中目標：按 R 選擇', 'message.heliosphere.sam_driving': '行進中：武器與雷達收起，停穩 2 秒自動展開',
        'screen.heliosphere.sam_auto': '自動模式', 'screen.heliosphere.sam_manual': '手動模式', 'screen.heliosphere.sam_board': '上車操控',
        'screen.heliosphere.sam_mode': '目前：%s', 'screen.heliosphere.sam_ammo': '導彈 %s/%s · 附近彈藥箱 %s 枚', 'screen.heliosphere.sam_power': '電量 %s/%s',
        'screen.heliosphere.sam_help': '空手左鍵車輛也能直接上車 · 手動模式下 R 切換目標、F 發射',
        'hud.heliosphere.sam_driving': '行進中（武器收起）', 'hud.heliosphere.sam_tracks': '雷達追蹤 %s 個目標',
        'hud.heliosphere.sam_state': '%s · 導彈 %s/%s · 目標 %s', 'hud.heliosphere.sam_keys': 'R 切換目標 · F 發射 · WASD 開動 · Shift 下車',
        'hud.heliosphere.sam_keys_drive': 'W/S 前進後退 · A/D 轉向 · 停穩 2 秒展開 · Shift 下車',
        'key.heliosphere.sam_next': '防空：切換目標', 'key.heliosphere.sam_fire': '防空：發射', 'key.category.heliosphere.air_defence': '日球層 · 防空車',
        'entity.heliosphere.sam_seat': '防空車艙口', 'entity.heliosphere.sam_drive': '行進中的防空車',
        'screen.heliosphere.sam_iff_toggle': '敵我識別開/關', 'screen.heliosphere.sam_iff_on': '敵我識別：開（只打敵對目標）', 'screen.heliosphere.sam_iff_off': '敵我識別：關（可以鎖定空中的玩家）',
        'hud.heliosphere.sam_iff_on': 'IFF 開', 'hud.heliosphere.sam_iff_off': 'IFF 關'})
    merge('en_us', {
        'message.heliosphere.sam_taken': 'Someone is already operating this vehicle', 'message.heliosphere.sam_mode_manual': 'manual', 'message.heliosphere.sam_mode_auto': 'automatic',
        'message.heliosphere.sam_boarded': 'Aboard %s (%s) · R next target · F fire · W/A/S/D drive · Shift leave',
        'message.heliosphere.sam_fire_auto': 'In automatic mode the vehicle fires by itself: right-click it to switch to manual', 'message.heliosphere.sam_fire_empty': 'No missiles left: rearm at an ammo crate',
        'message.heliosphere.sam_fire_none': 'No target selected: press R', 'message.heliosphere.sam_driving': 'On the move: weapons and radar stowed; stop for 2 s to deploy',
        'screen.heliosphere.sam_auto': 'Automatic', 'screen.heliosphere.sam_manual': 'Manual', 'screen.heliosphere.sam_board': 'Take the hatch',
        'screen.heliosphere.sam_mode': 'Mode: %s', 'screen.heliosphere.sam_ammo': 'Missiles %s/%s · nearby crate %s', 'screen.heliosphere.sam_power': 'Power %s/%s',
        'screen.heliosphere.sam_help': 'Left-click a vehicle empty-handed to board · manual: R next target, F fire',
        'hud.heliosphere.sam_driving': 'On the move (weapons stowed)', 'hud.heliosphere.sam_tracks': 'Radar tracking %s',
        'hud.heliosphere.sam_state': '%s · missiles %s/%s · targets %s', 'hud.heliosphere.sam_keys': 'R next · F fire · WASD drive · Shift leave',
        'hud.heliosphere.sam_keys_drive': 'W/S drive · A/D steer · stop 2 s to deploy · Shift leave',
        'key.heliosphere.sam_next': 'Air defence: next target', 'key.heliosphere.sam_fire': 'Air defence: fire', 'key.category.heliosphere.air_defence': 'Heliosphere · Air defence',
        'entity.heliosphere.sam_seat': 'Vehicle hatch', 'entity.heliosphere.sam_drive': 'Air defence vehicle (moving)',
        'screen.heliosphere.sam_iff_toggle': 'IFF on/off', 'screen.heliosphere.sam_iff_on': 'IFF on: hostile targets only', 'screen.heliosphere.sam_iff_off': 'IFF off: players in the air can be locked',
        'hud.heliosphere.sam_iff_on': 'IFF on', 'hud.heliosphere.sam_iff_off': 'IFF off'})


if __name__ == '__main__':
    crate_textures(); crate_model()
    for rid, spec in ROUNDS.items(): round_icon(rid, *spec)
    lang()
    part_icons()
    wrench_and_drops()
    operation_lang()
    print('sam ammo assets written')
