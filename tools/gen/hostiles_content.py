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

# machines of the outer worlds and the fortresses: id, name, en, tw, family, tier, hp, armour, damage, borrow, text
OUTER = [
    ('plasma_drone', 'VII型等离子无人机', 'Type VII Plasma Drone', 'VII型等離子無人機', '无人机', 7, 260, 18, 30, 'hunter_drone',
     '只在火星及更远的星球和堡垒中出现。在 64 格内发射青色等离子弹，命中点半径约 3 格爆炸，最高 30 点伤害并击退。'
     '应对：等离子弹飞行较慢，横移可躲；它装甲厚，用穿甲或爆炸武器。'),
    ('graviton_walker', '引力碾压机甲', 'Graviton Crusher', '引力碾壓機甲', '地面机械', 8, 420, 22, 26, 'siege_walker',
     '背部的引力环会把 24 格内的目标往自己身边拖拽，贴近后造成 26 点碾压伤害并施加缓慢 III。'
     '应对：被拖拽时别往它的方向跑，向侧面拉开；末影珍珠、鞘翅或高跳能脱离；远距离集火。'),
    ('phase_stalker', '相位刺客', 'Phase Stalker', '相位刺客', '地面机械', 8, 200, 14, 30, 'security_walker',
     '能在空间中跃迁，瞬间出现在目标身后，随后一记斩击造成 30 点伤害。跃迁前会有紫色闪光。'
     '应对：看到紫光立刻转身后退；背靠墙壁让它无法绕后；血量不高，显形后集中火力。'),
    ('fortress_overseer', '堡垒监管者', 'Fortress Overseer', '堡壘監管者', 'Boss', 9, 1500, 24, 34, 'rail_sentinel',
     '难度 10 以上的堡垒的守将，会显示 Boss 血条。轮流使用三道带预警线的光矛（每道 34 点伤害）、召唤一波蜂群自爆机，并给自己开启护盾（抗性 II）。'
     '应对：护盾亮起时先清理召唤物；光矛预警线出现就躲到掩体后。击败后才算攻破堡垒。'),
    ('hive_carrier', '蜂巢母舰', 'Hive Carrier', '蜂巢母艦', 'Boss', 9, 2200, 20, 26, 'missile_drone',
     '难度 12 以上的堡垒的守将，会显示 Boss 血条。飞行的巨型母舰，从四个舱口连续放出蜂群自爆机，并发射成组追踪导弹。'
     '应对：防空塔与导弹塔非常有效；带制导导弹发射器锁定它；导弹齐射时躲进坚固建筑。'),
    ('colossus', '末日巨像', 'Doom Colossus', '末日巨像', 'Boss', 10, 4000, 28, 44, 'siege_walker',
     '难度 14 以上的堡垒和外太阳系星球的终极守将，高约 8 格。等离子五连射、践踏地面击飞 14 格内所有目标，以及迫击炮弹雨。'
     '应对：不要贴近（践踏伤害极高）；分散站位躲炮雨；反物质步枪、轨道炮、奇点炮与战术核弹是对付它的手段。'),
]

FORTRESS_TIP = ('§c机械堡垒：§r主世界与所有固态星球上会随机出现机械堡垒（约每 576×576 格区域六成概率），难度 1～15 级。'
                '地球离出生点越远越难（最高 8 级），月球从 4 级起、火星 6 级、木卫与土卫 9～11 级、外太阳系 13 级起。'
                '靠近时会提示难度。堡垒内的「机器人制造站」会持续生产机器人，必须全部摧毁；难度 6 以上还有 Boss。'
                '制造站全部摧毁且 Boss 死亡即攻破，主楼楼顶会出现奖励箱（难度越高奖励越好），地下金库也有物资。')

