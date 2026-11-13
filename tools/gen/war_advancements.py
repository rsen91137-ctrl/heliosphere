#!/usr/bin/env python3
"""The "War & Exploration" advancement tab (overlay 01-core): advancements with one impossible criterion, granted
from code (com.heliosphere.core.progress.Achievements) when the deed is done; names in three languages."""
import json, os

D = 'jars/01-core/src/main/resources/data/heliosphere/advancement/war'
A = 'jars/01-core/src/main/resources/assets/heliosphere'

# id, parent, icon, frame, zh title, zh desc, tw title, tw desc, en title, en desc, hidden
ADV = [
    ('root', None, 'heliosphere:assault_rifle', 'task', '战争与探索', '机器的战争与星球的深处', '戰爭與探索', '機器的戰爭與星球的深處',
     'War & Exploration', 'The machines\' war and the depths of the planets', False),
    ('first_machine', 'root', 'heliosphere:patrol_drone_deployment', 'task', '废铁', '击毁第一台机器人', '廢鐵', '擊毀第一台機器人',
     'Scrap Metal', 'Destroy your first machine', False),
    ('fabricator', 'first_machine', 'heliosphere:robot_fabricator', 'task', '断其源头', '摧毁一台机器人制造站', '斷其源頭', '摧毀一台機器人製造站',
     'Cut Off at the Source', 'Destroy a robot fabricator', False),
    ('fortress_1', 'fabricator', 'minecraft:beacon', 'goal', '攻陷', '攻破一座机械堡垒', '攻陷', '攻破一座機械堡壘',
     'Breach', 'Take a machine fortress', False),
    ('fortress_10', 'fortress_1', 'minecraft:netherite_ingot', 'challenge', '铁壁倾颓', '攻破一座难度 10 以上的机械城塞', '鐵壁傾頹', '攻破一座難度 10 以上的機械城塞',
     'Iron Walls Fall', 'Take a fortress of difficulty 10 or more', False),
    ('mega_fortress', 'fortress_10', 'minecraft:nether_star', 'challenge', '要塞终结者', '攻破一座难度 13 以上的超大型要塞', '要塞終結者', '攻破一座難度 13 以上的超大型要塞',
     'Fortress Breaker', 'Take a mega-fortress (difficulty 13+)', False),
    ('titan_slayer', 'first_machine', 'heliosphere:titan_walker_deployment', 'challenge', '屠泰坦者', '击毁一台泰坦重装机甲', '屠泰坦者', '擊毀一台泰坦重裝機甲',
     'Titan Slayer', 'Destroy a Titan Heavy Walker', False),
    ('colossus', 'titan_slayer', 'heliosphere:colossus_deployment', 'challenge', '巨像倒下', '击毁末日巨像', '巨像倒下', '擊毀末日巨像',
     'The Colossus Falls', 'Destroy a Doom Colossus', False),
    ('siege', 'first_machine', 'minecraft:shield', 'goal', '守住家园', '击退一次对避难所的围攻', '守住家園', '擊退一次對避難所的圍攻',
     'Hold the Line', 'Repel a siege on your shelter', False),
    ('siege_10', 'siege', 'minecraft:totem_of_undying', 'challenge', '不落之城', '击退十次围攻', '不落之城', '擊退十次圍攻',
     'Unfallen', 'Repel ten sieges', False),
    ('dodge', 'first_machine', 'minecraft:feather', 'goal', '急转规避', '让一枚追踪你的导弹丢失目标', '急轉規避', '讓一枚追蹤你的導彈丟失目標',
     'Break Lock', 'Make a missile chasing you lose track of you', False),
    ('lock_on', 'root', 'heliosphere:smart_launcher', 'task', '锁定，发射', '用制导导弹发射器锁定并击中一个目标', '鎖定，發射', '用制導導彈發射器鎖定並擊中一個目標',
     'Lock and Launch', 'Lock on with the guided missile launcher and hit', False),
    ('sam_kill', 'lock_on', 'heliosphere:buk_telar', 'goal', '天空禁区', '用防空导弹系统击落一架飞行机器人', '天空禁區', '用防空導彈系統擊落一架飛行機器人',
     'No-Fly Zone', 'Shoot down a flying machine with an air-defence system', False),
    ('nuke', 'root', 'heliosphere:nuke_launcher', 'goal', '我已成为死神', '引爆一枚战术核弹', '我已成為死神', '引爆一枚戰術核彈',
     'Destroyer of Worlds', 'Set off a tactical nuke', False),
    ('full_armor', 'root', 'heliosphere:us_ocp_chestplate', 'task', '全副武装', '穿上一整套军用或特种护甲', '全副武裝', '穿上一整套軍用或特種護甲',
     'Fully Kitted', 'Wear a full set of military or special armour', False),
    ('cabin', 'root', 'heliosphere:rocket_qiming_1', 'task', '请系好安全带', '登上火箭座舱', '請繫好安全帶', '登上火箭座艙',
     'Fasten Your Seatbelt', 'Board a rocket\'s cabin', False),
    ('impact_witness', 'root', 'minecraft:magma_block', 'goal', '天火', '亲历一次小行星撞击', '天火', '親歷一次小行星撞擊',
     'Fire from the Sky', 'Live through an asteroid impact', False),
    ('jupiter_ocean', 'cabin', 'heliosphere:metallic_hydrogen', 'challenge', '金属之海', '下潜到木星的金属氢海洋上空', '金屬之海', '下潛到木星的金屬氫海洋上空',
     'Sea of Metal', 'Descend to Jupiter\'s metallic hydrogen ocean', True),
    ('diamond_rain', 'cabin', 'minecraft:diamond', 'goal', '钻石雨', '在冰巨星深处看见钻石落下', '鑽石雨', '在冰巨星深處看見鑽石落下',
     'Raining Diamonds', 'See diamonds fall in the depths of an ice giant', True),
]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def merge(code, entries):
    path = f'{A}/lang/{code}.json'
    have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    have.update(entries)
    write(path, have)


zh, tw, en = {}, {}, {}
for aid, parent, icon, frame, zt, zd, tt, td, et, ed, hidden in ADV:
    disp = {'icon': {'id': icon}, 'title': {'translate': f'advancement.heliosphere.war.{aid}'},
            'description': {'translate': f'advancement.heliosphere.war.{aid}.desc'}, 'frame': frame}
    if aid == 'root':
        disp.update({'background': 'minecraft:block/deepslate_tiles', 'show_toast': False, 'announce_to_chat': False})
    if hidden: disp['hidden'] = True
    obj = {'display': disp, 'criteria': {'done': {'trigger': 'minecraft:tick' if aid == 'root' else 'minecraft:impossible'}},
           'requirements': [['done']]}
    if parent: obj['parent'] = f'heliosphere:war/{parent}'
    write(f'{D}/{aid}.json', obj)
    zh[f'advancement.heliosphere.war.{aid}'] = zt; zh[f'advancement.heliosphere.war.{aid}.desc'] = zd
    tw[f'advancement.heliosphere.war.{aid}'] = tt; tw[f'advancement.heliosphere.war.{aid}.desc'] = td
    en[f'advancement.heliosphere.war.{aid}'] = et; en[f'advancement.heliosphere.war.{aid}.desc'] = ed
merge('zh_cn', zh); merge('zh_tw', tw); merge('en_us', en)
print(len(ADV), 'advancements')
