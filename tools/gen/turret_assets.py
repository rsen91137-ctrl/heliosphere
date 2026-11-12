#!/usr/bin/env python3
"""Item and block resources of the seven turrets and five upgrade kits (jars/06-weapons). The turrets are drawn
in the world by SentryRenderer; their item models here are block-element sketches in the armory textures."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'
TEX = {k: f'heliosphere:block/armory/{k}' for k in
       ['armor', 'steel', 'polymer', 'titanium', 'heat', 'shield', 'tread', 'barrel', 'optic', 'coil', 'amber', 'service', 'electronics', 'carbon', 'hazard', 'vent']}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def el(fr, to, tex):
    return {'from': fr, 'to': to, 'faces': {d: {'texture': f'#{tex}', 'uv': [0, 0, 16, 16]} for d in ['north', 'south', 'east', 'west', 'up', 'down']}}


BASE = [el([1, 0, 1], [15, 4, 15], 'armor'), el([2, 4, 2], [14, 6, 14], 'hazard'), el([4, 6, 4], [12, 10, 12], 'steel')]
HEADS = {
    'laser_turret': [el([4, 10, 3], [12, 15, 12], 'titanium'), el([7, 11, 12], [9, 13, 16], 'barrel'), el([6.5, 10.5, 15.6], [9.5, 13.5, 16], 'optic')],
    'sonic_turret': [el([3, 10, 4], [13, 15, 11], 'shield'), el([2, 9, 11], [14, 16, 12], 'vent'), el([5, 11, 12], [11, 15, 13], 'coil')],
    'autocannon_turret': [el([4, 10, 2], [12, 15, 11], 'carbon'), el([5, 11, 11], [7, 13, 16], 'barrel'), el([9, 11, 11], [11, 13, 16], 'barrel'), el([1, 10, 4], [4, 14, 9], 'hazard')],
    'flak_turret': [el([3, 10, 3], [13, 14, 11], 'armor'), el([4, 12, 11], [6, 14, 16], 'barrel'), el([10, 12, 11], [12, 14, 16], 'barrel')],
    'missile_turret': [el([1, 10, 4], [6, 15, 13], 'polymer'), el([10, 10, 4], [15, 15, 13], 'polymer'), el([6, 11, 6], [10, 14, 10], 'steel'), el([2, 11, 12.9], [5, 14, 13.1], 'heat'), el([11, 11, 12.9], [14, 14, 13.1], 'heat')],
    'rail_turret': [el([5, 10, 1], [11, 14, 9], 'titanium'), el([6.5, 11, 9], [9.5, 13, 16], 'coil'), el([7.5, 13, 3], [8.5, 13.5, 16], 'amber')],
    'tesla_turret': [el([5, 10, 5], [11, 13, 11], 'electronics'), el([6, 13, 6], [10, 14, 10], 'coil'), el([6.5, 14, 6.5], [9.5, 16, 9.5], 'amber')],
}
NAMES = {
    'laser_turret': ('激光防御塔', '激光防禦塔', 'Laser Turret'),
    'sonic_turret': ('声波防御塔', '聲波防禦塔', 'Sonic Turret'),
    'autocannon_turret': ('机炮防御塔', '機砲防禦塔', 'Autocannon Turret'),
    'flak_turret': ('防空高炮塔', '防空高砲塔', 'Flak Turret'),
    'missile_turret': ('导弹防御塔', '導彈防禦塔', 'Missile Turret'),
    'rail_turret': ('电磁轨道塔', '電磁軌道塔', 'Railgun Turret'),
    'tesla_turret': ('特斯拉线圈塔', '特斯拉線圈塔', 'Tesla Coil Turret'),
}
KITS = [('turret_kit_mk1', 'Ⅰ', 0x50A0FF), ('turret_kit_mk2', 'Ⅱ', 0x50E070), ('turret_kit_mk3', 'Ⅲ', 0xE8C030),
        ('turret_kit_mk4', 'Ⅳ', 0xE06030), ('turret_kit_mk5', 'Ⅴ', 0xC050FF)]


def kit_icon(level, rgb):
    img = np.zeros((16, 16, 4), np.uint8)
    img[2:14, 1:15] = (40, 52, 44, 255)                  # board
    img[3:13, 2:14] = (52, 92, 60, 255)
    for x in range(3, 13, 2):
        img[4, x] = (200, 170, 70, 255); img[11, x] = (200, 170, 70, 255)   # traces
    img[6:10, 5:11] = (30, 30, 34, 255)                  # chip
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    for i in range(level):                               # one bar per Mk
        img[6:10, 12 - i * 0 if False else 5 + i] = (r, g, b, 255)
    img[2, 1:15] = img[13, 1:15] = (20, 24, 22, 255)
    return img


def main():
    for tid, head in HEADS.items():
        write(f'{A}/blockstates/{tid}.json', {'variants': {'': {'model': f'heliosphere:block/{tid}'}}})
        write(f'{A}/models/block/{tid}.json', {'textures': {'particle': TEX['steel']}})
        write(f'{A}/models/item/{tid}.json', {'ambientocclusion': False, 'textures': {**TEX, 'particle': TEX['steel']}, 'elements': BASE + head,
                                              'display': {'gui': {'rotation': [25, 215, 0], 'scale': [0.7, 0.7, 0.7]}}})
        write(f'{A}/items/{tid}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{tid}'}})
        write(f'jars/06-weapons/src/main/resources/data/heliosphere/loot_table/blocks/{tid}.json',
              {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{tid}'}],
                                                     'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for i, (kid, roman, rgb) in enumerate(KITS):
        os.makedirs(f'{A}/textures/item', exist_ok=True)
        Image.fromarray(kit_icon(i + 1, rgb), 'RGBA').save(f'{A}/textures/item/{kid}.png')
        write(f'{A}/models/item/{kid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{kid}'}})
        write(f'{A}/items/{kid}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{kid}'}})
    langs = {'zh_cn': {}, 'zh_tw': {}, 'en_us': {}}
    for tid, (zh, tw, en) in NAMES.items():
        langs['zh_cn'][f'block.heliosphere.{tid}'] = zh; langs['zh_tw'][f'block.heliosphere.{tid}'] = tw; langs['en_us'][f'block.heliosphere.{tid}'] = en
    for i, (kid, roman, _) in enumerate(KITS):
        langs['zh_cn'][f'item.heliosphere.{kid}'] = f'防御塔升级套件 Mk {roman}' + ('（远程改装）' if i == 4 else '')
        langs['zh_tw'][f'item.heliosphere.{kid}'] = f'防禦塔升級套件 Mk {roman}' + ('（遠程改裝）' if i == 4 else '')
        langs['en_us'][f'item.heliosphere.{kid}'] = f'Turret Upgrade Kit Mk {roman}' + (' (Long-Range Refit)' if i == 4 else '')
    msgs = {
        'zh_cn': ('%s · Mk %s · 电量 %s/%s EU · 射程 %s 格 · 伤害 %s · 每秒 %s 发 · 每发 %s EU', '已升级到 Mk %s', '这座防御塔已经是 Mk %s 或更高'),
        'zh_tw': ('%s · Mk %s · 電量 %s/%s EU · 射程 %s 格 · 傷害 %s · 每秒 %s 發 · 每發 %s EU', '已升級到 Mk %s', '這座防禦塔已經是 Mk %s 或更高'),
        'en_us': ('%s · Mk %s · %s/%s EU · range %s · damage %s · %s shots/s · %s EU a shot', 'Upgraded to Mk %s', 'This turret is already Mk %s or better'),
    }
    for code, (status, up, has) in msgs.items():
        langs[code]['message.heliosphere.turret_status'] = status
        langs[code]['message.heliosphere.turret_upgraded'] = up
        langs[code]['message.heliosphere.turret_has_mk'] = has
    for code, entries in langs.items():
        p = f'{A}/lang/{code}.json'
        d = json.load(open(p, encoding='utf-8')) if os.path.exists(p) else {}
        d.update(entries)
        write(p, d)


if __name__ == '__main__':
    main()
