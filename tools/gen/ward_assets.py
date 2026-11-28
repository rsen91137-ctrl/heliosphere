#!/usr/bin/env python3
"""Ward generator and blast-proof blocks (06): textures, models, block states, loot, recipes, language, guide pages.
Run from the repository root."""
import json, os, random
from PIL import Image

R = 'jars/06-weapons/src/main/resources'
A = f'{R}/assets/heliosphere'
D = f'{R}/data/heliosphere'
G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'


def write(p, d):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f: json.dump(d, f, ensure_ascii=False, indent=2); f.write('\n')


def load(p, default):
    try: return json.load(open(p, encoding='utf-8'))
    except FileNotFoundError: return default


def clamp(v): return max(0, min(255, int(v)))


def concrete():
    rnd = random.Random(7)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-10, 10)
            base = 128 + n
            # aggregate specks
            if rnd.random() < 0.08: base -= 28
            if rnd.random() < 0.05: base += 22
            img.putpixel((x, y), (clamp(base), clamp(base + 2), clamp(base + 6), 255))
    # cast panel seams and rebar ties at the corners
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            r, g, b, _ = img.getpixel((x, y)); img.putpixel((x, y), (clamp(r - 30), clamp(g - 30), clamp(b - 28), 255))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img.putpixel((x, y), (70, 62, 58, 255)); img.putpixel((x + 1, y), (96, 84, 76, 255))
    # hazard stripe band in the middle, faint
    for x in range(1, 15):
        c = (168, 140, 60, 255) if (x // 2) % 2 == 0 else (88, 88, 92, 255)
        img.putpixel((x, 7), c); img.putpixel((x, 8), tuple(clamp(v - 12) for v in c[:3]) + (255,))
    return img


def glass():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge: img.putpixel((x, y), (84, 92, 104, 255)); continue
            a = 70
            if x + y in (6, 7, 18, 19): a = 140                      # glare streaks
            img.putpixel((x, y), (170, 205, 225, a))
    for i in range(1, 15):                                            # wire mesh
        for j in (5, 10):
            img.putpixel((j, i), (110, 122, 134, 200)); img.putpixel((i, j), (110, 122, 134, 200))
    return img


def generator(top, on):
    rnd = random.Random(11 if top else 13)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-6, 6)
            img.putpixel((x, y), (clamp(58 + n), clamp(54 + n), clamp(72 + n), 255))
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)): img.putpixel((x, y), (32, 30, 40, 255))
    glow = (210, 120, 255, 255) if on else (90, 70, 110, 255)
    core = (255, 220, 255, 255) if on else (120, 100, 130, 255)
    if top:
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if 4.2 < d < 5.4: img.putpixel((x, y), glow)
                elif d <= 2.2: img.putpixel((x, y), core)
    else:
        for y in range(3, 13):                                       # emitter coil
            c = glow if y % 2 else tuple(clamp(v * 0.7) for v in glow[:3]) + (255,)
            for x in range(5, 11): img.putpixel((x, y), c)
        for x in range(6, 10): img.putpixel((x, 7), core); img.putpixel((x, 8), core)
        for y in (2, 13):
            for x in range(4, 12): img.putpixel((x, y), (140, 140, 150, 255))
    return img


