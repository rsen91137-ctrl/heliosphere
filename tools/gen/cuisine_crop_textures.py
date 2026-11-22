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
SPEC = np.array([-0.36, -0.46, 0.81])
SPEC = SPEC / np.linalg.norm(SPEC)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def render(g, pal, outline=None, ground=False, th=(0.34, 0.80), seam='', seam_tone=0, bare=''):
    """g: 16 strings; pal: char -> Ramp (auto shaded region) or (Ramp, tone) (fixed tone).
    outline: None, 'in' (the silhouette's edge pixels become the outline) or 'out' (an outline is added around).
    ground: the plant continues below the bottom edge (stems are not lit as ends there).
    seam: region characters from back to front; where a region touches one further front, its edge gets the
    outline tone (seam_tone; separates touching objects of one material).
    bare: characters that never get or cause an outline (thin details such as awns, roots, loose seeds)."""
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
                if S[y, x] and cells[y][x] not in bare and any(
                        not (0 <= x + dx < W and 0 <= y + dy < H) or not S[y + dy, x + dx] for dx, dy in N4):
                    O[y, x] = True
                    ochar[(x, y)] = cells[y][x]
    elif outline == 'out':
        for y in range(H):
            for x in range(W):
                if S[y, x]:
                    continue
                near = [cells[y + dy][x + dx] for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0))
                        if 0 <= x + dx < W and 0 <= y + dy < H and S[y + dy, x + dx]
                        and cells[y + dy][x + dx] not in bare]
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
                n = n / np.linalg.norm(n)
                s = float(n @ LIGHT)
                t = 1 if s <= th[0] else 2 if s <= th[1] else 3
                if ramp[c].gloss is not None and float(n @ SPEC) > ramp[c].gloss:
                    t = 4
                if c in seam and any(0 <= x + dx < W and 0 <= y + dy < H and chars[y + dy, x + dx] in seam
                                     and seam.index(chars[y + dy, x + dx]) > seam.index(c) for dx, dy in N4):
                    t = seam_tone
                tone[y, x] = t
    for y in range(H):
        for x in range(W):
            if tone[y, x] >= 0:
                c = ochar.get((x, y), cells[y][x])
                out[y, x, :3] = ramp[c].t[tone[y, x]]
                out[y, x, 3] = 255
    return Image.fromarray(out, 'RGBA')


# =====================================================================================================================
# drawing on character grids


def _bezier(x0, y0, cx, cy, x1, y1):
    """Pixel-perfect 8-connected raster of a quadratic curve through pixel centres."""
    pts = []
    n = max(8, int(4 * (abs(x1 - x0) + abs(y1 - y0) + abs(cx - x0) + abs(cy - y0))))
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t * t * x1
        y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t * t * y1
        p = (math.floor(x + 0.5), math.floor(y + 0.5))
        if pts and pts[-1] == p:
            continue
        if len(pts) >= 2 and abs(p[0] - pts[-2][0]) <= 1 and abs(p[1] - pts[-2][1]) <= 1:
            pts[-1] = p          # drop the corner pixel of an L step
            if pts[-2] == p:
                pts.pop()
            continue
        pts.append(p)
    return pts


class Pic:
    """A mutable 16x16 character grid."""

    def __init__(self, rows=None):
        self.c = [list(r) for r in (rows or blank())]

    def rows(self):
        return [''.join(r) for r in self.c]

    def get(self, x, y):
        return self.c[y][x] if 0 <= x < 16 and 0 <= y < 16 else '.'

    def put(self, x, y, ch, under=False):
        if 0 <= x < 16 and 0 <= y < 16 and (not under or self.c[y][x] == '.'):
            self.c[y][x] = ch
        return self

    def pts(self, pts, ch, under=False):
        for x, y in pts:
            self.put(x, y, ch, under)
        return self

    def curve(self, x0, y0, x1, y1, ch, bend=0.0, under=False):
        """A 1px curve from (x0, y0) to (x1, y1); bend pushes the middle sideways (pixels, + = to the right
        of the direction of travel)."""
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        cx, cy = mx - dy / ln * bend * 2, my + dx / ln * bend * 2
        return self.pts(_bezier(x0, y0, cx, cy, x1, y1), ch, under)

    def ellipse(self, cx, cy, rx, ry, ch, under=False, slack=0.0):
        """Filled ellipse; cx, cy in pixel units (7.5 = centre of pixel 7)."""
        for y in range(16):
            for x in range(16):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0 + slack:
                    self.put(x, y, ch, under)
        return self

    def rellipse(self, cx, cy, a, b, deg, ch, under=False, slack=0.0):
        """Filled ellipse with semi-axes a (along the angle, degrees, 0 = right, 90 = up) and b."""
        ca, sa = math.cos(math.radians(deg)), -math.sin(math.radians(deg))
        for y in range(16):
            for x in range(16):
                u = (x - cx) * ca + (y - cy) * sa
                v = -(x - cx) * sa + (y - cy) * ca
                if (u / a) ** 2 + (v / b) ** 2 <= 1.0 + slack:
                    self.put(x, y, ch, under)
        return self

    def line(self, x0, y0, x1, y1, ch, under=False):
        return self.curve(x0, y0, x1, y1, ch, 0.0, under)

    def poly(self, pts, ch, under=False):
        """Filled polygon (vertices in pixel units, pixel centres sampled)."""
        k = len(pts)
        for y in range(16):
            for x in range(16):
                inside = False
                for i in range(k):
                    x1, y1 = pts[i]
                    x2, y2 = pts[(i + 1) % k]
                    if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                        inside = not inside
                if inside:
                    self.put(x, y, ch, under)
        return self

    def stamp(self, spr, x, y, mirror=False, under=False, sub=None):
        for j, row in enumerate(spr):
            if mirror:
                row = row[::-1]
            for i, c in enumerate(row):
                if c != '.':
                    self.put(x + i, y + j, (sub or {}).get(c, c), under)
        return self

    def sub(self, mapping):
        for row in self.c:
            for i, c in enumerate(row):
                row[i] = mapping.get(c, c)
        return self

    def where(self, ch):
        return [(x, y) for y in range(16) for x in range(16) if self.c[y][x] == ch]


