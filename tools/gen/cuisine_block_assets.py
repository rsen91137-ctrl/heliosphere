#!/usr/bin/env python3
"""Cookware and block assets of the food expansion (overlay jars/04-foods), after the 'look' notes of the MACHINES and
BLOCKS in cuisine_spec.py:

  textures/block/cuisine/<id>_<part>.png     16x16 pixel art (light from the top left, shaded, no noise)
  models/block/<id>.json, <id>_on.json       off / working (lit) models
  blockstates/<id>.json                      machines: facing x lit (y rotation 0/90/180/270 for N/E/S/W);
                                             induction stove and hydroponic planter: lit
  items/<id>.json                            item model definition pointing at the block model

The low cookware (wok, cooking pot, bamboo steamer, fermentation crock) are element models built from voxels: every
part is a set of 1-pixel cells with one material; the cells are merged into as few boxes as possible, faces that are
fully covered by other cells are dropped, faces on the block boundary get their cullface. UVs are explicit and come
from a 'frame' per part: the side faces of a round body sample one elevation drawing (the same from all four sides,
centred on the body), top and bottom faces a plan drawing. Every element stays inside the block's outline shape
(CookwareBlock.shapeOf), which is checked. The oven, grain mill and juicer are orientable full cubes; the induction
stove and the planter full cubes of their own.

Deterministic (no randomness, no timestamps); files are only rewritten when their bytes change. Run from the
repository root (or anywhere):
    python3 tools/gen/cuisine_block_assets.py [--sheet review.png]
--sheet also writes a review sheet: every texture enlarged and a rasterised view of every model (off and on) from
two sides, from below, and as the inventory icon.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import cuisine_spec as SPEC  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
RES = os.path.join(ROOT, 'jars/04-foods/src/main/resources')
ASSETS = os.path.join(RES, 'assets/heliosphere')
NS = 'heliosphere'
TEX = 'block/cuisine'
N = 16

MACHINES = [m[0] for m in SPEC.MACHINES]
BLOCKS = [b[0] for b in SPEC.BLOCKS]
LOW = ('wok', 'cooking_pot', 'steamer', 'fermenter')
CUBES = ('oven', 'grain_mill', 'juicer')
# outline / collision boxes in pixels (CookwareBlock.shapeOf); the element models must stay inside
SHAPES = {'wok': (1, 0, 1, 15, 5, 15), 'cooking_pot': (2, 0, 2, 14, 10, 14), 'steamer': (2, 0, 2, 14, 11, 14),
          'fermenter': (3, 0, 3, 13, 14, 13)}


# =====================================================================================================================
# colours


def rgba(h):
    """'#rrggbb' or '#rrggbbaa' -> (r, g, b, a)."""
    h = h.lstrip('#')
    v = [int(h[i:i + 2], 16) for i in range(0, len(h), 2)]
    return tuple(v) if len(v) == 4 else (v[0], v[1], v[2], 255)


def mix(a, b, t):
    a, b = rgba(a), rgba(b)
    return '#%02x%02x%02x' % tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp_at(ramp, t):
    """The tone of a dark-to-light ramp at t in [0, 1] (nearest tone, no dithering)."""
    t = min(1.0, max(0.0, t))
    return ramp[int(round(t * (len(ramp) - 1)))]


# Ramps, darkest to lightest; shadows lean cool, highlights warm.
STEEL = ['#3a3f4a', '#555c69', '#737b89', '#959daa', '#b8bfca', '#d8dde4', '#f1f4f7']     # stainless
DSTEEL = ['#1f2229', '#2c3039', '#3c414c', '#505662', '#686f7c', '#858c99']                   # dark steel
IRON = ['#0d0e12', '#16181e', '#1f2229', '#2a2e37', '#373c47', '#474d5a', '#5e6573', '#7b8391', '#a0a8b5']  # wok
PATINA = ['#1b1511', '#2a2019', '#3a2c21', '#4c3a2a', '#614a34', '#7a5e42']              # seasoned wok bowl
WOOD = ['#3a2416', '#553621', '#704a2d', '#8b5f3a', '#a47548', '#bc8d5b', '#d4a874']
BAMBOO = ['#5c4520', '#7a5f2d', '#987a3b', '#b4944b', '#cbad5f', '#dfc679', '#efdc9c']
BIND = ['#3e2c14', '#56401f', '#6f552b', '#8a6c38']                                      # bamboo binding strips
GLAZE = ['#1c0e07', '#2f170b', '#452210', '#5e2f16', '#7a3f1c', '#985224', '#b86c34', '#dc9a5a', '#f2c68e']
CLAY = ['#5a2e1a', '#7a4026', '#985436', '#b06a48']                                      # unglazed foot
OAK = ['#3d2913', '#56391c', '#715027', '#8c6634', '#a67d43', '#bf9656']
WATER = ['#173560', '#21487e', '#2e60a0', '#4a82c2', '#76a9dd', '#b4d6f4']
SOUP = ['#6a1f0c', '#922f12', '#b8461a', '#d76428', '#ee8838', '#fbb45c', '#ffe0a0']
EMBER = ['#3c0905', '#6e1207', '#a8220b', '#de4413', '#ff7a24', '#ffb446', '#ffe08a', '#fff6d0']
NUTRIENT = ['#0b2c1d', '#12452c', '#1b6340', '#268456', '#3aa76c', '#62c98a', '#a6ecb6']
GLASS = ['#050608', '#0a0c10', '#11141a', '#191d25', '#232832', '#30363f', '#464d5a']
CREAM = ['#6a604c', '#8d8269', '#ada288', '#c7bda2', '#dbd3b9', '#ece6d0', '#f8f4e6']
WHITE = ['#646c7a', '#8b94a2', '#acb4c0', '#c7cdd6', '#dce1e7', '#ecf0f4', '#fbfcfd']
CHROME = ['#2e333c', '#4c535f', '#727a87', '#a0a8b4', '#cdd3db', '#f4f6f9']


# =====================================================================================================================
# drawing


def canvas(color=None, h=N, w=N):
    img = np.zeros((h, w, 4), dtype=np.uint8)
    if color is not None:
        img[:, :] = rgba(color)
    return img


def put(img, x, y, c):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = rgba(c)


def rect(img, x0, y0, x1, y1, c):
    """Fill x0 <= x < x1, y0 <= y < y1."""
    img[max(0, y0):y1, max(0, x0):x1] = rgba(c)


def stamp(img, rows, pal, x0=0, y0=0):
    """Paint a character grid; '.' leaves the pixel as it is, ' ' makes it transparent."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            if ch == ' ':
                img[y0 + y, x0 + x] = (0, 0, 0, 0)
                continue
            assert ch in pal, (ch, row)
            put(img, x0 + x, y0 + y, pal[ch])
    return img


def grid(text):
    rows = [r.strip() for r in text.strip('\n').split('\n')]
    rows = [r for r in rows if r]
    assert len(rows) == N and all(len(r) == N for r in rows), [len(r) for r in rows]
    return rows


def bevel(img, ramp, hi=None, lo=None):
    """Light top row and left column, shaded bottom row and right column, like the industry casings."""
    n = len(ramp)
    hi = ramp[min(n - 1, 4)] if hi is None else hi
    lo = ramp[1] if lo is None else lo
    img[0, :] = img[:, 0] = rgba(hi)
    img[N - 1, :] = img[:, N - 1] = rgba(lo)
    img[0, 0] = rgba(ramp[-1])
    img[N - 1, N - 1] = rgba(ramp[0])
    img[0, N - 1] = img[N - 1, 0] = rgba(ramp[2])
    return img


def rivet(img, x, y, ramp):
    put(img, x, y, ramp[-1])
    put(img, x, y + 1, ramp[1])


def casing(ramp, base=3, rivets=((1, 1), (14, 1), (1, 13), (14, 13)), streaks=()):
    """A machine side: bevelled plate, deliberate brushed streaks (x, y, length, +1 lighter / -1 darker), bolts."""
    img = canvas(ramp[base])
    for x, y, n, s in streaks:
        img[y, x:x + n] = rgba(ramp[base + s])
    bevel(img, ramp)
    for x, y in rivets:
        rivet(img, x, y, ramp)
    return img


def disc_rows(d, cuts=None):
    """Per row of a round disc of diameter d: the inset from each side (a pixel circle unless cuts are given)."""
    if cuts is not None:
        assert len(cuts) == d, (d, cuts)
        return list(cuts)
    r = d / 2
    out = []
    for i in range(d):
        dist = abs(i + 0.5 - r)
        w = 2 * math.sqrt(max(0.0, r * r - dist * dist))
        best = min(range(d % 2, d + 1, 2), key=lambda k: (abs(k - w), -k))
        out.append((d - best) // 2)
    return out


ROUND = {4: [1, 0, 0, 1], 6: [1, 0, 0, 0, 0, 1], 8: [2, 1, 0, 0, 0, 0, 1, 2], 10: [3, 1, 0, 0, 0, 0, 0, 0, 1, 3],
         12: [3, 2, 1, 0, 0, 0, 0, 0, 0, 1, 2, 3]}


def disc(x0, z0, d, cuts=None):
    """The (x, z) cells of a round disc whose bounding square starts at (x0, z0)."""
    rows = disc_rows(d, ROUND.get(d) if cuts is None else cuts)
    return {(x0 + c + i, z0 + j) for j, c in enumerate(rows) for i in range(d - 2 * c)}


def ring(x0, z0, d, inner_d, cuts=None, inner_cuts=None):
    """A disc minus a centred smaller disc."""
    k = (d - inner_d) // 2
    assert (d - inner_d) % 2 == 0, (d, inner_d)
    return disc(x0, z0, d, cuts) - disc(x0 + k, z0 + k, inner_d, inner_cuts)


def layer(cells2d, y0, y1):
    return {(x, y, z) for (x, z) in cells2d for y in range(y0, y1)}


def cuboid(x0, y0, z0, x1, y1, z1):
    return {(x, y, z) for x in range(x0, x1) for y in range(y0, y1) for z in range(z0, z1)}


# =====================================================================================================================
# element models

DIRS = ('down', 'up', 'north', 'south', 'west', 'east')
NORMAL = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0),
          'east': (1, 0, 0)}


