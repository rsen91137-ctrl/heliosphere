#!/usr/bin/env python3
"""Resources of the armour sets (overlay 06-weapons): the worn textures (equipment layers), inventory icons,
equipment assets, repair tags and names.

The worn textures are painted into exactly the pixels the vanilla iron armour uses (build/ref/armor, taken from
the Minecraft client jar), face by face: camouflage on cloth, plate carriers with webbing and pouches on the
chest, helmets with their visors, masks and mounts, gloves, knee pads, boots. Icons are the vanilla shapes shaded
from their own light and dark, coloured with the set's pattern.
Usage: extract the references first (see the commit that added this), then run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'
D = 'jars/06-weapons/src/main/resources/data/heliosphere'
REF = 'build/ref/armor'


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


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def noise(shape, cell, seed):
    rng = np.random.default_rng(seed)
    gy, gx = shape[0] // cell + 2, shape[1] // cell + 2
    g = rng.random((gy, gx))
    ys, xs = np.mgrid[0:shape[0], 0:shape[1]] / cell
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    return (g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx) * (1 - fy) + (g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx) * fy


def camo(shape, kind, cols, seed):
    """A camouflage field in the set's colours."""
    cols = [hexc(c) for c in cols]
    out = np.zeros(shape + (3,))
    out[:] = cols[0]
    rng = np.random.default_rng(seed)
    if kind == 'multicam':                      # soft blobs over a tan base, small dark twigs
        for i, (cell, thr) in enumerate([(4, 0.62), (3, 0.66), (2, 0.72)]):
            n = noise(shape, cell, seed + i)
            out[n > thr] = cols[1 + i]
        twig = rng.random(shape) > 0.94
        out[twig] = cols[4]
    elif kind == 'digital':                     # square pixels in 2x2 blocks
        n = noise((shape[0] // 2 + 1, shape[1] // 2 + 1), 2, seed)
        n2 = noise((shape[0] // 2 + 1, shape[1] // 2 + 1), 3, seed + 9)
        block = np.zeros((shape[0] // 2 + 1, shape[1] // 2 + 1, 3)); block[:] = cols[0]
        block[n > 0.6] = cols[1]; block[n2 > 0.65] = cols[2]; block[(n < 0.25)] = cols[3]
        out = np.repeat(np.repeat(block, 2, 0), 2, 1)[:shape[0], :shape[1]]
    elif kind == 'starry':                      # Type 21 "starry sky": scattered irregular dots
        n = noise(shape, 3, seed)
        out[n > 0.6] = cols[1]
        dots = rng.random(shape)
        out[dots > 0.9] = cols[2]
        out[dots < 0.06] = cols[3]
    elif kind == 'ghillie':                     # shaggy strands
        n = noise(shape, 2, seed)
        out[n > 0.55] = cols[1]
        strands = rng.random(shape)
        out[strands > 0.82] = cols[2]
        out[strands < 0.12] = cols[3]
    elif kind == 'quilt':                       # padded panels
        out[:] = cols[0]
        ys, xs = np.mgrid[0:shape[0], 0:shape[1]]
        out[(ys % 3 == 0) | (xs % 4 == 0)] = cols[1]
    else:                                       # plain with a faint grain
        g = (rng.random(shape) - 0.5)[..., None] * 10
        out = out + g
    return out


# ---- the sets -------------------------------------------------------------------------------------------------
SETS = {
    'us_ocp': dict(names=('美军 OCP', '美軍 OCP', 'US Army OCP'),
                   pieces=('ACH 作战头盔', 'IOTV 防弹衣', 'OCP 作战裤', '作战靴'), pieces_tw=('ACH 戰鬥頭盔', 'IOTV 防彈衣', 'OCP 作戰褲', '作戰靴'),
                   pieces_en=('ACH Combat Helmet', 'IOTV Body Armor', 'Combat Trousers', 'Combat Boots'),
                   camo='multicam', cols=['#B6A07A', '#8A7354', '#7D7C52', '#D2C29E', '#4A3C2C'],
                   helmet='#8B7858', vest='#7C6A4C', webbing='#5E5039', glove='#5B4B37', boot='#A28C66', sole='#3A3128',
                   visor=None, mask=None, nvg=True, flag=('#B22234', '#FFFFFF', '#3C3B6E')),
    'ru_ratnik': dict(names=('俄军「战士」', '俄軍「戰士」', 'Russian Ratnik'),
                      pieces=('6B47 头盔', '6B45 防弹衣', 'EMR 迷彩裤', '战术靴'), pieces_tw=('6B47 頭盔', '6B45 防彈衣', 'EMR 迷彩褲', '戰術靴'),
                      pieces_en=('6B47 Helmet', '6B45 Body Armor', 'EMR Trousers', 'Tactical Boots'),
                      camo='digital', cols=['#6C7553', '#53442F', '#8D996C', '#383C2C'],
                      helmet='#5E6648', vest='#57603F', webbing='#40472F', glove='#2E2E2A', boot='#2A2A28', sole='#18181A',
                      visor=None, mask=None, nvg=False, flag=('#FFFFFF', '#0039A6', '#D52B1E')),
    'pla_starry': dict(names=('解放军 21 式星空迷彩', '解放軍 21 式星空迷彩', 'PLA Type 21 Starry'),
                       pieces=('QGF-03 头盔', '21 式防弹背心', '星空迷彩作战裤', '作战靴'), pieces_tw=('QGF-03 頭盔', '21 式防彈背心', '星空迷彩作戰褲', '作戰靴'),
                       pieces_en=('QGF-03 Helmet', 'Type 21 Vest', 'Starry Camo Trousers', 'Combat Boots'),
                       camo='starry', cols=['#56685A', '#3D4E45', '#A3AEA2', '#6E5E46'],
                       helmet='#4F5F4E', vest='#465544', webbing='#33402F', glove='#2C2F2A', boot='#26262A', sole='#141416',
                       visor=None, mask=None, nvg=False, flag=('#DE2910', '#FFDE00', '#DE2910')),
    'swat': dict(names=('特警重型', '特警重型', 'SWAT Heavy'),
                 pieces=('防弹头盔', '重型战术背心', '战术裤', '战术靴'), pieces_tw=('防彈頭盔', '重型戰術背心', '戰術褲', '戰術靴'),
                 pieces_en=('Ballistic Helmet', 'Heavy Tactical Vest', 'Tactical Trousers', 'Tactical Boots'),
                 camo='plain', cols=['#262A31'],
                 helmet='#1E2126', vest='#1A1D22', webbing='#30343C', glove='#141518', boot='#16171A', sole='#0C0C0E',
                 visor='#3A4A58', mask=None, nvg=False, flag=None, letters='#D8D8D8'),
    'eod': dict(names=('排爆', '排爆', 'EOD Bomb'),
                pieces=('排爆头盔', '排爆服上衣', '排爆护腿', '排爆靴'), pieces_tw=('排爆頭盔', '排爆服上衣', '排爆護腿', '排爆靴'),
                pieces_en=('Bomb Suit Helmet', 'Bomb Suit Jacket', 'Bomb Suit Trousers', 'Bomb Suit Boots'),
                camo='quilt', cols=['#5E6546', '#4E5539'],
                helmet='#565D40', vest='#6A7250', webbing='#474D35', glove='#2E2F2A', boot='#3A3B33', sole='#1C1C1A',
                visor='#9DB4C0', mask=None, nvg=False, flag=None),
    'hazmat': dict(names=('防化', '防化', 'Hazmat'),
                   pieces=('防毒面具头罩', '防化服上衣', '防化服裤', '防化胶靴'), pieces_tw=('防毒面具頭罩', '防化服上衣', '防化服褲', '防化膠靴'),
                   pieces_en=('Respirator Hood', 'Hazmat Suit', 'Hazmat Trousers', 'Rubber Boots'),
                   camo='plain', cols=['#E0C23A'],
                   helmet='#E0C23A', vest='#D8BA34', webbing='#9A8526', glove='#1E1E1E', boot='#1E1E1E', sole='#0E0E0E',
                   visor='#B8D0D8', mask='#5A5E62', nvg=False, flag=None),
    'ghillie': dict(names=('吉利伪装', '吉利偽裝', 'Ghillie'),
                    pieces=('吉利兜帽', '吉利伪装衣', '吉利伪装裤', '潜行靴'), pieces_tw=('吉利兜帽', '吉利偽裝衣', '吉利偽裝褲', '潛行靴'),
                    pieces_en=('Ghillie Hood', 'Ghillie Jacket', 'Ghillie Trousers', 'Stalker Boots'),
                    camo='ghillie', cols=['#5B6A36', '#6E5E3A', '#87964E', '#3A4426'],
                    helmet=None, vest=None, webbing='#3E472A', glove='#3A3A2C', boot='#3E3A2C', sole='#1E1C16',
                    visor=None, mask=None, nvg=False, flag=None),
    'exo': dict(names=('XM-7 动力外骨骼', 'XM-7 動力外骨骼', 'XM-7 Exoskeleton'),
                pieces=('外骨骼头盔', '外骨骼躯干', '外骨骼腿部', '外骨骼足部'), pieces_tw=('外骨骼頭盔', '外骨骼軀幹', '外骨骼腿部', '外骨骼足部'),
                pieces_en=('Exo Helmet', 'Exo Torso', 'Exo Legs', 'Exo Feet'),
                camo='plain', cols=['#3A3E45'],
                helmet='#3A3E45', vest='#454A52', webbing='#E07A2A', glove='#2A2D32', boot='#30333A', sole='#16171A',
                visor='#38C8E8', mask=None, nvg=False, flag=None, accent='#E07A2A'),
    'alien': dict(names=('外星合金', '外星合金', 'Alien Alloy'),
                  pieces=('外星合金头盔', '外星合金胸甲', '外星合金护腿', '外星合金战靴'), pieces_tw=('外星合金頭盔', '外星合金胸甲', '外星合金護腿', '外星合金戰靴'),
                  pieces_en=('Alien Alloy Helmet', 'Alien Alloy Chestplate', 'Alien Alloy Leggings', 'Alien Alloy Boots'),
                  camo='plain', cols=['#2C2338'],
                  helmet='#2C2338', vest='#352A44', webbing='#B070FF', glove='#221B2C', boot='#271F31', sole='#140F1A',
                  visor='#C08CFF', mask=None, nvg=False, flag=None, accent='#B070FF'),
}
REPAIR = {'us_ocp': ['nylon', 'steel_plate'], 'ru_ratnik': ['nylon', 'steel_plate'], 'pla_starry': ['nylon', 'steel_plate'],
          'swat': ['nylon', 'stainless_steel_plate'], 'eod': ['carbon_fiber', 'ti64_plate'], 'hazmat': ['plastic'],
          'ghillie': ['nylon'], 'exo': ['carbon_fiber', 'inconel_plate'], 'alien': ['alien_alloy']}


def put(img, x0, y0, x1, y1, c):
    img[y0:y1, x0:x1, :3] = hexc(c) if isinstance(c, str) else c


def shade(img, mask):
    """Light from above: the top faces brighter, the lowest rows of each face a little darker."""
    out = img.copy()
    return out


def box_faces(x, y, w, h, d):
    """UV rectangles of a model box (vanilla layout): top, bottom, right, front, left, back."""
    return {'top': (x + d, y, x + d + w, y + d), 'bottom': (x + d + w, y, x + d + 2 * w, y + d),
            'right': (x, y + d, x + d, y + d + h), 'front': (x + d, y + d, x + d + w, y + d + h),
            'left': (x + d + w, y + d, x + 2 * d + w, y + d + h), 'back': (x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h)}


HEAD, BODY, ARM, LEG = box_faces(0, 0, 8, 8, 8), box_faces(16, 16, 8, 12, 4), box_faces(40, 16, 4, 12, 4), box_faces(0, 16, 4, 12, 4)


def masks(closed_helmet):
    hum = np.zeros((32, 64), bool); leg = np.zeros((32, 64), bool)
    for f, (x0, y0, x1, y1) in HEAD.items():
        if f == 'bottom': continue
        if f == 'front' and not closed_helmet: hum[y0:y0 + 2, x0:x1] = True; continue     # open face: only the brim
        hum[y0:y1, x0:x1] = True
    for faces in (BODY, ARM):
        for f, (x0, y0, x1, y1) in faces.items(): hum[y0:y1, x0:x1] = True
    for f, (x0, y0, x1, y1) in LEG.items():                                                # boots: the lower third
        if f in ('right', 'front', 'left', 'back'): hum[y1 - 5:y1, x0:x1] = True
        if f == 'bottom': hum[y0:y1, x0:x1] = True
        if f in ('right', 'front', 'left', 'back') or f == 'top': leg[y0:y1, x0:x1] = True  # trousers
    for f in ('right', 'front', 'left', 'back'):                                          # waist
        x0, y0, x1, y1 = BODY[f]
        leg[y1 - 4:y1, x0:x1] = True
    x0, y0, x1, y1 = BODY['bottom']; leg[y0:y1, x0:x1] = True
    return hum, leg


def worn(name, s, seed):
    hum_mask, leg_mask = masks(bool(s['visor'] or s['mask']))
    H, W = hum_mask.shape
    cam = camo((H, W), s['camo'], s['cols'], seed)
    hum = np.zeros((H, W, 4)); hum[..., :3] = cam
    leg = np.zeros((H, W, 4)); leg[..., :3] = camo((H, W), s['camo'], s['cols'], seed + 50)

    # ---- helmet (head box, 0..32 x 0..16): faces top 8..16/0..8, right 0..8/8..16, front 8..16/8..16, left 16..24, back 24..32
    if s['helmet']:
        put(hum, 0, 0, 32, 16, s['helmet'])
        g = (np.random.default_rng(seed + 3).random((16, 32)) - 0.5) * 12
        hum[0:16, 0:32, :3] += g[..., None]
        if s['camo'] in ('multicam', 'digital', 'starry'):          # a cloth cover in the pattern
            hum[0:13, 0:32, :3] = hum[0:13, 0:32, :3] * 0.35 + cam[0:13, 0:32] * 0.65
        put(hum, 0, 13, 32, 14, s.get('webbing'))                    # rim / band
    if s['camo'] == 'ghillie':
        hum[0:16, 0:32, :3] = cam[0:16, 0:32]
    if s['nvg']:
        put(hum, 11, 8, 13, 10, '#1A1A1C'); put(hum, 10, 10, 14, 11, '#2A2A2E')   # NVG mount on the front
    if s['visor'] and s['mask'] is None:
        put(hum, 9, 10, 15, 14, s['visor']); put(hum, 9, 10, 15, 11, '#E8F0F4' if s['visor'] != '#3A4A58' else '#5A6A78')
    if s['mask']:
        put(hum, 9, 9, 15, 12, s['visor'])                          # clear eye piece
        put(hum, 10, 12, 14, 15, s['mask']); put(hum, 9, 13, 10, 15, '#2A2C2E'); put(hum, 14, 13, 15, 15, '#2A2C2E')   # filters
    if s.get('accent') and s['helmet']:
        put(hum, 8, 8, 16, 9, s['accent'])

    # ---- chest (body 16..40 x 16..32) and arms (40..56 x 16..32)
    if s['vest']:
        put(hum, 20, 20, 28, 31, s['vest']); put(hum, 32, 20, 40, 31, s['vest'])        # front and back plates
        put(hum, 16, 20, 20, 30, s['vest']); put(hum, 28, 20, 32, 30, s['vest'])        # sides
        put(hum, 20, 16, 28, 20, s['vest'])                                              # shoulders (top)
        for y in (23, 26, 29):                                                           # MOLLE webbing rows
            put(hum, 20, y, 28, y + 1, s['webbing']); put(hum, 32, y, 40, y + 1, s['webbing'])
        if s['camo'] not in ('plain', 'quilt'):
            put(hum, 21, 27, 23, 31, s['webbing']); put(hum, 23, 27, 25, 31, s['webbing']); put(hum, 25, 27, 27, 31, s['webbing'])   # magazine pouches
            put(hum, 21, 27, 23, 28, s['vest']); put(hum, 23, 27, 25, 28, s['vest']); put(hum, 25, 27, 27, 28, s['vest'])
        if s.get('letters'):                                                             # a light band with block letters across the back
            put(hum, 33, 21, 39, 23, s['letters'])
            for x in (34, 36, 38): put(hum, x, 21, x + 1, 23, s['vest'])
        if s['camo'] == 'quilt':
            put(hum, 21, 21, 27, 27, '#7A825E')                                          # the big front plate of the bomb suit
    if s.get('accent'):
        for x0 in (20, 32): put(hum, x0 + 3, 20, x0 + 5, 31, s['accent'])               # power lines
        put(hum, 44, 22, 48, 23, s['accent'])
    put(hum, 40, 29, 56, 32, s['glove'])                                                  # gloves
    if s['flag']:
        put(hum, 40, 21, 44, 22, s['flag'][0]); put(hum, 40, 22, 44, 23, s['flag'][1]); put(hum, 40, 23, 44, 24, s['flag'][2])  # shoulder patch
    # ---- boots: the bottom of the leg box (0..16 x 26..32)
    put(hum, 0, 25, 16, 32, s['boot'])
    put(hum, 0, 31, 16, 32, s['sole'])
    put(hum, 4, 25, 8, 26, s['sole'])                                                     # laces / cuff
    put(hum, 8, 20, 12, 24, s['sole'])                                                    # sole underside

    # ---- leggings layer: trousers on the legs, belt on the waist
    put(leg, 16, 29, 40, 32, s['webbing'])                                                # belt
    put(leg, 23, 29, 25, 30, '#B0A070')                                                   # buckle
    if s['camo'] in ('multicam', 'digital', 'starry', 'plain'):
        put(leg, 4, 23, 8, 25, s['webbing'])                                              # knee pad
    if s['camo'] == 'quilt':
        put(leg, 3, 21, 9, 27, '#7A825E')                                                 # leg plates
    if s.get('accent'):
        put(leg, 5, 17, 7, 31, s['accent'])

    # lighting: a touch brighter on top faces, darker on the lowest row of each face
    for img in (hum, leg):
        img[..., :3] = np.clip(img[..., :3], 0, 255)
    hum[..., 3] = np.where(hum_mask, 255, 0)
    leg[..., 3] = np.where(leg_mask, 255, 0)
    return hum.astype(np.uint8), leg.astype(np.uint8)


def icon(piece, s, seed):
    ref = np.asarray(Image.open(f'{REF}/textures_item_iron_{piece}.png').convert('RGBA')).astype(float)
    lum = ref[..., :3].mean(axis=2) / 255
    a = ref[..., 3] > 0
    cam = camo((16, 16), s['camo'], s['cols'], seed)
    base = {'helmet': s['helmet'] or s['cols'][0], 'chestplate': s['vest'] or s['cols'][0], 'leggings': None, 'boots': s['boot']}[piece]
    col = cam if base is None or (piece != 'boots' and s['camo'] in ('multicam', 'digital', 'starry', 'ghillie')) else np.broadcast_to(hexc(base), (16, 16, 3)).copy()
    k = 0.45 + 0.9 * lum
    out = np.zeros((16, 16, 4))
    out[..., :3] = np.clip(col * k[..., None], 0, 255)
    if piece == 'helmet' and s['visor']:
        out[7:9, 4:12, :3] = hexc(s['visor'])
    if piece == 'chestplate' and s.get('accent'):
        out[6:12, 7:9, :3] = hexc(s['accent'])
    if piece == 'chestplate' and s['camo'] in ('multicam', 'digital', 'starry'):
        out[9:11, 4:12, :3] = hexc(s['webbing'])
    out[..., 3] = np.where(a, 255, 0)
    # dark outline where the reference is darkest
    edge = a & (lum < 0.25)
    out[edge, :3] = np.clip(col[edge] * 0.3, 0, 255)
    return out.astype(np.uint8)


def main():
    os.makedirs(f'{A}/textures/entity/equipment/humanoid', exist_ok=True)
    os.makedirs(f'{A}/textures/entity/equipment/humanoid_leggings', exist_ok=True)
    os.makedirs(f'{A}/textures/item', exist_ok=True)
    zh, tw, en = {}, {}, {}
    sheet = Image.new('RGBA', (len(SETS) * 136, 200), (60, 60, 60, 255))
    for i, (name, s) in enumerate(SETS.items()):
        hum, leg = worn(name, s, 100 + i * 17)
        Image.fromarray(hum, 'RGBA').save(f'{A}/textures/entity/equipment/humanoid/{name}.png')
        Image.fromarray(leg, 'RGBA').save(f'{A}/textures/entity/equipment/humanoid_leggings/{name}.png')
        write(f'{A}/equipment/{name}.json', {'layers': {'humanoid': [{'texture': f'heliosphere:{name}'}],
                                                         'humanoid_leggings': [{'texture': f'heliosphere:{name}'}]}})
        write(f'{D}/tags/item/repairs_{name}_armor.json', {'replace': False, 'values': [f'heliosphere:{x}' for x in REPAIR[name]]})
        sheet.paste(Image.fromarray(hum, 'RGBA').resize((128, 64), Image.NEAREST), (i * 136 + 4, 4))
        sheet.paste(Image.fromarray(leg, 'RGBA').resize((128, 64), Image.NEAREST), (i * 136 + 4, 72))
        for j, piece in enumerate(['helmet', 'chestplate', 'leggings', 'boots']):
            item = f'{name}_{piece}'
            ic = icon(piece, s, 300 + i * 13 + j)
            Image.fromarray(ic, 'RGBA').save(f'{A}/textures/item/{item}.png')
            sheet.paste(Image.fromarray(ic, 'RGBA').resize((32, 32), Image.NEAREST), (i * 136 + 4 + j * 33, 150))
            write(f'{A}/models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{item}'}})
            write(f'{A}/items/{item}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{item}'}})
            zh[f'item.heliosphere.{item}'] = f'{s["names"][0]} {s["pieces"][j]}'
            tw[f'item.heliosphere.{item}'] = f'{s["names"][1]} {s["pieces_tw"][j]}'
            en[f'item.heliosphere.{item}'] = f'{s["names"][2]} {s["pieces_en"][j]}'
    merge('zh_cn', zh); merge('zh_tw', tw); merge('en_us', en)
    os.makedirs('build', exist_ok=True)
    sheet.save('build/armor_sheet.png')
    print(len(SETS), 'sets')


if __name__ == '__main__':
    main()
