#!/usr/bin/env python3
"""Habitat field (overlay 05-industry): textures of the emitter (off/on) and of the force-field panel, block states,
models, items, loot tables, language entries and the scroll's item pages and tip. Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/05-industry/src/main/resources/assets/heliosphere'
D = 'jars/05-industry/src/main/resources/data/heliosphere'
G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'


def load(p, d): return json.load(open(p, encoding='utf-8')) if os.path.exists(p) else d


def write(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f: json.dump(o, f, ensure_ascii=False, indent=2); f.write('\n')


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(path)


def panel():
    """A pale blue force field with a hexagon grid and a bright frame."""
    img = np.zeros((16, 16, 4), np.int32)
    img[:, :] = (120, 200, 255, 38)
    for y in range(16):
        for x in range(16):
            hx = (x + (y // 4 % 2) * 2) % 4
            if y % 4 == 0 or hx == 0: img[y, x] = (170, 230, 255, 105)
    img[0, :] = img[15, :] = (200, 245, 255, 200)
    img[:, 0] = img[:, 15] = (200, 245, 255, 200)
    save(img, f'{A}/textures/block/habitat_field_panel.png')


def emitter(on):
    rng = np.random.default_rng(5)
    side = np.zeros((16, 16, 4), np.int32); side[:, :] = (92, 100, 112, 255)
    side[:, :, :3] += rng.integers(-6, 7, (16, 16, 1))
    side[0, :, :3] = side[15, :, :3] = side[:, 0, :3] = side[:, 15, :3] = (52, 58, 66)
    for x in range(3, 13): side[12, x, :3] = (232, 180, 32) if x % 2 else (40, 40, 44)         # hazard band
    glow = (120, 220, 255) if on else (40, 70, 90)
    for y in range(3, 10):                                                                       # the vent window
        for x in range(4, 12): side[y, x, :3] = glow if (x + y) % 3 else tuple(int(c * 0.7) for c in glow)
    top = np.zeros((16, 16, 4), np.int32); top[:, :] = (80, 88, 98, 255)
    yy, xx = np.mgrid[0:16, 0:16]; r = np.hypot(xx - 7.5, yy - 7.5)
    top[(r > 3.5) & (r < 6.2), :3] = glow                                                         # the field ring
    top[r <= 2.2, :3] = (230, 250, 255) if on else (70, 90, 100)
    top[0, :, :3] = top[15, :, :3] = top[:, 0, :3] = top[:, 15, :3] = (52, 58, 66)
    sfx = '_on' if on else ''
    save(side, f'{A}/textures/block/habitat_field_emitter_side{sfx}.png')
    save(top, f'{A}/textures/block/habitat_field_emitter_top{sfx}.png')


LANG = {
    'block.heliosphere.habitat_field_emitter': ('栖息地力场发生器', 'Habitat Field Emitter', '棲息地力場發生器'),
    'block.heliosphere.habitat_field_panel': ('力场防护板', 'Force-Field Panel', '力場防護板'),
    'message.heliosphere.habitat_on': ('力场运行中：封闭空间 %s 格，耗电 %s FE/t，电量 %s / %s FE。内部空气可呼吸、恒温 20°C、地球重力。',
                                       'Field running: %s blocks sealed, %s FE/t, %s / %s FE. Inside: breathable air, 20°C, Earth gravity.',
                                       '力場運行中：封閉空間 %s 格，耗電 %s FE/t，電量 %s / %s FE。內部空氣可呼吸、恆溫 20°C、地球重力。'),
    'message.heliosphere.habitat_no_power': ('空间已封闭（%s 格），但电力不足：需要 %s FE/t（电量 %s / %s FE）。用电缆接上发电机或电池组。',
                                             'Space sealed (%s blocks) but not enough power: it needs %s FE/t (%s / %s FE). Connect a generator or battery by cable.',
                                             '空間已封閉（%s 格），但電力不足：需要 %s FE/t（電量 %s / %s FE）。用電纜接上發電機或電池組。'),
    'message.heliosphere.habitat_leak': ('空间没有封闭：在 %s, %s, %s 附近漏气（或超出 40 格 / 32768 格的上限）。用力场防护板或任意实心方块把缺口补上，门要关好。',
                                         'The space is not sealed: it leaks near %s, %s, %s (or is larger than 40 blocks / 32768 blocks). Close the gap with force-field panels or any solid block, and shut the doors.',
                                         '空間沒有封閉：在 %s, %s, %s 附近漏氣（或超出 40 格 / 32768 格的上限）。用力場防護板或任意實心方塊把缺口補上，門要關好。'),
    'message.heliosphere.habitat_nothing': ('发生器四周没有可以充气的空间', 'There is no open space around the emitter to fill', '發生器四周沒有可以充氣的空間'),
}
PAGE_E = ('栖息地力场发生器：在任何星球上造一片「地球环境」。用墙把一块空间整个围起来（顶和底也要封住），把发生器放在里面并通电：'
          '发生器每 2 秒检测一次，只要空间是封闭的（距发生器 40 格以内、最多 32768 格），里面的空气就可以呼吸、温度恒定 20°C、重力变成地球重力（1g），'
          '不穿宇航服也能活动，跳跃和摔落都和在地球上一样。墙可以用「力场防护板」（透明发光），也可以用任何实心方块（石头、玻璃、金属块……）；关着的门算墙，开着的门会漏气。'
          '漏气时右键发生器会告诉你漏在哪里。耗电 30 FE/t + 每 400 格空间 1 FE/t，用电缆接发电机/电池组；内部电池 50 万 FE；创造模式放置的不耗电。'
          '内部能看到淡淡的光点，发生器亮起表示正在运行。')
PAGE_P = '力场防护板：透明、发光的墙板，用来围出栖息地力场的空间（任何实心方块都能当墙，这种板只是看得见外面）。总装台：玻璃×8 + 钢板 + 铜线×2 → 8 块。'
TIP = ('§b星球栖息地：§r在外星球上用「力场防护板」或任何实心方块围出一个封闭空间，里面放「栖息地力场发生器」并接上电源，'
       '整个空间就会变成可呼吸、20°C、地球重力的环境，可以脱下宇航服建造基地。门要关好，漏气时右键发生器查看漏点。')


def main():
    panel(); emitter(False); emitter(True)
    for on in (False, True):
        sfx = '_on' if on else ''
        write(f'{A}/models/block/habitat_field_emitter{sfx}.json', {'parent': 'minecraft:block/cube_column',
              'textures': {'end': f'heliosphere:block/habitat_field_emitter_top{sfx}', 'side': f'heliosphere:block/habitat_field_emitter_side{sfx}'}})
    write(f'{A}/blockstates/habitat_field_emitter.json', {'variants': {'active=false': {'model': 'heliosphere:block/habitat_field_emitter'},
                                                                       'active=true': {'model': 'heliosphere:block/habitat_field_emitter_on'}}})
    write(f'{A}/models/block/habitat_field_panel.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'heliosphere:block/habitat_field_panel'}})
    write(f'{A}/blockstates/habitat_field_panel.json', {'variants': {'': {'model': 'heliosphere:block/habitat_field_panel'}}})
    for b in ('habitat_field_emitter', 'habitat_field_panel'):
        write(f'{A}/items/{b}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{b}'}})
        write(f'{D}/loot_table/blocks/{b}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
              'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{b}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for i, code in enumerate(('zh_cn', 'en_us', 'zh_tw')):
        p = f'{A}/lang/{code}.json'; d = load(p, {})
        for k, v in LANG.items(): d[k] = v[i]
        write(p, d)
    p = f'{G}/purposes.json'; d = load(p, {})
    d['heliosphere:habitat_field_emitter'] = PAGE_E; d['heliosphere:habitat_field_panel'] = PAGE_P
    write(p, d)
    p = f'{G}/chapters.json'; ch = load(p, None)
    if ch:
        for idx in (0, 5):                                              # the first chapter and «月面开拓»
            tips = ch[idx]['tips']; tips[:] = [t for t in tips if not t.startswith('§b星球栖息地')]; tips.append(TIP)
        write(p, ch)
    print('habitat assets written')


if __name__ == '__main__':
    main()
