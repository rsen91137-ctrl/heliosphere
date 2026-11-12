#!/usr/bin/env python3
"""Block resources of the robot fabricator (overlay 07-hostiles): textures, block and item models, mining tags."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/07-hostiles/src/main/resources/assets/heliosphere'
D = 'jars/07-hostiles/src/main/resources/data/minecraft/tags/block'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def png(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(path)


rng = np.random.default_rng(7)


def plate(base=(58, 60, 66)):
    img = np.zeros((16, 16, 4), np.int32)
    img[..., :3] = base
    img[..., 3] = 255
    img[..., :3] += rng.integers(-5, 6, (16, 16, 1))
    img[0, :, :3] += 22; img[:, 0, :3] += 16                     # bevel light
    img[15, :, :3] -= 24; img[:, 15, :3] -= 18                   # bevel shade
    for (y, x) in [(1, 1), (1, 14), (14, 1), (14, 14)]:          # rivets
        img[y, x, :3] = (120, 124, 130)
    return np.clip(img, 0, 255)


def side():
    img = plate()
    img[3:13, 3:13, :3] = (26, 26, 30)                           # bay
    img[3, 3:13, :3] = (90, 30, 26); img[12, 3:13, :3] = (16, 16, 18)
    for y in range(4, 12):                                       # assembly arm grille
        if y % 2 == 0: img[y, 4:12, :3] = (44, 44, 50)
    img[6:10, 6:10, :3] = (230, 50, 40)                          # glowing core
    img[7:9, 7:9, :3] = (255, 190, 120)
    for x in range(1, 15, 3): img[13, x:x + 2, :3] = (210, 170, 40)   # hazard stripe
    for x in range(2, 15, 3): img[13, x, :3] = (30, 30, 30)
    return img


def top():
    img = plate((64, 66, 72))
    for r in range(2, 7):                                        # circular hatch
        for a in np.linspace(0, 2 * np.pi, 64):
            y, x = int(round(7.5 + r * np.sin(a))), int(round(7.5 + r * np.cos(a)))
            img[y, x, :3] = (40, 40, 46) if r % 2 else (78, 80, 88)
    img[6:10, 6:10, :3] = (200, 40, 34); img[7:9, 7:9, :3] = (255, 150, 90)
    return img


def bottom():
    img = plate((44, 46, 50))
    for y in range(3, 13, 3): img[y, 2:14, :3] = (30, 30, 34)
    return img


def glow():
    img = np.zeros((16, 16, 4), np.int32)
    img[6:10, 6:10] = (255, 70, 50, 255); img[7:9, 7:9] = (255, 210, 150, 255)
    img[3, 3:13] = (255, 60, 40, 200)
    return img


png(f'{A}/textures/block/robot_fabricator_side.png', side())
png(f'{A}/textures/block/robot_fabricator_top.png', top())
png(f'{A}/textures/block/robot_fabricator_bottom.png', bottom())

faces = {d: {'texture': '#side' if d not in ('up', 'down') else ('#top' if d == 'up' else '#bottom'), 'cullface': d}
         for d in ['north', 'south', 'east', 'west', 'up', 'down']}
glow_faces = {d: {'texture': '#glow', 'cullface': d} for d in ['north', 'south', 'east', 'west']}
write(f'{A}/models/block/robot_fabricator.json', {
    'parent': 'minecraft:block/block',
    'textures': {'particle': 'heliosphere:block/robot_fabricator_side', 'side': 'heliosphere:block/robot_fabricator_side',
                 'top': 'heliosphere:block/robot_fabricator_top', 'bottom': 'heliosphere:block/robot_fabricator_bottom'},
    'elements': [
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces},
    ]})
write(f'{A}/blockstates/robot_fabricator.json', {'variants': {'': {'model': 'heliosphere:block/robot_fabricator'}}})
write(f'{A}/items/robot_fabricator.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:block/robot_fabricator'}})
write(f'{D}/mineable/pickaxe.json', {'replace': False, 'values': ['heliosphere:robot_fabricator']})
write(f'{D}/needs_iron_tool.json', {'replace': False, 'values': ['heliosphere:robot_fabricator']})
print('ok')
