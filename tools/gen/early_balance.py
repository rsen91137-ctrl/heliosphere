#!/usr/bin/env python3
"""Bestiary figures of the machines made gentler for the first months (EnemyRoster.REBALANCE and NEW): rewrites the
"生命 · 护甲 · 伤害" figures in the scroll's enemies.json. Must match EnemyRoster. Run from the repository root after
the other bestiary generators."""
import json, re

P = 'jars/11-guide/src/main/resources/assets/heliosphere/guide/enemies.json'
STATS = {  # id: (health, armour or None to keep, damage)
    'patrol_drone': (30, None, 3.5), 'assault_drone': (54, None, 5.5), 'security_walker': (70, None, 6.5),
    'repair_automaton': (52, None, 0), 'arc_hound': (64, None, 9), 'emp_spider': (95, None, 6.5),
    'siege_drone': (110, None, 15), 'missile_drone': (155, None, 21),
    'swarm_drone': (12, 0, 12), 'mortar_walker': (130, 11, 14), 'flame_walker': (145, 11, 4.5),
    # mid and late game, and the bosses (health past 1024 now holds)
    'missile_drone': (200, None, 21), 'hunter_drone': (340, None, None), 'siege_walker': (440, None, None), 'rail_sentinel': (620, None, None),
    'shield_bearer': (240, None, None), 'laser_drone': (170, None, None), 'stalker': (140, None, None), 'drill_walker': (460, None, None),
    'titan_walker': (1200, None, None), 'strike_drone': (280, None, None), 'strike_jet': (520, None, None), 'plasma_drone': (450, None, None),
    'graviton_walker': (800, None, None), 'phase_stalker': (360, None, None),
    'fortress_overseer': (10000, None, None), 'hive_carrier': (14000, None, None), 'colossus': (25000, None, None),
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
        if dmg is not None: t = re.sub(r'(导弹)?伤害\d+(\.\d+)?', lambda m: f'{m.group(1) or ""}伤害{fmt(dmg)}', t, count=1)
        e[k] = t
    if rid == 'swarm_drone': e['description'] = e['description'].replace('最高 16 点伤害', '最高 12 点伤害').replace('最高 10 点伤害', '最高 12 点伤害')
    if rid == 'mortar_walker': e['description'] = e['description'].replace('最高 16 点伤害', '最高 14 点伤害').replace('最高 12 点伤害', '最高 14 点伤害')
with open(P, 'w', encoding='utf-8') as f:
    json.dump(d, f, ensure_ascii=False, indent=2); f.write('\n')
print('bestiary figures updated')
