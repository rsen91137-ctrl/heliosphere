"""Item icons painted at any resolution (16 or 32)."""
import math

import numpy as np

from paint import Canvas, hexrgb, vnoise, rng


def lighter(c, f):
    b = hexrgb(c) * f
    b = np.clip(b, 0, 255)
    return '#%02X%02X%02X' % tuple(int(x) for x in b)


# --------------------------------------------------------------------------------------- generic shapes

def ingot(n, color, accent=None, seed=0):
    c = Canvas(n)
    top = [(0.30, 0.26), (0.76, 0.26), (0.90, 0.44), (0.16, 0.44)]
    front = [(0.16, 0.44), (0.90, 0.44), (0.84, 0.74), (0.10, 0.74)]
    c.poly(front, color, 0.9)
    c.poly(top, color, 1.25)
    c.poly([(0.76, 0.26), (0.90, 0.44), (0.84, 0.74), (0.92, 0.6)], color, 0.66)
    u, v = c.grid()
    c.paint((v > 0.45) & (v < 0.5) & (u > 0.2) & (u < 0.86), color, 1.08)
    # stamped mark and glint
    c.paint((abs(u - 0.5) < 0.1) & (abs(v - 0.6) < 0.045), color, 0.74)
    c.px(0.36, 0.31, '#FFFFFF', 0.95)
    if n >= 32:
        c.px(0.40, 0.31, '#FFFFFF', 0.8)
        c.px(0.62, 0.34, '#FFFFFF', 0.6)
    c.outline()
    return c.image()


def plate(n, color, accent=None, seed=0):
    c = Canvas(n)
    top = [(0.16, 0.30), (0.74, 0.22), (0.88, 0.62), (0.28, 0.72)]
    c.poly([(0.28, 0.72), (0.88, 0.62), (0.88, 0.68), (0.28, 0.78)], color, 0.62)
    c.poly(top, color, 1.08)
    u, v = c.grid()
    # brushed lines across the plate
    for k in range(4):
        y0 = 0.33 + k * 0.1
        c.paint((abs(v - (y0 + (u - 0.2) * -0.12)) < 0.012) & (u > 0.24) & (u < 0.8), color, 1.25 if k % 2 == 0 else 0.92)
    if accent:
        c.ellipse(0.52, 0.47, 0.09, 0.07, accent, 1.0)
    for (x, y) in ((0.24, 0.34), (0.70, 0.27), (0.80, 0.60), (0.33, 0.67)):
        c.px(x, y, lighter(color, 0.55))
    c.outline()
    return c.image()


def raw(n, color, accent=None, seed=0):
    c = Canvas(n)
    r = rng(seed)
    u, v = c.grid()
    blob = np.zeros_like(u, dtype=bool)
    for (cx, cy, rr) in ((0.48, 0.52, 0.28), (0.36, 0.46, 0.2), (0.62, 0.58, 0.2), (0.52, 0.38, 0.17)):
        cx += (r.random() - 0.5) * 0.06
        cy += (r.random() - 0.5) * 0.06
        blob |= ((u - cx) ** 2 + (v - cy) ** 2) <= rr ** 2
    noise = vnoise(n, f"raw{seed}", 4)
    shade = 1.25 - (u * 0.5 + v * 0.6) + (noise - 0.5) * 0.35
    c.paint(blob, color, shade + 0.25)
    if accent:
        acc = (vnoise(n, f"acc{seed}", 6) > 0.62) & blob & (u + v < 1.25)
        c.paint(acc, accent, 1.1 + (noise - 0.5) * 0.3)
    c.outline()
    return c.image()


