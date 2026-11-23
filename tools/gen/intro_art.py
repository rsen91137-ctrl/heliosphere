#!/usr/bin/env python3
"""Art for the opening cinematic (overlay 11-guide, client IntroScreen): layered sprites that the screen moves,
rotates, scales and tints in real time instead of the old five-picture slideshow.

Writes textures/gui/intro2/*.png (the asset contract with IntroScreen) and replaces the five 1920x1080 slideshow
pictures in textures/gui/intro/ with 1x1 transparent PNGs so the merged jar loses 13 MB.

Everything is drawn here: seeded fractal value noise, signed-distance and polygon shapes rendered at 2-4x and
box-filtered down, gaussian glows, lighting in linear light. Only the Earth and the Moon take their continents,
clouds, city lights and maria from the pack's own released maps (base/01-heliosphere-core, fetch it with
tools/fetch_base.sh) so they read as the real Earth and Moon at a glance; lighting, oceans, atmosphere, lava and
smoke are computed. The output is deterministic: two runs give byte-identical files.

Anchors for the screen (fractions of the texture size, x right, y down):
  earth / earth_burning: disc radius 0.905 of the half size (blue or fiery halo to 0.98), lit from the left; the
    impact basin of earth_burning (and the matching spot on earth) at (0.50, 0.435). moon 0.94, mars 0.90.
  coma: head centre (0.066, 0.5), tail to the right. rocket: nozzle exit centre (0.5, 0.973), 0.26 wide;
    flame: drawn at the rocket's scale with its top centre on the nozzle exit (top edge 0.25 wide).
  base: launch mount top (0.757, 0.859); turret muzzles (0.094, 0.489) (0.310, 0.512) (0.637, 0.473)
    (0.906, 0.496); SAM launcher tubes (0.848, 0.634). robots, base, stars, nebula tile left-right.
  shockwave: ring at 0.84 of the half size.

Usage (from anywhere): python3 tools/gen/intro_art.py [name ...]   (names: stars nebula earth ... logo old; default all)
"""
import glob
import io
import math
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GUI = os.path.join(ROOT, 'jars', '11-guide', 'src', 'main', 'resources', 'assets', 'heliosphere', 'textures', 'gui')
OUT = os.path.join(GUI, 'intro2')
OLD = os.path.join(GUI, 'intro')
OLD_NAMES = ('intro_earth', 'intro_sun', 'intro_proxima', 'intro_asteroid', 'intro_title')
TAU = 2.0 * math.pi


# ============================================================================ small maths
def hx(s):
    return np.array([int(s[i:i + 2], 16) / 255.0 for i in (1, 3, 5)])


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def ramp(t, stops):
    """Maps a scalar field to colours: stops = [(position, '#rrggbb'), ...] in increasing position."""
    pos = [p for p, _ in stops]
    cols = np.array([hx(c) if isinstance(c, str) else c for _, c in stops], dtype=np.float64)
    return np.stack([np.interp(t, pos, cols[:, i]) for i in range(3)], axis=-1)


def lin(c):
    return np.power(np.clip(c, 0.0, None), 2.2)


def srgb(c):
    return np.power(np.clip(c, 0.0, 1.0), 1.0 / 2.2)


def norm3(x, y, z):
    k = 1.0 / math.sqrt(x * x + y * y + z * z)
    return x * k, y * k, z * k


def stretch(a, lo_pct=1.0, hi_pct=99.0):
    lo, hi = np.percentile(a, [lo_pct, hi_pct])
    return np.clip((a - lo) / (hi - lo + 1e-12), 0.0, 1.0)


# ============================================================================ blur, resampling, layers
def _box_radii(sigma, n=3):
    w_ideal = math.sqrt(12.0 * sigma * sigma / n + 1.0)
    wl = int(w_ideal)
    if wl % 2 == 0:
        wl -= 1
    wu = wl + 2
    m = round((12.0 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4.0 * wl - 4.0))
    return [(wl if i < m else wu) // 2 for i in range(n)]


def _box(a, r, axis, wrap):
    if r < 1:
        return a
    n = a.shape[axis]
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode='wrap' if wrap else 'constant'), axis=axis)
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return (hi - lo) / (2 * r + 1)


def blur(a, sigma, wrap_x=False, wrap_y=False):
    """Gaussian blur (three box passes) over the first two axes; zero outside the image unless wrapped."""
    a = np.asarray(a, dtype=np.float64)
    if sigma < 0.5:
        return a
    for r in _box_radii(sigma):
        a = _box(a, r, 1, wrap_x)
        a = _box(a, r, 0, wrap_y)
    return a


def down(a, k):
    """Box-filter downsampling by an integer factor (use on premultiplied colour)."""
    if k == 1:
        return a
    h, w = a.shape[:2]
    return a.reshape(h // k, k, w // k, k, *a.shape[2:]).mean(axis=(1, 3))


def layer(rgb, a):
    """Straight colour + alpha -> premultiplied RGBA (H, W, 4)."""
    a = np.clip(a, 0.0, 1.0)
    rgb = np.broadcast_to(np.asarray(rgb, dtype=np.float64), a.shape + (3,))
    return np.concatenate([rgb * a[..., None], a[..., None]], axis=-1)


def over(dst, src):
    return src + dst * (1.0 - src[..., 3:4])


def light(dst, rgb, g):
    """Adds glowing light of colour rgb and strength g to a premultiplied layer (spills onto transparent pixels)."""
    g = np.clip(g, 0.0, None)
    out = dst.copy()
    out[..., :3] += np.asarray(rgb, dtype=np.float64) * g[..., None]
    out[..., 3] = np.clip(out[..., 3] + np.clip(g, 0, 1) * (1.0 - out[..., 3]), 0.0, 1.0)
    out[..., :3] = np.minimum(out[..., :3], out[..., 3:4])
    return out


def quant(x, dither, rng):
    """8-bit quantisation, optionally with triangular dither (never on exact black/white, so transparent borders
    stay exactly 0)."""
    v = np.clip(x, 0.0, 1.0) * 255.0
    if dither:
        noise = (rng.random(v.shape) - rng.random(v.shape)) * dither
        v = v + noise * ((v > 0.5) & (v < 254.5))
    return np.clip(np.floor(v + 0.5), 0, 255).astype(np.uint8)


def save(name, prem=None, rgb=None, dither=0.0, seed=1, adaptive=False):
    """Writes a premultiplied RGBA layer as a straight-alpha PNG (or an opaque RGB one). Fully transparent pixels
    take the colour of their surroundings so linear filtering never shows dark fringes. adaptive: store the
    colour of faint pixels with coarser steps (the blended error stays below half a level) for a smaller file."""
    rng = np.random.default_rng(seed)
    path = os.path.join(OUT, name)
    if rgb is not None:
        Image.fromarray(quant(rgb, dither, rng)).save(path, optimize=True)
        return
    a = np.clip(prem[..., 3], 0.0, 1.0)
    col = prem[..., :3] / np.maximum(a, 1e-9)[..., None]
    wa = blur(a, 3.0)
    fill = blur(prem[..., :3], 3.0) / np.maximum(wa, 1e-9)[..., None]
    fill[wa < 1e-4] = 0.0
    col = np.where((a < 0.5 / 255.0)[..., None], fill, col)
    qc = quant(col, dither, rng)
    if adaptive:
        step = np.where(a >= 0.5, 1, np.where(a >= 0.25, 2, np.where(a >= 0.125, 4, 8)))[..., None]
        qc = np.clip((qc.astype(np.int64) + step // 2) // step * step, 0, 255).astype(np.uint8)
    q = np.concatenate([qc, quant(a[..., None], dither, rng)], axis=-1)
    Image.fromarray(q).save(path, optimize=True)


# ============================================================================ noise
def fade(t):
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)


def vnoise2(x, y, px, py, seed):
    """Value noise on a px*py lattice that wraps both ways; x, y in lattice units."""
    g = np.random.default_rng(seed).random((py, px))
    xi = np.floor(x).astype(np.int64)
    yi = np.floor(y).astype(np.int64)
    fx = fade(x - xi)
    fy = fade(y - yi)
    x0, x1 = xi % px, (xi + 1) % px
    y0, y1 = yi % py, (yi + 1) % py
    a = g[y0, x0] + (g[y0, x1] - g[y0, x0]) * fx
    b = g[y1, x0] + (g[y1, x1] - g[y1, x0]) * fx
    return a + (b - a) * fy


def fbm2(u, v, cx, cy, octaves, seed, gain=0.5):
    """Fractal value noise of u, v that tiles with period 1 in both; cx*cy cells in the first octave. ~[0, 1]."""
    u, v = np.broadcast_arrays(np.asarray(u, dtype=np.float64), np.asarray(v, dtype=np.float64))
    rng = np.random.default_rng(seed + 1000)
    tot = np.zeros(u.shape)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        f = 2 ** o
        ox, oy = rng.random(2)
        tot += amp * vnoise2(u * cx * f + ox * cx * f, v * cy * f + oy * cy * f, cx * f, cy * f, seed * 97 + o)
        norm += amp
        amp *= gain
    return tot / norm


class Noise3:
    """3-D value noise on a hashed 256^3 lattice."""

    def __init__(self, seed):
        rng = np.random.default_rng(seed)
        self.perm = np.concatenate([rng.permutation(256)] * 2)
        self.val = rng.random(256)

    def __call__(self, x, y, z):
        xi, yi, zi = (np.floor(c).astype(np.int64) for c in (x, y, z))
        fx, fy, fz = fade(x - xi), fade(y - yi), fade(z - zi)
        xi &= 255
        yi &= 255
        zi &= 255
        P, V = self.perm, self.val

        def h(i, j, k):
            return V[P[P[P[xi + i] + yi + j] + zi + k]]

        x00 = lerp(h(0, 0, 0), h(1, 0, 0), fx)
        x10 = lerp(h(0, 1, 0), h(1, 1, 0), fx)
        x01 = lerp(h(0, 0, 1), h(1, 0, 1), fx)
        x11 = lerp(h(0, 1, 1), h(1, 1, 1), fx)
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz)


def _rotations(seed, n):
    rng = np.random.default_rng(seed)
    out = []
    for _ in range(n):
        q = rng.normal(size=4)
        a, b, c, d = q / np.linalg.norm(q)
        out.append(np.array([[a * a + b * b - c * c - d * d, 2 * (b * c - a * d), 2 * (b * d + a * c)],
                             [2 * (b * c + a * d), a * a - b * b + c * c - d * d, 2 * (c * d - a * b)],
                             [2 * (b * d - a * c), 2 * (c * d + a * b), a * a - b * b - c * c + d * d]]))
    return out


def fbm3(x, y, z, freq, octaves, seed, gain=0.5):
    """Fractal 3-D value noise, every octave turned by its own rotation so no lattice axis shows. ~[0, 1]."""
    noise = Noise3(seed)
    rots = _rotations(seed + 7, octaves)
    tot = 0.0
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        R = rots[o]
        f = freq * 2.0 ** o
        X = (R[0, 0] * x + R[0, 1] * y + R[0, 2] * z) * f + 31.7 * o
        Y = (R[1, 0] * x + R[1, 1] * y + R[1, 2] * z) * f + 17.1 * o
        Z = (R[2, 0] * x + R[2, 1] * y + R[2, 2] * z) * f + 47.3 * o
        tot = tot + amp * noise(X, Y, Z)
        norm += amp
        amp *= gain
    return tot / norm


# ============================================================================ spheres
def sphere_grid(n, radius):
    """Pixel grid of an n*n render with a sphere of the given pixel radius in the middle. Returns image-plane
    coordinates (u right, v up, in radii), distance from the centre, analytic edge coverage and the view-space
    normal (x right, y up, z towards the viewer), clamped onto the disc for the edge pixels."""
    c = n / 2.0
    ys, xs = np.mgrid[0:n, 0:n]
    u = (xs + 0.5 - c) / radius
    v = (c - ys - 0.5) / radius
    rho = np.hypot(u, v)
    cover = np.clip((1.0 - rho) * radius + 0.5, 0.0, 1.0)
    k = np.minimum(1.0, 0.99999 / np.maximum(rho, 1e-9))
    nx, ny = u * k, v * k
    nz = np.sqrt(np.clip(1.0 - nx * nx - ny * ny, 0.0, 1.0))
    return u, v, rho, cover, nx, ny, nz


def to_planet(nx, ny, nz, lat0, lon0, roll):
    """View-space directions -> planet frame (y north, lon 0 towards +z) for a view centred on lat0/lon0 (deg),
    the image rolled by roll (deg)."""
    r, a, b = math.radians(roll), -math.radians(lat0), math.radians(lon0)
    x1 = math.cos(r) * nx - math.sin(r) * ny
    y1 = math.sin(r) * nx + math.cos(r) * ny
    y2 = y1 * math.cos(a) - nz * math.sin(a)
    z2 = y1 * math.sin(a) + nz * math.cos(a)
    x3 = x1 * math.cos(b) + z2 * math.sin(b)
    z3 = -x1 * math.sin(b) + z2 * math.cos(b)
    return x3, y2, z3


def latlon(x, y, z):
    return np.arcsin(np.clip(y, -1.0, 1.0)), np.arctan2(x, z)


def sample_map(img, lat, lon):
    """Bilinear lookup in an equirectangular map (H, W, C), lon 0 in the middle, wrapping east-west."""
    H, W = img.shape[:2]
    x = (lon / TAU + 0.5) * W - 0.5
    y = (0.5 - lat / math.pi) * H - 0.5
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    fx = (x - x0)[..., None]
    fy = (y - y0)[..., None]
    x1, x0 = (x0 + 1) % W, x0 % W
    y1, y0 = np.clip(y0 + 1, 0, H - 1), np.clip(y0, 0, H - 1)
    a = img[y0, x0] * (1 - fx) + img[y0, x1] * fx
    b = img[y1, x0] * (1 - fx) + img[y1, x1] * fx
    return a * (1 - fy) + b * fy


def released_map(name):
    jars = sorted(glob.glob(os.path.join(ROOT, 'base', '01-heliosphere-core-*.jar')))
    if not jars:
        sys.exit('base/01-heliosphere-core-*.jar is missing: run tools/fetch_base.sh first')
    with zipfile.ZipFile(jars[0]) as z:
        data = z.read('assets/heliosphere/textures/environment/' + name)
    return np.asarray(Image.open(io.BytesIO(data)).convert('RGB'), dtype=np.float64) / 255.0


# ============================================================================ supersampled silhouettes
class Ink:
    """Shapes in 1x pixel coordinates rasterised at ss times the size into named masks (optionally wrapping
    left-right so the strip tiles)."""

    def __init__(self, w, h, ss=4, wrap=False):
        self.w, self.h, self.ss, self.wrap = w, h, ss, wrap
        self.im = {}

    def _draw(self, layer):
        if layer not in self.im:
            self.im[layer] = Image.new('L', (self.w * self.ss, self.h * self.ss), 0)
        return ImageDraw.Draw(self.im[layer])

    def _shifts(self):
        return (-self.w, 0, self.w) if self.wrap else (0,)

    def poly(self, pts, layer='body', fill=255):
        d, s = self._draw(layer), self.ss
        for o in self._shifts():
            d.polygon([((x + o) * s, y * s) for x, y in pts], fill=fill)

    def ellipse(self, cx, cy, rx, ry, layer='body', fill=255):
        d, s = self._draw(layer), self.ss
        for o in self._shifts():
            d.ellipse([(cx - rx + o) * s, (cy - ry) * s, (cx + rx + o) * s, (cy + ry) * s], fill=fill)

    def rect(self, x0, y0, x1, y1, layer='body', fill=255):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], layer, fill)

    def seg(self, a, b, w0, w1=None, layer='body', fill=255, caps=True):
        """Tapered limb from a to b with round ends."""
        w1 = w0 if w1 is None else w1
        (ax, ay), (bx, by) = a, b
        L = math.hypot(bx - ax, by - ay) or 1e-6
        nx, ny = -(by - ay) / L, (bx - ax) / L
        self.poly([(ax + nx * w0 / 2, ay + ny * w0 / 2), (bx + nx * w1 / 2, by + ny * w1 / 2),
                   (bx - nx * w1 / 2, by - ny * w1 / 2), (ax - nx * w0 / 2, ay - ny * w0 / 2)], layer, fill)
        if caps:
            self.ellipse(ax, ay, w0 / 2, w0 / 2, layer, fill)
            self.ellipse(bx, by, w1 / 2, w1 / 2, layer, fill)

    def mask(self, layer):
        if layer not in self.im:
            return np.zeros((self.h, self.w))
        return down(np.asarray(self.im[layer], dtype=np.float64) / 255.0, self.ss)


