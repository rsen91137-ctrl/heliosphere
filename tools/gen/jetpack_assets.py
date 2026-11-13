#!/usr/bin/env python3
"""Jetpacks (overlay 06-weapons): inventory icons, the pack worn on the back (humanoid equipment texture), equipment
assets, recipes, the repair tag and language entries. Run from the repository root."""
import json, os
import numpy as np
from PIL import Image

A = 'jars/06-weapons/src/main/resources/assets/heliosphere'
D = 'jars/06-weapons/src/main/resources/data/heliosphere'
rng = np.random.default_rng(5)

KINDS = {   # id: (tank colour, frame colour, nozzle colour, accent)
    'jetpack': ((190, 194, 200), (90, 94, 100), (60, 60, 64), (220, 160, 40)),
    'heavy_jetpack': ((92, 100, 112), (52, 56, 62), (40, 40, 44), (230, 120, 40)),
    'rcs_pack': ((232, 232, 226), (150, 152, 156), (110, 112, 116), (60, 140, 220)),
    'adaptive_jetpack': ((46, 52, 64), (24, 26, 30), (30, 30, 34), (90, 210, 255)),
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2); f.write('\n')


def merge(code, entries):
    path = f'{A}/lang/{code}.json'
    have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    have.update(entries); write(path, have)


def icon(kid, tank, frame, nozzle, accent):
    img = np.zeros((16, 16, 4), float)
    def px(y, x, c, a=255): img[y, x, :3] = c; img[y, x, 3] = a
    # frame plate behind the tanks
    for y in range(3, 13):
        for x in range(4, 12): px(y, x, np.array(frame) * (0.9 + 0.1 * rng.random()))
    # two tanks
    for cx in (5, 9):
        for y in range(2, 12):
            for x in range(cx - 1, cx + 2):
                shade = 1.15 if x == cx - 1 else 0.85 if x == cx + 1 else 1.0
                px(y, x, np.clip(np.array(tank) * shade, 0, 255))
        px(2, cx, np.clip(np.array(tank) * 1.25, 0, 255))
        px(7, cx - 1, accent); px(7, cx, accent); px(7, cx + 1, accent)        # band
        px(12, cx, nozzle); px(13, cx - 1, nozzle); px(13, cx + 1, nozzle); px(13, cx, nozzle)   # nozzle
        px(14, cx, (255, 170, 60), 200)                                        # flame
    # straps
    for y in range(4, 11): px(y, 3, (40, 40, 44)); px(y, 12, (40, 40, 44))
    # outline
    a = img[..., 3] > 0
    edge = np.zeros_like(a)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)): edge |= np.roll(np.roll(a, dy, 0), dx, 1) & ~a
    img[edge] = (24, 24, 26, 255)
    os.makedirs(f'{A}/textures/item', exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f'{A}/textures/item/{kid}.png')
    write(f'{A}/items/{kid}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{kid}'}})
    write(f'{A}/models/item/{kid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{kid}'}})


def worn(kid, tank, frame, nozzle, accent):
    """The humanoid chest layer (64x32): harness on the chest, the pack itself on the back of the torso."""
    img = np.zeros((32, 64, 4), float)
    def rect(x0, y0, x1, y1, c, a=255): img[y0:y1, x0:x1, :3] = c; img[y0:y1, x0:x1, 3] = a
    strap = (36, 36, 40)
    # front of the torso (x 20..28, y 20..32): two shoulder straps and a chest buckle
    rect(21, 20, 22, 32, strap); rect(26, 20, 27, 32, strap); rect(21, 25, 27, 26, strap); rect(23, 24, 25, 27, accent)
    # sides of the torso (x 16..20, 28..32): straps
    rect(16, 20, 20, 21, strap); rect(28, 20, 32, 21, strap)
    # top of the shoulders (x 20..28, y 16..20)
    rect(21, 16, 22, 20, strap); rect(26, 16, 27, 20, strap)
    # back (x 32..40, y 20..32): frame, two tanks, a band, nozzles at the bottom
    rect(32, 20, 40, 32, frame)
    for cx in (33, 37):
        rect(cx, 20, cx + 3, 31, tank)
        rect(cx, 20, cx + 1, 31, np.clip(np.array(tank) * 1.15, 0, 255))
        rect(cx + 2, 20, cx + 3, 31, np.clip(np.array(tank) * 0.8, 0, 255))
        rect(cx, 25, cx + 3, 26, accent)
        rect(cx, 31, cx + 3, 32, nozzle)
    os.makedirs(f'{A}/textures/entity/equipment/humanoid', exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f'{A}/textures/entity/equipment/humanoid/{kid}.png')
    write(f'{A}/equipment/{kid}.json', {'layers': {'humanoid': [{'texture': f'heliosphere:{kid}'}]}})