SIEGE_TIP = ('§c地球上的威胁：§r工业越发达，地下的「半人马先遣机群」越活跃（侦察无人机 → 机械虫群 → 先遣守卫 Boss），'
             '它们掉落外星合金和外星芯片，方舟部件要用。§c机械围攻：§r机器会记住你最常待、箱子熔炉机器最多、床所在的地方，'
             '把它认定为你的避难所；从第 3 天起，每隔 1.5～3 天，只要你在家，就会有一次围攻：侦察骚扰（无人机）、空降突击（空投舱）、'
             '远程炮击（迫击炮，看红圈）、夜间渗透（隐身刺客从近处摸进来）、总攻（多波次并有 Boss）。强度随天数、所在星球和已击退的次数上升；'
             '屏幕上方的血条显示剩余敌人，全部击毁即可获得战利品。建议在家周围布置防御塔。')

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
    for rid, name, _en, _tw, family, tier, hp, armour, dmg, _b, text in OUTER:
        stats = f'威胁等级{tier} · 生命{hp} · 护甲{armour} · 伤害{dmg}'
        out.append({'id': f'heliosphere:{rid}', 'name': name, 'family': family, 'summary': stats,
                    'description': stats + '。' + text + '\n可用创造模式物品栏「科技敌人」中的部署器召唤测试。'})
    out = [e for e in out if e.get('family') != '末日教团']
    write(f'{G}/guide/enemies.json', out)
    return out


def chapters():
    base = released('11', 'assets/heliosphere/guide/chapters.json')
    tip = ('§c机械敌人升级：§r第 20 天起出现成群自爆的蜂群无人机，之后陆续出现迫击炮机甲（看红色预警圈）、焚化者（喷火）、'
           '护盾机（先打它）、光矛狙击机（看到红色瞄准线就躲）、隐身的幽影刺客，第 90 天起是泰坦重装机甲。'
           '每种敌人的数值、武器和应对方法见卷轴顶部「工程手册 → 敌对生物」。')
    for ch in base:                                   # the Doomsday Cult is retired: its tip now tells of the sieges
        ch['tips'] = [SIEGE_TIP if '末日教团' in t else t for t in ch['tips']]
    for t in (tip, FORTRESS_TIP):
        if t not in base[0]['tips']:
            base[0]['tips'].append(t)
    write(f'{G}/guide/chapters.json', base)


FORT_ZH = {
    'block.heliosphere.robot_fabricator': '机器人制造站',
    'message.heliosphere.fortress_detected': '§l⚠ 侦测到机械堡垒 · 难度 %s/15 · 制造站 %s 座§r —— 摧毁所有制造站并击败守将即可攻破',
    'message.heliosphere.fabricator_down': '制造站已摧毁，剩余 %s 座',
    'message.heliosphere.fabricators_down': '所有制造站已摧毁！击败守将即可攻破堡垒',
    'message.heliosphere.fortress_boss_down': '堡垒守将 %s 已被击毁',
    'message.heliosphere.fortress_conquered': '★ 难度 %s 的机械堡垒已攻破！奖励箱位于主楼楼顶 (%s, %s, %s)',
}
FORT_EN = {
    'block.heliosphere.robot_fabricator': 'Robot Fabricator',
    'message.heliosphere.fortress_detected': '§l⚠ Machine fortress detected · difficulty %s/15 · %s fabricators§r — destroy every fabricator and its warden to take it',
    'message.heliosphere.fabricator_down': 'Fabricator destroyed, %s left',
    'message.heliosphere.fabricators_down': 'All fabricators destroyed! Defeat the warden to take the fortress',
    'message.heliosphere.fortress_boss_down': 'Fortress warden %s destroyed',
    'message.heliosphere.fortress_conquered': '★ Difficulty %s machine fortress conquered! Reward cache on the keep roof (%s, %s, %s)',
}
FORT_TW = {
    'block.heliosphere.robot_fabricator': '機器人製造站',
    'message.heliosphere.fortress_detected': '§l⚠ 偵測到機械堡壘 · 難度 %s/15 · 製造站 %s 座§r —— 摧毀所有製造站並擊敗守將即可攻破',
    'message.heliosphere.fabricator_down': '製造站已摧毀，剩餘 %s 座',
    'message.heliosphere.fabricators_down': '所有製造站已摧毀！擊敗守將即可攻破堡壘',
    'message.heliosphere.fortress_boss_down': '堡壘守將 %s 已被擊毀',
    'message.heliosphere.fortress_conquered': '★ 難度 %s 的機械堡壘已攻破！獎勵箱位於主樓樓頂 (%s, %s, %s)',
}


