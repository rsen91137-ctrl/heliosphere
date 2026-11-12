#!/usr/bin/env python3
"""Seamless procedural texture generator (PBR maps or pixel art). Needs only numpy + Pillow."""
import argparse
import os

import numpy as np
from PIL import Image

# ----------------------------------------------------------------------------- noise


def periodic_noise(n, cells, rng):
    """Value noise that tiles: lattice is cells x cells and wraps around."""
    cells = max(1, int(cells))
    lat = rng.random((cells, cells))
    t = np.arange(n) * cells / n
    i0 = np.floor(t).astype(int)
    f = t - i0
    f = f * f * (3 - 2 * f)
    i1 = (i0 + 1) % cells
    i0 %= cells
    a = lat[np.ix_(i0, i0)]
    b = lat[np.ix_(i0, i1)]
    c = lat[np.ix_(i1, i0)]
    d = lat[np.ix_(i1, i1)]
    fy, fx = f[:, None], f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(n, rng, base=4, octaves=5, gain=0.5):
    out = np.zeros((n, n))
    amp, total, cells = 1.0, 0.0, base
    for _ in range(octaves):
        out += amp * periodic_noise(n, min(cells, n), rng)
        total += amp
        amp *= gain
        cells *= 2
    return out / total


def ridged(n, rng, base=4, octaves=5):
    return 1 - np.abs(fbm(n, rng, base, octaves) * 2 - 1)


