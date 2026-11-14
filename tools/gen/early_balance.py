#!/usr/bin/env python3
"""Bestiary figures of the machines made gentler for the first months (EnemyRoster.REBALANCE and NEW): rewrites the
"生命 · 护甲 · 伤害" figures in the scroll's enemies.json. Must match EnemyRoster. Run from the repository root after
the other bestiary generators."""
import json, re

P = 'jars/11-guide/src/main/resources/assets/heliosphere/guide/enemies.json'
STATS = {  # id: (health, armour or None to keep, damage)
    'patrol_drone': (26, None, 3), 'assault_drone': (46, None, 4.5), 'security_walker': (60, None, 5.5),
    'repair_automaton': (45, None, 0), 'arc_hound': (58, None, 8), 'emp_spider': (90, None, 6),
    'siege_drone': (100, None, 13), 'missile_drone': (150, None, 20),
    'swarm_drone': (12, 0, 10), 'mortar_walker': (120, 10, 12), 'flame_walker': (140, 10, 4),
}


def fmt(x): return str(int(x)) if float(x).is_integer() else str(x)


d = json.load(open(P, encoding='utf-8'))
for e in d:
    rid = e['id'].split(':')[1]
    if rid not in STATS: continue
    hp, arm, dmg = STATS[rid]
    for k in ('summary', 'description'):
        t = e[k]
        t = re.sub(r'生命\d+(\.\d+)?', f'生命{fmt(hp)}', t, count=1)
        if arm is not None: t = re.sub(r'护甲\d+(\.\d+)?', f'护甲{fmt(arm)}', t, count=1)
        t = re.sub(r'(导弹)?伤害\d+(\.\d+)?', lambda m: f'{m.group(1) or ""}伤害{fmt(dmg)}', t, count=1)
        e[k] = t
    if rid == 'swarm_drone': e['description'] = e['description'].replace('最高 16 点伤害', '最高 10 点伤害')
    if rid == 'mortar_walker': e['description'] = e['description'].replace('最高 16 点伤害', '最高 12 点伤害')
with open(P, 'w', encoding='utf-8') as f:
    json.dump(d, f, ensure_ascii=False, indent=2); f.write('\n')
print('bestiary figures updated')