class Pose:
    """Local frame for drawing one figure: dx to the right (mirrored when flip=-1), dy up from the ground line."""

    def __init__(self, ink, x, gy, s=1.0, flip=1):
        self.ink, self.x, self.gy, self.s, self.f = ink, x, gy, s, flip

    def P(self, dx, dy):
        return self.x + dx * self.s * self.f, self.gy - dy * self.s

    def poly(self, pts, layer='body', fill=255):
        self.ink.poly([self.P(*p) for p in pts], layer, fill)

    def rect(self, x0, y0, x1, y1, layer='body', fill=255):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], layer, fill)

    def ell(self, cx, cy, rx, ry, layer='body', fill=255):
        X, Y = self.P(cx, cy)
        self.ink.ellipse(X, Y, rx * self.s, ry * self.s, layer, fill)

    def seg(self, a, b, w0, w1=None, layer='body', fill=255):
        self.ink.seg(self.P(*a), self.P(*b), w0 * self.s, (w0 if w1 is None else w1) * self.s, layer, fill)

    def eye(self, cx, cy, r):
        self.ell(cx, cy, r, r, 'eye')


def rim_light(body, dy=1.5, sigma=1.0, wrap_x=False):
    """How much empty sky lies just above each pixel of a silhouette: a backlit rim along its top edges."""
    empty = 1.0 - body
    k = int(math.ceil(dy))
    shifted = np.concatenate([np.ones((k, body.shape[1])), empty[:-k]], axis=0)
    return body * np.clip(blur(shifted, sigma, wrap_x=wrap_x) * 1.6, 0.0, 1.0)


def glow_of(mask, sigmas_weights, wrap_x=False):
    return sum(w * blur(mask, s, wrap_x=wrap_x) for s, w in sigmas_weights)


# ============================================================================ 1. stars
def make_stars():
    W, H = 1024, 512
    rng = np.random.default_rng(2097)
    img = np.zeros((H, W, 3))

    def splat(x, y, col, peak, sigma):
        r = int(math.ceil(sigma * 3.5))
        xi, yi = int(math.floor(x)), int(math.floor(y))
        xs = np.arange(xi - r, xi + r + 2)
        ys = np.arange(yi - r, yi + r + 2)
        k = np.exp(-((ys + 0.5 - y) ** 2) / (2 * sigma * sigma))[:, None] * \
            np.exp(-((xs + 0.5 - x) ** 2) / (2 * sigma * sigma))[None, :]
        k *= TAU * sigma * sigma / k.sum()            # same flux wherever the star sits inside its pixel
        img[np.ix_(ys % H, xs % W)] += k[..., None] * (np.asarray(col) * peak)

    def spike(x, y, col, peak, length, horizontal):
        r = int(length) + 2
        t = np.arange(-r, r + 1)
        across = np.arange(-1, 2)
        if horizontal:
            xs, ys = int(math.floor(x)) + t, int(math.floor(y)) + across
            fa = np.exp(-((ys + 0.5 - y) ** 2) / (2 * 0.45 ** 2))[:, None]
            fl = np.clip(1 - np.abs(xs + 0.5 - x) / length, 0, 1)[None, :] ** 2
        else:
            ys, xs = int(math.floor(y)) + t, int(math.floor(x)) + across
            fa = np.exp(-((xs + 0.5 - x) ** 2) / (2 * 0.45 ** 2))[None, :]
            fl = np.clip(1 - np.abs(ys + 0.5 - y) / length, 0, 1)[:, None] ** 2
        img[np.ix_(ys % H, xs % W)] += (fa * fl)[..., None] * (np.asarray(col) * peak)

    tints = [((1.00, 1.00, 1.00), 0.40), ((0.80, 0.88, 1.00), 0.24), ((1.00, 0.95, 0.84), 0.20),
             ((1.00, 0.80, 0.58), 0.08), ((1.00, 0.62, 0.50), 0.04), ((0.62, 0.74, 1.00), 0.04)]
    tcols = np.array([c for c, _ in tints])
    tw = np.array([w for _, w in tints])
    # clumpy density: more stars in some regions, as in a real sky
    dens = fbm2(np.linspace(0, 1, 128, endpoint=False)[None, :], np.linspace(0, 1, 64, endpoint=False)[:, None],
                3, 2, 4, 2098)
    dens = stretch(dens)
    stars = []
    while len(stars) < 4200:
        x, y = rng.random() * W, rng.random() * H
        if rng.random() < 0.35 + 0.65 * dens[int(y / H * 64), int(x / W * 128)]:
            stars.append((x, y))
    for x, y in stars:
        b = min(1.5, 0.05 * (1.0 - rng.random()) ** -0.85)
        c = tcols[rng.choice(len(tw), p=tw)]
        c = lerp(np.ones(3), c, 0.55 + 0.45 * rng.random())
        if b < 0.25:
            c = lerp(np.ones(3), c, 0.5)
        sigma = 0.48 + 0.2 * min(b, 1.0)
        splat(x, y, c, b, sigma)
        if b > 0.8:
            splat(x, y, c, 0.035 * b, 1.8 + b)
    # a handful of bright stars with a soft halo and faint diffraction spikes, some of them vividly coloured
    for col in ('#ffffff', '#ffb46a', '#ff7a5a', '#dfe8ff', '#8fb4ff', '#fff2d8', '#b8d4ff', '#ffd27a', '#9ad0ff'):
        x, y = rng.random() * W, rng.random() * H
        c = hx(col)
        splat(x, y, c, 1.6, 0.8)
        splat(x, y, c, 0.09, 3.4)
        for horizontal in (True, False):
            spike(x, y, c, 0.28, 7 + 5 * rng.random(), horizontal)
    rgb = 1.0 - np.exp(-img * 1.15)                    # soft shoulder instead of hard clipping
    save('stars.png', rgb=rgb, dither=0.0)


