"""Resolution-independent pixel-art painter for Heliosphere textures (16x16 built in, 32x32 HD pack).

Shapes are described in unit coordinates and rasterised at N pixels; lighting comes from the top-left,
each material snaps to a small colour ramp, and outlines/highlights are added afterwards, which keeps the
classic Minecraft look at any resolution.
"""
import math
import random
import zlib

import numpy as np
from PIL import Image

RAMP = [0.42, 0.58, 0.74, 0.88, 1.0, 1.14, 1.3, 1.48]


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def snap(f):
    return min(RAMP, key=lambda r: abs(r - f))


class Canvas:
    def __init__(self, n):
        self.n = n
        self.rgb = np.zeros((n, n, 3))
        self.a = np.zeros((n, n))
        self.mat = np.full((n, n), -1, dtype=int)
        self.colors = []

    # ---------------------------------------------------------------- rasterisation helpers
    def _grid(self):
        n = self.n
        ys, xs = np.mgrid[0:n, 0:n]
        return (xs + 0.5) / n, (ys + 0.5) / n

    def material(self, color):
        self.colors.append(hexrgb(color) if isinstance(color, str) else np.array(color, dtype=float))
        return len(self.colors) - 1

    def paint(self, mask, color, shade=1.0, quant=True):
        """mask: bool array; shade: scalar or array of brightness factors."""
        m = self.material(color)
        sh = np.broadcast_to(np.asarray(shade, dtype=float), mask.shape)
        base = self.colors[m]
        for y, x in zip(*np.nonzero(mask)):
            f = snap(sh[y, x]) if quant else sh[y, x]
            self.rgb[y, x] = np.clip(base * f, 0, 255)
            self.a[y, x] = 1
            self.mat[y, x] = m

    def poly(self, pts, color, shade=1.0):
        u, v = self._grid()
        inside = np.zeros(u.shape, dtype=bool)
        k = len(pts)
        for i in range(k):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % k]
            cond = ((y1 > v) != (y2 > v)) & (u < (x2 - x1) * (v - y1) / ((y2 - y1) + 1e-12) + x1)
            inside ^= cond
        self.paint(inside, color, shade)
        return inside

    def ellipse(self, cx, cy, rx, ry, color, shade=1.0):
        u, v = self._grid()
        inside = ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 <= 1
        self.paint(inside, color, shade)
        return inside

    def rect(self, x0, y0, x1, y1, color, shade=1.0):
        u, v = self._grid()
        inside = (u >= x0) & (u < x1) & (v >= y0) & (v < y1)
        self.paint(inside, color, shade)
        return inside

    def px(self, x, y, color, f=1.0):
        """Single pixel in unit coordinates."""
        n = self.n
        i, j = int(x * n), int(y * n)
        if 0 <= i < n and 0 <= j < n:
            self.rgb[j, i] = np.clip(hexrgb(color) * f, 0, 255)
            self.a[j, i] = 1

    def grid(self):
        return self._grid()

    # ---------------------------------------------------------------- post effects
    def outline(self, dark=0.45):
        n = self.n
        out = self.rgb.copy()
        for y in range(n):
            for x in range(n):
                if self.a[y, x] == 0:
                    continue
                edge = False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < n and 0 <= yy < n) or self.a[yy, xx] == 0:
                        edge = True
                if edge:
                    # top-left edges catch the light, bottom-right edges sink into shadow
                    lit = (y > 0 and self.a[y - 1, x] == 0) or (x > 0 and self.a[y, x - 1] == 0)
                    shadow = (y < n - 1 and self.a[y + 1, x] == 0) or (x < n - 1 and self.a[y, x + 1] == 0)
                    if shadow:
                        out[y, x] = self.rgb[y, x] * dark
                    elif lit:
                        out[y, x] = np.clip(self.rgb[y, x] * 1.18, 0, 255)
        self.rgb = out

    def image(self):
        img = np.zeros((self.n, self.n, 4), dtype=np.uint8)
        img[..., :3] = np.clip(self.rgb, 0, 255).astype(np.uint8)
        img[..., 3] = (self.a * 255).astype(np.uint8)
        return Image.fromarray(img, 'RGBA')


def rng(seed):
    return random.Random(seed)


def vnoise(n, seed, cells):
    """Tileable value noise in [0,1] at resolution n with `cells` lattice cells."""
    r = np.random.RandomState(zlib.crc32(str(seed).encode()) % (2 ** 31))
    g = r.rand(cells, cells)
    ys, xs = np.mgrid[0:n, 0:n]
    fx, fy = (xs + 0.5) / n * cells, (ys + 0.5) / n * cells
    ix, iy = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[iy % cells, ix % cells] * (1 - tx) + g[iy % cells, (ix + 1) % cells] * tx
    b = g[(iy + 1) % cells, ix % cells] * (1 - tx) + g[(iy + 1) % cells, (ix + 1) % cells] * tx
    return a * (1 - ty) + b * ty


def fbm(n, seed, base=2, octaves=3):
    out = np.zeros((n, n))
    amp, tot = 1.0, 0
    for o in range(octaves):
        out += vnoise(n, f"{seed}/{o}", base * 2 ** o) * amp
        tot += amp
        amp *= 0.5
    return out / tot


def to_img(rgb, alpha=None):
    n = rgb.shape[0]
    img = np.zeros((n, n, 4), dtype=np.uint8)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    img[..., 3] = 255 if alpha is None else (np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


def quantize(rgb, base, levels=RAMP):
    """Snap a shaded image back onto the material's colour ramp for a crisp pixel-art look."""
    b = np.maximum(base.reshape(1, 1, 3), 1)
    f = (rgb / b).mean(axis=2)
    snapped = np.vectorize(snap)(f)
    return np.clip(b * snapped[..., None], 0, 255)