class Frame:
    """How a part's faces map to its textures. The body occupies x0..x1, z0..z1 and its top is at ytop; side faces
    sample an elevation drawing whose left edge is at u = us and whose top row (y = ytop) is v = vs, viewed from
    outside (so all four sides read the same for a round body); 'inner' faces (facing the body's axis) use ui, vi;
    up faces a plan with north up (x0, z0 at ut, vt); down faces the plan seen from below (north down, at ub, vb).
    Frame() is the vanilla mapping (uv = the face's position in the block)."""

    def __init__(self, x0=0, z0=0, x1=16, z1=16, ytop=16, us=None, vs=None, ui=None, vi=None, ut=None, vt=None,
                 ub=None, vb=None):
        self.x0, self.z0, self.x1, self.z1, self.ytop = x0, z0, x1, z1, ytop
        self.us = x0 if us is None else us
        self.vs = 16 - ytop if vs is None else vs
        self.ui = self.us if ui is None else ui
        self.vi = self.vs if vi is None else vi
        self.ut = x0 if ut is None else ut
        self.vt = z0 if vt is None else vt
        self.ub = x0 if ub is None else ub
        self.vb = 16 - z1 if vb is None else vb

    @property
    def center(self):
        return (self.x0 + self.x1) / 2, (self.z0 + self.z1) / 2

    def uv(self, d, lo, hi, inner=False):
        (x1, y1, z1), (x2, y2, z2) = lo, hi
        f = self
        if d == 'up':
            return [x1 - f.x0 + f.ut, z1 - f.z0 + f.vt, x2 - f.x0 + f.ut, z2 - f.z0 + f.vt]
        if d == 'down':
            return [x1 - f.x0 + f.ub, f.z1 - z2 + f.vb, x2 - f.x0 + f.ub, f.z1 - z1 + f.vb]
        us, vs = (f.ui, f.vi) if inner else (f.us, f.vs)
        v = [f.ytop - y2 + vs, f.ytop - y1 + vs]
        if d == 'south':
            return [x1 - f.x0 + us, v[0], x2 - f.x0 + us, v[1]]
        if d == 'north':
            return [f.x1 - x2 + us, v[0], f.x1 - x1 + us, v[1]]
        if d == 'west':
            return [z1 - f.z0 + us, v[0], z2 - f.z0 + us, v[1]]
        return [f.z1 - z2 + us, v[0], f.z1 - z1 + us, v[1]]   # east


WORLD = Frame()


class Part:
    """One material: texture keys per face class ('up', 'down', 'side', 'inner' or a direction; 'all' as the
    fallback), a frame, and the element flags."""

    def __init__(self, tex, frame=WORLD, emission=0, shade=True, opaque=True):
        self.tex = {'all': tex} if isinstance(tex, str) else dict(tex)
        self.frame = frame
        self.emission = emission
        self.shade = shade
        self.opaque = opaque

    def key(self, d, inner):
        t = self.tex
        if d in t:
            return t[d]
        if d in ('up', 'down'):
            return t.get(d, t.get('all'))
        if inner and 'inner' in t:
            return t['inner']
        return t.get('side', t.get('all'))


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def runs(xs):
    """Maximal runs of consecutive integers -> [(start, end_exclusive)]."""
    out = []
    for x in sorted(xs):
        if out and out[-1][1] == x:
            out[-1][1] = x + 1
        else:
            out.append([x, x + 1])
    return [tuple(r) for r in out]


def boxes_of(cells):
    """Greedy decomposition of a set of cells into boxes (x0, y0, z0, x1, y1, z1): runs along x, merged over z, then
    identical rectangles merged over y."""
    by_y = {}
    for x, y, z in cells:
        by_y.setdefault(y, set()).add((x, z))
    rects_by_y = {}
    for y, cs in by_y.items():
        rows = {}
        for x, z in cs:
            rows.setdefault(z, []).append(x)
        rects, open_ = [], {}
        for z in range(min(rows), max(rows) + 2):
            spans = set(runs(rows.get(z, [])))
            for sp in sorted(open_):
                if sp not in spans:
                    rects.append((sp[0], open_.pop(sp), sp[1], z))
            for sp in sorted(spans):
                open_.setdefault(sp, z)
        rects_by_y[y] = set(rects)
    boxes, open3 = [], {}
    ys = sorted(rects_by_y)
    for y in range(ys[0], ys[-1] + 2):
        rs = rects_by_y.get(y, set())
        for r in sorted(open3):
            if r not in rs:
                boxes.append((r[0], open3.pop(r), r[1], r[2], y, r[3]))
        for r in sorted(rs):
            open3.setdefault(r, y)
    return sorted(boxes, key=lambda b: (b[1], b[2], b[0], b[4], b[5], b[3]))


class Model:
    """A block model: parts filled with cells (later fills win), plus raw elements; parent block/block."""

    def __init__(self, textures, particle, ao=True):
        self.textures = dict(textures)
        self.particle = particle
        self.cells = {}
        self.parts = []
        self.raw = []
        self.display = None
        self.ao = ao

    def part(self, tex, frame=WORLD, **kw):
        self.parts.append(Part(tex, frame, **kw))
        return len(self.parts) - 1

    def fill(self, p, cells):
        for c in cells:
            assert all(0 <= v < 16 for v in c), c
            self.cells[c] = p

    def carve(self, cells):
        for c in cells:
            self.cells.pop(c, None)

    def element(self, lo, hi, faces, rotation=None, emission=0, shade=True):
        """A raw element; faces: direction -> (texture key, uv or Frame, cullface or None)."""
        out = {}
        for d in DIRS:
            if d in faces:
                key, uv, cull = faces[d]
                if isinstance(uv, Frame):
                    uv = uv.uv(d, lo, hi)
                out[d] = (key, uv, cull)
        self.raw.append((tuple(lo), tuple(hi), out, rotation, emission, shade))

    def elements(self):
        occ = {c for c, p in self.cells.items() if self.parts[p].opaque}
        els = []
        for pi, part in enumerate(self.parts):
            cells = {c for c, p in self.cells.items() if p == pi}
            if not cells:
                continue
            cx, cz = part.frame.center
            for b in boxes_of(cells):
                x0, y0, z0, x1, y1, z1 = b
                faces = {}
                for d in DIRS:
                    if hidden(b, d, occ):
                        continue
                    lo, hi = (x0, y0, z0), (x1, y1, z1)
                    nx, ny, nz = NORMAL[d]
                    fx = (x0 + x1) / 2 if nx == 0 else (x1 if nx > 0 else x0)
                    fz = (z0 + z1) / 2 if nz == 0 else (z1 if nz > 0 else z0)
                    inner = ny == 0 and (fx - cx) * nx + (fz - cz) * nz < 0
                    uv = part.frame.uv(d, lo, hi, inner)
                    faces[d] = (part.key(d, inner), uv, cull(b, d))
                if faces:
                    els.append(((x0, y0, z0), (x1, y1, z1), faces, None, part.emission, part.shade))
        return els + self.raw

    def json(self):
        out = {'parent': 'minecraft:block/block'}
        if not self.ao:
            out['ambientocclusion'] = False
        tex = {'particle': '#' + self.particle}
        tex.update({k: f'{NS}:{TEX}/{v}' for k, v in sorted(self.textures.items())})
        out['textures'] = tex
        if self.display:
            out['display'] = self.display
        els = []
        for lo, hi, faces, rot, emission, shade in self.elements():
            e = {'from': [num(v) for v in lo], 'to': [num(v) for v in hi]}
            if rot:
                e['rotation'] = {'origin': [num(v) for v in rot['origin']], 'axis': rot['axis'], 'angle': num(rot['angle'])}
            if not shade:
                e['shade'] = False
            if emission:
                e['light_emission'] = emission
            fs = {}
            for d in DIRS:
                if d in faces:
                    key, uv, c = faces[d]
                    assert key in self.textures, (key, sorted(self.textures))
                    assert all(-1e-6 <= v <= 16 + 1e-6 for v in uv), (key, d, uv)
                    f = {'uv': [num(v) for v in uv], 'texture': '#' + key}
                    if c:
                        f['cullface'] = c
                    fs[d] = f
            e['faces'] = fs
            els.append(e)
        out['elements'] = els
        return out


def hidden(b, d, occ):
    """Whether face d of box b is completely covered by opaque cells."""
    x0, y0, z0, x1, y1, z1 = b
    if d == 'down':
        cells = ((x, y0 - 1, z) for x in range(x0, x1) for z in range(z0, z1))
    elif d == 'up':
        cells = ((x, y1, z) for x in range(x0, x1) for z in range(z0, z1))
    elif d == 'north':
        cells = ((x, y, z0 - 1) for x in range(x0, x1) for y in range(y0, y1))
    elif d == 'south':
        cells = ((x, y, z1) for x in range(x0, x1) for y in range(y0, y1))
    elif d == 'west':
        cells = ((x0 - 1, y, z) for y in range(y0, y1) for z in range(z0, z1))
    else:
        cells = ((x1, y, z) for y in range(y0, y1) for z in range(z0, z1))
    return all(c in occ for c in cells)