# ============================================================================ 2. nebula
def make_nebula():
    W, H = 1024, 512
    ys, xs = np.mgrid[0:H, 0:W]
    u = (xs + 0.5) / W
    v = (ys + 0.5) / H
    q1 = fbm2(u, v, 3, 2, 5, 71)
    q2 = fbm2(u, v, 3, 2, 5, 72)
    uw = u + 0.25 * (q1 - 0.5)
    vw = v + 0.25 * (q2 - 0.5)
    d = stretch(fbm2(uw, vw, 4, 2, 7, 73), 2, 99.5)
    soft = smooth(0.22, 0.95, d)
    centre = 0.5 + 0.12 * np.sin(TAU * u + 1.1) + 0.05 * np.sin(2 * TAU * u + 0.3)
    band = np.exp(-((v - centre) / 0.27) ** 2)
    dens = soft * (0.22 + 0.78 * band)
    detail = fbm2(uw + 0.1 * q2, vw, 14, 7, 4, 74)
    dens = dens * (0.55 + 0.9 * detail)
    ridge = 1.0 - np.abs(2.0 * fbm2(uw, vw, 5, 3, 6, 75) - 1.0)
    dens = dens * (1.0 - 0.75 * smooth(0.82, 0.97, ridge))           # dark dust lanes
    dens = np.clip(dens, 0.0, 1.0)
    # thin bright filaments threading the clouds
    fil = 1.0 - np.abs(2.0 * fbm2(uw + 0.15 * q1, vw - 0.1 * q2, 6, 3, 6, 79) - 1.0)
    fil = smooth(0.86, 0.985, fil) * smooth(0.1, 0.6, soft) * (0.4 + 0.6 * band)
    hue1 = smooth(0.3, 0.7, stretch(fbm2(u, v, 2, 1, 4, 76)))
    hue2 = smooth(0.55, 0.85, stretch(fbm2(u, v, 3, 2, 4, 77)))
    hue3 = smooth(0.7, 0.95, stretch(fbm2(u, v, 2, 2, 3, 78)))
    col = lerp(hx('#6a2fc4'), hx('#2448d8'), hue1[..., None])
    col = lerp(col, hx('#139e9a'), (0.9 * hue2)[..., None])
    col = lerp(col, hx('#a8338a'), (0.7 * hue3)[..., None])
    # colour carries only hue and the broad glow of the brightest parts; all the fine structure lives in alpha
    # (smooth colour planes keep the PNG small)
    dsoft = blur(dens, 6.0, wrap_x=True, wrap_y=True)
    fsoft = blur(fil, 4.0, wrap_x=True, wrap_y=True)
    col = col * 0.95 + 0.25 * (dsoft ** 2)[..., None] + (0.9 * fsoft)[..., None] * lerp(col, np.ones(3), 0.5)
    a = np.clip(dens * (0.35 + 0.4 * dens) + fil * 0.32, 0.0, 0.75)
    save('nebula.png', layer(np.clip(col, 0, 1), a), adaptive=True)


# ============================================================================ 3/4. Earth and burning Earth
EARTH_VIEW = dict(lat0=16.0, lon0=12.0, roll=-14.0)
SUN = norm3(-0.86, 0.30, 0.40)                          # light from the left, a little above and in front
IMPACT = (24.0, 14.0)                                   # lat, lon of the impact basin on the burning Earth
_EARTH = {}


def earth_maps():
    if _EARTH:
        return _EARTH
    day = down(released_map('earth_orbital.png'), 2)    # 2048x1024
    lights = released_map('earth_lights.png')           # 2048x1024
    r, g, b = day[..., 0], day[..., 1], day[..., 2]
    mx, mn = day.max(-1), day.min(-1)
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    sat = (mx - mn) / (mx + 1e-6)
    ocean = smooth(0.012, 0.05, b - np.maximum(r, g)) * (1.0 - smooth(0.2, 0.42, lum))
    cloud = smooth(0.22, 0.62, lum) * (1.0 - smooth(0.12, 0.34, sat))
    near_land = 1.0 - blur(ocean, 5.0, wrap_x=True)
    water = lerp(hx('#041a44'), hx('#0b4275'), smooth(0.05, 0.6, near_land)[..., None])
    day2 = day + ocean[..., None] * (water - day) * 0.92
    _EARTH.update(day=day2, lights=lights.mean(-1), ocean=ocean, cloud=cloud,
                  land=1.0 - blur(ocean, 1.5, wrap_x=True))
    return _EARTH


def render_earth(burning):
    S, ss = 512, 2
    n = S * ss
    R = 0.905 * n / 2
    u, v, rho, cover, nx, ny, nz = sphere_grid(n, R)
    px, py, pz = to_planet(nx, ny, nz, **EARTH_VIEW)
    lat, lon = latlon(px, py, pz)
    m = earth_maps()
    ndl = nx * SUN[0] + ny * SUN[1] + nz * SUN[2]
    fres = 1.0 - nz
    lxy = np.array(SUN[:2]) / math.hypot(*SUN[:2])
    limb_lit = smooth(-0.55, 0.65, (u * lxy[0] + v * lxy[1]) / np.maximum(rho, 1e-6))
    outside = np.clip(rho - 1.0, 0.0, None)
    window = smooth(1.085, 1.035, rho)

    if not burning:
        alb = lin(sample_map(m['day'], lat, lon))
        cloud = sample_map(m['cloud'][..., None], lat, lon)[..., 0]
        ocean = sample_map(m['ocean'][..., None], lat, lon)[..., 0]
        lights = sample_map(m['lights'][..., None], lat, lon)[..., 0]
        diffuse = np.clip(ndl, 0.0, None)
        dusk = (1.0 - smooth(0.0, 0.32, ndl)) * smooth(-0.08, 0.04, ndl)
        sun = lerp(np.array([1.0, 0.97, 0.92]), np.array([1.0, 0.52, 0.28]), dusk[..., None])
        col = alb * sun * diffuse[..., None] * 1.18
        hx_, hy_, hz_ = norm3(SUN[0], SUN[1], SUN[2] + 1.0)
        ndh = np.clip(nx * hx_ + ny * hy_ + nz * hz_, 0.0, 1.0)
        glint = (ndh ** 900 * 0.55 + ndh ** 60 * 0.07) * ocean * (1.0 - cloud) * smooth(0.0, 0.1, ndl)
        col += glint[..., None] * np.array([1.0, 0.92, 0.78])
        night = smooth(0.06, -0.16, ndl)
        city = lin(lights) * (1.0 - 0.7 * cloud) * night * 3.2
        col += city[..., None] * lin(hx('#ffb562'))
        col += alb * 0.004 * np.array([0.5, 0.6, 1.0])                  # faint moonlit night side
        day = smooth(-0.3, 0.45, ndl)
        col += (fres ** 2.4 * day * 0.85 + 0.03 * day)[..., None] * lin(hx('#5d95ff'))
        col = srgb(col)
        planet = layer(col, cover)
        halo = np.exp(-outside / 0.018) * (0.15 + 0.85 * limb_lit) * window * (rho > 0.98)
        halo_col = lerp(hx('#cfe6ff'), hx('#3f7dff'), smooth(1.0, 1.04, rho)[..., None])
        out = over(layer(halo_col, halo * 0.95), planet)
        prem = down(out, ss)
        save('earth.png', prem)
        return

    # ---------------- burning: the same continents as dark crust with glowing cracks, the oceans boiled into lava
    land = sample_map(m['land'][..., None], lat, lon)[..., 0]
    cloud = sample_map(m['cloud'][..., None], lat, lon)[..., 0]
    lava_crack = 1.0 - np.abs(2.0 * fbm3(px, py, pz, 4.0, 6, 301) - 1.0)
    lava_fine = 1.0 - np.abs(2.0 * fbm3(px, py, pz, 11.0, 5, 302) - 1.0)
    hot = smooth(0.5, 0.75, fbm3(px, py, pz, 2.2, 5, 303))
    plates = fbm3(px, py, pz, 7.0, 6, 304)
    crust_var = fbm3(px, py, pz, 18.0, 4, 305)
    t_land = (smooth(0.885, 0.975, lava_crack) * (0.5 + 0.45 * hot) + smooth(0.92, 0.985, lava_fine) * 0.38
              + 0.07 * hot * crust_var)
    crust = smooth(0.56, 0.6, plates)                                    # cooling rafts on the lava seas
    seam = np.exp(-((plates - 0.56) / 0.016) ** 2)
    flow = fbm3(px, py, pz, 5.0, 4, 306)
    t_sea = (1.0 - crust) * (0.5 + 0.28 * flow) + crust * (0.06 + 0.08 * hot) + seam * 0.3
    temp = lerp(t_sea, t_land, land)
    coast = np.exp(-((land - 0.5) / 0.2) ** 2)                           # lava lapping at every coastline
    temp = np.maximum(temp, coast * (0.62 + 0.25 * flow))
    # the impact basin: white-hot core, broken molten rings, glowing ejecta rays
    ilat, ilon = math.radians(IMPACT[0]), math.radians(IMPACT[1])
    ix, iy, iz = math.cos(ilat) * math.sin(ilon), math.sin(ilat), math.cos(ilat) * math.cos(ilon)
    dlt = np.arccos(np.clip(px * ix + py * iy + pz * iz, -1.0, 1.0))
    e1 = np.cross([ix, iy, iz], [0.0, 1.0, 0.0])
    e1 /= np.linalg.norm(e1)
    e2 = np.cross([ix, iy, iz], e1)
    ang = np.arctan2(px * e2[0] + py * e2[1] + pz * e2[2], px * e1[0] + py * e1[1] + pz * e1[2])
    rays = smooth(0.55, 0.8, fbm2((ang / TAU) % 1.0, dlt * 0.0, 22, 1, 3, 307))
    wob = fbm3(px, py, pz, 9.0, 3, 309)
    dw = dlt + 0.035 * (wob - 0.5)
    core = 1.0 - smooth(0.0, 0.12, dw)                                   # white-hot melt pool
    pool = smooth(0.2, 0.1, dw)
    rim = np.exp(-((dw - 0.215) / 0.03) ** 2)                            # dark uplifted crater rim
    ring = np.exp(-((dw - 0.31) / 0.018) ** 2) * smooth(0.3, 0.55, fbm3(px, py, pz, 7.0, 3, 310))
    temp = temp * (1.0 - 0.85 * rim) * (1.0 - smooth(0.24, 0.12, dw))
    temp = temp + pool * 0.75 + 1.0 * core + ring * 0.6
    temp += rays * np.exp(-dlt / 0.35) * smooth(0.25, 0.4, dlt) * 0.35 * (1.0 - land * 0.5)
    emit = lin(ramp(np.clip(temp, 0, 2), [(0.0, '#000000'), (0.1, '#1c0300'), (0.25, '#650d00'), (0.45, '#c02600'),
                                          (0.65, '#ff5508'), (0.85, '#ff9822'), (1.1, '#ffd670'), (1.5, '#fff3cc'),
                                          (2.0, '#ffffff')]))
    emit *= (0.25 + 1.15 * np.clip(temp, 0, 1.6))[..., None]
    diffuse = np.clip(ndl, 0.0, None)
    crust_col = lin(lerp(hx('#120c0a'), hx('#3a2a22'), crust_var[..., None]))
    col = crust_col * diffuse[..., None] * np.array([1.0, 0.7, 0.52]) * 1.1 + emit
    smoke = np.clip(cloud * 1.3 + 0.45 * (fbm3(px, py, pz, 3.5, 5, 308) - 0.42), 0.0, 1.0) * 0.85
    under = blur(emit, 6.0)
    smoke_col = lin(hx('#3a2e28')) * (0.12 + 0.9 * diffuse[..., None]) + under * 0.3
    col = col * (1.0 - smoke[..., None]) + smoke_col * smoke[..., None]
    col += blur(emit * smooth(0.9, 1.3, temp)[..., None], 10.0) * 0.5    # bloom of the white-hot areas
    col += (fres ** 2.2 * 1.1)[..., None] * lin(hx('#ff4a14'))
    col = 1.0 - np.exp(-col * 1.25)
    col = srgb(col)
    planet = layer(col, cover)
    halo = np.exp(-outside / 0.03) * (0.65 + 0.35 * limb_lit) * window * (rho > 0.98)
    halo_col = lerp(hx('#ff8a3a'), hx('#c81e08'), smooth(1.0, 1.05, rho)[..., None])
    out = over(layer(halo_col, halo), planet)
    save('earth_burning.png', down(out, ss))