# =====================================================================================================================
# materials (outline, dark, mid, light, highlight)

# foliage
LEAF = Ramp('#1b3d12', '#2c6119', '#3f8424', '#5ca634', '#8fcc5c')
LEAF_BK = Ramp('#132d0d', '#1f4813', '#2d631d', '#3f8129', '#5f9f43')
LEAF_DK = Ramp('#0c2612', '#15401c', '#215b29', '#317739', '#4f984f')
LEAF_DK_BK = Ramp('#081c0d', '#0e2f14', '#16441d', '#225a29', '#3a7440')
LEAF_BLUE = Ramp('#15382f', '#255a4b', '#387b66', '#559d84', '#8ac2aa')
LEAF_BLUE_BK = Ramp('#0e2922', '#1a4438', '#29604f', '#3c7d69', '#629f89')
LEAF_GREY = Ramp('#1f3c26', '#345e3b', '#4d8354', '#6ea572', '#9ec69c')
LEAF_BRIGHT = Ramp('#255010', '#367514', '#4f9d1d', '#74c331', '#a9e562')
LEAF_BRIGHT_BK = Ramp('#1b3d0c', '#2a5c10', '#3c7d17', '#56a025', '#80c64a')
LEAF_YG = Ramp('#2c4a13', '#456c1d', '#62922a', '#86b63d', '#b6d670')
LEAF_YG_BK = Ramp('#203810', '#345418', '#4a7422', '#649530', '#8cb655')
LEAF_PALE = Ramp('#47652e', '#72934d', '#9cbd6f', '#c4de96', '#e7f4c4')
LEAF_GLOSS = Ramp('#0d2711', '#18431e', '#25612c', '#388342', '#7cc47a', gloss=0.975)
LEAF_GLOSS_BK = Ramp('#091d0d', '#113217', '#1a4822', '#286131', '#4f8a55')
LEAF_TOM = Ramp('#18361a', '#28562a', '#3a7a38', '#559c4c', '#86c27a')
LEAF_TOM_BK = Ramp('#102714', '#1c401f', '#29592b', '#3a763a', '#5c975a')
STEM = Ramp('#2a3a14', '#476124', '#658834', '#88ab48', '#b4cf79')
STEM_PURPLE = Ramp('#2a1430', '#4b2650', '#6b3e6d', '#8b5a89', '#b28caf')
STEM_RED = Ramp('#4a1222', '#7e2238', '#ad3a52', '#d06276', '#ec9eac')
WOOD = Ramp('#2c1c0f', '#4f341c', '#73502d', '#966f43', '#ba9567')
WOODY = Ramp('#271a12', '#45301f', '#644930', '#846545', '#ab8d6c')
# produce
RICE_WHITE = Ramp('#5f6676', '#9ba3b3', '#cdd2db', '#e9ebef', '#ffffff')
GOLD = Ramp('#5c3c10', '#97691f', '#c69833', '#e4be4f', '#f7e28a')
GOLD_BK = Ramp('#4a300c', '#7c5518', '#a67c28', '#c69c3c', '#e4c46c')
PANICLE = Ramp('#3c5418', '#617e26', '#88a63a', '#adc858', '#d6e696')
STRAW = Ramp('#694f1e', '#9f8134', '#c7a851', '#e2c776', '#f4e3a6')
SOY = Ramp('#6e5524', '#b39548', '#dcc277', '#efdc9f', '#fbf2cf', gloss=0.985)
HILUM = Ramp('#3a2410', '#5e3c1c', '#7e5428', '#9c6c38', '#bc8c58')
SOY_POD = Ramp('#46461c', '#76752f', '#a19f4b', '#c6c270', '#e5e1a5')
SOY_POD_GREEN = Ramp('#2a4614', '#45701e', '#62992c', '#86bc44', '#b4dc7c')
CORN = Ramp('#6a4006', '#ae7512', '#e0a71d', '#f6cd3e', '#fff096')
HUSK = Ramp('#2c4815', '#4b7225', '#709e39', '#9ac35a', '#c9e393')
HUSK_BK = Ramp('#22380f', '#3a5a1b', '#57802b', '#77a240', '#a4c672')
SILK = Ramp('#6a4e1c', '#a7863a', '#d6b85a', '#efda86', '#fff4c4')
TASSEL = Ramp('#5e4a1c', '#937a36', '#c0a456', '#dcc47e', '#f2e2b2')
PEANUT = Ramp('#5a3b1b', '#936533', '#bf8d53', '#dbb179', '#f1d7a8')
PEANUT_RED = Ramp('#4e1414', '#86272a', '#b44246', '#d4686a', '#eea4a0')
SESAME = Ramp('#6c5a36', '#b09a6c', '#dccca0', '#f2e8c8', '#fffaea')
BLACK = Ramp('#0a0a0e', '#1c1c22', '#2e2e36', '#4a4a56', '#8a8a9a')
TOMATO = Ramp('#560d0f', '#a01f1b', '#d33929', '#ef6a48', '#ffd4bc', gloss=0.975)
TOMATO_GREEN = Ramp('#2e4a12', '#4f7a1c', '#73a52c', '#98c844', '#cfe98c', gloss=0.975)
CHILI = Ramp('#5a090c', '#a51319', '#dc2923', '#ff5b3b', '#ffc4ac', gloss=0.975)
CHILI_GREEN = Ramp('#1e4010', '#356a18', '#4e9222', '#70b636', '#b0e07c', gloss=0.975)
BELL = Ramp('#123e0c', '#257014', '#3e9d20', '#64c634', '#d0f6a8', gloss=0.965)
BELL_CAP = Ramp('#1c3410', '#2f5418', '#467424', '#5f9432', '#90bc62')
EGGPLANT = Ramp('#1c0a24', '#3c134e', '#5c226e', '#823e94', '#d6b0e6', gloss=0.965)
EGG_CAP = Ramp('#1e3512', '#33571c', '#4b7a29', '#679c3a', '#9cc672')
EGG_FLOWER = Ramp('#3a1e5a', '#683a92', '#9264c2', '#b68ede', '#dec8f4')
CUCUMBER = Ramp('#0e2c10', '#1b4719', '#2b6725', '#468935', '#86bd6c')
FLOWER_YELLOW = Ramp('#6a4800', '#bf8800', '#efbf1e', '#ffdf4e', '#fff7b0')
FLOWER_WHITE = Ramp('#6a7068', '#b8beb4', '#e7ebe3', '#f8faf6', '#ffffff')
FLOWER_CENTRE = Ramp('#6a4800', '#b88400', '#e8b81c', '#f8d84a', '#fff4a0')
CABBAGE = Ramp('#5f6e48', '#a3b386', '#d8e2bc', '#f0f5dd', '#ffffff')
LETTUCE = Ramp('#285612', '#3c7e16', '#58a920', '#7fcc36', '#b4ea6a')
LETTUCE_HEART = Ramp('#3e6a14', '#5c921e', '#84bc2e', '#aedb4e', '#dcf490')
SPINACH = Ramp('#0b2814', '#14421e', '#1f5e2b', '#2f7c3c', '#55a05a')
PINK = Ramp('#561431', '#93284f', '#c64873', '#e57698', '#f6b6c8')
ONION = Ramp('#47240f', '#83461b', '#b56e2b', '#da9946', '#f2ca82')
PAPER = Ramp('#5e4626', '#9a7c4e', '#c6a874', '#e2cc9c', '#f6ead0')
ROOTLET = Ramp('#6a5e48', '#a8987a', '#d4c6a6', '#ece2c8', '#fbf6e8')
GARLIC = Ramp('#675d56', '#ada397', '#dfd7cb', '#f4f0e8', '#ffffff')
GARLIC_PURPLE = Ramp('#4c2c46', '#7c4c72', '#a87498', '#c89ab8', '#e6cade')
GINGER = Ramp('#5a3f1f', '#97703f', '#c79f63', '#e1c389', '#f4e1b6')
GINGER_PINK = Ramp('#6a2a30', '#a8485a', '#d4707e', '#eca0a8', '#f8d0d4')
SCALLION_WHITE = Ramp('#687858', '#b6c2a6', '#e4ecda', '#f7faf1', '#ffffff')
SCALLION = Ramp('#1d4a14', '#2e721c', '#459c28', '#65c03c', '#a2e07a')
DAIKON = Ramp('#6e786a', '#b6beb0', '#e2e7dd', '#f5f7f2', '#ffffff')
DAIKON_SHOULDER = Ramp('#4f6a36', '#83a45a', '#b0cf84', '#d0e8a8', '#eef8d8')
SWEET_POTATO = Ramp('#3a0d1d', '#6b1d33', '#983146', '#bf4f5f', '#e48e98', gloss=0.99)
TARO = Ramp('#2c1b0f', '#52351d', '#77532f', '#9a7347', '#c29f79')
TARO_BUD = Ramp('#4a1c3a', '#7e3462', '#ac548a', '#cc7eaa', '#ecbcd6')
STRAWBERRY = Ramp('#5c0b1b', '#a6172d', '#da2b3f', '#f35b63', '#ffc2c2', gloss=0.975)
STRAW_SEED = Ramp('#6a5000', '#b89000', '#f0c828', '#ffe45a', '#fff6b8')
UNRIPE_BERRY = Ramp('#4a6230', '#7a9a50', '#a8c878', '#d0e6a6', '#f0f8dc')
GRAPE = Ramp('#1d0b29', '#3b1950', '#5b2a74', '#7e449a', '#caa2e2', gloss=0.975)
GRAPE_GREEN = Ramp('#3a5418', '#5e8226', '#84ac3a', '#a8cc58', '#d4ec98', gloss=0.975)
BLUEBERRY = Ramp('#121834', '#243266', '#395092', '#677db6', '#b6c4e2', gloss=0.985)
BLUE_UNRIPE = Ramp('#4a2a3a', '#7e4a5e', '#a87084', '#c898a8', '#e8ccd6')
PINEAPPLE = Ramp('#583808', '#9e6813', '#d29823', '#efc243', '#fbe88e')
PINE_LEAF = Ramp('#1e3a2a', '#335e42', '#4b8058', '#6aa274', '#9cc6a0')
PINE_FLOWER = Ramp('#4a1e3a', '#86345e', '#b85486', '#d880a8', '#f2bcd4')
TEA_TIP = Ramp('#38590f', '#5b8b1b', '#82b52a', '#a9d94a', '#d8f28c')
COFFEE_CHERRY = Ramp('#480a10', '#88141c', '#c1222f', '#e44a4a', '#ffb0a0', gloss=0.975)
COFFEE_GREEN = Ramp('#2e4614', '#4c7020', '#6c9a2e', '#90bc46', '#c4e080')
ROAST = Ramp('#1e0f08', '#3e2010', '#5e341c', '#7e4c2c', '#b8866a', gloss=0.975)
GREEN_COFFEE = Ramp('#4a5236', '#7c8660', '#a8b088', '#c8ceac', '#e6eadc')
SICHUAN = Ramp('#47130a', '#862312', '#b53c1d', '#d6623a', '#f2a07e')
SICHUAN_GREEN = Ramp('#2a4214', '#466a1e', '#66922c', '#8ab444', '#bcd884')
MUNG = Ramp('#1f3c10', '#3a6a1a', '#58962a', '#7cbc40', '#b8e07a')
MUNG_POD = Ramp('#0a130b', '#162618', '#233c26', '#365838', '#587a58')
BARLEY = Ramp('#5e4414', '#977224', '#c6a03a', '#e2c45a', '#f6e496')
CHIVE = Ramp('#0b2a12', '#15441c', '#20602a', '#2f7e39', '#55a25a')
CHIVE_FLOWER = FLOWER_WHITE
TIE = Ramp('#4a1a10', '#86301c', '#b8492c', '#d86c48', '#f2a684')
SOIL = Ramp('#24170e', '#3f2a1a', '#5c3f27', '#7a5638', '#9c7652')


