#!/usr/bin/env python3
"""Data of the two strike aircraft (StrikeCraft, overlay 07-hostiles): language entries merged into the overlay's
lang files, deployer item definitions (the icons come from tools/gen/deployer_icons.py and the "icons" test part),
their bestiary entries in the scroll (overlay 11-guide, guide/enemies.json) and a tip in the scroll's first
chapter. Merges into the existing files, so it can be run again; run it after hostiles_content.py, which rewrites
the bestiary and the 07 language files from scratch. Run from the repository root."""
import json, os

H = 'jars/07-hostiles/src/main/resources/assets/heliosphere'
G = 'jars/11-guide/src/main/resources/assets/heliosphere'


def load(path, default):
    if not os.path.exists(path): return default
    with open(path, encoding='utf-8') as f: return json.load(f)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


NAMES = {
    'strike_drone': ('VIII型察打无人机', 'Type VIII Strike Drone', 'VIII型察打無人機'),
    'strike_jet': ('鹞隼固定翼攻击机', 'Falcon Fixed-Wing Attack Jet', '鷂隼固定翼攻擊機'),
}
MESSAGES = {
    'message.heliosphere.standoff_launch': (
        '⚠ %s 在 %s 格外发射了超视距导弹！躲进有顶的建筑，或用防空系统拦截',
        '⚠ %s fired a stand-off missile from %s blocks! Get under a roof or intercept it',
        '⚠ %s 在 %s 格外發射了超視距導彈！躲進有頂的建築，或用防空系統攔截'),
}

# must match EnemyRoster (day, health, armour, damage) and TIER
ENTRIES = [
    ('strike_drone', '无人机', 209, 180, 10, 30, 7,
     '§c超视距打击。§r爬升到目标上方约 70 格，在 190 格外绕圈盘旋，每 7～10 秒从翼下挂架发射一枚防区外导弹（射程 320 格，'
     '发射时屏幕会提示来袭方向和距离，导弹逼近时有警报）。它几乎从不进入你的视距，普通枪械很难够到。'
     '只有你站在露天（头顶能看到天空）或在它视线内时才会开火——躲进有屋顶的建筑就是掩体。'
     '应对：建造防空系统（雷达车+发射车），它们会自动击落无人机并拦截来袭导弹；也可以用制导导弹发射器锁定它。'
     '近距离时它会用机炮四连发。'),
    ('strike_jet', '固定翼', 250, 320, 14, 36, 8,
     '§c超视距打击。§r不会悬停：以恒定高速飞行，转弯半径大。从约 230 格外对准你俯冲进入，一次投放两枚防区外导弹，'
     '随即掉头脱离到 330 格外，绕一圈后再次进入，循环往复（每轮约 15 秒）。导弹会炸毁方块。'
     '只有你露天或在它视线内时才会投弹，屋顶就是掩体。'
     '应对：防空系统是最有效的反制——发射车会优先拦截来袭导弹，再追击飞机本体；它脱离时背对你，是制导导弹追尾的好时机。'
     '偶尔贴近时会用机炮扫射。'),
]


def lang():
    for i, code in enumerate(('zh_cn', 'en_us', 'zh_tw')):
        path = f'{H}/lang/{code}.json'
        d = load(path, {})
        for rid, names in NAMES.items():
            d[f'entity.heliosphere.{rid}'] = names[i]
            d[f'item.heliosphere.{rid}_deployment'] = names[i] + ('部署器（创造）', ' Deployer (Creative)', '部署器（創造）')[i]
        for k, v in MESSAGES.items(): d[k] = v[i]
        write(path, d)


def items():
    for rid in NAMES:
        if not os.path.exists(f'{H}/textures/item/{rid}_deployment.png'):
            print('note: no icon yet for', rid, '(run the icons test part and tools/gen/deployer_icons.py)')
        write(f'{H}/models/item/{rid}_deployment.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{rid}_deployment'}})
        write(f'{H}/items/{rid}_deployment.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{rid}_deployment'}})


def bestiary():
    path = f'{G}/guide/enemies.json'
    d = load(path, [])
    d = [e for e in d if e['id'].split(':')[1] not in NAMES]
    worlds = '地球（按天数自然出现）、水星/金星、谷神星/灶神星/木卫、土卫六/土卫二、外太阳系'
    for rid, family, day, hp, armour, dmg, tier, text in ENTRIES:
        stats = f'第{day}天起 · 生命{hp} · 护甲{armour} · 导弹伤害{dmg}'
        fort = '9～15' if tier == 7 else '11～15'
        dossier = (f'\n§b技术等级 {tier}/10§r；堡垒难度 {fort} 的空中护卫（只在堡垒周围 420 格内作战）；可能出现于：{worlds}；'
                   '导弹会炸毁方块，且能炸穿黑曜石和远古残骸；也会参加对避难所的围攻（轰炸型）。'
                   '同一时间在一名玩家附近最多出现 2 架自然生成的打击飞机。')
        d.append({'id': f'heliosphere:{rid}', 'name': NAMES[rid][0], 'family': family, 'summary': stats,
                  'description': stats + '。' + text + '\n可用创造模式物品栏「科技敌人」中的部署器召唤测试。' + dossier})
    write(path, d)
    return len(d)


TIP = ('§c超视距打击：§r第 209 天起会出现 VIII 型察打无人机，第 250 天起出现鹞隼固定翼攻击机。它们在 200～330 格外盘旋或往复进入，'
       '向露天的玩家发射防区外导弹（屏幕会提示来袭）。躲进有屋顶的建筑可以避开；真正的反制是防空系统——'
       '发射车会自动拦截来袭的导弹并击落飞机。')


def chapters():
    path = f'{G}/guide/chapters.json'
    d = load(path, None)
    if not d: return
    tips = d[0]['tips']
    tips[:] = [t for t in tips if not t.startswith('§c超视距打击')]
    tips.append(TIP)
    write(path, d)


if __name__ == '__main__':
    lang(); items(); n = bestiary(); chapters()
    print('strike craft assets written;', n, 'bestiary entries')