# ============================================================================ 5. asteroid
def make_asteroid():
    S, ss = 256, 3
    n = S * ss
    ys, xs = np.mgrid[0:n, 0:n]
    u = (xs + 0.5) / n * 2.0 - 1.0
    v = 1.0 - (ys + 0.5) / n * 2.0
    th = np.arctan2(v, u)
    rho = np.hypot(u, v)
    rng = np.random.default_rng(20970)
    r = np.full(u.shape, 0.70)
    for k, a in ((2, 0.17), (3, 0.085), (4, 0.05), (5, 0.03), (6, 0.022), (7, 0.016), (9, 0.012), (11, 0.008)):
        r += 0.70 * a * np.cos(k * th + rng.random() * TAU)
    r += 0.035 * (fbm2((th / TAU) % 1.0, 0.0 * th, 18, 1, 5, 501) - 0.5)
    t = np.clip(rho / r, 0.0, 1.0)
    dome = np.sqrt(np.clip(1.0 - t * t, 0.0, 1.0))
    h0 = 0.62 * dome                                  # same height at the centre from every direction
    h = h0 + 0.2 * (fbm3(u * 1.3, v * 1.3, h0 * 1.3, 1.0, 5, 502) - 0.5) * dome
    # craters: bowl, raised rim, measured on the surface so the ones near the limb are foreshortened
    albedo = np.ones(u.shape)
    placed = 0
    while placed < 58:
        cu, cv = rng.random() * 2 - 1, rng.random() * 2 - 1
        ci = min(n - 1, max(0, int((1 - cv) / 2 * n)))
        cj = min(n - 1, max(0, int((cu + 1) / 2 * n)))
        if math.hypot(cu, cv) > 0.95 * r[ci, cj]:
            continue
        cr = 0.025 + 0.2 * rng.random() ** 3.0
        ch = h0[ci, cj]
        d = np.sqrt((u - cu) ** 2 + (v - cv) ** 2 + (h0 - ch) ** 2) / cr
        prof = np.where(d < 1.0, -(1.0 - d * d), 0.0) * 0.36 + 0.13 * np.exp(-((d - 1.0) / 0.22) ** 2)
        h = h + prof * cr * dome ** 0.5
        albedo *= 1.0 - 0.12 * smooth(1.0, 0.7, d) + 0.1 * np.exp(-((d - 1.05) / 0.18) ** 2)
        placed += 1
    for _ in range(90):                                                                       # boulders
        cu, cv = rng.random() * 1.8 - 0.9, rng.random() * 1.8 - 0.9
        br = 0.008 + 0.018 * rng.random() ** 2
        h = h + 0.8 * br * np.exp(-((u - cu) ** 2 + (v - cv) ** 2) / (br * br)) * dome
    h = h + 0.026 * (fbm3(u, v, h0, 8.0, 5, 503) - 0.5) * dome ** 0.3
    h = h + 0.012 * (1.0 - np.abs(2 * fbm3(u, v, h0, 20.0, 3, 504) - 1.0)) * dome ** 0.3
    # normals and a hard light from the upper left
    step = 2.0 / n
    dhr, dhc = np.gradient(h, step)
    gx, gy = dhc, -dhr
    ln = np.sqrt(gx * gx + gy * gy + 1.0)
    Nx, Ny, Nz = -gx / ln, -gy / ln, 1.0 / ln
    L = norm3(-0.82, 0.50, 0.28)
    ndl = Nx * L[0] + Ny * L[1] + Nz * L[2]
    # cast shadows: march over the height field towards the light
    lxy = math.hypot(L[0], L[1])
    dcol, drow, rise = L[0] / lxy, -L[1] / lxy, L[2] / lxy * step
    shadow = np.zeros(u.shape)
    rows, cols = np.mgrid[0:n, 0:n]
    for k in range(1, int(n * 0.9), 2):
        rr = np.clip(np.round(rows + drow * k).astype(np.int64), 0, n - 1)
        cc = np.clip(np.round(cols + dcol * k).astype(np.int64), 0, n - 1)
        shadow = np.maximum(shadow, h[rr, cc] - (h + rise * k))
    lit = 1.0 - smooth(0.0, 0.01, shadow)
    tone = fbm3(u, v, h0, 3.0, 5, 505)
    alb = lerp(hx('#2c2724'), hx('#625850'), smooth(0.3, 0.75, tone)[..., None])
    rust = smooth(0.62, 0.8, fbm3(u, v, h0, 2.2, 4, 507))
    alb = lerp(alb, hx('#5a3a2a'), (0.5 * rust)[..., None])
    alb = alb * (albedo * (0.9 + 0.2 * fbm3(u, v, h0, 30.0, 2, 506)))[..., None]
    diff = np.clip(ndl, 0.0, None) * lit
    fill = np.clip(Nx * 0.75 - Ny * 0.25 + Nz * 0.6, 0.0, None) * 0.035
    col = lin(alb) * (diff[..., None] * np.array([1.0, 0.95, 0.88]) * 3.2 + fill[..., None] * np.array([0.5, 0.6, 1.0])
                      + 0.008)
    col = srgb(col)
    cover = np.clip((r - rho) * n / 2.0 + 0.5, 0.0, 1.0)
    save('asteroid.png', down(layer(col, cover), ss))


# ============================================================================ 6. coma
def make_coma():
    W, H, ss = 512, 128, 2
    ys, xs = np.mgrid[0:H * ss, 0:W * ss]
    x = (xs + 0.5) / ss
    y = (ys + 0.5) / ss
    head = 34.0
    s = (x - head) / (W - head)
    sp = np.clip(s, 0.0, 1.0)
    dy = y - H / 2
    n1 = fbm2(x / W, y / H, 5, 2, 5, 61)
    n2 = fbm2(x / W, y / H, 12, 4, 4, 65)
    dyt = dy + (n1 - 0.5) * 40.0 * sp ** 0.8 + (n2 - 0.5) * 14.0 * sp ** 0.5
    width = 10.0 + 46.0 * sp ** 0.55
    body = np.exp(-(dyt / width) ** 2 * 1.7)
    along = np.where(s >= 0, (1.0 - sp) ** 1.05, np.exp(-((x - head) / 10.0) ** 2))
    streak = fbm2(x / W, y / H, 3, 12, 5, 62)
    tongues = smooth(0.35, 0.75, fbm2(x / W, y / H, 8, 6, 4, 66))
    core = np.exp(-((x - head) ** 2 / (2 * 15.0 ** 2) + dy ** 2 / (2 * 10.0 ** 2)))
    inner = np.exp(-(dyt / (width * 0.35)) ** 2 * 2.0) * (1.0 - sp) ** 2.2
    inten = body * along * (0.5 + 0.7 * streak + 0.35 * tongues) * 1.15 + 0.7 * inner + 1.3 * core
    inten *= smooth(2.0, 12.0, x) * smooth(H / 2, H / 2 - 8, np.abs(dy)) * smooth(1.0, 0.86, s)
    # embers drifting off the edges of the tail
    rng = np.random.default_rng(63)
    emb = np.zeros_like(inten)
    for _ in range(34):
        ex = head + 40 + rng.random() * 360
        ew = 10.0 + 46.0 * ((ex - head) / (W - head)) ** 0.55
        ey = H / 2 + (rng.random() * 2 - 1) * ew * 0.9
        er = 0.7 + 0.6 * rng.random()
        emb += (0.4 + 0.8 * rng.random()) * np.exp(-((x - ex) ** 2 + (y - ey) ** 2) / (2 * er * er)) * \
            (1 - (ex - head) / W)
    inten = inten + emb * 0.7
    temp = inten * (1.0 - 0.3 * sp)
    col = ramp(temp, [(0.0, '#6a0800'), (0.18, '#b81c00'), (0.38, '#f04a08'), (0.6, '#ff8c1c'),
                      (0.85, '#ffd060'), (1.1, '#fff6d0'), (1.4, '#ffffff')])
    a = np.clip(inten * 1.55, 0.0, 1.0) ** 0.8
    save('coma.png', down(layer(col, a), ss), dither=1.0, seed=64)


# ============================================================================ 7/8. Moon and Mars
def make_moon():
    S, ss = 256, 2
    n = S * ss
    R = 0.94 * n / 2
    u, v, rho, cover, nx, ny, nz = sphere_grid(n, R)
    px, py, pz = to_planet(nx, ny, nz, lat0=4.0, lon0=-8.0, roll=-8.0)
    lat, lon = latlon(px, py, pz)
    alb = sample_map(released_map('moon_orbital.png'), lat, lon)
    alb = lin(np.clip((alb - 0.16) / 0.74, 0.0, 1.0))                  # deeper maria
    mu0 = nx * SUN[0] + ny * SUN[1] + nz * SUN[2]
    mu = np.clip(nz, 0.0, 1.0)
    ls = np.where(mu0 > 0, mu0 / (mu0 + mu + 1e-6), 0.0) * 2.0           # Lommel-Seeliger: the flat-lit Moon
    shade = (0.6 * ls + 0.4 * np.clip(mu0, 0.0, None)) * smooth(-0.02, 0.06, mu0)
    col = alb * shade[..., None] * 1.15 + alb * 0.005
    col = srgb(col)
    save('moon.png', down(layer(col, cover), ss))


