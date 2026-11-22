#!/usr/bin/env python3
"""Crop textures of the food expansion (overlay jars/04-foods), drawn after the 'look' notes in cuisine_spec.py:

  textures/block/crop/<crop>_stage0..3.png   the four growth stages (0 sprout, 1 young leaves, 2 nearly grown,
                                             3 mature with the produce showing); 'crop' model crops are a row of
                                             plants for the four wheat-like planes, 'cross' crops one bush
  textures/item/<crop>_seeds.png             the seeds (every crop's seeds differ in colour and shape)
  textures/item/<produce>.png                the produce

Every texture is 16x16 pixel art written as a character grid: each letter is a material (a five-tone ramp:
outline, dark, mid, light, highlight) that is shaded automatically with light from the top left (a rounded
height field per region), or a fixed tone of a ramp. Items get a dark outline, plants none; plants stand on the
bottom row. Deterministic (no randomness, no timestamps). Run from anywhere:
    python3 tools/gen/cuisine_crop_textures.py [--sheet review.png] [--only crop,crop]
"""
import colorsys
import math
import os
import sys
import textwrap

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import cuisine_spec as SPEC  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
TEX = os.path.join(ROOT, 'jars/04-foods/src/main/resources/assets/heliosphere/textures')

# =====================================================================================================================
# colour ramps


def hx(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _toward(h, target, amt):
    d = (target - h + 0.5) % 1.0 - 0.5
    return target if abs(d) <= amt else (h + math.copysign(amt, d)) % 1.0


def derive(base):
    """Five tones from a mid colour: shadows cooler and more saturated, lights warmer and paler."""
    r, g, b = (v / 255 for v in hx(base))
    h, s, v = colorsys.rgb_to_hsv(r, g, b)

    def mk(dv, ds, target, dh):
        hh = _toward(h, target, dh) if s > 0.06 else h
        return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hh, min(1.0, s * ds), min(1.0, v * dv)))
    return [mk(0.40, 1.22, 0.70, 0.05), mk(0.66, 1.12, 0.70, 0.025), hx(base), mk(1.20, 0.86, 0.16, 0.02),
            mk(1.42, 0.58, 0.16, 0.04)]


class Ramp:
    """Outline, dark, mid, light, highlight. One colour: derived. gloss: auto highlight where the light hits."""

    def __init__(self, *tones, gloss=None):
        if len(tones) == 1:
            tones = derive(tones[0])
        assert len(tones) == 5, tones
        self.t = [hx(t) if isinstance(t, str) else tuple(t) for t in tones]
        self.gloss = gloss


# =====================================================================================================================
# grids


def grid(s, w=16, h=16, bottom=True):
    """Parse a character grid; shorter grids are padded with empty rows (at the top when bottom=True)."""
    lines = textwrap.dedent(s).strip('\n').split('\n')
    lines = [ln.strip() for ln in lines]
    for ln in lines:
        assert len(ln) == w, (len(ln), ln)
    assert len(lines) <= h, len(lines)
    pad = ['.' * w] * (h - len(lines))
    return pad + lines if bottom else lines + pad


def sprite(s):
    """A small sprite of any size (rows of equal width)."""
    lines = [ln.strip() for ln in textwrap.dedent(s).strip('\n').split('\n')]
    assert len({len(ln) for ln in lines}) == 1, lines
    return lines


def blank():
    return ['.' * 16 for _ in range(16)]


def stamp(g, spr, x, y, mirror=False):
    """Copy the non-empty cells of a sprite onto a grid at (x, y); off-grid cells are dropped."""
    g = [list(r) for r in g]
    for j, row in enumerate(spr):
        if mirror:
            row = row[::-1]
        for i, c in enumerate(row):
            if c != '.' and 0 <= y + j < 16 and 0 <= x + i < 16:
                g[y + j][x + i] = c
    return [''.join(r) for r in g]


def mirror(g):
    return [r[::-1] for r in g]


# =====================================================================================================================
# shading

