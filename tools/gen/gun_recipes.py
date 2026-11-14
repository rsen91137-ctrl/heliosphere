#!/usr/bin/env python3
"""Crafting-table recipes of the conventional guns and the attachments (overlay 06-weapons, overriding the released
data). The released recipes were one copied pattern: eleven guns shared the same grid (so the table only ever gave the
battle rifle), seven attachments another (only the angled grip came out) and three optics a third. Each now has its
own grid, and the cost follows the gun: a pistol is a barrel, a slide and a grip; the rotary cannon needs motors,
titanium and a battery. Run from the repository root, then tools/gen/guide_recipes.py for the scroll."""
import json, os

OUT = 'jars/06-weapons/src/main/resources/data/heliosphere/recipe'
K = {
    'L': 'heliosphere:steel_ingot',        # barrel
    'S': 'heliosphere:steel_plate',        # receiver, slide
    'P': 'heliosphere:plastic',            # grip, stock
    'I': 'minecraft:iron_ingot',           # trigger group
    'C': 'heliosphere:circuit_board',      # fire control
    'M': 'heliosphere:electric_motor',
    'B': 'heliosphere:battery_cell',
    'T': 'heliosphere:ti64_plate',
    'X': 'heliosphere:processor',
    'W': 'heliosphere:copper_wire',
    'G': 'minecraft:glass_pane',
}
GUNS = {
    'service_pistol':            ['LSS', ' PI'],
    'heavy_pistol':              ['LLS', ' PT'],
    'machine_pistol':            ['LSS', 'CPI'],
    'compact_smg':               ['LSS', ' CP', ' S '],
    'burst_carbine':             ['LLS', 'SCP', '  S'],
    'battle_rifle':              ['LLL', 'SCS', '  P'],
    'designated_marksman_rifle': ['LLL', 'TCS', 'G P'],
    'light_machine_gun':         ['LLL', 'SMS', 'BSP'],
    'pump_shotgun':              ['LL ', 'SPS', '  P'],
    'rotary_cannon':             ['LLL', 'MBM', 'TCT'],
    'gauss_pistol':              ['WWT', 'XBP'],
    'smart_launcher':            ['TTT', 'XCB', '  P'],
}
ATTACHMENTS = {
    'attachment_angled_grip':    ['PS', 'P '],
    'attachment_vertical_grip':  ['S', 'P', 'P'],
    'attachment_extended_mag':   ['S', 'S', 'P'],
    'attachment_drum_mag':       [' S ', 'SWS', ' S '],
    'attachment_muzzle_brake':   ['SLS'],
    'attachment_suppressor':     [' S ', 'SLS', ' S '],
    'attachment_red_dot':        ['SGS', 'WBW'],
    'attachment_scope_4x':       ['GSG', 'SLS'],
    'attachment_scope_8x':       ['GTG', 'TLT', 'GTG'],
}


def recipe(item, pattern):
    used = sorted({c for row in pattern for c in row if c != ' '})
    return {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
            'key': {c: K[c] for c in used}, 'result': {'id': f'heliosphere:{item}', 'count': 1}}


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    seen = {}
    for item, pat in {**GUNS, **ATTACHMENTS}.items():
        sig = tuple(pat)
        assert sig not in seen, (item, seen.get(sig))
        seen[sig] = item
        with open(f'{OUT}/{item}.json', 'w', encoding='utf-8') as f:
            json.dump(recipe(item, pat), f, ensure_ascii=False)
    print(len(seen), 'gun and attachment recipes written')
