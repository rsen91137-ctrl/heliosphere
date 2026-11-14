#!/usr/bin/env python3
"""Data of the anti-burrowing features (Underground, overlay 07-hostiles; PENETRATOR missile, overlay 06): language
entries, the rockbore drill walker's deployer and bestiary entry, and the scroll tip. Merges into existing files;
run after hostiles_content.py and strike_craft_assets.py. Run from the repository root."""
import json, os

H = 'jars/07-hostiles/src/main/resources/assets/heliosphere'
W = 'jars/06-weapons/src/main/resources/assets/heliosphere'
G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'


def load(p, d):
    return json.load(open(p, encoding='utf-8')) if os.path.exists(p) else d


def write(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(o, f, ensure_ascii=False, indent=2); f.write('\n')


LANG = {  # key: (zh, en, tw)
    'entity.heliosphere.drill_walker': ('穿岩钻掘机', 'Rockbore Drill Walker', '穿岩鑽掘機'),
    'item.heliosphere.drill_walker_deployment': ('穿岩钻掘机部署器（创造）', 'Rockbore Drill Walker Deployer (Creative)', '穿岩鑽掘機部署器（創造）'),
    'message.heliosphere.penetrator_launch': ('⚠ 钻地弹来袭！它会钻穿岩层后在深处爆炸——离开地面红圈的正下方',
                                              '⚠ Bunker buster incoming! It bores through rock and goes off deep: get out from under the red ring',
                                              '⚠ 鑽地彈來襲！它會鑽穿岩層後在深處爆炸——離開地面紅圈的正下方'),
    'message.heliosphere.sabotage': ('⚠ 机械正在破坏你地表的设施！（%s, %s, %s）', '⚠ Machines are wrecking your surface installations! (%s, %s, %s)',
                                     '⚠ 機械正在破壞你地表的設施！（%s, %s, %s）'),
    'event.heliosphere.siege.underground': ('机械发现你躲在地下：钻掘机正在打洞，渗透部队从洞穴里摸过来，钻地弹将从天而降！',
                                            'The machines know you are underground: drills are boring in, infiltrators come through the caves, bunker busters are on the way!',
                                            '機械發現你躲在地下：鑽掘機正在打洞，滲透部隊從洞穴裡摸過來，鑽地彈將從天而降！'),
    'event.heliosphere.siege.covered': ('机械发现你躲在屋顶下：钻地弹将从天而降！', 'The machines know you are under a roof: bunker busters are on the way!',
                                        '機械發現你躲在屋頂下：鑽地彈將從天而降！'),
}
LANG.update({
    'event.heliosphere.evolution': ('⚠ 机械进化：第 %s / %s 阶段', '⚠ Machine evolution: stage %s of %s', '⚠ 機械進化：第 %s / %s 階段'),
    'event.heliosphere.evolution.new': ('新出现：%s。围攻约每 %s～%s 天一次；地球上的机械生命 +%s%%，护甲更厚。',
                                        'New: %s. Sieges about every %s-%s days; machines on Earth have +%s%% health and more armour.',
                                        '新出現：%s。圍攻約每 %s～%s 天一次；地球上的機械生命 +%s%%，護甲更厚。'),
    'event.heliosphere.evolution.none': ('（本阶段没有新机型）%s围攻约每 %s～%s 天一次；地球上的机械生命 +%s%%，护甲更厚。',
                                         '(no new machine this stage)%s Sieges about every %s-%s days; machines on Earth have +%s%% health and more armour.',
                                         '（本階段沒有新機型）%s圍攻約每 %s～%s 天一次；地球上的機械生命 +%s%%，護甲更厚。'),
})
MISSILE = ('钻地弹', 'Bunker buster', '鑽地彈')

TIP = ('§c地下不再安全：§r① 地面机械看不见你、又走不过去时，会朝你挖隧道（有裂纹和摩擦声预警），能挖什么取决于技术等级：'
       '5 级以下挖不动黑曜石、哭泣的黑曜石和远古残骸，基岩和强化深板岩谁都挖不动；② 第 82 天起出现「穿岩钻掘机」，'
       '一看不见你就开挖，速度是普通机械的 5 倍，6 级能钻穿黑曜石；③ 你在屋顶下躲太久，打击飞机和围攻会改用钻地弹——'
       '它直奔你的位置，钻穿 10～28 格方块后在深处爆炸，地面上你头顶会出现红色预警圈；④ 避难所在地下时，围攻的渗透部队'
       '和一半地面机械会从附近洞穴里出现；⑤ 围攻中机械 15 秒打不到你，就会去拆你地表的机器、太阳能板和发电设备。'
       '对策：防御塔和防空系统照样有效；用强化深板岩/基岩级材料加固最核心的房间；在隧道口布置火力；别让机器全摆在露天。')


EVO = ('§c机械进化（150 天）：§r小行星在发现后第 150 天撞击地球。机械分 15 个阶段进化，每 10 天一阶，每阶出现一两种新机型并在全服公告：'
       '第 3 天巡逻无人机 → 10 突击无人机 → 14 安保步行机 → 18 维修机器人 → 22 电弧猎犬 → 26 蜂群自爆机 → 32 炮击无人机 → 36 迫击炮机甲 → '
       '40 电磁脉冲蛛机 → 45 焚化者 → 52 制导无人机 → 56 护盾机 → 62 光矛狙击机 → 66 重装攻城机甲 → 72 幽影刺客 → 76 猎杀无人机 → '
       '82 穿岩钻掘机 → 87 察打无人机 → 92 轨道炮哨卫 → 104 固定翼攻击机 → 112 泰坦 → 122 等离子无人机 → 132 相位刺客 → 142 引力碾压机甲。'
       '每进化一阶，地球上的机械生命 +5%、护甲更厚；围攻从每 2.5～3.75 天一次逐步缩短到每 0.5～0.75 天一次（造出防空后再快 40%），'
       '自然出现的机械也越来越频繁；围攻难度 = 进化阶段 + 已击退次数/3。')


def main():
    for i, code in enumerate(('zh_cn', 'en_us', 'zh_tw')):
        p = f'{H}/lang/{code}.json'; d = load(p, {})
        for k, v in LANG.items(): d[k] = v[i]
        write(p, d)
        p = f'{W}/lang/{code}.json'; d = load(p, {})
        d['missile.heliosphere.penetrator'] = MISSILE[i]
        write(p, d)
    write(f'{H}/models/item/drill_walker_deployment.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'heliosphere:item/drill_walker_deployment'}})
    write(f'{H}/items/drill_walker_deployment.json', {'model': {'type': 'minecraft:model', 'model': 'heliosphere:item/drill_walker_deployment'}})
    p = f'{G}/enemies.json'; e = load(p, [])
    e = [x for x in e if x['id'] != 'heliosphere:drill_walker']
    stats = '第82天起 · 生命300 · 护甲18 · 伤害22'
    e.append({'id': 'heliosphere:drill_walker', 'name': '穿岩钻掘机', 'family': '地面机械', 'summary': stats,
              'description': stats + '。专门对付躲在地下的玩家：一看不见你就朝你的位置挖隧道，速度是普通机械的 5 倍，'
              '能钻穿黑曜石（基岩和强化深板岩除外），挖掘时有刺耳的摩擦声和火花。贴近后用钻头造成 22 点伤害并击退。'
              '围攻时如果你躲在地下，每波都会派 1～3 台。应对：听到钻头声就在隧道口埋伏；它在隧道里只能直线前进，'
              '穿甲武器和爆炸物很好用。\n可用创造模式物品栏「科技敌人」中的部署器召唤测试。'
              '\n§b技术等级 6/10§r；堡垒难度 7～15 的守军与制造站产物；也会参加对避难所的围攻。'})
    write(p, e)
    # the 150-day schedule (EnemyRoster.FIRST_DAY and the new roles): every bestiary entry's first day
    import re
    DAYS = {'patrol_drone': 3, 'assault_drone': 10, 'security_walker': 14, 'repair_automaton': 18, 'arc_hound': 22, 'swarm_drone': 26,
            'siege_drone': 32, 'mortar_walker': 36, 'emp_spider': 40, 'flame_walker': 45, 'missile_drone': 52, 'shield_bearer': 56,
            'laser_drone': 62, 'siege_walker': 66, 'stalker': 72, 'hunter_drone': 76, 'drill_walker': 82, 'strike_drone': 87,
            'rail_sentinel': 92, 'strike_jet': 104, 'titan_walker': 112, 'plasma_drone': 122, 'phase_stalker': 132, 'graviton_walker': 142}
    e = load(f'{G}/enemies.json', [])
    for x in e:
        rid = x['id'].split(':')[1]
        if rid not in DAYS: continue
        for k in ('summary', 'description'):
            if re.search(r'第\d+天起', x[k]): x[k] = re.sub(r'第\d+天起', f'第{DAYS[rid]}天起', x[k], count=1)
            elif k == 'summary' or x[k].startswith('威胁等级'):
                x[k] = re.sub(r'^威胁等级\d+', f'第{DAYS[rid]}天起', x[k], count=1)
    write(f'{G}/enemies.json', e)
    p = f'{G}/chapters.json'; ch = load(p, None)
    if ch:
        tips = ch[0]['tips']
        tips[:] = [t for t in tips if not t.startswith('§c地下不再安全')]
        tips.append(TIP)
        tips[:] = [t for t in tips if not t.startswith('§c机械进化')]
        tips.append(EVO)
        fix = {'第 20 天起出现成群自爆的蜂群无人机': '第 26 天起出现成群自爆的蜂群无人机', '第 90 天起是泰坦重装机甲': '第 112 天起是泰坦重装机甲',
               '第 65 天起会出现 VIII 型察打无人机，第 80 天起出现鹞隼固定翼攻击机': '第 87 天起会出现 VIII 型察打无人机，第 104 天起出现鹞隼固定翼攻击机',
               '第 100 天首次主撞击': '第 150 天首次主撞击'}
        for c in ch:
            out = []
            for t in c['tips']:
                for a, b in fix.items(): t = t.replace(a, b)
                out.append(t)
            c['tips'] = out
        write(p, ch)
    print('underground assets written')


if __name__ == '__main__':
    main()
