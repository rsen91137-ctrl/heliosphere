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


class Ramp:
    """Five tones of one material: outline, dark, mid, light, highlight. gloss: a glint (the highlight tone) where
    the surface faces the light within this threshold (shiny fruit)."""

    def __init__(self, *tones, gloss=None):
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


# =====================================================================================================================
# shading

LIGHT = np.array([-0.52, -0.66, 0.54])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
GLINT = np.array([-0.36, -0.46, 0.81])
GLINT = GLINT / np.linalg.norm(GLINT)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def render(g, pal, outline=None, ground=False, th=(0.34, 0.80), seam='', seam_tone=0, bare=''):
    """g: 16 strings; pal: char -> Ramp (auto shaded region) or (Ramp, tone) (fixed tone).
    outline: None, 'in' (the silhouette's edge pixels become the outline) or 'out' (an outline is added around).
    ground: the plant continues below the bottom edge (stems are not lit as ends there).
    seam: region characters from back to front; where a region touches one further front, its edge gets the
    outline tone (seam_tone; separates touching objects of one material). With '|' the string lists layers
    ('Ggh|r': leaves are not set off against each other, only against the fruit).
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
    if '|' in seam:
        layer = {c: i for i, part in enumerate(seam.split('|')) for c in part}
    else:
        layer = {c: i for i, c in enumerate(seam)}
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
                if ramp[c].gloss is not None and float(n @ GLINT) > ramp[c].gloss:
                    t = 4
                if c in layer and any(0 <= x + dx < W and 0 <= y + dy < H and chars[y + dy, x + dx] in layer
                                      and layer[chars[y + dy, x + dx]] > layer[c] for dx, dy in N4):
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

    def stamp(self, spr, x, y, mirror=False, under=False, sub=None):
        for j, row in enumerate(spr):
            if mirror:
                row = row[::-1]
            for i, c in enumerate(row):
                if c != '.':
                    self.put(x + i, y + j, (sub or {}).get(c, c), under)
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
# produce colours
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
ROAST = Ramp('#1e0f08', '#3e2010', '#5e341c', '#7e4c2c', '#b8866a', gloss=0.975)
GREEN_COFFEE = Ramp('#4a5236', '#7c8660', '#a8b088', '#c8ceac', '#e6eadc')
SICHUAN = Ramp('#47130a', '#862312', '#b53c1d', '#d6623a', '#f2a07e')
SICHUAN_GREEN = Ramp('#2a4214', '#466a1e', '#66922c', '#8ab444', '#bcd884')
MUNG = Ramp('#1f3c10', '#3a6a1a', '#58962a', '#7cbc40', '#b8e07a')
MUNG_POD = Ramp('#0a130b', '#162618', '#233c26', '#365838', '#587a58')
BARLEY = Ramp('#5e4414', '#977224', '#c6a03a', '#e2c45a', '#f6e496')
CHIVE = Ramp('#0b2a12', '#15441c', '#20602a', '#2f7e39', '#55a25a')
TIE = Ramp('#4a1a10', '#86301c', '#b8492c', '#d86c48', '#f2a684')
# seed colours
KERNEL_CAP = Ramp('#7a6838', '#c8b47a', '#efe2b0', '#fbf2d2', '#ffffff')
SESAME_TAN = Ramp('#4e3214', '#86582a', '#b07c40', '#cc9c5a', '#e8c48a')
TOMATO_SEED = Ramp('#6a5e34', '#ab9c64', '#d6c890', '#ebe0b2', '#f8f2d8')
CHILI_SEED = Ramp('#6e4a08', '#b88218', '#e8b03a', '#f6cc62', '#fde8a0')
BELL_SEED = Ramp('#6e6650', '#b4ac90', '#e8e2c8', '#f6f2e0', '#ffffff')
EGG_SEED = Ramp('#5a4428', '#94744a', '#bea070', '#d8bc8e', '#eedcb8')
CUKE_SEED = Ramp('#6a6244', '#b2a67c', '#e2d6aa', '#f2eacc', '#fdfaee')
NAPA_SEED = Ramp('#2a0e08', '#4e1e12', '#74301e', '#94462c', '#b8684a')
LETTUCE_SEED = Ramp('#4a4a46', '#84827a', '#b0aea4', '#cecdc4', '#ecebe4')
SPINACH_SEED = Ramp('#3e3a1e', '#6e6838', '#9c9458', '#bab276', '#dad4a4')
SEED_BLACK = Ramp('#060608', '#161518', '#2a282c', '#46434a', '#8c8890', gloss=0.97)
RADISH_SEED = Ramp('#3a1a0e', '#6a321c', '#985034', '#b8704e', '#d8a080')
GRAPE_SEED = Ramp('#26140a', '#4a2a14', '#6e4424', '#8e6036', '#b48a60')
TEA_SEED = Ramp('#1e120a', '#3a2414', '#563820', '#74502e', '#a07a56', gloss=0.98)
MUNG_DRY = Ramp('#1e3010', '#3a5418', '#567a24', '#749a34', '#a8c470')
BARLEY_GRAIN = Ramp('#5a4820', '#9a8048', '#cdb47a', '#e4d09c', '#f6ecc8')
BLUE_DRY = Ramp('#0e1028', '#1e2450', '#30397a', '#4f5c9e', '#8e9ac8')
# fruit on the plant (no automatic glint; a single highlight pixel is placed by hand)


def matte(r):
    return Ramp(*r.t)


TOMATO_M, TOMATO_GREEN_M, CHILI_M, CHILI_GREEN_M = matte(TOMATO), matte(TOMATO_GREEN), matte(CHILI), matte(CHILI_GREEN)
BELL_M, EGGPLANT_M, STRAWBERRY_M, GRAPE_M, GRAPE_GREEN_M = matte(BELL), matte(EGGPLANT), matte(STRAWBERRY), \
    matte(GRAPE), matte(GRAPE_GREEN)
BLUEBERRY_M, COFFEE_CHERRY_M, SWEET_POTATO_M = matte(BLUEBERRY), matte(COFFEE_CHERRY), matte(SWEET_POTATO)


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
    for c, (x1, y1), (x2, y2) in (('a', (3.4, 9.3), (7.3, 5.4)), ('b', (8.3, 10.3), (12.2, 6.4))):
        p.rellipse(x1, y1, 3.0, 2.6, 45, c)
        p.rellipse(x2, y2, 3.0, 2.6, 45, c)
    for x, y in p.where('a') + p.where('b'):
        if x % 3 == 1 and y % 3 == 1 and interior(p, x, y):
            p.put(x, y, 'n')
    return item(p.rows(), {'a': PEANUT, 'b': PEANUT, 'n': (PEANUT, 1)}, seam='ab')


def produce_sesame():
    p = Pic()
    rows = ((11, (2, 5, 8, 11), 'd'), (8, (3, 6, 9), 'c'), (5, (5, 8), 'b'), (2, (6,), 'a'))
    black = {(5, 11), (9, 8), (6, 2)}
    for y, xs, ch in rows:
        for i, x in enumerate(xs):
            c = ch.upper() if (x, y) in black else ch
            p.stamp(['x.' if i % 2 else '.x', 'xx', 'xx'], x, y, sub={'x': c})
    return item(p.rows(), {c: SESAME for c in 'abcd'} | {c: BLACK for c in 'ABCD'}, mode='out', seam='aAbBcCdD')


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
    p = Pic(grid("""
    ................
    ................
    .....j.jj.j.....
    ....jjjjjjjj....
    ..h.jjjjjjjj.h..
    .hhhjjjjjjjjhhh.
    hhhhhjjjjjjhhhhh
    .hhhhhjjjjhhhhh.
    hhhhhhhjjhhhhhhh
    ghhhhhhhhhhhhhhg
    .ggghhhhhhhhggg.
    gggggghhhhgggggg
    .gggggggggggggg.
    g.gggggggggggg.g
    ..g.gg.gg.gg.g..
    ................
    """))
    for x, y in ((7, 12), (7, 11), (6, 10), (8, 10), (4, 12), (3, 11), (11, 12), (12, 11), (2, 8), (13, 8), (3, 7), (12, 7)):
        if interior(p, x, y):
            p.put(x, y, 'L')
    return item(p.rows(), {'g': LETTUCE, 'h': LETTUCE, 'j': LETTUCE_HEART, 'L': (LETTUCE, 3)}, seam='jhg', seam_tone=1)


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
    ................
    .........ggg....
    ....gg...ggg....
    ...gggg..ggg....
    ...gggg..ggg..g.
    ....ggg..ggg.ggg
    ..ggggggggggggg.
    .gggggggggggggg.
    gggggggggggggg..
    .gggggggggggg...
    ..ggggg.gggg....
    ................
    ................
    ................
    """))
    for x, y in ((4, 7), (5, 7), (10, 6), (11, 6), (2, 10), (3, 11), (7, 9), (7, 10), (11, 10), (12, 9)):
        if interior(p, x, y):
            p.put(x, y, 'G')
    for x, y in ((10, 4), (4, 5), (14, 7), (1, 10)):
        if interior(p, x, y):
            p.put(x, y, 'p')
    return item(p.rows(), {'g': GINGER, 'G': (GINGER, 1), 'p': (GINGER_PINK, 3)})


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
    p.pts([(1, 7), (0, 10), (14, 6), (15, 9), (1, 12), (14, 12), (4, 14), (11, 14)], 'x')
    return item(p.rows(), {'t': TARO, 'T': (TARO, 1), 'b': TARO_BUD, 'x': (TARO, 1)}, bare='x')


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
    for x, y in ((3, 6), (6, 7), (9, 6), (12, 7), (4, 9), (7, 10), (10, 9), (5, 12), (8, 12), (3, 8), (11, 11)):
        if p.get(x, y) == 'r' and interior(p, x, y):
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
    for x, y, c in ((5, 1, 'a'), (1, 5, 'b'), (7, 5, 'c'), (4, 9, 'd')):
        p.stamp(CIRCLE6, x, y, sub={'x': c})
    for x, y, c in ((5, 1, 'a'), (1, 5, 'b'), (7, 5, 'c'), (4, 9, 'd')):
        if p.get(x + 2, y + 1) == c:
            p.put(x + 2, y + 1, 'k').put(x + 3, y + 1, 'k').put(x + 2, y + 2, 'K')
    return item(p.rows(), {c: BLUEBERRY for c in 'abcd'} | {'k': (BLUEBERRY, 0), 'K': (BLUEBERRY, 4)}, seam='abcd')