# =====================================================================================================================
# shapes shared by several textures

CIRCLE4 = sprite('''
.xx.
xxxx
xxxx
.xx.
''')
CIRCLE5 = sprite('''
.xxx.
xxxxx
xxxxx
xxxxx
.xxx.
''')
CIRCLE6 = sprite('''
.xxxx.
xxxxxx
xxxxxx
xxxxxx
xxxxxx
.xxxx.
''')
CIRCLE7 = sprite('''
..xxx..
.xxxxx.
xxxxxxx
xxxxxxx
xxxxxxx
.xxxxx.
..xxx..
''')


def item(rows, pal, mode='in', seam='', **kw):
    return (rows if isinstance(rows, list) else grid(rows), pal, mode, seam, kw)


def interior(p, x, y):
    """True when all four neighbours of (x, y) are drawn (detail pixels stay off the outline)."""
    return all(p.get(x + dx, y + dy) != '.' for dx, dy in N4)


# =====================================================================================================================
# produce


def heap(p, cx, top, bottom, rx, ch):
    """A mound standing on row 'bottom' with its peak at row 'top'."""
    ry = bottom - top + 0.5
    for y in range(top, bottom + 1):
        for x in range(16):
            if ((x - cx) / rx) ** 2 + ((y - bottom - 0.5) / ry) ** 2 <= 1.0:
                p.put(x, y, ch)
    return p


