#!/usr/bin/env python3
"""The portable sentry (overlay 06-weapons): item icon and model, language entries, the scroll's item page line and tip.
Merges into existing files. Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'
G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'


def load(p, d): return json.load(open(p, encoding='utf-8')) if os.path.exists(p) else d


def write(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f: json.dump(o, f, ensure_ascii=False, indent=2); f.write('\n')


def icon():
    img = np.zeros((16, 16, 4), np.uint8)
    def px(x, y, c): img[y, x] = (*c, 255)
    steel, dark, sand, glass = (140, 144, 150), (46, 49, 54), (184, 158, 106), (42, 58, 90)
    for i in range(6):                                           # tripod legs
        px(7 - i, 10 + i // 1 if 10 + i < 16 else 15, steel) if 10 + i < 16 else None
        px(8 + i, 10 + i, steel) if 10 + i < 16 else None
    for y in range(9, 16): px(7, y, steel)
    for x in range(5, 11):                                       # battery hub
        for y in range(8, 10): px(x, y, dark)
    px(10, 8, (96, 255, 144))
    for x in range(4, 11):                                       # head
        for y in range(4, 8): px(x, y, sand)
    for x in range(4, 11): px(x, 3, glass)                       # solar panel
    for x in range(11, 16): px(x, 5, steel)                      # barrel
    px(15, 5, dark)
    px(9, 5, (96, 224, 255))
    for y in range(3, 8): px(4, y, tuple(int(v * 0.75) for v in sand))
    Image.fromarray(img, 'RGBA').save(f'{A}/textures/item/portable_sentry.png')


LANG = {
    'item.heliosphere.portable_sentry': ('便携式哨戒炮', 'Portable Sentry', '便攜式哨戒炮'),
    'entity.heliosphere.portable_sentry': ('便携式哨戒炮', 'Portable Sentry', '便攜式哨戒炮'),
    'item.heliosphere.portable_sentry.charge': ('电量 %s / %s FE', 'Charge %s / %s FE', '電量 %s / %s FE'),
    'item.heliosphere.portable_sentry.hint': ('右键地面架设：自动射击 %s 格内看得见的敌对生物和机械（每发 %s 伤害）。挨着发电机/电池组等电源方块会自动充电，白天露天靠太阳能板慢充。潜行+右键或直接打它收回，电量保留。',
                                              'Right-click the ground to set it up: it shoots hostile creatures and machines it can see within %s blocks (%s damage a shot). Charges from any power block it stands next to (generators, battery banks) and slowly from its solar panel by day. Sneak + right-click or hit it to pick it up, charge kept.',
                                              '右鍵地面架設：自動射擊 %s 格內看得見的敵對生物和機械（每發 %s 傷害）。挨著發電機/電池組等電源方塊會自動充電，白天露天靠太陽能板慢充。潛行+右鍵或直接打它收回，電量保留。'),
    'message.heliosphere.portable_sentry': ('便携式哨戒炮 · 电量 %s/%s FE · 耐久 %s/%s · 射程 %s 格 · 每发 %s 伤害', 'Portable sentry · %s/%s FE · %s/%s health · %s blocks · %s damage a shot',
                                            '便攜式哨戒炮 · 電量 %s/%s FE · 耐久 %s/%s · 射程 %s 格 · 每發 %s 傷害'),
    'message.heliosphere.portable_sentry_packed': ('已收回便携式哨戒炮（电量 %s/%s FE）', 'Picked up the portable sentry (%s/%s FE)', '已收回便攜式哨戒炮（電量 %s/%s FE）'),
}
PAGE = ('便携式哨戒炮：随身携带的小型自动炮塔。右键地面架设，自动射击 20 格内看得见的敌对生物与机械，每发 7 点伤害、约每 0.6 秒一发、每发耗电 25 FE，'
        '内置 8000 FE 电池（约 320 发，出厂满电）。挨着发电机、电池组等能输出电力的方块就会自动充电（每 0.5 秒最多 400 FE），白天露天还能靠背上的太阳能板慢充。'
        '潜行+右键或自己打它即可收回，电量保留；物品栏里的蓝色条就是电量。耐久 40，被打坏会掉落为物品。威力适中：适合野外过夜、挖矿时守住通道，不能代替基地防御塔。')


def main():
    icon()
    write(f'{A}/models/item/portable_sentry.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'heliosphere:item/portable_sentry'}})
    write(f'{A}/items/portable_sentry.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:item/portable_sentry'}})
    for i, code in enumerate(('zh_cn', 'en_us', 'zh_tw')):
        p = f'{A}/lang/{code}.json'; d = load(p, {})
        for k, v in LANG.items(): d[k] = v[i]
        write(p, d)
    p = f'{G}/purposes.json'; d = load(p, {})
    d['heliosphere:portable_sentry'] = PAGE
    write(p, d)
    print('portable sentry assets written')


if __name__ == '__main__':
    main()