RECIPES = {
    'jetpack': (['PCP', 'THT', 'I I'], {'P': 'steel_plate', 'C': 'circuit_board', 'T': 'canister_hydrogen', 'H': 'battery_cell', 'I': 'inconel_plate'}),
    'heavy_jetpack': (['TJT', 'MPM', 'I I'], {'T': 'ti64_plate', 'J': 'jetpack', 'M': 'electric_motor', 'P': 'processor', 'I': 'inconel_plate'}),
    'rcs_pack': (['ACA', 'NTN', 'A A'], {'A': 'al_li_plate', 'C': 'circuit_board', 'N': 'copper_wire', 'T': 'canister_hydrogen'}),
    'adaptive_jetpack': (['FHF', 'PRP', 'F F'], {'F': 'carbon_fiber', 'H': 'heavy_jetpack', 'R': 'rcs_pack', 'P': 'processor'}),
}


def data():
    for k, (pattern, key) in RECIPES.items():
        write(f'{D}/recipe/{k}.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
                                       'key': {c: f'heliosphere:{v}' for c, v in key.items()}, 'result': {'id': f'heliosphere:{k}', 'count': 1}})
    write(f'{D}/tags/item/repairs_jetpack.json', {'values': ['heliosphere:steel_plate']})


def lang():
    merge('zh_cn', {
        'item.heliosphere.jetpack': '喷气背包', 'item.heliosphere.jetpack.desc': '地球级重力用（1.6g 以内）：空中按住跳跃上升',
        'item.heliosphere.heavy_jetpack': '重型喷气背包', 'item.heliosphere.heavy_jetpack.desc': '高重力星球用（4g 以内，如木星、土星深处）：推力大，耗氢快',
        'item.heliosphere.rcs_pack': '低重力机动背包', 'item.heliosphere.rcs_pack.desc': '月球、火卫、小行星等极低重力用：可侧向推进、潜行悬停，非常省燃料；在地球上推不动你',
        'item.heliosphere.adaptive_jetpack': '自适应喷气背包', 'item.heliosphere.adaptive_jetpack.desc': '任何重力下自动匹配推力，带侧推与悬停',
        'item.heliosphere.jetpack.fuel': '燃料 %s%%（身上的氢气罐会自动补充）',
        'item.heliosphere.jetpack.keys': '空中按住跳跃上升%s', 'item.heliosphere.jetpack.keys_hold': '，移动键侧推，潜行悬停',
        'message.heliosphere.jetpack_too_weak': '这里的重力 %sg 超出了这个背包的推力（最多约 %sg）',
    })
    merge('zh_tw', {
        'item.heliosphere.jetpack': '噴氣背包', 'item.heliosphere.jetpack.desc': '地球級重力用（1.6g 以內）：空中按住跳躍上升',
        'item.heliosphere.heavy_jetpack': '重型噴氣背包', 'item.heliosphere.heavy_jetpack.desc': '高重力星球用（4g 以內，如木星、土星深處）：推力大，耗氫快',
        'item.heliosphere.rcs_pack': '低重力機動背包', 'item.heliosphere.rcs_pack.desc': '月球、火衛、小行星等極低重力用：可側向推進、潛行懸停，非常省燃料；在地球上推不動你',
        'item.heliosphere.adaptive_jetpack': '自適應噴氣背包', 'item.heliosphere.adaptive_jetpack.desc': '任何重力下自動匹配推力，帶側推與懸停',
        'item.heliosphere.jetpack.fuel': '燃料 %s%%（身上的氫氣罐會自動補充）',
        'item.heliosphere.jetpack.keys': '空中按住跳躍上升%s', 'item.heliosphere.jetpack.keys_hold': '，移動鍵側推，潛行懸停',
        'message.heliosphere.jetpack_too_weak': '這裡的重力 %sg 超出了這個背包的推力（最多約 %sg）',
    })
    merge('en_us', {
        'item.heliosphere.jetpack': 'Jetpack', 'item.heliosphere.jetpack.desc': 'For Earth-class gravity (up to 1.6 g): hold jump in the air to climb',
        'item.heliosphere.heavy_jetpack': 'Heavy-Lift Jetpack', 'item.heliosphere.heavy_jetpack.desc': 'For high-gravity worlds (up to 4 g, deep in Jupiter or Saturn): strong and thirsty',
        'item.heliosphere.rcs_pack': 'Low-Gravity Manoeuvring Pack', 'item.heliosphere.rcs_pack.desc': 'For the Moon, Phobos, asteroids: side thrust, sneak to hold still, very frugal; too weak to lift you on Earth',
        'item.heliosphere.adaptive_jetpack': 'Adaptive Jetpack', 'item.heliosphere.adaptive_jetpack.desc': 'Matches its thrust to any gravity, with side thrusters and hold',
        'item.heliosphere.jetpack.fuel': 'Fuel %s%% (hydrogen canisters you carry refill it)',
        'item.heliosphere.jetpack.keys': 'Hold jump in the air to climb%s', 'item.heliosphere.jetpack.keys_hold': ', movement keys push sideways, sneak holds still',
        'message.heliosphere.jetpack_too_weak': 'Gravity here (%s g) is beyond this pack (about %s g at most)',
    })


if __name__ == '__main__':
    for k, (t, f, n, a) in KINDS.items():
        icon(k, t, f, n, a); worn(k, t, f, n, a)
    data(); lang()
    print('jetpack assets written')
