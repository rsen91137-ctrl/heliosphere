"""Synthesizes rocket sound effects (mono OGG): ignition, loopable engine roar, stage separation, cabin alarm."""
import os, subprocess, sys
import numpy as np
from scipy import signal
from scipy.io import wavfile

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
SR = 32000
rng = np.random.default_rng(7)


def lp(x, fc, o=2):
    b, a = signal.butter(o, fc / (SR / 2), 'low'); return signal.lfilter(b, a, x)


def bp(x, lo, hi, o=2):
    b, a = signal.butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band'); return signal.lfilter(b, a, x)


def brown(n):
    w = rng.normal(0, 1, n); b = np.cumsum(w); b -= lp(b, 8); return b / (np.max(np.abs(b)) + 1e-9)


def crackle(n, rate=60, decay=0.004):
    out = np.zeros(n)
    idx = rng.integers(0, n, int(n / SR * rate))
    L = int(SR * decay * 6)
    k = np.exp(-np.arange(L) / (SR * decay)) * rng.normal(0, 1, L)
    for i in idx:
        e = min(n, i + L); out[i:e] += k[:e - i] * rng.uniform(0.3, 1.0)
    return out


def save(x, name):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.95
    wav = f'{OUT}/{name}.wav'
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', f'{OUT}/{name}.ogg'], check=True)
    os.remove(wav)


def roar(n):
    t = np.arange(n) / SR
    body = lp(brown(n), 220) * 1.0 + bp(rng.normal(0, 1, n), 120, 900) * 0.35 + bp(rng.normal(0, 1, n), 900, 3500) * 0.08
    rumble = np.sin(2 * np.pi * 38 * t + np.sin(2 * np.pi * 0.7 * t) * 2) * 0.25
    flutter = 1 + 0.12 * np.sin(2 * np.pi * 9 * t + rng.random()) + 0.08 * np.sin(2 * np.pi * 13.7 * t)
    return (body + rumble) * flutter + bp(crackle(n, 90), 400, 6000) * 0.35


# loopable engine: render 6 s, crossfade the tail into the head
n = SR * 6
x = roar(n + SR)
fade = np.linspace(0, 1, SR)
x[:SR] = x[:SR] * fade + x[n:n + SR] * (1 - fade)
save(np.tanh(x[:n] * 1.3), 'rocket_engine')

# ignition: sparks and chatter, then the roar swells in over 3 s
n = SR * 4
t = np.arange(n) / SR
ign = roar(n) * np.clip((t - 0.6) / 2.4, 0, 1) ** 1.5
ign += bp(crackle(n, 30, 0.002), 1500, 8000) * np.exp(-t * 1.5) * 0.6
thump = lp(rng.normal(0, 1, n), 120) * np.exp(-np.abs(t - 0.7) * 8) * 2
save(np.tanh((ign + thump) * 1.2), 'rocket_ignition')

# stage separation: pyro bang + metallic ring + hiss
n = int(SR * 2.2)
t = np.arange(n) / SR
bang = lp(rng.normal(0, 1, n), 2500) * np.exp(-t * 18) * 1.5
ring = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, d in ((310, 3), (523, 4), (847, 5), (1290, 7))) * 0.25
hiss = bp(rng.normal(0, 1, n), 2000, 9000) * np.exp(-t * 2.5) * 0.3
save(bang + ring + hiss, 'rocket_separation')

# cabin master alarm: two-tone
n = SR * 2
t = np.arange(n) / SR
tone = np.where((t % 0.5) < 0.25, np.sign(np.sin(2 * np.pi * 880 * t)), np.sign(np.sin(2 * np.pi * 660 * t)))
save(lp(tone, 3000) * 0.5 * np.clip(1 - (t - 1.8) / 0.2, 0, 1), 'cabin_alarm')
print('sfx done')