def gem(n, color, accent=None, seed=0):
    c = Canvas(n)
    t, m, b = 0.18, 0.42, 0.86
    l, r_, cl, cr = 0.22, 0.78, 0.38, 0.62
    c.poly([(cl, t), (cr, t), (r_, m), (0.5, b), (l, m)], color, 1.0)
    c.poly([(cl, t), (0.5, m), (l, m)], color, 1.42)
    c.poly([(cl, t), (cr, t), (0.5, m)], color, 1.28)
    c.poly([(cr, t), (r_, m), (0.5, m)], color, 1.0)
    c.poly([(l, m), (0.5, m), (0.5, b)], color, 0.86)
    c.poly([(0.5, m), (r_, m), (0.5, b)], color, 0.62)
    c.px(0.42, 0.25, '#FFFFFF')
    if n >= 32:
        c.px(0.45, 0.25, '#FFFFFF', 0.85)
        c.px(0.40, 0.30, '#FFFFFF', 0.7)
    if accent:
        c.px(0.56, 0.62, accent)
        c.px(0.34, 0.52, accent, 0.9)
    c.outline(0.5)
    return c.image()


def dust(n, color, accent=None, seed=0):
    c = Canvas(n)
    u, v = c.grid()
    mound = (v > 0.42 + 0.9 * (u - 0.5) ** 2 * 1.6) & (v < 0.82) & (abs(u - 0.5) < 0.38)
    noise = vnoise(n, f"dust{seed}", 8)
    shade = 1.3 - (v - 0.4) * 0.9 - (u - 0.3) * 0.25 + (noise - 0.5) * 0.4
    c.paint(mound, color, shade)
    if accent:
        c.paint(mound & (noise > 0.72), accent, 1.1)
    for (x, y) in ((0.18, 0.78), (0.84, 0.76), (0.76, 0.5), (0.24, 0.6)):
        c.px(x, y, color, 0.9)
    c.outline(0.55)
    return c.image()


def canister(n, body, band, seed=0):
    c = Canvas(n)
    u, v = c.grid()
    cyl = lambda: 0.55 + 0.75 * np.cos((u - 0.42) * math.pi * 1.9)
    c.rect(0.3, 0.26, 0.7, 0.86, body, cyl())
    c.ellipse(0.5, 0.86, 0.2, 0.05, body, 0.6)
    c.ellipse(0.5, 0.26, 0.2, 0.06, body, 1.35)
    c.rect(0.3, 0.44, 0.7, 0.56, band, cyl() * 1.05)
    c.rect(0.44, 0.12, 0.56, 0.24, '#5A5E66', 1.0)
    c.rect(0.36, 0.1, 0.64, 0.14, '#C04040', 1.0)
    if n >= 32:
        c.rect(0.36, 0.62, 0.64, 0.64, '#30343A', 0.9)
        c.rect(0.36, 0.67, 0.56, 0.69, '#30343A', 0.9)
        c.px(0.36, 0.3, '#FFFFFF', 0.9)
    c.outline()
    return c.image()


def cylinder(n, color, accent=None, seed=0):
    c = Canvas(n)
    u, v = c.grid()
    cyl = 0.55 + 0.75 * np.cos((u - 0.42) * math.pi * 1.6)
    c.rect(0.24, 0.24, 0.76, 0.8, color, cyl)
    c.ellipse(0.5, 0.8, 0.26, 0.07, color, 0.6)
    c.ellipse(0.5, 0.24, 0.26, 0.08, color, 1.3)
    c.ellipse(0.5, 0.24, 0.12, 0.04, lighter(color, 0.6), 1.0)
    if accent:
        c.rect(0.24, 0.42, 0.76, 0.5, accent, cyl)
        c.rect(0.24, 0.6, 0.76, 0.64, accent, cyl * 0.9)
    c.outline()
    return c.image()


def chip(n, color, accent=None, seed=0):
    c = Canvas(n)
    pins = 5 if n >= 32 else 4
    for k in range(pins):
        x = 0.28 + k * (0.44 / (pins - 1))
        c.rect(x - 0.025, 0.12, x + 0.025, 0.24, '#C8CCD4', 1.1)
        c.rect(x - 0.025, 0.76, x + 0.025, 0.88, '#C8CCD4', 0.9)
        c.rect(0.12, x - 0.025, 0.24, x + 0.025, '#C8CCD4', 1.0)
        c.rect(0.76, x - 0.025, 0.88, x + 0.025, '#C8CCD4', 0.85)
    c.rect(0.22, 0.22, 0.78, 0.78, color, 1.0)
    c.rect(0.22, 0.22, 0.78, 0.26, color, 1.3)
    c.rect(0.36, 0.36, 0.64, 0.64, '#B8BCC4', 1.1)
    if accent:
        c.rect(0.4, 0.4, 0.6, 0.6, accent, 1.15)
        c.rect(0.44, 0.44, 0.5, 0.5, '#FFFFFF', 0.9)
    c.px(0.27, 0.71, '#FFFFFF', 0.7)
    c.outline(0.5)
    return c.image()