def produce_pineapple():
    p = Pic(grid("""
    ......g.g.......
    ...h..ggg..h....
    ...hh.ggg.hh....
    ....hhgggghh....
    ....hhhgghhh....
    .....hhgghh.....
    """, bottom=False))
    p.ellipse(7.5, 10.6, 4.7, 4.9, 'p')
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
    for y in (4, 7, 10):
        p.stamp(['xx', 'xx'], 5, y, sub={'x': 'a'})
        p.stamp(['xx', 'xx'], 8, y + 1, sub={'x': 'b'})
    for x0, y0, x1, y1 in ((5, 3, 4, 0), (4, 7, 2, 1), (4, 10, 1, 4), (9, 4, 10, 0), (10, 8, 12, 1), (10, 11, 13, 5)):
        p.line(x0, y0, x1, y1, 'w', under=True)
    p.line(7, 13, 7, 15, 's')
    return item(p.rows(), {'a': BARLEY, 'b': BARLEY, 'w': (BARLEY, 3), 's': STRAW}, mode='out', bare='w')


def produce_chinese_chive():
    p = Pic()
    stripe = {13: 'a', 14: 'a', 15: 'b', 16: 'c', 17: 'a', 18: 'b', 19: 'b'}
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y
            if -11 <= u <= 7 and v in stripe:
                p.put(x, y, 'w' if u <= -9 else 't' if u in (-5, -4) else stripe[v])
    for x, y, c in ((10, 3, 'a'), (11, 2, 'a'), (11, 1, 'a'), (12, 0, 'a'), (12, 3, 'b'), (13, 2, 'b'), (14, 1, 'b'),
                    (12, 5, 'a'), (13, 4, 'a'), (14, 4, 'b'), (15, 3, 'b'), (13, 6, 'b'), (14, 6, 'b'), (15, 6, 'b')):
        p.put(x, y, c)
    return item(p.rows(), {'a': (CHIVE, 3), 'b': (CHIVE, 2), 'c': (CHIVE, 1), 't': (TIE, 2), 'w': (SCALLION_WHITE, 2)})


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


# =====================================================================================================================
# seeds


def rot(spr):
    """Rotate a sprite a quarter turn clockwise."""
    return [''.join(spr[len(spr) - 1 - j][i] for j in range(len(spr))) for i in range(len(spr[0]))]


def scatter(spr, spots, sub=None):
    p = Pic()
    for spot in spots:
        x, y = spot[0], spot[1]
        f = spot[2] if len(spot) > 2 else ''
        sp = spr
        for _ in range(f.count('r')):
            sp = rot(sp)
        p.stamp(sp, x, y, mirror='m' in f, sub=sub)
    return p


def seeds_rice():
    p = scatter(['.xd', 'xx.'], [(2, 3), (7, 2, 'm'), (11, 4), (4, 7, 'm'), (9, 8), (2, 11), (7, 12, 'm'), (12, 10, 'm')])
    return item(p.rows(), {'x': GOLD, 'd': (GOLD, 1)}, 'out')


def seeds_soybean():
    p = scatter(['xxx', 'xxe', 'xxx'], [(2, 2), (8, 1), (11, 6), (5, 7), (1, 11), (9, 11)])
    return item(p.rows(), {'x': SOY, 'e': (HILUM, 1)}, 'out')


def seeds_corn():
    p = scatter(['ccc', 'xxx', '.x.'], [(2, 2), (9, 2), (5, 7), (11, 8), (2, 11), (8, 12)])
    return item(p.rows(), {'x': CORN, 'c': KERNEL_CAP}, 'out')


def seeds_peanut():
    p = scatter(['xx', 'xx', 'xx'], [(2, 2), (7, 2, 'r'), (12, 4), (5, 7, 'r'), (9, 9), (2, 11), (11, 13, 'r')])
    return item(p.rows(), {'x': PEANUT_RED}, 'out')


def seeds_sesame():
    p = scatter(['.x', 'xx', 'xx'], [(3, 2), (8, 2, 'm'), (12, 4), (5, 7, 'm'), (10, 9), (2, 11), (7, 12, 'm')])
    return item(p.rows(), {'x': SESAME_TAN}, 'out')


def seeds_tomato():
    p = scatter(['xx', 'xx'], [(3, 2), (8, 2), (12, 4), (5, 6), (10, 8), (2, 9), (6, 11), (11, 12)])
    return item(p.rows(), {'x': TOMATO_SEED}, 'out')


def seeds_chili():
    p = scatter(['xxx', 'xx.'], [(2, 2), (8, 2, 'm'), (11, 6), (4, 7, 'm'), (9, 10), (2, 12, 'm'), (11, 13, 'm')])
    return item(p.rows(), {'x': CHILI_SEED}, 'out')


def seeds_bell_pepper():
    p = scatter(['.xx', 'xxx', 'xx.'], [(2, 2), (9, 2, 'm'), (5, 7, 'm'), (11, 8), (2, 11), (8, 12, 'm')])
    return item(p.rows(), {'x': BELL_SEED}, 'out')


def seeds_eggplant():
    p = scatter(['xxx', 'xxe'], [(2, 2), (8, 1, 'm'), (12, 5, 'r'), (4, 6, 'm'), (9, 9), (2, 11, 'r'), (6, 12)])
    return item(p.rows(), {'x': EGG_SEED, 'e': (EGG_SEED, 1)}, 'out')


def seeds_cucumber():
    p = scatter(['.x', 'xx', 'xx', 'x.'], [(2, 2), (7, 1, 'm'), (12, 3), (4, 8, 'm'), (9, 8), (13, 10, 'm'), (6, 12, 'r')])
    return item(p.rows(), {'x': CUKE_SEED}, 'out')


def seeds_napa_cabbage():
    p = scatter(['xx', 'xx'], [(2, 2), (6, 3), (11, 2), (3, 7), (8, 7), (12, 8), (5, 11), (10, 12), (1, 12)])
    return item(p.rows(), {'x': NAPA_SEED}, 'out')


def seeds_lettuce():
    p = scatter(['x', 'x', 'x'], [(3, 2), (8, 1, 'r'), (12, 4), (5, 7, 'r'), (10, 9), (2, 11), (7, 13, 'r')])
    return item(p.rows(), {'x': LETTUCE_SEED}, 'out')


def seeds_spinach():
    p = scatter(['.p..', '.xx.', 'xxxp', '.xx.'], [(1, 1), (8, 2, 'm'), (4, 7, 'r'), (10, 8), (2, 11, 'm'), (8, 12, 'r')])
    return item(p.rows(), {'x': SPINACH_SEED, 'p': (SPINACH_SEED, 1)}, 'out', bare='p')


def seeds_onion():
    p = scatter(['..t..', '.xxx.', 'xxxxx', 'xxxxx', '.xxx.'], [(1, 2), (9, 1), (5, 9)])
    p.put(7, 15, 'r').put(5, 15, '.')
    return item(p.rows(), {'x': ONION, 't': PAPER, 'r': ROOTLET}, 'out')


def seeds_garlic():
    p = scatter(['..x.', '.xxx', 'xxxx', 'xxxx', 'vxx.'], [(2, 2), (9, 3, 'm'), (5, 9)])
    return item(p.rows(), {'x': GARLIC, 'v': (GARLIC_PURPLE, 2)}, 'out')


def seeds_ginger():
    return item("""
    ................
    ................
    ................
    ................
    .........j......
    ........jj......
    ........j.......
    ....ggggpg......
    ...ggggggggg....
    ..gggggggggggg..
    ..ggggGggggggg..
    ...ggggggGgggg..
    ....gggggggg....
    ......ggg.......
    ................
    ................
    """, {'g': GINGER, 'G': (GINGER, 1), 'p': (GINGER_PINK, 2), 'j': LEAF}, 'in')


