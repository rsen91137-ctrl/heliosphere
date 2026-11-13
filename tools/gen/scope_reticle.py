#!/usr/bin/env python3
"""The 4x scope's reticle after the player's sketch "LT70/77 precision sight, 5.56x45 / 7.62x39, 4x": centre ring and
dot, a ticked horizontal line marked 5 at each end, a bullet-drop ladder under the centre marked 1, 4 and 8 (hundreds
of metres), and a stadia rangefinder bottom left (a 1.7 m man: a base line and a dotted curve marked 2 to 10).
The dark surround of the released texture is kept. Run from the repository root."""
import glob, io, math, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = 'jars/06-weapons/src/main/resources/assets/heliosphere/textures/gui/scope_4x.png'
jar = glob.glob('base/06-*.jar')[0]
orig = Image.open(io.BytesIO(zipfile.ZipFile(jar).read('assets/heliosphere/textures/gui/scope_4x.png'))).convert('RGBA')
N = orig.size[0]
a = np.array(orig).astype(float)
yy, xx = np.mgrid[0:N, 0:N]
r = np.hypot(xx - N / 2 + 0.5, yy - N / 2 + 0.5)
R = N * 0.47
a[r < R * 0.93] = 0                                                   # clear the old reticle, keep the dark surround

S = 4
big = Image.new('RGBA', (N * S, N * S), (0, 0, 0, 0))
d = ImageDraw.Draw(big)
c = N * S / 2
RR = R * S
ink = (12, 12, 14, 235)
w = max(2, int(1.2 * S))


def line(x0, y0, x1, y1, width=w): d.line([(c + x0 * RR, c + y0 * RR), (c + x1 * RR, c + y1 * RR)], fill=ink, width=width)


try:
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', int(14 * S))
except OSError:
    font = ImageFont.load_default()


def label(x, y, t): d.text((c + x * RR, c + y * RR), t, fill=ink, font=font, anchor='mm')


# horizontal line, broken around the centre ring, ticked every 0.105 R, marked 5 at the fifth tick
line(-0.93, 0, -0.08, 0); line(0.08, 0, 0.93, 0)
for i in range(1, 9):
    x = 0.105 * i
    h = 0.045 if i == 5 else 0.022
    for sx in (-1, 1): line(sx * x, -h, sx * x, h)
label(-0.105 * 5, 0.1, '5'); label(0.105 * 5, 0.1, '5')
# centre ring and dot
d.ellipse([c - 0.07 * RR, c - 0.07 * RR, c + 0.07 * RR, c + 0.07 * RR], outline=ink, width=w)
d.ellipse([c - 0.012 * RR, c - 0.012 * RR, c + 0.012 * RR, c + 0.012 * RR], fill=(220, 40, 40, 255))
# vertical line above the centre (thin)
line(0, -0.93, 0, -0.08, max(1, w // 2))
# the bullet-drop ladder under the centre: three columns, rungs, marked 1, 4, 8
xs = (-0.085, -0.03, 0.03, 0.085)
top, bottom = 0.075, 0.9
for x in xs: line(x, top, x, bottom)
rungs = [0.12, 0.2, 0.29, 0.39, 0.5, 0.62, 0.75, 0.88]
for y in rungs: line(xs[0], y, xs[-1], y)
for mark, y in (('1', 0.12), ('4', 0.39), ('8', 0.88)):
    label(-0.15, y, mark); label(0.15, y, mark)
# the rangefinder, bottom left: base line with ticks marked 1.7, and a dotted curve marked 2..10
by = 0.56
line(-0.86, by, -0.34, by)
for i in range(7): line(-0.86 + i * 0.087, by, -0.86 + i * 0.087, by + 0.03, max(1, w // 2))
label(-0.25, by + 0.005, '1.7')
for k, (mark, t) in enumerate(zip(('2', '4', '6', '8', '10'), (0.1, 0.3, 0.5, 0.7, 0.9))):
    pass
pts = []
for i in range(25):
    t = i / 24
    x = -0.34 - 0.52 * t
    y = by - 0.06 - 0.32 * (1 - t) ** 2.2                       # the man's height shrinks with distance
    pts.append((x, y))
for x, y in pts[::2]: d.ellipse([c + x * RR - 1.8 * S, c + y * RR - 1.8 * S, c + x * RR + 1.8 * S, c + y * RR + 1.8 * S], fill=ink)
for mark, t in (('2', 0.06), ('4', 0.3), ('6', 0.52), ('8', 0.74), ('10', 0.96)):
    x = -0.34 - 0.52 * t; y = by - 0.06 - 0.32 * (1 - t) ** 2.2
    label(x + 0.02, y - 0.06, mark)
# the sight's name, small, at the bottom edge
try:
    small = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', int(9 * S))
except OSError:
    small = font
d.text((c + 0.48 * RR, c + 0.62 * RR), 'LT70/77  4x', fill=(12, 12, 14, 160), font=small, anchor='mm')

ret = np.array(big.resize((N, N), Image.LANCZOS)).astype(float)
alpha = ret[..., 3:4] / 255
out = a.copy()
out[..., :3] = out[..., :3] * (1 - alpha) + ret[..., :3] * alpha
out[..., 3:4] = np.maximum(out[..., 3:4], ret[..., 3:4])
Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(OUT)
print('scope reticle written', OUT)
