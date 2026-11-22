#!/usr/bin/env python3
"""Starch from CO2 (overlay 05-industry, industry.kitchen.Starch): the starch item and the starch reactor's block
textures, its orientable block models (off and on), blockstate, item definitions, loot table, pickaxe tag and the
language entries. Deterministic. Run from the repository root:
    python3 tools/gen/starch_assets.py [--no-shared] [--sheet preview.png]
--no-shared skips the files other work shares (language files and the pickaxe tag)."""
import json, os, sys
from PIL import Image

R = 'jars/05-industry/src/main/resources'
A = f'{R}/assets/heliosphere'
D = f'{R}/data'
ID = 'starch_reactor'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def rgb(h, a=255):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def shade(c, k):
    return tuple(max(0, min(255, round(v * k))) for v in c[:3]) + (c[3],)


def new():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), c)


# ---- the machine casing: bevelled light-green steel, like the other industry machines ----
CASE = rgb('#8fa79a')
EDGE_HI, EDGE_LO, SEAM = shade(CASE, 1.22), shade(CASE, 0.62), shade(CASE, 0.78)
BOLT = rgb('#d9e2dc')


def casing():
    img = new()
    rect(img, 0, 0, 15, 15, CASE)
    for i in range(16):
        img.putpixel((i, 0), EDGE_HI); img.putpixel((0, i), EDGE_HI)
        img.putpixel((i, 15), EDGE_LO); img.putpixel((15, i), EDGE_LO)
    for i in range(1, 15):
        img.putpixel((i, 1), shade(CASE, 1.08)); img.putpixel((i, 14), shade(CASE, 0.8))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), BOLT); img.putpixel((x + 1, y + 1) if x < 8 else (x - 1, y + 1), EDGE_LO)
    return img


def front(on):
    img = casing()
    glass, frame = rgb('#1d2b26'), rgb('#4b5b54')
    rect(img, 3, 3, 12, 12, frame)
    rect(img, 4, 4, 11, 11, glass)
    # three vertical photobioreactor tubes: green culture, brighter when running
    tube = [rgb('#3d8f3a'), rgb('#57b84a'), rgb('#7fe06a')] if on else [rgb('#2c5a2e'), rgb('#3a7438'), rgb('#4b8a45')]
    for tx in (5, 7, 9):
        for y in range(5, 11):
            img.putpixel((tx, y), tube[1]); img.putpixel((tx + 1, y), tube[0])
        img.putpixel((tx, 5), tube[2])
    if on:                                                    # rising bubbles and the grow lights
        for x, y in ((5, 9), (8, 7), (9, 9), (6, 6), (10, 6)):
            img.putpixel((x, y), rgb('#c8ffb8'))
        for x in range(4, 12):
            img.putpixel((x, 4), rgb('#fff3b0'))
    else:
        for x in range(4, 12):
            img.putpixel((x, 4), rgb('#3a3a2a'))
    # CO2 inlet pipe at the bottom, status lamp at the top right
    for x in range(3, 13):
        img.putpixel((x, 13), rgb('#b9c4c9')); img.putpixel((x, 12), shade(rgb('#b9c4c9'), 0.75))
    img.putpixel((12, 2), rgb('#7cff7c') if on else rgb('#335533'))
    return img


def side():
    img = casing()
    # a grille and two pipes
    for y in range(4, 12, 2):
        for x in range(4, 12):
            img.putpixel((x, y), shade(CASE, 0.55))
            img.putpixel((x, y + 1), shade(CASE, 0.9))
    for y in range(2, 14):
        img.putpixel((2, y), rgb('#b9c4c9')); img.putpixel((13, y), rgb('#d0a24a'))
    return img


def top():
    img = casing()
    # a skylight panel: the light collectors of the artificial photosynthesis
    rect(img, 3, 3, 12, 12, rgb('#3b4a55'))
    for y in range(4, 12):
        for x in range(4, 12):
            img.putpixel((x, y), rgb('#5d87a8') if (x + y) % 4 else rgb('#86b3d6'))
    for i in range(4, 12):
        img.putpixel((7, i), rgb('#2f3d47')); img.putpixel((i, 7), rgb('#2f3d47'))
    return img


