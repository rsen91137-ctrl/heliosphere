#!/usr/bin/env python3
"""Paints the assets of the nuclear aftermath (jars/06-weapons): the three blocks, the radiation
sickness icon and the Geiger counter click. Deterministic; run from the repository root."""
import json, os, struct, subprocess, tempfile, wave
import numpy as np
from PIL import Image

RES = 'jars/06-weapons/src/main/resources'
A = f'{RES}/assets/heliosphere'
D = f'{RES}/data/heliosphere'

def noise(rng, n=16, octaves=3):
    out = np.zeros((n, n))
    for o in range(octaves):
        k = 2 ** (o + 1)
        g = rng.random((k, k))
        out += np.kron(g, np.ones((n // k, n // k))) / (o + 1)
    return out / out.max()

def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA').save(path)

def trinitite():
    rng = np.random.default_rng(11)
    n = noise(rng) * 0.6 + rng.random((16, 16)) * 0.4
    img = np.zeros((16, 16, 4))
    img[..., 0] = 52 + 60 * n
    img[..., 1] = 92 + 90 * n
    img[..., 2] = 58 + 50 * n
    img[..., 3] = 255
    for _ in range(5):                       # dark flow lines in the glass
        x, y = rng.integers(0, 16, 2)
        for _ in range(rng.integers(4, 9)):
            img[y % 16, x % 16, :3] *= 0.62
            x += rng.integers(0, 2); y += rng.integers(-1, 2)
    for _ in range(7):                       # bright bubbles
        x, y = rng.integers(0, 16, 2)
        img[y, x, :3] = (176, 232, 150)
    return img

def scorched():
    rng = np.random.default_rng(23)
    n = noise(rng) * 0.5 + rng.random((16, 16)) * 0.5
    img = np.zeros((16, 16, 4))
    img[..., 0] = 26 + 30 * n
    img[..., 1] = 22 + 24 * n
    img[..., 2] = 20 + 20 * n
    img[..., 3] = 255
    for _ in range(4):                       # cracks still glowing faintly
        x, y = rng.integers(0, 16, 2)
        for _ in range(rng.integers(3, 6)):
            img[y % 16, x % 16, :3] = (118, 44, 16)
            x += rng.integers(-1, 2); y += rng.integers(0, 2)
    for _ in range(10):
        x, y = rng.integers(0, 16, 2)
        img[y, x, :3] = (70, 66, 62)         # ash flecks
    return img

def ash():
    rng = np.random.default_rng(37)
    n = rng.random((16, 16))
    img = np.zeros((16, 16, 4))
    for c, base in enumerate((150, 148, 142)):
        img[..., c] = base + 50 * n
    img[..., 3] = 255
    for _ in range(14):
        x, y = rng.integers(0, 16, 2)
        img[y, x, :3] = (92, 90, 86)
    for _ in range(3):
        x, y = rng.integers(0, 16, 2)
        img[y, x, :3] = (150, 214, 96)       # a hot particle
    return img

def trefoil():
    n = 18
    img = np.zeros((n, n, 4))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            r = (dx * dx + dy * dy) ** 0.5
            a = (np.degrees(np.arctan2(-dy, dx)) + 360) % 360
            blade = 3.2 < r < 8.2 and any(abs(((a - m + 180) % 360) - 180) < 30 for m in (90, 210, 330))
            if r < 8.9: img[y, x] = (38, 44, 20, 255)
            if blade or r < 1.9: img[y, x] = (176, 232, 52, 255)
    return img

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')

def click():
    """A Geiger-Mueller tube firing: a few milliseconds of ringing crack."""
    rate = 44100
    t = np.arange(int(rate * 0.03)) / rate
    rng = np.random.default_rng(5)
    s = (rng.standard_normal(t.size) * np.exp(-t / 0.0016) + 0.5 * np.sin(2 * np.pi * 3300 * t) * np.exp(-t / 0.004)) * 0.8
    pcm = (np.clip(s, -1, 1) * 32767).astype('<i2')
    out = f'{A}/sounds/geiger_click.ogg'
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        with wave.open(f'{tmp}/c.wav', 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', f'{tmp}/c.wav', '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4',
                        '-map_metadata', '-1', '-fflags', '+bitexact', out], check=True)

def main():
    for name, paint in (('trinitite', trinitite), ('scorched_earth', scorched), ('fallout_ash', ash)):
        save(paint(), f'{A}/textures/block/{name}.png')
        write(f'{A}/blockstates/{name}.json', {'variants': {'': {'model': f'heliosphere:block/{name}'}}})
        if name == 'fallout_ash':
            write(f'{A}/models/block/{name}.json', {'parent': 'minecraft:block/carpet', 'textures': {'wool': f'heliosphere:block/{name}'}})
        else:
            write(f'{A}/models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'heliosphere:block/{name}'}})
        write(f'{A}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{name}'}})
        pools = [] if name == 'fallout_ash' else [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'heliosphere:{name}'}],
                                                    'conditions': [{'condition': 'minecraft:survives_explosion'}]}]
        write(f'{D}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': pools, 'random_sequence': f'heliosphere:blocks/{name}'})
    save(trefoil(), f'{A}/textures/mob_effect/radiation.png')
    click()
    # merged into the released files key by key (see jars/overlay.gradle)
    write(f'{A}/sounds.json', {'geiger_click': {'sounds': [{'name': 'heliosphere:geiger_click', 'volume': 1.0}], 'subtitle': 'subtitles.heliosphere.geiger'}})
    lang = {
        'zh_cn': ('核融玻璃', '焦土', '辐射尘', '辐射病', '盖革计数器咔嗒声'),
        'zh_tw': ('核融玻璃', '焦土', '輻射塵', '輻射病', '蓋革計數器喀嗒聲'),
        'en_us': ('Trinitite', 'Scorched Earth', 'Fallout Ash', 'Radiation Sickness', 'Geiger counter clicks'),
    }
    for code, (t, s, f, r, g) in lang.items():
        write(f'{A}/lang/{code}.json', {
            'block.heliosphere.trinitite': t, 'block.heliosphere.scorched_earth': s, 'block.heliosphere.fallout_ash': f,
            'effect.heliosphere.radiation': r, 'subtitles.heliosphere.geiger': g})

if __name__ == '__main__':
    main()
