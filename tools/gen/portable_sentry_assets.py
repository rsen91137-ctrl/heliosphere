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
    'item.heliosphere.portable_sentry': ('便携式肩载哨戒炮', 'Portable Shoulder Sentry', '便攜式肩載哨戒炮'),
    'item.heliosphere.portable_sentry.charge': ('电量 %s / %s FE', 'Charge %s / %s FE', '電量 %s / %s FE'),
    'item.heliosphere.portable_sentry.hint': ('挂在胸甲上使用：在物品栏里拿起它，点到任意胸甲（或其他胸部装备）上即可挂载；空手右键那件胸甲取下。穿着时自动射击 %s 格内看得见的敌对生物和机械（每发 %s 伤害）。站在发电机/电池组等电源方块旁边会充电，白天露天靠太阳能板慢充。',
                                              'Wear it on a chestplate: in your inventory pick it up and click it onto any chestplate (or other chest armour); right-click that armour with an empty cursor to take it off. Worn, it shoots hostile creatures and machines it can see within %s blocks (%s damage a shot). Charges while you stand next to a power block (generators, battery banks) and slowly from its solar panel by day.',
                                              '掛在胸甲上使用：在物品欄裡拿起它，點到任意胸甲（或其他胸部裝備）上即可掛載；空手右鍵那件胸甲取下。穿著時自動射擊 %s 格內看得見的敵對生物和機械（每發 %s 傷害）。站在發電機/電池組等電源方塊旁邊會充電，白天露天靠太陽能板慢充。'),
    'item.heliosphere.shoulder_sentry.fitted': ('已挂载肩载哨戒炮 · 电量 %s/%s FE（空手右键取下）', 'Shoulder sentry fitted · %s/%s FE (right-click with an empty cursor to take it off)',
                                                '已掛載肩載哨戒炮 · 電量 %s/%s FE（空手右鍵取下）'),
    'message.heliosphere.shoulder_sentry_charging': ('肩载哨戒炮充电中：%s / %s FE', 'Shoulder sentry charging: %s / %s FE', '肩載哨戒炮充電中：%s / %s FE'),
    'item.heliosphere.portable_sentry.stats': ('等级 Mk %s · 每发 %s 伤害 · 射程 %s 格 · 每 %s 秒一发', 'Mk %s · %s damage a shot · %s blocks · a shot every %s s', '等級 Mk %s · 每發 %s 傷害 · 射程 %s 格 · 每 %s 秒一發'),
    'message.heliosphere.shoulder_sentry_upgraded': ('肩载哨戒炮已升级到 Mk %s', 'Shoulder sentry upgraded to Mk %s', '肩載哨戒炮已升級到 Mk %s'),
    'message.heliosphere.shoulder_sentry_has_mk': ('这门哨戒炮已经是 Mk %s 了，需要更高级的升级套件', 'This sentry is already Mk %s: it needs a higher kit', '這門哨戒炮已經是 Mk %s 了，需要更高級的升級套件'),
    'message.heliosphere.shoulder_sentry_occupied': ('这件盔甲上已经挂着一门哨戒炮了', 'This armour already carries a sentry', '這件盔甲上已經掛著一門哨戒炮了'),
}
PAGE = ('便携式肩载哨戒炮：挂在胸甲上的小型自动炮塔。在物品栏里拿起它点到任意胸甲（或其他胸部装备）上即可挂载，空手右键那件胸甲取下，电量随炮走。'
        '穿着这件胸甲时，肩头的小炮会自动射击 16 格内看得见的敌对生物与机械：每发 6 点伤害、约每 0.7 秒一发、每发耗电 25 FE，内置 8000 FE 电池（约 320 发，出厂满电）。'
        '站在发电机、电池组等能输出电力的方块旁边（一格以内）就会充电（每 0.5 秒最多 400 FE），白天露天还能靠炮上的太阳能板慢充；创造模式不耗电。'
        '可用「防御塔升级套件」升级：在物品栏里把套件点到哨戒炮上（或点到挂着哨戒炮的胸甲上），直接升到套件的等级（Mk Ⅰ～Ⅴ，可以跳级）：'
        'Mk Ⅰ 7.5 伤害/18 格/10000 FE；Mk Ⅱ 9/20 格/12000 FE；Mk Ⅲ 11/22 格/15000 FE（双管）；Mk Ⅳ 13/24 格/18000 FE；Mk Ⅴ 16/26 格/24000 FE，射速逐级提高到每 0.4 秒一发。'
        '基础型威力适中：适合野外赶路、挖矿时清掉零散敌人，升满后能独当一面，但仍不能代替基地防御塔。')


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
