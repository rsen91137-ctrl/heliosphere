#!/usr/bin/env python3
"""Polish pass over the weakest overlay textures: redraws them as hand-placed 16x16 pixel art.

The first-generation icons for the power blocks, the habitat field, the machine nuke silo, the turret upgrade
kits, the rocket section kits and a few single items were flat fills with speckle noise. This script repaints
them in place (same file name, same size, same transparency behaviour) with small hue-shifted colour ramps,
light from the top-left, bevelled casings, dark item outlines and deliberate details (rivets, louvres, lenses,
hazard stripes, status bars). Every pixel is fixed: the only "random" element, the faint brushed streaks on a
casing, is seeded from the texture name, so two runs give byte-identical files.

Run from anywhere:  python3 tools/gen/texture_polish.py        (run it after the older *_assets.py generators,
which would otherwise write the old look back).
"""
import os
import random
import zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = 'src/main/resources/assets/heliosphere/textures'
N = 16
WRITTEN = []


# ------------------------------------------------------------------------------------------------ colour helpers

def rgba(h):
    """'#rrggbb' or '#rrggbbaa' -> (r, g, b, a)."""
    h = h.lstrip('#')
    v = [int(h[i:i + 2], 16) for i in range(0, len(h), 2)]
    return tuple(v) if len(v) == 4 else (v[0], v[1], v[2], 255)


def mix(a, b, t):
    a, b = rgba(a), rgba(b)
    return '#%02x%02x%02x' % tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def scale(h, f):
    """Brighten (f > 1, towards a warm white) or darken (f < 1, towards a cool black) a colour."""
    return mix(h, '#fff6e0', min(1.0, f - 1.0)) if f >= 1 else mix(h, '#0a0c1c', 1.0 - f)


# Casing ramps, darkest to lightest; shadows lean blue/violet, highlights lean warm.
WHITE = ['#687080', '#98a1af', '#c2c9d2', '#dce1e7', '#f3f5f8']
PALE = ['#5c6472', '#8c95a3', '#b6bdc7', '#d2d7de', '#eceff3']
STEEL = ['#2b2e38', '#444955', '#626875', '#848b99', '#aab1bd']
LEAD = ['#27272f', '#3d3e4a', '#575a68', '#777b8b', '#9da2b1']
DARK = ['#131419', '#202229', '#30333c', '#454954', '#5f646f']
GREEN = ['#12302a', '#1f4532', '#33603e', '#4d7d4d', '#78a268']
BLUE = ['#1c2244', '#283a60', '#38567f', '#4f76a0', '#79a0c2']
FIELD = ['#252a38', '#3b4354', '#566172', '#788496', '#a1adbc']

# Shared detail colours (one letter each; digits 0-4 always mean the casing ramp of the texture being drawn).
COMMON = {
    'k': '#0d0f14', 'K': '#1b1e27',                                               # recess / screen black
    'x': '#23262d', 'd': '#3d424c', 's': '#5c626e', 'f': '#818896', 'v': '#a9b0bc',  # bare steel, dark -> light
    'w': '#f2f5f8',
    'u': '#a8741a', 'y': '#e8b82a', 'Y': '#ffe06a',                               # hazard yellow
    'e': '#7d1a22', 'r': '#cf3328', 'R': '#ff7b5a',                               # warning red
    'n': '#14234a', 'N': '#213d73', 'b': '#2f6ccc', 'B': '#74b4f6',               # coolant blue
    't': '#17607c', 'c': '#4fd0ee', 'C': '#c8f7ff',                               # field cyan
    'q': '#6e3a22', 'p': '#b8683a', 'P': '#e59a62',                               # copper
    'g': '#5cf07c', 'G': '#1d3527',                                               # status light: lit / dark
}


# ------------------------------------------------------------------------------------------------ drawing helpers

def blank():
    return np.zeros((N, N, 4), dtype=np.uint8)


def check(name, rows):
    assert len(rows) == N and all(len(r) == N for r in rows), (name, [len(r) for r in rows])