def seeds_scallion():
    p = scatter(['x..', 'xx.', 'xxx'], [(2, 2), (8, 2, 'm'), (12, 6, 'r'), (4, 7, 'r'), (9, 11, 'm'), (2, 12)])
    return item(p.rows(), {'x': SEED_BLACK}, 'out')


def seeds_radish():
    p = scatter(CIRCLE4, [(2, 2), (9, 2), (6, 7), (1, 11), (11, 10)])
    return item(p.rows(), {'x': RADISH_SEED}, 'out')


def seeds_sweet_potato():
    return item("""
    ................
    ................
    ....gg...hh.....
    ...gggg.hhhh....
    ...ggggshhhh....
    ....ggs.hhh.....
    ......s.........
    .....pps........
    ....ppppppp.....
    ...ppppppppp....
    ..ppppppppppp...
    ..pppppppppp....
    ...pppppppp.....
    .....pppp.......
    ................
    ................
    """, {'p': SWEET_POTATO, 'g': LEAF, 'h': LEAF, 's': STEM}, 'in', seam='ghps')


def seeds_taro():
    p = Pic()
    for x, y, c in ((2, 4, 'a'), (9, 2, 'b'), (5, 9, 'a')):
        p.stamp(CIRCLE5, x, y, sub={'x': c})
        p.put(x + 2, y - 1, 'p').put(x + 2, y, 'p')
        for dx, dy in ((1, 2), (3, 3)):
            if interior(p, x + dx, y + dy):
                p.put(x + dx, y + dy, 'T')
    return item(p.rows(), {'a': TARO, 'b': TARO, 'T': (TARO, 1), 'p': TARO_BUD}, 'out', seam='ab')


def seeds_strawberry():
    p = scatter(['x', 'x'], [(4, 2), (9, 3), (13, 2), (2, 7), (7, 7), (11, 8), (4, 12), (9, 12), (13, 12)])
    return item(p.rows(), {'x': STRAW_SEED}, 'out')


def seeds_grape():
    p = scatter(['.x.', 'xxx', 'xxx', '.x.'], [(2, 2), (9, 1), (12, 7), (5, 7), (2, 12), (9, 11)])
    for x, y in p.where('x'):
        if p.get(x, y - 1) == '.' and p.get(x - 1, y) == '.' and p.get(x + 1, y) == '.':
            p.put(x, y, 'b')
    return item(p.rows(), {'x': GRAPE_SEED, 'b': (GRAPE_SEED, 1)}, 'out')


def seeds_blueberry():
    p = Pic()
    for x, y, c in ((2, 2, 'a'), (9, 3, 'b'), (5, 9, 'a')):
        p.stamp(CIRCLE5, x, y, sub={'x': c})
        p.put(x + 2, y + 1, 'k')
        p.put(x + 1, y + 3, 'w')
    return item(p.rows(), {'a': BLUE_DRY, 'b': BLUE_DRY, 'k': (BLUE_DRY, 0), 'w': (BLUE_DRY, 1)}, 'out', seam='ab')


def seeds_pineapple():
    return item("""
    ................
    .......g........
    ...h...gg...h...
    ...hh..gg..hh...
    ....hh.gg.hh....
    .h...hhgghh...h.
    ..hh.hhgghh.hh..
    ...hhhhgghhhh...
    ....hhhgghhh....
    .....hhgghh.....
    ......gggg......
    .....pppppp.....
    ....pppPPppp....
    .....pppppp.....
    ................
    ................
    """, {'g': PINE_LEAF, 'h': PINE_LEAF, 'p': PINEAPPLE, 'P': (PINEAPPLE, 1)}, 'in', seam='hgp')


def seeds_tea():
    p = Pic()
    for x, y, c in ((2, 2, 'a'), (9, 4, 'b'), (4, 9, 'b')):
        p.stamp(CIRCLE6, x, y, sub={'x': c})
        p.put(x + 3, y + 4, 'h').put(x + 4, y + 4, 'h')
    return item(p.rows(), {'a': TEA_SEED, 'b': TEA_SEED, 'h': (PEANUT, 3)}, 'out', seam='ab')


def seeds_coffee_shrub():
    p = Pic()
    beans = ((4.5, 4.5, 70, 'a'), (11.0, 5.5, 110, 'b'), (5.5, 11.0, 30, 'b'), (11.5, 11.5, 150, 'a'))
    for cx, cy, deg, ch in beans:
        p.rellipse(cx, cy, 2.6, 1.8, deg, ch)
    for cx, cy, deg, ch in beans:
        ca, sa = math.cos(math.radians(deg)), -math.sin(math.radians(deg))
        for t in (-1.0, 0.0, 1.0):
            xi, yi = math.floor(cx + ca * t + 0.5), math.floor(cy + sa * t + 0.5)
            if p.get(xi, yi) == ch:
                p.put(xi, yi, 'x')
    return item(p.rows(), {'a': GREEN_COFFEE, 'b': GREEN_COFFEE, 'x': (GREEN_COFFEE, 1)}, 'out', seam='ab')


def seeds_sichuan_pepper():
    p = scatter(['xxx', 'xxx', 'xxx'], [(2, 2), (9, 2), (12, 7), (5, 7), (2, 12), (9, 11)])
    return item(p.rows(), {'x': SEED_BLACK}, 'out')


def seeds_mung_bean():
    p = scatter(['xxx', 'xlx'], [(2, 2), (9, 2), (12, 7, 'r'), (5, 7), (2, 12), (8, 12)])
    return item(p.rows(), {'x': MUNG_DRY, 'l': (MUNG_DRY, 4)}, 'out')


def seeds_barley():
    p = scatter(['.x.', 'xxx', 'xdx', 'xxx', '.x.'], [(2, 1), (8, 2, 'r'), (12, 6), (5, 8, 'r'), (2, 10), (9, 11, 'r')])
    return item(p.rows(), {'x': BARLEY_GRAIN, 'd': (BARLEY_GRAIN, 1)}, 'out')


def seeds_chinese_chive():
    p = scatter(['xxx', 'xxx', '.x.'], [(2, 2), (8, 1), (12, 5), (5, 7), (10, 10), (2, 12), (13, 12)])
    for x, y in p.where('x'):
        if p.get(x - 1, y) == '.' and p.get(x, y - 1) == '.':
            p.put(x, y, 'h')
    return item(p.rows(), {'x': SEED_BLACK, 'h': (SEED_BLACK, 3)}, 'out')


SEED_ART = {c[0]: globals()['seeds_' + c[0]] for c in SPEC.CROPS}


# =====================================================================================================================
# plants (growth stages): a crop is a function returning its four stage grids and the palette (or one per stage)


def crop(stages, pal, seam=''):
    """Four stage grids; pal (and seam) for all stages or a list of four. seam lists regions back to front: a leaf
    touching a fruit further front gets its outline tone there, which sets the fruit off against the foliage."""
    return {'stages': [s if isinstance(s, list) else grid(s) for s in stages],
            'pal': pal if isinstance(pal, list) else [pal] * 4,
            'seam': seam if isinstance(seam, list) else [seam] * 4}


def sub(g, mapping):
    """Replace characters in a grid ('' deletes)."""
    return [''.join(mapping.get(c, c) or '.' for c in r) for r in g]


def tipped(g, ch='g', tip='l'):
    """Light the tips of thin shoots: a pixel of ch with nothing above it or diagonally above."""
    g = [list(r) for r in (g if isinstance(g, list) else grid(g))]
    for y in range(1, 16):
        for x in range(16):
            if g[y][x] == ch and all(g[y - 1][x + dx] == '.' for dx in (-1, 0, 1) if 0 <= x + dx < 16):
                g[y][x] = tip
    return [''.join(r) for r in g]


def plant_rice():
    s0 = """
    ...........g....
    ..g.g......g.g..
    ...g...g.g..g...
    ...g....g...g...
    """
    xs = (2, 7, 12)
    p1 = Pic()
    for x in xs:
        p1.curve(x, 15, x - 2, 10, 'g', -0.4).curve(x, 15, x, 8, 'g', 0.2).curve(x, 15, x + 2, 9, 'g', 0.4)
        p1.curve(x, 15, x + 1, 11, 'G', 0.2)
    def clumps():
        p = Pic()
        for x in xs:
            p.curve(x, 15, x - 3, 8, 'G', -0.6).curve(x, 15, x + 3, 9, 'G', 0.6)
            p.curve(x, 15, x - 2, 9, 'g', -0.4).curve(x, 15, x + 2, 10, 'g', 0.5).curve(x, 15, x, 4, 's', 0.1)
        return p
    p2, p3 = clumps(), clumps()
    for x in xs:
        p2.pts([(x, 3), (x, 2), (x + 1, 1), (x + 1, 0), (x - 1, 2), (x + 1, 3)], 'k')
        p3.pts([(x, 3), (x + 1, 2), (x + 2, 1), (x + 3, 2), (x + 3, 3), (x + 4, 4), (x + 4, 5), (x + 4, 6)], 'k')
        p3.pts([(x + 1, 1), (x + 2, 2), (x + 3, 4), (x + 3, 5), (x + 3, 6), (x + 4, 7)], 'K')
    pal = {'l': (LEAF_YG, 3), 'g': LEAF_YG, 'G': LEAF_YG_BK, 's': LEAF_YG, 'k': PANICLE, 'K': PANICLE}
    pal3 = {'l': (LEAF_YG, 3), 'g': LEAF_YG, 'G': LEAF_YG_BK, 's': LEAF_YG, 'k': GOLD, 'K': GOLD_BK}
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], [pal, pal, pal, pal3], seam='Ggs|Kk')