def board(n, color, accent=None, seed=0):
    c = Canvas(n)
    c.rect(0.12, 0.16, 0.88, 0.84, color, 1.0)
    c.rect(0.12, 0.16, 0.88, 0.2, color, 1.3)
    u, v = c.grid()
    gold = accent or '#E0C050'
    r = rng(seed)
    for k in range(4 if n < 32 else 7):
        y = 0.26 + k * (0.5 / (4 if n < 32 else 7))
        x0 = 0.16 + r.random() * 0.2
        x1 = x0 + 0.25 + r.random() * 0.35
        c.paint((abs(v - y) < 0.5 / n) & (u > x0) & (u < min(0.84, x1)), '#C89040', 1.0)
    c.rect(0.5, 0.36, 0.74, 0.62, '#26262C', 1.0)
    c.rect(0.5, 0.36, 0.74, 0.39, '#26262C', 1.4)
    for (x, y) in ((0.2, 0.74), (0.3, 0.74), (0.4, 0.74), (0.2, 0.3), (0.8, 0.72)):
        c.px(x, y, gold, 1.1)
    c.outline(0.5)
    return c.image()


def gear(n, color, accent=None, seed=0):
    c = Canvas(n)
    u, v = c.grid()
    du, dv = u - 0.5, v - 0.5
    rr = np.sqrt(du * du + dv * dv)
    ang = np.arctan2(dv, du)
    teeth = 8
    tooth = (np.cos(ang * teeth) > 0.25)
    outer = (rr < 0.40) & ((rr < 0.31) | tooth)
    hole = rr < 0.11
    shade = 1.15 - (du + dv) * 0.8
    c.paint(outer & ~hole, color, shade)
    ring = (rr > 0.16) & (rr < 0.21)
    c.paint(ring, color, shade * 0.75)
    if accent:
        c.paint(hole & (rr > 0.06), accent, 1.0)
    c.outline(0.5)
    return c.image()


def rod(n, color, accent=None, seed=0):
    """Coil of wire."""
    c = Canvas(n)
    for k in range(5):
        cx, cy = 0.34 + k * 0.08, 0.66 - k * 0.08
        c.ellipse(cx, cy, 0.2, 0.08, color, 1.25 - k * 0.04)
        c.ellipse(cx, cy, 0.14, 0.04, color, 0.55)
    c.rect(0.68, 0.18, 0.84, 0.22, color, 1.2)
    c.outline(0.5)
    return c.image()


def nozzle(n, color, accent=None, seed=0):
    c = Canvas(n)
    u, v = c.grid()
    half = 0.08 + np.clip(v - 0.12, 0, 1) ** 1.6 * 0.55
    bell = (v > 0.12) & (v < 0.86) & (abs(u - 0.5) < half)
    cyl = 0.6 + 0.65 * np.cos((u - 0.42) * math.pi * 1.4)
    heat = np.clip((v - 0.5) * 1.6, 0, 1)
    c.paint(bell, color, cyl - heat * 0.15)
    for k in range(3):
        y = 0.35 + k * 0.17
        hw = 0.08 + (y - 0.12) ** 1.6 * 0.55
        c.paint((abs(v - y) < 0.5 / n) & (abs(u - 0.5) < hw), color, 0.6)
    c.ellipse(0.5, 0.86, 0.38, 0.05, '#2A2020', 1.0)
    c.ellipse(0.5, 0.86, 0.3, 0.03, accent or '#FF8030', 1.1)
    c.rect(0.42, 0.06, 0.58, 0.14, '#7A7E86', 1.1)
    c.outline(0.5)
    return c.image()


