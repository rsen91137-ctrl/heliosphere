#!/usr/bin/env python3
"""The machine fortresses' point-defence launcher (overlay 07-hostiles): textures, block model (a plinth, four
vertical launch cells, a small fire-control radar panel), blockstate, item, language; and the interception
messages (overlay 06-weapons). Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/07-hostiles/src/main/resources/assets/heliosphere'
A6 = 'jars/06-weapons/src/main/resources/assets/heliosphere'
T = f'{A}/textures/block/point_defence'
rng = np.random.default_rng(15)
def C(*v): return np.array(v, float)
STEEL = C(70, 74, 80); DARK = C(34, 36, 40); LIGHT = C(120, 126, 134); RED = C(178, 40, 34); YEL = C(214, 170, 46); WHITE = C(208, 212, 214)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f: json.dump(obj, f, ensure_ascii=False, indent=2); f.write('\n')


def merge(base, code, entries):
    path = f'{base}/lang/{code}.json'
    have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    have.update(entries); write(path, have)


def canvas(c, n=6): return np.zeros((16, 16, 3)) + c + rng.normal(0, n, (16, 16, 1))
def save(img, name):
    os.makedirs(T, exist_ok=True); Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f'{T}/{name}.png')


def textures():
    img = canvas(STEEL); img[0, :] = LIGHT; img[15, :] = DARK
    for x in range(16): img[11:13, x] = YEL if (x // 2) % 2 == 0 else DARK        # hazard band
    for p in ((2, 2), (2, 13), (8, 2), (8, 13)): img[p] = LIGHT
    img[4:8, 6:10] = DARK; img[5, 7:9] = RED                                      # warning light
    save(img, 'plinth_side')
    img = canvas(STEEL, 4); img[0, :] = img[:, 0] = LIGHT; img[15, :] = img[:, 15] = DARK
    save(img, 'plinth_top')
    img = canvas(DARK, 4)                                                          # the cells' hatches from above
    for cy in (1, 9):
        for cx in (1, 9):
            img[cy:cy + 6, cx:cx + 6] = C(54, 56, 60); img[cy, cx:cx + 6] = LIGHT * 0.9
            img[cy + 2:cy + 4, cx + 2:cx + 4] = RED
    save(img, 'cells_top')
    img = canvas(STEEL, 5)
    for x in (0, 5, 10, 15): img[:, x] = DARK
    img[0, :] = LIGHT
    save(img, 'cells_side')
    img = canvas(WHITE, 4)                                                          # the radar's face: a grid of elements
    for y in range(1, 15, 2):
        for x in range(1, 15, 2): img[y, x] = C(150, 160, 170)
    img[0, :] = img[15, :] = img[:, 0] = img[:, 15] = C(120, 124, 130)
    save(img, 'radar')


def model():
    t = lambda n: f'heliosphere:block/point_defence/{n}'
    def box(f, to, tex, up=None, rot=None):
        faces = {d: {'texture': f'#{tex}'} for d in ('north', 'south', 'east', 'west', 'down')}
        faces['up'] = {'texture': f'#{up or tex}'}
        e = {'from': f, 'to': to, 'faces': faces}
        if rot: e['rotation'] = rot
        return e
    elements = [
        box([0, 0, 0], [16, 6, 16], 'plinth', 'plinth_top'),
        box([2, 6, 2], [14, 13, 14], 'cells', 'cells_top'),
        box([12.5, 6, 6], [13.5, 15.5, 10], 'plinth'),                            # radar mast
        box([12, 10, 4], [16, 15.8, 12], 'radar', 'radar', {'angle': -22.5, 'axis': 'z', 'origin': [14, 12, 8]}),
    ]
    write(f'{A}/models/block/point_defence_launcher.json', {'parent': 'minecraft:block/block', 'textures': {
        'particle': t('plinth_side'), 'plinth': t('plinth_side'), 'plinth_top': t('plinth_top'), 'cells': t('cells_side'),
        'cells_top': t('cells_top'), 'radar': t('radar')}, 'elements': elements})
    write(f'{A}/blockstates/point_defence_launcher.json', {'variants': {'': {'model': 'heliosphere:block/point_defence_launcher'}}})
    write(f'{A}/items/point_defence_launcher.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:block/point_defence_launcher'}})


def lang():
    merge(A, 'zh_cn', {'block.heliosphere.point_defence_launcher': '要塞点防御拦截阵地'})
    merge(A, 'zh_tw', {'block.heliosphere.point_defence_launcher': '要塞點防禦攔截陣地'})
    merge(A, 'en_us', {'block.heliosphere.point_defence_launcher': 'Fortress Point-Defence Launcher'})
    merge(A6, 'zh_cn', {'message.heliosphere.point_defence_engaging': '⚠ 难度 %s 的机械堡垒防空正在拦截你的导弹！先摧毁它们楼顶的拦截阵地',
                        'message.heliosphere.nuke_intercepted': '你的战术核弹在空中被拦截击毁，没有发生核爆',
                        'message.heliosphere.missile_intercepted': '你的导弹被拦截了'})
    merge(A6, 'zh_tw', {'message.heliosphere.point_defence_engaging': '⚠ 難度 %s 的機械堡壘防空正在攔截你的導彈！先摧毀它們樓頂的攔截陣地',
                        'message.heliosphere.nuke_intercepted': '你的戰術核彈在空中被攔截擊毀，沒有發生核爆',
                        'message.heliosphere.missile_intercepted': '你的導彈被攔截了'})
    merge(A6, 'en_us', {'message.heliosphere.point_defence_engaging': '⚠ A difficulty %s machine fortress is shooting down your missile! Destroy the launchers on its roofs first',
                        'message.heliosphere.nuke_intercepted': 'Your tactical nuke was shot down in the air; no nuclear explosion',
                        'message.heliosphere.missile_intercepted': 'Your missile was intercepted'})


if __name__ == '__main__':
    textures(); model(); lang(); print('point defence assets written')