def plant_soybean():
    s0 = """
    ......gg.hh.....
    ......ggshh.....
    ........s.......
    ........s.......
    """
    s1 = """
    .......gg.......
    ......gggg......
    ...hh..gg..hh...
    ..hhhh.s..hhhh..
    ...hhh.s.hhhh...
    .....h.ss.h.....
    ........s.......
    ........s.......
    """
    s3 = """
    ......gg.gg.....
    ....g.gggggg.g..
    ...ggg.gsgg.ggg.
    ..hhgg.GsG.gghh.
    .hhhh.GGsGG.hhhh
    ..hhGGGGsGGGGhh.
    .gg.GdGGsGGdG.gg
    ggggGdGdsdGdGggg
    .gggGdGdsdGdGgg.
    ..g..GGdsdGG..g.
    ....hhh.s.hhh...
    ...hhhh.s.hhhh..
    ....hh..s..hh...
    ........s.......
    """
    g2 = sub(grid(s3), {'d': 'G'})
    g2 = stamp(g2, ['f'], 5, 8)
    g2 = stamp(g2, ['f'], 11, 9)
    g2 = stamp(g2, ['u', 'u'], 7, 9)
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'u': SOY_POD_GREEN, 'd': SOY_POD, 'f': (EGG_FLOWER, 3)}
    return crop([s0, s1, g2, s3], pal, seam='Gsghud')


CORN_PLANT = sprite("""
..t.t...
...tt...
...ss...
...sshh.
...ss.hh
.ggss..h
gg.ss..h
g..ss.yy
g..ss.e.
...ssee.
.ggssee.
gg.sse..
g..sshh.
g..ss.hh
...ss..h
...ss...
""")


def plant_corn():
    s0 = """
    ...g.......g....
    ...gg......gg...
    ...g.......g....
    """
    p1 = Pic()
    for x, m in ((4, 1), (11, -1)):
        p1.curve(x, 15, x - 3 * m, 9, 'g', -0.7 * m).curve(x, 15, x - 2 * m, 10, 'g', -0.5 * m)
        p1.curve(x, 15, x + 3 * m, 8, 'h', 0.7 * m).curve(x, 14, x + 2 * m, 9, 'h', 0.6 * m)
        p1.curve(x, 15, x, 7, 'G', 0.2)
    p3 = Pic().stamp(CORN_PLANT, 0, 0).stamp(CORN_PLANT, 8, 0)
    young = sub(CORN_PLANT, {'t': '', 'y': '', 'e': ''})
    p2 = Pic().stamp(young, 0, 1).stamp(young, 8, 1)
    for x in (3, 11):
        p2.pts([(x, 2), (x + 1, 2), (x, 1)], 'j')
        p2.pts([(x + 2, 10), (x + 2, 11)], 'e')
    pal = {'l': (LEAF, 3), 'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'j': HUSK, 't': TASSEL, 'e': HUSK, 'y': SILK, 'k': CORN}
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], pal, seam='ghsG|ey|t')


def plant_peanut():
    s0 = """
    ......gg.hh.....
    ......ggshh.....
    ........s.......
    """
    s1 = """
    ......gg.hh.....
    ...gg.gg.hh.hh..
    ..gggg..s..hhhh.
    ...gg..s.s..hh..
    .......s.s......
    ........s.......
    """
    s2 = """
    .....gg..f.hh...
    ..gg.gggshhhh.hh
    .gggg.gg.hhh.hhh
    ..gg.GGsGsGG..h.
    .hh.GGGGsGGGG.gg
    hhhh.GG.s.GG.ggg
    .hh.....s.....g.
    """
    g3 = grid(s2)
    g3 = stamp(g3, ['f'], 4, 9)
    g3 = stamp(g3, ['.pp.', 'pppp'], 1, 14)
    g3 = stamp(g3, ['.pp.', 'pppp'], 11, 14)
    g3 = stamp(g3, ['pp', 'pp'], 6, 14)
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'f': FLOWER_YELLOW, 'p': PEANUT}
    return crop([s0, s1, s2, g3], pal)


SESAME_PLANT = sprite("""
...g...
..gsf..
...sff.
gg.s...
.ggsc..
...sc..
...s.gg
..csgg.
..cs...
gg.s...
.ggsc..
...sc..
...s.gg
...sgg.
...s...
...s...
""")


def plant_sesame():
    s0 = """
    ...gg......gg...
    ....s..gg...s...
    ....s...s...s...
    """
    young = sub(SESAME_PLANT[8:], {'c': ''})
    p1 = Pic().stamp(young, 1, 16 - len(young)).stamp(young, 9, 16 - len(young), mirror=True)
    p1.put(5, 15 - len(young) + 1, 'g').put(12, 15 - len(young) + 1, 'g')
    flowering = sub(SESAME_PLANT, {'c': 'f'})
    p2 = Pic().stamp(sub(flowering[2:], {'f': ''}), 1, 2).stamp(sub(flowering[2:], {'f': ''}), 9, 3, mirror=True)
    for x, y in ((5, 6), (6, 7), (2, 10), (13, 7), (10, 11), (12, 12)):
        p2.put(x, y, 'f')
    p3 = Pic().stamp(SESAME_PLANT, 1, 0).stamp(SESAME_PLANT, 9, 1, mirror=True)
    pal = {'g': LEAF, 's': STEM, 'f': (FLOWER_WHITE, 3), 'c': (SOY_POD_GREEN, 3)}
    return crop([s0, p1.rows(), p2.rows(), p3.rows()], pal, seam='gs|cf')


def plant_tomato():
    s0 = """
    ......g.h.......
    .......sh.......
    .......s........
    """
    s1 = """
    .....g...h......
    ....ggg.hhh.....
    ...g.g.s.h.h....
    ..gg..gshh.hh...
    ...g...s...h....
    ......gsh.......
    .......s........
    .......s........
    """
    s3 = """
    ................
    ....GG...hh.....
    ...GGGg.hhhhh...
    ..GGgggs.hhhhh..
    .GGrrrg.s.hhhh..
    .Gr*rrgss.GGhh..
    .grrrrg.sGrrrh..
    ..grrg..sr*rrr..
    .hhggg.ssrrrrrG.
    hhhhh.s.gggrrGG.
    .hhhhs..Gggggg..
    ..hh.s.GGrrrg...
    ....hs.Gr*rr....
    .....s..rrrr....
    ......s..rr.....
    ......s.........
    """
    g2 = sub(grid(s3), {'r': 'u', '*': 'u'})
    g2 = stamp(g2, ['f'], 12, 2)
    g2 = stamp(g2, ['f'], 3, 10)
    pal = {'g': LEAF_TOM, 'h': LEAF_TOM, 'G': LEAF_TOM_BK, 's': STEM, 'r': TOMATO_M, '*': (TOMATO, 4),
           'u': TOMATO_GREEN_M, 'f': (FLOWER_YELLOW, 3)}
    return crop([s0, s1, g2, s3], pal, seam='Gghsur')


def plant_chili():
    s0 = """
    ......g.h.......
    .......sh.......
    .......s........
    """
    s1 = """
    ......g.h.......
    .....gg.hh......
    ....gg.s.hh.....
    .....g.s.h......
    ......gsh.......
    .......s........
    .......s........
    """
    s3 = """
    ................
    ................
    ......gg.hh.....
    ....ggggshhhh...
    ...ggg.gs.hhhh..
    ..gGGgg.shhGGh..
    ..GGrGG.sGGrGG..
    .gGGrGGssGGrhh..
    .ggrgGrs.GrGhhh.
    ..grg.rs.rG.hh..
    ...r..sr.r..r...
    ...r..s..r..r...
    ......s.........
    ......s.........
    ......s.........
    ......s.........
    """
    g2 = sub(grid(s3), {'r': 'u'})
    g2 = stamp(g2, ['f'], 5, 3)
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'r': CHILI_M, 'u': CHILI_GREEN_M, 'f': (FLOWER_WHITE, 3)}
    return crop([s0, s1, g2, s3], pal, seam='Gghsur')