def make_mars():
    S, ss = 256, 2
    n = S * ss
    R = 0.9 * n / 2
    u, v, rho, cover, nx, ny, nz = sphere_grid(n, R)
    px, py, pz = to_planet(nx, ny, nz, lat0=18.0, lon0=-60.0, roll=-12.0)
    lat, lon = latlon(px, py, pz)
    big = fbm3(px, py, pz, 1.5, 6, 501)
    dark = smooth(0.5, 0.6, big)
    mott = fbm3(px, py, pz, 4.5, 5, 502)
    fine = fbm3(px, py, pz, 16.0, 4, 503)
    base = ramp(mott, [(0.25, '#9c4422'), (0.5, '#c0612f'), (0.75, '#dc9157')])
    alb = lerp(base, hx('#583124'), (0.75 * dark)[..., None]) * (0.88 + 0.24 * fine)[..., None]
    # Valles Marineris-like canyon system south of the equator
    clat = math.radians(-9.0) + 0.03 * np.sin(lon * 5.0) + 0.03 * (fbm3(px, py, pz, 9, 3, 504) - 0.5)
    span = smooth(math.radians(-112), math.radians(-92), lon) * smooth(math.radians(-30), math.radians(-50), lon)
    broken = smooth(0.35, 0.6, fbm3(px, py, pz, 12, 3, 507))
    canyon = np.exp(-((lat - clat) / 0.014) ** 2) * span * (0.45 + 0.55 * broken)
    height = -0.35 * canyon
    alb = alb * (1.0 - 0.3 * canyon)[..., None]
    rng = np.random.default_rng(505)
    for _ in range(90):
        z = rng.normal(size=3)
        z /= np.linalg.norm(z)
        cr = 0.012 + 0.07 * rng.random() ** 3
        d = np.arccos(np.clip(px * z[0] + py * z[1] + pz * z[2], -1, 1)) / cr
        height = height + (np.where(d < 1, -(1 - d * d), 0) * 0.6 + 0.3 * np.exp(-((d - 1) / 0.25) ** 2)) * cr * 6
        alb = alb * (1.0 - 0.08 * (d < 0.9) + 0.06 * np.exp(-((d - 1.05) / 0.2) ** 2))[..., None]
    # northern polar cap with a ragged edge
    edge = math.radians(76) + 0.16 * (fbm3(px, py, pz, 6, 5, 506) - 0.5)
    cap = smooth(edge - 0.01, edge + 0.05, lat) * (0.75 + 0.25 * fbm3(px, py, pz, 20, 3, 508))
    alb = lerp(alb, hx('#e6ddd2'), cap[..., None])
    hr, hc = np.gradient(height)
    k = 2.2
    mx_, my_, mz_ = nx - hc * k, ny + hr * k, nz
    ln = np.sqrt(mx_ ** 2 + my_ ** 2 + mz_ ** 2)
    mu0 = np.clip((mx_ * SUN[0] + my_ * SUN[1] + mz_ * SUN[2]) / ln, 0.0, None)
    mu = np.clip(nz, 0.05, 1.0)
    shade = mu0 ** 0.85 * mu ** -0.15
    col = lin(alb) * shade[..., None] * 1.15
    day = smooth(-0.2, 0.4, nx * SUN[0] + ny * SUN[1] + nz * SUN[2])
    col += ((1.0 - nz) ** 3.0 * day * 0.5)[..., None] * lin(hx('#f0a27c'))
    col = srgb(col)
    planet = layer(col, cover)
    lxy = np.array(SUN[:2]) / math.hypot(*SUN[:2])
    limb_lit = smooth(-0.4, 0.7, (u * lxy[0] + v * lxy[1]) / np.maximum(rho, 1e-6))
    halo = np.exp(-np.clip(rho - 1, 0, None) / 0.013) * limb_lit * 0.6 * smooth(1.1, 1.05, rho) * (rho > 0.98)
    save('mars.png', down(over(layer(hx('#f2b08a'), halo), planet), ss))


# ============================================================================ 9. robots
def walker(ink, x, gy, s=1.0, gun=1):
    """Front view of a bipedal assault mech, ~230 px tall at s=1: gatling arm, claw, spiked knees, V visor."""
    for f in (1, -1):
        q = Pose(ink, x, gy, s, f)
        q.poly([(9, 0), (50, 0), (46, 7), (38, 11), (18, 11), (11, 6)])                      # clawed foot
        q.poly([(48, 0), (57, 0), (49, 6)])                                                  # toe spike
        q.ell(28, 13, 7, 6)                                                                  # ankle
        q.poly([(20, 13), (36, 13), (40, 40), (38, 62), (22, 64), (18, 40)])                 # shin
        q.poly([(13, 58), (42, 54), (55, 63), (45, 70), (40, 81), (18, 81), (11, 68)])       # spiked knee guard
        q.poly([(18, 79), (38, 79), (34, 106), (15, 108)])                                   # thigh
        q.seg((45, 72), (35, 102), 3.2)                                                      # hydraulic ram
        q.ell(18, 106, 10, 9)                                                                # hip actuator
        q.seg((18, 112), (14, 124), 3.0)                                                     # waist piston
    q = Pose(ink, x, gy, s, 1)
    q.poly([(-23, 100), (23, 100), (27, 114), (-27, 114)])                                   # pelvis
    q.rect(-12, 112, 12, 126)                                                                # waist
    q.poly([(-30, 122), (30, 122), (46, 140), (55, 162), (48, 178), (20, 184), (-20, 184), (-48, 178),
            (-55, 162), (-46, 140)])                                                         # chest
    for k in range(3):                                                                       # chest vents
        q.rect(-18, 146 + 7 * k, -5, 148.6 + 7 * k, fill=0)
        q.rect(5, 146 + 7 * k, 18, 148.6 + 7 * k, fill=0)
    q.poly([(-12, 178), (12, 178), (15, 188), (9, 197), (-9, 197), (-15, 188)])              # head
    for f in (1, -1):
        p = Pose(ink, x, gy, s, f)
        p.poly([(1.5, 186.0), (10, 189.5), (10, 187.0), (1.5, 183.5)], 'eye')                # angry V visor
        p.poly([(38, 162), (70, 168), (79, 182), (71, 193), (46, 193), (35, 180)])           # pauldron
        p.poly([(54, 191), (60, 206), (65, 191)])                                            # pauldron spike
        p.seg((60, 172), (66, 138), 13, 12)                                                  # upper arm
        p.ell(66, 136, 8, 8)                                                                 # elbow
    q.rect(24, 184, 31, 210)                                                                 # exhaust stacks
    q.rect(-31, 184, -24, 204)
    g = Pose(ink, x, gy, s, gun)                                                             # gatling arm
    g.poly([(54, 138), (78, 138), (81, 106), (75, 92), (57, 92), (51, 106)])
    for bx in (56.5, 62.5, 68.5):
        g.rect(bx, 50, bx + 3.4, 94)
    g.rect(54, 62, 73, 66)
    g.rect(55, 46, 72, 51)
    g.seg((34, 196), (38, 228), 1.8)                                                         # antenna
    g.eye(38, 229, 1.4)
    c = Pose(ink, x, gy, s, -gun)                                                            # claw arm
    c.seg((66, 136), (70, 106), 12, 10)
    c.ell(70, 104, 9, 7)
    c.seg((64, 102), (56, 84), 5, 2.6)
    c.seg((70, 100), (71, 80), 5, 2.6)
    c.seg((76, 102), (83, 86), 5, 2.6)
    c.rect(40, 192, 64, 206)                                                                 # missile pod
    for hx_ in (44, 50, 56):
        c.rect(hx_, 196, hx_ + 3, 199, fill=0)
        c.rect(hx_, 201, hx_ + 3, 204, fill=0)


def trooper(ink, x, gy, s=1.0, f=1):
    """Front view of an infantry android, ~120 px tall at s=1, rifle held across the chest."""
    for side in (1, -1):
        q = Pose(ink, x, gy, s, side)
        q.poly([(2, 0), (18, 0), (16, 5), (5, 5)])                                           # foot
        q.seg((10, 4), (11, 30), 5.5, 6.5)                                                   # shin
        q.ell(11, 32, 4.6, 4.6)                                                              # knee
        q.seg((11, 32), (8, 55), 7, 8)                                                       # thigh
        q.poly([(12, 88), (27, 90), (29, 97), (15, 99)])                                     # shoulder pad
        q.seg((22, 90), (26, 69), 5, 4.5)                                                    # upper arm
    q = Pose(ink, x, gy, s, f)
    q.poly([(-12, 53), (12, 53), (14, 61), (-14, 61)])                                       # pelvis
    q.rect(-4, 59, 4, 68)                                                                    # spine
    q.poly([(-12, 66), (12, 66), (21, 84), (19, 94), (-19, 94), (-21, 84)])                  # ribcage
    q.rect(-9, 74, 9, 75.6, fill=0)
    q.rect(-10, 79, 10, 80.6, fill=0)
    q.poly([(-7, 94), (7, 94), (8, 106), (4, 111), (-4, 111), (-8, 106)])                    # skull
    q.eye(-3.0, 102, 1.3)
    q.eye(3.0, 102, 1.3)
    q.seg((5, 108), (9, 120), 1.2)                                                           # antenna
    q.seg((26, 69), (8, 69), 5)                                                              # forearms on the rifle
    q.seg((-26, 69), (-8, 63.5), 5)
    q.seg((-36, 54), (32, 77), 3.8)                                                          # rifle
    q.rect(20, 66, 26, 71)


def spider(ink, x, gy, s=1.0):
    for f in (1, -1):
        q = Pose(ink, x, gy, s, f)
        for hip, knee, foot, w in (((22, 52), (60, 96), (100, 0), 8.0),
                                   ((26, 46), (80, 76), (128, 0), 7.0),
                                   ((20, 40), (46, 68), (66, 0), 7.5)):
            q.seg(hip, knee, w * 1.25, w)
            q.seg(knee, (foot[0], foot[1] + 6), w, w * 0.5)
            q.poly([(foot[0] - 2.5, 7), (foot[0] + 2.5, 7), (foot[0] + 0.6, 0), (foot[0] - 0.6, 0)])
            q.ell(knee[0], knee[1], w * 0.75, w * 0.75)
    q = Pose(ink, x, gy, s, 1)
    q.ell(0, 47, 37, 21)
    q.ell(0, 62, 22, 14)
    q.rect(-14, 70, -10, 92)
    q.rect(10, 70, 14, 92)
    q.rect(-3, 72, 3, 82)
    for ex, ey, er in ((-8, 50, 2.6), (8, 50, 2.6), (-16, 45, 1.8), (16, 45, 1.8), (0, 43, 1.5)):
        q.eye(ex, ey, er)


