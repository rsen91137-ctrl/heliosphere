#!/usr/bin/env python3
"""More of the ores everyone needs (overlay 02-materials): copies the released placed and configured ore features and
raises the veins per chunk and the vein size, most for the ores used all through the tech tree (graphite above all),
less for the late, rare ones. Only chunks generated afterwards get them. Run from the repository root."""
import glob, json, os, zipfile

OUT = 'jars/02-materials/src/main/resources/data/heliosphere/worldgen'
# ore: (veins per chunk, vein size, lowest Y, highest Y) - None keeps the released value
RATES = {
    'ore_graphite':        (12, 12, -48, 96),
    'ore_bauxite':         (14, 14, None, None),
    'ore_ilmenite':        (10, 10, None, 64),
    'ore_cassiterite':     (10, 10, None, None),
    'ore_galena':          (10, 10, None, 64),
    'ore_sphalerite':      (9, 10, None, 64),
    'ore_pentlandite':     (10, 10, None, 64),
    'ore_chromite':        (9, 9, None, 64),
    'ore_halite':          (8, 14, None, None),
    'ore_sulfur':          (9, 10, None, 48),
    'ore_fluorite':        (9, 10, None, None),
    'ore_spodumene':       (8, 8, None, None),
    'ore_apatite':         (7, 9, None, None),
    'ore_titanomagnetite': (8, 9, None, 64),
    'ore_molybdenite':     (6, 7, None, 32),
    'ore_cobaltite':       (6, 6, None, 32),
    'ore_monazite':        (5, 6, None, None),
    'ore_wolframite':      (5, 7, None, None),
    'ore_pyrochlore':      (5, 6, None, None),
    'ore_beryl':           (4, 5, None, None),
    'ore_uraninite':       (3, 5, None, None),
    'ore_sperrylite':      (2, 4, None, None),
    'oil_sand':            (8, 26, None, None),
}


def main():
    z = zipfile.ZipFile(glob.glob('base/02-*.jar')[0])
    for name, (count, size, lo, hi) in RATES.items():
        placed = json.loads(z.read(f'data/heliosphere/worldgen/placed_feature/{name}.json'))
        for p in placed['placement']:
            if p['type'] == 'minecraft:count': p['count'] = count
            if p['type'] == 'minecraft:height_range':
                if lo is not None: p['height']['min_inclusive'] = {'absolute': lo}
                if hi is not None: p['height']['max_inclusive'] = {'absolute': hi}
        conf = json.loads(z.read(f'data/heliosphere/worldgen/configured_feature/{name}.json'))
        conf['config']['size'] = size
        for kind, obj in (('placed_feature', placed), ('configured_feature', conf)):
            path = f'{OUT}/{kind}/{name}.json'
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'w') as f: json.dump(obj, f, indent=2); f.write('\n')
    # the scroll's "dig it at Earth Y a ~ b" lines follow the new ranges
    import re
    rp = 'jars/11-guide/src/main/resources/assets/heliosphere/guide/recipes.json'
    rec = json.load(open(rp, encoding='utf-8'))
    changed = 0
    for name, (count, size, lo, hi) in RATES.items():
        k = name[4:] if name.startswith('ore_') else name
        placed = json.loads(z.read(f'data/heliosphere/worldgen/placed_feature/{name}.json'))
        h = [p for p in placed['placement'] if p['type'] == 'minecraft:height_range'][0]['height']
        a = lo if lo is not None else h['min_inclusive']['absolute']
        b = hi if hi is not None else h['max_inclusive']['absolute']
        for e in rec:
            if e['id'] not in (f'heliosphere:raw_{k}', f'heliosphere:{k}', f'heliosphere:{k}_ore', f'heliosphere:deepslate_{k}_ore'): continue
            new = [re.sub(r'地球 Y -?\d+ ~ -?\d+', f'地球 Y {a} ~ {b}', x) for x in e['s']]
            if new != e['s']: e['s'] = new; changed += 1
    with open(rp, 'w', encoding='utf-8') as f: json.dump(rec, f, ensure_ascii=False, indent=1); f.write('\n')
    print(len(RATES), 'ore features written;', changed, 'scroll entries updated')


if __name__ == '__main__':
    main()
