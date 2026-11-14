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


def capsule():
    """A glass capsule with steel caps and a green life-support light."""
    img = np.zeros((16, 16, 4), np.int32)
    for y in range(2, 14):
        for x in range(4, 12):
            img[y, x] = (150, 215, 240, 150)
            if x in (4, 11): img[y, x] = (90, 150, 180, 220)
    for x in range(5, 11): img[4, x] = (220, 245, 255, 200)                     # a glint
    for y in (1, 2, 13, 14):
        for x in range(4, 12): img[y, x] = (120, 126, 136, 255)
    for x in range(5, 11): img[1, x] = (160, 166, 176, 255)
    img[13, 10] = (90, 255, 140, 255)
    save(img, f'{A}/textures/item/bio_capsule.png')


LANG = {
    'item.heliosphere.bio_capsule': ('生物收容舱', 'Bio-Containment Capsule', '生物收容艙'),
    'item.heliosphere.bio_capsule.hint': ('右键生物把它收进舱里（保留血量、名字、年龄、项圈等一切），再对地面右键放出。玩家、Boss 和机械不能收容。',
                                          'Right-click a creature to take it in (health, name, age, collar and all), then right-click the ground to let it out. Players, bosses and machines cannot be taken.',
                                          '右鍵生物把牠收進艙裡（保留血量、名字、年齡、項圈等一切），再對地面右鍵放出。玩家、Boss 和機械不能收容。'),
    'item.heliosphere.bio_capsule.holds': ('舱内：%s（%s）', 'Holding: %s (%s)', '艙內：%s（%s）'),
    'item.heliosphere.bio_capsule.release': ('对地面右键放出', 'Right-click the ground to let it out', '對地面右鍵放出'),
    'message.heliosphere.bio_capsule_taken': ('已收容：%s', 'Taken in: %s', '已收容：%s'),
    'message.heliosphere.bio_capsule_released': ('已放出：%s', 'Let out: %s', '已放出：%s'),
    'message.heliosphere.bio_capsule_full': ('收容舱里已经有生物了，先放出来', 'The capsule already holds a creature: let it out first', '收容艙裡已經有生物了，先放出來'),
    'message.heliosphere.bio_capsule_refused': ('这个不能收容（玩家、Boss 和机械除外）', 'That cannot be taken (no players, bosses or machines)', '這個不能收容（玩家、Boss 和機械除外）'),
    'message.heliosphere.bio_capsule_no_air': ('注意：这里没有可呼吸的空气，地球生物会慢慢窒息——把它放进栖息地力场的圈里。',
                                               'Careful: there is no breathable air here and Earth creatures slowly suffocate - let it out inside a habitat field.',
                                               '注意：這裡沒有可呼吸的空氣，地球生物會慢慢窒息——把牠放進棲息地力場的圈裡。'),
    'block.heliosphere.habitat_field_emitter': ('栖息地力场发生器', 'Habitat Field Emitter', '棲息地力場發生器'),
    'block.heliosphere.habitat_field_panel': ('力场防护板', 'Force-Field Panel', '力場防護板'),
    'message.heliosphere.habitat_on': ('力场运行中：圈内 %s 格地面，从发生器下方 %s 格到上方 %s 格，空气可呼吸、恒温 20°C、地球重力。',
                                       'Field running: %s columns inside the ring, from %s blocks below the emitter to %s above: breathable air, 20°C, Earth gravity.',
                                       '力場運行中：圈內 %s 格地面，從發生器下方 %s 格到上方 %s 格，空氣可呼吸、恆溫 20°C、地球重力。'),
    'message.heliosphere.habitat_leak': ('这一圈没有围合：在 %s, %s, %s 附近有缺口（或超出 40 格 / 6400 格的范围）。和发生器同一高度的一圈墙必须连续，门要关好。',
                                         'The ring is not closed: there is a gap near %s, %s, %s (or it is larger than 40 blocks / 6400 columns). The wall at the emitter\'s height must go all the way round, doors shut.',
                                         '這一圈沒有圍合：在 %s, %s, %s 附近有缺口（或超出 40 格 / 6400 格的範圍）。和發生器同一高度的一圈牆必須連續，門要關好。'),
}
PAGE_E = ('栖息地力场发生器：在任何星球上造一片「地球环境」，不耗电。在地面上用「力场防护板」或任何实心方块围一圈（一格高就够，不需要屋顶），把发生器放在圈内地面上：'
          '发生器每 2 秒沿它所在的那一层向四周检查，只要这一圈是闭合的（距发生器 40 格以内、圈内最多 6400 格），圈内从发生器下方 6 格到上方 30 格的整个空间就可以呼吸、恒温 20°C、'
          '重力变成地球重力（1g），不穿宇航服也能活动，跳跃和摔落都和在地球上一样。和发生器同一高度的那一圈必须连续：关着的门算墙，开着的门或缺一块就会失效，右键发生器会告诉你缺口在哪里。'
          '圈内能看到淡淡的光点，发生器亮起表示正在运行。')
PAGE_P = '力场防护板：透明、发光的墙板，在地面上围一圈就能圈出栖息地力场的范围（任何实心方块都能当围墙，这种板只是看得见外面）。总装台：玻璃×8 + 钢板 + 铜线×2 → 8 块。'
PAGE_C = ('生物收容舱：物种移植用。右键生物把它收进舱里（血量、名字、年龄、繁殖状态、项圈、驯服主人都保留），带上火箭，到目的地对地面右键放出；可以反复使用。'
          '玩家、Boss（末影龙、凋灵）和机械敌人不能收容。地球生物（原版的动物、村民等）在没有可呼吸空气的星球上会慢慢窒息（每 2 秒 2 点伤害），要放进栖息地力场的圈里才能活下去、繁衍。'
          '总装台：玻璃×4、钢板×2、电路板×1、压缩氧气罐×1。')
TIP = ('§b星球栖息地：§r在外星球的地面上用「力场防护板」或任何实心方块围一圈（一格高、不用屋顶），把「栖息地力场发生器」放在圈内同一高度，'
       '不用接电，圈内从下方 6 格到上方 30 格就会变成可呼吸、20°C、地球重力的环境，可以脱下宇航服建造基地。缺口处会失效，右键发生器查看缺口位置。')


def main():
    panel(); emitter(False); emitter(True); capsule()
    write(f'{A}/models/item/bio_capsule.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'heliosphere:item/bio_capsule'}})
    write(f'{A}/items/bio_capsule.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:item/bio_capsule'}})
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
    d['heliosphere:habitat_field_emitter'] = PAGE_E; d['heliosphere:habitat_field_panel'] = PAGE_P; d['heliosphere:bio_capsule'] = PAGE_C
    write(p, d)
    p = f'{G}/chapters.json'; ch = load(p, None)
    if ch:
        for idx in (0, 5):                                              # the first chapter and «月面开拓»
            tips = ch[idx]['tips']; tips[:] = [t for t in tips if not t.startswith('§b星球栖息地')]; tips.append(TIP)
        write(p, ch)
    print('habitat assets written')


if __name__ == '__main__':
    main()
