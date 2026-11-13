#!/usr/bin/env python3
"""Resources of the air-defence systems and guided missiles (overlay 06-weapons): names, messages, block and item
models and inventory icons of the six vehicles. Language entries are merged into the overlay's lang files."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'


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


VEH = {
    'buk_telar': ('9K317M「山毛榉-M3」发射车（TELAR）', '9K317M「山毛櫸-M3」發射車（TELAR）', '9K317M Buk-M3 TELAR'),
    'buk_tads': ('9S18M3「山毛榉-M3」雷达车', '9S18M3「山毛櫸-M3」雷達車', '9S18M3 Buk-M3 Search Radar'),
    'samp_tel': ('SAMP/T「紫菀-30」发射车', 'SAMP/T「紫菀-30」發射車', 'SAMP/T Aster 30 Launcher'),
    'samp_tads': ('SAMP/T「阿拉贝尔」雷达车', 'SAMP/T「阿拉貝爾」雷達車', 'SAMP/T Arabel Radar'),
    'iris_tel': ('IRIS-T SLM 发射车', 'IRIS-T SLM 發射車', 'IRIS-T SLM Launcher'),
    'iris_tads': ('IRIS-T SLM 雷达车（TRML-4D）', 'IRIS-T SLM 雷達車（TRML-4D）', 'IRIS-T SLM Radar (TRML-4D)'),
}
ZH = {f'block.heliosphere.{k}': v[0] for k, v in VEH.items()}
TW = {f'block.heliosphere.{k}': v[1] for k, v in VEH.items()}
EN = {f'block.heliosphere.{k}': v[2] for k, v in VEH.items()}
ZH.update({
    'block.heliosphere.sam_hull': '防空车辆车体', 'entity.heliosphere.guided_missile': '制导导弹',
    'message.heliosphere.missile_warning': '§c⚠ 导弹来袭！距离 %s 格 —— 躲到掩体后面或急转规避',
    'message.heliosphere.sam_no_room': '空间不足：车辆需要 %s×%s 格的空地',
    'message.heliosphere.sam_radar_status': '%s · %s · 跟踪 %s 个目标 · 探测距离 %s 格 · 电量 %s/%s',
    'message.heliosphere.sam_launcher_status': '%s · 弹药 %s/%s · %s · 射程 %s 格 · 电量 %s/%s',
    'message.heliosphere.sam_on': '§a工作中', 'message.heliosphere.sam_off': '§c缺电停机',
    'message.heliosphere.sam_linked': '§a已与雷达车联网', 'message.heliosphere.sam_alone': '§e未联网（仅自身传感器）',
    'message.heliosphere.lock_hold': '已锁定 %s · %s 格 · 导引方式：%s',
    'message.heliosphere.guidance_pn': '比例导引（高速目标）', 'message.heliosphere.guidance_pursuit': '追踪法（低速目标）',
})
TW.update({
    'block.heliosphere.sam_hull': '防空車輛車體', 'entity.heliosphere.guided_missile': '制導導彈',
    'message.heliosphere.missile_warning': '§c⚠ 導彈來襲！距離 %s 格 —— 躲到掩體後面或急轉規避',
    'message.heliosphere.sam_no_room': '空間不足：車輛需要 %s×%s 格的空地',
    'message.heliosphere.sam_radar_status': '%s · %s · 追蹤 %s 個目標 · 偵測距離 %s 格 · 電量 %s/%s',
    'message.heliosphere.sam_launcher_status': '%s · 彈藥 %s/%s · %s · 射程 %s 格 · 電量 %s/%s',
    'message.heliosphere.sam_on': '§a運作中', 'message.heliosphere.sam_off': '§c缺電停機',
    'message.heliosphere.sam_linked': '§a已與雷達車聯網', 'message.heliosphere.sam_alone': '§e未聯網（僅自身感測器）',
    'message.heliosphere.lock_hold': '已鎖定 %s · %s 格 · 導引方式：%s',
    'message.heliosphere.guidance_pn': '比例導引（高速目標）', 'message.heliosphere.guidance_pursuit': '追蹤法（低速目標）',
})
EN.update({
    'block.heliosphere.sam_hull': 'Air Defence Vehicle Hull', 'entity.heliosphere.guided_missile': 'Guided Missile',
    'message.heliosphere.missile_warning': '§c⚠ Missile inbound! %s blocks — get behind cover or break hard',
    'message.heliosphere.sam_no_room': 'Not enough room: the vehicle needs a clear %s×%s area',
    'message.heliosphere.sam_radar_status': '%s · %s · %s tracks · detection %s blocks · power %s/%s',
    'message.heliosphere.sam_launcher_status': '%s · missiles %s/%s · %s · range %s blocks · power %s/%s',
    'message.heliosphere.sam_on': '§aactive', 'message.heliosphere.sam_off': '§cno power',
    'message.heliosphere.sam_linked': '§alinked to radar', 'message.heliosphere.sam_alone': '§enot linked (own sensor only)',
    'message.heliosphere.lock_hold': 'Locked %s · %s blocks · guidance: %s',
    'message.heliosphere.guidance_pn': 'proportional navigation (fast target)', 'message.heliosphere.guidance_pursuit': 'pure pursuit (slow target)',
})
for code, d in (('zh_cn', ZH), ('zh_tw', TW), ('en_us', EN)):
    merge(code, d)

# blocks: drawn by the renderer; the model only gives the breaking particles
for k in list(VEH) + ['sam_hull']:
    write(f'{A}/blockstates/{k}.json', {'variants': {'': {'model': 'heliosphere:block/sam_vehicle'}}} if k == 'sam_hull'
          else {'variants': {f'facing={f}': {'model': 'heliosphere:block/sam_vehicle'} for f in ['north', 'south', 'east', 'west']}})
write(f'{A}/models/block/sam_vehicle.json', {'textures': {'particle': 'heliosphere:item/sam_paint'}})


# inventory icons: side views drawn in pixels
def icon(kind):
    img = np.zeros((32, 32, 4), np.uint8)
    paint = {'buk': (86, 96, 58), 'samp': (102, 103, 74), 'iris': (62, 74, 53)}[kind.split('_')[0]]
    dark = tuple(int(c * 0.7) for c in paint)

    def rect(x0, y0, x1, y1, c):
        img[y0:y1, x0:x1, :3] = c; img[y0:y1, x0:x1, 3] = 255
    if kind.startswith('buk'):
        rect(2, 18, 30, 25, paint); rect(3, 24, 29, 28, (30, 30, 32))
        for x in range(5, 29, 4): rect(x, 25, x + 3, 28, (70, 72, 76))
        if kind == 'buk_telar':
            rect(9, 14, 22, 18, dark)
            for i in range(6):
                y = 12 - i
                rect(8 + i * 3, y, 14 + i * 3, y + 3, paint)
            rect(22, 3, 27, 8, (176, 42, 32)); rect(23, 13, 27, 18, (168, 170, 154))
        else:
            rect(4, 14, 14, 18, paint); rect(15, 3, 18, 18, dark); rect(12, 2, 24, 14, (168, 170, 154))
    else:
        rect(2, 18, 30, 22, dark); rect(23, 11, 30, 22, paint); rect(25, 12, 29, 16, (40, 52, 60))
        for x in (4, 9, 18, 23): rect(x, 22, x + 4, 27, (30, 30, 32))
        if kind.endswith('tel'):
            rect(4, 6, 12, 18, paint)
            for i in range(4): rect(5 + i * 2, 5, 6 + i * 2, 6, (176, 42, 32) if kind == 'samp_tel' else (200, 200, 184))
            rect(20, 2, 21, 18, (110, 114, 118))
        else:
            rect(3, 11, 22, 18, paint); rect(10, 4, 18, 11, (40, 44, 40) if kind == 'samp_tads' else dark)
    # outline
    m = img[..., 3] > 0
    ring = np.zeros_like(m)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(np.roll(m, dy, 0), dx, 1)
    ring &= ~m
    img[ring] = (18, 20, 18, 255)
    return img


os.makedirs(f'{A}/textures/item', exist_ok=True)
for k in VEH:
    Image.fromarray(icon(k), 'RGBA').resize((32, 32), Image.NEAREST).save(f'{A}/textures/item/{k}.png')
    write(f'{A}/models/item/{k}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{k}'}})
    write(f'{A}/items/{k}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{k}'}})
p = np.zeros((16, 16, 4), np.uint8); p[..., :3] = (86, 96, 58); p[..., 3] = 255
Image.fromarray(p, 'RGBA').save(f'{A}/textures/item/sam_paint.png')
print('ok')

# the creative ammo box: an olive ammo can with a gold star
img = np.zeros((16, 16, 4), np.uint8)
def r(x0, y0, x1, y1, c):
    img[y0:y1, x0:x1, :3] = c; img[y0:y1, x0:x1, 3] = 255
r(2, 5, 14, 15, (74, 84, 50)); r(2, 5, 14, 6, (96, 108, 66)); r(2, 14, 14, 15, (52, 60, 36))
r(5, 3, 11, 5, (60, 62, 64)); r(6, 2, 10, 3, (90, 92, 96))          # handle
r(3, 7, 4, 13, (52, 60, 36)); r(12, 7, 13, 13, (52, 60, 36))          # ribs
star = [(7, 8), (8, 8), (6, 9), (7, 9), (8, 9), (9, 9), (7, 10), (8, 10), (6, 11), (9, 11), (7, 7), (8, 7)]
for x, y in star: img[y, x] = (240, 200, 60, 255)
r(5, 12, 11, 13, (200, 170, 70))
Image.fromarray(img, 'RGBA').save(f'{A}/textures/item/creative_ammo_box.png')
write(f'{A}/models/item/creative_ammo_box.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'heliosphere:item/creative_ammo_box'}})
write(f'{A}/items/creative_ammo_box.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:item/creative_ammo_box'}})
merge('zh_cn', {'item.heliosphere.creative_ammo_box': '创造模式弹药盒', 'item.heliosphere.creative_ammo_box.desc': '放在背包里：所有枪械换弹无需弹药与能量电池'})
merge('zh_tw', {'item.heliosphere.creative_ammo_box': '創造模式彈藥盒', 'item.heliosphere.creative_ammo_box.desc': '放在背包裡：所有槍械換彈無需彈藥與能量電池'})
merge('en_us', {'item.heliosphere.creative_ammo_box': 'Creative Ammo Box', 'item.heliosphere.creative_ammo_box.desc': 'Keep it in your inventory: every gun reloads without ammunition or power cells'})
print('ammo box ok')