LIGHT = np.array([-0.52, -0.66, 0.54])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def render(g, pal, outline=None, ground=False, th=(0.34, 0.80)):
    """g: 16 strings; pal: char -> Ramp (auto shaded region) or (Ramp, tone) (fixed tone).
    outline: None, 'in' (the silhouette's edge pixels become the outline) or 'out' (an outline is added around).
    ground: the plant continues below the bottom edge (stems are not lit as ends there)."""
    H = W = 16
    cells = [list(r) for r in g]
    for row in cells:
        for c in row:
            assert c == '.' or c in pal, c
    ramp = {}
    fixed = {}
    for c, v in pal.items():
        if isinstance(v, tuple):
            ramp[c], fixed[c] = v
        else:
            ramp[c] = v
    S = np.array([[c != '.' for c in row] for row in cells])
    out = np.zeros((H, W, 4), dtype=np.uint8)
    O = np.zeros((H, W), dtype=bool)
    ochar = {}
    if outline == 'in':
        for y in range(H):
            for x in range(W):
                if S[y, x] and any(not (0 <= x + dx < W and 0 <= y + dy < H) or not S[y + dy, x + dx]
                                   for dx, dy in N4):
                    O[y, x] = True
                    ochar[(x, y)] = cells[y][x]
    elif outline == 'out':
        for y in range(H):
            for x in range(W):
                if S[y, x]:
                    continue
                near = [cells[y + dy][x + dx] for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0))
                        if 0 <= x + dx < W and 0 <= y + dy < H and S[y + dy, x + dx]]
                if near:
                    O[y, x] = True
                    ochar[(x, y)] = max(near, key=near.count)
    body = S & ~O
    tone = np.full((H, W), -1)
    for (x, y), c in ochar.items():
        tone[y, x] = 0
    chars = np.array(cells)
    fixed_mask = np.zeros((H, W), dtype=bool)
    for c, t in fixed.items():
        m = body & (chars == c)
        tone[m] = t
        fixed_mask |= m
    ys, xs = np.mgrid[0:H, 0:W]
    for c in sorted(set(chars[body].tolist()) - set(fixed)):
        same_ramp = np.zeros((H, W), dtype=bool)
        for c2 in fixed:
            if ramp[c2] is ramp[c]:
                same_ramp |= chars == c2
        inside = body & ((chars == c) | (fixed_mask & same_ramp))
        seen = np.zeros((H, W), dtype=bool)
        for y0, x0 in zip(*np.nonzero(body & (chars == c))):
            if seen[y0, x0]:
                continue
            comp = np.zeros((H, W), dtype=bool)
            stack = [(y0, x0)]
            seen[y0, x0] = comp[y0, x0] = True
            while stack:
                y, x = stack.pop()
                for dx, dy in N4:
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < H and 0 <= xx < W and inside[yy, xx] and not comp[yy, xx]:
                        comp[yy, xx] = True
                        if chars[yy, xx] == c:
                            seen[yy, xx] = True
                        stack.append((yy, xx))
            # distance to the region's edge (image borders count as edges, the bottom one not for plants)
            oy, ox = np.nonzero(~comp)
            py, px = np.nonzero(comp)
            if len(oy):
                d = np.sqrt((py[:, None] - oy[None, :]) ** 2 + (px[:, None] - ox[None, :]) ** 2).min(axis=1)
            else:
                d = np.full(len(py), 99.0)
            border = np.minimum(px + 1, W - px)
            border = np.minimum(border, py + 1)
            if not ground:
                border = np.minimum(border, H - py)
            d = np.minimum(d, border)
            R = d.max()
            hgt = np.zeros((H, W))
            hgt[py, px] = np.sqrt(np.maximum(0.0, R * R - (R - d) ** 2))

            def hv(y, x, y_, x_):
                if not (0 <= x_ < W):
                    return 0.0
                if y_ >= H:
                    return hgt[y, x] if ground else 0.0
                if y_ < 0:
                    return 0.0
                return hgt[y_, x_] if comp[y_, x_] else 0.0
            for y, x in zip(py, px):
                if chars[y, x] != c:
                    continue
                gx = (hv(y, x, y, x + 1) - hv(y, x, y, x - 1)) / 2
                gy = (hv(y, x, y + 1, x) - hv(y, x, y - 1, x)) / 2
                n = np.array([-gx, -gy, 1.0])
                s = float(n @ LIGHT / np.linalg.norm(n))
                t = 1 if s <= th[0] else 2 if s <= th[1] else 3
                if ramp[c].gloss is not None and s > ramp[c].gloss:
                    t = 4
                tone[y, x] = t
    for y in range(H):
        for x in range(W):
            if tone[y, x] >= 0:
                c = ochar.get((x, y), cells[y][x])
                out[y, x, :3] = ramp[c].t[tone[y, x]]
                out[y, x, 3] = 255
    return Image.fromarray(out, 'RGBA')