def stamp(img, name, rows, pal):
    """Paint an ASCII sprite; '.' leaves the pixel as it is."""
    check(name, rows)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                assert ch in pal, (name, y, x, ch)
                img[y, x] = rgba(pal[ch])


def casing(ramp, seed, rivets=(), streaks=True):
    """A machine side: bevelled plate (lit top/left, shaded bottom/right), faint brushed streaks, bolts."""
    img = blank()
    img[:, :] = rgba(ramp[2])
    if streaks:
        rnd = random.Random(zlib.crc32(seed.encode()))
        for tone, count in ((mix(ramp[2], ramp[3], 0.45), 6), (mix(ramp[2], ramp[1], 0.4), 5)):
            for _ in range(count):
                y, x, n = rnd.randrange(1, 15), rnd.randrange(1, 13), rnd.randrange(2, 6)
                img[y, x:min(15, x + n)] = rgba(tone)
    img[0, :] = img[:, 0] = rgba(ramp[3])
    img[15, :] = img[:, 15] = rgba(ramp[1])
    img[0, 0] = rgba(ramp[4])
    img[15, 15] = rgba(ramp[0])
    img[0, 15] = img[15, 0] = rgba(ramp[2])
    for x, y in rivets:
        img[y, x] = rgba(ramp[4])
        img[y + 1, x] = rgba(ramp[0])
    return img


def fins(ramp):
    """Radiator fins: alternating lit and shaded columns with bright tips and dark feet."""
    img = casing(ramp, '', streaks=False)
    for x in range(1, 15):
        lit = x % 2 == 1
        img[1:15, x] = rgba(ramp[3] if lit else ramp[1])
        img[1, x] = rgba(ramp[4] if lit else ramp[2])
        img[14, x] = rgba(ramp[2] if lit else ramp[0])
    return img


def relief(img, rows, ramp, inset='', raised=''):
    """Shade the casing around details: a rim of shadow and light round recesses, a cast shadow under bosses."""
    def has(x, y, chars):
        return 0 <= x < N and 0 <= y < N and rows[y][x] in chars
    for y in range(1, 15):
        for x in range(1, 15):
            if rows[y][x] != '.':
                continue
            if inset and (has(x + 1, y, inset) or has(x, y + 1, inset)):
                img[y, x] = rgba(ramp[1])
            elif inset and (has(x - 1, y, inset) or has(x, y - 1, inset)):
                img[y, x] = rgba(ramp[4])
            elif raised and (has(x - 1, y, raised) or has(x, y - 1, raised)):
                img[y, x] = rgba(ramp[1])


def block(path, ramp, rows, pal=None, rivets=(), inset='', raised='', base=None, streaks=True):
    """One opaque block face: casing + ASCII details + relief shading."""
    name = os.path.basename(path)
    seed = name[:-3] if name.endswith('_on') else name
    img = base.copy() if base is not None else casing(ramp, seed, rivets, streaks)
    colours = dict(COMMON)
    colours.update({str(i): ramp[i] for i in range(5)})
    colours.update(pal or {})
    stamp(img, name, rows, colours)
    relief(img, rows, ramp, inset, raised)
    assert img[:, :, 3].min() == 255, name
    save(path, img)


def item(path, rows, pal):
    img = blank()
    stamp(img, os.path.basename(path), rows, pal)
    save(path, img)


def save(path, img):
    """Overwrite an existing texture; refuses to create a file or to change its size."""
    full = os.path.join(ROOT, 'jars', path.replace('@', TEX) + '.png')
    assert os.path.isfile(full), full
    with Image.open(full) as old:
        assert old.size == (N, N), (full, old.size)
    Image.fromarray(img, 'RGBA').save(full)
    WRITTEN.append(os.path.relpath(full, ROOT))


def lit(rows, n):
    """Light the first n segments of the status bar (the row of 'G')."""
    out = []
    for r in rows:
        if 'G' in r:
            r = r.replace('G', 'g', n)
        out.append(r)
    return out


CORNERS = ((1, 1), (14, 1), (1, 13), (14, 13))    # one bolt in each corner of a casing
GAUGE = '....GGGGGGGG....'
E = '................'


# ------------------------------------------------------------------------------------------------ power blocks