def produce_rice():
    p = heap(Pic(), 7.5, 5, 14, 7.2, 'w')
    for x, y in ((6, 6), (9, 7), (3, 8), (7, 9), (11, 10), (2, 11), (7, 12), (11, 13), (4, 13)):
        if interior(p, x, y) and interior(p, x + 1, y):
            p.put(x, y, 'h').put(x + 1, y, 'h')
            if p.get(x + 2, y + 1) == 'w' and interior(p, x + 2, y + 1):
                p.put(x + 2, y + 1, 'd')
    return item(p.rows(), {'w': RICE_WHITE, 'd': (RICE_WHITE, 1), 'h': (RICE_WHITE, 4)})


def produce_soybean():
    p = Pic()
    p.stamp(CIRCLE7, 4, 1, sub={'x': 'a'})
    p.stamp(CIRCLE7, 1, 7, sub={'x': 'b'})
    p.stamp(CIRCLE7, 8, 7, sub={'x': 'c'})
    p.put(9, 5, 'e').put(5, 11, 'e').put(12, 11, 'e')
    return item(p.rows(), {'a': SOY, 'b': SOY, 'c': SOY, 'e': (HILUM, 1)}, seam='abc')


def produce_corn():
    p = Pic(grid("""
    ................
    ............yy..
    ..........yyyyy.
    .........yyyyyy.
    ........yyyyyyy.
    .......yyyyyyy..
    ...h..yyyyyyy...
    ...hh.yyyyyy.k..
    ...hhhyyyyykkk..
    ...hhhhyyykkk...
    ...hhhhhykkk....
    ..hhhhhhkkk.....
    ..hhhhhhkk......
    .shhhhhk........
    ss..............
    s...............
    """))
    for x, y in p.where('y'):
        if (x + y) % 2 == 0 and (x - y) % 4 == 0 and interior(p, x, y):
            p.put(x, y, 'Y')
    return item(p.rows(), {'y': CORN, 'Y': (CORN, 1), 'h': HUSK, 'k': HUSK_BK, 's': STEM}, seam='kyh')


