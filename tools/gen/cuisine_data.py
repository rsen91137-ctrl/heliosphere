#!/usr/bin/env python3
"""Data and language files of the food expansion (overlay jars/04-foods), all generated from cuisine_spec.py:

  assets/heliosphere/lang/{zh_cn,en_us,zh_tw}.json         every name and the fixed tooltip / screen keys
  assets/heliosphere/blockstates/<crop>.json                crop blockstates, age 0-3
  assets/heliosphere/models/block/crop/<crop>_stage<N>.json crop models (parent block/crop or block/cross)
  assets/heliosphere/items/<id>.json, models/item/<id>.json every item except the cookware and the block items
  data/heliosphere/loot_table/blocks/<id>.json              crops, cookware machines and blocks
  data/heliosphere/recipe/<id>.json                         crafting table: the spec's 'crafting' recipes and CRAFT
  data/minecraft/tags/block/mineable/{pickaxe,axe}.json     cookware and blocks
  data/minecraft/tags/block/{maintains_farmland,crops}.json crops (dry farmland under them stays farmland, bees help)
  data/minecraft/tags/item/villager_plantable_seeds.json    seeds (farmer villagers pick them up and plant them)

The cookware and block models, their blockstates and item definitions belong to the block-assets generator, the
textures to the texture generator, the machine recipes to the Java side (from cuisine.json). Deterministic; writes
only the files above, deletes nothing (files it no longer writes are listed as stale). Run from anywhere:
    python3 tools/gen/cuisine_data.py
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import cuisine_spec as S  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
RES = os.path.join(ROOT, 'jars/04-foods/src/main/resources')
NS = 'heliosphere'

# ---------------------------------------------------------------------------------------------------------------------
# language

# Player-facing descriptions of the food buffs (effect.heliosphere.<id>.desc), from the notes in S.EFFECTS.
EFFECT_DESC = {
    'warmth': ('免疫撞击冬季的霜冻，在细雪中也不会冻僵', 'Immune to the frost of the impact winter and to freezing in powder snow'),
    'rad_shield': ('身体受到的辐射剂量减为四分之一', 'Cuts the radiation dose reaching the body to a quarter'),
    'acid_shield': ('酸雨不再造成伤害', 'Acid rain does no harm'),
    'nourished': ('饥饿值不会下降', 'Hunger does not drop'),
}

# Keys the Java side reads besides the item / block / effect names (zh_cn, en_us; zh_tw is converted).
FIXED = [
    ('tooltip.heliosphere.cuisine.heat', '放在热源上使用：营火、火、岩浆、岩浆块、点燃的熔炉/烟熏炉/高炉或电磁炉',
     'Place it on a heat source: campfire, fire, lava, magma block, a lit furnace/smoker/blast furnace or an induction stove'),
    ('condition.heliosphere.needs_heat', '需要热源', 'Needs heat below'),
    ('tooltip.heliosphere.cuisine.tool', '合成时留在合成格里，每次消耗 1 点耐久', 'Stays in the crafting grid, losing 1 durability per use'),
    ('tooltip.heliosphere.cuisine.choose', '放好食材后，点右侧配方列表里的菜开始做（或按 ▶）',
     'Put the ingredients in, then click the dish in the recipe list (or press ▶)'),
    ('tooltip.heliosphere.seed_packet', '右键打开：随机得到 3 包作物种子', 'Right-click: three random crop seeds'),
    ('tooltip.heliosphere.hydroponic_planter', '作物直接种在上面，不需要耕地和水；通电（8 FE/t）时长得更快并发光',
     'Plant crops right on it, no farmland or water needed; grows faster and gives light while powered (8 FE/t)'),
    ('tooltip.heliosphere.induction_stove', '通电后作为炒锅、汤锅、蒸笼的热源（烹饪时 12 FE/t）',
     'Powered, it heats a wok, pot or steamer on top (12 FE/t while cooking)'),
    ('screen.heliosphere.cuisine.heat_ok', '热源：有', 'Heat: yes'),
    ('screen.heliosphere.cuisine.heat_missing', '热源：没有（放到营火、岩浆块、点燃的炉子或电磁炉上）',
     'Heat: none (put it on a campfire, magma block, lit furnace or induction stove)'),
]

# Further keys other parts of the expansion ask for (their 'extraLang'): (key, zh_cn, en_us[, zh_tw]).
EXTRA = []

# Simplified -> traditional (Taiwan forms), by hand. Words first (longest match), then single characters. A character
# that converts differently by meaning (面 noodle/surface, 干, 卷, 卜 ...) is in neither table and must be covered by a
# word, so a new name with it fails here instead of being converted wrongly.
TW_WORDS = {
    '面粉': '麵粉', '面团': '麵糰', '面条': '麵條', '拉面': '拉麵', '炸酱面': '炸醬麵', '意面': '意麵', '擀面杖': '擀麵杖',
    '上面': '上面', '萝卜': '蘿蔔', '饼干': '餅乾', '春卷': '春捲', '咖喱': '咖哩', '御寒': '禦寒',
    '细雪': '粉雪',  # powder snow: the block's name in the Taiwanese game
}
TW_CHARS = dict(zip(
    '丝为会伤体侧冻减击剂叶发后团圆块头姜宫并开扬时机条树汉汤没浆浓炉点烟烧烫热熏电盐种笼红细绿芦苏萝营萨葱蓝辐酱里钵锅键长随饥饪饭饱饺饼饿馒鱼鲜鸡鸭麦黄',
    '絲為會傷體側凍減擊劑葉發後團圓塊頭薑宮並開揚時機條樹漢湯沒漿濃爐點煙燒燙熱燻電鹽種籠紅細綠蘆蘇蘿營薩蔥藍輻醬裡缽鍋鍵長隨飢飪飯飽餃餅餓饅魚鮮雞鴨麥黃'))
TW_SAME = set(
    '一丁三上下不中久之也京作使保值做僵光免再冬刀分列到包北南受右合和咖啡四在地培堡大奶好始婆子季害寒射小岩州得快意成'
    '或打抗披拉拍按排接撞擀放方明昔春更月有材杖果格植椒榨槽次每水汁沙油治泡洋派消源火炒炸烤烹煎熔燃爆牛物玉珍珠瓜生用留'
    '番疫瘦白的皮直研磁磨稻箱米粉粥粽糊糕糖罐耐耕耗肉腐腩腹芋芝花茄茶草莓菜菠萄葡葫蒜蒸蓉蔬薯蛋表要豆身辣通造配酪酵酸醋'
    '量降雨雪需霜青韭食香骨高麻')
TW_WORD_KEYS = sorted(TW_WORDS, key=len, reverse=True)


def cjk(ch):
    return 0x3400 <= ord(ch) <= 0x9FFF or 0xF900 <= ord(ch) <= 0xFAFF  # CJK ideographs (unified, ext. A, compatibility)


def traditional(s):
    out, i = [], 0
    while i < len(s):
        word = next((w for w in TW_WORD_KEYS if s.startswith(w, i)), None)
        if word:
            out.append(TW_WORDS[word])
            i += len(word)
            continue
        ch = s[i]
        if ch in TW_CHARS:
            out.append(TW_CHARS[ch])
        elif ch in TW_SAME or not cjk(ch):
            out.append(ch)
        else:
            sys.exit(f'zh_tw: {ch!r} in {s!r} has no traditional form here: add the word to TW_WORDS, or the character '
                     f'to TW_CHARS (if it changes) or TW_SAME (if it does not)')
        i += 1
    return ''.join(out)


def seed_names(crop_row, taken_en):
    """'<crop>种子' / '<crop> Seeds'; where the English is already another item's name (the produce of sesame is
    'Sesame Seeds'), the seed for sowing becomes '<crop> Planting Seeds' so the two can be told apart."""
    zh, en = crop_row[1] + '种子', crop_row[2] + ' Seeds'
    return zh, (crop_row[2] + ' Planting Seeds' if en in taken_en else en)


def names():
    """(key, zh_cn, en_us, zh_tw or None) of every entry, in a fixed order."""
    rows = []
    missing = [e[0] for e in S.EFFECTS if e[0] not in EFFECT_DESC]
    assert not missing, f'describe the new effects in EFFECT_DESC: {missing}'
    for e in S.EFFECTS:
        rows.append((f'effect.{NS}.{e[0]}', e[1], e[2], None))
        zh, en = EFFECT_DESC[e[0]]
        rows.append((f'effect.{NS}.{e[0]}.desc', zh, en, None))
    taken_en = {c[5] for c in S.CROPS}
    taken_en |= {x[2] for g in (S.INGREDIENTS, S.DISHES, S.TOOLS, S.OTHER, S.MACHINES, S.BLOCKS) for x in g}
    for c in S.CROPS:
        crop, zh, en, produce, pzh, pen = c[:6]
        rows.append((f'block.{NS}.{crop}', zh, en, None))
        rows.append((f'item.{NS}.{crop}_seeds', *seed_names(c, taken_en), None))
        rows.append((f'item.{NS}.{produce}', pzh, pen, None))
    for group in (S.INGREDIENTS, S.DISHES, S.TOOLS, S.OTHER):
        rows += [(f'item.{NS}.{x[0]}', x[1], x[2], None) for x in group]
    for group in (S.MACHINES, S.BLOCKS):
        rows += [(f'block.{NS}.{x[0]}', x[1], x[2], None) for x in group]
    rows += [(k, zh, en, None) for k, zh, en in FIXED]
    rows += [(x[0], x[1], x[2], x[3] if len(x) > 3 else None) for x in EXTRA]
    keys = [r[0] for r in rows]
    dup = sorted({k for k in keys if keys.count(k) > 1})
    assert not dup, f'translation keys twice: {dup}'
    return rows


def lang():
    rows = names()
    tables = {'zh_cn': {k: zh for k, zh, en, tw in rows}, 'en_us': {k: en for k, zh, en, tw in rows},
              'zh_tw': {k: tw or traditional(zh) for k, zh, en, tw in rows}}
    held = {f'block.{NS}.{x[0]}' for g in (S.MACHINES, S.BLOCKS) for x in g}  # block items, named by their block
    for code, table in tables.items():
        write(f'assets/{NS}/lang/{code}.json', table)
        seen = {}
        for k, v in table.items():
            if k.startswith('item.') or k in held:
                seen.setdefault(v, []).append(k)
        for v, ks in seen.items():
            if len(ks) > 1:
                print(f'  warning: {code}: {len(ks)} items are all called {v!r}: {", ".join(ks)}')
    return len(rows)


# ---------------------------------------------------------------------------------------------------------------------
# blockstates and models

STAGES = 4  # crop age 0..3


def crops():
    for c in S.CROPS:
        crop, model = c[0], c[7]
        assert model in ('crop', 'cross'), (crop, model)
        write(f'assets/{NS}/blockstates/{crop}.json',
              {'variants': {f'age={n}': {'model': f'{NS}:block/crop/{crop}_stage{n}'} for n in range(STAGES)}})
        for n in range(STAGES):
            write(f'assets/{NS}/models/block/crop/{crop}_stage{n}.json',
                  {'parent': f'minecraft:block/{model}', 'textures': {model: f'{NS}:block/crop/{crop}_stage{n}'}})


def flat_items():
    """Every item drawn as a flat sprite: seeds, produce, ingredients, dishes, tools and the seed packet. The cookware
    machines and the blocks are block items whose definitions point at the block model (block-assets generator)."""
    ids = [c[0] + '_seeds' for c in S.CROPS] + [c[3] for c in S.CROPS]
    for group in (S.INGREDIENTS, S.DISHES, S.TOOLS, S.OTHER):
        ids += [x[0] for x in group]
    assert len(ids) == len(set(ids)), 'an item twice'
    assert not set(ids) & ({m[0] for m in S.MACHINES} | {b[0] for b in S.BLOCKS})
    return ids


def items():
    ids = flat_items()
    for i in ids:
        write(f'assets/{NS}/items/{i}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{i}'}})
        write(f'assets/{NS}/models/item/{i}.json',
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{i}'}})
    return len(ids)


# ---------------------------------------------------------------------------------------------------------------------
# loot tables

def mature(crop):
    return [{'condition': 'minecraft:block_state_property', 'block': f'{NS}:{crop}',
             'properties': {'age': str(STAGES - 1)}}]


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi)}


def loot():
    for c in S.CROPS:
        crop, produce = c[0], c[3]
        seed = f'{NS}:{crop}_seeds'
        write(f'data/{NS}/loot_table/blocks/{crop}.json', {
            'type': 'minecraft:block',
            'functions': [{'function': 'minecraft:explosion_decay'}],
            'pools': [
                # broken at any age: the seed back
                {'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': seed}]},
                # ripe: 1-3 of the produce, more with Fortune
                {'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': mature(crop), 'entries': [{
                    'type': 'minecraft:item', 'name': f'{NS}:{produce}', 'functions': [
                        {'function': 'minecraft:set_count', 'count': uniform(1, 3), 'add': False},
                        {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune',
                         'formula': 'minecraft:uniform_bonus_count', 'parameters': {'bonusMultiplier': 1}}]}]},
                # ripe: 0-2 seeds more
                {'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': mature(crop), 'entries': [{
                    'type': 'minecraft:item', 'name': seed,
                    'functions': [{'function': 'minecraft:set_count', 'count': uniform(0, 2), 'add': False}]}]},
            ],
            'random_sequence': f'{NS}:blocks/{crop}',
        })
    blocks = [m[0] for m in S.MACHINES] + [b[0] for b in S.BLOCKS]
    for b in blocks:  # every cookware machine and block drops itself
        write(f'data/{NS}/loot_table/blocks/{b}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{b}'}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'{NS}:blocks/{b}',
        })
    return len(S.CROPS) + len(blocks)


# ---------------------------------------------------------------------------------------------------------------------
# crafting-table recipes

def ref(x):
    """Item or tag reference: a bare id is ours, 'ns:x' and '#ns:tag' are kept."""
    return x if ':' in x else f'{NS}:{x}'


def path(x):
    return x.split(':')[-1]


# Shapes for the CRAFT table (ingredient counts are checked against the spec). Each was checked against the vanilla
# 1.21.11 recipe list for an arrangement another recipe also takes: the minecart (5 iron in a U), cauldron, hopper,
# stonecutter, bowl, bamboo block, scaffolding, decorated pot and flower pot all differ by an item or a cell.
# Anything in CRAFT without a shape here becomes a shapeless recipe.
SHAPES = {
    'kitchen_knife': (['NIN', ' S '], {'N': 'minecraft:iron_nugget', 'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'}),
    'rolling_pin': (['S#S'], {'S': 'minecraft:stick', '#': '#minecraft:wooden_slabs'}),
    'mortar_and_pestle': (['TST', ' T '], {'T': 'minecraft:stone', 'S': 'minecraft:stick'}),
    'wok': (['  S', 'I I', 'III'], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'}),
    'cooking_pot': ([' I ', 'IBI', 'III'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:bucket'}),
    'steamer': (['BBB', 'BSB', 'BBB'], {'B': 'minecraft:bamboo', 'S': 'minecraft:string'}),
    'fermenter': (['CTC', 'T T', 'STS'], {'C': 'minecraft:clay_ball', 'T': 'minecraft:terracotta', 'S': 'minecraft:oak_slab'}),
}


def category(item):
    return 'equipment' if item in {t[0] for t in S.TOOLS} else 'misc'


def result(item, count):
    return {'id': ref(item), 'count': count}


def shapeless(inputs, out, count, group=None):
    ings = [ref(i) for i, n in inputs for _ in range(n)]
    assert 1 <= len(ings) <= 9, (out, len(ings))
    r = {'type': 'minecraft:crafting_shapeless', 'category': category(out)}
    if group:
        r['group'] = group
    r.update({'ingredients': ings, 'result': result(out, count)})
    return r


def shaped(item, inputs):
    pattern, key = SHAPES[item]
    assert 1 <= len(pattern) <= 3 and len({len(row) for row in pattern}) == 1 and len(pattern[0]) <= 3, (item, pattern)
    counts = {}
    for row in pattern:
        for ch in row:
            if ch != ' ':
                counts[key[ch]] = counts.get(key[ch], 0) + 1
    want = {}
    for i, n in inputs:
        want[ref(i)] = want.get(ref(i), 0) + n
    assert counts == want, f'the shape of {item} does not use the spec ingredients: {counts} != {want}'
    assert set(key) == {ch for row in pattern for ch in row} - {' '}, (item, 'unused key')
    return {'type': 'minecraft:crafting_shaped', 'category': category(item), 'pattern': pattern, 'key': key,
            'result': result(item, 1)}


def recipes():
    made = {}
    crafting = [(ins, out) for machine, ins, out, sec in S.R if machine == 'crafting']
    first, ways = {}, {}
    for ins, (out, n) in crafting:
        first.setdefault(out, ins)
        ways[out] = ways.get(out, 0) + 1
    seen = {}
    for ins, (out, n) in crafting:
        seen[out] = seen.get(out, 0) + 1
        name = path(out)
        if seen[out] > 1:  # another way to the same thing: named after what it takes instead
            new = [i for i, _ in ins if i not in {j for j, _ in first[out]}]
            if new:
                name += f'_from_{path(new[0])}'
            base, k = name, 2
            while name in made:
                name, k = f'{base}_{k}', k + 1
        made[name] = shapeless(ins, out, n, group=f'{NS}:{path(out)}' if ways[out] > 1 else None)
    for item, ins in S.CRAFT.items():
        assert ':' not in item and item not in made, ('recipe name twice', item)
        made[item] = shaped(item, ins) if item in SHAPES else shapeless(ins, item, 1)
    unknown = set(SHAPES) - set(S.CRAFT)
    assert not unknown, f'shapes for things the spec does not craft: {unknown}'
    for name, r in made.items():
        write(f'data/{NS}/recipe/{name}.json', r)
    return len(made)


# ---------------------------------------------------------------------------------------------------------------------
# tags (entries optional: a missing block or item must not void the whole vanilla tag)

AXE = {'steamer', 'fermenter'}  # bamboo and wood; the rest of the cookware is metal or stone


def tag(rel, ids):
    write(rel, {'replace': False, 'values': [{'id': f'{NS}:{i}', 'required': False} for i in ids]})


def tags():
    blocks = [m[0] for m in S.MACHINES] + [b[0] for b in S.BLOCKS]
    assert AXE <= set(blocks), AXE - set(blocks)
    tag('data/minecraft/tags/block/mineable/pickaxe.json', [b for b in blocks if b not in AXE])
    tag('data/minecraft/tags/block/mineable/axe.json', [b for b in blocks if b in AXE])
    crop_ids = [c[0] for c in S.CROPS]
    # FarmBlock turns dry farmland back to dirt (and the crop pops off) unless the block on it is in this tag
    tag('data/minecraft/tags/block/maintains_farmland.json', crop_ids)
    tag('data/minecraft/tags/block/crops.json', crop_ids)  # in bee_growables: bees carrying pollen grow them
    tag('data/minecraft/tags/item/villager_plantable_seeds.json', [c + '_seeds' for c in crop_ids])
    return 5


# ---------------------------------------------------------------------------------------------------------------------

written = []


def write(rel, obj):
    """Write one JSON file under the module's resources (left untouched when the content is the same)."""
    assert rel not in written, ('written twice', rel)
    written.append(rel)
    p = os.path.join(RES, rel)
    text = json.dumps(obj, ensure_ascii=False, indent=2) + '\n'
    old = None
    if os.path.exists(p):
        with open(p, encoding='utf-8') as f:
            old = f.read()
    if old != text:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)


