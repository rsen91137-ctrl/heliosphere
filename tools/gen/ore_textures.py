#!/usr/bin/env python3
"""Redraws the ore textures: the 22 mineral overlays (laid over stone and deepslate by the released models), the oil
sand, the six planetary deposits and meteoric iron. Each mineral has its own habit (rounded nodules, prismatic
crystals, cubic grains, platy flakes, thin veins) and a three-tone palette lit from the top left, in the style of the
vanilla ores. Writes into the overlays 02-materials, 05-industry and 12-sphereworld and a contact sheet for checking.
Run from the repository root."""
import os
import numpy as np
from PIL import Image

T = 'src/main/resources/assets/heliosphere/textures/block'
OUT = {'02': f'jars/02-materials/{T}', '05': f'jars/05-industry/{T}', '12': f'jars/12-sphereworld/{T}'}


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.int32)


# mineral: (jar, habit, outline, body, light, accent or None, clusters)
ORES = {
    'apatite':         ('02', 'prism',  '#123f45', '#2f9a98', '#9ff0dc', None, 7),
    'beryl':           ('02', 'prism',  '#0e4a28', '#2fb664', '#c4ffd8', '#ffffff', 7),
    'fluorite':        ('02', 'cube',   '#2a0f5a', '#8346e0', '#e2c6ff', '#5ad6a0', 7),
    'graphite':        ('02', 'flake',  '#050507', '#2a2a33', '#f4f4ff', '#ffffff', 10),
    'sulfur':          ('02', 'nodule', '#6b5a05', '#e6cc1e', '#fff8a8', None, 7),
    'bauxite':         ('05', 'nodule', '#4f1d0a', '#b4562a', '#f0a466', None, 8),
    'cassiterite':     ('05', 'nodule', '#1a0f08', '#5c3a22', '#d6a66e', '#ffe6c0', 7),
    'chromite':        ('05', 'cube',   '#1c0a10', '#7a3550', '#f08cb0', '#ffd0e0', 10),
    'cobaltite':       ('05', 'cube',   '#3b2a3a', '#a487a4', '#f6def6', '#ffffff', 7),
    'galena':          ('05', 'cube',   '#10141c', '#8a9ab8', '#ffffff', '#ffffff', 10),
    'halite':          ('05', 'cube',   '#5a2a3c', '#f4c4d4', '#ffffff', '#ffffff', 9),
    'ilmenite':        ('05', 'nodule', '#0c0812', '#4c3462', '#cfa6ec', '#f4e0ff', 10),
    'molybdenite':     ('05', 'flake',  '#141c30', '#5a7ed0', '#e0eaff', '#ffffff', 10),
    'monazite':        ('05', 'prism',  '#5a2a0c', '#c8782a', '#ffd690', None, 7),
    'pentlandite':     ('05', 'nodule', '#4a3a10', '#b8963a', '#f8e6a0', '#fffbe0', 8),
    'pyrochlore':      ('05', 'cube',   '#3a120a', '#9c3a1a', '#ec8a44', None, 7),
    'sperrylite':      ('05', 'cube',   '#243442', '#d6ecf6', '#ffffff', '#a0f0ff', 9),
    'sphalerite':      ('05', 'nodule', '#2e1206', '#8c4814', '#eaa848', '#ffd890', 7),
    'spodumene':       ('05', 'prism',  '#5e3e60', '#d4a2d6', '#fff0ff', None, 7),
    'titanomagnetite': ('05', 'nodule', '#06080e', '#26406e', '#8cc0ff', '#e6f2ff', 10),
    'uraninite':       ('05', 'nodule', '#060a06', '#1f2a18', '#4a5a3a', '#7aff5a', 7),
    'wolframite':      ('05', 'blade',  '#120d08', '#463626', '#a88e6c', '#e0d0b0', 7),
}


def shade(mask, outline, body, light, accent, rng):
    """Colours a mineral mask: dark rim on the lower right, light on the upper left, a few glints."""
    img = np.zeros((16, 16, 4), np.int32)
    m = mask
    up = np.roll(m, 1, 0); left = np.roll(m, 1, 1); down = np.roll(m, -1, 0); right = np.roll(m, -1, 1)
    edge_lr = m & (~down | ~right)
    edge_ul = m & (~up | ~left)
    img[m, :3] = body
    img[m, 3] = 255
    img[edge_ul, :3] = (body * 0.45 + light * 0.55).astype(np.int32)
    img[edge_lr, :3] = outline
    # one bright pixel near the upper-left of each blob
    lab = label(m)
    for k in range(1, lab.max() + 1):
        ys, xs = np.nonzero(lab == k)
        if len(ys) == 0: continue
        i = np.argmin(ys + xs)
        img[ys[i], xs[i], :3] = light
        if accent is not None and len(ys) > 3 and rng.random() < 0.6:
            j = rng.integers(len(ys))
            img[ys[j], xs[j], :3] = accent
    return img