def wind_turbine():
    p = '05-industry/@/block/power/wind_turbine'
    rotor_idle = ['......kkkk......', '....kkk43xxx....', '...kkxx43xxxx...', '...kxxx43xxxx...', '..kkxxx43xxxxx..',
                  '..kxxxxfsxxxxx..', '..kxxxxsdxxxxx..', '..kxx43xx43xxx..', '...x43xxxx43x...', '...43xxxxxx43...',
                  '....xxxxxxxx....', '......xxxx......']
    rotor_spin = ['......kkkk......', '....kkkxxxxx....', '...43xxxxxx43...', '...k43xxxx43x...', '..kkx43xx43xxx..',
                  '..kxxxxfsxxxxx..', '..kxxxxsdxxxxx..', '..kxxxx43xxxxx..', '...xxxx43xxxx...', '...xxxx43xxxx...',
                  '....xxx43xxx....', '......xxxx......']
    for rotor, suffix, n in ((rotor_idle, '', 0), (rotor_spin, '_on', 6)):
        rows = lit([E] + rotor + [E, GAUGE, E], n)
        block(p + '_front' + suffix, WHITE, rows, inset='kxfsd43gG')
    block(p + '_side', WHITE, [
        E, E, E,
        '..0.0.0..0.0.0..', '..1.1.1..1.1.1..', '..1.1.1..1.1.1..', '..1.1.1..1.1.1..', '..1.1.1..1.1.1..',
        '..1.1.1..1.1.1..', '..1.1.1..1.1.1..',
        E,
        '.RrrrrrrrrrrrrR.', '.reeeeeeeeeeeee.',
        E, E, E])
    block(p + '_top', WHITE, [
        E, E, E,
        '...4444444444...', '...4........1...',
        '...4..xxxx..1...', '...4.xrRRrx.1...', '...4.xRwRrx.1...', '...4.xrRrex.1...', '...4.xrreex.1...',
        '...4..xxxx..1...',
        '...4........1...', '...1111111111...',
        E, E, E], raised='x', rivets=CORNERS)


def fission_reactor():
    p = '05-industry/@/block/power/fission_reactor'
    rows = [E,
            '.yykkyykkyykkyy.', '.ykkyykkyykkyyk.',
            E,
            '..kkkkkkkkkkkk..',
            '..kIittIittIik..', '..kIittIittIik..', '..kIittIittIik..', '..kIittIittIik..', '..kIittIittIik..',
            '..kIittIittIik..',
            '..kkkkkkkkkkkk..',
            E, GAUGE, E, E]
    cold = {'I': '#2c4f84', 'i': '#1b3158', 't': '#0e1830'}
    hot = {'I': '#e2fcff', 'i': '#79e2ff', 't': '#1f5fd0'}
    block(p + '_front', PALE, rows, cold, inset='kgG')
    block(p + '_front_on', PALE, lit(rows, 7), hot, inset='kgG')
    block(p + '_side', PALE, [
        E,
        '...Bbn1...Bbn1..', '...Bbn1...Bbn1..',
        '..vfsdx..vfsdx..',
        '...Bbn1...Bbn1..', '...Bbn1...Bbn1..',
        '...BbnvvvvBbn1..', '...BbnsrrsBbn1..', '...BbnddddBbn1..',
        '...Bbn1111Bbn1..', '...Bbn1...Bbn1..', '...Bbn1...Bbn1..',
        '..vfsdx..vfsdx..',
        '...Bbn1...Bbn1..', '...Bbn1...Bbn1..',
        E])
    block(p + '_top', PALE, [
        E, E,
        '......ffff......', '....ffsssssd....', '...fssyyyyssd...', '...fskkyykksd...', '..fsykkyykkysd..',
        '..fsyyykkyyysd..', '..fsyyykkyyysd..', '..fsyyyyyyyysd..', '...ssyykkyysd...', '...ssskkkksdd...',
        '....dddddddd....', '......dddd......',
        E, E], raised='fsdyk')


