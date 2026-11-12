#!/usr/bin/env python3
"""Redraws the planetary surface textures (overlay 12-sphereworld) so each world reads differently. Every block keeps
its released colours (taken from the released texture) but gets a structure of its own: fine regolith with
micro-craters and pebbles, rippled sand, fractured rock, vesicular basalt, cracked ice with sheen, powdery frost,
blotchy volcanic crust, layered sediment. 16x16, lit from the top left, palette-quantised like vanilla.
Usage: tools/gen/planet_textures.py (reads base/12-*.jar). Writes build/planet_sheet.png for checking."""
import glob, io, os, zipfile
import numpy as np
from PIL import Image

OUT = 'jars/12-sphereworld/src/main/resources/assets/heliosphere/textures/block'

STYLE = {
    'moon_regolith': 'regolith', 'mare_regolith': 'regolith', 'helium3_regolith': 'regolith', 'mars_regolith': 'regolith',
    'mercury_regolith': 'regolith', 'venus_regolith': 'regolith', 'phobos_regolith': 'regolith', 'ceres_regolith': 'regolith',
    'vesta_regolith': 'regolith', 'callisto_regolith': 'regolith', 'charon_regolith': 'regolith',
    'mars_sand': 'sand', 'tholin_sand': 'sand',
    'anorthosite': 'rock', 'mars_rock': 'rock', 'tessera_rock': 'layered', 'eucrite': 'rock', 'diogenite': 'crystal',
    'carbonaceous_rock': 'rock', 'kreep_rock': 'crystal', 'olivine_rock': 'crystal', 'ganymede_dark': 'rock',
    'mare_basalt': 'basalt', 'high_ti_basalt': 'basalt', 'mars_basalt': 'basalt', 'venus_basalt': 'basalt', 'io_silicate': 'basalt',
    'mercury_crust': 'rock',
    'moon_ice': 'ice', 'mars_ice': 'ice', 'europa_ice': 'ice', 'grooved_ice': 'grooved', 'miranda_ice': 'grooved',
    'tiger_stripe_ice': 'ice', 'water_ice_bedrock': 'ice', 'titan_ice_bedrock': 'ice', 'methane_ice': 'ice', 'nitrogen_ice': 'ice',
    'dry_ice': 'frost', 'so2_frost': 'frost', 'eris_frost': 'frost', 'metal_frost': 'frost', 'enceladus_snow': 'frost',
    'sulfur_crust': 'crust', 'tholin_crust': 'crust', 'cantaloupe_terrain': 'cantaloupe', 'corona_ridge': 'layered',
    'lineae_salt': 'layered', 'smectite_clay': 'layered', 'ammoniated_clay': 'layered',
}


def released():
    z = zipfile.ZipFile(glob.glob('base/12-*.jar')[0])
    out = {}
    for n in STYLE:
        p = f'assets/heliosphere/textures/block/{n}.png'
        if p in z.namelist():
            out[n] = np.asarray(Image.open(io.BytesIO(z.read(p))).convert('RGB')).astype(float)
    return out


def palette(img, n=5):
    """n tones from dark to light around the texture's own colours."""
    px = img.reshape(-1, 3)
    lum = px @ [0.3, 0.59, 0.11]
    order = np.argsort(lum)
    chunks = np.array_split(px[order], n)
    tones = np.array([c.mean(axis=0) for c in chunks])
    # widen the range a little: released textures are often flat
    mid = tones.mean(axis=0)
    return np.clip(mid + (tones - mid) * 1.25, 0, 255)


def noise(seed, cells):
    rng = np.random.default_rng(seed)
    g = rng.random((cells, cells))
    ys, xs = np.mgrid[0:16, 0:16] * (cells / 16)
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    y1, x1 = (y0 + 1) % cells, (x0 + 1) % cells
    return (g[y0, x0] * (1 - fx) + g[y0, x1] * fx) * (1 - fy) + (g[y1, x0] * (1 - fx) + g[y1, x1] * fx) * fy


def norm(v):
    return (v - v.min()) / (v.max() - v.min() + 1e-9)


