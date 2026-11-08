"""Block textures: planetary rock and soil, ore overlays, machine casings and rocket hardware."""
import math

import numpy as np
from PIL import Image

from paint import hexrgb, vnoise, fbm, to_img, rng, Canvas


def _grid(n):
    ys, xs = np.mgrid[0:n, 0:n]
    return (xs + 0.5) / n, (ys + 0.5) / n


def shade_rgb(base, f):
    return np.clip(hexrgb(base)[None, None, :] * f[..., None], 0, 255)


def stepped(f, levels=6, lo=0.62, hi=1.28):
    """Quantise a brightness field into a few tones (pixel-art look)."""
    t = np.clip((f - lo) / (hi - lo), 0, 1)
    return lo + np.round(t * (levels - 1)) / (levels - 1) * (hi - lo)


# ------------------------------------------------------------------------------------- natural surfaces

def rock(n, base, speck, density, seed, style):
    u, v = _grid(n)
    r = rng(seed)
    big = fbm(n, f"{seed}a", 2, 3)
    small = vnoise(n, f"{seed}b", max(4, n // 2))
    f = 0.82 + (big - 0.5) * 0.42 + (small - 0.5) * 0.22
    rgb = shade_rgb(base, stepped(f))
    sp = hexrgb(speck)
    if style in ("regolith", "sand", "soil"):
        # fine grains plus a few pebbles lit from the top-left
        grains = vnoise(n, f"{seed}g", n) > 1 - density * 1.6
        rgb[grains] = rgb[grains] * 0.6 + sp * 0.4
        for _ in range(max(2, n // 6)):
            px, py, rr = r.random(), r.random(), (1.2 + r.random() * 1.6) / n
            d = np.hypot(u - px, v - py)
            peb = d < rr
            rgb[peb] = np.clip(sp * (1.1 - (u[peb] - px + v[peb] - py) * n * 0.08)[..., None], 0, 255)
            rim = (d >= rr) & (d < rr + 1.0 / n) & (u > px) & (v > py)
            rgb[rim] *= 0.7
        if style == "sand":
            ripple = (np.sin((v * 5 + u * 1.5 + big * 0.6) * math.pi * 2) > 0.7)
            rgb[ripple] *= 1.12
    elif style == "basalt":
        holes = vnoise(n, f"{seed}h", max(4, n // 3))
        ves = holes > 0.78
        rgb[ves] *= 0.55
        edge = (holes > 0.72) & ~ves
        rgb[edge] = rgb[edge] * 0.8 + sp * 0.2
    elif style == "crystal":
        # elongated plagioclase laths / olivine grains
        for _ in range(max(6, n // 2)):
            x0, y0 = r.random(), r.random()
            ang = r.random() * math.pi
            ln = (2 + r.random() * 3) / n
            for t in np.linspace(-ln, ln, n):
                xx, yy = int(((x0 + math.cos(ang) * t) % 1) * n), int(((y0 + math.sin(ang) * t) % 1) * n)
                rgb[yy, xx] = sp * (0.95 + r.random() * 0.15)
    elif style == "ice":
        rgb = shade_rgb(base, stepped(0.95 + (big - 0.5) * 0.25 + (small - 0.5) * 0.1, 5, 0.8, 1.15))
        for _ in range(2 if n < 32 else 4):
            x, y = r.random() * n, r.random() * n
            ang = r.random() * math.pi * 2
            for step in range(int(n * 0.7)):
                ang += (r.random() - 0.5) * 0.8
                x, y = (x + math.cos(ang)) % n, (y + math.sin(ang)) % n
                rgb[int(y), int(x)] = hexrgb(base) * 0.72
        bub = vnoise(n, f"{seed}bub", n) > 0.9
        rgb[bub] = np.minimum(rgb[bub] * 1.25, 255)
    elif style == "deposit":
        for _ in range(max(3, n // 4)):
            px, py, rr = r.random(), r.random(), (1.0 + r.random() * 1.5) / n * (2 if n >= 32 else 1.4)
            d = np.hypot(((u - px + 0.5) % 1) - 0.5, ((v - py + 0.5) % 1) - 0.5)
            nod = d < rr
            rgb[nod] = np.clip(sp * (1.15 - (d[nod] / rr) * 0.5)[..., None], 0, 255)
    elif style == "layered":
        bands = np.sin((v * 6 + big * 0.8) * math.pi * 2)
        rgb[bands > 0.55] *= 1.12
        rgb[bands < -0.6] *= 0.86
        grains = vnoise(n, f"{seed}g", n) > 1 - density
        rgb[grains] = rgb[grains] * 0.5 + sp * 0.5
    elif style == "frost":
        rgb = shade_rgb(base, stepped(1.0 + (small - 0.5) * 0.18, 4, 0.88, 1.12))
        fl = vnoise(n, f"{seed}f", n) > 0.82
        rgb[fl] = 255
    else:
        grains = vnoise(n, f"{seed}g", n) > 1 - density
        rgb[grains] = rgb[grains] * 0.55 + sp * 0.45
    return to_img(rgb)


def bricks(n, base, mortar, seed):
    u, v = _grid(n)
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    rows = 4
    row = np.floor(v * rows)
    offs = (row % 2) * 0.25
    col = np.floor((u + offs) * 2)
    tone = np.vectorize(lambda a, b: 0.88 + ((int(a) * 7 + int(b) * 13) % 5) * 0.06)(row, col)
    f = tone + (noise - 0.5) * 0.12
    rgb = shade_rgb(base, stepped(f, 5, 0.8, 1.15))
    gap = ((v * rows) % 1 < 1.0 / n * rows) | (((u + offs) * 2) % 1 < 1.0 / n * 2)
    rgb[gap] = hexrgb(mortar)
    return to_img(rgb)


def ore_overlay(n, c1, c2, seed, clusters=None):
    """Transparent crystal clusters drawn over the vanilla stone/deepslate texture by a two-layer model."""
    r = rng(seed)
    a, b = hexrgb(c1), hexrgb(c2)
    img = np.zeros((n, n, 4))
    clusters = clusters or (4 if n < 32 else 6)
    s = n / 16
    for _ in range(clusters):
        cx, cy = r.uniform(2, 13) * s, r.uniform(2, 13) * s
        pts = [(cx, cy)]
        for _ in range(int((3 + r.randint(0, 3)) * s)):
            x, y = pts[-1]
            pts.append((min(n - 1, max(0, x + r.choice((-1, 0, 1)))), min(n - 1, max(0, y + r.choice((-1, 0, 1))))))
        for i, (x, y) in enumerate(pts):
            x, y = int(x), int(y)
            hi = i == 0 or r.random() < 0.22
            col = b if hi else a * (0.9 + r.random() * 0.2)
            img[y, x, :3] = np.clip(col, 0, 255)
            img[y, x, 3] = 255
        for (x, y) in pts:
            x, y = int(x), int(y)
            if x + 1 < n and y + 1 < n and img[y + 1, x + 1, 3] == 0:
                img[y + 1, x + 1, :3] = a * 0.45
                img[y + 1, x + 1, 3] = 255
            if n >= 32 and x - 1 >= 0 and y - 1 >= 0 and img[y - 1, x - 1, 3] == 0 and r.random() < 0.5:
                img[y - 1, x - 1, :3] = np.clip(b * 1.1, 0, 255)
                img[y - 1, x - 1, 3] = 255
    return Image.fromarray(img.astype(np.uint8), 'RGBA')


def oil_sand(n, seed):
    u, v = _grid(n)
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    rgb = shade_rgb('#C8B07A', stepped(0.9 + (noise - 0.5) * 0.3, 5, 0.75, 1.1))
    tar = fbm(n, f"{seed}t", 3, 3) > 0.52
    rgb[tar] = hexrgb('#2A2420') * (0.8 + vnoise(n, f"{seed}s", n)[tar][..., None] * 0.5)
    shine = tar & (vnoise(n, f"{seed}q", n) > 0.85)
    rgb[shine] = hexrgb('#6A5E50')
    return to_img(rgb)


# ------------------------------------------------------------------------------------- hardware

def panel(n, base, seed, rivets=True, seam=True, grime=0.12):
    u, v = _grid(n)
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    f = 1.0 + (noise - 0.5) * grime
    rgb = shade_rgb(base, stepped(f, 5, 0.85, 1.12))
    e = 1
    rgb[:e, :] = hexrgb(base) * 1.32
    rgb[:, :e] = hexrgb(base) * 1.25
    rgb[-e:, :] = hexrgb(base) * 0.55
    rgb[:, -e:] = hexrgb(base) * 0.62
    if seam:
        m = n // 2
        rgb[m, 1:-1] = hexrgb(base) * 0.72
        rgb[m + 1, 1:-1] = hexrgb(base) * 1.12
    if rivets:
        k = max(2, n // 8)
        for (x, y) in ((k, k), (n - k - 1, k), (k, n - k - 2), (n - k - 1, n - k - 2)):
            rgb[y, x] = hexrgb(base) * 1.55
            rgb[y + 1, x] = hexrgb(base) * 0.5
    return rgb


GLYPHS = {
    'flame': ["...#....", "...##...", "..###...", "..####..", ".##+###.", ".#+++##.", ".##++##.", "..####.."],
    'bolt': ["....##..", "...##...", "..##....", ".######.", "....##..", "...##...", "..##....", ".#......"],
    'drop': ["...##...", "...##...", "..####..", "..####..", ".######.", ".##+###.", ".######.", "..####.."],
    'flask': ["..###...", "...#....", "...#....", "..###...", ".#####..", ".#+++#..", "#+++++#.", "#######."],
    'gear': ["..#..#..", ".######.", "##+..+##", ".#....#.", ".#....#.", "##+..+##", ".######.", "..#..#.."],
    'chip': [".#.#.#..", "######..", "#+++#...", "#+++##..", "#+++#...", "######..", ".#.#.#..", "........"],
    'wave': ["........", ".##..##.", "#..##..#", "........", ".##..##.", "#..##..#", "........", "........"],
    'atom': ["...##...", ".#.##.#.", "#..##..#", ".######.", ".######.", "#..##..#", ".#.##.#.", "...##..."],
    'sun': ["#..#..#.", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "#..#..#.", "........"],
    'grid': ["########", "#+#+#+#+", "########", "#+#+#+#+", "########", "#+#+#+#+", "########", "#+#+#+#+"],
    'rocket': ["...#....", "..###...", "..#+#...", "..###...", "..###...", ".#####..", "#.###.#.", "..#+#..."],
    'o2': [".##.....", "#..#....", "#..#....", ".##.##..", "......#.", ".....#..", "....###.", "........"],
    'spin': ["..####..", ".#....#.", "#..##..#", "#.#++#.#", "#.#++#.#", "#..##..#", ".#....#.", "..####.."],
    'print': ["########", "#..#...#", "...#....", "...#....", "..###...", ".#####..", "########", "#++++++#"],
    'arm': ["#.......", "##......", ".##.....", "..##..#.", "...####.", "....##..", "...####.", "..######"],
}


def machine_faces(n, casing, accent, glyph, seed):
    """Returns side, top, front, front_on textures for a machine."""
    side = panel(n, casing, f"{seed}s")
    # vent slots on the side
    for k in range(3):
        y = int(n * (0.62 + k * 0.08))
        side[y, int(n * 0.25):int(n * 0.75)] = hexrgb(casing) * 0.45
    top = panel(n, casing, f"{seed}t", seam=False)
    c = n // 2
    rr = n * 0.28
    u, v = _grid(n)
    fan = np.hypot(u - 0.5, v - 0.5) * n < rr
    top[fan] = hexrgb(casing) * 0.5
    blades = fan & (np.cos(np.arctan2(v - 0.5, u - 0.5) * 4) > 0.3)
    top[blades] = hexrgb(casing) * 0.95
    top[c, c] = hexrgb(accent)

    def front(lit):
        f = panel(n, casing, f"{seed}f", seam=False)
        s = n / 16
        x0, y0, x1, y1 = int(3 * s), int(3 * s), int(13 * s), int(13 * s)
        f[y0:y1, x0:x1] = hexrgb('#1C2026')
        f[y0, x0:x1] = hexrgb('#0C0E12')
        f[y0:y1, x0] = hexrgb('#0C0E12')
        f[y1 - 1, x0:x1] = hexrgb('#3A404A')
        g = GLYPHS[glyph]
        acc = hexrgb(accent) * (1.25 if lit else 0.75)
        cell = max(1, int(s))
        for gy, row in enumerate(g):
            for gx, ch in enumerate(row):
                if ch == '.':
                    continue
                col = acc if ch == '#' else acc * 0.6
                yy, xx = y0 + int(1 * s) + gy * cell, x0 + int(1 * s) + gx * cell
                f[yy:yy + cell, xx:xx + cell] = np.clip(col, 0, 255)
        # status LEDs
        led = hexrgb('#40FF60') if lit else hexrgb('#305030')
        f[int(14 * s), int(3 * s):int(3 * s) + cell] = led
        f[int(14 * s), int(5 * s):int(5 * s) + cell] = hexrgb('#FFB040') if lit else hexrgb('#504030')
        return f

    return to_img(side), to_img(top), to_img(front(False)), to_img(front(True))


def tank_side(n, base, band, seed):
    u, v = _grid(n)
    cyl = 0.82 + 0.3 * np.cos((u - 0.38) * math.pi * 1.2)
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    rgb = shade_rgb(base, stepped(cyl + (noise - 0.5) * 0.06, 6, 0.7, 1.15))
    s = n / 16
    for yy in (int(1 * s), int(15 * s) - 1):
        rgb[yy, :] = hexrgb(base) * 0.62
    rgb[int(7 * s):int(9 * s), :] = shade_rgb(band, cyl)[int(7 * s):int(9 * s), :]
    # weld seam and stencil marks
    rgb[:, int(11 * s)] = hexrgb(base) * 0.82
    for k in range(3):
        rgb[int(4 * s), int((4 + k * 2) * s)] = hexrgb('#30343A')
    return to_img(rgb)


def tank_cap(n, base, seed):
    u, v = _grid(n)
    d = np.hypot(u - 0.5, v - 0.5)
    rgb = shade_rgb(base, stepped(1.12 - d * 0.6 - (u + v - 1) * 0.2, 5, 0.7, 1.2))
    ring = np.abs(d - 0.38) < 0.7 / n
    rgb[ring] = hexrgb(base) * 0.6
    port = d < 0.08
    rgb[port] = hexrgb('#50545C')
    return to_img(rgb)


def stripes(n, a, b, seed):
    u, v = _grid(n)
    band = ((u + v) * 4) % 1 < 0.5
    rgb = np.where(band[..., None], hexrgb(a)[None, None, :], hexrgb(b)[None, None, :]).astype(float)
    rgb[:max(1, n // 8), :] = hexrgb('#5A5A60')
    rgb[-max(1, n // 8):, :] = hexrgb('#3A3A40')
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    rgb *= (0.92 + noise * 0.12)[..., None]
    return to_img(rgb)


def truss(n, base, seed):
    img = np.zeros((n, n, 4))
    c = hexrgb(base)
    t = max(1, n // 8)
    for i in range(n):
        for w in range(t):
            for (x, y) in ((i, w), (i, n - 1 - w), (w, i), (n - 1 - w, i), (i, min(n - 1, i + w)), (i, max(0, n - 1 - i - w))):
                img[y, x, :3] = c * (1.15 if (x + y) < n else 0.8)
                img[y, x, 3] = 255
    return Image.fromarray(img.astype(np.uint8), 'RGBA')


def nozzle_tex(n, base, seed):
    u, v = _grid(n)
    heat = np.clip(v * 1.2 - 0.1, 0, 1)
    cyl = 0.8 + 0.3 * np.cos((u - 0.38) * math.pi * 1.2)
    rgb = shade_rgb(base, cyl * (1 - heat * 0.35))
    glow = hexrgb('#5A2A18') * heat[..., None] * 0.6
    rgb = np.clip(rgb * (1 - heat[..., None] * 0.4) + glow, 0, 255)
    for k in range(1, 4):
        rgb[int(n * k / 4), :] *= 0.75
    return to_img(rgb)


def capsule_side(n, base, window, seed):
    rgb = panel(n, base, seed, rivets=False, seam=False)
    s = n / 16
    u, v = _grid(n)
    win = (np.abs(u - 0.5) < 0.18) & (np.abs(v - 0.42) < 0.14)
    rgb[win] = hexrgb(window) * (0.8 + (1 - v[win]) * 0.5)[..., None]
    rgb[win & (u < 0.42) & (v < 0.38)] = hexrgb('#A0C8E8')
    rgb[int(12 * s), :] = hexrgb('#C03020')
    rgb[int(13 * s), :] = hexrgb('#C03020') * 0.8
    return to_img(rgb)


def ablative(n, base, seed):
    u, v = _grid(n)
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    rgb = shade_rgb(base, stepped(0.95 + (noise - 0.5) * 0.3, 5, 0.75, 1.15))
    tiles = 4 if n < 32 else 6
    gap = ((u * tiles) % 1 < 1.0 / n * tiles) | ((v * tiles + (np.floor(u * tiles) % 2) * 0.5) % 1 < 1.0 / n * tiles)
    rgb[gap] = hexrgb(base) * 0.45
    return to_img(rgb)


def pad_top(n, seed):
    u, v = _grid(n)
    noise = vnoise(n, f"{seed}", max(4, n // 2))
    rgb = shade_rgb('#8A8A88', stepped(0.95 + (noise - 0.5) * 0.25, 5, 0.75, 1.12))
    burn = fbm(n, f"{seed}b", 2, 3)
    rgb[burn > 0.58] *= 0.72
    k = max(1, n // 8)
    edge = (u * n < k) | (u * n >= n - k) | (v * n < k) | (v * n >= n - k)
    hazard = ((u + v) * n / k / 2).astype(int) % 2 == 0
    rgb[edge & hazard] = hexrgb('#E0C040')
    rgb[edge & ~hazard] = hexrgb('#2A2A2E')
    grate = (np.abs(u - 0.5) < 0.2) & (np.abs(v - 0.5) < 0.2) & ((v * n).astype(int) % 2 == 0)
    rgb[grate] = hexrgb('#3A3A3E')
    return to_img(rgb)


def console_front(n, seed):
    rgb = panel(n, '#4A5058', seed, seam=False)
    s = n / 16
    x0, y0, x1, y1 = int(2 * s), int(2 * s), int(14 * s), int(10 * s)
    rgb[y0:y1, x0:x1] = hexrgb('#0A1A12')
    r = rng(seed)
    for row in range(y0 + 1, y1 - 1, max(1, int(2 * s))):
        ln = r.randint(int(3 * s), int(10 * s))
        rgb[row, x0 + 1:x0 + 1 + ln] = hexrgb('#40FF80') * (0.7 + r.random() * 0.4)
    for k in range(4):
        rgb[int(12 * s):int(13 * s) + 1, int((3 + k * 3) * s):int((4 + k * 3) * s) + 1] = hexrgb(['#E04040', '#40E060', '#E0C040', '#4080FF'][k])
    return to_img(rgb)


def box_face(n, base, accent, seed, icon=None):
    rgb = panel(n, base, seed, seam=False)
    s = n / 16
    rgb[int(6 * s):int(10 * s), int(2 * s):int(14 * s)] = hexrgb(accent) * 0.9
    rgb[int(6 * s), int(2 * s):int(14 * s)] = hexrgb(accent) * 1.2
    for k in range(5):
        rgb[int(12 * s), int((3 + k * 2) * s)] = hexrgb('#30343A')
    return to_img(rgb)


def plasma_strip(n, frames=16):
    strip = np.zeros((n * frames, n, 4))
    u, v = _grid(n)
    for f in range(frames):
        t = f / frames * 2 * math.pi
        val = (np.sin(u * 9.0 + t) + np.sin(v * 8.0 - t * 1.3) + np.sin((u + v) * 5.5 + t * 0.7)
               + np.sin(np.hypot(u - 0.5, v - 0.5) * 11 - t)) / 4
        cells = vnoise(n, f"plasma{f % 4}", max(4, n // 4))
        val = (val + 1) / 2 * 0.75 + cells * 0.25
        hot = np.array([255, 252, 228.0])
        cool = np.array([255, 128, 24.0])
        rgb = cool[None, None, :] * (1 - val[..., None] ** 1.4) + hot[None, None, :] * val[..., None] ** 1.4
        dark = val < 0.22
        rgb[dark] = rgb[dark] * 0.82
        strip[f * n:(f + 1) * n, :, :3] = rgb
        strip[f * n:(f + 1) * n, :, 3] = 255
    return Image.fromarray(strip.astype(np.uint8), 'RGBA')


def suit_layer(w, color, accent, legs=False):
    """Humanoid armour texture (64x32 or 128x64) following the player-model UV layout."""
    s = w // 64
    img = np.zeros((32 * s, 64 * s, 4))
    base = hexrgb(color)
    acc = hexrgb(accent)

    def box(x, y, wd, h, d, fn):
        # faces: top, bottom, right, front, left, back
        faces = {'top': (x + d, y, wd, d), 'bottom': (x + d + wd, y, wd, d), 'right': (x, y + d, d, h),
                 'front': (x + d, y + d, wd, h), 'left': (x + d + wd, y + d, d, h), 'back': (x + d + wd + d, y + d, wd, h)}
        for name, (fx, fy, fw, fh) in faces.items():
            for yy in range(fy * s, (fy + fh) * s):
                for xx in range(fx * s, (fx + fw) * s):
                    lu, lv = (xx - fx * s + 0.5) / (fw * s), (yy - fy * s + 0.5) / (fh * s)
                    img[yy, xx, :3] = np.clip(fn(name, lu, lv), 0, 255)
                    img[yy, xx, 3] = 255

    noise = vnoise(16 * s, "suit", 8)

    def fabric(name, lu, lv, tint=1.0):
        n = noise[int(lv * 15.99 * s) % (16 * s), int(lu * 15.99 * s) % (16 * s)]
        f = 0.9 + n * 0.14 + (0.06 if name in ('top', 'front') else -0.04 if name == 'back' else 0)
        return base * f * tint

    if not legs:
        def head(name, lu, lv):
            if name == 'front' and 0.12 < lu < 0.88 and 0.25 < lv < 0.72:
                g = 0.55 + (1 - lv) * 0.6
                gold = np.array([200, 160, 64.0]) * g
                if lu < 0.4 and lv < 0.45:
                    gold = np.array([255, 240, 190.0])
                return gold
            if lv > 0.86:
                return acc * 0.9
            return fabric(name, lu, lv, 1.05)

        box(0, 0, 8, 8, 8, head)

        def body(name, lu, lv):
            if name == 'front' and 0.2 < lu < 0.8 and 0.15 < lv < 0.45:
                c = np.array([120, 128, 140.0])
                if 0.3 < lu < 0.4 and 0.25 < lv < 0.35:
                    return np.array([64, 224, 96.0])
                if 0.6 < lu < 0.7 and 0.25 < lv < 0.35:
                    return np.array([224, 64, 64.0])
                return c
            if name == 'back':
                return np.array([150, 156, 168.0]) * (0.9 + 0.1 * (lv < 0.5))
            if 0.55 < lv < 0.62:
                return acc
            return fabric(name, lu, lv)

        box(16, 16, 8, 12, 4, body)

        def arm(name, lu, lv):
            if 0.3 < lv < 0.38:
                return acc
            if lv > 0.8:
                return np.array([90, 96, 104.0])
            return fabric(name, lu, lv)

        box(40, 16, 4, 12, 4, arm)

    def leg(name, lu, lv):
        if legs:
            if 0.35 < lv < 0.48 and name == 'front':
                return np.array([176, 180, 188.0])
            if lv < 0.12:
                return acc
            return fabric(name, lu, lv)
        # boots in the main layer
        if lv > 0.62:
            if lv > 0.88:
                return np.array([60, 64, 70.0])
            return fabric(name, lu, lv, 0.92)
        return np.array([0, 0, 0.0])

    if legs:
        box(0, 16, 4, 12, 4, leg)

        def waist(name, lu, lv):
            return acc if lv < 0.25 else fabric(name, lu, lv)
        box(16, 16, 8, 12, 4, waist)
    else:
        box(0, 16, 4, 12, 4, leg)
        # keep only boot pixels on the leg region
        reg = img[16 * s:32 * s, 0:16 * s]
        blank = reg[..., :3].sum(axis=2) == 0
        reg[blank, 3] = 0
    return Image.fromarray(img.astype(np.uint8), 'RGBA')



def stargate(n):
    """Dark alloy frame with a glowing event-horizon ring and chevrons."""
    import numpy as np
    img = panel(n, '#20283A', 7, rivets=True, seam=False, grime=0.08)
    a = np.array(img).astype(float)
    yy, xx = np.mgrid[0:n, 0:n]
    d = np.sqrt((xx - (n - 1) / 2) ** 2 + (yy - (n - 1) / 2) ** 2) / (n / 2)
    ring = np.exp(-((d - 0.68) / 0.07) ** 2)
    inner = np.clip(1 - d / 0.55, 0, 1) ** 1.5
    a[..., 0] += ring * 40 + inner * 30
    a[..., 1] += ring * 160 + inner * 120
    a[..., 2] += ring * 230 + inner * 200
    for k in range(8):
        ang = k * np.pi / 4
        cx, cy = int((n - 1) / 2 + np.cos(ang) * n * 0.42), int((n - 1) / 2 + np.sin(ang) * n * 0.42)
        a[max(0, cy - 1):cy + 1, max(0, cx - 1):cx + 1, :3] = [255, 170, 60]
    from PIL import Image
    return Image.fromarray(np.clip(a, 0, 255).astype('uint8'), 'RGBA' if a.shape[2] == 4 else 'RGB')



def silo(n):
    """Planetary-defence silo hatch: grey armour with hazard stripes and a central hatch."""
    import numpy as np
    from PIL import Image
    a = np.array(panel(n, '#5A6068', 11, rivets=True, seam=True)).astype(float)
    yy, xx = np.mgrid[0:n, 0:n]
    stripe = (((xx + yy) // max(1, n // 8)) % 2 == 0) & ((xx < n * 0.12) | (xx > n * 0.88) | (yy < n * 0.12) | (yy > n * 0.88))
    a[stripe, 0] = 230; a[stripe, 1] = 180; a[stripe, 2] = 40
    d = np.sqrt((xx - (n - 1) / 2) ** 2 + (yy - (n - 1) / 2) ** 2)
    hatch = d < n * 0.28
    a[hatch, :3] = a[hatch, :3] * 0.55
    a[(d > n * 0.26) & (d < n * 0.3), :3] = [200, 60, 50]
    return Image.fromarray(np.clip(a, 0, 255).astype('uint8'))