def cells_voronoi(n, count, rng):
    """Tileable Worley noise: distance to nearest and second-nearest feature point."""
    count = max(2, int(count))
    pts = rng.random((count, 2))
    ys, xs = np.mgrid[0:n, 0:n] / n
    d1 = np.full((n, n), 9.0)
    d2 = np.full((n, n), 9.0)
    ids = np.zeros((n, n), dtype=int)
    for k, (px, py) in enumerate(pts):
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, 1 - dx)
        dy = np.minimum(dy, 1 - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < d1
        d2 = np.where(closer, d1, np.minimum(d2, d))
        ids = np.where(closer, k, ids)
        d1 = np.where(closer, d, d1)
    return d1, d2, ids


def norm(a):
    lo, hi = a.min(), a.max()
    return (a - lo) / (hi - lo + 1e-9)


# ----------------------------------------------------------------------------- materials
# each returns (height 0..1, roughness 0..1, colour-ramp position 0..1)


def m_stone(n, r, s):
    d1, d2, _ = cells_voronoi(n, int(18 * s), r)
    cracks = np.clip((d2 - d1) * 14, 0, 1)
    h = norm(0.65 * fbm(n, r, int(6 * s)) + 0.35 * cracks)
    return h, 0.75 + 0.2 * (1 - h), h


def m_rock(n, r, s):
    h = norm(ridged(n, r, int(3 * s), 6) * 0.7 + fbm(n, r, int(12 * s)) * 0.3)
    return h, 0.85 - 0.15 * h, h


def m_metal(n, r, s):
    brushed = np.repeat(fbm(n, r, int(64 * s), 2)[:1], n, 0)  # horizontal streaks
    grime = fbm(n, r, int(4 * s))
    h = norm(0.15 * brushed + 0.05 * grime)
    rough = 0.25 + 0.35 * norm(grime) ** 2
    return h, rough, norm(0.6 * brushed + 0.4 * grime)


def m_rust(n, r, s):
    base_h, base_r, _ = m_metal(n, r, s)
    mask = np.clip((fbm(n, r, int(5 * s)) - 0.45) * 6, 0, 1)
    pits = fbm(n, r, int(24 * s))
    h = norm(base_h * (1 - mask) + (0.5 + 0.5 * pits) * mask)
    return h, base_r * (1 - mask) + 0.9 * mask, np.clip(mask * (0.4 + 0.6 * pits), 0, 1)


def m_wood(n, r, s):
    ys, xs = np.mgrid[0:n, 0:n] / n
    warp = fbm(n, r, int(4 * s)) * 3
    rings = 0.5 + 0.5 * np.sin((ys * int(10 * s) + warp) * 2 * np.pi)
    grain = np.repeat(fbm(n, r, int(48 * s), 3)[:, :1], n, 1)
    h = norm(0.7 * rings + 0.3 * grain)
    return h, 0.6 + 0.2 * (1 - h), h


def m_sand(n, r, s):
    ys, _ = np.mgrid[0:n, 0:n] / n
    dunes = 0.5 + 0.5 * np.sin((ys * int(6 * s) + fbm(n, r, 3) * 2) * 2 * np.pi)
    grains = r.random((n, n))
    h = norm(0.75 * dunes + 0.15 * fbm(n, r, int(16 * s)) + 0.1 * grains)
    return h, 0.9 + 0.1 * grains, norm(h + 0.2 * grains)


def m_brick(n, r, s):
    rows = max(2, int(8 * s))
    cols = max(1, rows // 2)
    ys, xs = np.mgrid[0:n, 0:n] / n
    row = np.floor(ys * rows)
    xo = xs * cols + 0.5 * (row % 2)
    fy = ys * rows - row
    fx = xo - np.floor(xo)
    mortar = 0.06
    edge = np.minimum(np.minimum(fx, 1 - fx) * cols * 0.5 / (rows * 0.25), np.minimum(fy, 1 - fy))
    brick = np.clip(edge / mortar, 0, 1)
    tint_id = (row * 7 + np.floor(xo) % cols * 13) % 5 / 4.0
    h = norm(brick * (0.85 + 0.15 * fbm(n, r, int(16 * s))))
    ramp = np.where(brick > 0.5, 0.35 + 0.5 * tint_id * 0.6 + 0.15 * fbm(n, r, 8), 0.05)
    return h, 0.8 + 0.15 * (1 - brick), np.clip(ramp, 0, 1)


def m_ice(n, r, s):
    d1, d2, _ = cells_voronoi(n, int(10 * s), r)
    cracks = 1 - np.clip((d2 - d1) * 30, 0, 1)
    h = norm(fbm(n, r, int(4 * s), 4) - 0.4 * cracks)
    return h, 0.05 + 0.3 * cracks, norm(h - 0.5 * cracks)


def m_lava(n, r, s):
    d1, d2, _ = cells_voronoi(n, int(14 * s), r)
    seams = 1 - np.clip((d2 - d1) * 10, 0, 1)
    glow = np.clip(seams + 0.4 * (fbm(n, r, int(6 * s)) - 0.5), 0, 1)
    h = norm(1 - glow + 0.2 * fbm(n, r, int(20 * s)))
    return h, 0.9 - 0.6 * glow, glow


def m_grass(n, r, s):
    blades = fbm(n, r, int(48 * s), 3)
    clumps = fbm(n, r, int(5 * s))
    h = norm(0.6 * blades + 0.4 * clumps)
    return h, 0.85 + 0.1 * blades, h


MATERIALS = {
    'stone': (m_stone, ['#3a3a3e', '#6a6a70', '#9a9aa0', '#c4c4c8']),
    'rock': (m_rock, ['#3b2a20', '#6d5340', '#9c8268', '#c9b294']),
    'metal': (m_metal, ['#5a6068', '#8a929c', '#b8c0c8', '#dde3ea']),
    'rust': (m_rust, ['#7c8288', '#7a3b1c', '#a5521f', '#d0843c']),
    'wood': (m_wood, ['#4a2c17', '#7a4e2c', '#a8743f', '#c99a5f']),
    'sand': (m_sand, ['#a88a5a', '#c9a96e', '#e0c58e', '#f2deb0']),
    'brick': (m_brick, ['#8e8a82', '#7a2f22', '#9c3f2c', '#b9583a']),
    'ice': (m_ice, ['#6fa4c8', '#9cc8e4', '#c8e4f4', '#f0faff']),
    'lava': (m_lava, ['#1a0c08', '#5a1408', '#d2400a', '#ffd23c']),
    'grass': (m_grass, ['#2f5a1e', '#4a7f2a', '#6ea33a', '#9cc95a']),
}

# ----------------------------------------------------------------------------- outputs


def hexrgb(h):
    h = h.strip().lstrip('#')
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def colour_ramp(t, colours):
    cs = np.array([hexrgb(c) for c in colours], dtype=float)
    pos = np.clip(t, 0, 1) * (len(cs) - 1)
    i = np.minimum(np.floor(pos).astype(int), len(cs) - 2)
    f = (pos - i)[..., None]
    return cs[i] * (1 - f) + cs[i + 1] * f


def normal_map(h, strength):
    dx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * strength
    dy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * strength
    nz = np.ones_like(h)
    v = np.stack([-dx, dy, nz], -1)  # OpenGL convention (three.js / Blender)
    v /= np.linalg.norm(v, axis=-1, keepdims=True)
    return (v * 0.5 + 0.5) * 255


def lit(albedo, h, strength=6.0):
    """Bake simple top-left lighting into the colour (used for pixel art / previews)."""
    dx = np.roll(h, -1, 1) - np.roll(h, 1, 1)
    dy = np.roll(h, -1, 0) - np.roll(h, 1, 0)
    shade = 1 + strength * (-dx - dy) * 0.5
    return np.clip(albedo * shade[..., None], 0, 255)


def save(arr, path):
    a = np.clip(arr, 0, 255).astype(np.uint8)
    Image.fromarray(a).save(path)
    print('wrote', path)


def tile_preview(path):
    im = Image.open(path)
    w, h = im.size
    big = Image.new(im.mode, (w * 2, h * 2))
    for x in (0, w):
        for y in (0, h):
            big.paste(im, (x, y))
    out = path.replace('.png', '_tiled.png')
    big.save(out)
    print('wrote', out)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--material', default='stone', choices=sorted(MATERIALS))
    p.add_argument('--mode', default='pbr', choices=['pbr', 'pixel'])
    p.add_argument('--size', type=int, default=None, help='pbr: 1024 default; pixel: 16 default')
    p.add_argument('--seed', type=int, default=1)
    p.add_argument('--scale', type=float, default=1.0, help='pattern density (bigger = finer)')
    p.add_argument('--colors', default=None, help='comma-separated hex colours, dark to light')
    p.add_argument('--levels', type=int, default=5, help='pixel mode: colour steps')
    p.add_argument('--normal-strength', type=float, default=None)
    p.add_argument('--tile-preview', action='store_true', help='also write a 2x2 tiled image to check seams')
    p.add_argument('--out', required=True, help='output path prefix, e.g. textures/stone')
    a = p.parse_args()

    fn, default_cols = MATERIALS[a.material]
    cols = [c for c in a.colors.split(',')] if a.colors else default_cols
    rng = np.random.default_rng(a.seed)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)

    if a.mode == 'pbr':
        n = a.size or 1024
        h, rough, ramp = fn(n, rng, a.scale)
        albedo = colour_ramp(ramp, cols)
        strength = a.normal_strength or n / 64
        save(albedo, a.out + '_albedo.png')
        save(normal_map(h, strength), a.out + '_normal.png')
        save(np.clip(rough, 0, 1) * 255, a.out + '_roughness.png')
        save(h * 255, a.out + '_height.png')
        if a.tile_preview:
            tile_preview(a.out + '_albedo.png')
    else:
        n = a.size or 16
        hi = n * 8  # compute detail at high res, then pixelate
        # at 16-32 px only a few features fit, so coarsen the pattern
        h, _, ramp = fn(hi, rng, a.scale * min(1.0, n / 64))
        small = lambda x: np.asarray(Image.fromarray((x * 255).astype(np.uint8)).resize((n, n), Image.BOX)) / 255.0
        h_s, ramp_s = small(norm(h)), small(np.clip(ramp, 0, 1))
        ramp_q = np.round(ramp_s * (a.levels - 1)) / (a.levels - 1)  # snap to a few steps
        img = lit(colour_ramp(ramp_q, cols), h_s, strength=1.2)
        save(img, a.out + '.png')
        prev = Image.fromarray(img.astype(np.uint8)).resize((n * 8, n * 8), Image.NEAREST)
        prev.save(a.out + '_preview.png')
        print('wrote', a.out + '_preview.png')
        if a.tile_preview:
            tile_preview(a.out + '_preview.png')


if __name__ == '__main__':
    main()