def scroll(n, color='#E8D8A8', accent='#5A3A1E', seed=0):
    c = Canvas(n)
    c.rect(0.2, 0.18, 0.8, 0.82, color, 1.05)
    u, v = c.grid()
    for k in range(5):
        y = 0.3 + k * 0.11
        c.paint((abs(v - y) < 0.5 / n) & (u > 0.27) & (u < 0.73 - (k % 2) * 0.12), '#6A5030', 0.9)
    c.rect(0.12, 0.12, 0.88, 0.2, accent, 1.0)
    c.rect(0.12, 0.8, 0.88, 0.88, accent, 0.85)
    c.ellipse(0.12, 0.16, 0.05, 0.05, '#C8A040', 1.1)
    c.ellipse(0.88, 0.16, 0.05, 0.05, '#C8A040', 1.1)
    c.ellipse(0.12, 0.84, 0.05, 0.05, '#C8A040', 0.9)
    c.ellipse(0.88, 0.84, 0.05, 0.05, '#C8A040', 0.9)
    c.ellipse(0.5, 0.62, 0.08, 0.08, '#4FC3F7', 1.1)
    c.outline(0.5)
    return c.image()


# --------------------------------------------------------------------------------------- special icons

def turbine_blade(n, color, accent=None, seed=0):
    c = Canvas(n)
    c.poly([(0.30, 0.82), (0.42, 0.82), (0.70, 0.22), (0.62, 0.14), (0.48, 0.2)], color, 1.1)
    c.poly([(0.42, 0.82), (0.70, 0.22), (0.74, 0.3), (0.5, 0.82)], color, 0.72)
    c.rect(0.24, 0.78, 0.56, 0.9, '#7C7068', 1.0)
    c.outline()
    return c.image()


def magnet(n, color, accent='#C04040', seed=0):
    c = Canvas(n)
    u, v = c.grid()
    du, dv = u - 0.5, v - 0.48
    rr = np.sqrt(du * du + dv * dv)
    arch = (rr < 0.32) & (rr > 0.16) & (v < 0.48)
    c.paint(arch, accent, 1.15 - (du + dv))
    c.rect(0.18, 0.48, 0.34, 0.74, accent, 1.0)
    c.rect(0.66, 0.48, 0.82, 0.74, accent, 0.8)
    c.rect(0.18, 0.74, 0.34, 0.86, '#D0D4DC', 1.1)
    c.rect(0.66, 0.74, 0.82, 0.86, '#D0D4DC', 0.9)
    c.outline()
    return c.image()


def valve(n, color, accent='#3060A0', seed=0):
    c = Canvas(n)
    c.rect(0.1, 0.56, 0.9, 0.72, color, 1.0)
    c.rect(0.1, 0.56, 0.9, 0.6, color, 1.3)
    c.rect(0.38, 0.4, 0.62, 0.78, color, 0.85)
    c.rect(0.47, 0.22, 0.53, 0.4, '#5A5E66', 1.0)
    c.ellipse(0.5, 0.2, 0.22, 0.06, accent, 1.1)
    c.outline()
    return c.image()


def injector(n, color, accent='#202020', seed=0):
    c = Canvas(n)
    u, v = c.grid()
    c.ellipse(0.5, 0.5, 0.38, 0.38, color, 1.25 - (u + v) * 0.5)
    rr = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2)
    ang = np.arctan2(v - 0.5, u - 0.5)
    holes = ((np.abs(rr - 0.26) < 0.7 / n) | (np.abs(rr - 0.14) < 0.7 / n)) & (np.cos(ang * (10 if n >= 32 else 6)) > 0.6)
    c.paint(holes, accent, 1.0)
    c.paint(rr < 0.04, accent, 1.0)
    c.outline()
    return c.image()