def starch():
    """A paper sack of starch with a green leaf on it and white powder at the open top."""
    img = new()
    bag, bag_lo, bag_hi, ink = rgb('#d8c49a'), rgb('#a8916a'), rgb('#efe2c2'), rgb('#4f3d27')
    powder, powder_lo = rgb('#fbfbf6'), rgb('#d9dde0')
    for y in range(5, 15):
        for x in range(3, 13):
            img.putpixel((x, y), bag)
    for y in range(5, 15):
        img.putpixel((3, y), bag_hi); img.putpixel((12, y), bag_lo); img.putpixel((2, y), ink); img.putpixel((13, y), ink)
    for x in range(3, 13):
        img.putpixel((x, 15), ink); img.putpixel((x, 14), bag_lo)
    # powder heaped above the rim
    for x, h in zip(range(3, 13), (1, 2, 3, 4, 4, 4, 3, 3, 2, 1)):
        for y in range(5 - h, 6):
            img.putpixel((x, y), powder if y < 5 or x % 3 else powder_lo)
    for x, h in zip(range(3, 13), (1, 2, 3, 4, 4, 4, 3, 3, 2, 1)):          # a soft grey rim so the heap shows on light backgrounds
        img.putpixel((x, 4 - h), rgb('#b4bac0'))
    img.putpixel((2, 4), ink); img.putpixel((13, 4), ink)
    # the leaf
    leaf, leaf_lo = rgb('#58a44a'), rgb('#3a7a33')
    for x, y in ((7, 8), (8, 8), (6, 9), (7, 9), (8, 9), (9, 9), (7, 10), (8, 10), (6, 11)):
        img.putpixel((x, y), leaf)
    for x, y in ((9, 8), (9, 10), (7, 11)):
        img.putpixel((x, y), leaf_lo)
    return img


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    shared = '--no-shared' not in sys.argv
    tex = {f'{A}/textures/block/machine/{ID}_front.png': front(False), f'{A}/textures/block/machine/{ID}_front_on.png': front(True),
           f'{A}/textures/block/machine/{ID}_side.png': side(), f'{A}/textures/block/machine/{ID}_top.png': top(),
           f'{A}/textures/item/starch.png': starch()}
    for p, img in tex.items(): save(img, p)
    for on in (False, True):
        write(f'{A}/models/block/{ID}{"_on" if on else ""}.json', {'parent': 'minecraft:block/orientable', 'textures': {
            'top': f'heliosphere:block/machine/{ID}_top', 'front': f'heliosphere:block/machine/{ID}_front{"_on" if on else ""}',
            'side': f'heliosphere:block/machine/{ID}_side'}})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for lit in (False, True):
            v = {'model': f'heliosphere:block/{ID}{"_on" if lit else ""}'}
            if y: v['y'] = y
            variants[f'facing={facing},lit={"true" if lit else "false"}'] = v
    write(f'{A}/blockstates/{ID}.json', {'variants': variants})
    write(f'{A}/items/{ID}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{ID}'}})
    write(f'{A}/items/starch.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:item/starch'}})
    write(f'{A}/models/item/starch.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'heliosphere:item/starch'}})
    write(f'{D}/heliosphere/loot_table/blocks/{ID}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': f'heliosphere:{ID}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    if shared:
        tag = f'{D}/minecraft/tags/block/mineable/pickaxe.json'
        t = json.load(open(tag, encoding='utf-8')) if os.path.exists(tag) else {'replace': False, 'values': []}
        entry = f'heliosphere:{ID}'
        if entry not in t['values'] and {'id': entry, 'required': False} not in t['values']: t['values'].append(entry)
        write(tag, t)
        lang = {
            'zh_cn': {f'block.heliosphere.{ID}': '人工光合淀粉反应器', 'item.heliosphere.starch': '淀粉',
                      'item.heliosphere.starch_made': '%s（淀粉合成）', 'tooltip.heliosphere.starch_made': '淀粉合成：比正常的少一格饱食度',
                      'condition.heliosphere.co2_air': '需要在火星或金星露天', 'condition.heliosphere.open_air': '需要有大气并能看到天空'},
            'zh_tw': {f'block.heliosphere.{ID}': '人工光合澱粉反應器', 'item.heliosphere.starch': '澱粉',
                      'item.heliosphere.starch_made': '%s（澱粉合成）', 'tooltip.heliosphere.starch_made': '澱粉合成：比正常的少一格飽食度',
                      'condition.heliosphere.co2_air': '需要在火星或金星露天', 'condition.heliosphere.open_air': '需要有大氣並能看到天空'},
            'en_us': {f'block.heliosphere.{ID}': 'Artificial Photosynthesis Starch Reactor', 'item.heliosphere.starch': 'Starch',
                      'item.heliosphere.starch_made': '%s (Starch-made)', 'tooltip.heliosphere.starch_made': 'Made from starch: one shank less food than the real thing',
                      'condition.heliosphere.co2_air': 'Needs the open sky of Mars or Venus', 'condition.heliosphere.open_air': 'Needs air and the open sky'},
        }
        for code, kv in lang.items():
            path = f'{A}/lang/{code}.json'
            have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
            have.update(kv)
            write(path, have)
    if '--sheet' in sys.argv:
        out = sys.argv[sys.argv.index('--sheet') + 1]
        sheet = Image.new('RGBA', (16 * 8 * 5 + 40, 16 * 8 + 16), (40, 40, 48, 255))
        for i, img in enumerate(tex.values()):
            sheet.paste(img.resize((128, 128), Image.NEAREST), (8 + i * 136, 8))
        sheet.save(out)
    print('starch assets written' + ('' if shared else ' (shared files skipped)'))


main()