def cull(b, d):
    x0, y0, z0, x1, y1, z1 = b
    edge = {'down': y0 == 0, 'up': y1 == 16, 'north': z0 == 0, 'south': z1 == 16, 'west': x0 == 0, 'east': x1 == 16}
    return d if edge[d] else None


# =====================================================================================================================
# preview rasteriser (orthographic, z-buffered, back faces culled, vanilla face shading, alpha test as for cut-out)

FACE_SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}


def face_corners(d, lo, hi):
    """The corners of a face at the texture's top-left, top-right, bottom-right, bottom-left (vanilla UV layout)."""
    (x1, y1, z1), (x2, y2, z2) = lo, hi
    return {
        'north': [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        'south': [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        'west': [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        'east': [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        'up': [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        'down': [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[d]


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


CUBE_PARENTS = {
    'minecraft:block/orientable_with_bottom': {'down': 'bottom', 'up': 'top', 'north': 'front', 'south': 'side',
                                                'west': 'side', 'east': 'side'},
    'minecraft:block/orientable': {'down': 'top', 'up': 'top', 'north': 'front', 'south': 'side', 'west': 'side',
                                   'east': 'side'},
}


def model_quads(js, images):
    """Faces of a model JSON (our element models, or an orientable cube) as (corners, uv, image, normal, shade)."""
    textures = dict(js.get('textures', {}))
    els = js.get('elements')
    if els is None:
        keys = CUBE_PARENTS[js['parent']]
        els = [{'from': [0, 0, 0], 'to': [16, 16, 16],
                'faces': {d: {'texture': '#' + keys[d], 'cullface': d} for d in DIRS}}]

    def resolve(ref):
        while ref.startswith('#'):
            ref = textures[ref[1:]]
        return images[ref.split(':', 1)[1]]

    out = []
    for e in els:
        lo, hi = e['from'], e['to']
        rot = e.get('rotation')
        for d, f in e['faces'].items():
            assert f.get('rotation', 0) == 0
            pts = np.array(face_corners(d, lo, hi), float)
            normal = np.array(NORMAL[d], float)
            if rot:
                m = rot_matrix(rot['axis'], rot['angle'])
                o = np.array(rot['origin'], float)
                pts = (pts - o) @ m.T + o
                normal = m @ normal
            uv = f.get('uv') or Frame().uv(d, lo, hi)
            out.append((pts, uv, resolve(f['texture']), normal, e.get('shade', True) and FACE_SHADE[d] or 1.0))
    return out


def euler_xyz(rx, ry, rz):
    return rot_matrix('x', rx) @ rot_matrix('y', ry) @ rot_matrix('z', rz)


def view_basis(cam):
    c = np.array(cam, float)
    c /= np.linalg.norm(c)
    f = -c
    right = np.cross(f, [0.0, 1.0, 0.0])
    right /= np.linalg.norm(right)
    up = np.cross(right, f)
    return right, up, f


def rasterise(quads, basis, size, scale, offset=(0.0, 0.0), center=(8, 8, 8), frame=None):
    """Draw quads into a size x size RGBA image. basis: (right, up, forward) in model space; scale: image pixels per
    model pixel; offset: image-pixel shift. frame: optional box (x0, y0, z0, x1, y1, z1) drawn as an outline."""
    right, up, fwd = basis
    img = np.zeros((size, size, 4), float)
    depth = np.full((size, size), np.inf)
    cx = size / 2 + offset[0]
    cy = size / 2 + offset[1]
    cen = np.array(center, float)

    def project(p):
        q = p - cen
        return np.stack([cx + (q @ right) * scale, cy - (q @ up) * scale, q @ fwd], axis=-1)

    for pts, uv, tex, normal, shade in quads:
        if normal @ fwd >= -1e-6:
            continue
        P = project(pts)
        tl, tr, bl = P[0], P[1], P[3]
        e1, e2 = tr - tl, bl - tl
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        x0 = max(0, int(math.floor(P[:, 0].min())))
        x1 = min(size, int(math.ceil(P[:, 0].max())) + 1)
        y0 = max(0, int(math.floor(P[:, 1].min())))
        y1 = min(size, int(math.ceil(P[:, 1].max())) + 1)
        if x0 >= x1 or y0 >= y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        px, py = gx - tl[0], gy - tl[1]
        s = (px * e2[1] - py * e2[0]) / det
        t = (e1[0] * py - e1[1] * px) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not inside.any():
            continue
        z = tl[2] + s * e1[2] + t * e2[2]
        h, w = tex.shape[:2]
        u = uv[0] + s * (uv[2] - uv[0])
        v = uv[1] + t * (uv[3] - uv[1])
        iu = np.clip(np.floor(u / 16 * w - (1e-6 if uv[2] < uv[0] else -1e-6)).astype(int), 0, w - 1)
        iv = np.clip(np.floor(v / 16 * h - (1e-6 if uv[3] < uv[1] else -1e-6)).astype(int), 0, h - 1)
        col = tex[iv, iu].astype(float)
        ok = inside & (col[..., 3] >= 128) & (z < depth[y0:y1, x0:x1])
        if not ok.any():
            continue
        sub = img[y0:y1, x0:x1]
        sub[ok, :3] = col[ok, :3] * shade
        sub[ok, 3] = 255
        depth[y0:y1, x0:x1][ok] = z[ok]
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')
    if frame is not None:
        x0, y0, z0, x1, y1, z1 = frame
        corners = np.array([[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], float)
        P = project(corners)
        dr = ImageDraw.Draw(out)
        for i in range(8):
            for j in range(i + 1, 8):
                if bin(i ^ j).count('1') == 1:
                    dr.line([tuple(P[i, :2]), tuple(P[j, :2])], fill=(255, 255, 255, 110), width=1)
    return out


def render_view(js, images, cam, size=168, frame=None, zoom=1.0, center=(8, 8, 8)):
    return rasterise(model_quads(js, images), view_basis(cam), size, size / 30.0 * zoom, frame=frame, center=center)


GUI_DEFAULT = {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}


def render_gui(js, images, k=6):
    """The inventory icon: the model's 'gui' display transform, in a 16 x 16 slot drawn k times enlarged."""
    d = dict(GUI_DEFAULT)
    d.update((js.get('display') or {}).get('gui', {}))
    r = euler_xyz(*d['rotation'])
    right, up, fwd = r.T @ np.array([1.0, 0, 0]), r.T @ np.array([0, 1.0, 0]), r.T @ np.array([0, 0, -1.0])
    sc = d['scale'][0]
    size = 16 * k
    t = d['translation']
    return rasterise(model_quads(js, images), (right, up, fwd), size, k * sc, offset=(t[0] * k, -t[1] * k))


# =====================================================================================================================
# output

WRITTEN = []


def write_bytes(rel, data):
    assert rel not in WRITTEN, ('written twice', rel)
    WRITTEN.append(rel)
    p = os.path.join(ASSETS, rel)
    old = None
    if os.path.exists(p):
        with open(p, 'rb') as f:
            old = f.read()
    if old != data:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'wb') as f:
            f.write(data)


def write_json(rel, obj):
    write_bytes(rel, (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


def png_bytes(img):
    import io
    buf = io.BytesIO()
    Image.fromarray(img, 'RGBA').save(buf, 'PNG')
    return buf.getvalue()


# =====================================================================================================================
# shared painting helpers for round bodies


def roundness(u, u0, d, peak=-0.35):
    """Brightness 0..1 across a cylinder drawn from u0 to u0 + d (light from the left of centre)."""
    t = (u + 0.5 - (u0 + d / 2)) / (d / 2)
    return max(0.0, 1.0 - abs(t - peak) / (1.0 + abs(peak)) * 1.15)


def elevation(img, ramp, rows, u0, d, lo=0, hi=None, peak=-0.35):
    """Shade rows of a cylinder: rows is a list of (texture row, offset added to the ramp index)."""
    hi = len(ramp) - 1 if hi is None else hi
    for v, off in rows:
        for u in range(N):
            b = roundness(u, u0, d, peak)
            i = int(round(lo + b * (hi - lo))) + off
            img[v, u] = rgba(ramp[min(len(ramp) - 1, max(0, i))])
    return img


def heights(model, cells2d_part=None):
    """Top surface height per (x, z) of a model's cells."""
    top = {}
    for (x, y, z) in model.cells:
        top[(x, z)] = max(top.get((x, z), -1), y + 1)
    return top



def fried_rice():
    """Egg fried rice: staggered grains of rice and egg, with deliberate bits of scallion, pepper and pork."""
    img = canvas('#e9cf7a')
    base = ['#fbf1d2', '#f2d978', '#fff8e4', '#e8c35a']
    for v in range(N):
        for u in range(N):
            g = (u + (v % 2)) // 2
            img[v, u] = rgba(base[(g * 3 + v) % 4] if (u + (v % 2)) % 2 == 0 else base[(g + v * 2 + 1) % 4])
    for u, v in ((6, 6), (9, 8), (5, 9), (8, 5), (10, 10), (2, 3), (13, 2), (12, 13), (3, 12), (7, 11), (11, 12),
                 (5, 12), (9, 12), (7, 12)):
        put(img, u, v, '#58ac46')
    for u, v in ((7, 7), (10, 6), (4, 2), (14, 9), (1, 7), (8, 11), (4, 11)):
        put(img, u, v, '#8ad86a')
    for u, v in ((8, 9), (6, 10), (11, 4), (2, 14), (13, 6), (10, 12)):
        put(img, u, v, '#d6402c')
    for u, v in ((6, 7), (9, 10), (12, 11)):
        put(img, u, v, '#8c5430')
        put(img, u + 1, v, '#6e3c20')
    return img


def glow(ramp, edge, core, fill, side_rows=()):
    """A hot surface: a radial glow (core tone at the centre, edge tone at radius 3) on a fill, plus side rows."""
    img = canvas(ramp[fill])
    for v in range(N):
        for u in range(N):
            r = math.hypot(u + 0.5 - 8, v + 0.5 - 8)
            if r < 3.5:
                img[v, u] = rgba(ramp[max(edge, core - int(r))])
    for v, i in side_rows:
        for u in range(N):
            img[v, u] = rgba(ramp[i + (1 if 6 <= u <= 9 else 0)])
    return img


# =====================================================================================================================
# wok: a black round-bottomed carbon-steel bowl (D 10) with a wooden handle, 5 px high
# bowl x 1..11, z 3..13 (centre 6, 8); layers: D4, D6, D8 (solid, its top the floor), ring D10/D6 (terrace), ring D10/D8

WOK_FRAME = Frame(1, 3, 11, 13, 5, us=3, vs=11, ui=3, vi=0, ut=3, vt=3, ub=3, vb=3)


def wok_textures():
    t = {}
    side = canvas(IRON[2])
    rows = {11: '2223467765544322', 12: '2222356543332222', 13: '2222245433222222', 14: '2222223322211222',
            15: '1111112211111111'}                    # rim lip, wall, curve, base: a soft streak of light left of centre
    for v, row in rows.items():
        for u, ch in enumerate(row):
            put(side, u, v, IRON[int(ch)])
    t['wok_side'] = side

    top = canvas(IRON[2])
    m = Model({}, 'x')
    _wok_body(m, m.part('x'))
    h = heights(m)
    cx, cz = 6, 8
    for (x, z), y in h.items():
        if x > 10:
            continue
        u, v = x - 1 + 3, z - 3 + 3
        dx, dz = x + 0.5 - cx, z + 0.5 - cz
        lit = (dx + dz) / 6.0                        # +1 towards the south-east: the inner wall that faces the light
        if y == 5:                                   # rim top: a bright steel ring, brightest on the far (north-west) arc
            r = math.hypot(dx, dz)
            c = IRON[6] if r < 4.3 else IRON[5]
            if dx + dz < -4.5:
                c = IRON[7]
            if dx + dz > 5:
                c = IRON[4]
        elif y == 4:                                 # the slope: shadowed under the north-west rim, lit opposite
            c = ramp_at(PATINA[1:], 0.5 + 0.6 * lit)
        else:                                        # the seasoned floor, a soft glint towards the light
            c = PATINA[3] if lit > 0.15 else PATINA[2]
            if 0.15 < lit < 0.45 and abs(dx - dz) < 1.5:
                c = PATINA[5]
        put(top, u, v, c)
    rect(top, 3, 0, 13, 1, IRON[2])                  # inner walls: rim row, then the slope row (shadowed)
    rect(top, 3, 1, 13, 2, IRON[1])
    t['wok_top'] = top

    bottom = canvas(IRON[1])
    for (x, z) in disc(1, 3, 10):
        r = math.hypot(x + 0.5 - 6, z + 0.5 - 8)
        put(bottom, x - 1 + 3, (13 - z - 1) + 3, IRON[2] if r > 3.2 else IRON[1])
    t['wok_bottom'] = bottom

    wood = canvas(WOOD[3])
    tones = [4, 3, 4, 5, 3, 2, 4, 5, 3, 4, 3, 5, 3, 4, 3, 2]     # one tone a row (grain along the handle)
    for v in range(N):
        rect(wood, 0, v, N, v + 1, WOOD[tones[v]])
    for x, v, n in ((1, 2, 3), (9, 5, 4), (4, 9, 3), (11, 13, 3), (6, 14, 4), (2, 6, 2)):
        rect(wood, x, v, x + n, v + 1, WOOD[2])
    rect(wood, 12, 11, 16, 12, WOOD[5]), rect(wood, 12, 12, 16, 13, WOOD[3])   # south face: lit top, shaded lower
    rect(wood, 0, 11, 5, 12, WOOD[5]), rect(wood, 0, 12, 5, 13, WOOD[3])       # north face
    rect(wood, 12, 7, 16, 8, WOOD[6]), rect(wood, 12, 8, 16, 9, WOOD[4])       # top: a highlight along the edge
    stamp(wood, ['cC', 'Cc'], {'c': WOOD[1], 'C': WOOD[3]}, 7, 11)            # end grain of the butt
    t['wok_handle'] = wood

    t['wok_food'] = fried_rice()
    t['wok_ember'] = glow(EMBER, 5, 7, 4, side_rows=((15, 5), (14, 4)))
    t['wok_hot'] = glow(EMBER, 3, 4, 2, side_rows=((14, 3), (13, 2)))
    return t


def _wok_body(m, iron, bottom=None, hot=None):
    m.fill(bottom if bottom is not None else iron, layer(disc(4, 6, 4), 0, 1))
    m.fill(hot if hot is not None else iron, layer(disc(3, 5, 6), 1, 2))
    m.fill(iron, layer(disc(2, 4, 8), 2, 3))
    m.fill(iron, layer(ring(1, 3, 10, 6), 3, 4))
    m.fill(iron, layer(ring(1, 3, 10, 8), 4, 5))


def wok_model(on):
    tex = {'side': 'wok_side', 'top': 'wok_top', 'bottom': 'wok_bottom', 'handle': 'wok_handle'}
    if on:
        tex.update(food='wok_food', ember='wok_ember', hot='wok_hot')
    m = Model(tex, 'side')
    m.display = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 2.5, 0], 'scale': [0.8, 0.8, 0.8]}}
    iron = m.part({'side': 'side', 'inner': 'top', 'up': 'top', 'down': 'bottom'}, WOK_FRAME)
    if on:
        ember = m.part('ember', WOK_FRAME, emission=15, shade=False)
        hot = m.part('hot', WOK_FRAME, emission=9, shade=False)
        _wok_body(m, iron, ember, hot)
        food = m.part('food', WOK_FRAME)
        m.fill(food, layer(disc(3, 5, 6), 3, 4))
        m.fill(food, layer(disc(4, 6, 4), 4, 5) - {(4, 4, 6), (7, 4, 9)})
    else:
        _wok_body(m, iron)
    socket = m.part({'all': 'side'}, WORLD)
    m.fill(socket, cuboid(11, 3, 7, 12, 5, 9))
    handle = m.part('handle', WORLD)
    m.fill(handle, cuboid(12, 3, 7, 15, 5, 9))
    return m


def digits(img, rows, ramp, x0=0):
    """Paint rows of ramp-index digits: {texture row: 'digits'} ('.' skips a pixel)."""
    for v, row in rows.items():
        for i, ch in enumerate(row):
            if ch != '.':
                put(img, x0 + i, v, ramp[int(ch)])
    return img


def plan(d, x0, z0, fn, img, u0=None, v0=None):
    """Paint fn(dx, dz, cell) over the cells of a disc (texel = cell position unless u0/v0 move it)."""
    for (x, z) in disc(x0, z0, d):
        c = fn(x + 0.5 - (x0 + d / 2), z + 0.5 - (z0 + d / 2), (x, z))
        if c:
            put(img, x if u0 is None else x - x0 + u0, z if v0 is None else z - z0 + v0, c)
    return img


# =====================================================================================================================
# cooking pot: a stainless stock pot (D 10, x 3..13, z 3..13), two side handles, a lid with a black knob; 10 px high.
# Working: the lid is pushed aside and the broth shows.

POT_FRAME = Frame(3, 3, 13, 13, 10, us=3, vs=6, ui=3, vi=-2, ut=3, vt=3, ub=3, vb=3)


def pot_lid_frame(shift):
    return Frame(3 + shift, 3, 13 + shift, 13, 10, us=3, vs=6, ut=3, vt=3, ub=3, vb=3)


def pot_textures():
    t = {}
    side = canvas(STEEL[3])
    digits(side, {
        0: '1111111111111111',                        # inner wall above the broth (shaded)
        1: '2222222222222222',
        7: '3333345554433333',                        # lid edge
        8: '4444456665544444',                        # rolled rim
        9: '3332345543332233',
        10: '3332345543332233',
        11: '3332345443322233',
        12: '3332345443322233',
        13: '3332344443322233',
        14: '2222334433222222',
        15: '1111223322111111',                       # base
    }, STEEL)
    t['cooking_pot_side'] = side

    top = canvas(STEEL[3])

    def lid(dx, dz, c):
        r = math.hypot(dx, dz)
        if r > 3.3:                                   # the lid's rolled edge
            return STEEL[5] if dx + dz < 0 else STEEL[3]
        if 1.6 < r < 2.6 and dx + dz < -1:            # a bright arc of reflection
            return STEEL[6]
        if r <= 1.6:                                  # around the knob
            return STEEL[3]
        return STEEL[4] if dx + dz < 1.5 else STEEL[3]

    def rim(dx, dz, c):
        return STEEL[6] if dx + dz < -2 else (STEEL[5] if dx + dz < 3 else STEEL[4])

    plan(10, 3, 3, rim, top)
    plan(8, 4, 4, lid, top)
    t['cooking_pot_top'] = top

    bottom = canvas(STEEL[2])
    plan(10, 3, 3, lambda dx, dz, c: STEEL[3], bottom, 3, 3)
    plan(8, 4, 4, lambda dx, dz, c: STEEL[2] if math.hypot(dx, dz) > 2.6 else STEEL[1], bottom, 4, 4)
    t['cooking_pot_bottom'] = bottom

    knob = canvas('#22242a')
    stamp(knob, ['ab', 'bc'], {'a': '#5c6270', 'b': '#33363e', 'c': '#16171b'}, 7, 7)   # top: lit corner
    rect(knob, 0, 6, 16, 7, '#3a3d45')                                             # sides (row 6)
    put(knob, 7, 6, '#555a66')
    t['cooking_pot_knob'] = knob

    handle = canvas('#26282e')                       # black phenolic grips
    for v in range(N):
        rect(handle, 0, v, N, v + 1, '#3c3f47' if v % 2 == 0 else '#26282e')
    rect(handle, 0, 6, N, 7, '#4a4e58'), rect(handle, 0, 9, N, 10, '#1a1b20')    # side faces: lit top row, dark lower
    rect(handle, 0, 10, N, 11, '#1a1b20')
    t['cooking_pot_handle'] = handle

    soup = canvas(SOUP[3])

    def broth(dx, dz, c):
        r = math.hypot(dx, dz)
        if r > 3.2:
            return SOUP[2]                            # darker at the wall
        return SOUP[4] if (dx + dz) < -1 else SOUP[3]

    plan(8, 4, 4, broth, soup)
    stamp(soup, [
        '..........',
        '..W.......',
        '.Wg...oO..',
        '.....Gk...',
        '..o.......',
        '...Ok..W..',
        '.g......g.',
        '....W.....',
    ], {'W': '#ffe9c0', 'g': '#5aa83e', 'G': '#86d066', 'o': '#c0451a', 'O': '#ff9a4a', 'k': '#f6c25a'}, 3, 4)
    t['cooking_pot_soup'] = soup
    return t


def pot_model(on):
    tex = {'side': 'cooking_pot_side', 'top': 'cooking_pot_top', 'bottom': 'cooking_pot_bottom',
           'knob': 'cooking_pot_knob', 'handle': 'cooking_pot_handle'}
    if on:
        tex['soup'] = 'cooking_pot_soup'
    m = Model(tex, 'side')
    body = m.part({'side': 'side', 'inner': 'side', 'up': 'top', 'down': 'bottom'}, POT_FRAME)
    m.fill(body, layer(disc(4, 4, 8), 0, 1))
    m.fill(body, layer(disc(3, 3, 10), 1, 8))
    shift = 2 if on else 0
    if on:
        m.carve(layer(disc(4, 4, 8), 7, 8))
        m.fill(m.part('soup', POT_FRAME), layer(disc(4, 4, 8), 6, 7))
    lf = pot_lid_frame(shift)
    lid = m.part({'side': 'side', 'up': 'top', 'down': 'bottom'}, lf)
    m.fill(lid, layer(disc(4 + shift, 4, 8), 8, 9))
    knob = m.part('knob', lf)
    m.fill(knob, cuboid(7 + shift, 9, 7, 9 + shift, 10, 9))
    handle = m.part('handle', WORLD)
    grip = cuboid(0, 6, 6, 1, 7, 10) | cuboid(0, 5, 6, 1, 6, 7) | cuboid(0, 5, 9, 1, 6, 10)   # a bracket: bar on two stubs
    m.fill(handle, {(x + 2, y, z) for x, y, z in grip} | {(x + 13, y, z) for x, y, z in grip})
    return m


# =====================================================================================================================
# bamboo steamer: two round bamboo tiers (D 12, x 2..14) and a woven domed lid; 11 px high. Working: the lid rides up
# on the steam, a band of steam shows under it (the rising steam itself is particles).

STEAM_FRAME = Frame(2, 2, 14, 14, 11, us=2, vs=5, ut=2, vt=2, ub=2, vb=2)


def steamer_textures():
    t = {}
    side = canvas(BAMBOO[3])
    band = {15: 1, 12: 2, 11: 1, 8: 2, 7: 1}          # binding strips: tier feet and rims, the lid's rim
    for v in range(5, 16):
        for u in range(N):
            b = roundness(u, 2, 12)
            if v in band:
                c = BIND[min(3, int(round(b * 2.4)) + band[v] - 1)]
            elif v in (5, 6):                         # woven dome: a low-contrast basket weave
                c = BAMBOO[min(6, int(round(1.5 + b * 3.5)) + (1 if ((u // 2) + v) % 2 == 0 else 0))]
            else:                                     # bent bamboo sheet, a soft shine
                c = BAMBOO[min(6, int(round(1.5 + b * 4.5)))]
            side[v, u] = rgba(c)
    for u in (4, 8, 12):                              # stitches through the rims
        put(side, u, 12, BAMBOO[5]), put(side, u, 8, BAMBOO[5])
    for v in (9, 10, 13, 14):                         # the overlap seam of each bent sheet
        put(side, 10, v, BAMBOO[2])
    t['steamer_side'] = side

    steam = canvas('#e6ebf0')                         # the steam band under the lifted lid: puffs, dark gaps between
    stamp(steam, ['wWwgWWwwgWwWWwgw'], {'w': '#f6f8fa', 'W': '#ffffff', 'g': '#9aa3ad'}, 0, 7)
    t['steamer_steam'] = steam

    for on in (False, True):
        top = canvas(BAMBOO[3])

        def lid(dx, dz, c):
            r = math.hypot(dx, dz)
            x, z = c
            if r > 4.9:
                return BIND[3] if dx + dz < 0 else BIND[2]          # the rim strip
            if r < 1.0:
                return BIND[1]                                       # the knot at the centre
            weave = BAMBOO[4] if ((x // 2) + (z // 2)) % 2 == 0 else BAMBOO[3]
            return mix(weave, BAMBOO[6], 0.3) if (dx + dz < -2 and (x + z) % 2) else weave

        plan(12, 2, 2, lid, top)
        if on:                                        # wisps of steam curling off the lid
            stamp(top, ['ww..', '.wW.', '..w.'], {'w': '#e9eef3', 'W': '#ffffff'}, 4, 4)
            stamp(top, ['.w', 'wW', 'w.'], {'w': '#e9eef3', 'W': '#ffffff'}, 10, 8)
        t['steamer_top_on' if on else 'steamer_top'] = top

    bottom = canvas(BAMBOO[2])
    for v in range(N):
        for u in range(N):
            bottom[v, u] = rgba(BAMBOO[3] if v % 3 != 2 else BIND[1])   # the slatted floor from below
    plan(12, 2, 2, lambda dx, dz, c: BIND[2] if math.hypot(dx, dz) > 5 else None, bottom, 2, 2)
    t['steamer_bottom'] = bottom
    return t


def steamer_model(on):
    sfx = '_on' if on else ''
    tex = {'side': 'steamer_side', 'top': 'steamer_top' + sfx, 'bottom': 'steamer_bottom'}
    if on:
        tex['steam'] = 'steamer_steam'
    m = Model(tex, 'side')
    p = m.part({'side': 'side', 'up': 'top', 'down': 'bottom'}, STEAM_FRAME)
    m.fill(p, layer(disc(2, 2, 12), 0, 8))
    if on:                                            # lid lifted by one pixel on a band of steam
        m.fill(m.part('steam', STEAM_FRAME), layer(disc(2, 2, 12), 8, 9))
        lid = m.part({'side': 'side', 'up': 'top', 'down': 'bottom'},
                     Frame(2, 2, 14, 14, 11, us=2, vs=6, ut=2, vt=2, ub=2, vb=2))
        m.fill(lid, layer(disc(2, 2, 12), 9, 10))
        m.fill(lid, layer(disc(3, 3, 10), 10, 11))
    else:
        m.fill(p, layer(disc(2, 2, 12), 8, 9))
        m.fill(p, layer(disc(3, 3, 10), 9, 10))
        m.fill(p, layer(disc(4, 4, 8), 10, 11))
    return m


# =====================================================================================================================
# fermentation crock: a glazed brown jar (D 10, x 3..13) on a wooden board, a water-seal moat round its mouth and an
# inverted-bowl lid standing in the water; 14 px high. Working: the seal water bubbles.

FERM_FRAME = Frame(3, 3, 13, 13, 14, us=3, vs=2, ut=3, vt=3, ub=3, vb=3)


def fermenter_textures():
    t = {}
    side_row9 = '2222234432221222'
    side = canvas(GLAZE[3])
    digits(side, {
        2: '3333456654333333',                        # lid
        3: '3334567765443333',                        # moat wall
        4: '4445678876554444',                        # flange (a lit lip)
        5: '2222345543222222',                        # neck, in the flange's shadow
        6: '3333457654333333',                        # shoulder
        7: '3333468754333333',                        # belly, the glaze's shine left of centre
        8: '3333568654333333',
        9: '3333457543332333',
        10: '3333456543332233',
        11: '2222345432221122',
        12: '1111234321111111',
        13: '1111123211111111',
    }, GLAZE)
    for u in range(4, 12):                            # an incised line round the belly
        put(side, u, 9, GLAZE[int(side_row9[u])])
    digits(side, {14: '0000123321000000'}, CLAY)      # unglazed foot
    t['fermenter_side'] = side

    top = canvas(GLAZE[3])

    def rim(dx, dz, c):
        return GLAZE[7] if dx + dz < -3 else (GLAZE[6] if dx + dz < 2 else GLAZE[5])

    def lid(dx, dz, c):
        r = math.hypot(dx, dz)
        if r > 2.2:
            return GLAZE[5] if dx + dz < 0 else GLAZE[4]
        if dx + dz < -0.5:
            return GLAZE[8]
        return GLAZE[6]

    plan(10, 3, 3, rim, top)
    plan(6, 5, 5, lid, top)
    t['fermenter_top'] = top

    for on in (False, True):
        water = canvas(WATER[2])
        plan(8, 4, 4, lambda dx, dz, c: WATER[3] if dx + dz < 0 else WATER[2], water)
        for u, v in ((5, 5), (10, 10), (4, 9), (11, 6)):
            put(water, u, v, WATER[4])               # glints
        if on:                                        # fermentation gas bubbling through the seal
            for u, v in ((5, 4), (11, 5), (4, 10), (10, 11), (7, 4), (11, 9)):
                put(water, u, v, WATER[5])
            for u, v in ((6, 4), (4, 6), (11, 10)):
                put(water, u, v, '#f2fbff')
        t['fermenter_water_on' if on else 'fermenter_water'] = water

    wood = canvas(OAK[3])
    for v in range(N):
        rect(wood, 0, v, N, v + 1, OAK[[3, 4, 3, 2][v % 4]])
    for u in range(N):
        put(wood, u, 15, OAK[4] if u % 5 else OAK[1])    # side of the board: lit edge, plank joints
    for x in (5, 11):
        rect(wood, x, 0, x + 1, 15, OAK[1])             # plank joints on top
    t['fermenter_wood'] = wood
    return t


def fermenter_model(on):
    m = Model({'side': 'fermenter_side', 'top': 'fermenter_top', 'water': 'fermenter_water' + ('_on' if on else ''),
               'wood': 'fermenter_wood'}, 'side')
    wood = m.part('wood', Frame(3, 3, 13, 13, 14, us=3, vs=2))
    m.fill(wood, layer(disc(3, 3, 10, [1] + [0] * 8 + [1]), 0, 1))
    crock = m.part({'side': 'side', 'up': 'top', 'down': 'side'}, FERM_FRAME)
    m.fill(crock, layer(disc(5, 5, 6), 1, 2))
    m.fill(crock, layer(disc(4, 4, 8), 2, 3))
    m.fill(crock, layer(disc(3, 3, 10), 3, 9))
    m.fill(crock, layer(disc(4, 4, 8), 9, 10))
    m.fill(crock, layer(disc(5, 5, 6), 10, 11))
    m.fill(crock, layer(disc(3, 3, 10), 11, 12))
    m.fill(crock, layer(ring(3, 3, 10, 8), 12, 13))
    m.fill(m.part('water', FERM_FRAME), layer(ring(4, 4, 8, 6), 12, 13))
    lid = m.part({'side': 'side', 'up': 'top', 'down': 'side'}, FERM_FRAME)
    m.fill(lid, layer(disc(5, 5, 6), 12, 13))
    m.fill(lid, layer(disc(6, 6, 4), 13, 14))
    return m


# =====================================================================================================================
# full-cube machines: oven, grain mill, juicer (orientable: front on the north face, turned by the blockstate)

def face(ramp, rows, pal, rivets=(), base=None):
    """An opaque machine face: a casing (or base), a character grid (digits = the casing ramp), the bevel kept."""
    img = base.copy() if base is not None else casing(ramp, rivets=rivets)
    colours = {str(i): c for i, c in enumerate(ramp)}
    colours.update(pal)
    stamp(img, grid(rows), colours)
    assert img[:, :, 3].min() == 255
    return img


def louvres(ramp, rows=(4, 7, 10), x0=3, x1=13):
    img = casing(ramp)
    for y in rows:
        rect(img, x0, y, x1, y + 1, ramp[0])
        rect(img, x0, y + 1, x1, y + 2, ramp[-2])
    return img


OVEN_PAL = {'k': '#101217', 'K': '#161a21', 'J': '#20262f', 'G': '#4a5464', 'r': '#343a45', 'h': '#eef1f5',
            'H': '#7d8592', 'd': '#2b2e35', 'D': '#5d6370', 'm': '#e0e4ea', 'o': '#0f1612', 'p': '#203328',
            'g': '#2a4a32', 'q': '#5a4428'}
OVEN_HOT = dict(OVEN_PAL, K='#b8461a', J='#e0682a', G='#ffd890', r='#6a2410', p='#ff8a3a', g='#5cf07c', q='#ffb040',
                y='#ffb446', Y='#fff0b0', c='#8a4a1c', C='#d89040', o='#1a0f0a')


def oven_textures():
    t = {}
    front = '''
        5555555555555554
        5333333333333332
        53oppo333Dm3Dm32
        53oooo333dd3dd32
        5333333333333332
        5kkkkkkkkkkkkkk2
        544hhhhhhhhhh442
        544H11111111H442
        544kkkkkkkkkk442
        544kGJKKKKKKk442
        544kJGKKKKKKk442
        544krrrrrrrrk442
        544kKKKKKKKKk442
        544kkkkkkkkkk442
        54g3q333333333q2
        2111111111111110'''
    t['oven_front'] = face(STEEL, front, OVEN_PAL)
    t['oven_front_on'] = face(STEEL, front.replace('544kGJKKKKKKk442', '544kyYYYYYYyk442')
                              .replace('544kJGKKKKKKk442', '544kJGJcCCcJk442')
                              .replace('544krrrrrrrrk442', '544krrcccccrk442')
                              .replace('544kKKKKKKKKk442', '544kJKKKKKKJk442'), OVEN_HOT)
    t['oven_side'] = louvres(STEEL, rows=(3, 6, 9, 12), x0=3, x1=13)
    top = casing(STEEL)
    for y in (3, 5, 7):                               # a grille over the oven cavity
        for x in range(3, 13):
            put(top, x, y, STEEL[0] if x % 2 else STEEL[1])
            put(top, x, y + 1, STEEL[5] if x % 2 else STEEL[4])
    t['oven_top'] = top
    bottom = casing(STEEL, base=2, rivets=())
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rect(bottom, x, y, x + 2, y + 2, '#202228')    # rubber feet
    t['oven_bottom'] = bottom
    return t


STONE = ['#4a4844', '#64625d', '#7e7c76', '#9a9891', '#b6b4ad']
MILL_PAL = {'c': '#eef1f4', 'C': '#a9b0ba', 'x': '#5b626d', 'k': '#2e2a24', 'w': '#b3a98f', 'W': '#5a5244',
            'f': '#f6f2e8', 'g': '#2a4a32', 'q': '#5a4428', 'K': '#1c1f24'}


def millstone(img, phase, dust=False):
    """The grinding stone seen through the porthole: furrows radiating from the eye, turned by phase (radians)."""
    for (x, y) in disc(5, 2, 6):
        dx, dy = x + 0.5 - 8, y + 0.5 - 5
        r = math.hypot(dx, dy)
        a = (math.atan2(dy, dx) + phase) % (math.pi / 2)
        if r < 1.0:
            c = '#24221f'
        elif abs(a - math.pi / 4) < 0.33 and r > 1.2:
            c = STONE[1]
        else:
            c = STONE[3] if dx + dy < -1.2 else (STONE[2] if dx + dy < 1.5 else STONE[1])
        put(img, x, y, c)
    if dust:
        for x, y in ((5, 7), (10, 6), (6, 8), (9, 8), (10, 7)):
            put(img, x, y, '#ece6d8')


def grain_mill_textures():
    t = {}
    rows = '''
        5555555555555554
        544444cccC444442
        54444c....C44442
        5444c......C4442
        5444c......x4442
        5444C......x4442
        5444C......x4442
        54444C....x44442
        544444xxxx444442
        5444444444444442
        5444444kk4444442
        544wwwwwwwwww442
        544wwwWWWWwww442
        544wwwwwwwwww442
        54g4q44444444442
        2111111111111110'''
    off = face(CREAM, rows, MILL_PAL)
    millstone(off, 0.0)
    t['grain_mill_front'] = off
    on = face(CREAM, rows.replace('544wwwwwwwwww442\n        544wwwWWWWwww442',
                                  '544wffwfffwfw442\n        544wwwWWWWwww442'),
              dict(MILL_PAL, g='#5cf07c', q='#ffb040'))
    put(on, 7, 10, '#f6f2e8'), put(on, 8, 10, '#d8d0bc')              # flour pouring from the spout
    millstone(on, math.pi / 4 * 0.5, dust=True)
    t['grain_mill_front_on'] = on
    side = louvres(CREAM, rows=(9, 11, 13), x0=3, x1=13)
    stamp(side, ['xxxxxx', 'xccCCx', 'xxxxxx'], MILL_PAL, 5, 3)     # a small chrome name plate
    t['grain_mill_side'] = side
    top = casing(CREAM)
    hopper = '''
        ................
        ................
        ..xxxxxxxxxxxx..
        ..xccccccccccC..
        ..xcCCCCCCCCxC..
        ..xcCyYyYyyxxC..
        ..xcCYyYyyyxxC..
        ..xcCyYyyyyxxC..
        ..xcCYyyyuyxxC..
        ..xcCyyyuuyxxC..
        ..xcCyyuuuuxxC..
        ..xcCxxxxxxxxC..
        ..xcxxxxxxxxxC..
        ..xCCCCCCCCCCC..
        ................
        ................'''
    stamp(top, grid(hopper), dict(MILL_PAL, y='#d9b24a', Y='#f2d77a', u='#a8822e'))
    t['grain_mill_top'] = top
    t['grain_mill_bottom'] = casing(CREAM, base=2, rivets=())
    return t


JUICER_PAL = {'k': '#2a2f38', 'K': '#1b1f26', 'g': '#9fbccc', 'G': '#e6f3fa', 'q': '#6f8a9a', 'n': '#8d95a2',
              'N': '#4a505a', 't': '#3c424c', 'T': '#8b94a2', 'b': '#2a4a32', 'B': '#5a4428'}
JUICE = {'j': '#ff9a2a', 'J': '#ffc65a', 'i': '#d86a14'}


def juicer_textures():
    t = {}
    rows = '''
        5555555555555554
        5222222222222221
        5222222NN2222221
        5444444nn4444442
        544kkkkkkkkkk442
        544kKKKKKKKKk442
        544kKgGgggKKk442
        544kKGKKKgqqk442
        544kKGKKKgKqk442
        544kKGKKKgKqk442
        544kKgKKKgqqk442
        544kKgKKKgKKk442
        544kKgggggKKk442
        544ktTtTtTtTk442
        54b4B44444444442
        2111111111111110'''
    t['juicer_front'] = face(WHITE, rows, JUICER_PAL)
    full = rows.replace('544kKKKKKKKKk442\n        544kKgGgggKKk442', '544kKKKjKKKKk442\n        544kKgGjggKKk442')
    full = full.replace('544kKGKKKgqqk442\n        544kKGKKKgKqk442\n        544kKGKKKgKqk442\n        544kKgKKKgqqk442\n'
                        '        544kKgKKKgKKk442',
                        '544kKGKjKgqqk442\n        544kKGJJJgKqk442\n        544kKGjjigKqk442\n        544kKgjjigqqk442\n'
                        '        544kKgjiigKKk442')
    t['juicer_front_on'] = face(WHITE, full, dict(JUICER_PAL, b='#5cf07c', B='#ffb040', **JUICE))
    side = louvres(WHITE, rows=(3, 5, 7), x0=3, x1=13)
    rect(side, 1, 11, 15, 12, '#7fb8d8'), rect(side, 1, 12, 15, 13, '#5a94b8')   # a pale blue trim
    t['juicer_side'] = side
    top = casing(WHITE)
    chute = '''
        ................
        ................
        ................
        ................
        ......nnnn......
        ....nnNNNNnn....
        ....nNKKKKNN....
        ...nNKGGGgKNN...
        ...nNKGggqKNN...
        ...nNKGgqqKNN...
        ...nNKgqqqKNN...
        ....nNKKKKNN....
        ....NNNNNNNN....
        ......NNNN......
        ................
        ................'''
    stamp(top, grid(chute), dict(JUICER_PAL, G='#ffffff', g='#e2e7ec', q='#b9c0c9'))
    t['juicer_top'] = top
    t['juicer_bottom'] = casing(WHITE, base=2, rivets=())
    return t


def cube_model(mid, on):
    return {'parent': 'minecraft:block/orientable_with_bottom',
            'textures': {'top': f'{NS}:{TEX}/{mid}_top', 'front': f'{NS}:{TEX}/{mid}_front' + ('_on' if on else ''),
                         'side': f'{NS}:{TEX}/{mid}_side', 'bottom': f'{NS}:{TEX}/{mid}_bottom'}}


# =====================================================================================================================
# induction stove: black glass top with a cooking ring (glowing red when hot), steel sides. Full cube; the top face is
# its own element so that the hot ring can glow in the dark (light_emission).

def circle_cells(cx, cy, r):
    """Pixels of a 1-pixel circle outline of radius r round (cx, cy) (texel centres)."""
    out = set()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if abs(d - r) < 0.5:
                out.add((x, y))
    return out


def stove_top(hot):
    img = canvas(GLASS[2])
    for y in range(1, 15):                            # a soft reflection across the glass from the top left
        for x in range(1, 15):
            k = x + y
            img[y, x] = rgba(GLASS[3] if k < 9 else (GLASS[2] if k < 20 else GLASS[1]))
    for x, y in ((3, 2), (4, 2), (2, 3), (2, 4), (5, 2), (2, 5)):
        put(img, x, y, GLASS[5])                      # the glint on the glass
    outer = circle_cells(8, 7.5, 5.0)
    inner = circle_cells(8, 7.5, 2.0)
    if hot:
        for y in range(N):
            for x in range(N):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 7.5)
                if 2.5 < d < 4.5:
                    put(img, x, y, '#5a0e08' if d > 3.5 else '#7a1609')     # the coil glowing through the glass
                elif d < 1.5:
                    put(img, x, y, '#3a0805')
        for x, y in outer:
            put(img, x, y, EMBER[4])
        for x, y in inner:
            put(img, x, y, EMBER[3])
        for x, y in ((8, 2), (3, 7), (13, 8), (8, 12), (4, 4), (12, 11)):
            if (x, y) in outer:
                put(img, x, y, EMBER[6])               # hottest spots on the ring
    else:
        for x, y in outer | inner:
            put(img, x, y, GLASS[4])
    stamp(img, ['-.+..o'], {'-': GLASS[5], '+': GLASS[5], 'o': '#ff4a3a' if hot else '#4a1a18'}, 5, 14)   # touch keys
    img[0, :] = rgba(STEEL[5])                         # steel frame round the glass
    img[:, 0] = rgba(STEEL[4])
    img[15, :] = rgba(STEEL[2])
    img[:, 15] = rgba(STEEL[2])
    img[0, 0] = rgba(STEEL[6])
    img[15, 15] = rgba(STEEL[1])
    return img


def stove_textures():
    t = {'induction_stove_top': stove_top(False), 'induction_stove_top_on': stove_top(True)}
    side = casing(STEEL, rivets=((1, 3), (14, 3), (1, 13), (14, 13)))
    rect(side, 0, 0, 16, 1, GLASS[1])                  # the edge of the glass top
    put(side, 2, 0, GLASS[4]), put(side, 3, 0, GLASS[3])
    rect(side, 0, 1, 16, 2, STEEL[5])                  # steel lip under the glass
    for x in range(3, 13):                            # air slots
        put(side, x, 10, STEEL[0] if x % 2 else STEEL[2])
        put(side, x, 11, STEEL[5] if x % 2 else STEEL[3])
    rect(side, 0, 15, 16, 16, STEEL[1])
    rect(side, 1, 15, 3, 16, '#202228'), rect(side, 13, 15, 15, 16, '#202228')    # feet
    t['induction_stove_side'] = side
    bottom = casing(STEEL, base=2, rivets=())
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rect(bottom, x, y, x + 2, y + 2, '#202228')
    t['induction_stove_bottom'] = bottom
    return t


def stove_model(on):
    m = Model({'side': 'induction_stove_side', 'bottom': 'induction_stove_bottom',
               'top': 'induction_stove_top' + ('_on' if on else '')}, 'side')
    m.element((0, 0, 0), (16, 16, 16), {d: ('bottom' if d == 'down' else 'side', WORLD, d)
                                        for d in ('down', 'north', 'south', 'west', 'east')})
    m.element((0, 16, 0), (16, 16, 16), {'up': ('top', WORLD, 'up')}, emission=15 if on else 0)
    return m


# =====================================================================================================================
# hydroponic planter: a steel trough, nutrient solution one pixel below a rim with lamp dots (brighter when
# powered), a window in each steel side. The crop stands on the solution, as on farmland (15 px).

def planter_top(lit):
    img = canvas(STEEL[3])
    for y in range(N):
        for x in range(N):
            edge = min(x, y, 15 - x, 15 - y)
            if edge >= 2:                             # the solution, shaded under the north and west rims
                c = NUTRIENT[2] if (x == 2 or y == 2) else NUTRIENT[3]
                if lit and c == NUTRIENT[3]:
                    c = mix(NUTRIENT[3], NUTRIENT[4], 0.35)
                img[y, x] = rgba(c)
            elif edge == 0:
                img[y, x] = rgba(STEEL[5] if (x == 0 or y == 0) else STEEL[2])
            else:
                img[y, x] = rgba(STEEL[4] if (x == 1 or y == 1) else STEEL[3])
    for x, y in ((4, 4), (5, 4), (10, 5), (11, 5), (4, 10), (11, 11), (10, 11), (6, 12)):
        put(img, x, y, NUTRIENT[5])                   # ripples
    stamp(img, ['.xx.', 'xwWx', 'xWwx', '.xx.'], {'x': DSTEEL[1], 'w': '#cdbb8e', 'W': '#e6d8b0'}, 6, 6)   # net cup
    lamp = ('#ffe6fa', '#e27ccc') if lit else ('#6a4a72', '#3a2c40')
    for i in (3, 6, 9, 12):                           # lamp dots along the inner edge of the rim
        for x, y in ((i, 1), (1, i), (i, 14), (14, i)):
            put(img, x, y, lamp[0])
        if lit:
            for x, y in ((i, 0), (0, i), (i, 15), (15, i)):
                put(img, x, y, lamp[1])
    return img


def planter_textures():
    t = {'hydroponic_planter_top': planter_top(False), 'hydroponic_planter_top_on': planter_top(True)}
    side = casing(STEEL, rivets=((1, 2), (14, 2), (1, 13), (14, 13)))
    rect(side, 0, 0, 16, 1, STEEL[5])                  # the rim's outer face (y 15..16)
    rect(side, 0, 1, 16, 2, STEEL[2])
    window = '''
        ................
        ................
        ................
        ...kkkkkkkkkk...
        ...kGgggggggk...
        ...kgggggggGk...
        ...kSSSSSSSSk...
        ...knnwnnnwnk...
        ...knwnnwnwnk...
        ...knwnnwnnnk...
        ...knnwnwnwbk...
        ...knnnnwnnnk...
        ...kkkkkkkkkk...
        ................
        ................
        ................'''
    stamp(side, grid(window), {'k': DSTEEL[0], 'G': GLASS[5], 'g': GLASS[3], 'S': NUTRIENT[5], 'n': NUTRIENT[3],
                               'w': '#e8e0c8', 'b': NUTRIENT[6]})
    t['hydroponic_planter_side'] = side
    rim = canvas(STEEL[2])
    rect(rim, 0, 0, 16, 1, STEEL[2])
    t['hydroponic_planter_rim'] = rim
    bottom = casing(STEEL, base=2, rivets=())
    t['hydroponic_planter_bottom'] = bottom
    return t


def planter_model(on):
    m = Model({'side': 'hydroponic_planter_side', 'top': 'hydroponic_planter_top' + ('_on' if on else ''),
               'rim': 'hydroponic_planter_rim', 'bottom': 'hydroponic_planter_bottom'}, 'side')
    body = m.part({'side': 'side', 'up': 'top', 'down': 'bottom'}, WORLD)
    m.fill(body, cuboid(0, 0, 0, 16, 15, 16))
    rim = m.part({'side': 'side', 'inner': 'rim', 'up': 'top', 'down': 'bottom'}, WORLD)
    m.fill(rim, cuboid(0, 15, 0, 16, 16, 16) - cuboid(2, 15, 2, 14, 16, 14))
    return m


# =====================================================================================================================
# assembly

TEXTURES = {'wok': wok_textures, 'cooking_pot': pot_textures, 'steamer': steamer_textures,
            'fermenter': fermenter_textures, 'oven': oven_textures, 'grain_mill': grain_mill_textures,
            'juicer': juicer_textures, 'induction_stove': stove_textures, 'hydroponic_planter': planter_textures}
ELEMENT_MODELS = {'wok': wok_model, 'cooking_pot': pot_model, 'steamer': steamer_model, 'fermenter': fermenter_model,
                  'induction_stove': stove_model, 'hydroponic_planter': planter_model}


def models_of(bid):
    """{'<id>': json, '<id>_on': json}"""
    if bid in ELEMENT_MODELS:
        return {bid: ELEMENT_MODELS[bid](False).json(), bid + '_on': ELEMENT_MODELS[bid](True).json()}
    return {bid: cube_model(bid, False), bid + '_on': cube_model(bid, True)}


def blockstate(bid):
    if bid in MACHINES:
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for lit in ('false', 'true'):
                v = {'model': f'{NS}:block/{bid}' + ('_on' if lit == 'true' else '')}
                if y:
                    v['y'] = y
                variants[f'facing={facing},lit={lit}'] = v
        return {'variants': variants}
    return {'variants': {'lit=false': {'model': f'{NS}:block/{bid}'}, 'lit=true': {'model': f'{NS}:block/{bid}_on'}}}


def texture_refs(js):
    return {v.split(':', 1)[1] for v in js.get('textures', {}).values() if not v.startswith('#')}


def element_bounds(js):
    """Min and max corner over every element (rotations applied)."""
    pts = []
    for e in js.get('elements', []):
        lo, hi = e['from'], e['to']
        c = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])], float)
        r = e.get('rotation')
        if r:
            o = np.array(r['origin'], float)
            c = (c - o) @ rot_matrix(r['axis'], r['angle']).T + o
        pts.append(c)
    a = np.concatenate(pts)
    return a.min(axis=0), a.max(axis=0)


def build():
    """Everything in memory: {id: (textures, models)}; validated."""
    assert set(TEXTURES) == set(MACHINES) | set(BLOCKS), (sorted(TEXTURES), MACHINES, BLOCKS)
    out = {}
    names = set()
    for bid in MACHINES + BLOCKS:
        tex = TEXTURES[bid]()
        for name, img in tex.items():
            assert name.startswith(bid + '_'), name
            assert name not in names, name
            names.add(name)
            assert img.shape == (N, N, 4) and img.dtype == np.uint8, (name, img.shape)
            if bid not in LOW and bid != 'hydroponic_planter':
                assert img[:, :, 3].min() == 255, ('solid-layer block with see-through pixels', name)
        models = models_of(bid)
        used = set()
        for mname, js in models.items():
            refs = texture_refs(js)
            missing = {r for r in refs if r[len(TEX) + 1:] not in tex}
            assert not missing, (mname, missing)
            used |= {r[len(TEX) + 1:] for r in refs}
            lo, hi = element_bounds(js) if 'elements' in js else (np.zeros(3), np.full(3, 16.0))
            box = SHAPES.get(bid, (0, 0, 0, 16, 16, 16))
            assert (lo >= np.array(box[:3]) - 1e-6).all() and (hi <= np.array(box[3:]) + 1e-6).all(), \
                (mname, 'outside its shape', lo, hi, box)
            if bid in SHAPES:                         # stands on the floor, as tall as its outline, as wide one way
                assert abs(lo[1]) < 1e-6 and abs(hi[1] - box[4]) < 1e-6, (mname, 'height differs', lo, hi, box)
                assert any(abs(lo[i] - box[i]) < 1e-6 and abs(hi[i] - box[i + 3]) < 1e-6 for i in (0, 2)), \
                    (mname, 'narrower than its outline both ways', lo, hi, box)
        unused = set(tex) - used
        assert not unused, (bid, 'textures no model uses', unused)
        out[bid] = (tex, models)
    return out


def write_all(data):
    for bid, (tex, models) in data.items():
        for name, img in tex.items():
            write_bytes(f'textures/{TEX}/{name}.png', png_bytes(img))
        for mname, js in models.items():
            write_json(f'models/block/{mname}.json', js)
        write_json(f'blockstates/{bid}.json', blockstate(bid))
        write_json(f'items/{bid}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})


def stale():
    """Files in textures/block/cuisine that this run did not write."""
    d = os.path.join(ASSETS, 'textures', TEX)
    mine = {os.path.basename(p) for p in WRITTEN if p.startswith(f'textures/{TEX}/')}
    return sorted(f for f in os.listdir(d) if f not in mine) if os.path.isdir(d) else []


# =====================================================================================================================
# review sheet

LOOK = {m[0]: m[-1] for m in SPEC.MACHINES}
LOOK.update({b[0]: b[3] for b in SPEC.BLOCKS})


def sheet(data, path):
    """Textures x6 and, per block, views of the off and working models (two sides, low, from below, inventory)."""
    views = [('NW', (-1, 0.8, -1)), ('SE', (1, 0.8, 1)), ('low', (-1, 0.25, -0.5)), ('below', (-0.7, -0.9, -1))]
    R, T = 176, 96
    rows = []
    for bid, (tex, models) in data.items():
        images = {f'{TEX}/{k}': v for k, v in tex.items()}
        zoom = 1.0
        cy = 8
        if bid in SHAPES:
            box = SHAPES[bid]
            zoom = 1.25 if box[4] <= 11 else 1.1
            cy = box[4] / 2 + 1
        tiles = []
        for mname in (bid, bid + '_on'):
            js = models[mname]
            for label, cam in (views if bid in SHAPES else views[:2]):
                im = render_view(js, images, cam, size=R, frame=SHAPES.get(bid), zoom=zoom, center=(8, cy, 8))
                tiles.append((f'{mname} {label}', im))
            g = render_gui(js, images, k=6)
            bg = Image.new('RGBA', (R, R), (139, 139, 139, 255))
            bg.alpha_composite(g, ((R - g.width) // 2, (R - g.height) // 2))
            tiles.append((f'{mname} item', bg))
        rows.append((bid, tex, tiles))
    width = max(len(t) for _, _, t in rows) * (R + 6) + 12
    width = max(width, 12 * (T + 10) + 12)
    heights = []
    for bid, tex, tiles in rows:
        nt = len(tex)
        trows = (nt * (T + 10) + width - 1) // (width - 12)
        heights.append(28 + R + 20 + trows * (T + 22) + 10)
    img = Image.new('RGBA', (width, sum(heights) + 10), (52, 55, 63, 255))
    dr = ImageDraw.Draw(img)
    y = 6
    for (bid, tex, tiles), h in zip(rows, heights):
        dr.text((10, y + 4), f'{bid}  -  {LOOK[bid][:150]}', fill=(240, 240, 240, 255))
        x = 6
        for label, tile in tiles:
            img.alpha_composite(tile, (x, y + 24))
            dr.text((x + 3, y + 24 + R + 2), label, fill=(200, 205, 215, 255))
            x += R + 6
        ty = y + 24 + R + 20
        x = 10
        for name, t in tex.items():
            if x + T > width - 6:
                x = 10
                ty += T + 22
            big = Image.fromarray(t, 'RGBA').resize((T, T), Image.NEAREST)
            checker = Image.new('RGBA', (T, T), (90, 94, 102, 255))
            checker.alpha_composite(big)
            img.alpha_composite(checker, (x, ty))
            dr.text((x, ty + T + 2), name[len(bid) + 1:], fill=(200, 205, 215, 255))
            x += T + 10
        y += h
        dr.line([(0, y - 4), (width, y - 4)], fill=(80, 84, 94, 255))
    img.save(path)


def main():
    args = sys.argv[1:]
    sheet_path = None
    if '--sheet' in args:
        sheet_path = args[args.index('--sheet') + 1]
    data = build()
    write_all(data)
    n_tex = sum(len(t) for t, _ in data.values())
    n_el = {bid: [len(js.get('elements', [])) for js in m.values()] for bid, (t, m) in data.items() if bid in ELEMENT_MODELS}
    print(f'{len(data)} blocks: {n_tex} textures, {2 * len(data)} block models, {len(data)} blockstates, '
          f'{len(data)} item definitions under {os.path.relpath(ASSETS, ROOT)}')
    print('  elements (off, on): ' + ', '.join(f'{k} {v[0]}/{v[1]}' for k, v in n_el.items()))
    for f in stale():
        print(f'  stale (not written by this run, left alone): textures/{TEX}/{f}')
    if sheet_path:
        sheet(data, sheet_path)
        print(f'  review sheet: {sheet_path}')


if __name__ == '__main__':
    main()