def titan(ink, x, gy, s=1.0):
    """Front view of the siege titan boss, ~250 px tall at s=1: triple cannon, armoured fist, reactor core."""
    for f in (1, -1):
        q = Pose(ink, x, gy, s, f)
        q.poly([(8, 0), (84, 0), (81, 12), (70, 20), (22, 20), (13, 12)])                     # foot
        q.rect(36, 2, 37.6, 12, fill=0)                                                       # toe joints
        q.rect(58, 2, 59.6, 12, fill=0)
        q.poly([(24, 18), (62, 18), (68, 58), (64, 86), (28, 88), (20, 58)])                  # shin
        q.poly([(14, 80), (68, 78), (85, 90), (72, 98), (62, 118), (24, 118), (10, 98)])      # spiked knee guard
        q.poly([(26, 114), (60, 114), (56, 142), (20, 144)])                                  # thigh
        q.seg((71, 101), (60, 138), 4.5)                                                      # hydraulic ram
    q = Pose(ink, x, gy, s, 1)
    q.poly([(-50, 136), (50, 136), (56, 154), (-56, 154)])                                    # hips
    q.poly([(-34, 150), (34, 150), (38, 165), (-38, 165)])                                    # abdomen
    q.rect(-30, 155, 30, 156.8, fill=0)
    q.rect(-30, 159.5, 30, 161.3, fill=0)
    q.poly([(-58, 163), (58, 163), (84, 186), (98, 210), (90, 224), (54, 232), (-54, 232), (-90, 224),
            (-98, 210), (-84, 186)])                                                          # torso
    for k in range(4):                                                                        # chest grilles
        q.rect(-36, 182 + 6 * k, -13, 184.2 + 6 * k, fill=0)
        q.rect(13, 182 + 6 * k, 36, 184.2 + 6 * k, fill=0)
    q.ell(0, 197, 4.2, 4.2, 'eye')                                                            # reactor core
    q.poly([(-20, 226), (20, 226), (23, 238), (14, 246), (-14, 246), (-23, 238)])             # helmet
    q.rect(-14, 234, 14, 237.2, 'eye')                                                        # visor slit
    q.rect(44, 228, 52, 251)                                                                  # exhaust stacks
    q.rect(-52, 228, -44, 247)
    for f in (1, -1):
        p = Pose(ink, x, gy, s, f)
        p.poly([(17, 238), (27, 253), (24, 236)])                                             # helmet fins
        p.poly([(72, 194), (122, 200), (134, 218), (124, 236), (88, 238), (70, 224)])        # pauldron
        p.poly([(78, 184), (124, 189), (131, 202), (121, 199), (82, 196)])                   # lower plate
        p.poly([(94, 236), (100, 250), (106, 236)])                                          # spikes
        p.poly([(110, 233), (117, 246), (120, 231)])
        p.seg((104, 196), (112, 154), 28, 24)                                                # upper arm
    a = Pose(ink, x, gy, s, 1)                                                               # armoured fist
    a.poly([(92, 158), (132, 158), (136, 118), (130, 76), (98, 76), (90, 118)])
    a.poly([(92, 78), (134, 78), (138, 58), (130, 40), (100, 40), (88, 58)])
    for fx in (104, 114, 124):
        a.rect(fx, 40, fx + 1.6, 56, fill=0)
    b = Pose(ink, x, gy, s, -1)                                                              # triple cannon
    b.poly([(88, 160), (134, 160), (140, 116), (134, 92), (94, 92), (84, 116)])
    for bx in (96, 107, 118):
        b.rect(bx, 22, bx + 8, 94)
        b.rect(bx - 1, 15, bx + 9, 23)
    b.rect(92, 52, 130, 58)


def quad_drone(ink, x, y, s=1.0):
    q = Pose(ink, x, y, s, 1)
    q.poly([(-15, -6), (15, -6), (19, 1), (13, 9), (-13, 9), (-19, 1)])
    for f in (1, -1):
        p = Pose(ink, x, y, s, f)
        p.seg((14, 3), (34, 7), 3.2)
        p.ell(34, 5, 5, 4.2)
        p.ell(34, 10.5, 18, 1.7)
        p.rect(33, -6, 35, 2)
    q.rect(-3, -13, 3, -6)
    q.rect(-1, -18, 1, -12)
    q.eye(0, 1.5, 3.2)


def wing_drone(ink, x, y, s=1.0):
    q = Pose(ink, x, y, s, 1)
    q.poly([(-50, 1), (-14, 5), (-6, 11), (6, 11), (14, 5), (50, 1), (52, -2), (14, -3), (6, -9), (-6, -9),
            (-14, -3), (-52, -2)])
    for f in (1, -1):
        Pose(ink, x, y, s, f).poly([(47, 0), (52, 11), (54, 11), (52, -1)])
        Pose(ink, x, y, s, f).ell(22, -3, 5, 3.4)
    q.eye(0, 1.5, 2.6)


def silhouette_layer(ink, base_rgb, rim_rgb, eye_core, eye_glow, glows, wrap):
    body = ink.mask('body')
    eye = ink.mask('eye')
    rim = rim_light(body, wrap_x=wrap)
    col = lerp(np.broadcast_to(base_rgb, body.shape + (3,)), np.broadcast_to(rim_rgb, body.shape + (3,)),
               rim[..., None])
    out = layer(col, body)
    g = glow_of(eye, glows, wrap_x=wrap)
    out = light(out, eye_glow, g)
    out = light(out, eye_core, np.clip(eye * 1.6 - 0.25, 0, 1))
    return out


def make_robots():
    W, H = 1024, 256
    ink = Ink(W, H, ss=4, wrap=True)
    xs = np.arange(W + 1)
    prof = fbm2(xs / W, 0.0 * xs, 24, 1, 4, 91)
    ground = H - (5.0 + 5.0 * prof)
    ink.poly([(0, H)] + [(float(x), float(gy)) for x, gy in zip(xs, ground)] + [(W, H)])
    rng = np.random.default_rng(92)
    for _ in range(26):                                                                       # rubble
        x0 = rng.random() * W
        w = 3 + rng.random() * 9
        hgt = 2 + rng.random() * 6
        ink.poly([(x0, H - 4), (x0 + w * 0.3, H - 4 - hgt), (x0 + w * 0.7, H - 4 - hgt * 0.8), (x0 + w, H - 4)])
    gy = H - 5
    trooper(ink, 30, gy, 0.95, 1)
    walker(ink, 152, gy, 0.8, 1)
    spider(ink, 312, gy, 0.86)
    trooper(ink, 398, gy, 0.82, -1)
    titan(ink, 548, gy, 0.97)
    trooper(ink, 716, gy, 1.0, -1)
    spider(ink, 808, gy, 0.66)
    walker(ink, 918, gy, 0.68, -1)
    trooper(ink, 1004, gy, 0.76, 1)
    quad_drone(ink, 92, 46, 0.9)
    wing_drone(ink, 268, 30, 1.0)
    quad_drone(ink, 430, 78, 0.62)
    quad_drone(ink, 700, 40, 1.05)
    wing_drone(ink, 846, 84, 0.66)
    quad_drone(ink, 975, 24, 0.55)
    out = silhouette_layer(ink, hx('#0b0709'), hx('#5a1410'), np.array([1.0, 0.86, 0.8]), hx('#ff2414'),
                           ((1.6, 0.9), (4.5, 0.55), (10.0, 0.25)), True)
    save('robots.png', out)


# ============================================================================ 10. base
def make_base():
    W, H = 1024, 256
    ink = Ink(W, H, ss=4, wrap=True)
    rng = np.random.default_rng(101)
    ink.rect(0, 244, W, H)                                                                   # ground
    ink.rect(0, 206, W, 245)                                                                 # outer wall
    for i in range(42):                                                                      # parapet blocks
        x0 = 4 + i * W / 42
        ink.rect(x0, 200, x0 + 13, 207)
    for x0 in range(10, W, 64):                                                              # buttresses
        ink.poly([(x0, 245), (x0 + 6, 214), (x0 + 12, 214), (x0 + 18, 245)])

    def tower(cx, top, turret_dir):
        ink.poly([(cx - 21, 245), (cx - 18, top), (cx + 18, top), (cx + 21, 245)])
        ink.rect(cx - 24, top - 5, cx + 24, top + 2)                                         # turret ring
        ink.ellipse(cx, top - 10, 15, 10)                                                    # turret head
        ink.rect(cx - 17, top - 10, cx + 17, top - 4)
        ang = math.radians(40 if turret_dir > 0 else 140)
        dx, dy = math.cos(ang), -math.sin(ang)
        for off in (-4.2, 4.2):                                                              # twin barrels
            bx, by = cx + dx * 6 + off * math.sin(ang), top - 12 + dy * 6 + off * math.cos(ang)
            ink.seg((bx, by), (bx + 36 * dx, by + 36 * dy), 3.6, 3.0)
            ink.seg((bx + 33 * dx, by + 33 * dy), (bx + 39 * dx, by + 39 * dy), 4.6)       # muzzle brake
        sx = cx - turret_dir * 6                                                             # sensor mast
        ink.rect(sx - 1, top - 30, sx + 1, top - 17)
        ink.ellipse(sx, top - 31, 3.2, 2.2)
        return (sx, top - 34)

    beacons = []
    beacons.append(tower(62, 166, 1))
    beacons.append(tower(352, 172, -1))
    beacons.append(tower(618, 162, 1))
    beacons.append(tower(962, 168, -1))
    # factory hall with a sawtooth roof and chimneys
    ink.rect(112, 168, 300, 206)
    for i in range(5):
        x0 = 112 + i * 37.6
        ink.poly([(x0, 168), (x0, 150), (x0 + 37.6, 168)])
    for cx, top, w in ((146, 66, 13), (184, 92, 10), (276, 56, 15)):
        ink.poly([(cx - w / 2 - 2, 169), (cx - w / 2, top), (cx + w / 2, top), (cx + w / 2 + 2, 169)])
        for yb in (top + 6, top + 26):
            ink.rect(cx - w / 2 - 2, yb, cx + w / 2 + 2, yb + 3)
        beacons.append((cx, top - 2))
    # silo and command dome with an antenna mast
    ink.rect(306, 132, 334, 206)
    ink.ellipse(320, 132, 14, 9)
    ink.ellipse(462, 206, 66, 74)
    ink.rect(394, 196, 530, 206)
    ink.rect(461, 52, 464, 140)
    for yb, w in ((70, 10), (92, 16), (114, 22)):
        ink.rect(462.5 - w / 2, yb, 462.5 + w / 2, yb + 2)
    beacons.append((462.5, 50))
    ink.seg((500, 150), (520, 136), 3)
    ink.ellipse(524, 132, 9, 4)
    # spherical tanks
    for cx in (556, 592):
        ink.rect(cx - 12, 180, cx - 9, 206)
        ink.rect(cx + 9, 180, cx + 12, 206)
        ink.ellipse(cx, 172, 17, 17)
    # launch tower (lattice), crane and service arms; the pad sits right of it
    tx0, tx1, ttop = 700, 726, 34
    ink.rect(tx0, ttop, tx1, 245)
    y = ttop + 6
    while y + 15 < 198:
        ink.poly([(tx0 + 3, y + 2), (tx1 - 3, y + 2), (tx1 - 3, y + 15), (tx0 + 3, y + 15)], fill=0)
        ink.seg((tx0 + 3, y + 2), (tx1 - 3, y + 15), 2.2)
        ink.seg((tx1 - 3, y + 2), (tx0 + 3, y + 15), 2.2)
        y += 18
    ink.rect(tx0 - 30, ttop - 2, tx1 + 8, ttop + 4)                                           # crane jib
    ink.rect(tx0 - 28, ttop + 4, tx0 - 27, ttop + 26)
    ink.rect(tx1, 88, 768, 92)                                                               # service arms
    ink.rect(tx1, 140, 762, 144)
    ink.rect(tx1, 196, 758, 200)
    ink.rect(712, ttop - 16, 714, ttop)
    beacons.append((713, ttop - 18))
    beacons.append((tx0 - 30, ttop - 4))
    ink.poly([(734, 245), (738, 226), (812, 226), (816, 245)])                               # launch pad
    ink.rect(752, 220, 798, 227)                                                             # launch mount
    # SAM launcher on the wall: four-cell box raised towards the sky, with its radar panel
    ink.rect(820, 194, 866, 202)
    ang = math.radians(48)
    ux, uy = math.cos(ang), -math.sin(ang)
    vx, vy = math.sin(ang), math.cos(ang)
    px_, py_ = 842.0, 192.0
    ink.poly([(px_ - 8 * vx, py_ - 8 * vy), (px_ + 8 * vx, py_ + 8 * vy),
              (px_ + 8 * vx + 40 * ux, py_ + 8 * vy + 40 * uy), (px_ - 8 * vx + 40 * ux, py_ - 8 * vy + 40 * uy)])
    for k in (-4.5, 0.0, 4.5):                                                               # missile noses
        bx, by = px_ + k * vx + 40 * ux, py_ + k * vy + 40 * uy
        ink.poly([(bx - 1.8 * vx, by - 1.8 * vy), (bx + 1.8 * vx, by + 1.8 * vy), (bx + 5 * ux, by + 5 * uy)])
    ink.seg((832, 194), (836, 186), 3)
    ink.rect(824, 182, 836, 186)
    ink.seg((860, 194), (860, 168), 1.8)                                                     # radar panel mast
    ink.poly([(853, 160), (867, 156), (868, 168), (854, 172)])
    beacons.append((860, 154))
    # radar dish on a lattice mast
    ink.poly([(872, 245), (878, 150), (884, 150), (890, 245)])
    ink.poly([(878, 160), (884, 160), (884, 196), (878, 196)], fill=0)
    ink.seg((878, 160), (884, 196), 1.6)
    ink.seg((884, 160), (878, 196), 1.6)
    da = math.radians(35)                                                                    # dish tilted up-left

    def ell_pts(cx, cy, rx, ry, rot, n=48):
        return [(cx + rx * math.cos(t) * math.cos(rot) - ry * math.sin(t) * math.sin(rot),
                 cy + rx * math.cos(t) * math.sin(rot) + ry * math.sin(t) * math.cos(rot))
                for t in (TAU * i / n for i in range(n))]
    dcx, dcy = 878.0, 128.0
    ink.poly(ell_pts(dcx, dcy, 25, 10, da))
    nxd, nyd = -math.sin(da), -math.cos(da)                                                  # dish axis
    ink.poly(ell_pts(dcx + 5 * nxd, dcy + 5 * nyd, 23, 7.5, da), fill=0)
    ink.seg((dcx, dcy), (dcx + 18 * nxd, dcy + 18 * nyd), 1.5)                              # feed horn
    ink.ellipse(dcx + 19 * nxd, dcy + 19 * nyd, 2.2, 2.2)
    ink.seg((dcx + 6, dcy + 8), (881, 152), 3.5)
    beacons.append((881, 147))
    # warm windows (lit and dark), slit windows along the wall
    win = Ink(W, H, ss=4, wrap=True)
    for i in range(5):                                                                       # sawtooth glazing
        x0 = 112 + i * 37.6
        win.poly([(x0 + 1.5, 154), (x0 + 1.5, 166), (x0 + 6, 166), (x0 + 6, 157)])
    for x0 in range(118, 296, 9):
        if rng.random() < 0.75:
            win.rect(x0, 180, x0 + 5, 186)
        if rng.random() < 0.5:
            win.rect(x0, 192, x0 + 5, 198)
    for i in range(13):                                                                      # dome window band
        a = math.radians(200 + i * 11.5)
        wx, wy = 462 + 52 * math.cos(a), 206 + 58 * math.sin(a)
        if rng.random() < 0.85:
            win.rect(wx - 2.5, wy - 2, wx + 2.5, wy + 2)
    win.rect(312, 150, 328, 153)
    for x0 in range(8, W, 23):                                                               # wall slits
        if rng.random() < 0.38:
            win.rect(x0, 222, x0 + 7, 225)
    for cx, top in ((62, 166), (352, 172), (618, 162), (962, 168)):
        win.rect(cx - 8, top + 12, cx + 8, top + 16)
    for yb in (66, 112, 160, 214):                                                           # launch tower lights
        win.rect(tx1 - 2, yb, tx1 + 1, yb + 2)
    bea = Ink(W, H, ss=4, wrap=True)
    for bx, by in beacons:
        bea.ellipse(bx, by, 2.2, 2.2)
    body = ink.mask('body')
    wins = win.mask('body') * body
    rim = rim_light(body, 1.5, 1.0, wrap_x=True)
    col = lerp(np.broadcast_to(hx('#05070d'), body.shape + (3,)), np.broadcast_to(hx('#1c2a40'), body.shape + (3,)),
               rim[..., None])
    out = layer(col, body)
    out = light(out, hx('#ffb054'), glow_of(wins, ((1.2, 0.6), (3.5, 0.3)), True))
    out = light(out, hx('#ffd9a0'), wins)
    bm = bea.mask('body')
    out = light(out, hx('#ff2a18'), glow_of(bm, ((1.5, 1.0), (4.0, 0.6), (9.0, 0.3)), True))
    out = light(out, hx('#ffd0c0'), np.clip(bm * 1.5, 0, 1))
    save('base.png', out)