def geothermal_generator():
    p = '05-industry/@/block/power/geothermal_generator'
    rows = [E, E,
            '...kkkkkkkkkk...',
            '...kPPPPPPPpk...', '...kppppppqqk...', '...kjjjjjjjjk...',
            '...kPPPPPPPpk...', '...kppppppqqk...', '...kjjjjjjjjk...',
            '...kPPPPPPPpk...', '...kppppppqqk...',
            '...kkkkkkkkkk...',
            E, GAUGE, E, E]
    cold = {'P': '#7c5444', 'p': '#58382e', 'q': '#3b2520', 'j': '#0d0f14'}
    hot = {'P': '#ffd676', 'p': '#ff8f2c', 'q': '#c9521c', 'j': '#4a1710'}
    block(p + '_front', STEEL, rows, cold, inset='kgG', rivets=((1, 1), (14, 1)))
    block(p + '_front_on', STEEL, lit(rows, 7), hot, inset='kgG', rivets=((1, 1), (14, 1)))
    block(p + '_side', STEEL, [
        E,
        '......Pppq1.....', '.....vffsdx.....', '......Pppq1.....', '......Pppq1.....',
        '..0000Pppq0000..', '..3333Pppq1333..',
        '......Pppq1.....', '......Pppq1.....',
        '..0000Pppq0000..', '..3333Pppq1333..',
        '......Pppq1.....', '......Pppq1.....', '.....vffsdx.....', '......Pppq1.....',
        E])
    block(p + '_top', STEEL, [
        E, E,
        '......vvff......', '....vvffffss....', '...vffkfdkssd...', '...vfkqfdqksd...', '..vfkqpfdpqksd..',
        '..vfffffffffsd..', '..vfddddddddsd..', '..vfkqpfdpqksd..', '...fskqfdqkdd...', '...fsskfdkddx...',
        '....ssdddddx....', '......ddxx......',
        E, E], raised='vfsdxk')


def cell_front(path, ramp, cell, cap, pal_off, pal_on, charge):
    """Battery fronts: a full-width bay of upright cells behind a dark frame, charge bar underneath."""
    reps = 12 // len(cell)
    body = '.k' + cell * reps + 'k.'
    shine = '.k' + ('H' + cell[1:]) * reps + 'k.'
    band = '.k' + ('D' * len(cell)) * reps + 'k.'
    rows = [E, E,
            '.kkkkkkkkkkkkkk.',
            '.k' + cap * reps + 'k.',
            shine, shine, body, body, band, body, body,
            '.kkkkkkkkkkkkkk.',
            E, GAUGE, E, E]
    block(path + '_front', ramp, rows, pal_off)
    block(path + '_front_on', ramp, lit(rows, charge), pal_on)


def vent_side(path, ramp, last):
    """Battery sides: three cooling louvres and a block-specific bottom row (fourth louvre, handle or label)."""
    louvre = ['...0000000000...', '...3333333333...', E]
    block(path + '_side', ramp, [E, E, E] + louvre * 3 + last + [E, E], rivets=CORNERS)


def terminal_top(path, ramp):
    """Battery tops: a red and a black terminal post on a split lid, polarity marks engraved below."""
    block(path + '_top', ramp, [
        E, E,
        '.......13.......',
        '...xxx.13.xxx...', '..xRRrx13xfddx..', '..xRrex13xdxxx..', '..xreex13xdxkx..', '...xxx.13.xxx...',
        '.......13.......', '.......13.......',
        '....0..13.......', '...000.13.000...', '....0..13.......',
        '.......13.......',
        E, E], raised='x', rivets=CORNERS)