def produce_peanut():
    p = Pic()
    for c, (x1, y1), (x2, y2) in (('a', (4.0, 8.5), (7.7, 4.8)), ('b', (8.3, 12.3), (12.0, 8.6))):
        p.rellipse(x1, y1, 2.9, 2.4, 45, c)
        p.rellipse(x2, y2, 2.9, 2.4, 45, c)
    for x, y in p.where('a') + p.where('b'):
        if x % 3 == 1 and y % 3 == 1 and interior(p, x, y):
            p.put(x, y, 'n')
    return item(p.rows(), {'a': PEANUT, 'b': PEANUT, 'n': (PEANUT, 1)}, seam='ab')


def produce_sesame():
    p = heap(Pic(), 7.5, 7, 14, 7.3, 'c')
    for x, y in ((5, 9), (10, 9), (3, 11), (8, 11), (12, 11), (6, 12), (10, 13)):
        p.put(x, y, 'k').put(x, y + 1, 'k')
    for x, y in ((7, 8), (8, 9), (4, 10), (11, 10), (6, 11), (9, 12), (2, 13), (13, 13)):
        if p.get(x, y) == 'c' and interior(p, x, y):
            p.put(x, y, 'l')
    return item(p.rows(), {'c': SESAME, 'l': (SESAME, 4), 'k': (BLACK, 2)})


def produce_tomato():
    return item("""
    ................
    ................
    ........s.......
    .......ss.......
    ....gg.gg.gg....
    ..rrrggggggrrr..
    .rrrgrrggrrgrrr.
    .rrrrrrrrrrrrrr.
    .rrrrrrrrrrrrrr.
    .rrrrrrrrrrrrrr.
    .rrrrrrrrrrrrrr.
    .rrrrrrrrrrrrrr.
    ..rrrrrrrrrrrr..
    ...rrrrrrrrrr...
    .....rrrrrr.....
    ................
    """, {'r': TOMATO, 'g': LEAF, 's': STEM})


def produce_chili():
    return item("""
    ................
    .............s..
    ............s...
    ...........s....
    .........ggg....
    ........grrg....
    .......rrrr.....
    ......rrrr......
    .....rrrr.......
    ....rrrr........
    ....rrr.........
    ...rrr..........
    ...rr...........
    ...rr...........
    ....r...........
    ................
    """, {'r': CHILI, 'g': LEAF, 's': STEM}, mode='out')


def produce_bell_pepper():
    return item("""
    ................
    ........ss......
    .......ss.......
    ....bbkkkkdd....
    ..bbbbbkkddddd..
    .bbbbbcccccdddd.
    .bbbbcccccccddd.
    .bbbbcccccccddd.
    .bbbbcccccccddd.
    .bbbbcccccccddd.
    .bbbbcccccccddd.
    .bbbbcccccccddd.
    .bbbbcccccccddd.
    ..bbbbcccccddd..
    ...bb..ccc..dd..
    ................
    """, {'b': BELL, 'c': BELL, 'd': BELL, 'k': BELL_CAP, 's': STEM}, seam='bdck')


def produce_eggplant():
    p = Pic()
    p.rellipse(6.0, 10.0, 6.6, 3.6, 45, 'p')
    p.pts([(9, 4), (10, 3), (11, 3), (12, 3), (10, 4), (11, 4), (12, 4), (13, 4), (9, 5), (10, 5), (11, 5), (12, 5),
           (8, 5), (7, 6), (11, 6), (12, 6), (10, 7)], 'g')
    p.pts([(12, 2), (13, 1), (13, 2)], 's')
    return item(p.rows(), {'p': EGGPLANT, 'g': EGG_CAP, 's': STEM}, seam='pg')


def produce_cucumber():
    p = Pic()
    p.rellipse(7.5, 7.5, 7.6, 2.7, 45, 'c')
    for x, y in p.where('c'):
        if (x + 2 * y) % 5 == 0 and (x - y) % 2 == 0 and interior(p, x, y):
            p.put(x, y, 'l')
    p.pts([(14, 1), (13, 1)], 's')
    return item(p.rows(), {'c': CUCUMBER, 'l': (CUCUMBER, 3), 's': STEM})


def produce_napa_cabbage():
    p = Pic(grid("""
    ................
    ......k.kk......
    ....kkkkkkkk....
    ...kkKkkkKkkk...
    ...gkkkkKkkkh...
    ..ggGgkkkkhhhh..
    ..gggggkkhhHhh..
    ..gGggggxhhhhh..
    ..wgggggxhhhhv..
    ..wwgGggxhhhvv..
    ..wwwgggxhhvvv..
    ..wwwwggxhvvvv..
    ...wwwwwxvvvv...
    ...wwwwwxvvvv...
    ....wwwwxvvv....
    ................
    """))
    return item(p.rows(), {'k': LEAF_PALE, 'K': (LEAF_PALE, 1), 'g': LEAF_PALE, 'h': LEAF_PALE, 'G': (LEAF_PALE, 1),
                           'H': (LEAF_PALE, 1), 'w': CABBAGE, 'v': CABBAGE, 'x': (CABBAGE, 1)},
                seam='kghwv', seam_tone=1)