SIEGE_ZH = {
    'event.heliosphere.siege.recon.warn': '§l⚠ 机械侦察队正在接近你的避难所（难度 %s）§r 坐标 %s, %s',
    'event.heliosphere.siege.assault.warn': '§l⚠ 雷达捕获空投舱信号：机械突击队即将空降到你的避难所（难度 %s）§r 坐标 %s, %s',
    'event.heliosphere.siege.bombard.warn': '§l⚠ 机械炮兵正在避难所外架设阵地（难度 %s）§r 坐标 %s, %s —— 留意红色落点预警',
    'event.heliosphere.siege.infiltration.warn': '§l⚠ 侦测到隐身信号……有东西正摸向你的避难所（难度 %s）§r 坐标 %s, %s',
    'event.heliosphere.siege.onslaught.warn': '§l☢ 机械大军总攻！多波次部队与守将正扑向你的避难所（难度 %s）§r 坐标 %s, %s',
    'event.heliosphere.siege.recon.bar': '机械侦察 · 难度 %s',
    'event.heliosphere.siege.assault.bar': '空降突击 · 难度 %s',
    'event.heliosphere.siege.bombard.bar': '远程炮击 · 难度 %s',
    'event.heliosphere.siege.infiltration.bar': '夜间渗透 · 难度 %s',
    'event.heliosphere.siege.onslaught.bar': '机械总攻 · 难度 %s',
    'event.heliosphere.siege.left': ' · 剩余 %s · 第 %s/%s 波',
    'event.heliosphere.siege.eta': '敌军预计 %s 秒后抵达，准备防御！',
    'event.heliosphere.siege.wave': '第 %s/%s 波敌军从%s方逼近！',
    'event.heliosphere.siege.pods': '第 %s/%s 波空投舱从%s方坠落！',
    'event.heliosphere.siege.won': '★ 围攻已击退！战利品掉落在你脚下（已击退 %s 次，之后的进攻会更强）',
    'event.heliosphere.siege.over': '围攻结束，机械部队暂时撤离……它们还会回来。',
}
SIEGE_TW = {k: v.replace('机械', '機械').replace('侦察', '偵察').replace('难度', '難度').replace('坐标', '座標').replace('雷达', '雷達')
            .replace('空投舱', '空投艙').replace('信号', '信號').replace('突击队', '突擊隊').replace('即将', '即將').replace('空降', '空降')
            .replace('炮兵', '砲兵').replace('阵地', '陣地').replace('留意', '留意').replace('预警', '預警').replace('侦测', '偵測')
            .replace('隐身', '隱身').replace('东西', '東西').replace('总攻', '總攻').replace('波次', '波次').replace('部队', '部隊')
            .replace('守将', '守將').replace('扑向', '撲向').replace('避难所', '避難所').replace('击', '擊').replace('剩余', '剩餘')
            .replace('预计', '預計').replace('后', '後').replace('抵达', '抵達').replace('准备', '準備').replace('防御', '防禦')
            .replace('军', '軍').replace('逼近', '逼近').replace('坠落', '墜落').replace('战利品', '戰利品').replace('掉落', '掉落')
            .replace('脚下', '腳下').replace('进攻', '進攻').replace('会', '會').replace('结束', '結束').replace('撤离', '撤離')
            .replace('还', '還').replace('来', '來').replace('渗透', '滲透').replace('远程', '遠程').replace('炮', '砲')
            for k, v in SIEGE_ZH.items()}