def batteries():
    p = '05-industry/@/block/power/'
    cell_front(p + 'grid_storage_array', GREEN, 'LMMD', 'vffd',
               {'H': '#9ee0ab', 'L': '#74c487', 'M': '#58a86c', 'D': '#2f7046'},
               {'H': '#e6ffe4', 'L': '#a8f8b0', 'M': '#7ce890', 'D': '#3fae5c'}, 6)
    vent_side(p + 'grid_storage_array', GREEN, ['...0000000000...', '...3333333333...'])
    terminal_top(p + 'grid_storage_array', GREEN)

    cell_front(p + 'lithium_battery_cabinet', BLUE, 'LMD', 'vfd',
               {'H': '#a4e2f2', 'L': '#73c3df', 'M': '#52a5c9', 'D': '#347ba3'},
               {'H': '#f0fdff', 'L': '#b6f3ff', 'M': '#7edffa', 'D': '#48b4dc'}, 6)
    vent_side(p + 'lithium_battery_cabinet', BLUE, ['....kkkkkkkk....', '....4vvvvvv4....'])
    terminal_top(p + 'lithium_battery_cabinet', BLUE)

    cell_front(p + 'supercapacitor_bank', DARK, 'LD', 'vd',
               {'H': '#f2cf5a', 'L': '#d9a82c', 'D': '#946a18'},
               {'H': '#fff3a8', 'L': '#ffd94a', 'D': '#d99a22'}, 7)
    vent_side(p + 'supercapacitor_bank', DARK, ['......ykyy......', '......yyky......'])
    terminal_top(p + 'supercapacitor_bank', DARK)


def hydrogen_fuel_cell():
    p = '05-industry/@/block/power/hydrogen_fuel_cell'
    rows = [E,
            '......bBBb......',
            E,
            '..vskkkkkkkksd..',
            '..fsMmmmmmmmsd..', '..fsjjjjjjjjsd..', '..fsMmmmmmmmsd..', '..fsjjjjjjjjsd..', '..fsMmmmmmmmsd..',
            '..fsjjjjjjjjsd..', '..fsMmmmmmmmsd..',
            '..fskkkkkkkkdx..',
            E, GAUGE, E, E]
    idle = {'M': '#3f76c8', 'm': '#24509a', 'j': '#132a57'}
    live = {'M': '#b4deff', 'm': '#3f8ef2', 'j': '#1b469c'}
    block(p + '_front', WHITE, rows, idle, inset='kgGbB', raised='vfsdx')
    block(p + '_front_on', WHITE, lit(rows, 5), live, inset='kgGbB', raised='vfsdx')
    block(p + '_side', WHITE, [
        E, E, E, E,
        '..kkkkkkkkkkkk..',
        '..kkvBBvvvvvkk..', '..kwwCCwwwwwvk..', '..kvvBBvvvvvrk..', '..kffbbfffffsk..', '..kssbbsssssdk..',
        '..kkdNNdddddkk..',
        '..kkkkkkkkkkkk..',
        E, E, E, E], inset='k', rivets=CORNERS)
    block(p + '_top', WHITE, [
        E, E, E, E, E,
        '..kkkkkkkkkkkk..',
        '..kvBvvBBvvBvk..', '..kwCwwCCwwCwk..', '..kfbffbbffbfk..', '..ksNssNNssNsk..',
        '..kkkkkkkkkkkk..',
        E, E, E, E, E], inset='k', rivets=CORNERS)


def rtg():
    p = '05-industry/@/block/power/rtg'
    base = fins(LEAD)
    rows = [E, E,
            '...xxxxxxxxxx...',
            '...xffYYyyssx...', '...xfkkYykksx...', '...xYkkyykkyx...', '...xYyykkyyyx...', '...xyyykkyyyx...',
            '...xyyyyyyyux...', '...xsyykkyudx...', '...xsdkkkkddx...',
            '...xxxxxxxxxx...',
            '....xxxxxxxx....', '....xGGGGGGx....', '....xxxxxxxx....',
            E]
    block(p + '_front', LEAD, rows, base=base)
    block(p + '_front_on', LEAD, lit(rows, 6), base=base)
    block(p + '_side', LEAD, [
        E, E, E, E, E, E,
        '.xxxxxxxxxxxxxx.', '.vvfvvvvvvvvfvv.', '.ssxssssssssxss.', '.xxxxxxxxxxxxxx.',
        E, E, E, E, E, E], base=base)
    block(p + '_top', LEAD, [
        E, E, E,
        '.....xxxxxx.....', '....xvffffsx....', '...xvfsxxsssx...', '...xfsxqrqxsx...', '...xfxqrYrqxx...',
        '...xfxrYRrqxx...', '...xssxqrqxdx...', '...xsssxxsddx...', '....xssdddx.....', '.....xxxxxx.....',
        E, E, E], base=base)