def produce_lettuce():
    return item("""
    ................
    ................
    ......jjjj......
    ....jjjjjjjj....
    ...jjjjjjjjjj...
    ..hjjjjjjjjjjh..
    .hhhjjjjjjjjhhh.
    .hhhhjjjjjjhhhh.
    hhhhhhjjjjhhhhhh
    hhhhhhhjjhhhhhhh
    ghhhhhhhhhhhhhhg
    gggghhhhhhhhgggg
    .gggggghhgggggg.
    ..ggggggggggggg.
    ...g.gg.gg.gg...
    ................
    """, {'g': LETTUCE, 'h': LETTUCE, 'j': LETTUCE_HEART}, seam='jhg', seam_tone=1)


def produce_spinach():
    return item("""
    ......bbb.......
    .....bbbbb......
    .aa..bbbbb..cc..
    aaaa.bbbbb.cccc.
    aaaaabbbbbccccc.
    aaaaabbbbbccccc.
    .aaaabbbbbcccc..
    ..aaa.bbb.ccc...
    ....p..p..p.....
    .....p.p.p......
    ......ppp.......
    .......pq.......
    ......qqq.......
    ......qq........
    .......q........
    ................
    """, {'a': SPINACH, 'b': SPINACH, 'c': SPINACH, 'p': PINK, 'q': PINK}, seam='bac')


def produce_onion():
    p = Pic(grid("""
    ................
    ........t.......
    .......tt.......
    .......t........
    ......ooo.......
    ....ooooooo.....
    ...ooooooooo....
    ..ooooooooooo...
    ..ooooooooooo...
    .ooooooooooooo..
    .ooooooooooooo..
    .ooooooooooooo..
    ..ooooooooooo...
    ...ooooooooo....
    .....rrrrr......
    ......r.r.......
    """))
    for x, y in ((6, 5), (5, 6), (4, 7), (4, 8), (4, 9), (4, 10), (5, 11), (6, 12),
                 (9, 5), (10, 6), (10, 7), (11, 8), (11, 9), (11, 10), (10, 11), (9, 12), (7, 6), (7, 7)):
        if interior(p, x, y):
            p.put(x, y, 'O')
    return item(p.rows(), {'o': ONION, 'O': (ONION, 1), 't': PAPER, 'r': ROOTLET})


def produce_garlic():
    p = Pic(grid("""
    ................
    .......tt.......
    .......t........
    ......www.......
    .....wwwww......
    ....wwwwwww.....
    ...wwwwwwwww....
    ..wwwwwwwwwww...
    ..wwwwwwwwwww...
    .wwwwwwwwwwwww..
    .wwwwwwwwwwwww..
    .wwwwwwwwwwwww..
    ..wwwwwwwwwww...
    ...wwwwwwwww....
    ....rrrrrrr.....
    ................
    """))
    for x, y in ((6, 5), (5, 6), (4, 7), (4, 8), (3, 9), (3, 10), (4, 11), (5, 12),
                 (8, 5), (9, 6), (10, 7), (10, 8), (11, 9), (11, 10), (10, 11), (9, 12),
                 (7, 6), (7, 7), (7, 8), (7, 9), (7, 10), (7, 11), (7, 12)):
        if interior(p, x, y):
            p.put(x, y, 'W')
    for x, y in ((5, 11), (6, 12), (8, 12), (9, 11), (2, 11)):
        if interior(p, x, y) and p.get(x, y) == 'w':
            p.put(x, y, 'v')
    return item(p.rows(), {'w': GARLIC, 'W': (GARLIC, 1), 'v': (GARLIC_PURPLE, 2), 't': PAPER, 'r': ROOTLET})


def produce_ginger():
    p = Pic(grid("""
    ................
    ................
    ..........pp....
    .........gggp...
    ...pp....gggg...
    ..pggg...ggg....
    ..gggg..gggg....
    ...gggggggg.....
    ..gggggggggggp..
    .ggggggggggggpp.
    .ggggggggggggg..
    ..ggggggggggg...
    ..pgggg.gggg....
    ...pp....pp.....
    ................
    ................
    """))
    for x, y in ((4, 6), (5, 6), (10, 5), (11, 5), (3, 9), (4, 10), (8, 9), (8, 10), (11, 10), (12, 9)):
        if interior(p, x, y):
            p.put(x, y, 'G')
    return item(p.rows(), {'g': GINGER, 'G': (GINGER, 1), 'p': GINGER_PINK})


def produce_scallion():
    p = Pic()
    for x in range(2, 14):
        for k in (14, 15):
            p.put(x, k - x, 'w' if x < 6 else 'g')
    for x in range(4, 15):
        for k in (18, 19):
            p.put(x, k - x, 'v' if x < 8 else 'h')
    p.put(14, 1, '.').put(13, 0, 'g').put(15, 0, '.')
    p.pts([(1, 13), (1, 14), (2, 14), (0, 14), (3, 15), (3, 16)], 'r')
    p.pts([(3, 15), (4, 15), (2, 15), (5, 16)], 'r')
    return item(p.rows(), {'w': SCALLION_WHITE, 'g': SCALLION, 'v': SCALLION_WHITE, 'h': SCALLION, 'r': (ROOTLET, 2)},
                mode='out', bare='r')