def plant_bell_pepper():
    s0 = """
    ......gg.hh.....
    .......shh......
    .......s........
    """
    s1 = """
    .....gg.hh......
    ....gggshhh.....
    ...gg..s..hh....
    ....gg.shhh.....
    ......gsh.......
    .......s........
    .......s........
    """
    s3 = """
    ................
    .....GG..hh.....
    ...GGGGg.hhhh...
    ..GGGgggshhhhh..
    .GGggg.gs.hhhhh.
    .Gggg..sssGGhhh.
    ..g..bbbb.sGGG..
    .hhh.b*bb.bbbb..
    hhhhhbbbb.b*bb..
    .hhhhbbbbsbbbb..
    ..hh.b.b.sbbbb..
    .....GGGsGb.b...
    ....GGggsggg....
    .....gg.s.gg....
    ........s.......
    ........s.......
    """
    g2 = sub(grid(s3), {'b': 'g', '*': 'g'})
    g2 = stamp(g2, ['bb', 'bb'], 6, 7)
    g2 = stamp(g2, ['f'], 11, 7)
    pal = {'g': LEAF_DK, 'h': LEAF_DK, 'G': LEAF_DK_BK, 's': STEM, 'b': BELL_M, '*': (BELL, 4), 'f': (FLOWER_WHITE, 3)}
    return crop([s0, s1, g2, s3], pal, seam='Gghsb')


def plant_eggplant():
    s0 = """
    ......gg.hh.....
    .......shh......
    .......s........
    """
    s1 = """
    ....ggg..hhh....
    ...ggggshhhh....
    ...gg..s..hh....
    ......gsh.......
    .......s........
    .......s........
    """
    s3 = """
    ................
    ....GGG..hhh....
    ...GGGGG.hhhhh..
    ..GGGggg.shhhhh.
    .GGgggg.s.hhhhh.
    .Gggg.f.s.GGhhh.
    ..gg..ys.GGGGh..
    .hhhh.pps.GGfy..
    hhhhh.p*s..pp...
    .hhh..pps..p*...
    ..h...pp...pp...
    ......pp...pp...
    ......pp....p...
    .......p........
    .......s........
    .......s........
    """
    g2 = sub(grid(s3), {'p': '', '*': ''})
    g2 = stamp(g2, ['pp', 'pp'], 6, 7)
    g2 = stamp(g2, ['p', 'p'], 11, 8)
    g2 = stamp(g2, ['fy'], 2, 9)
    pal = {'g': LEAF_GREY, 'h': LEAF_GREY, 'G': LEAF_BK, 's': STEM_PURPLE, 'p': EGGPLANT_M, '*': (EGGPLANT, 4),
           'f': EGG_FLOWER, 'y': (FLOWER_CENTRE, 3)}
    return crop([s0, s1, g2, s3], pal, seam='Gghsp')


def plant_cucumber():
    s0 = """
    ......gg.hh.....
    ......ggshh.....
    ........s.......
    """
    s1 = """
    .......w........
    .......w........
    .......w.gg.....
    .......wgggg....
    ...gg..wsgg.....
    ..gggg.ws.......
    ...gggsw........
    .....s.w........
    ......sw........
    .......w........
    """
    s3 = """
    .......w........
    ...gg..ws.hh....
    ..gggg.w.hhhh...
    ..ggggswshhhh...
    ...gg.fwshh.....
    .GG..s.ws..GG...
    GGGG.s.wsfGGGG..
    GGGGs.cw.s.GG...
    .GG..ccw..s.cc..
    ...hhlcw.hh.lc..
    ..hhhcc.whhhcc..
    ..hhhcl.whhhcl..
    ...hhcc.w.hhcc..
    .....c..ws..c...
    .......w.s......
    .......w........
    """
    g2 = sub(grid(s3), {'c': '', 'l': ''})
    g2 = stamp(g2, ['c', 'c'], 5, 9)
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'w': WOOD, 'c': CUCUMBER, 'l': (CUCUMBER, 3),
           'f': (FLOWER_YELLOW, 3)}
    return crop([s0, s1, g2, s3], pal, seam='Gghswc')


def plant_napa_cabbage():
    s0 = """
    ......gg.hh.....
    .....gggshhh....
    ........s.......
    """
    s1 = """
    ......g..h......
    .....ggg.hhh....
    ....gggw.whhh...
    ...ggggw.whhhh..
    ...gggw...whhh..
    ....ggw...wwh...
    ......ww.ww.....
    """
    s2 = """
    ......kkk.......
    .....kkkkk......
    ....kkkkkkk.....
    ....kkkkkkk.....
    ..g.kkkkkkk.h...
    .gggkkkkkkkhhh..
    .gggwkkkkkwhhh..
    ggggwwkkkwwhhhh.
    .gggwwwkwwwhhh..
    ..ggwwwwwwwhh...
    ...gGwwwwwGhh...
    .....wwwwww.....
    """
    s3 = """
    ......kkkk......
    .....kkKkkk.....
    ....kkkkkKkk....
    ....kkKkkkkk....
    ....kkkkKkkk....
    ...gkkkkkkkkh...
    ..ggkkkKkkkkhh..
    .gggwkkkkkkwhhh.
    .gggwwkkkkwwhhh.
    ggggwwwkkwwwhhhh
    .gggwwwwwwwwhhh.
    ..ggwwwwwwwwhh..
    ...gwwwwwwwwh...
    ..GGwwwwwwwwGG..
    ....wwwwwwww....
    """
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 'k': LEAF_PALE, 'K': (LEAF_PALE, 1), 'w': CABBAGE, 's': CABBAGE}
    return crop([s0, s1, s2, s3], pal, seam='Ggh|w|k')


def plant_lettuce():
    s0 = """
    ......gg.hh.....
    ......ggshh.....
    ........s.......
    """
    s1 = """
    .....gg..hh.....
    ....gggg.hhhh...
    ..hh.ggjjgg.gg..
    .hhhh.gjjg.gggg.
    ..hhhh.jj.gggg..
    """
    s2 = """
    ......jj.jj.....
    ....gjjjjjjh....
    ..g.gjjjjjjhh.h.
    .ggggjjjjjjhhhh.
    gggghhjjjjggghhh
    .ggghhhjjggghhh.
    g.Gggghhggggh.h.
    ..GGGggghhhGG...
    """
    s3 = """
    .....j.jj.j.....
    ...g.jjjjjj.h...
    ..ggjjjjjjjjhh..
    .gggjjjjjjjjhhh.
    g.ghhjjjjjjggh.h
    gggghhjjjjgggghh
    .ggghhhjjggghhh.
    g.gggghhgggghh.h
    .GGgggghhggghGG.
    ..GGGgggghhGGG..
    ....GGGGGGGG....
    """
    pal = {'g': LETTUCE, 'h': LETTUCE, 'j': LETTUCE_HEART, 'G': LEAF_BRIGHT_BK, 's': STEM}
    return crop([s0, s1, s2, s3], pal, seam='G|gh|j')


def plant_spinach():
    s0 = """
    ......g..h......
    .......gsh......
    ........s.......
    """
    s1 = """
    ....gg....hh....
    ...gggg..hhhh...
    ...gggg..hhhh...
    .....gp..ph.....
    ......pppp......
    """
    s2 = """
    .......gg.......
    ..gg..gggg..hh..
    .gggg.gggg.hhhh.
    .ggggg.gg.hhhhh.
    ..gggg..p.hhhh..
    ....ggp.p.hh....
    ..hh...pp...gg..
    .hhhhh.pp.ggggg.
    ..hh...pp...gg..
    """
    s3 = """
    .......gg.......
    ......gggg......
    ..gg..gggg..hh..
    .gggg.gggg.hhhh.
    ggggg.gggg.hhhhh
    .gggg..gg..hhhh.
    ..ggg..p...hhh..
    ....gp.p..ph....
    .hhh..pp.pp.ggg.
    hhhhh..ppp.ggggg
    .hhhhh.pp.ggggg.
    ..hh...pp...gg..
    """
    pal = {'g': SPINACH, 'h': SPINACH, 'p': STEM_RED, 's': STEM_RED}
    return crop([s0, s1, s2, s3], pal, seam='gh|p')


def onion_like(leaf_rows, bulb, xs=(4, 11)):
    p = Pic()
    for x in xs:
        for dx, h, bend, ch in leaf_rows:
            p.curve(x, 15, x + dx, 15 - h, ch, bend)
        if bulb:
            p.stamp(bulb, x - len(bulb[0]) // 2, 16 - len(bulb))
    return p


ONION_BULB = sprite("""
..t..
.bbb.
bbbbb
bbbbb
""")
GARLIC_BULB = sprite("""
.bbb.
bbbbb
bbbbb
""")


def plant_onion():
    s0 = """
    .g.....g...g....
    .g..g..g...g..g.
    ..g.g...g..g..g.
    ..g.g...g...g.g.
    """
    p1 = onion_like([(-2, 6, -0.3, 'g'), (0, 8, 0.1, 'h'), (2, 7, 0.3, 'g'), (1, 5, 0.2, 'G')], None, (3, 8, 13))
    leaves2 = [(-3, 10, -0.4, 'g'), (-1, 12, -0.1, 'h'), (1, 13, 0.1, 'g'), (3, 11, 0.4, 'h'), (0, 8, 0.0, 'G')]
    p2 = onion_like(leaves2, sprite("""
    .b.
    bbb
    """), (4, 11))
    leaves3 = [(-4, 9, -0.9, 'g'), (-1, 12, -0.1, 'h'), (1, 13, 0.1, 'g'), (4, 10, 0.8, 'h'), (2, 7, 0.3, 'G')]
    p3 = onion_like(leaves3, ONION_BULB, (4, 11))
    for x in (4, 11):
        p3.pts([(x - 4, 6), (x + 4, 5)], 'y')
    pal = {'l': (LEAF_BLUE, 3), 'g': LEAF_BLUE, 'h': LEAF_BLUE, 'G': LEAF_BLUE_BK, 'b': ONION, 't': ONION, 'y': (STRAW, 3)}
    pal2 = dict(pal, b=SCALLION_WHITE)
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], [pal, pal, pal2, pal], seam='Ggh|tb')