# ------------------------------------------------------------------------------------------------ habitat field

def habitat():
    p = '05-industry/@/block/habitat_field_emitter'
    lattice = ['IiiiIiiiIi', 'iIiIiIiIiI', 'iiIiiiIiii', 'iIiIiIiIiI', 'IiiiIiiiIi', 'iIiIiIiIiI', 'iiIiiiIiii']
    side = [E, E, '..kkkkkkkkkkkk..'] + ['..k' + r + 'k..' for r in lattice] + [
        '..kkkkkkkkkkkk..', E, '.yykkyykkyykkyy.', '.ukkuukkuukkuuk.', E, E]
    block(p + '_side', FIELD, side, {'I': '#27406e', 'i': '#131f3d'}, inset='k')
    block(p + '_side_on', FIELD, side, {'I': '#d4fbff', 'i': '#33a8de'}, inset='k')
    top = [E, E,
           '......IIII......', '....IIIIIIII....', '...IIIiiiiIIi...', '...IIjjjjjjii...', '..IIjjjLLjjjii..',
           '..IIjjLHHljjii..', '..IIjjLHlljjii..', '..IIjjjlljjjii..', '...IIjjjjjjii...', '...Iiijjjjiii...',
           '....iiiiiiii....', '......iiii......',
           E, E]
    block(p + '_top', FIELD, top, {'I': '#2c4a80', 'i': '#172a52', 'j': '#0d0f14',
                                   'L': '#818896', 'H': '#a9b0bc', 'l': '#5c626e'}, raised='Ii',
          rivets=CORNERS)
    block(p + '_top_on', FIELD, top, {'I': '#9defff', 'i': '#3fb6ea', 'j': '#12456f',
                                      'L': '#c8f7ff', 'H': '#ffffff', 'l': '#7fe2fa'}, raised='Ii',
          rivets=CORNERS)

    # The panel is see-through on purpose: the original's three alpha steps (frame 200, lines 105, field 38) stay.
    img = blank()
    for y in range(N):
        for x in range(N):
            edge = x in (0, 15) or y in (0, 15)
            node = (x + y) % 8 == 7 and (x - y) % 8 == 0
            line = (x + y) % 8 == 7 or (x - y) % 8 == 0
            if edge:
                img[y, x] = (200, 245, 255, 200)
            elif node or (line and (x in (1, 14) or y in (1, 14))):
                img[y, x] = (214, 248, 255, 105)
            elif line:
                img[y, x] = (170, 230, 255, 105)
            else:
                img[y, x] = (120, 200, 255, 38)
    img[0, 0] = img[0, 15] = img[15, 0] = img[15, 15] = (234, 252, 255, 200)
    save('05-industry/@/block/habitat_field_panel', img)


# ------------------------------------------------------------------------------------------------ machine nuke silo

