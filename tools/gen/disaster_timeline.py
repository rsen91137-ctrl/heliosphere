#!/usr/bin/env python3
"""The scroll's disaster timeline (guide/timeline.json, the "灾变时程" page) on the 365-day road to the impact.

The released page counted the days of a 100-day game. The disasters still come in the last hundred days before the
impact (the released ImpactWinter starts its prelude 100 days before the deadline): with the asteroid due on day 365
that is acid rain from day 270, thunderstorms from 275, quakes from 280, frost from 285, floods from 295, wildfires
from 310 and the alternating phase from 325. Also: the acid cloth is now translucent, and the page gets what this
pack added after the impact (the dimension collapse, the magma tide, the machines' last months). Run from the
repository root; overwrites the overlay's timeline.json from the released one."""
import glob, json, zipfile

OUT = 'jars/11-guide/src/main/resources/assets/heliosphere/guide/timeline.json'
DEADLINE = 365
OFFSET = DEADLINE - 100           # the released days counted from the start of a 100-day game

base = json.loads(zipfile.ZipFile(glob.glob('base/11-*.jar')[0]).read('assets/heliosphere/guide/timeline.json'))
CLOTH = ('防酸覆布现在是半透明的薄布（和地毯一样薄），阳光能透过：铺在屋顶或空地上方都能挡酸雨，布下面的太阳能板照常发电，室内也不会变暗。')

out = []
for e in base:
    day, title, short, text, items = e
    if day == '60–100': day = f'{60 + OFFSET}–{DEADLINE - 1}'
    elif day == '100': day = str(DEADLINE)
    elif day.isdigit(): day = str(int(day) + OFFSET)
    if 'heliosphere:acid_roof_cloth' in items: text = text + ' ' + CLOTH
    out.append([day, title, short, text, items])

# insert the pack's own entries before the first impact, and after it
first = next(i for i, e in enumerate(out) if e[0] == str(DEADLINE))
out.insert(first, [f'{DEADLINE - 3}', '下界与末地崩塌预警',
                   '撞击前 3 天起离开下界和末地，并把重生点设在主世界。',
                   '撞击那一刻，与主世界相连的下界和末地会一起崩塌：留在里面的玩家会死亡，设在那里的重生点会被抹去。撞击前 3 天、1 天以及最后 10、5、2、1 分钟和 30 秒，'
                   '身处下界或末地的玩家都会收到警报。撞击之后所有下界和末地传送门都会失效，点亮或触碰会让它碎裂爆炸。',
                   ['minecraft:obsidian', 'minecraft:ender_eye']])
out.insert(0, ['216–364', '机械的最后几个月',
                   '布防空：核弹井、超视距打击、高血量机械。',
                   '第 216 天（进化第 10 阶段）起，9 级以上机械堡垒的核弹井开始工作：加注燃料 5 分钟并全程警报，只有防空车能在空中拦截，拆掉核弹井可以中止发射。'
                   '每次围攻都会带来超视距察打无人机和固定翼攻击机，迫击炮和攻城炮弹可以被防空高炮塔在空中打掉；第 12 阶段起，突击和炮击围攻的最后一波会出现泰坦，'
                   '猛攻会出现堡垒监管者（第 14 阶段起是蜂巢母舰）。',
                   ['heliosphere:buk_telar', 'heliosphere:buk_tads', 'heliosphere:flak_turret', 'heliosphere:machine_nuke_silo']])
out.append(['主撞击后', '岩浆潮',
            '离开地球，或住进人工建筑上。',
            '撞击之后整颗地球的地表会逐渐被岩浆吞没：撞击点附近一两天内熔化并向下侵蚀约 10 格，越远越慢，半个地球外要几周、只熔化表面一两格。'
            '只有天然地面会熔化：顶上是人造方块（木板、玻璃、混凝土等）的地方和海洋不受影响。多次撞击会叠加。',
            ['minecraft:stone_bricks', 'minecraft:glass', 'heliosphere:launch_console']])
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
    f.write('\n')
print(len(out), 'entries:', [e[0] for e in out])