def battery(n, color, accent='#A0C0FF', seed=0):
    c = Canvas(n)
    u, v = c.grid()
    cyl = 0.55 + 0.75 * np.cos((u - 0.42) * math.pi * 1.9)
    c.rect(0.3, 0.2, 0.7, 0.86, color, cyl)
    c.rect(0.3, 0.2, 0.7, 0.34, '#C8CCD4', cyl)
    c.rect(0.43, 0.12, 0.57, 0.2, '#C8CCD4', 1.2)
    c.rect(0.44, 0.52, 0.56, 0.56, accent, 1.2)
    c.rect(0.48, 0.48, 0.52, 0.6, accent, 1.2)
    c.outline()
    return c.image()


def solar(n, color='#1A2A5A', accent='#6080D0', seed=0):
    c = Canvas(n)
    c.poly([(0.14, 0.32), (0.72, 0.2), (0.88, 0.66), (0.3, 0.8)], color, 1.0)
    u, v = c.grid()
    cells = 4 if n < 32 else 6
    for k in range(1, cells):
        t = k / cells
        x0, y0 = 0.14 + (0.72 - 0.14) * t, 0.32 + (0.2 - 0.32) * t
        x1, y1 = 0.3 + (0.88 - 0.3) * t, 0.8 + (0.66 - 0.8) * t
        line = np.abs((v - y0) * (x1 - x0) - (u - x0) * (y1 - y0)) / math.hypot(x1 - x0, y1 - y0) < 0.6 / n
        c.paint(line & (v > 0.18) & (v < 0.82), '#C0C8D8', 1.0)
    c.px(0.3, 0.34, '#FFFFFF', 0.9)
    c.px(0.34, 0.33, accent, 1.2)
    c.outline(0.5)
    return c.image()


def wafer(n, color='#404860', accent='#8090C0', seed=0):
    c = Canvas(n)
    u, v = c.grid()
    rr = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2)
    disc = rr < 0.38
    irid = 0.9 + 0.35 * np.sin((u * 3 + v * 2) * math.pi)
    c.paint(disc, color, irid)
    g = 6 if n < 32 else 10
    grid = disc & (((u * g) % 1 < 1.5 / n * g) | ((v * g) % 1 < 1.5 / n * g)) & (rr < 0.33)
    c.paint(grid, accent, 1.1)
    c.paint(disc & (v > 0.85), color, 0.6)
    c.outline(0.5)
    return c.image()


def tile(n, color, accent=None, seed=0):
    c = Canvas(n)
    hexp = [(0.5 + 0.38 * math.cos(math.pi / 3 * k + math.pi / 6), 0.5 + 0.38 * math.sin(math.pi / 3 * k + math.pi / 6)) for k in range(6)]
    u, v = c.grid()
    c.poly(hexp, color, 1.0)
    noise = vnoise(n, f"tile{seed}", 6)
    c.paint((noise > 0.66) & (((u - 0.5) ** 2 + (v - 0.5) ** 2) < 0.1), color, 0.7)
    c.outline(0.55)
    return c.image()


def canopy(n, color='#F07030', accent='#FFFFFF', seed=0):
    c = Canvas(n)
    u, v = c.grid()
    dome = (((u - 0.5) / 0.4) ** 2 + ((v - 0.5) / 0.3) ** 2 <= 1) & (v < 0.5)
    stripes = (np.floor((u - 0.1) * 6) % 2 == 0)
    c.paint(dome & stripes, color, 1.1 - (v - 0.2))
    c.paint(dome & ~stripes, accent, 1.0 - (v - 0.2) * 0.5)
    for x in (0.14, 0.32, 0.5, 0.68, 0.86):
        t = np.linspace(0, 1, 40)
        for tt in t:
            c.px(x + (0.5 - x) * tt, 0.5 + 0.3 * tt, '#B0B0B0', 0.9)
    c.rect(0.44, 0.78, 0.56, 0.88, '#606060', 1.0)
    c.outline(0.6)
    return c.image()