def plant_garlic():
    s0 = """
    ..g.......g.....
    ..g..g....g..g..
    ..g..g....g..g..
    """
    def straps(leaves, bulb, thick=True):
        p = Pic()
        for x in (4, 11):
            for dx, h, bend, ch in leaves:
                p.curve(x, 15, x + dx, 15 - h, ch, bend)
                if thick:
                    p.curve(x + (1 if dx >= 0 else -1), 15, x + dx + (1 if dx >= 0 else -1), 16 - h, ch, bend)
            if bulb:
                p.stamp(bulb, x - len(bulb[0]) // 2, 16 - len(bulb))
        return p
    p1 = straps([(-3, 6, -0.6, 'g'), (-1, 7, -0.2, 'h'), (1, 8, 0.1, 'g'), (3, 6, 0.6, 'h')], None, thick=False)
    leaves = [(-5, 8, -1.4, 'g'), (-1, 12, -0.4, 'h'), (2, 11, 0.6, 'g'), (5, 7, 1.4, 'h')]
    p2 = straps(leaves, sprite("""
    .b.
    bbb
    """))
    p3 = straps(leaves, GARLIC_BULB)
    for x in (4, 11):
        p3.put(x - 1, 15, 'v')
    pal = {'l': (LEAF_GREY, 3), 'g': LEAF_GREY, 'h': LEAF_GREY, 'G': LEAF_BK, 'b': GARLIC, 'v': (GARLIC_PURPLE, 2)}
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], pal, seam='gh|bv')


def plant_ginger():
    s0 = """
    ...p......p.....
    ...g..p...g..p..
    ...g..g...g..g..
    """
    def reeds(top, leaves_from):
        p = Pic()
        for x in (2, 7, 12):
            p.line(x, 15, x, top, 's')
            for i, y in enumerate(range(leaves_from, top - 1, -2)):
                m = 1 if i % 2 else -1
                p.pts([(x + m, y), (x + 2 * m, y - 1), (x + 3 * m, y - 1)], 'g' if m > 0 else 'h')
        return p
    p1 = reeds(9, 12)
    p2 = reeds(3, 12)
    p3 = reeds(2, 11)
    p3.stamp(sprite("""
    .rr.....rr...rr.
    rrrrr.rrrrrrrrrr
    """), 0, 14)
    pal = {'g': LEAF, 'h': LEAF, 's': STEM, 'p': (GINGER_PINK, 3), 'r': GINGER}
    return crop([s0, p1.rows(), p2.rows(), p3.rows()], pal, seam='ghs|r')


def plant_scallion():
    s0 = """
    ..g.....g....g..
    ..g..g..g....g..
    ..g..g..g..g.g..
    """
    def clump(tubes, base):
        p = Pic()
        for x0 in (2, 8, 13):
            for k, (dx, h, lean) in enumerate(tubes):
                p.curve(x0 + dx, 15, x0 + dx + lean, 15 - h, 'ghj'[k % 3], 0.0)
            if base:
                for dx in (-1, 0, 1):
                    p.put(x0 + dx, 15, 'w').put(x0 + dx, 14, 'w').put(x0 + dx, 13, 'v')
        return p
    p1 = clump([(-1, 5, -1), (0, 7, 0), (1, 6, 1)], False)
    p2 = clump([(-1, 9, -2), (0, 12, 0), (1, 10, 2), (0, 8, -1)], False)
    p3 = clump([(-1, 10, -3), (-1, 13, -1), (0, 15, 0), (1, 14, 1), (1, 11, 3)], True)
    pal = {'l': (SCALLION, 3), 'g': SCALLION, 'h': SCALLION, 'j': SCALLION, 'w': SCALLION_WHITE, 'v': (DAIKON_SHOULDER, 3)}
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], pal, seam='g|h|j|vw')


def plant_radish():
    s0 = """
    .....gg.hh......
    .....gg.hh......
    .......s........
    """
    s1 = """
    ......g..h......
    .....ggg.hhh....
    ..gg.gg..hh.hh..
    .gggg.gg.hhhhh..
    ..gg...ss...hh..
    .......ss.......
    """
    s2 = """
    ......gg.hh.....
    .....gggghhh....
    ..g..ggg.hhh..h.
    .ggg..gg.hh..hhh
    ggggg.gg.hh.hhhh
    .ggg.G.gghh.hhh.
    ..g.GGG.gh.GG.h.
    ....GGG.g..GGG..
    ......gggh.GG...
    .......ss.......
    .......ss.......
    """
    s3 = """
    ......gg.hh.....
    .....gggghhh....
    ..g..ggg.hhh..h.
    .ggg..gg.hh..hhh
    ggggg.gg.hh.hhhh
    .ggg.G.gghh.hhh.
    ..g.GGG.gh.GG.h.
    ....GGG.g..GGG..
    ......gggh.GG...
    ......kkkk......
    .....kkkkkk.....
    .....wwwwww.....
    .....wwwwww.....
    """
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': SCALLION_WHITE, 'k': DAIKON_SHOULDER, 'w': DAIKON}
    return crop([s0, s1, s2, s3], pal, seam='Ggh|kw')


HEART = sprite("""
xx.xx
xxxxx
.xxx.
..x..
""")


def plant_sweet_potato():
    s0 = """
    ......gg.hh.....
    .....gggshhh....
    ........s.......
    """
    s1 = """
    ...gg.gg..h.h...
    ...ggggg.hhhhh..
    ....ggg...hhh...
    .....g.s.s.h....
    .......sss......
    """
    def vine(hearts):
        p = Pic()
        for (x, y, ch) in hearts:
            p.curve(7, 15, x + 2, y + 3, 's', 0.8 if x < 7 else -0.8)
        for (x, y, ch) in hearts:
            p.stamp(HEART, x, y, sub={'x': ch})
        return p
    hearts = [(3, 4, 'g'), (9, 5, 'h'), (0, 9, 'h'), (6, 8, 'j'), (11, 10, 'g')]
    p2 = vine(hearts)
    p3 = vine(hearts)
    p3.stamp(sprite("""
    .rrr.....rr..
    rrrrr...rrrr.
    """), 2, 14)
    pal = {'g': LEAF, 'h': LEAF, 'j': LEAF, 's': STEM_PURPLE, 'r': SWEET_POTATO_M}
    return crop([s0, s1, p2.rows(), p3.rows()], pal, seam='s|g|h|j|r')


def plant_taro():
    s0 = """
    .......g........
    .......gg.......
    .......s........
    """
    s1 = """
    ...ggggg.hhhh...
    ...gggg..hhhh...
    ....gg...hhh....
    .....gs..sh.....
    ......s..s......
    .......ss.......
    .......ss.......
    """
    s2 = """
    ..gggggg........
    .gggggggg.hhhhh.
    .gggggvg.hhhhhhh
    ..ggggg..hvhhhh.
    ...ggs...shhh...
    ....gs...sh.....
    .....s...s......
    ..GGGs...s......
    .GGGGGs.sGG.....
    ..GGG.s.sGGG....
    ...G...ss.G.....
    .......ss.......
    .......ss.......
    """
    s3 = """
    .gggggg.........
    ggggggggg.hhhhh.
    ggggggvgg.hhhhhh
    gggggvggghhvhhhh
    .ggggggg..hvhhh.
    ..gggs....shhh..
    ...gs.....shh...
    ....s....s.h....
    ..GGs....s.GG...
    .GGGGs..s.GGGG..
    ..GGG.s.s.GGG...
    ...G...ss..G....
    .......ss.......
    .....bbbbbb.....
    ....bbbbbbbb....
    .....bbbbbb.....
    """
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'v': (LEAF, 3), 'b': TARO}
    return crop([s0, s1, s2, s3], pal, seam='Ggh|s|b')