def quant(v, pal):
    idx = np.clip((norm(v) * len(pal)).astype(int), 0, len(pal) - 1)
    return pal[idx], idx


def disc(img, idx, pal, cy, cx, r, light=True):
    """A small lit pebble or (light=False) a micro-crater."""
    for y in range(-r - 1, r + 2):
        for x in range(-r - 1, r + 2):
            d = (x * x + y * y) ** 0.5
            yy, xx = (cy + y) % 16, (cx + x) % 16
            if d <= r:
                side = (x + y) / (r + 1e-9)          # -1 top-left .. 1 bottom-right
                k = len(pal) - 1 if light else 0
                t = int(np.clip((len(pal) - 1) / 2 - side * (len(pal) - 1) / 2 * (1 if light else -1), 0, len(pal) - 1))
                img[yy, xx] = pal[t]
            elif d <= r + 1 and not light and (x + y) > 0:
                img[yy, xx] = pal[-1]                # lit far rim of a crater


def regolith(n, pal, rng, seed):
    v = noise(seed, 4) * 0.5 + noise(seed + 5, 8) * 0.35 + rng.random((16, 16)) * 0.15
    img, idx = quant(v, pal[:4])
    img = img.copy()
    for _ in range(rng.integers(1, 3)): disc(img, idx, pal, *rng.integers(0, 16, 2), int(rng.integers(1, 3)), light=False)
    for _ in range(rng.integers(2, 4)): disc(img, idx, pal, *rng.integers(0, 16, 2), 1)
    return img


def sand(n, pal, rng, seed):
    ys, xs = np.mgrid[0:16, 0:16]
    rip = np.sin((ys + noise(seed, 4) * 5) / 16 * np.pi * 2 * 3)
    v = rip * 0.5 + rng.random((16, 16)) * 0.2 + noise(seed + 1, 8) * 0.3
    return quant(v, pal)[0]


def rock(n, pal, rng, seed):
    # a few angular plates separated by dark cracks
    pts = rng.integers(0, 16, (5, 2))
    ys, xs = np.mgrid[0:16, 0:16]
    d = np.stack([np.minimum(abs(ys - y), 16 - abs(ys - y)) ** 2 + np.minimum(abs(xs - x), 16 - abs(xs - x)) ** 2 for y, x in pts])
    near = np.sort(d, axis=0)
    cell = np.argmin(d, axis=0)
    shade = rng.random(5)[cell]
    v = shade * 0.55 + rng.random((16, 16)) * 0.12 + noise(seed + 2, 8) * 0.33
    img = quant(v, pal)[0].copy()
    edge = (near[1] - near[0]) < 3
    img[edge] = pal[0]
    below = np.roll(edge, 1, 0) & ~edge                     # lit lip under each crack
    img[below] = pal[-1] * 0.85 + pal[-2] * 0.15
    return img


def basalt(n, pal, rng, seed):
    v = noise(seed, 4) * 0.55 + noise(seed + 4, 8) * 0.3 + rng.random((16, 16)) * 0.15
    img = quant(v, pal[:4])[0].copy()
    for _ in range(rng.integers(5, 9)):                       # vesicles
        y, x = rng.integers(0, 16, 2)
        img[y, x] = pal[0]
        img[(y + 1) % 16, x] = pal[-1] * 0.7 + pal[2] * 0.3
    return img


def crystal(n, pal, rng, seed):
    img = rock(n, pal[:4], rng, seed)
    hi = pal[-1]
    for _ in range(rng.integers(3, 6)):                       # glinting grains
        y, x = rng.integers(0, 15, 2)
        img[y, x] = hi; img[y, x + 1] = hi * 0.8 + pal[2] * 0.2; img[y + 1, x] = pal[1]
    return img


def ice(n, pal, rng, seed):
    v = noise(seed, 3) * 0.7 + rng.random((16, 16)) * 0.15 + noise(seed + 3, 8) * 0.15
    img = quant(v, pal[1:])[0].copy()
    y, x = rng.integers(0, 16, 2)
    for _ in range(18):                                       # a wandering crack
        img[y % 16, x % 16] = pal[0]
        img[(y + 1) % 16, x % 16] = pal[-1]
        x += 1; y += int(rng.integers(-1, 2))
    for _ in range(3):                                        # sheen
        y, x = rng.integers(0, 15, 2)
        img[y, x] = np.minimum(pal[-1] * 1.08, 255)
    return img