def produce_radish():
    return item("""
    ..........gg....
    .........gggg.hh
    .........gggghhh
    ..........gghhh.
    .........kkhh...
    ........wkkk....
    .......wwwkk....
    ......wwwwww....
    .....wwwwww.....
    ....wwwwww......
    ...wwwwww.......
    ..wwwwww........
    ..wwwww.........
    .wwww...........
    .ww.............
    ................
    """, {'w': DAIKON, 'k': DAIKON_SHOULDER, 'g': LEAF, 'h': LEAF}, seam='wkgh')


def produce_sweet_potato():
    p = Pic()
    p.rellipse(7.5, 8.0, 6.4, 3.4, 35, 's')
    p.pts([(1, 12), (0, 13), (14, 4), (15, 3)], 't')
    for x, y in ((5, 8), (9, 7), (7, 10), (11, 6)):
        if interior(p, x, y):
            p.put(x, y, 'S')
    return item(p.rows(), {'s': SWEET_POTATO, 'S': (SWEET_POTATO, 1), 't': (SWEET_POTATO, 1)}, bare='t')


def produce_taro():
    p = Pic()
    p.ellipse(7.5, 9.0, 5.8, 5.6, 't')
    p.pts([(7, 2), (8, 2), (7, 3), (8, 3), (8, 1)], 'b')
    for x, y in p.where('t'):
        if y in (6, 9, 12) and 0 < x % 4 < 3 and interior(p, x, y):
            p.put(x, y, 'T')
    return item(p.rows(), {'t': TARO, 'T': (TARO, 1), 'b': TARO_BUD})


def produce_strawberry():
    p = Pic(grid("""
    ................
    .......s........
    ....gg.s.gg.....
    ...ggggsgggg....
    ..rrggggggrrr...
    .rrrrrggrrrrrr..
    .rrrrrrrrrrrrr..
    .rrrrrrrrrrrrr..
    .rrrrrrrrrrrrr..
    ..rrrrrrrrrrr...
    ..rrrrrrrrrrr...
    ...rrrrrrrrr....
    ....rrrrrrr.....
    .....rrrrr......
    ......rrr.......
    ................
    """))
    for x, y in p.where('r'):
        if (x + y) % 3 == 0 and (x - y) % 2 == 0 and y > 5 and interior(p, x, y):
            p.put(x, y, 'y')
    return item(p.rows(), {'r': STRAWBERRY, 'g': LEAF, 's': STEM, 'y': (STRAW_SEED, 3)})


def produce_grape():
    p = Pic()
    xs = {0: [2, 5, 8, 11], 1: [3, 6, 9], 2: [5, 8], 3: [6]}
    ys = [3, 6, 9, 12]
    chars = ['ab', 'cd', 'ef', 'gh']
    for r in range(4):
        for i, x in enumerate(xs[r]):
            p.stamp(CIRCLE4, x, ys[r], sub={'x': chars[r][i % 2]})
    p.pts([(8, 0), (8, 1), (8, 2), (7, 2)], 'w')
    return item(p.rows(), {c: GRAPE for c in 'abcdefgh'} | {'w': WOOD}, seam='abcdefgh')


def produce_blueberry():
    p = Pic()
    for x, y, c in ((6, 1, 'a'), (2, 5, 'b'), (8, 6, 'c'), (4, 9, 'd')):
        p.stamp(CIRCLE6, x, y, sub={'x': c})
    for x, y in ((8, 2), (4, 6), (10, 7), (6, 10)):
        p.put(x, y, 'k').put(x + 1, y, 'k')
    return item(p.rows(), {c: BLUEBERRY for c in 'abcd'} | {'k': (BLUEBERRY, 0)}, seam='abcd')


def produce_pineapple():
    p = Pic(grid("""
    ......gg........
    ..h...gg...hh...
    ..hh..ggg.hh....
    ...hh.gg.hh..h..
    ....hgggggh.hh..
    .....gggggggh...
    """, bottom=False))
    p.ellipse(7.5, 10.5, 4.8, 5.0, 'p')
    for x, y in p.where('p'):
        if ((x + y) % 3 == 0 or (x - y) % 3 == 0) and interior(p, x, y):
            p.put(x, y, 'P')
    return item(p.rows(), {'p': PINEAPPLE, 'P': (PINEAPPLE, 1), 'g': PINE_LEAF, 'h': PINE_LEAF}, seam='hgp')


def produce_tea_leaves():
    p = Pic()
    p.rellipse(6.0, 9.5, 5.6, 2.5, 40, 'a')
    p.rellipse(10.5, 7.5, 4.6, 2.1, 100, 'b')
    p.rellipse(11.5, 2.6, 1.6, 1.0, 70, 'j')
    p.curve(2, 13, 9, 6, 'm', 0.3)
    p.curve(10, 11, 11, 4, 'n', -0.2)
    for x, y in p.where('m') + p.where('n'):
        if not interior(p, x, y):
            p.put(x, y, 'a' if p.get(x, y) == 'm' else 'b')
    p.pts([(1, 14), (2, 13)], 's')
    return item(p.rows(), {'a': LEAF_GLOSS, 'b': LEAF_GLOSS, 'm': (LEAF_GLOSS, 3), 'n': (LEAF_GLOSS, 3),
                           'j': TEA_TIP, 's': STEM}, seam='bja')