def plant_strawberry():
    s0 = """
    ......gg.hh.....
    ......ggshh.....
    ........s.......
    """
    s1 = """
    ...gg.....hh....
    ..gggg...hhhh...
    ...gg.gg..hh....
    .....gggg.......
    ....s.gg.s......
    .....s.s.s......
    ......sss.......
    """
    s3 = """
    .....gg..hh.....
    ...g.gggghhh.h..
    ..ggg.gg.hh.hhh.
    .ggggg.ss..hhhhh
    ..ggg.s..s..hhh.
    .hh.Gs.fy.sG.gg.
    hhhhGG.ff.GGgggg
    .hhrrs....srrgg.
    ..rrrr.ss.rrrr..
    ..r*rr.ss.rr*r..
    ...rr..ss..rr...
    .......ss.......
    """
    g2 = sub(grid(s3), {'r': 'u', '*': 'u'})
    g2 = stamp(g2, ['fy'], 2, 9)
    g2 = stamp(g2, ['yf'], 12, 9)
    pal = {'g': LEAF_BRIGHT, 'h': LEAF_BRIGHT, 'G': LEAF_BRIGHT_BK, 's': STEM, 'f': FLOWER_WHITE,
           'y': (FLOWER_CENTRE, 3), 'r': STRAWBERRY_M, '*': (STRAW_SEED, 3), 'u': UNRIPE_BERRY}
    return crop([s0, s1, g2, s3], pal, seam='Gghs|ur')


GRAPE_BUNCH = sprite("""
.rr.
rrrr
r*rr
.rr.
..r.
""")


def plant_grape():
    s0 = """
    .......ww.......
    .......ww.......
    .......ww.......
    .......ww.......
    ......gwwg......
    .......ws.......
    .......ww.......
    """
    s1 = """
    .......ww.......
    .......ww.......
    .......ww.......
    .......ww.......
    ......gww.......
    .....ggwwhh.....
    ....ggswwhhh....
    .....gsww.hh....
    ......sws.......
    ...hh..wws.gg...
    ..hhhhswws.ggg..
    ...hh.swwsgg....
    .......wws......
    .......ww.......
    """
    s2 = """
    ....gg.ww.......
    ...gggsww.hhh...
    ..ggggsww.hhhh..
    ...gg.swwshhhh..
    ......sww.shh...
    .hhh..swwsGGG...
    hhhhh.swwGGGGG..
    .hhhGGswwsGGG...
    ...GGGswwsgg....
    ....GGswwgggg...
    ......swwsggg...
    ..gg..sww.......
    .gggg.sww.hh....
    ..gg..swwhhhh...
    .......ww.hh....
    .......ww.......
    """
    g3 = Pic(grid(s2))
    g3.stamp(GRAPE_BUNCH, 2, 8).stamp(GRAPE_BUNCH, 10, 9)
    g2 = Pic(grid(s2))
    g2.stamp(sub(GRAPE_BUNCH[:3], {'r': 'u', '*': 'u'}), 2, 8).stamp(sub(GRAPE_BUNCH[:3], {'r': 'u', '*': 'u'}), 10, 9)
    pal = {'g': LEAF, 'h': LEAF, 'G': LEAF_BK, 's': STEM, 'w': WOOD, 'r': GRAPE_M, '*': (GRAPE, 4), 'u': GRAPE_GREEN_M}
    return crop([s0, s1, g2.rows(), g3.rows()], pal, seam='Gghsw|ur')


def plant_blueberry():
    s0 = """
    ......g.h.......
    .......gh.......
    .......w........
    """
    s1 = """
    .....gg.hh......
    ....ggg.hhh.....
    ....gg.w.hh.....
    ......gwh.......
    ....gg.w.hh.....
    .......w........
    .......w........
    """
    s3 = """
    ................
    ....GG..hh......
    ..GGGGg.hhhh....
    .GGggggg.hhhhh..
    .GgggbbgghhGGhh.
    .ggggbbghhGGGGh.
    ..gbb.gggGGbbG..
    .hhbbhh.ggGbbgg.
    hhhhhhh.gggg.gg.
    .hhbbh.w..gbbg..
    ..hbbw..w.gbb...
    ....w...w.w.....
    .....w..w.w.....
    ......w.ww......
    .......ww.......
    .......ww.......
    """
    g2 = sub(grid(s3), {'b': 'u'})
    pal = {'g': LEAF_GREY, 'h': LEAF_GREY, 'G': LEAF_BK, 'w': WOODY, 'b': BLUEBERRY_M, 'u': BLUE_UNRIPE}
    return crop([s0, s1, g2, s3], pal, seam='Gghw|ub')


def plant_pineapple():
    s0 = """
    ......g..h......
    .......gh.......
    ......gghh......
    """
    s1 = """
    ...g........h...
    ....g......h....
    ..g..g.gh.h..h..
    ...g..gghh..h...
    ....gggghhhh....
    .....gggghh.....
    ......gghh......
    """
    def rosette(p):
        for tx, ty, ch in ((0, 6, 'g'), (2, 3, 'h'), (4, 2, 'g'), (11, 2, 'h'), (13, 3, 'g'), (15, 6, 'h'), (1, 10, 'G'),
                           (14, 10, 'G'), (6, 4, 'G'), (9, 4, 'G')):
            p.curve(7 if tx < 8 else 8, 15, tx, ty, ch, 0.2 if tx < 8 else -0.2)
            p.curve(7 if tx < 8 else 8, 14, tx + (1 if tx < 8 else -1), ty + 1, ch, 0.2 if tx < 8 else -0.2)
        return p
    p2 = rosette(Pic())
    p2.stamp(sprite("""
    .gg.
    .qq.
    qqqq
    .qq.
    """), 6, 8)
    p3 = rosette(Pic())
    p3.stamp(sprite("""
    .gg..
    g.g.g
    .ggg.
    .ppp.
    ppPpp
    pPpPp
    ppPpp
    .ppp.
    """), 5, 5)
    pal = {'g': PINE_LEAF, 'h': PINE_LEAF, 'G': LEAF_BLUE_BK, 'q': PINE_FLOWER, 'p': PINEAPPLE, 'P': (PINEAPPLE, 1)}
    return crop([s0, s1, p2.rows(), p3.rows()], pal, seam='Ggh|qp')


def plant_tea():
    s0 = """
    ......gg.hh.....
    .......g.h......
    ........w.......
    """
    s1 = """
    .....gg.hh......
    ....gggghhh.....
    ...ggg.w.hhh....
    ....g.gwh.h.....
    .......w........
    .......w........
    """
    s2 = """
    .....GGGgg......
    ...GGGGGgggg....
    ..GGGGgggggggh..
    .GGGGgggggghhhh.
    .GGGgggggghhhhh.
    GGGgggghhhhhhhhh
    .GGgghhhhhhhhhh.
    .GGghhhhhhhhhhh.
    ..ghhhhhhhhhhh..
    ....hhhhhhhh....
    ......w..w......
    .......ww.......
    .......ww.......
    """
    g3 = Pic(grid(s2))
    for x, y in ((6, 3), (9, 3), (4, 4), (11, 4), (13, 6), (2, 6), (8, 4), (14, 8), (1, 8), (12, 5)):
        g3.put(x, y, 'k')
    g2 = Pic(grid(s2))
    for x, y in ((7, 3), (11, 4), (3, 5)):
        g2.put(x, y, 'k')
    pal = {'g': LEAF_GLOSS, 'h': LEAF_GLOSS, 'G': LEAF_GLOSS_BK, 'w': WOODY, 'k': (TEA_TIP, 3)}
    return crop([s0, s1, g2.rows(), g3.rows()], pal, seam='Ggh|w')


def plant_coffee_shrub():
    s0 = """
    ........c.......
    .......cc.......
    ........s.......
    ........s.......
    """
    s1 = """
    ......gg.hh.....
    .....ggg.hhh....
    ........w.......
    ...gg...w..hh...
    ..ggg...w..hhh..
    ........w.......
    ........w.......
    """
    s3 = """
    .......gg.......
    ......gggg......
    ..gg....w...hh..
    .gggg.rrwrr.hhhh
    gggggwwwwwwwhhhh
    .ggg.rr.w.rrhhh.
    ...hh...w...gg..
    ..hhhh..w..gggg.
    .hhhhhwwwwwggggg
    hhhhrr..w..rrgg.
    .hh.rr..w...rr..
    ........w.......
    ........w.......
    ........w.......
    ........w.......
    ........w.......
    """
    g2 = Pic(sub(grid(s3), {'r': ''}))
    g2.pts([(6, 3), (10, 3), (5, 5), (11, 5), (4, 9), (12, 9), (5, 10), (13, 10)], 'f')
    g2 = g2.rows()
    pal = {'g': LEAF_GLOSS, 'h': LEAF_GLOSS, 'w': WOODY, 'r': COFFEE_CHERRY_M, 'f': (FLOWER_WHITE, 3), 'c': (PAPER, 3),
           's': STEM}
    return crop([s0, s1, g2, s3], pal, seam='ghw|r')


