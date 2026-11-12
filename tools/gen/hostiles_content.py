#!/usr/bin/env python3
"""Writes the data of the enemy expansion: the bestiary (guide/enemies.json, overlay 11-guide), a new tip in
the scroll's first chapter, the language entries and creative deployer models for the new machines (overlay
07-hostiles). Reads the released files from base/ so the numbers stay in step. Run from the repository root."""
import glob, io, json, os, re, zipfile

G = 'jars/11-guide/src/main/resources/assets/heliosphere'
H = 'jars/07-hostiles/src/main/resources/assets/heliosphere'


def released(prefix, path):
    return json.loads(zipfile.ZipFile(glob.glob(f'base/{prefix}-*.jar')[0]).read(path).decode('utf-8'))


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


# released id -> (health, damage) after the rebalance; must match EnemyRoster.REBALANCE
REBALANCE = {
    'patrol_drone': (36, 4), 'assault_drone': (66, 7), 'siege_drone': (120, 17), 'missile_drone': (160, 22),
    'hunter_drone': (220, 26), 'security_walker': (85, 8), 'repair_automaton': (60, 0), 'arc_hound': (72, 11),
    'emp_spider': (100, 7), 'siege_walker': (280, 21), 'rail_sentinel': (360, 34),
}
WEAPON_NOTES = {
    'patrol_drone': '机炮改为三连发：第一发之后 0.15 秒、0.3 秒各补射一发，后两发略有散布。',
    'assault_drone': '机炮改为三连发：第一发之后 0.15 秒、0.3 秒各补射一发，后两发略有散布。',
    'security_walker': '手炮改为三连发，近距离很难全部躲开。',
    'missile_drone': '导弹改为成对发射：第二枚在 0.4 秒后升空，两枚都会追踪。',
    'hunter_drone': '导弹改为成对发射：第二枚在 0.4 秒后升空，两枚都会追踪。',
    'rail_sentinel': '轨道炮弹丸命中处会炸开冲击波，躲在掩体后也可能被余波擦伤。',
}

# new machines: id, name, family, day, hp, armour, damage, model to borrow for the deployer, text
NEW = [
    ('swarm_drone', 'VI型蜂群自爆机', '无人机', 20, 12, 0, 16, 'patrol_drone',
     '一次出现 5 架，体型很小、速度快。贴近目标 2.8 格内引爆，半径约 3.6 格内造成最高 16 点伤害并击飞，自身同时被摧毁。'
     '应对：保持移动，用霰弹枪、冲锋枪或范围武器在它们贴近前清掉；别让它们在你身边扎堆。'),
    ('mortar_walker', '迫击炮机甲', '地面机械', 28, 140, 12, 16, 'siege_walker',
     '在 56 格内曲射炮弹，会预判你的移动方向。落点会提前 2.5 秒出现红色闪烁的预警圈，圈越闪越快；炮弹在半径 4.5 格内爆炸，最高 16 点伤害。'
     '应对：看到红圈立刻离开，不要原地对枪；它近身很弱，冲上去用高伤害武器快速击毁。'),
    ('flame_walker', '焚化者步行机', '地面机械', 35, 150, 12, 5, 'siege_walker',
     '背负燃料罐，向 7 格内前方 25 度锥形范围持续喷火，每 0.25 秒造成 5 点伤害并点燃 5 秒。'
     '应对：拉开距离，它无法远程攻击；水和防火药水能救命。'),
    ('shield_bearer', '壁垒护盾机', '地面机械', 42, 170, 16, 0, 'repair_automaton',
     '本身不攻击，每秒给 12 格内的友军机械施加「抗性 II」，并叠加最多 8 点吸收护盾，护盾出现时会显示蓝色光罩。'
     '应对：优先击毁它，否则周围所有机械都会变得极其耐打；穿甲武器（狙击、轨道炮）对它最有效。'),
    ('laser_drone', '光矛狙击无人机', '无人机', 50, 110, 8, 30, 'hunter_drone',
     '在 80 格外锁定目标：先射出一道红色瞄准线并发出升调提示音，0.8 秒后发射光矛，造成 30 点伤害并点燃。开火时如果你已经躲到方块后面，光矛只会打在掩体上。'
     '应对：看到红线立刻进掩体或横移；它血量不高，用狙击枪反制。'),
    ('stalker', '幽影刺客', '地面机械', 55, 90, 6, 18, 'security_walker',
     '7 格内没有玩家、且最近 3 秒没被打中时会隐身，只剩一层微光。贴近后显形并用双刃斩击，造成 18 点伤害、流血 3 秒（凋零）和减速。'
     '应对：留意空气中的微光和金属脚步声；它血量低，显形瞬间集中火力。'),
    ('titan_walker', '泰坦重装机甲', '地面机械', 90, 650, 22, 28, 'siege_walker',
     '后期最强的地球机械。交替使用两种武器：等离子连射（3 发，每发在半径 3.2 格内爆炸）和导弹齐射（4 枚追踪导弹，发射时屏幕会提示）。'
     '应对：利用地形分割视线，导弹齐射时躲到坚固掩体后；反物质步枪、轨道炮和奇点炮是主力。'),
]