def produce_coffee_beans():
    p = Pic()
    beans = ((5.0, 5.0, 75, 'a'), (11.0, 6.5, 115, 'b'), (7.5, 11.5, 15, 'c'))
    for cx, cy, deg, ch in beans:
        p.rellipse(cx, cy, 3.7, 2.6, deg, ch)
    for cx, cy, deg, ch in beans:
        ca, sa = math.cos(math.radians(deg)), -math.sin(math.radians(deg))
        for i in range(-6, 7):
            t = i / 6 * 2.4
            off = 0.45 * math.sin(i / 6 * math.pi)
            x, y = cx + ca * t - sa * off, cy + sa * t + ca * off
            xi, yi = math.floor(x + 0.5), math.floor(y + 0.5)
            if p.get(xi, yi) == ch and interior(p, xi, yi):
                p.put(xi, yi, 'x')
    return item(p.rows(), {'a': ROAST, 'b': ROAST, 'c': ROAST, 'x': (ROAST, 0)}, seam='abc')


def produce_sichuan_pepper():
    p = Pic()
    p.curve(8, 0, 8, 7, 'w', 0.2)
    p.curve(8, 4, 3, 9, 'w', -0.3)
    p.curve(8, 5, 13, 10, 'w', 0.3)
    spots = [(3, 2, 'a'), (9, 1, 'b'), (11, 5, 'a'), (5, 6, 'b'), (1, 8, 'a'), (9, 9, 'b'), (5, 11, 'a'), (11, 11, 'c')]
    for x, y, ch in spots:
        p.stamp(CIRCLE4, x, y, sub={'x': ch})
    for x, y in ((5, 3), (12, 6), (2, 9), (10, 10), (6, 12)):
        p.put(x, y, 'k')
    return item(p.rows(), {'a': SICHUAN, 'b': SICHUAN, 'c': SICHUAN, 'k': (BLACK, 1), 'w': WOODY}, seam='wabc')


def produce_mung_bean():
    p = Pic()
    for i, (x, y) in enumerate(((6, 5), (4, 8), (8, 8), (2, 11), (6, 11), (10, 11))):
        p.stamp(['xxx', 'xxx'], x, y, sub={'x': 'abcdef'[i]})
        p.put(x + 1, y, 'l')
    return item(p.rows(), {c: MUNG for c in 'abcdef'} | {'l': (MUNG, 4)}, mode='out')


def produce_barley():
    p = Pic()
    p.rellipse(7.0, 9.0, 4.6, 1.9, 45, 'a')
    for x, y in p.where('a'):
        if (x + y) % 2 == 0 and interior(p, x, y):
            p.put(x, y, 'A')
    for x0, y0, x1, y1 in ((5, 9, 8, 1), (6, 8, 10, 1), (7, 7, 12, 1), (8, 6, 14, 1), (9, 5, 15, 2), (4, 10, 6, 3)):
        p.curve(x0, y0, x1, y1, 'w')
    p.stamp(['.'], 0, 0)
    p.curve(4, 12, 1, 15, 's')
    return item(p.rows(), {'a': BARLEY, 'A': (BARLEY, 1), 'w': (BARLEY, 3), 's': STRAW}, mode='out', bare='w')


def produce_chinese_chive():
    p = Pic()
    for i, (dx, dy, ch) in enumerate(((0, 0, 'g'), (1, 0, 'h'), (1, 1, 'j'), (2, 1, 'g'), (2, 2, 'h'))):
        p.curve(2 + dx, 13 + dy, 12 + dx, 2 + dy, ch, 0.0)
    p.put(12, 2, '.').put(13, 1, 'g').put(14, 3, 'j').put(15, 2, 'j').put(13, 4, '.').put(14, 4, 'h')
    for x, y in p.where('g') + p.where('h') + p.where('j'):
        if x + y in (15, 16):
            p.put(x, y, 'w')
        if x + y in (11, 12, 13) and x < 6:
            p.put(x, y, 't')
    return item(p.rows(), {'g': CHIVE, 'h': CHIVE, 'j': CHIVE, 't': TIE, 'w': (SCALLION_WHITE, 2)}, mode='in',
                seam='ghj', seam_tone=1)


PRODUCE_ART = {
    'rice': produce_rice, 'soybean': produce_soybean, 'corn': produce_corn, 'peanut': produce_peanut,
    'sesame': produce_sesame, 'tomato': produce_tomato, 'chili': produce_chili, 'bell_pepper': produce_bell_pepper,
    'eggplant': produce_eggplant, 'cucumber': produce_cucumber, 'napa_cabbage': produce_napa_cabbage,
    'lettuce': produce_lettuce, 'spinach': produce_spinach, 'onion': produce_onion, 'garlic': produce_garlic,
    'ginger': produce_ginger, 'scallion': produce_scallion, 'radish': produce_radish,
    'sweet_potato': produce_sweet_potato, 'taro': produce_taro, 'strawberry': produce_strawberry,
    'grape': produce_grape, 'blueberry': produce_blueberry, 'pineapple': produce_pineapple,
    'tea_leaves': produce_tea_leaves, 'coffee_beans': produce_coffee_beans, 'sichuan_pepper': produce_sichuan_pepper,
    'mung_bean': produce_mung_bean, 'barley': produce_barley, 'chinese_chive': produce_chinese_chive,
}