SIEGE_EN = {
    'event.heliosphere.siege.recon.warn': '§l⚠ A machine recon party is closing on your shelter (difficulty %s)§r at %s, %s',
    'event.heliosphere.siege.assault.warn': '§l⚠ Drop pod signals on radar: a machine assault force is about to land on your shelter (difficulty %s)§r at %s, %s',
    'event.heliosphere.siege.bombard.warn': '§l⚠ Machine artillery is digging in outside your shelter (difficulty %s)§r at %s, %s — watch for red target rings',
    'event.heliosphere.siege.infiltration.warn': '§l⚠ Cloaked signals detected… something is creeping up on your shelter (difficulty %s)§r at %s, %s',
    'event.heliosphere.siege.onslaught.warn': '§l☢ Machine onslaught! Waves of troops and a warden are bearing down on your shelter (difficulty %s)§r at %s, %s',
    'event.heliosphere.siege.recon.bar': 'Machine Recon · difficulty %s',
    'event.heliosphere.siege.assault.bar': 'Drop Assault · difficulty %s',
    'event.heliosphere.siege.bombard.bar': 'Bombardment · difficulty %s',
    'event.heliosphere.siege.infiltration.bar': 'Infiltration · difficulty %s',
    'event.heliosphere.siege.onslaught.bar': 'Machine Onslaught · difficulty %s',
    'event.heliosphere.siege.left': ' · %s left · wave %s/%s',
    'event.heliosphere.siege.eta': 'Enemy arrival in about %s s. Get ready!',
    'event.heliosphere.siege.wave': 'Wave %s/%s approaching from the %s!',
    'event.heliosphere.siege.pods': 'Wave %s/%s: drop pods falling from the %s!',
    'event.heliosphere.siege.won': '★ Siege repelled! The spoils are at your feet (%s repelled; the next attacks will be stronger)',
    'event.heliosphere.siege.over': 'The siege is over and the machines pull back… for now.',
}


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
    for rid, name, en_name, tw_name, *_ in OUTER:
        zh[f'entity.heliosphere.{rid}'] = name
        zh[f'item.heliosphere.{rid}_deployment'] = f'{name}部署器（创造）'
        en[f'entity.heliosphere.{rid}'] = en_name
        en[f'item.heliosphere.{rid}_deployment'] = f'{en_name} Deployer (Creative)'
        tw[f'entity.heliosphere.{rid}'] = tw_name
        tw[f'item.heliosphere.{rid}_deployment'] = f'{tw_name}部署器（創造）'
    for d, vals in ((zh, FORT_ZH), (en, FORT_EN), (tw, FORT_TW)):
        d.update(vals)
    d_zh = {'east': '东', 'south': '南', 'west': '西', 'north': '北'}
    d_tw = d_zh
    d_en = {k: k.capitalize() for k in d_zh}
    for k in d_zh:
        zh[f'event.heliosphere.dir.{k}'] = d_zh[k]; tw[f'event.heliosphere.dir.{k}'] = d_tw[k]; en[f'event.heliosphere.dir.{k}'] = d_en[k]
    for d, vals in ((zh, SIEGE_ZH), (en, SIEGE_EN), (tw, SIEGE_TW)):
        d.update(vals)
    zh['message.heliosphere.titan_salvo'] = '§c§l⚠ 泰坦导弹齐射！立刻寻找掩体'
    en['message.heliosphere.titan_salvo'] = '§c§l⚠ Titan missile salvo! Take cover now'
    tw['message.heliosphere.titan_salvo'] = '§c§l⚠ 泰坦導彈齊射！立刻尋找掩體'
    write(f'{H}/lang/zh_cn.json', zh)
    write(f'{H}/lang/en_us.json', en)
    write(f'{H}/lang/zh_tw.json', tw)
    z = zipfile.ZipFile(glob.glob('base/07-*.jar')[0])
    for rid, borrow in [(n[0], n[7]) for n in NEW] + [(n[0], n[9]) for n in OUTER]:
        model = json.loads(z.read(f'assets/heliosphere/models/item/{borrow}_deployment.json'))
        write(f'{H}/models/item/{rid}_deployment.json', model)
        write(f'{H}/items/{rid}_deployment.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{rid}_deployment'}})


if __name__ == '__main__':
    entries = bestiary()
    chapters()
    lang_and_models()
    print(len(entries), 'bestiary entries')
