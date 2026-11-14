#!/usr/bin/env python3
"""Engine sounds of the air-defence vehicles (overlay 06-weapons): a seamless diesel loop (sam_engine.ogg) and a
start-up (sam_engine_start.ogg), synthesised and encoded with ffmpeg (libvorbis, mono so the game positions it).
The loop is two seconds of a big V12 at idle: every component repeats a whole number of times in the loop, so it
joins without a click; the game raises its pitch and volume with speed. Run from the repository root."""
import os, subprocess, tempfile, wave
import numpy as np

OUT = 'jars/06-weapons/src/main/resources/assets/heliosphere/sounds'
SR = 44100


def lowpass(x, cutoff):
    """One-pole low-pass, applied forwards and backwards (no phase shift)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)): acc = (1 - a) * x[i] + a * acc; y[i] = acc
    acc = 0.0
    for i in range(len(x) - 1, -1, -1): acc = (1 - a) * y[i] + a * acc; y[i] = acc
    return y


def periodic_noise(n, cutoff, rng):
    """Band-limited noise that loops seamlessly: built in the frequency domain."""
    spec = np.zeros(n // 2 + 1, complex)
    f = np.fft.rfftfreq(n, 1 / SR)
    mag = 1 / (1 + (f / cutoff) ** 4)
    spec[:] = mag * np.exp(2j * np.pi * rng.random(len(f)))
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / np.abs(x).max()


def engine(t, firing, rng, rough=1.0):
    n = len(t)
    period = 1 / firing
    phase = (t % period) / period
    # each firing: a knock decaying in a third of the cycle; the strength wanders smoothly (whole cycles per loop)
    dur = n / SR
    amp = 0.85 + 0.09 * np.sin(2 * np.pi * 3 / dur * t) + 0.06 * np.sin(2 * np.pi * 7 / dur * t + 1.3)
    attack = 1 - np.exp(-phase * 60)                  # no instant steps: they click
    pulse = amp * np.exp(-phase * 9) * np.sin(2 * np.pi * phase * 2.2)
    body = np.zeros(n)
    for h, a in ((1, 1.0), (2, 0.55), (3, 0.35), (4, 0.22), (6, 0.12), (8, 0.07)):
        body += a * np.sin(2 * np.pi * firing * h * t + h * 0.7)
    body *= amp
    rumble = periodic_noise(n, 180, rng) * (0.55 + 0.45 * attack * np.exp(-phase * 5))
    clatter = periodic_noise(n, 2500, rng) * attack * np.exp(-phase * 14) * 0.22 * rough
    whine = 0.05 * np.sin(2 * np.pi * firing * 31 * t) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.5 * t))   # the turbocharger
    x = 0.9 * pulse + 0.55 * body + 0.6 * rumble + clatter + whine
    return x


def write_ogg(name, x):
    x = x / np.abs(x).max() * 0.92
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        w = os.path.join(d, 'a.wav')
        with wave.open(w, 'wb') as f:
            f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)


def main():
    rng = np.random.default_rng(7)
    dur = 2.0
    t = np.arange(int(SR * dur)) / SR
    firing = 36.0                                   # 72 knocks in the loop
    write_ogg('sam_engine', engine(t, firing, rng))

    # start-up: starter motor whirr, a few coughs, then the engine catches and settles to idle
    dur = 2.6
    t = np.arange(int(SR * dur)) / SR
    starter = (0.35 * np.sign(np.sin(2 * np.pi * (14 + 6 * t) * t)) * np.exp(-((t - 0.45) / 0.35) ** 2))
    starter = lowpass(starter, 900)
    rate = np.where(t < 0.8, 8 + 10 * t, np.where(t < 1.4, 16 + (t - 0.8) * 70, 58 - (t - 1.4) * 18))
    rate = np.maximum(rate, 36)
    rate[t < 0.8] = 8 + 10 * t[t < 0.8]
    ph = np.cumsum(rate) / SR
    frac = ph % 1
    k = np.floor(ph).astype(int)
    cough = np.exp(-frac * 9) * np.sin(2 * np.pi * frac * 2.2) * (0.6 + 0.4 * np.sin(k * 1.7))
    caught = np.clip((t - 0.7) / 0.3, 0, 1)
    noise = np.random.default_rng(3).normal(size=len(t))
    noise = lowpass(noise, 200)
    noise /= np.abs(noise).max()
    harm = sum(a * np.sin(2 * np.pi * h * ph + h) for h, a in ((1, 1.0), (2, 0.5), (3, 0.3), (4, 0.2)))
    run = (0.9 * cough + 0.5 * harm + 0.5 * noise * (0.5 + 0.5 * np.exp(-frac * 5))) * caught
    fade = np.clip((dur - t) / 0.4, 0, 1)
    write_ogg('sam_engine_start', (starter + run * 0.9) * fade)
    print('engine sounds written')


if __name__ == '__main__':
    main()