def nuke_silo():
    p = '07-hostiles/@/block/machine_nuke_silo'
    img = casing(DARK, 'silo_top', streaks=False)
    for y in range(N):
        for x in range(N):
            if x < 2 or x > 13 or y < 2 or y > 13:
                yellow = ((x + y) // 2) % 2 == 0
                lit_edge = x == 0 or y == 0
                img[y, x] = rgba(('#ffe06a' if lit_edge else '#e8b82a') if yellow else '#14151a')
    img[15, :, :3] = (img[15, :, :3] * 0.72).astype(np.uint8)
    img[:, 15, :3] = (img[:, 15, :3] * 0.72).astype(np.uint8)
    doors = [E, E,
             '..xxxxxxxxxxxx..',
             '..xvffffvffffx..', '..xfsssdfsssdx..', '..xfsxsdfsxsdx..', '..xfsssxxsssdx..', '..xfssxrRxssdx..',
             '..xfssxreexsdx..', '..xfsssxxsssdx..', '..xfsxsdfsxsdx..', '..xfsssdfsssdx..', '..xsddddsddddx..',
             '..xxxxxxxxxxxx..',
             E, E]
    stamp(img, 'silo_top', doors, COMMON)
    save(p + '_top', img)
    block(p + '_side', DARK, [
        E,
        '.yykkyykkyykkyy.', '.ukkuukkuukkuuk.',
        E, E,
        '...xxxxxxxxxx...',
        '...xRRxRRxRRx...', '...xrrxrrxrrx...', '...xrrxrrxrrx...', '...xeexeexeex...',
        '...xxxxxxxxxx...',
        E,
        '..000000000000..', '..333333333333..',
        E, E], raised='x', rivets=((1, 5), (14, 5), (1, 9), (14, 9)))
    block(p + '_bottom', DARK, [
        E, E, E,
        '...4444..4444...', '...4...1.4...1..', '...4...1.4...1..', '...4...1.4...1..', '....1111..1111..',
        E,
        '...4444..4444...', '...4...1.4...1..', '...4...1.4...1..', '...4...1.4...1..', '....1111..1111..',
        E, E], rivets=CORNERS)


# ------------------------------------------------------------------------------------------------ items

OUT = '#17161c'


def turret_kits():
    """Upgrade boards: the chip glows in the tier colour and the bottom row lights one pip per tier."""
    tiers = ['#3fa0ff', '#58e878', '#f2c230', '#f0642c', '#b65cff']
    for i, tier in enumerate(tiers, 1):
        pips = 'G'.join('HT' if n < i else 'dd' for n in range(5))
        rows = [E, E,
                '.oooooooooooooo.',
                'oMLLLLLLLLLLLLMo',
                'oLyGyGyGGyGyGyDo', 'oLuGuGuGGuGuGuDo',
                'oLGGGKKKKKKGGGDo', 'oLGtGkHTTTkGtGDo', 'oLttGkTTTSkGttDo', 'oLGGGkkkkkkGGGDo',
                'oLGGGGGGGGGGGGDo',
                'o' + pips + 'o',
                'oDDDDDDDDDDDDDDo',
                '.oooooooooooooo.',
                E, E]
        item('06-weapons/@/item/turret_kit_mk%d' % i, rows, {
            'o': '#0b1f15', 'D': '#1c4a2c', 'G': '#2a6a3c', 'M': '#3c8a50', 'L': '#57a866', 't': '#43915a',
            'd': '#123522', 'y': '#f0c440', 'u': '#a8741a', 'k': '#0d0f14', 'K': '#30343f',
            'T': tier, 'H': scale(tier, 1.55), 'S': scale(tier, 0.7)})


def portable_sentry():
    item('06-weapons/@/item/portable_sentry', [
        E,
        '..oooooooo......',
        '.oBbBbBbBbo.....',
        '.oooooooooo.....',
        '.oHTTTTTTTo.....',
        '.oTkkkkTTToooooo',
        '.oTkCckTTtxvfffo',
        '.oTkkkkTttxdssso',
        '.oTttttttuoooooo',
        '.otttttuuuo.....',
        '.oooooooooo.....',
        '....oKKgKo......',
        '...oooooooo.....',
        '...dx.dx.dx.....',
        '..dx..dx..dx....',
        '.oo...oo...oo...',
    ], {'o': OUT, 'B': '#4f7fd6', 'b': '#243e86', 'H': '#ecd8a4', 'T': '#c9ad72', 't': '#9c8150', 'u': '#6d5834',
        'k': '#0d0f14', 'K': '#3a3e49', 'C': '#d2fbff', 'c': '#58dcf5', 'g': '#5cf07c',
        'v': '#b4bbc6', 'f': '#8d94a1', 's': '#666c78', 'd': '#434852', 'x': '#23262d'})


def bio_capsule():
    """Glass stays see-through like the original (its body was alpha 150-220); caps, rim and specimen are solid."""
    item('05-industry/@/item/bio_capsule', [
        E,
        '....oooooooo....',
        '...ovvfffssdo...',
        '...offsssdddo...',
        '....oooooooo....',
        '....jWaaaaaj....',
        '....jWllllQj....',
        '....jWlqGqQj....',
        '....jWqGGgQj....',
        '....jWqqGgQj....',
        '....jlqqqqQj....',
        '....jQQQQQQj....',
        '....oooooooo....',
        '...ovvfffssdo...',
        '...offsgsdddo...',
        '....oooooooo....',
    ], {'o': OUT, 'v': '#b4bbc6', 'f': '#8d94a1', 's': '#666c78', 'd': '#434852',
        'j': '#3a86a8eb', 'W': '#f0fdffdc', 'a': '#c8f0ff80', 'l': '#a8eaffb4', 'q': '#6fd3f2aa', 'Q': '#48a8d0be',
        'G': '#6ee886', 'g': '#2f9a55'})


def laoba_lunch():
    item('05-industry/@/item/laoba_lunch', [
        E,
        '...........w.w..',
        '............k...',
        '.......oo.......',
        '......ohlo......',
        '.....ohlmdo.....',
        '.....oeeeeo.....',
        '....ohhllmdo....',
        '....ollmmddo....',
        '...ooeeeeeeoo...',
        '..ohhhlllmmmdo..',
        '..olllmmmmdddo..',
        '.ooeeeeeeeeeeoo.',
        '.ohhhhlllmmmmdo.',
        '.ollmmmmmmddddo.',
        '..oooooooooooo..',
    ], {'o': '#2a170b', 'e': '#44270f', 'd': '#623a17', 'm': '#875521', 'l': '#ad7430', 'h': '#d6a258',
        'w': '#c8dcf0c8', 'k': '#1e1e1e'})


ROCKET_BANDS = {
    'sounding': '#dc7828', 'qiming_1': '#d23c32', 'qiming_2': '#c85a28', 'dawn_3': '#e6b43c',
    'prospector': '#96785a', 'europa_surveyor': '#5aaadc', 'titan_explorer': '#dc963c',
    'horizon_x': '#5a6ee6', 'phobos_clipper': '#be5046', 'enceladus_pioneer': '#96dce6'}
ROCKET_SECTIONS = {'sounding': 1, 'qiming_1': 2, 'qiming_2': 2, 'dawn_3': 2, 'prospector': 2, 'europa_surveyor': 2,
                   'titan_explorer': 3, 'horizon_x': 2, 'phobos_clipper': 2, 'enceladus_pioneer': 2}


def rocket_kits():
    """A hollow hull section in the rocket's own livery; the thin stripes under the band count the section number."""
    hull = 'LwLMMSDX'
    stripe = '01234567'
    shade = [1.0, 1.3, 1.12, 1.0, 0.94, 0.8, 0.64, 0.5]
    for rocket, band in ROCKET_BANDS.items():
        for i in range(1, ROCKET_SECTIONS[rocket] + 1):
            marks = {1: 'hhshh', 2: 'hshsh', 3: 'shshs'}[i]
            rows = [E,
                    '.....oooooo.....',
                    '...ooSSSSSSoo...',
                    '...oKkkkkkkKo...',
                    '...oLwwLMMSDo...',
                    '...o' + hull + 'o...',
                    '...o' + stripe + 'o...', '...o' + stripe + 'o...',
                    '...o' + hull + 'o...']
            rows += ['...o' + (stripe if m == 's' else hull) + 'o...' for m in marks]
            rows += ['...oSSSSDDXXo...',
                     '....oooooooo....']
            pal = {'o': OUT, 'w': '#f6f8fa', 'L': '#dde1e7', 'M': '#bfc5ce', 'S': '#99a0ac', 'D': '#727986',
                   'X': '#565c69', 'k': '#15171d', 'K': '#2b2e38'}
            pal.update({str(c): scale(band, f) for c, f in enumerate(shade)})
            item('09-rocketry/@/item/rocket_kit_%s_%d' % (rocket, i), rows, pal)


def main():
    wind_turbine()
    fission_reactor()
    geothermal_generator()
    batteries()
    hydrogen_fuel_cell()
    rtg()
    habitat()
    nuke_silo()
    turret_kits()
    portable_sentry()
    bio_capsule()
    laoba_lunch()
    rocket_kits()
    print('repainted %d textures' % len(WRITTEN))


if __name__ == '__main__':
    main()
