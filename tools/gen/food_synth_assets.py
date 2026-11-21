#!/usr/bin/env python3
"""The food synthesizer and its fifteen buff meals (overlay 05-industry, package industry.kitchen): hand-pixelled
16x16 textures (drawn below as character maps, so every run gives the same files), the orientable block models
(off and on), blockstate, item definitions, loot table, pickaxe tag and the language entries in three languages.
Run from the repository root:  python3 tools/gen/food_synth_assets.py [--sheet preview.png]"""
import json, os, sys
from PIL import Image

R = 'jars/05-industry/src/main/resources'
A = f'{R}/assets/heliosphere'
D = f'{R}/data'
MACHINE = 'food_synthesizer'


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


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + ((int(h[6:8], 16),) if len(h) == 8 else (255,))


def paint(rows, palette):
    """A 16x16 image from sixteen strings of sixteen characters; '.' is transparent."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.': img.putpixel((x, y), rgb(palette[ch]))
    return img


# ---- the machine: ivory enamel casing, an oven window with a bowl in it, a hopper on top, an orange band -------------

CASE = {
    'L': '#beb9a8', 'T': '#c8c3b2', 'B': '#96917f', 'b': '#9f9a88', 'D': '#58544b', 'R': '#646056',
    'r': '#d8d3c0', 's': '#6c685d',                              # rivet and its shadow
    'K': '#1a1b20', 'k': '#101216', 'g': '#39414f', 'S': '#3a4049',  # window frame, dark inside, glass glint, sill
    'W': '#e4e0d2', 'w': '#a9a59a',                              # bowl
    'f': '#6f4f28', 'F': '#8a6534',                              # food, cold
    'O': '#e8842c', 'o': '#b05c1c',                              # orange band
    'e': '#305030', 'E': '#504030',                              # status lights, off
    'v': '#3b3832', 't': '#cac5b3',                              # vent slat and its lit lip
    'n': '#46423c', 'p': '#605c54', 'q': '#827d70',              # hopper: shadowed rim, shaded and lit slopes
}
HOT = dict(CASE, k='#3a1a0c', h='#3a1a0c', H='#743210', g='#7a5a48', f='#ff9c2e', F='#ffd872', m='#f4dcb4',
           e='#50e66e', E='#ffaa32', W='#fff0d8', w='#d8b890')

FRONT = [
    'LTTTTTTTTTTTTTTR',
    'LBBbBBbbBBBBBBBR',
    'LBrBBBBBBBBBBrBR',
    'LBsKKKKKKKKKKsBR',
    'LBBKgkkkkkkkkBBR',
    'LBBKkgkkkkkkkBBR',
    'LBBKkkkfffkkkbbR',
    'LBBKkkfFfffkkbbR',
    'LBBKkWWWWWWwkBBR',
    'LBBKkkWWWwwkkBBR',
    'LbbKkkkwwwkkkBBR',
    'LbrSSSSSSSSSSrBR',
    'LBBBBBBBBBBBBBBR',
    'LBeBEBBBOOOOOoBR',
    'LBBBbbBBBBBBbBBR',
    'DDDDDDDDDDDDDDDR',
]
FRONT_ON = FRONT[:4] + [
    'LBBKghhmhhmhhBBR',
    'LBBKhghhmhhmhBBR',
    'LBBKhhHfffHhhbbR',
    'LBBKhHfFfffHhbbR',
    'LBBKhWWWWWWwhBBR',
    'LBBKhhWWWwwhhBBR',
    'LbbKhhhwwwhhhBBR',
] + FRONT[11:]
SIDE = [
    'LTTTTTTTTTTTTTTR',
    'LBBBBbbBBBBBBBBR',
    'LBrBBBBBBBBBBrBR',
    'LBsBBBBBBBBbbsBR',
    'LBBvvvvvvvvvvBBR',
    'LBBttttttttttBBR',
    'LBBvvvvvvvvvvBBR',
    'LBBttttttttttBBR',
    'LbbvvvvvvvvvvBBR',
    'LbbttttttttttBBR',
    'LBBBBBBBBBBBBBBR',
    'LOOOOOOOOOOOOOOR',
    'LooooooooooooooR',
    'LBrBBBBBBbbBBrBR',
    'LBsBBBBBBBBBBsBR',
    'DDDDDDDDDDDDDDDR',
]
TOP = [
    'LTTTTTTTTTTTTTTR',
    'LBBBBBBBBBBBbbBR',
    'LBrBBBBBBBBBBrBR',
    'LBBnnnnnnnnnnBBR',
    'LBBnppppppppqBBR',
    'LBBnpppppppqqBBR',
    'LBBnppkkkkpqqBBR',
    'LBBnppkkkkpqqBBR',
    'LbbnppkkkkpqqBBR',
    'LbbnppkkkkpqqBBR',
    'LBBnpqqqqqqqqBBR',
    'LBBnqqqqqqqqqBBR',
    'LBBttttttttttBBR',
    'LBrBBBBBBBBBBrBR',
    'LBBBBbbBBBBBBBBR',
    'DDDDDDDDDDDDDDDR',
]

# ---- the meals: id -> (palette, rows). Dark outline, light from the top left, three or four tones a colour. ----------

MEALS = {}

MEALS['sprint_gel'] = (  # a squeeze pouch with a lightning bolt
    {'o': '#12303a', 'C': '#d8e4e8', 'c': '#8fa4ac', 'B': '#2fb4d6', 'W': '#9be4f4', 'b': '#1f8cb0', 'd': '#16688c',
     'Y': '#ffe14a'},
    ['................',
     '.......oo.......',
     '......oCco......',
     '......oCco......',
     '.....oobboo.....',
     '....oWBBBBbo....',
     '...oWWBBBBBbo...',
     '...oWBBBYYBbo...',
     '...oWBBYYBBdo...',
     '...oBBYYYYBdo...',
     '...oBBBBYYBdo...',
     '...oBBBYYBBdo...',
     '...oBBBYBBbdo...',
     '...obBBBBbddo...',
     '....oobbddoo....',
     '....oooooooo....'])

MEALS['miner_stew'] = (  # an open tin of stew, a pickaxe on the label
    {'o': '#2a2620', 'M': '#c4c8cc', 'm': '#8f9399', 'n': '#62666c', 'S': '#8f4e24', 's': '#6a3416', 'C': '#f08a24',
     'P': '#f2d27a', 'L': '#e8c23a', 'l': '#b88e1e', 'K': '#4a3a10'},
    ['................',
     '................',
     '....oooooooo....',
     '...oMSSCSSPmo...',
     '..oMSPSSSCSSno..',
     '..oMsSSCSSPsno..',
     '..oMMssssssmno..',
     '..oMMMMmmmmnno..',
     '..oLLLLLLLLllo..',
     '..oLLLKKKLLllo..',
     '..oLLKLKLKLllo..',
     '..oLLLLKLLLllo..',
     '..oLLLLKLLLllo..',
     '..oMMMMmmmmnno..',
     '...oMMmmmmnno...',
     '....oooooooo....'])

MEALS['myofiber_steak'] = (  # a grilled steak with its rim of fat
    {'o': '#3a1410', 'R': '#c8483a', 'r': '#a03428', 'd': '#7a241c', 'H': '#e87868', 'F': '#f4e6d2', 'f': '#d0bca0',
     'G': '#5a1c16'},
    ['................',
     '................',
     '......ooooo.....',
     '....ooFFFFFoo...',
     '...oFFHHRRRFFo..',
     '..oFHHRRGRRRFo..',
     '..oFHRRGRRGRrFo.',
     '.oFHRRGRRGRRrFo.',
     '.oFRRGRRGRRrrfo.',
     '.oFRRRRGRRrrdfo.',
     '.oFRRRGRRrrddfo.',
     '..ofRRRRrrdddfo.',
     '..offrrrrddffo..',
     '...ooffffffoo...',
     '.....oooooo.....',
     '................'])

MEALS['regen_broth'] = (  # a steaming bowl of rosy broth
    {'o': '#2a2030', 'W': '#f2f0ea', 'w': '#c8c6c8', 'g': '#9896a0', 'S': '#e8506a', 's': '#c03450', 'P': '#ffa0b0',
     'G': '#5ac85a', 'T': '#eef0f4'},
    ['................',
     '.....T....T.....',
     '....T....T......',
     '.....T....T.....',
     '................',
     '...oooooooooo...',
     '..oWSSPPSSGSwo..',
     '.oWSPPSSSGGSswo.',
     '.oWWSSSSSSSsswo.',
     '.oWWWWsssswwwgo.',
     '..oWWWWwwwwwgo..',
     '..oWWWwwwwwggo..',
     '...oWWwwwwggo...',
     '....oowwggoo....',
     '....owwwwggo....',
     '.....oooooo.....'])

MEALS['bulwark_ration'] = (  # a dense olive brick stamped with a shield
    {'o': '#20241a', 'A': '#8a9a5a', 'a': '#6e7e46', 'd': '#4e5c30', 'H': '#b4c284', 'S': '#ece8d4', 's': '#b0aa90'},
    ['................',
     '................',
     '....ooooooooooo.',
     '...oHHHHHHHHHAo.',
     '..oHHAAAAAAAAao.',
     '.oooooooooooaao.',
     '.oAAAAAAAAAoaao.',
     '.oAASSSSSAAoaao.',
     '.oAASSSSsAAoaao.',
     '.oAASSSssAAoaao.',
     '.oAAASSsAAAoado.',
     '.oAAAAsAAAaoddo.',
     '.oAaaaaaaaaodo..',
     '.oaaaaaaadaoo...',
     '.ooooooooooo....',
     '................'])

MEALS['cryo_jelly'] = (  # a cube of ice-blue jelly with two berries set in it
    {'o': '#1c3a56', 'I': '#c4ecff', 'i': '#86cdf0', 'b': '#58a6d8', 'W': '#ffffff', 'R': '#e0486a', 'r': '#a82c4c'},
    ['................',
     '................',
     '....oooooooo....',
     '...oWWIIIIIio...',
     '..oWIIIIIIIiio..',
     '..oIIIIIIIiiio..',
     '..oIWIIIIIiibo..',
     '..oIWIRRIIiibo..',
     '..oIIIRrIRRibo..',
     '..oIIIIIiRribo..',
     '..oIIiiiiiibbo..',
     '..oIiiiiiibbbo..',
     '..oiiiiibbbbbo..',
     '...oibbbbbbbo...',
     '....oooooooo....',
     '................'])

MEALS['owl_juice'] = (  # a corked bottle of orange juice with an owl's two bright eyes
    {'o': '#3a2210', 'C': '#9a7652', 'c': '#6a4e34', 'G': '#e4f4f8', 'g': '#a8c8d0', 'J': '#ffa030', 'j': '#e07818',
     'k': '#b85a10', 'H': '#ffd070', 'Y': '#fff27a'},
    ['................',
     '......oooo......',
     '......oCco......',
     '......oooo......',
     '......oGgo......',
     '.....ooGgoo.....',
     '....oGGggggo....',
     '...oGHJJJJJjo...',
     '...oHJJJJJJjo...',
     '...oHJYYJYYjo...',
     '...oJJYoJYojo...',
     '...oJJJJJJjko...',
     '...oJJJJJjjko...',
     '...ojJJJjjkko...',
     '....ojjjkkko....',
     '.....oooooo.....'])

MEALS['kelp_sushi'] = (  # two kelp rolls with salmon in the middle
    {'o': '#10201a', 'N': '#2a5a3e', 'n': '#1c4430', 'h': '#3e7a54', 'W': '#f6f4ec', 'w': '#d2d0c4', 'S': '#ff8a50',
     's': '#e06830'},
    ['................',
     '........ooooo...',
     '.......ohhhhNo..',
     '......ohWWWWNno.',
     '......oNWSSWwno.',
     '..ooooooWSsWwno.',
     '.ohhhhNooWwwNno.',
     'ohWWWWWNnoNNnno.',
     'ohWWSSWWNnonno..',
     'oNWSSSSWwnnoo...',
     'oNWSSSsWwnno....',
     'oNWWSssWwnno....',
     'oNnWWWWwNnno....',
     '.onnNNNNnno.....',
     '..ooooooooo.....',
     '................'])

MEALS['bounce_jelly'] = (  # three balls of slime gel on a skewer
    {'o': '#143a18', 'G': '#6ee05a', 'g': '#3cb83a', 'd': '#2a8a2e', 'W': '#d8ffc8', 'T': '#c8a070', 't': '#8a6a40'},
    ['...........oooT.',
     '..........oWGgo.',
     '..........oGGgo.',
     '..........oggdo.',
     '.......ooooooo..',
     '......oWGgo.....',
     '......oGGgo.....',
     '......oggdo.....',
     '...ooooooo......',
     '..oWGgo.........',
     '..oGGgo.........',
     '..oggdo.........',
     '..Tooo..........',
     '.T..............',
     't...............',
     '................'])

MEALS['hydrogen_meringue'] = (  # a white whipped peak
    {'o': '#3a4660', 'W': '#ffffff', 'w': '#e4ecf8', 's': '#b8c8e4', 'd': '#8ea0c8'},
    ['................',
     '........o.......',
     '.......oWo......',
     '.......oWwo.....',
     '......oWWwo.....',
     '.....oWWwwso....',
     '....oWWWwwsso...',
     '....osWWWwwso...',
     '...oWssWWwwsdo..',
     '..oWWWsswwwssdo.',
     '..oWWWWwsssssdo.',
     '.oWWWWwwwwsssddo',
     '.oWWwwwwwssssddo',
     '.oswwwwsssssdddo',
     '..oosssssddddoo.',
     '....oooooooooo..'])

MEALS['shield_shake'] = (  # a lidded cup with a straw and a blue shield on it
    {'o': '#2a1c14', 'L': '#f0f0f0', 'l': '#c0c4c8', 'S': '#c89060', 's': '#a06c40', 'd': '#7a4e2a', 'H': '#e8b888',
     'R': '#e84a4a', 'r': '#b02c2c', 'B': '#4aa8e8', 'b': '#2a78c0'},
    ['..........oo....',
     '.........oRro...',
     '........oRro....',
     '....oooooRoo....',
     '...oLLLLRrllo...',
     '...oLLLLLLllo...',
     '...oooooooooo...',
     '....oHSSSSsdo...',
     '....oHSBBSsdo...',
     '....oHBBBBsdo...',
     '....oHBBbbsdo...',
     '....oHSBbSsdo...',
     '.....oSSSsdo....',
     '.....oHSSsdo....',
     '.....oSsssdo....',
     '......ooooo.....'])

MEALS['vital_pudding'] = (  # a custard pudding under caramel, a cherry on top
    {'o': '#3a2410', 'C': '#8a4a18', 'c': '#6a3410', 'K': '#b06828', 'Y': '#ffe08a', 'y': '#f0c050', 'd': '#d09a30',
     'P': '#f4f4f6', 'p': '#b8bcc8', 'R': '#e8384a', 'r': '#a82030', 'W': '#ffb0b8'},
    ['................',
     '.......oo.......',
     '......oWRo......',
     '......oRro......',
     '....ooKKKKoo....',
     '...oKKCCCCCco...',
     '...oCCCccccco...',
     '...oYYCYYycyo...',
     '..oYYYYYYyyydo..',
     '..oYYYYYyyyddo..',
     '..oYYYYyyyyddo..',
     '.ooYYyyyyydddoo.',
     'oPPooooooooooppo',
     '.oPPPPPPPpppppo.',
     '..oooooooooooo..',
     '................'])

MEALS['fortune_cookie'] = (  # a folded cookie with its slip of paper
    {'o': '#4a2c10', 'T': '#e8b860', 't': '#c89040', 'd': '#a06c28', 'H': '#f8dc98', 'P': '#ffffff', 'p': '#d8d8e0',
     'R': '#e84040'},
    ['................',
     '................',
     '.....ooooo......',
     '...ooHHHTToo....',
     '..oHHTTTTTTto...',
     '.oHTTTTTTTTtto..',
     '.oHTTTTttTTTtdo.',
     'oHTTTTtddtTTtdo.',
     'oTTTTtdoodtTtdoo',
     'oTTTtdo..odttoPo',
     'oTTtdo....oooPpo',
     'otttdo.....oPRpo',
     '.otddo....oPRPo.',
     '..ooo.....oPpo..',
     '...........oo...',
     '................'])

MEALS['nutrient_capsule'] = (  # a pill, half white, half green
    {'o': '#1a2a22', 'G': '#58d878', 'g': '#34a856', 'd': '#207a3c', 'H': '#b8ffc8', 'W': '#fafafa', 'w': '#d0d4d8',
     's': '#a0a6b0'},
    ['................',
     '.........oooo...',
     '........oHHGGo..',
     '.......oHGGGGgo.',
     '......oHGGGGGgdo',
     '.....ooGGGGGgddo',
     '....oWWoGGGggdo.',
     '...oWWWWoGggdo..',
     '..oWWWWWwoddo...',
     '.oWWWWWwwsoo....',
     'oWWWWWwwsso.....',
     'oWWWWwwsso......',
     'owWWwwsso.......',
     '.owwssso........',
     '..ooooo.........',
     '................'])

MEALS['captain_feast'] = (  # a whole roast on a gold platter, with greens and a tomato
    {'o': '#2a1a0c', 'B': '#c87830', 'b': '#a05a20', 'd': '#7a3e14', 'H': '#eea858', 'G': '#f4d048', 'g': '#c89a20',
     'k': '#8a6a10', 'L': '#58c048', 'R': '#e04838', 'W': '#f6eedc'},
    ['................',
     '...........oo...',
     '..........oWWo..',
     '......ooo.oWo...',
     '....ooHHHooBo...',
     '...oHHHBBBBBbo..',
     '...oHHBBBBBBbbo.',
     '...oHBBBBBBbbdo.',
     '.ooLoBBBBBbbbdo.',
     'oLLLLobbbbbddoRo',
     'oGGLLGoooooooGRo',
     'oGGGGGGGGGGGGggo',
     '.ogggggggggkkko.',
     '..oooooooooooo..',
     '................',
     '................'])


def textures():
    out = {}
    for name, rows, pal in ((f'{MACHINE}_front', FRONT, CASE), (f'{MACHINE}_front_on', FRONT_ON, HOT),
                            (f'{MACHINE}_side', SIDE, CASE), (f'{MACHINE}_top', TOP, CASE)):
        out[f'block/machine/{name}'] = paint(rows, pal)
    for food, (pal, rows) in MEALS.items():
        out[f'item/{food}'] = paint(rows, pal)
    for path, img in out.items():
        os.makedirs(os.path.dirname(f'{A}/textures/{path}.png'), exist_ok=True)
        img.save(f'{A}/textures/{path}.png')
    return out


def sheet(images, path, scale=10, columns=8):
    """An enlarged contact sheet on the grey of an inventory slot, to judge the textures by eye."""
    cell = 16 * scale + 8
    rows = (len(images) + columns - 1) // columns
    img = Image.new('RGBA', (columns * cell, rows * cell), (139, 139, 139, 255))
    for i, tex in enumerate(images.values()):
        big = tex.resize((16 * scale, 16 * scale), Image.NEAREST)
        img.paste(big, ((i % columns) * cell + 4, (i // columns) * cell + 4), big)
    img.save(path)


def models():
    tex = f'heliosphere:block/machine/{MACHINE}'
    for suffix in ('', '_on'):
        write(f'{A}/models/block/{MACHINE}{suffix}.json', {'parent': 'minecraft:block/orientable', 'textures': {
            'top': f'{tex}_top', 'front': f'{tex}_front{suffix}', 'side': f'{tex}_side'}})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for lit, suffix in (('false', ''), ('true', '_on')):
            v = {'model': f'heliosphere:block/{MACHINE}{suffix}'}
            if y: v['y'] = y
            variants[f'facing={facing},lit={lit}'] = v
    write(f'{A}/blockstates/{MACHINE}.json', {'variants': variants})
    write(f'{A}/items/{MACHINE}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{MACHINE}'}})
    for food in MEALS:
        write(f'{A}/items/{food}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{food}'}})
        write(f'{A}/models/item/{food}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{food}'}})


def data():
    write(f'{D}/heliosphere/loot_table/blocks/{MACHINE}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{MACHINE}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'heliosphere:blocks/{MACHINE}'})
    path = f'{D}/minecraft/tags/block/mineable/pickaxe.json'
    tag = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'replace': False, 'values': []}
    entry = {'id': f'heliosphere:{MACHINE}', 'required': False}
    if entry not in tag['values']: tag['values'].append(entry)
    write(path, tag)


# id: (zh_cn name, zh_cn hint, zh_tw name, zh_tw hint, en_us name, en_us hint)
TEXT = {
    'sprint_gel': ('冲刺能量胶', '高糖蜂蜜能量胶。挤一口，接下来几分钟都能全速冲刺，赶路、甩开机器人都靠它。',
                   '衝刺能量膠', '高糖蜂蜜能量膠。擠一口，接下來幾分鐘都能全速衝刺，趕路、甩開機器人都靠它。',
                   'Sprint Energy Gel', 'Sugar and honey in a squeeze pouch. One mouthful and you run flat out for minutes.'),
    'miner_stew': ('矿工炖肉罐头', '牛肉、土豆和胡萝卜炖成的一罐。吃完挖矿、砍树的手速明显变快。',
                   '礦工燉肉罐頭', '牛肉、馬鈴薯和胡蘿蔔燉成的一罐。吃完挖礦、砍樹的手速明顯變快。',
                   "Miner's Stew", 'A tin of beef, potato and carrot. Your pick and axe work noticeably faster afterwards.'),
    'myofiber_steak': ('肌纤维合成牛排', '合成机把牛肉纤维重新排了一遍。吃下去，拳头和刀都更有劲。',
                       '肌纖維合成牛排', '合成機把牛肉纖維重新排了一遍。吃下去，拳頭和刀都更有勁。',
                       'Myofiber Steak', 'Beef fibre re-laid by the synthesizer. Your fists and blades hit harder.'),
    'regen_broth': ('纳米修复浓汤', '鸡汤配双菇，热乎乎喝下去，伤口会自己慢慢愈合。',
                    '奈米修復濃湯', '雞湯配雙菇，熱呼呼喝下去，傷口會自己慢慢癒合。',
                    'Nano-Repair Broth', 'Chicken broth with two mushrooms. Drink it hot and your wounds close by themselves.'),
    'bulwark_ration': ('堡垒高密口粮', '压缩饼干加猪肉压成的硬砖。嚼完之后，挨打没那么疼了。',
                       '堡壘高密口糧', '壓縮餅乾加豬肉壓成的硬磚。嚼完之後，挨打沒那麼疼了。',
                       'Bulwark Ration', 'Space rations and pork pressed into a brick. Blows hurt a good deal less after it.'),
    'cryo_jelly': ('冰芯冷凝果冻', '月球冰做的果冻，凉意从胃里透到皮肤，岩浆和火焰都烫不着你。',
                   '冰芯冷凝果凍', '月球冰做的果凍，涼意從胃裡透到皮膚，岩漿和火焰都燙不著你。',
                   'Cryo Jelly', 'Jelly set with lunar ice. The chill goes right through you: lava and fire cannot burn you.'),
    'owl_juice': ('夜枭浓缩汁', '胡萝卜和发光浆果榨的浓缩汁。喝完，黑夜和矿洞里都看得一清二楚。',
                  '夜梟濃縮汁', '胡蘿蔔和發光漿果榨的濃縮汁。喝完，黑夜和礦洞裡都看得一清二楚。',
                  'Night Owl Juice', 'Carrot and glow berry concentrate. Night and caves turn as clear as day.'),
    'kelp_sushi': ('深潜海藻卷', '海带裹三文鱼。吃完能在水下自由呼吸，下海、潜进冰下海洋都用得上。',
                   '深潛海藻卷', '海帶裹鮭魚。吃完能在水下自由呼吸，下海、潛進冰下海洋都用得上。',
                   'Deep-Dive Kelp Roll', 'Salmon rolled in kelp. Breathe freely under water, in the sea or under the ice.'),
    'bounce_jelly': ('弹力凝胶糖串', '史莱姆凝胶做的弹弹糖。吃完一跳能蹦上好几格高。',
                     '彈力凝膠糖串', '史萊姆凝膠做的彈彈糖。吃完一跳能蹦上好幾格高。',
                     'Bounce Gel Skewer', 'Springy sweets of slime gel. You jump several blocks high.'),
    'hydrogen_meringue': ('氢泡蛋白酥', '用氢气打发的蛋白酥，轻得像要飘起来。吃完从高处落下也摔不着。空罐会退回机器。',
                          '氫泡蛋白酥', '用氫氣打發的蛋白酥，輕得像要飄起來。吃完從高處落下也摔不著。空罐會退回機器。',
                          'Hydrogen Meringue', 'Meringue whipped with hydrogen, light enough to float. You drift down from any height. The canister comes back empty.'),
    'shield_shake': ('护盾蛋白奶昔', '牛奶可可蛋白奶昔。喝完多出一层额外的护盾血量。空桶会退回机器。',
                     '護盾蛋白奶昔', '牛奶可可蛋白奶昔。喝完多出一層額外的護盾血量。空桶會退回機器。',
                     'Shield Protein Shake', 'Milk, cocoa and egg. Gives you a layer of extra health on top of your own. The bucket comes back.'),
    'vital_pudding': ('强心布丁', '蜂蜜西瓜蛋奶布丁。吃完生命上限多出 4 颗心。',
                      '強心布丁', '蜂蜜西瓜蛋奶布丁。吃完生命上限多出 4 顆心。',
                      'Vitality Pudding', 'Honey, melon and egg custard. Four more hearts of maximum health.'),
    'fortune_cookie': ('幸运签语饼', '掰开有张小纸条：“今日宜开箱、钓鱼。”吃完运气变好。',
                       '幸運籤語餅', '掰開有張小紙條：「今日宜開箱、釣魚。」吃完運氣變好。',
                       'Fortune Cookie', 'The slip inside reads: "A good day for chests and fishing." Luck is with you.'),
    'nutrient_capsule': ('全营养胶囊', '一粒顶一顿：一口吞下，饥饿值瞬间回满，打架时也来得及吃。',
                         '全營養膠囊', '一粒頂一頓：一口吞下，飢餓值瞬間回滿，打架時也來得及吃。',
                         'Nutrient Capsule', 'A whole meal in a pill: swallowed in a moment, it fills your hunger at once, even in a fight.'),
    'captain_feast': ('舰长盛宴', '用托林、木卫二海盐和月球冰调味的整桌大餐。一顿下来，力量、速度、急迫、抗性提升、伤害吸收和生命恢复全都有。',
                      '艦長盛宴', '用托林、木衛二海鹽和月球冰調味的整桌大餐。一頓下來，力量、加速、挖掘加速、抗性、吸收和回復全都有。',
                      "Captain's Feast", 'A whole table seasoned with tholin, Europan salt and lunar ice: strength, speed, haste, resistance, extra health and regeneration at once.'),
}


def lang():
    assert set(TEXT) == set(MEALS)
    zh = {f'block.heliosphere.{MACHINE}': '食品合成机'}
    tw = {f'block.heliosphere.{MACHINE}': '食品合成機'}
    en = {f'block.heliosphere.{MACHINE}': 'Food Synthesizer'}
    for food, (zn, zhint, tn, thint, name, hint) in TEXT.items():
        key = f'item.heliosphere.{food}'
        zh[key], zh[key + '.hint'] = zn, zhint
        tw[key], tw[key + '.hint'] = tn, thint
        en[key], en[key + '.hint'] = name, hint
    merge('zh_cn', zh); merge('zh_tw', tw); merge('en_us', en)


if __name__ == '__main__':
    images = textures()
    models(); data(); lang()
    if '--sheet' in sys.argv: sheet(images, sys.argv[sys.argv.index('--sheet') + 1])
    print(f'food synthesizer assets written: {len(images)} textures, {len(MEALS)} meals')