def label(m):
    lab = np.zeros(m.shape, np.int32); n = 0
    for y in range(16):
        for x in range(16):
            if m[y, x] and not lab[y, x]:
                n += 1; stack = [(y, x)]
                while stack:
                    a, b = stack.pop()
                    if 0 <= a < 16 and 0 <= b < 16 and m[a, b] and not lab[a, b]:
                        lab[a, b] = n; stack += [(a + 1, b), (a - 1, b), (a, b + 1), (a, b - 1)]
    return lab


def blob(habit, rng):
    """One cluster's footprint, centred near (0, 0)."""
    pts = set()
    if habit == 'nodule':
        r = rng.uniform(1.2, 2.1)
        for y in range(-3, 4):
            for x in range(-3, 4):
                if (x / r) ** 2 + (y / (r * rng.uniform(0.8, 1.1))) ** 2 <= 1.15: pts.add((y, x))
        if rng.random() < 0.5: pts.add((int(rng.integers(-2, 3)), int(rng.integers(-2, 3))))
    elif habit == 'cube':
        s = int(rng.integers(2, 4))
        for y in range(s):
            for x in range(s): pts.add((y, x))
        if rng.random() < 0.6:
            o = int(rng.integers(1, s + 1))
            for y in range(2):
                for x in range(2): pts.add((y + o, x + s - 1))
    elif habit == 'prism':
        L = int(rng.integers(3, 6)); d = rng.choice([(1, 1), (1, -1), (0, 1), (1, 0)])
        for i in range(L):
            pts.add((i * d[0], i * d[1]))
            pts.add((i * d[0] + (1 if d[1] else 0), i * d[1] + (0 if d[1] else 1)))
        if rng.random() < 0.5:
            for i in range(L - 2): pts.add((i * d[0] + 2, i * d[1] - 1 if d[1] else i * d[1] + 2))
    elif habit == 'blade':
        L = int(rng.integers(3, 5)); d = rng.choice([(1, 1), (1, -1)])
        for i in range(L): pts.add((i * d[0], i * d[1])); pts.add((i * d[0], i * d[1] + 1)); pts.add((i * d[0] + 1, i * d[1]))
    elif habit == 'flake':
        w = int(rng.integers(3, 5))
        for x in range(w): pts.add((0, x)); pts.add((1, x))
        if rng.random() < 0.6:
            for x in range(w - 1): pts.add((2, x + 1))
    return pts


def ore(name, spec, seed):
    _jar, habit, o, b, l, a, n = spec
    rng = np.random.default_rng(seed)
    best = None
    for attempt in range(40):
        mask = np.zeros((16, 16), bool)
        centres = []
        for _ in range(n):
            for _try in range(30):
                cy, cx = int(rng.integers(2, 13)), int(rng.integers(2, 13))
                if all(abs(cy - y) + abs(cx - x) > 5 for y, x in centres): break
            centres.append((cy, cx))
            for y, x in blob(habit, rng):
                yy, xx = cy + y, cx + x
                if 1 <= yy < 15 and 1 <= xx < 15: mask[yy, xx] = True
        cover = mask.mean()
        if 0.20 <= cover <= 0.34: best = mask; break
        if best is None or abs(cover - 0.27) < abs(best.mean() - 0.27): best = mask
    return shade(best, hexc(o), hexc(b), hexc(l), None if a is None else hexc(a), rng)


# ---- full blocks ---------------------------------------------------------------------------------------------------

def noise(seed, scale):
    """Tileable value noise on a 16x16 grid, 0..1."""
    rng = np.random.default_rng(seed)
    g = rng.random((scale, scale))
    ys, xs = np.mgrid[0:16, 0:16] * (scale / 16)
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    y1, x1 = (y0 + 1) % scale, (x0 + 1) % scale
    v = (g[y0, x0] * (1 - fx) + g[y0, x1] * fx) * (1 - fy) + (g[y1, x0] * (1 - fx) + g[y1, x1] * fx) * fy
    return v


def ramp(v, cols):
    cols = [hexc(c) for c in cols]
    idx = np.clip((v * len(cols)).astype(int), 0, len(cols) - 1)
    img = np.zeros((16, 16, 4), np.int32)
    for i, c in enumerate(cols): img[idx == i, :3] = c
    img[..., 3] = 255
    return img


def grains(seed, cols, base_scale=4, fine=0.55):
    rng = np.random.default_rng(seed)
    v = noise(seed, base_scale) * (1 - fine) + rng.random((16, 16)) * fine
    v = (v - v.min()) / (v.max() - v.min() + 1e-9)
    return ramp(v, cols)