def stale():
    """Files in the directories this script fills that it did not write this time (left in place)."""
    others = {m[0] for m in S.MACHINES} | {b[0] for b in S.BLOCKS}  # the block-assets generator's
    mine = set(written)
    found = []
    for d, skip_others in ((f'data/{NS}/recipe', False), (f'data/{NS}/loot_table/blocks', False),
                           (f'assets/{NS}/models/block/crop', False), (f'assets/{NS}/models/item', True),
                           (f'assets/{NS}/items', True)):
        full = os.path.join(RES, d)
        if not os.path.isdir(full):
            continue
        for f in sorted(os.listdir(full)):
            rel = f'{d}/{f}'
            if f.endswith('.json') and rel not in mine and not (skip_others and f[:-5] in others):
                found.append(rel)
    return found


def main():
    S.validate()
    n_lang = lang()
    crops()
    n_items = items()
    n_loot = loot()
    n_recipes = recipes()
    n_tags = tags()
    print(f'{n_lang} translation keys x3, {len(S.CROPS)} crops ({len(S.CROPS) * STAGES} stage models), {n_items} item models, '
          f'{n_loot} loot tables, {n_recipes} recipes, {n_tags} tags: {len(written)} files under {os.path.relpath(RES, ROOT)}')
    for rel in stale():
        print(f'  stale (not written by this run, left alone): {rel}')


if __name__ == '__main__':
    main()
