#!/usr/bin/env python3
"""Paints textures/entity/tech_kit.png for the machine renderer (jars/07-hostiles): a 512x512 atlas of
8x8 tiles, 64 px each, of greyscale surface detail. The renderer tints each tile per machine, so one set
of detail serves every paint scheme. Tile numbers must match TechRenderer.Tile. Deterministic."""
import os
import numpy as np
from PIL import Image

T = 64
OUT = 'jars/07-hostiles/src/main/resources/assets/heliosphere/textures/entity/tech_kit.png'
rng = np.random.default_rng(2097)


def noise(scale, octaves=4, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((T, T))
    amp = 1.0
    for o in range(octaves):
        k = max(1, int(T / scale * 2 ** o))
        g = r.random((k + 1, k + 1))
        ys = np.linspace(0, k, T, endpoint=False)
        xs = np.linspace(0, k, T, endpoint=False)
        yi, xi = np.floor(ys).astype(int), np.floor(xs).astype(int)
        fy, fx = ys - yi, xs - xi
        fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
        a = g[yi][:, xi] * (1 - fx) + g[yi][:, xi + 1] * fx
        b = g[yi + 1][:, xi] * (1 - fx) + g[yi + 1][:, xi + 1] * fx
        out += amp * (a * (1 - fy[:, None]) + b * fy[:, None])
        amp *= 0.5
    return out / out.max()


def wear(img, seed, strength=0.18):
    n = noise(10, 4, seed)
    scratches = np.zeros((T, T))
    r = np.random.default_rng(seed + 1)
    for _ in range(14):
        x, y = r.integers(0, T, 2)
        dx, dy = r.uniform(-1, 1, 2)
        for t in range(r.integers(4, 16)):
            xx, yy = int(x + dx * t) % T, int(y + dy * t) % T
            scratches[yy, xx] = 1
    return np.clip(img * (1 - strength * 0.5 + strength * n) + scratches * 0.22, 0, 1)


def bevel_panel(lines, rivets=True, base=0.78, seed=0):
    img = np.full((T, T), base)
    for (x0, y0, x1, y1) in lines:                      # recessed seams with a lit and a shaded edge
        if x0 == x1:
            img[y0:y1, x0] = 0.25; img[y0:y1, min(T - 1, x0 + 1)] = 0.95
        else:
            img[y0, x0:x1] = 0.25; img[min(T - 1, y0 + 1), x0:x1] = 0.95
    img[0, :] = img[:, 0] = 0.95
    img[-1, :] = img[:, -1] = 0.3
    if rivets:
        for (x, y) in [(5, 5), (58, 5), (5, 58), (58, 58), (32, 5), (32, 58)]:
            img[y - 1:y + 2, x - 1:x + 2] = 0.45; img[y - 1, x - 1] = 1.0
    return wear(img, seed)


def tiles():
    out = {}
    # 0 plate: light armour with panel seams and rivets
    out[0] = bevel_panel([(0, 22, 64, 22), (40, 22, 40, 64), (0, 46, 40, 46)], seed=1)
    # 1 heavy plate: thick armour, chamfered border, big bolts
    img = np.full((T, T), 0.72)
    for i in range(6):
        img[i, i:T - i] = 0.95 - i * 0.04; img[i:T - i, i] = 0.9 - i * 0.04
        img[T - 1 - i, i:T - i] = 0.3 + i * 0.05; img[i:T - i, T - 1 - i] = 0.35 + i * 0.05
    for (x, y) in [(12, 12), (51, 12), (12, 51), (51, 51)]:
        yy, xx = np.ogrid[:T, :T]
        m = (xx - x) ** 2 + (yy - y) ** 2 <= 9
        img[m] = 0.5; img[y - 2, x - 2] = 1
    out[1] = wear(img, 2)
    # 2 carbon weave
    yy, xx = np.mgrid[:T, :T]
    w = ((xx // 4 + yy // 4) % 2)
    img = 0.35 + 0.25 * w * (0.6 + 0.4 * np.sin(xx * 0.8)) + 0.25 * (1 - w) * (0.6 + 0.4 * np.sin(yy * 0.8))
    out[2] = wear(img, 3, 0.1)
    # 3 vent grille
    img = np.full((T, T), 0.6)
    for y in range(8, 56, 7):
        img[y:y + 4, 8:56] = 0.12; img[y + 4, 8:56] = 0.9
    img[6:8, 6:58] = 0.3; img[56:58, 6:58] = 0.9
    out[3] = wear(img, 4, 0.1)
    # 4 hazard stripes (painted in colour by the renderer's accent tint: dark bands stay dark)
    img = np.where(((xx + yy) // 11) % 2 == 0, 0.95, 0.12)
    out[4] = wear(img, 5, 0.3)
    # 5 joint / hydraulic: ribbed cylinder
    img = 0.45 + 0.4 * np.abs(np.sin(yy * np.pi / 6)) * (0.7 + 0.3 * np.cos((xx - 32) / 20))
    img[:, 28:36] = np.clip(img[:, 28:36] + 0.25, 0, 1)
    out[5] = wear(img, 6, 0.1)
    # 6 tread
    img = np.full((T, T), 0.25)
    for y in range(0, T, 8):
        img[y:y + 5, 4:60] = 0.55; img[y, 4:60] = 0.8
        img[y + 2:y + 3, 30:34] = 0.2
    out[6] = wear(img, 7, 0.2)
    # 7 cables
    img = np.full((T, T), 0.15)
    for i, x in enumerate(range(4, 60, 9)):
        c = 0.5 + 0.4 * np.cos((xx - x - 4) / 4.5 * np.pi / 2)
        img = np.where(np.abs(xx - x - 4) < 4, c * (0.8 + 0.2 * np.sin(yy * 0.5 + i)), img)
    out[7] = img
    # 8 lens: dark glass, bright rim, highlight
    r = np.hypot(xx - 31.5, yy - 31.5)
    img = np.where(r < 26, 0.08 + 0.1 * (r / 26), 0.6)
    img = np.where((r > 24) & (r < 27), 0.9, img)
    img = np.where(np.hypot(xx - 22, yy - 22) < 5, 0.95, img)
    out[8] = img
    # 9 emitter panel (glows; greyscale is the brightness)
    img = 0.35 + 0.65 * np.exp(-((yy - 32) / 9.0) ** 2)
    img *= 0.85 + 0.15 * np.sin(xx * 0.4)
    out[9] = np.clip(img, 0, 1)
    # 10 barrel: dark steel with bore and rifling rings
    img = 0.3 + 0.25 * np.cos((xx - 32) / 32 * np.pi / 2)
    for y in range(4, T, 12):
        img[y:y + 2, :] = 0.15
    out[10] = wear(img, 11, 0.15)
    # 11 stencil decal: chevrons over plate
    img = out[0].copy()
    for k in range(3):
        for x in range(10, 54):
            y = 20 + k * 10 + abs(x - 32) // 2
            if y < T - 2:
                img[y:y + 3, x] = 0.15
    out[11] = img
    # 12 rotor disc (alpha drawn by the renderer as the dark of this tile)
    r = np.hypot(xx - 31.5, yy - 31.5)
    a = np.arctan2(yy - 31.5, xx - 31.5)
    img = np.where(r < 31, 0.25 + 0.5 * (np.cos(a * 3) ** 8) * (r / 31), 0.0)
    img = np.where(r < 5, 0.6, img)
    out[12] = img
    # 13 thruster ring (glows)
    img = np.exp(-((r - 20) / 6.0) ** 2) + 0.6 * np.exp(-(r / 10.0) ** 2)
    out[13] = np.clip(img, 0, 1)
    # 14 scorched / scratched dark metal
    out[14] = wear(np.full((T, T), 0.42) * (0.7 + 0.3 * noise(6, 4, 15)), 16, 0.4)
    # 15 honeycomb mesh
    img = np.full((T, T), 0.55)
    for cy in range(-8, T + 8, 14):
        for cx in range(-8, T + 8, 16):
            ox = 8 if (cy // 14) % 2 else 0
            m = np.hypot(xx - cx - ox, (yy - cy) * 1.15) < 6
            img[m] = 0.18
    out[15] = wear(img, 17, 0.12)
    # 16 visor: a bright slit across dark glass (glows)
    img = np.full((T, T), 0.05)
    img[26:38, 4:60] = 0.5 + 0.5 * np.exp(-((yy[26:38, 4:60] - 32) / 3.0) ** 2)
    out[16] = img
    # 17 warning lamp / beacon (glows)
    out[17] = np.clip(np.exp(-(r / 14.0) ** 2) * 1.2, 0, 1)
    return out


def main():
    atlas = np.zeros((8 * T, 8 * T, 4), dtype=np.uint8)
    for i, img in tiles().items():
        x0, y0 = (i % 8) * T, (i // 8) * T
        g = (np.clip(img, 0, 1) * 255).astype(np.uint8)
        atlas[y0:y0 + T, x0:x0 + T, 0] = g
        atlas[y0:y0 + T, x0:x0 + T, 1] = g
        atlas[y0:y0 + T, x0:x0 + T, 2] = g
        alpha = 255 if i != 12 else (np.clip(img * 2.2, 0, 1) * 255).astype(np.uint8)
        atlas[y0:y0 + T, x0:x0 + T, 3] = alpha
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    Image.fromarray(atlas, 'RGBA').save(OUT)


if __name__ == '__main__':
    main()