# ============================================================================ 11. rocket and flame
def make_rocket():
    W, H, ss = 128, 256, 4
    n_w, n_h = W * ss, H * ss
    ys, xs = np.mgrid[0:n_h, 0:n_w]
    x = (xs + 0.5) / ss - W / 2
    y = (ys + 0.5) / ss
    Rb, tip, nose_end, body_end, tail_end, noz_end = 19.0, 8.0, 74.0, 214.0, 222.0, 249.0
    t = np.clip((y - tip) / (nose_end - tip), 0.0, 1.0)
    r = np.where(y < nose_end, Rb * np.sqrt(np.clip(1 - (1 - t) ** 2, 0, 1)) ** 1.12, Rb)
    r = np.where(y > body_end, Rb - (Rb - 15.0) * np.clip((y - body_end) / (tail_end - body_end), 0, 1), r)
    in_body = (y >= tip) & (y <= tail_end)
    cov_body = np.clip(r - np.abs(x) + 0.5, 0, 1) * in_body
    nozr = 9.0 + 7.5 * np.clip((y - tail_end) / (noz_end - tail_end), 0, 1) ** 1.3
    in_noz = (y > tail_end) & (y <= noz_end)
    cov_noz = np.clip(nozr - np.abs(x) + 0.5, 0, 1) * in_noz
    # cylinder shading, light from the upper left and front
    nxs = np.clip(x / np.maximum(r, 1e-6), -1, 1)
    nzs = np.sqrt(1 - nxs ** 2)
    dr = np.gradient(r, axis=0) * ss
    nys = np.clip(-dr * 0.9, -2, 2)
    ln = np.sqrt(nxs ** 2 + nys ** 2 + nzs ** 2)
    Nx, Ny, Nz = nxs / ln, nys / ln, nzs / ln
    L = norm3(-0.62, 0.35, 0.70)
    ndl = np.clip(Nx * L[0] + Ny * L[1] + Nz * L[2], 0, None)
    hx_, hy_, hz_ = norm3(L[0], L[1], L[2] + 1)
    spec = np.clip(Nx * hx_ + Ny * hy_ + Nz * hz_, 0, 1) ** 36
    az = np.arcsin(nxs)                                                                      # azimuth on the body
    alb = np.broadcast_to(hx('#eceef2'), x.shape + (3,)).copy()
    grey = (y > 118) & (y < 150) & (az > 0.15)
    alb[grey] = hx('#b9bec6')
    band = ((y > 80) & (y < 93)) | ((y > 198) & (y < 205))
    alb[band] = hx('#e2541c')
    roll = (y > 160) & (y < 196) & (((y < 178) & (az < 0)) | ((y >= 178) & (az >= 0)))
    alb[roll] = hx('#1b1d22')
    for yl in (100.0, 118.0, 150.0, 160.0, 214.0):
        alb[np.abs(y - yl) < 0.45] *= 0.62
    window = np.hypot(x + 2.0, y - 58.0) < 4.2
    alb[window] = hx('#14243c')
    shade = 0.22 + 0.95 * ndl
    col = alb * shade[..., None] + (spec * 0.55)[..., None]
    col[window & (np.hypot(x + 3.2, y - 56.8) < 1.3)] += 0.7
    body = layer(np.clip(col, 0, 1), cov_body)
    # nozzle bell
    nzn = np.clip(x / np.maximum(nozr, 1e-6), -1, 1)
    nshade = 0.25 + 0.85 * np.clip(-nzn * 0.62 + np.sqrt(1 - nzn ** 2) * 0.7, 0, None)
    ncol = hx('#3a3d44') * nshade[..., None] + (np.clip(-nzn * 0.62 + np.sqrt(1 - nzn ** 2) * 0.7, 0, 1) ** 12 * 0.5)[
        ..., None]
    ncol *= (1.0 - 0.25 * (np.abs(((y - tail_end) % 6.0) - 3.0) < 0.4))[..., None]
    noz = layer(np.clip(ncol, 0, 1), cov_noz)
    # fins: two swept side fins and the one facing us, seen edge-on
    fin = Ink(W, H, ss=ss)
    for f in (1, -1):
        fin.poly([(W / 2 + f * (Rb - 1), 172), (W / 2 + f * 44, 206), (W / 2 + f * 46, 236), (W / 2 + f * 42, 238),
                  (W / 2 + f * (Rb - 1), 220)])
    fm = np.asarray(fin.im['body'], dtype=np.float64) / 255.0
    fshade = np.where(x < 0, 1.0, 0.72) * (0.85 + 0.15 * np.clip((240 - y) / 60, 0, 1))
    fcol = hx('#d24b18') * fshade[..., None]
    fins = layer(fcol, fm)
    front_fin = (np.abs(x) < 2.6) & (y > 182) & (y < 240)
    ffcov = np.clip(2.6 - np.abs(x) + 0.5, 0, 1) * smooth(181.5, 182.5, y) * smooth(240.5, 239.5, y)
    ffcol = hx('#e2541c') * (0.75 + 0.35 * np.clip(-x / 2.6, -1, 1))[..., None]
    out = over(over(over(fins, noz), body), layer(ffcol, ffcov * front_fin))
    save('rocket.png', down(out, ss))


def make_flame():
    N, ss = 128, 2
    ys, xs = np.mgrid[0:N * ss, 0:N * ss]
    x = (xs + 0.5) / ss - N / 2
    y = (ys + 0.5) / ss
    t = y / N
    nz = fbm2((x + N / 2) / N, y / N, 9, 2, 5, 111)
    tip = 1.0 + 0.18 * (fbm2((x + N / 2) / N, 0.0 * y, 6, 1, 3, 114) - 0.5)                # ragged tail end
    tt = np.clip(t / tip, 0.0, 1.0)
    w = 17.0 * (1.0 + 1.1 * np.sin(np.clip(tt / 0.4, 0, 1) * math.pi / 2)) * np.clip(1.0 - tt ** 1.4, 0, 1) ** 0.7
    w = w + 0.05
    xx = x + (nz - 0.5) * 12.0 * t
    outer = np.exp(-(xx / w) ** 2 * 1.6)
    cw = 0.45 * w * np.clip(1.0 - t / 0.5, 0, 1) ** 0.6 + 0.05
    core = np.exp(-(xx / cw) ** 2 * 2.2) * (t < 0.52)
    diamonds = 0.78 + 0.22 * np.cos(np.clip(t, 0, 0.5) * TAU * 4.0) ** 2
    flick = fbm2((x + N / 2) / N, y / N, 14, 3, 4, 112)
    inten = outer * (1.0 - tt) ** 0.45 * (0.7 + 0.7 * flick) + core * diamonds * 0.75
    inten += 0.55 * np.exp(-(x ** 2) / (2 * 12.0 ** 2)) * np.exp(-y / 6.0)                  # hot nozzle exit
    col = ramp(inten, [(0.0, '#7a1000'), (0.2, '#d83400'), (0.42, '#ff7410'), (0.65, '#ffbc3a'),
                       (0.88, '#fff1bf'), (1.15, '#ffffff')])
    a = np.clip(inten * 1.45, 0.0, 1.0)
    save('flame.png', down(layer(col, a), ss), dither=1.0, seed=113)