def oil_sand():
    """Bitumen-soaked sand: warm sand with black, glossy lumps of tar and a few dark seeps."""
    rng = np.random.default_rng(11)
    img = grains(11, ['#5e4a32', '#6e573b', '#7d6546', '#8e7552'], 4, 0.65)
    tar = ore('tar', ('02', 'nodule', '#060504', '#17120e', '#3a302a', '#9c9088', 8), 23)
    m = tar[..., 3] > 0
    img[m] = tar[m]
    for y in range(16):                                         # oil seeping down under each lump
        for x in range(16):
            if m[y, x] and y + 1 < 16 and not m[y + 1, x] and rng.random() < 0.7:
                img[y + 1, x, :3] = (img[y + 1, x, :3] * 0.55).astype(np.int32)
    for _ in range(10):                                         # bright quartz grains
        y, x = rng.integers(0, 16, 2)
        if not m[y, x]: img[y, x, :3] = hexc('#c8b48e')
    return img


def deposit(seed, ground, mineral, habit='nodule', n=4, accent=None):
    """A planetary deposit: grainy ground with mineral crystals set in it."""
    img = grains(seed, ground, 4, 0.5)
    o, b, l = mineral
    rng = np.random.default_rng(seed + 1)
    over = ore('x', ('12', habit, o, b, l, accent, n), seed + 2)
    m = over[..., 3] > 0
    img[m] = over[m]
    return img


FULL = {
    ('02', 'oil_sand'): oil_sand,
    ('12', 'gypsum_deposit'): lambda: deposit(41, ['#9c8a74', '#b09c84', '#c2ae94'], ['#9a968c', '#eceae2', '#ffffff'], 'prism', 5),
    ('12', 'hematite_deposit'): lambda: deposit(43, ['#4e2420', '#62302a', '#743c32'], ['#1a1012', '#4a3a40', '#b8a8b4'], 'nodule', 5, '#d04a3a'),
    ('12', 'hollows_deposit'): lambda: deposit(47, ['#4a4a50', '#5a5a62', '#6a6a72'], ['#6a86a8', '#b8d2f0', '#ffffff'], 'flake', 6),
    ('12', 'natron_deposit'): lambda: deposit(53, ['#8a6a4a', '#a07e58', '#b49068'], ['#a8a49a', '#ece6d8', '#ffffff'], 'cube', 6),
    ('12', 'perchlorate_deposit'): lambda: deposit(59, ['#9a5a34', '#ae6a40', '#c07a4c'], ['#a89e8a', '#f0e8d4', '#ffffff'], 'flake', 5, '#ffe0a0'),
    ('12', 'tholin_deposit'): lambda: deposit(61, ['#5a3416', '#6e421c', '#824e22'], ['#3a1a06', '#c86a1a', '#ffb050'], 'nodule', 5),
    ('12', 'meteoric_iron_ore'): lambda: deposit(67, ['#2c2a2a', '#3a3836', '#4a4644'], ['#3a3e46', '#8c96a4', '#eef4fc'], 'cube', 5, '#c87a4a'),
}


def save(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(path)


def composite(over, base):
    out = base.copy()
    m = over[..., 3] > 0
    out[m] = over[m]
    return out


if __name__ == '__main__':
    sheet_items = []
    stone = np.array(Image.open('build/ref/stone.png').convert('RGBA')).astype(np.int32) if os.path.exists('build/ref/stone.png') else None
    deep = np.array(Image.open('build/ref/deepslate.png').convert('RGBA')).astype(np.int32) if os.path.exists('build/ref/deepslate.png') else None
    for i, (name, spec) in enumerate(ORES.items()):
        img = ore(name, spec, 100 + i * 7)
        save(f"{OUT[spec[0]]}/ore/{name}.png", img)
        sheet_items.append(composite(img, stone) if stone is not None else img)
        if deep is not None: sheet_items.append(composite(img, deep))
    for (jar, name), f in FULL.items():
        img = f()
        save(f'{OUT[jar]}/{name}.png', img)
        sheet_items.append(img)
    cols = 10
    rows = (len(sheet_items) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * 136, rows * 136), (30, 30, 30, 255))
    for k, im in enumerate(sheet_items):
        t = Image.fromarray(np.clip(im, 0, 255).astype(np.uint8), 'RGBA').resize((128, 128), Image.NEAREST)
        sheet.paste(t, ((k % cols) * 136 + 4, (k // cols) * 136 + 4), t)
    os.makedirs('build', exist_ok=True)
    sheet.save('build/ore_sheet.png')
    print(len(sheet_items), 'tiles')