def plant_sichuan_pepper():
    s0 = """
    ......g.h.......
    .......gh.......
    .......w........
    """
    s1 = """
    .....g.h........
    ....gg.hh.......
    .....g.w.h.g....
    ......wwhhgg....
    .......w...g....
    ......tw........
    .......w........
    """
    s3 = """
    ................
    .....gg..hh.....
    ...gggrrhhhh....
    ..gggrrrrhh.hh..
    .gg..rrww..hhhh.
    ..gg.twg.wrr.h..
    ...gggw.gwrrr...
    .hh..tw.w.rr.gg.
    hhhrrw..wt..gggg
    .hrrrw.w...gg.g.
    ..rr..ww........
    .......wt.......
    ......tw........
    .......w........
    .......w........
    .......w........
    """
    g2 = sub(grid(s3), {'r': 'u'})
    pal = {'g': LEAF_GLOSS, 'h': LEAF_GLOSS, 'w': WOODY, 't': (WOODY, 3), 'r': SICHUAN, 'u': SICHUAN_GREEN}
    return crop([s0, s1, g2, s3], pal, seam='ghwt|ur')


def plant_mung_bean():
    s0 = """
    ......gg.hh.....
    ......ggshh.....
    ........s.......
    ........s.......
    """
    s1 = """
    .......gg.......
    ......gggg......
    ...hh..gg..hh...
    ..hhhh.s..hhhh..
    ...hhh.s.hhhh...
    .....h.ss.h.....
    ........s.......
    ........s.......
    """
    s3 = """
    ......gg.gg.....
    ....g.gggggg.g..
    ...ggg.gsgg.ggg.
    ..hhgg.GsG.gghh.
    .hhhh.GGsGG.hhhh
    ..hhGGGGsGGGGhh.
    .gg.GGGGsGGGG.gg
    ggggGGGGsGGGGggg
    .gggd.dGsGd.dgg.
    ..g.d.d.s.d.d.g.
    ...d.d..s..d.d..
    ...d.d..s..d.d..
    ........s.......
    ........s.......
    """
    g2 = Pic(sub(grid(s3), {'d': ''}))
    g2.pts([(4, 10), (4, 11), (12, 10), (12, 11)], 'u').pts([(6, 10), (10, 10)], 'f')
    pal = {'g': LEAF_YG, 'h': LEAF_YG, 'G': LEAF_YG_BK, 's': STEM, 'd': MUNG_POD, 'u': SOY_POD_GREEN,
           'f': (FLOWER_YELLOW, 3)}
    return crop([s0, s1, g2.rows(), s3], pal, seam='Gsgh|ud')


def plant_barley():
    s0 = """
    ...........g....
    ..g.g......g.g..
    ...g...g.g..g...
    ...g....g...g...
    """
    xs = (2, 7, 12)
    p1 = Pic()
    for x in xs:
        p1.curve(x, 15, x - 2, 10, 'g', -0.4).curve(x, 15, x, 8, 'g', 0.2).curve(x, 15, x + 2, 9, 'g', 0.4)
    def stalks():
        p = Pic()
        for x in xs:
            p.curve(x, 15, x - 3, 10, 'G', -0.5).curve(x, 15, x + 3, 11, 'G', 0.5)
            p.line(x, 15, x, 5, 's').line(x + 2, 15, x + 2, 7, 's')
        return p
    p2, p3 = stalks(), stalks()
    for p in (p2, p3):
        for x in xs:
            for hx, top in ((x, 4), (x + 2, 6)):
                p.pts([(hx, top), (hx, top - 1), (hx, top - 2), (hx - 1, top - 1), (hx - 1, top - 2), (hx + 1, top - 1)], 'k')
                p.pts([(hx - 1, top - 3), (hx - 2, top - 5), (hx, top - 4), (hx, top - 5), (hx + 1, top - 3),
                       (hx + 2, top - 5)], 'a')
    pal2 = {'l': (LEAF_BLUE, 3), 'g': LEAF_BLUE, 'G': LEAF_BLUE_BK, 's': LEAF_YG, 'k': PANICLE, 'a': (PANICLE, 3)}
    pal3 = {'l': (LEAF_BLUE, 3), 'g': LEAF_BLUE, 'G': GOLD_BK, 's': STRAW, 'k': BARLEY, 'a': (BARLEY, 3)}
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], [pal2, pal2, pal2, pal3], seam='Gs|k')


def plant_chinese_chive():
    s0 = """
    ..g.g.....g.g...
    ...g..g....g..g.
    ...g..g....g..g.
    """
    def clumps(n, tall, flowers):
        p = Pic()
        for x in (2, 7, 12):
            for i in range(n):
                dx = (i - n // 2)
                h = tall - abs(dx) * 2 + (i % 2)
                p.curve(x, 15, x + dx * 2, 15 - h, 'g' if i % 2 else 'h', dx * 0.4)
            if flowers:
                p.line(x + 1, 15, x + 1, 2, 's')
                p.pts([(x, 1), (x + 1, 1), (x + 2, 1), (x + 1, 0), (x, 2), (x + 2, 2)], 'f')
        return p
    p1 = clumps(3, 7, False)
    p2 = clumps(5, 11, False)
    p3 = clumps(5, 13, True)
    pal = {'l': (CHIVE, 3), 'g': CHIVE, 'h': CHIVE, 's': CHIVE, 'f': FLOWER_WHITE}
    return crop([tipped(s0), p1.rows(), p2.rows(), p3.rows()], pal, seam='gh|f')


# =====================================================================================================================
# output

PLANT_ART = {c[0]: globals()['plant_' + c[0]] for c in SPEC.CROPS}


def textures(only=None):
    """Every texture of the crops: {path relative to textures/: (image, kind)}."""
    out = {}
    for c in SPEC.CROPS:
        cid, produce = c[0], c[3]
        if only and cid not in only:
            continue
        art = PLANT_ART[cid]()
        assert len(art['stages']) == 4, cid
        for n, (g, pal, seam) in enumerate(zip(art['stages'], art['pal'], art['seam'])):
            assert len(g) == 16 and all(len(r) == 16 for r in g), (cid, n)
            assert g[15].strip('.'), f'{cid} stage {n}: the plant must stand on the bottom row'
            out[f'block/crop/{cid}_stage{n}.png'] = (render(g, pal, ground=True, seam=seam), 'plant')
        for path, fn in ((f'item/{cid}_seeds.png', SEED_ART[cid]), (f'item/{produce}.png', PRODUCE_ART[produce])):
            g, pal, mode, seam, kw = fn()
            out[path] = (render(g, pal, outline=mode, seam=seam, **kw), 'item')
    return out


def sheet(tex, path, scale=6):
    """Review sheet: one row per crop with its four stages, the seeds and the produce."""
    font = ImageFont.load_default(size=15)
    small = ImageFont.load_default(size=12)
    cell = 16 * scale
    gap = 10
    left = 150
    crops = [c for c in SPEC.CROPS if f'block/crop/{c[0]}_stage0.png' in tex]
    w = left + 6 * (cell + gap) + gap
    h = 40 + len(crops) * (cell + gap) + gap
    img = Image.new('RGBA', (w, h), (126, 132, 122, 255))
    d = ImageDraw.Draw(img)
    for i, head in enumerate(('stage 0', 'stage 1', 'stage 2', 'stage 3', 'seeds', 'produce')):
        d.text((left + i * (cell + gap) + 4, 14), head, fill=(30, 30, 30, 255), font=font)
    for r, c in enumerate(crops):
        y = 40 + r * (cell + gap)
        d.text((10, y + cell // 2 - 16), c[0], fill=(15, 15, 15, 255), font=font)
        d.text((10, y + cell // 2 + 4), f'{c[7]} -> {c[3]}', fill=(45, 45, 45, 255), font=small)
        paths = [f'block/crop/{c[0]}_stage{n}.png' for n in range(4)] + [f'item/{c[0]}_seeds.png', f'item/{c[3]}.png']
        for i, pth in enumerate(paths):
            x = left + i * (cell + gap)
            bg = (104, 92, 74, 255) if i < 4 else (150, 156, 146, 255)
            d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=bg)
            if i < 4:
                d.rectangle((x, y + cell - scale, x + cell - 1, y + cell - 1), fill=(78, 58, 40, 255))
            img.alpha_composite(tex[pth][0].resize((cell, cell), Image.NEAREST), (x, y))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.save(path)


def main(argv):
    only = None
    sheet_path = None
    i = 0
    while i < len(argv):
        if argv[i] == '--sheet':
            sheet_path = argv[i + 1]
            i += 2
        elif argv[i] == '--only':
            only = set(argv[i + 1].split(','))
            i += 2
        else:
            raise SystemExit(__doc__)
    tex = textures(only)
    for rel, (img, kind) in sorted(tex.items()):
        assert img.size == (16, 16) and img.mode == 'RGBA', rel
        path = os.path.join(TEX, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path, format='PNG')
    n_plant = sum(1 for v in tex.values() if v[1] == 'plant')
    print(f'{n_plant} crop stage textures, {len(tex) - n_plant} item textures -> {os.path.relpath(TEX, ROOT)}')
    if sheet_path:
        sheet(tex, sheet_path)
        print('review sheet ->', sheet_path)


if __name__ == '__main__':
    main(sys.argv[1:])