CULT = [
    ('cult_fanatic', '教团狂信徒', '近战。手持斧头冲锋，伤害高，常打头阵。应对：边退边打，别被围住。'),
    ('cult_raider', '末日教徒', '远程。用弩射击，跟随突袭队行动。应对：用掩体，优先击杀，避免被多人集火。'),
    ('cult_priest', '教团祭司', '施法者。召唤尖牙从地下刺出，还会召唤恼鬼。应对：最高优先级，靠近后快速击杀。'),
]
CULT_INTRO = ('开始建造火箭后，末日教团会派突袭队袭击你（每次 3～6 人）；再往后会截获他们的通讯，暴露据点坐标。'
              '据点有重兵把守，摧毁据点核心（战术核弹也行）后教团在这一带瓦解，并能缴获铀、钚和炸药。')


def bestiary():
    base = released('11', 'assets/heliosphere/guide/enemies.json')
    out = []
    for e in base:
        rid = e['id'].split(':')[1]
        if rid in REBALANCE:
            hp, dmg = REBALANCE[rid]
            for k in ('summary', 'description'):
                e[k] = re.sub(r'生命\d+', f'生命{hp}', e[k])
                e[k] = re.sub(r'伤害\d+(\.\d+)?', f'伤害{dmg}', e[k], count=1)
            if rid in WEAPON_NOTES:
                e['description'] += '\n§6武器强化：§r' + WEAPON_NOTES[rid]
        out.append(e)
    for rid, name, family, day, hp, armour, dmg, _, text in NEW:
        stats = f'第{day}天起 · 生命{hp} · 护甲{armour}' + (f' · 伤害{dmg}' if dmg else ' · 不攻击')
        out.append({'id': f'heliosphere:{rid}', 'name': name, 'family': family, 'summary': stats,
                    'description': stats + '。' + text + '\n可用创造模式物品栏「科技敌人」中的部署器召唤测试。'})
    for rid, name, text in CULT:
        out.append({'id': f'heliosphere:{rid}', 'name': name, 'family': '末日教团', 'summary': '人类敌人 · 突袭与据点',
                    'description': text + '\n' + CULT_INTRO})
    write(f'{G}/guide/enemies.json', out)
    return out


def chapters():
    base = released('11', 'assets/heliosphere/guide/chapters.json')
    tip = ('§c机械敌人升级：§r第 20 天起出现成群自爆的蜂群无人机，之后陆续出现迫击炮机甲（看红色预警圈）、焚化者（喷火）、'
           '护盾机（先打它）、光矛狙击机（看到红色瞄准线就躲）、隐身的幽影刺客，第 90 天起是泰坦重装机甲。'
           '每种敌人的数值、武器和应对方法见卷轴顶部「工程手册 → 敌对生物」。')
    if tip not in base[0]['tips']:
        base[0]['tips'].append(tip)
    write(f'{G}/guide/chapters.json', base)


def lang_and_models():
    zh, en, tw = {}, {}, {}
    en_names = {'swarm_drone': 'Type VI Swarm Drone', 'mortar_walker': 'Mortar Walker', 'flame_walker': 'Incinerator Walker',
                'shield_bearer': 'Bulwark Shield Unit', 'laser_drone': 'Lance Sniper Drone', 'stalker': 'Shade Stalker',
                'titan_walker': 'Titan Heavy Walker'}
    tw_names = {'swarm_drone': 'VI型蜂群自爆機', 'mortar_walker': '迫擊砲機甲', 'flame_walker': '焚化者步行機',
                'shield_bearer': '壁壘護盾機', 'laser_drone': '光矛狙擊無人機', 'stalker': '幽影刺客', 'titan_walker': '泰坦重裝機甲'}
    for rid, name, *_ in NEW:
        zh[f'entity.heliosphere.{rid}'] = name
        zh[f'item.heliosphere.{rid}_deployment'] = f'{name}部署器（创造）'
        en[f'entity.heliosphere.{rid}'] = en_names[rid]
        en[f'item.heliosphere.{rid}_deployment'] = f'{en_names[rid]} Deployer (Creative)'
        tw[f'entity.heliosphere.{rid}'] = tw_names[rid]
        tw[f'item.heliosphere.{rid}_deployment'] = f'{tw_names[rid]}部署器（創造）'
    zh['message.heliosphere.titan_salvo'] = '§c§l⚠ 泰坦导弹齐射！立刻寻找掩体'
    en['message.heliosphere.titan_salvo'] = '§c§l⚠ Titan missile salvo! Take cover now'
    tw['message.heliosphere.titan_salvo'] = '§c§l⚠ 泰坦導彈齊射！立刻尋找掩體'
    write(f'{H}/lang/zh_cn.json', zh)
    write(f'{H}/lang/en_us.json', en)
    write(f'{H}/lang/zh_tw.json', tw)
    z = zipfile.ZipFile(glob.glob('base/07-*.jar')[0])
    for rid, *_, borrow, _text in [(n[0], n[7], n[8]) for n in NEW]:
        model = json.loads(z.read(f'assets/heliosphere/models/item/{borrow}_deployment.json'))
        write(f'{H}/models/item/{rid}_deployment.json', model)
        write(f'{H}/items/{rid}_deployment.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{rid}_deployment'}})


if __name__ == '__main__':
    entries = bestiary()
    chapters()
    lang_and_models()
    print(len(entries), 'bestiary entries')
