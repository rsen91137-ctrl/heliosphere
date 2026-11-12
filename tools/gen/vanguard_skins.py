#!/usr/bin/env python3
"""Repaints the Centaurian vanguard skins (textures/entity/vanguard/*.png) at 128 px per tile, keeping the
released layout of four tiles (armour, graphite, service panel, emitter) and each machine's colours, which
are sampled from the released texture in base/07-*.jar. Deterministic; run from the repository root."""
import glob, io, os, zipfile
import numpy as np
from PIL import Image

T = 128
OUT = 'jars/07-hostiles/src/main/resources/assets/heliosphere/textures/entity/vanguard'
yy, xx = np.mgrid[:T, :T].astype(float)


def vnoise(scale, seed, octaves=4):
    r = np.random.default_rng(seed)
    out = np.zeros((T, T)); amp = 1.0; norm = 0
    for o in range(octaves):
        k = max(2, int(T / scale * 2 ** o))
        g = r.random((k + 1, k + 1))
        ys = np.linspace(0, k, T, endpoint=False); xs = ys
        yi, xi = ys.astype(int), xs.astype(int)
        fy, fx = ys - yi, xs - xi
        fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
        a = g[yi][:, xi] * (1 - fx) + g[yi][:, xi + 1] * fx
        b = g[yi + 1][:, xi] * (1 - fx) + g[yi + 1][:, xi + 1] * fx
        out += amp * (a * (1 - fy[:, None]) + b * fy[:, None]); norm += amp; amp *= 0.5
    return out / norm


def hexgrid(size):
    """Distance to the nearest hexagon edge, 0 on edges."""
    q = (xx * np.sqrt(3) / 3 - yy / 3) / size
    r = yy * 2 / 3 / size
    x, z = q, r; y = -x - z
    rx, ry, rz = np.round(x), np.round(y), np.round(z)
    dx, dy, dz = abs(rx - x), abs(ry - y), abs(rz - z)
    m1 = (dx > dy) & (dx > dz); m2 = ~m1 & (dy > dz)
    rx = np.where(m1, -ry - rz, rx); ry = np.where(m2, -rx - rz, ry)
    fx, fz = x - rx, z - rz
    d = np.maximum.reduce([abs(fx), abs(fz), abs(fx + fz)])
    return 0.5 - d


def shade(base, light):
    return np.clip(base[None, None, :] * light[..., None], 0, 255)


def armour(c, seed):
    e = hexgrid(13)
    light = 0.78 + 0.25 * vnoise(20, seed)
    light = np.where(e < 0.06, 0.45, light)                         # seams
    light = np.where((e > 0.06) & (e < 0.1), light * 1.18, light)   # lit bevel
    flow = np.sin((xx + 30 * vnoise(40, seed + 1)) / 9.0)
    light = light + 0.06 * flow
    return shade(c, light)


def graphite(c, seed):
    light = 0.35 + 0.1 * vnoise(10, seed)
    for y in range(12, T - 8, 14):
        band = (yy >= y) & (yy < y + 7) & (xx > 10) & (xx < T - 10)
        light = np.where(band, 0.12, light)
        light = np.where((yy == y + 7) & (xx > 10) & (xx < T - 10), 0.7, light)
    return shade(c, light)


def service(c, seed):
    light = 0.82 + 0.12 * vnoise(16, seed)
    inner = (abs(xx - 63.5) < 40) & (abs(yy - 63.5) < 40)
    rim = inner & ~((abs(xx - 63.5) < 36) & (abs(yy - 63.5) < 36))
    light = np.where(rim, 0.5, light)
    for i in range(4):                                             # glyph strokes: the Centaurian marks
        light = np.where((abs(yy - (40 + i * 16)) < 2) & (abs(xx - 63.5) < 10 + 6 * i), 0.4, light)
    for (x, y) in [(14, 14), (113, 14), (14, 113), (113, 113)]:
        light = np.where(np.hypot(xx - x, yy - y) < 4, 0.45, light)
    return shade(c, light)


def emitter(c, seed):
    r = np.hypot(xx - 63.5, yy - 63.5)
    a = np.arctan2(yy - 63.5, xx - 63.5)
    crystal = np.abs(np.cos(a * 4)) ** 6 * np.exp(-r / 40)
    light = 0.25 + 1.4 * np.exp(-(r / 26) ** 2) + 0.7 * crystal + 0.25 * vnoise(8, seed)
    light = np.where(abs(r - 52) < 3, 1.1, light)
    return shade(c, np.clip(light, 0, 1.6))


def main():
    base = glob.glob('base/07-*.jar')[0]
    z = zipfile.ZipFile(base)
    os.makedirs(OUT, exist_ok=True)
    for i, name in enumerate(['scout', 'interceptor', 'crawler', 'bulwark', 'sentinel']):
        old = np.asarray(Image.open(io.BytesIO(z.read(f'assets/heliosphere/textures/entity/vanguard/{name}.png'))).convert('RGB')).astype(float)
        tw = old.shape[1] // 4
        cols = [old[:, k * tw:(k + 1) * tw].reshape(-1, 3).mean(0) for k in range(4)]
        cols[3] = old[:, 3 * tw:].reshape(-1, 3).max(0) * 0.9           # the emitter keeps its brightest colour
        tiles = [armour(cols[0] * 1.15, i * 10 + 1), graphite(cols[1] * 1.4, i * 10 + 2),
                 service(cols[2] * 1.1, i * 10 + 3), emitter(cols[3], i * 10 + 4)]
        img = np.concatenate(tiles, axis=1).astype(np.uint8)
        Image.fromarray(img, 'RGB').save(f'{OUT}/{name}.png')


if __name__ == '__main__':
    main()