def cartridge(n, color, accent, seed=0):
    """Science data cartridge."""
    c = Canvas(n)
    c.rect(0.2, 0.18, 0.8, 0.84, color, 1.0)
    c.rect(0.2, 0.18, 0.8, 0.22, color, 1.3)
    c.rect(0.28, 0.28, 0.72, 0.54, '#E8E4D8', 1.0)
    c.rect(0.32, 0.33, 0.68, 0.36, accent, 0.9)
    c.rect(0.32, 0.41, 0.6, 0.44, '#606060', 1.0)
    c.rect(0.32, 0.47, 0.5, 0.5, '#606060', 1.0)
    for k in range(4):
        c.rect(0.3 + k * 0.11, 0.74, 0.36 + k * 0.11, 0.84, '#D8B040', 1.0)
    c.ellipse(0.66, 0.64, 0.05, 0.05, accent, 1.3)
    c.outline(0.5)
    return c.image()


def rocket(n, body='#F0F0F0', accent='#C03020', seed=0):
    c = Canvas(n)
    u, v = c.grid()
    c.poly([(0.38, 0.30), (0.62, 0.30), (0.62, 0.74), (0.38, 0.74)], body, 0.75 + 0.6 * np.cos((u - 0.46) * math.pi * 2.4))
    c.poly([(0.38, 0.30), (0.5, 0.06), (0.62, 0.30)], accent, 1.1)
    c.poly([(0.38, 0.58), (0.24, 0.82), (0.38, 0.74)], accent, 0.9)
    c.poly([(0.62, 0.58), (0.76, 0.82), (0.62, 0.74)], accent, 0.75)
    c.ellipse(0.5, 0.42, 0.06, 0.06, '#3070C0', 1.2)
    c.rect(0.42, 0.74, 0.58, 0.8, '#505058', 1.0)
    c.poly([(0.42, 0.8), (0.58, 0.8), (0.5, 0.98)], '#FFA030', 1.2)
    c.poly([(0.46, 0.8), (0.54, 0.8), (0.5, 0.92)], '#FFF0A0', 1.0)
    c.outline(0.5)
    return c.image()


def helmet(n, color, accent, seed=0):
    c = Canvas(n)
    u, v = c.grid()
    shell = (((u - 0.5) / 0.36) ** 2 + ((v - 0.5) / 0.36) ** 2 <= 1) & (v < 0.84)
    c.paint(shell, color, 1.2 - (u + v - 0.8) * 0.6)
    visor = (((u - 0.53) / 0.24) ** 2 + ((v - 0.5) / 0.16) ** 2 <= 1)
    c.paint(visor, '#C8A040', 1.1 - (v - 0.4))
    c.paint(visor & (u < 0.44) & (v < 0.46), '#FFF0C0', 1.0)
    c.rect(0.2, 0.74, 0.8, 0.84, accent, 1.0)
    c.outline(0.5)
    return c.image()


def chest(n, color, accent, seed=0):
    c = Canvas(n)
    c.poly([(0.2, 0.18), (0.8, 0.18), (0.9, 0.42), (0.78, 0.42), (0.76, 0.86), (0.24, 0.86), (0.22, 0.42), (0.1, 0.42)], color, 1.0)
    c.rect(0.36, 0.34, 0.64, 0.56, '#808890', 1.0)
    c.px(0.42, 0.42, '#40E060', 1.2)
    c.px(0.5, 0.42, '#E04040', 1.2)
    c.px(0.58, 0.42, '#4080FF', 1.2)
    c.rect(0.24, 0.62, 0.76, 0.68, accent, 1.0)
    c.rect(0.4, 0.14, 0.6, 0.2, '#606870', 1.0)
    c.outline(0.5)
    return c.image()


def legs(n, color, accent, seed=0):
    c = Canvas(n)
    c.poly([(0.24, 0.16), (0.76, 0.16), (0.78, 0.86), (0.58, 0.86), (0.5, 0.36), (0.42, 0.86), (0.22, 0.86)], color, 1.0)
    c.rect(0.24, 0.16, 0.76, 0.24, accent, 1.0)
    c.rect(0.26, 0.5, 0.38, 0.6, '#B0B4BC', 1.0)
    c.rect(0.62, 0.5, 0.74, 0.6, '#B0B4BC', 0.85)
    c.outline(0.5)
    return c.image()


