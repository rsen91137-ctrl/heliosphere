#!/usr/bin/env python3
"""Resources of the giant-planet interiors (overlay 12-sphereworld): cloud deck textures (the three released ones
redrawn, two new), the glowing deep haze, the animated metallic hydrogen and ionic oceans, block models, names
and the layer reports. Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/12-sphereworld/src/main/resources/assets/heliosphere'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def png(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(path)


def tile_noise(seed, cells, size=16, t=0.0):
    rng = np.random.default_rng(seed)
    g = rng.random((cells, cells))
    ph = rng.random((cells, cells)) * 6.283
    g = 0.5 + 0.5 * np.sin(ph + t * 6.283) * 0.6 + (g - 0.5) * 0.4
    ys, xs = np.mgrid[0:size, 0:size] * (cells / size)
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    y1, x1 = (y0 + 1) % cells, (x0 + 1) % cells
    return (g[y0, x0] * (1 - fx) + g[y0, x1] * fx) * (1 - fy) + (g[y1, x0] * (1 - fx) + g[y1, x1] * fx) * fy


def ramp(v, cols):
    cols = np.array([[int(c[i:i + 2], 16) for i in (1, 3, 5)] for c in cols], float)
    v = np.clip(v, 0, 0.999) * (len(cols) - 1)
    i = v.astype(int); f = (v - i)[..., None]
    return cols[i] * (1 - f) + cols[np.minimum(i + 1, len(cols) - 1)] * f


def cloud(seed, cols, streak=False):
    v = tile_noise(seed, 4) * 0.6 + tile_noise(seed + 1, 8) * 0.4
    if streak:
        v = v * 0.7 + 0.3 * (0.5 + 0.5 * np.sin(np.arange(16)[:, None] / 16 * 6.283 * 2 + tile_noise(seed + 2, 4) * 3))
    v = (v - v.min()) / (v.max() - v.min() + 1e-9)
    v = np.round(v * 5) / 5                                   # pixel-art banding
    img = np.zeros((16, 16, 4))
    img[..., :3] = ramp(v, cols)
    img[..., 3] = 255
    return img


def ocean(seed, cols, frames=16, glint='#ffffff'):
    out = np.zeros((16 * frames, 16, 4))
    for f in range(frames):
        t = f / frames
        v = tile_noise(seed, 4, t=t) * 0.6 + tile_noise(seed + 5, 8, t=t * 2) * 0.4
        v = (v - v.min()) / (v.max() - v.min() + 1e-9)
        v = np.round(v * 6) / 6
        img = np.zeros((16, 16, 4))
        img[..., :3] = ramp(v, cols)
        img[..., 3] = 255
        hi = v > 0.92
        g = np.array([int(glint[i:i + 2], 16) for i in (1, 3, 5)])
        img[hi, :3] = g
        out[f * 16:(f + 1) * 16] = img
    return out


TEX = {
    'ammonia_cloud': cloud(3, ['#d8cfc0', '#e6dccb', '#f2eadb', '#fbf6ec'], True),
    'nh4sh_cloud': cloud(5, ['#8a5a32', '#a8703e', '#c48a52', '#dcaa72'], True),
    'methane_cloud': cloud(7, ['#5c8fb0', '#74a6c4', '#90bfd8', '#b4d8ea'], True),
    'water_cloud': cloud(11, ['#b8c0c8', '#cfd6dc', '#e2e7eb', '#f6f8fa']),
    'hydrogen_sulfide_cloud': cloud(13, ['#7e8c5a', '#97a46a', '#b0bc80', '#ccd49c']),
    'deep_haze': cloud(17, ['#6e1e08', '#a83a10', '#d8661c', '#ffa040']),
}
for name, img in TEX.items():
    png(f'{A}/textures/block/{name}.png', img)
png(f'{A}/textures/block/metallic_hydrogen.png', ocean(23, ['#5a6270', '#8a94a4', '#b8c2d0', '#dfe6f0'], glint='#ffffff'))
png(f'{A}/textures/block/ionic_ocean.png', ocean(29, ['#0a1e6a', '#1840b0', '#2e6cf0', '#70b0ff'], glint='#d0f0ff'))
for n in ('metallic_hydrogen', 'ionic_ocean'):
    write(f'{A}/textures/block/{n}.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})

for n in ('water_cloud', 'hydrogen_sulfide_cloud', 'deep_haze', 'metallic_hydrogen', 'ionic_ocean'):
    write(f'{A}/models/block/{n}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'heliosphere:block/{n}'}})
    write(f'{A}/blockstates/{n}.json', {'variants': {'': {'model': f'heliosphere:block/{n}'}}})
    write(f'{A}/items/{n}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:block/{n}'}})

ZH = {'block.heliosphere.water_cloud': '水冰云', 'block.heliosphere.hydrogen_sulfide_cloud': '硫化氢云',
      'block.heliosphere.deep_haze': '炽热深层雾霭', 'block.heliosphere.metallic_hydrogen': '液态金属氢',
      'block.heliosphere.ionic_ocean': '离子水-氨海洋'}
EN = {'block.heliosphere.water_cloud': 'Water Ice Cloud', 'block.heliosphere.hydrogen_sulfide_cloud': 'Hydrogen Sulfide Cloud',
      'block.heliosphere.deep_haze': 'Searing Deep Haze', 'block.heliosphere.metallic_hydrogen': 'Liquid Metallic Hydrogen',
      'block.heliosphere.ionic_ocean': 'Ionic Water-Ammonia Ocean'}
TW = {'block.heliosphere.water_cloud': '水冰雲', 'block.heliosphere.hydrogen_sulfide_cloud': '硫化氫雲',
      'block.heliosphere.deep_haze': '熾熱深層霧靄', 'block.heliosphere.metallic_hydrogen': '液態金屬氫',
      'block.heliosphere.ionic_ocean': '離子水-氨海洋'}
GAS = ['平流层 · 顶层氨冰薄云之上，气压 %s bar，%s°C', '主云层 · 氨冰云与浮空平台，气压 %s bar，%s°C',
       '第二云层 · 硫氢化铵云（棕红色），气压 %s bar，%s°C', '水冰云层 · 闪电的发源地，气压 %s bar，%s°C',
       '超临界深渊 · 氢气已像液体一样浓稠，气压 %s bar，%s°C —— 任何宇航服都撑不了多久',
       '金属氢海洋上空 · 气压 %s bar，%s°C —— 下方是导电的液态金属氢']
ICE = ['平流层 · 顶层甲烷薄云之上，气压 %s bar，%s°C', '主云层 · 甲烷云与浮空平台，气压 %s bar，%s°C',
       '硫化氢云层，气压 %s bar，%s°C', '硫氢化铵与水冰云层，气压 %s bar，%s°C',
       '超临界深渊 · 钻石雨正在落下，气压 %s bar，%s°C', '离子海洋上空 · 气压 %s bar，%s°C —— 下方是滚烫的离子水-氨海洋']
GAS_EN = ['Stratosphere, above the thin ammonia haze · %s bar, %s °C', 'Main cloud deck · ammonia clouds and aerostats · %s bar, %s °C',
          'Second deck · ammonium hydrosulfide clouds · %s bar, %s °C', 'Water clouds · where the lightning is born · %s bar, %s °C',
          'Supercritical depths · hydrogen thick as a liquid · %s bar, %s °C — no suit lasts long here',
          'Above the metallic hydrogen ocean · %s bar, %s °C']
ICE_EN = ['Stratosphere, above the thin methane haze · %s bar, %s °C', 'Main cloud deck · methane clouds and aerostats · %s bar, %s °C',
          'Hydrogen sulfide clouds · %s bar, %s °C', 'Ammonium hydrosulfide and water clouds · %s bar, %s °C',
          'Supercritical depths · it is raining diamonds · %s bar, %s °C', 'Above the ionic ocean · %s bar, %s °C']
for i in range(6):
    ZH[f'message.heliosphere.giant_layer.gas.{i}'] = GAS[i]; ZH[f'message.heliosphere.giant_layer.ice.{i}'] = ICE[i]
    TW[f'message.heliosphere.giant_layer.gas.{i}'] = GAS[i].replace('层', '層').replace('气压', '氣壓').replace('云', '雲').replace('浮空平台', '浮空平台').replace('铵', '銨').replace('红', '紅').replace('闪电', '閃電').replace('发源', '發源').replace('临界', '臨界').replace('氢', '氫').replace('浓稠', '濃稠').replace('宇航服', '太空衣').replace('撑', '撐').replace('导电', '導電').replace('态', '態').replace('属', '屬').replace('顶', '頂').replace('薄', '薄')
    TW[f'message.heliosphere.giant_layer.ice.{i}'] = ICE[i].replace('层', '層').replace('气压', '氣壓').replace('云', '雲').replace('铵', '銨').replace('临界', '臨界').replace('钻石', '鑽石').replace('落下', '落下').replace('离子', '離子').replace('滚烫', '滾燙').replace('氢', '氫').replace('顶', '頂').replace('甲烷', '甲烷')
    EN[f'message.heliosphere.giant_layer.gas.{i}'] = GAS_EN[i]; EN[f'message.heliosphere.giant_layer.ice.{i}'] = ICE_EN[i]
write(f'{A}/lang/zh_cn.json', ZH); write(f'{A}/lang/en_us.json', EN); write(f'{A}/lang/zh_tw.json', TW)
print('ok')
