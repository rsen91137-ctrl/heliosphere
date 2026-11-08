"""Renders the opening cinematic: four 960x540 key-art plates and an 82-second orchestral-ambient score."""
import os, subprocess, sys, math
import numpy as np
from PIL import Image, ImageFilter
from scipy import signal

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(2097)
W, H = 960, 540


def stars(img, n=2600, seed=1):
    r = np.random.default_rng(seed)
    cols = np.array([[155, 176, 255], [202, 215, 255], [248, 247, 255], [255, 244, 234], [255, 210, 161], [255, 180, 140]])
    weights = np.array([0.08, 0.17, 0.30, 0.25, 0.12, 0.08])
    for _ in range(n):
        x, y = r.integers(0, W), r.integers(0, H)
        c = cols[r.choice(len(cols), p=weights)]
        b = r.random() ** 3
        img[y, x] = np.minimum(255, img[y, x] + c * (0.25 + 0.75 * b))
        if b > 0.85 and 1 <= x < W - 1 and 1 <= y < H - 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[y + dy, x + dx] = np.minimum(255, img[y + dy, x + dx] + c * 0.35 * b)
    return img


def milky(img, angle=0.35, strength=38):
    yy, xx = np.mgrid[0:H, 0:W]
    d = (yy - H * 0.5) * math.cos(angle) - (xx - W * 0.5) * math.sin(angle)
    band = np.exp(-(d / 70.0) ** 2)
    noise = np.array(Image.fromarray((rng.random((H // 8, W // 8)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)) / 255.0
    band = band * (0.5 + 0.8 * noise)
    img += band[..., None] * np.array([strength * 0.9, strength * 0.85, strength * 1.1])
    return img


def sphere(img, cx, cy, R, colour_fn, light=(-0.6, -0.3, 0.75), atmo=None, night=None):
    yy, xx = np.mgrid[0:H, 0:W]
    nx, ny = (xx - cx) / R, (yy - cy) / R
    rr = nx * nx + ny * ny
    inside = rr <= 1
    nz = np.sqrt(np.clip(1 - rr, 0, 1))
    L = np.array(light) / np.linalg.norm(light)
    lam = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
    base = colour_fn(nx, ny, nz)
    shade = (0.03 + 0.97 * lam ** 0.8)[..., None]
    col = base * shade
    if night is not None:
        col += night(nx, ny, nz) * (np.clip(0.15 - lam, 0, 0.15) / 0.15)[..., None]
    img[inside] = col[inside]
    if atmo is not None:
        dist = np.sqrt(rr)
        rim = np.exp(-((dist - 1.0) / 0.035) ** 2) * np.clip(nx * L[0] + ny * L[1] + 0.35, 0, 1.2)
        glow = np.exp(-np.clip(dist - 1.0, 0, None) / 0.03) * (dist > 1) * np.clip(nx * L[0] + ny * L[1] + 0.2, 0, 1)
        img += (rim + glow * 0.8)[..., None] * np.array(atmo)
    return img


_NOISE = {}


def fbm(nx, ny, nz, oct=5, seed=0):
    """Multi-octave smooth value noise sampled across the disc (with a spherical warp so it reads as 3D)."""
    out = np.zeros_like(nx)
    amp, size = 1.0, 6
    # warp toward the limb so features foreshorten like on a sphere
    k = 1.0 / np.maximum(0.25, np.sqrt(np.clip(nz, 0, 1)) + 0.25)
    u, v = nx * k * 0.6, ny * k * 0.6
    for o in range(oct):
        key = (seed, o)
        if key not in _NOISE:
            r = np.random.default_rng(seed * 31 + o)
            small = (r.random((size, size)) * 255).astype(np.uint8)
            _NOISE[key] = np.array(Image.fromarray(small).resize((1024, 1024), Image.BICUBIC), dtype=np.float32) / 127.5 - 1
        tex = _NOISE[key]
        iu = np.abs(((((u * 0.5 + 0.5) * 1023).astype(int)) % 2046) - 1023)
        iv = np.abs(((((v * 0.5 + 0.5) * 1023).astype(int)) % 2046) - 1023)
        out += amp * tex[iv, iu]
        amp *= 0.5; size = int(size * 2.2)
    return out


def save(img, name, vignette=True):
    img = np.clip(img, 0, 255)
    if vignette:
        yy, xx = np.mgrid[0:H, 0:W]
        v = 1 - 0.55 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) ** 1.4
        img = img * np.clip(v, 0.25, 1)[..., None]
    # film grain
    img = img + rng.normal(0, 2.2, img.shape)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f'{OUT}/{name}.png', optimize=True)


# 1. Earth at dawn, seen from low orbit
img = np.zeros((H, W, 3)); stars(img, 1800, 3); milky(img, 0.5, 20)
def earth_col(nx, ny, nz):
    n = fbm(nx, ny, nz, 6, 7)
    land = n > 0.35
    c = np.where(land[..., None], np.array([70, 92, 48]) + (n[..., None] - 0.35) * np.array([120, 80, 40]), np.array([14, 42, 96]))
    cl = fbm(nx * 0.8, ny * 1.0, nz, 5, 11)
    clouds = np.clip((cl - 0.15) * 1.6, 0, 1)[..., None]
    return c * (1 - clouds) + clouds * np.array([235, 238, 245])
def lights(nx, ny, nz):
    n = fbm(nx * 0.9, ny * 0.9, nz, 5, 21)
    return (np.clip(n - 0.55, 0, 1) * 600)[..., None] * np.array([1.0, 0.75, 0.35])
sphere(img, W * 0.5, H * 1.62, H * 1.25, earth_col, light=(0.9, -0.45, 0.2), atmo=[90, 160, 255], night=lights)
yy, xx = np.mgrid[0:H, 0:W]
sun = np.exp(-(((xx - W * 0.86) ** 2 + (yy - H * 0.47) ** 2) / (2 * 18 ** 2)))
flare = np.exp(-np.abs(yy - H * 0.47) / 2.0) * np.exp(-np.abs(xx - W * 0.86) / 140.0)
img += (sun * 900 + flare * 90)[..., None] * np.array([1.0, 0.93, 0.8])
save(img, 'intro_earth')

# 2. The Sun's corona with prominences
img = np.zeros((H, W, 3)); stars(img, 900, 5)
cx, cy, R = W * 0.38, H * 0.55, H * 0.42
d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / R
ang = np.arctan2(yy - cy, xx - cx)
streak = 0.6 + 0.4 * np.sin(ang * 23 + np.sin(ang * 7) * 2) * np.sin(ang * 11 + 1.3)
corona = np.exp(-(np.clip(d - 1, 0, None)) / 0.22) * streak * (d > 1)
img += corona[..., None] * np.array([255, 190, 110]) * 0.9
def sun_col(nx, ny, nz):
    g = fbm(nx * 0.9, ny * 0.9, nz, 6, 31)
    return np.array([255, 170, 60]) * (0.85 + 0.15 * g[..., None]) + np.array([0, 40, 20]) * nz[..., None]
sphere(img, cx, cy, R, sun_col, light=(0, 0, 1))
limb = np.exp(-((d - 1) / 0.02) ** 2)
img += limb[..., None] * np.array([255, 120, 40]) * 0.6
for k in range(5):
    a = rng.random() * math.pi * 2
    for t in np.linspace(0, math.pi, 120):
        rr = R * (1 + 0.12 * math.sin(t) * (0.6 + rng.random() * 0.02))
        x = int(cx + math.cos(a + t * 0.08) * rr); y = int(cy + math.sin(a + t * 0.08) * rr)
        if 2 <= x < W - 2 and 2 <= y < H - 2:
            img[y - 3:y + 3, x - 3:x + 3] += np.array([255, 110, 40]) * 0.25
save(img, 'intro_sun')

# 3. Proxima Centauri: red dwarf, a tidally locked world, and the silhouettes of a fleet
img = np.zeros((H, W, 3)); stars(img, 2600, 9); milky(img, -0.3, 30)
pd = np.sqrt((xx - W * 0.78) ** 2 + (yy - H * 0.28) ** 2)
img += (np.exp(-pd / 28) * 700 + np.exp(-pd / 110) * 120)[..., None] * np.array([1.0, 0.42, 0.25])
def pb_col(nx, ny, nz):
    g = fbm(nx * 2, ny * 2, nz, 5, 41)
    return np.array([120, 70, 50]) + g[..., None] * np.array([40, 25, 15])
sphere(img, W * 0.3, H * 0.66, H * 0.36, pb_col, light=(0.85, -0.5, 0.2), atmo=[200, 90, 60])
ship = Image.new('L', (W, H), 0)
from PIL import ImageDraw
dr = ImageDraw.Draw(ship)
for k in range(9):
    sx, sy, s = W * (0.48 + 0.05 * k + rng.random() * 0.03), H * (0.18 + 0.06 * k + rng.random() * 0.04), 26 - k * 1.8
    dr.polygon([(sx - s * 2.2, sy), (sx + s * 1.6, sy - s * 0.35), (sx + s * 2.2, sy), (sx + s * 1.6, sy + s * 0.35)], fill=255)
    dr.rectangle([sx - s * 0.6, sy - s * 0.7, sx + s * 0.2, sy + s * 0.7], fill=255)
mask = np.array(ship.filter(ImageFilter.GaussianBlur(0.6))) / 255.0
img = img * (1 - mask[..., None] * 0.97)
engine = np.array(ship.filter(ImageFilter.GaussianBlur(5))) / 255.0 - mask
img += np.clip(engine, 0, 1)[..., None] * np.array([90, 140, 255]) * 0.7
save(img, 'intro_proxima')

# 4. Title plate: a rocket climbing out of the atmosphere, Moon in the distance
img = np.zeros((H, W, 3)); stars(img, 2200, 13); milky(img, 0.9, 26)
grad = np.clip((yy - H * 0.55) / (H * 0.45), 0, 1)
img += (grad ** 2)[..., None] * np.array([30, 70, 140])
def moon_col(nx, ny, nz):
    g = fbm(nx * 3, ny * 3, nz, 6, 51)
    return np.array([150, 150, 145]) + g[..., None] * np.array([30, 30, 30])
sphere(img, W * 0.82, H * 0.22, H * 0.08, moon_col, light=(-0.7, 0.2, 0.6))
trail_x = W * 0.45
for t in np.linspace(0, 1, 400):
    x = trail_x + math.sin(t * 3) * 6 * t
    y = H * 0.32 + t * H * 0.75
    w = 2 + t * 22
    yv = int(y)
    if 0 <= yv < H:
        x0, x1 = int(max(0, x - w)), int(min(W, x + w))
        img[yv, x0:x1] += np.array([200, 200, 210]) * 0.10 * (1 - t * 0.6)
flame = np.exp(-(((xx - trail_x) / 4) ** 2 + ((yy - H * 0.33) / 14) ** 2))
img += flame[..., None] * np.array([255, 200, 120]) * 3
img[int(H * 0.27):int(H * 0.32), int(trail_x - 2):int(trail_x + 3)] = np.array([230, 230, 235])
save(img, 'intro_title')
print('plates done')

# ------------------------------------------------------------------ score
SR = 32000
T = 82
N = SR * T
t = np.arange(N) / SR
mix = np.zeros((2, N))


def env(start, attack, hold, release, total=N):
    e = np.zeros(total)
    s, a, h, r = int(start * SR), int(attack * SR), int(hold * SR), int(release * SR)
    seg = np.concatenate([np.linspace(0, 1, max(a, 1)), np.ones(max(h, 0)), np.linspace(1, 0, max(r, 1))])
    end = min(total, s + len(seg))
    e[s:end] = seg[:end - s]
    return e


def saw(freq, detune_cents=(0, -8, 7, -14, 12)):
    out = np.zeros(N)
    for c in detune_cents:
        f = freq * 2 ** (c / 1200)
        ph = (t * f + rng.random()) % 1.0
        out += 2 * ph - 1
    return out / len(detune_cents)


def lowpass(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def note(n):  # MIDI -> Hz
    return 440 * 2 ** ((n - 69) / 12)


def pan(x, p):
    mix[0] += x * math.cos((p + 1) * math.pi / 4)
    mix[1] += x * math.sin((p + 1) * math.pi / 4)


# drone: D1 + D2 with slow swell
drone = (np.sin(2 * np.pi * note(26) * t) * 0.6 + lowpass(saw(note(38)), 300) * 0.5) * (env(0, 8, 60, 14) * 0.9)
pan(drone * 0.55, 0)
# wind / space noise
noise = lowpass(rng.normal(0, 1, N), 900) * (0.08 + 0.05 * np.sin(t * 0.21))
pan(noise * env(0, 4, 70, 8) * 0.5, -0.3)
pan(lowpass(rng.normal(0, 1, N), 700) * (0.07 + 0.05 * np.cos(t * 0.17)) * env(0, 4, 70, 8) * 0.5, 0.3)
# string chords Dm Bb Gm A (x2), each 6 s from 12 s
chords = [[50, 53, 57, 62], [46, 50, 53, 58], [43, 50, 55, 58], [45, 49, 52, 57], [50, 53, 57, 62], [46, 50, 53, 58], [43, 46, 50, 55], [45, 49, 52, 57]]
for i, ch in enumerate(chords):
    st = 12 + i * 6
    for k, n in enumerate(ch):
        e = env(st, 2.2, 3.2, 2.8)
        voice = lowpass(saw(note(n)), 1400 + 300 * k) * e * (0.16 + 0.04 * (i >= 4))
        pan(voice, -0.6 + k * 0.4)
# high tremolo strings for tension (A5 / Bb5 alternating)
trem = np.sin(2 * np.pi * note(81) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t))
trem2 = np.sin(2 * np.pi * note(82) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 7.3 * t))
pan(trem * env(26, 6, 10, 2) * 0.05, 0.5)
pan(trem2 * env(44, 4, 12, 4) * 0.06, -0.5)
# brass stabs on chord changes in the second half
for i in range(4, 8):
    st = 12 + i * 6
    for n in chords[i][:3]:
        f_env = env(st, 0.08, 0.6, 1.6)
        b = saw(note(n - 12), (0, 5, -5))
        b = lowpass(b, 900) * f_env * 0.22
        pan(b, 0)


# timpani / taiko hits and a heartbeat
def hit(at, f0=55, decay=1.2, amp=0.9, nz=0.3):
    s = int(at * SR)
    L = int(SR * (decay + 0.2))
    tt = np.arange(L) / SR
    tone = np.sin(2 * np.pi * (f0 * (1 + 0.6 * np.exp(-tt * 18))) * tt) * np.exp(-tt / decay * 3)
    tone += lowpass(rng.normal(0, 1, L), 400) * np.exp(-tt * 25) * nz
    e = min(N, s + L)
    for c in (0, 1):
        mix[c, s:e] += tone[:e - s] * amp * 0.5


for k in range(0, 12, 2):
    hit(k + 0.3, 48, 0.5, 0.35, 0.1); hit(k + 0.55, 46, 0.5, 0.28, 0.1)
for k in np.arange(12, 36, 3):
    hit(k, 52, 1.0, 0.5)
for k in np.arange(36, 58, 1.5):
    hit(k, 58, 0.8, 0.55)
for k in np.arange(54, 60, 0.375):
    hit(k, 62, 0.5, 0.35 + (k - 54) * 0.06)
hit(60, 36, 4.0, 1.5, 0.8)  # climax impact
# riser 50-60 s
rs = np.zeros(N)
seg = (t >= 50) & (t < 60)
fr = 200 * 2 ** ((t - 50) / 2.5)
rs[seg] = (lowpass(rng.normal(0, 1, N), 4000)[seg] * 0.3 + np.sin(2 * np.pi * np.cumsum(fr) / SR)[seg] * 0.1) * ((t[seg] - 50) / 10) ** 2
pan(rs * 0.5, 0)
# final ominous low note + Proxima signal pips (morse-like)
pan(lowpass(saw(note(31)), 200) * env(62, 3, 10, 7) * 0.5, 0)
for k, dur in enumerate([0.12, 0.12, 0.36, 0.12, 0.36, 0.36, 0.12]):
    s0 = 68 + k * 0.6
    seg = (t >= s0) & (t < s0 + dur)
    pan(np.where(seg, np.sin(2 * np.pi * 1320 * t) * 0.08, 0), 0.4)
# master: soft clip, normalise, fades
master = np.tanh(mix * 1.1)
master *= env(0, 2.5, T - 8, 5.5)[None, :]
master /= np.max(np.abs(master)) * 1.05
wav = f'{OUT}/intro.wav'
from scipy.io import wavfile
wavfile.write(wav, SR, (master.T * 32767).astype(np.int16))
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', f'{OUT}/intro_theme.ogg'], check=True)
os.remove(wav)
print('score done')