def grooved(n, pal, rng, seed):
    ys, xs = np.mgrid[0:16, 0:16]
    g = ((ys + xs // 5) % 4 == 0)
    v = noise(seed, 4) * 0.6 + rng.random((16, 16)) * 0.4
    img = quant(v, pal[1:])[0].copy()
    img[g] = pal[0]
    img[np.roll(g, 1, 0) & ~g] = pal[-1]
    return img


def frost(n, pal, rng, seed):
    v = rng.random((16, 16)) * 0.25 + noise(seed, 4) * 0.45 + noise(seed + 6, 8) * 0.3
    img = quant(v, pal[1:])[0].copy()
    for _ in range(6):
        y, x = rng.integers(0, 16, 2)
        img[y, x] = np.minimum(pal[-1] * 1.1, 255)
    return img


def crust(n, pal, rng, seed):
    v = noise(seed, 5) * 0.7 + rng.random((16, 16)) * 0.3
    img, idx = quant(v, pal)
    img = img.copy()
    hot = norm(noise(seed + 9, 3)) > 0.78
    img[hot] = pal[-1]
    return img


def cantaloupe(n, pal, rng, seed):
    ys, xs = np.mgrid[0:16, 0:16]
    pits = [(rng.integers(0, 16), rng.integers(0, 16)) for _ in range(6)]
    d = np.min([np.minimum(abs(ys - y), 16 - abs(ys - y)) ** 2 + np.minimum(abs(xs - x), 16 - abs(xs - x)) ** 2 for y, x in pits], axis=0)
    v = np.sqrt(d) / 6 + rng.random((16, 16)) * 0.25
    return quant(v, pal)[0]


def layered(n, pal, rng, seed):
    ys, xs = np.mgrid[0:16, 0:16]
    bands = np.sin((ys + noise(seed, 3) * 4) / 16 * np.pi * 2 * 2.5)
    v = bands * 0.6 + rng.random((16, 16)) * 0.12 + noise(seed + 1, 8) * 0.28
    return quant(v, pal)[0]


PAINT = {'regolith': regolith, 'sand': sand, 'rock': rock, 'basalt': basalt, 'crystal': crystal, 'ice': ice, 'grooved': grooved,
         'frost': frost, 'crust': crust, 'cantaloupe': cantaloupe, 'layered': layered}


def main():
    src = released()
    os.makedirs(OUT, exist_ok=True)
    tiles = []
    for i, (n, img) in enumerate(sorted(src.items())):
        pal = palette(img)
        rng = np.random.default_rng(1000 + i)
        out = PAINT[STYLE[n]](n, pal, rng, 2000 + i)
        if n == 'helium3_regolith':                       # keep its golden ilmenite glints
            for _ in range(4):
                y, x = rng.integers(1, 15, 2)
                out[y, x] = (250, 220, 90); out[y, x + 1] = (210, 170, 50); out[y + 1, x] = (150, 120, 40)
        rgba = np.dstack([np.clip(out, 0, 255), np.full((16, 16), 255)]).astype(np.uint8)
        Image.fromarray(rgba, 'RGBA').save(f'{OUT}/{n}.png')
        tiles.append((img, rgba))
    cols = 8
    sheet = Image.new('RGB', (cols * 2 * 68, ((len(tiles) + cols - 1) // cols) * 68), (25, 25, 25))
    for k, (old, new) in enumerate(tiles):
        x, y = (k % cols) * 136, (k // cols) * 68
        sheet.paste(Image.fromarray(old.astype(np.uint8)).resize((64, 64), Image.NEAREST), (x + 2, y + 2))
        sheet.paste(Image.fromarray(new[..., :3]).resize((64, 64), Image.NEAREST), (x + 68, y + 2))
    os.makedirs('build', exist_ok=True)
    sheet.save('build/planet_sheet.png')
    print(len(tiles), 'textures')


if __name__ == '__main__':
    main()