def boots(n, color, accent, seed=0):
    c = Canvas(n)
    c.poly([(0.16, 0.36), (0.36, 0.36), (0.36, 0.66), (0.46, 0.7), (0.46, 0.82), (0.12, 0.82)], color, 1.05)
    c.poly([(0.56, 0.36), (0.76, 0.36), (0.76, 0.66), (0.88, 0.7), (0.88, 0.82), (0.54, 0.82)], color, 0.85)
    c.rect(0.12, 0.78, 0.46, 0.84, '#404448', 1.0)
    c.rect(0.54, 0.78, 0.88, 0.84, '#404448', 0.9)
    c.rect(0.16, 0.42, 0.36, 0.46, accent, 1.0)
    c.rect(0.56, 0.42, 0.76, 0.46, accent, 0.9)
    c.outline(0.5)
    return c.image()


SHAPES = {'ingot': ingot, 'plate': plate, 'raw': raw, 'gem': gem, 'dust': dust, 'cyl': cylinder, 'chip': chip, 'board': board,
          'gear': gear, 'rod': rod, 'nozzle': nozzle, 'scroll': scroll}

SPECIAL = {
    'turbine_blade': lambda n, c, a, s: turbine_blade(n, c, a, s),
    'ndfeb_magnet': lambda n, c, a, s: magnet(n, '#5A5A60', '#C04040', s),
    'valve': lambda n, c, a, s: valve(n, '#B0B4BC', '#3060A0', s),
    'injector': lambda n, c, a, s: injector(n, '#8890A0', '#202020', s),
    'battery_cell': lambda n, c, a, s: battery(n, '#3050A0', '#A0C0FF', s),
    'solar_cell': lambda n, c, a, s: solar(n, '#1A2A5A', '#6080D0', s),
    'silicon_wafer': lambda n, c, a, s: wafer(n, '#404860', '#8090C0', s),
    'vacuum_wafer': lambda n, c, a, s: wafer(n, '#304070', '#C0D0FF', s),
    'heat_shield_tile': lambda n, c, a, s: tile(n, '#2E241E', None, s),
    'parachute_canopy': lambda n, c, a, s: canopy(n, '#F07030', '#F8F8F8', s),
    'science_suborbital': lambda n, c, a, s: cartridge(n, '#204060', '#40FFC0', s),
    'science_moon': lambda n, c, a, s: cartridge(n, '#3C3C40', '#E0E0E0', s),
    'science_mars': lambda n, c, a, s: cartridge(n, '#602010', '#FF8040', s),
    'star_scroll': lambda n, c, a, s: scroll(n),
}


def item_icon(n, iid, spec, seed=0):
    if iid in SPECIAL:
        return SPECIAL[iid](n, spec[1] if len(spec) > 1 else None, spec[2] if len(spec) > 2 else None, seed)
    shape = spec[0]
    if shape == 'canister':
        band = spec[4] if len(spec) > 4 and spec[4] else '#C0C0C0'
        return canister(n, spec[1], band, seed)
    accent = spec[2] if len(spec) > 2 else None
    return SHAPES[shape](n, spec[1], accent, seed)


def wrench(n, color='#C8CCD4', accent='#FFB040', seed=0):
    """Rocket recovery wrench: diagonal handle, open jaw, orange grip."""
    c = Canvas(n)
    c.poly([(0.18, 0.74), (0.26, 0.82), (0.62, 0.46), (0.54, 0.38)], color, 1.0)
    c.poly([(0.16, 0.76), (0.24, 0.84), (0.38, 0.70), (0.30, 0.62)], accent, 1.0)
    c.ellipse(0.68, 0.32, 0.17, 0.17, color, 1.15)
    c.poly([(0.66, 0.30), (0.86, 0.10), (0.92, 0.18), (0.74, 0.36)], '#00000000' if False else '#2A2E36', 0.6)
    c.ellipse(0.68, 0.32, 0.06, 0.06, '#2A2E36', 0.6)
    c.outline()
    return c.image()