# ============================================================================ 12-14. shockwave, glow, noise
def make_shockwave():
    N = 256
    ys, xs = np.mgrid[0:N, 0:N]
    dx, dy = xs + 0.5 - N / 2, ys + 0.5 - N / 2
    r = np.hypot(dx, dy) / (N / 2)
    th = np.arctan2(dy, dx)
    wob = fbm2((th / TAU) % 1.0, 0.0 * th, 20, 1, 4, 121)
    r0 = 0.84
    d = r - r0
    ring = np.where(d > 0, np.exp(-(d / 0.016) ** 2), np.exp(-(d / 0.07) ** 2))
    haze = 0.07 * smooth(0.5, r0, r) * (d < 0)
    a = np.clip(ring * (0.8 + 0.4 * wob) + haze, 0.0, 1.0) * smooth(1.0, 0.95, r)
    col = ramp(np.clip(ring, 0, 1), [(0.0, '#ff8a20'), (0.45, '#ffc848'), (0.8, '#fff2c0'), (1.0, '#ffffff')])
    save('shockwave.png', layer(col, a), dither=1.0, seed=122)


def make_glow():
    N = 128
    ys, xs = np.mgrid[0:N, 0:N]
    r = np.hypot(xs + 0.5 - N / 2, ys + 0.5 - N / 2) / (N / 2)
    a = 0.6 * np.exp(-(r / 0.2) ** 2) + 0.4 * np.exp(-(r / 0.5) ** 2)
    a = a * np.clip(1.0 - r * r, 0.0, 1.0) ** 2
    a /= a.max()
    save('glow.png', layer(np.ones(3), a), dither=1.0, seed=131)


def make_noise():
    N = 256
    rng = np.random.default_rng(141)
    lum = rng.integers(0, 16, (N, N)) / 15.0
    lum = np.clip(lum * (0.7 + 0.6 * rng.random(N))[:, None], 0, 1)                          # line-to-line jitter
    ys, xs = np.mgrid[0:N, 0:N]
    blot = stretch(fbm2((xs + 0.5) / N, (ys + 0.5) / N, 4, 4, 4, 142))
    a = (0.3 + 0.7 * blot) * (0.55 + 0.45 * rng.integers(0, 8, (N, N)) / 7.0)
    a = np.round(a * 15.0) / 15.0
    q = np.concatenate([np.repeat(np.round(lum * 255).astype(np.uint8)[..., None], 3, axis=-1),
                        np.round(a * 255).astype(np.uint8)[..., None]], axis=-1)
    Image.fromarray(q).save(os.path.join(OUT, 'noise.png'), optimize=True)


# ============================================================================ 15. logo
def sd_box(px, py, x0, y0, x1, y1, r=(0.0, 0.0, 0.0, 0.0)):
    """Signed distance to an axis-aligned box (y down) with corner radii (top-left, top-right, bottom-right,
    bottom-left)."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    qx, qy = px - cx, py - cy
    rr = np.where(qx < 0, np.where(qy < 0, r[0], r[3]), np.where(qy < 0, r[1], r[2]))
    ax = np.abs(qx) - (x1 - x0) / 2 + rr
    ay = np.abs(qy) - (y1 - y0) / 2 + rr
    return np.hypot(np.maximum(ax, 0), np.maximum(ay, 0)) + np.minimum(np.maximum(ax, ay), 0) - rr


def sd_poly(px, py, pts):
    """Signed distance to a simple polygon."""
    d = np.full(px.shape, np.inf)
    sign = np.ones(px.shape)
    n = len(pts)
    for i in range(n):
        (ax, ay), (bx, by) = pts[i - 1], pts[i]
        ex, ey = bx - ax, by - ay
        wx, wy = px - ax, py - ay
        h = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey), 0, 1)
        d = np.minimum(d, (wx - ex * h) ** 2 + (wy - ey * h) ** 2)
        c1 = py >= ay
        c2 = py < by
        c3 = ex * wy > ey * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        sign = np.where(flip, -sign, sign)
    return sign * np.sqrt(d)


T_STROKE, R_OUT, R_IN = 0.2, 0.27, 0.085
WIDTHS = {'H': 1.12, 'E': 0.98, 'L': 0.92, 'I': T_STROKE, 'O': 1.3, 'S': 1.12, 'P': 1.04, 'R': 1.1}


def glyph(ch, px, py, w):
    t, ro, ri = T_STROKE, R_OUT, R_IN
    U = np.minimum
    if ch == 'H':
        return U(U(sd_box(px, py, 0, 0, t, 1), sd_box(px, py, w - t, 0, w, 1)),
                 sd_box(px, py, 0, 0.5 - t / 2, w, 0.5 + t / 2))
    if ch == 'E':
        return U(U(sd_box(px, py, 0, 0, t, 1), sd_box(px, py, 0, 0, w, t)),
                 U(sd_box(px, py, 0, 1 - t, w, 1), sd_box(px, py, 0, 0.5 - t / 2, w * 0.86, 0.5 + t / 2)))
    if ch == 'L':
        return U(sd_box(px, py, 0, 0, t, 1), sd_box(px, py, 0, 1 - t, w, 1))
    if ch == 'I':
        return sd_box(px, py, 0, 0, t, 1)
    if ch == 'O':
        return np.maximum(sd_box(px, py, 0, 0, w, 1, (ro,) * 4), -sd_box(px, py, t, t, w - t, 1 - t, (ri,) * 4))
    if ch in 'PR':
        m = 0.54
        bowl = np.maximum(sd_box(px, py, 0, 0, w, m + t / 2, (0, ro, ro, 0)),
                          -sd_box(px, py, t, t, w - t, m - t / 2, (0, ri, ri, 0)))
        d = U(sd_box(px, py, 0, 0, t, 1), bowl)
        if ch == 'R':
            d = U(d, sd_poly(px, py, [(w * 0.42, m), (w * 0.42 + t * 1.15, m), (w, 1), (w - t * 1.25, 1)]))
        return d
    if ch == 'S':
        # two half rings sharing the middle bar; each half is cut open past its counter's rounded end, so only
        # one ring forms each turn of the spine
        m, c = 0.5, t + ri + 0.02
        top = np.maximum(sd_box(px, py, 0, 0, w, m + t / 2, (ro,) * 4),
                         -sd_box(px, py, t, t, w - t, m - t / 2, (ri,) * 4))
        top = np.maximum(top, -sd_box(px, py, w - c, t, w + 1, m + t / 2 + 0.01))
        bot = np.maximum(sd_box(px, py, 0, m - t / 2, w, 1, (ro,) * 4),
                         -sd_box(px, py, t, m + t / 2, w - t, 1 - t, (ri,) * 4))
        bot = np.maximum(bot, -sd_box(px, py, -1, m - t / 2 - 0.01, c, 1 - t))
        return U(top, bot)
    raise ValueError(ch)


def make_logo():
    W, H, ss = 1024, 256, 2
    word = 'HELIOSPHERE'
    gap = 0.23
    total = sum(WIDTHS[c] for c in word) + gap * (len(word) - 1)
    cap = (W - 2 * 40) / total
    x0 = (W - total * cap) / 2
    top = (H - cap) / 2
    ys, xs = np.mgrid[0:H * ss, 0:W * ss]
    X = (xs + 0.5) / ss
    Y = (ys + 0.5) / ss
    d = np.full(X.shape, 1e4)
    cx = x0
    for c in word:
        w = WIDTHS[c]
        lo, hi = cx - 0.3 * cap, cx + (w + 0.3) * cap
        sel = (X[0] > lo) & (X[0] < hi)
        sub = glyph(c, (X[:, sel] - cx) / cap, (Y[:, sel] - top) / cap, w) * cap
        d[:, sel] = np.minimum(d[:, sel], sub)
        cx += (w + gap) * cap
    px = 1.0 / ss
    fill = np.clip(0.5 - d / px, 0, 1)
    outline_w = 1.6
    edge = np.clip(0.5 - (d - outline_w) / px, 0, 1) - fill
    # brushed metal: bright sky reflection on top, dark horizon line, warm sunlit reflection below
    tt = (Y - top) / cap
    metal = ramp(tt, [(0.0, '#ffffff'), (0.2, '#e3e9ef'), (0.46, '#97a3b0'), (0.5, '#46505c'), (0.56, '#5a5250'),
                      (0.8, '#c08e5e'), (1.0, '#f6c88e')])
    metal *= (0.94 + 0.06 * fbm2(X / W, Y / H, 60, 2, 3, 151))[..., None]                     # brushing
    gy, gx = np.gradient(d)
    gl = np.sqrt(gx * gx + gy * gy) + 1e-9
    facing = -(gx * -0.6 + gy * -0.8) / gl                                                   # edge normal vs light
    bevel = smooth(3.0, 0.0, -d) * (d < 0)
    metal = metal * (1.0 + 0.35 * (bevel * facing)[..., None]) + (0.25 * bevel * np.clip(facing, 0, 1))[..., None]
    letters = layer(np.clip(metal, 0, 1), fill)
    rim = layer(hx('#fff1d8'), edge)
    shape = np.clip(fill + edge, 0, 1)
    glow = blur(shape, 6.0 * ss) * 0.55 + blur(shape, 18.0 * ss) * 0.4
    halo = layer(hx('#ff7a2c'), np.clip(glow * 0.75, 0, 1))
    out = over(over(halo, rim), letters)
    save('logo.png', down(out, ss))


# ============================================================================ main
def make_old():
    os.makedirs(OLD, exist_ok=True)
    for name in OLD_NAMES:
        Image.fromarray(np.zeros((1, 1, 4), dtype=np.uint8)).save(os.path.join(OLD, name + '.png'), optimize=True)


JOBS = {
    'stars': make_stars, 'nebula': make_nebula, 'earth': lambda: render_earth(False),
    'earth_burning': lambda: render_earth(True), 'asteroid': make_asteroid, 'coma': make_coma, 'moon': make_moon,
    'mars': make_mars, 'robots': make_robots, 'base': make_base, 'rocket': make_rocket, 'flame': make_flame,
    'shockwave': make_shockwave, 'glow': make_glow, 'noise': make_noise, 'logo': make_logo, 'old': make_old,
}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    names = sys.argv[1:] or list(JOBS)
    for nm in names:
        if nm not in JOBS:
            sys.exit('unknown image %r (choose from %s)' % (nm, ' '.join(JOBS)))
        JOBS[nm]()
        print('wrote', nm)