LANG = {
    'block.heliosphere.ward_generator': ('结界发生器', 'Ward Generator', '結界發生器'),
    'block.heliosphere.blast_concrete': ('抗爆混凝土', 'Blast-Proof Concrete', '抗爆混凝土'),
    'block.heliosphere.blast_glass': ('抗爆玻璃', 'Blast-Proof Glass', '抗爆玻璃'),
    'message.heliosphere.ward_on': ('结界运行中：%s×%s 格范围（上下各 24 格）。范围内玩家获得生命恢复 I 和抗性提升 I；机器人减速、每秒 10%% 概率故障 1.5 秒、每秒损失 1.5%% 生命（最多 4 点）。电量 %s FE，耗电 %s FE/t。',
                                    'Ward up over %s×%s blocks (24 up and down). Players inside get Regeneration I and Resistance I; machines are slowed, have a 10%% chance a second of a 1.5-second malfunction and lose 1.5%% of their health a second (4 at most). Charge %s FE, using %s FE/t.',
                                    '結界運行中：%s×%s 格範圍（上下各 24 格）。範圍內玩家獲得生命恢復 I 和抗性提升 I；機器人減速、每秒 10%% 機率故障 1.5 秒、每秒損失 1.5%% 生命（最多 4 點）。電量 %s FE，耗電 %s FE/t。'),
    'message.heliosphere.ward_unpowered': ('结界发生器没有电：用电缆接到电网（运行时耗电 %s FE/t）。当前电量 %s FE。',
                                           'The ward generator has no power: connect it to the grid with a cable (it uses %s FE/t while running). Charge %s FE.',
                                           '結界發生器沒有電：用電纜接到電網（運行時耗電 %s FE/t）。目前電量 %s FE。'),
}
PAGES = {
    'heliosphere:ward_generator': '结界发生器：接电缆供电（运行时 40 FE/t，自带 80000 FE 储能），在周围 64×64 格（上下各 24 格）罩出一片结界，每秒生效一次：'
                                  '范围内的玩家获得生命恢复 I 和抗性提升 I；机器人被减速（步行机缓慢 II，无人机速度降两成），每秒有 10% 概率故障 1.5 秒（停住不动、不能开火，冒火花），'
                                  '每秒损失 1.5% 生命（最多 4 点，只会削弱不会直接打死）。多台发生器效果不叠加。右键查看电量。',
    'heliosphere:blast_concrete': '抗爆混凝土：爆炸抗性 2000，比黑曜石（1200）和所有机器人弹头的上限（1300）都高——导弹、炮击、钻地弹、钻掘机、核爆冲击波都破坏不了它，'
                                  '机器人只能绕路或从别处进攻。玩家用石镐以上就能挖下来，硬度和石头差不多。工作台：石头×4 + 沙砾×4 + 铁锭 → 8 块，适合大量建造掩体和外墙。',
    'heliosphere:blast_glass': '抗爆玻璃：和抗爆混凝土一样炸不坏的透明夹丝玻璃，用来做观察窗。工作台：玻璃×4 + 抗爆混凝土×4 + 铁锭 → 8 块。用镐挖下来不会碎。',
}
TIP = ('§d结界发生器与抗爆方块：§r「结界发生器」（总装台）接上电就在周围 64×64 格罩出结界：玩家获得生命恢复 I + 抗性提升 I；机器人减速、偶尔故障停火 1.5 秒、慢慢掉血（每秒最多 4 点）。'
       '「抗爆混凝土」（工作台：石头×4 + 沙砾×4 + 铁锭 → 8）和「抗爆玻璃」爆炸抗性 2000，机器人的导弹、炮击、钻地弹和钻掘机都打不穿，用它造掩体和外墙最划算。'
       '另外：现在任何护甲都能额外抵挡机器人和怪物的攻击（护甲值越高越多，最多再减 70%），宇航服护甲提升到钻石级；玩家武器对机器人伤害 ×2.5；防御炮塔伤害 ×1.4、耐久提高一半。')


def main():
    os.makedirs(f'{A}/textures/block', exist_ok=True)
    concrete().save(f'{A}/textures/block/blast_concrete.png')
    glass().save(f'{A}/textures/block/blast_glass.png')
    for on in (False, True):
        sfx = '_on' if on else ''
        generator(True, on).save(f'{A}/textures/block/ward_generator_top{sfx}.png')
        generator(False, on).save(f'{A}/textures/block/ward_generator_side{sfx}.png')
        write(f'{A}/models/block/ward_generator{sfx}.json', {'parent': 'minecraft:block/cube_column',
              'textures': {'end': f'heliosphere:block/ward_generator_top{sfx}', 'side': f'heliosphere:block/ward_generator_side{sfx}'}})
    write(f'{A}/blockstates/ward_generator.json', {'variants': {'active=false': {'model': 'heliosphere:block/ward_generator'},
                                                                  'active=true': {'model': 'heliosphere:block/ward_generator_on'}}})
    for b in ('blast_concrete', 'blast_glass'):
        write(f'{A}/models/block/{b}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'heliosphere:block/{b}'}})
        write(f'{A}/blockstates/{b}.json', {'variants': {'': {'model': f'heliosphere:block/{b}'}}})
    for b in ('ward_generator', 'blast_concrete', 'blast_glass'):
        write(f'{A}/items/{b}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{b}'}})
        write(f'{D}/loot_table/blocks/{b}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
              'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{b}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(f'{D}/recipe/blast_concrete.json', {'type': 'minecraft:crafting_shaped', 'category': 'building',
          'pattern': ['SGS', 'GIG', 'SGS'], 'key': {'S': 'minecraft:stone', 'G': 'minecraft:gravel', 'I': 'minecraft:iron_ingot'},
          'result': {'id': 'heliosphere:blast_concrete', 'count': 8}})
    write(f'{D}/recipe/blast_glass.json', {'type': 'minecraft:crafting_shaped', 'category': 'building',
          'pattern': ['GCG', 'CIC', 'GCG'], 'key': {'G': 'minecraft:glass', 'C': 'heliosphere:blast_concrete', 'I': 'minecraft:iron_ingot'},
          'result': {'id': 'heliosphere:blast_glass', 'count': 8}})
    for i, code in enumerate(('zh_cn', 'en_us', 'zh_tw')):
        p = f'{A}/lang/{code}.json'; d = load(p, {})
        for k, v in LANG.items(): d[k] = v[i]
        write(p, d)
    p = f'{G}/purposes.json'; d = load(p, {})
    d.update(PAGES)
    write(p, d)
    print('ward assets written')


if __name__ == '__main__':
    main()
