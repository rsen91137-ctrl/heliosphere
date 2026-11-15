#!/usr/bin/env python3
"""Tips in the scroll's first chapter for the stage-21 changes: heat recovery from furnaces, how a machine chooses its
recipe, and what destroyed machines drop. Replaces its own tips (by their opening words), so it can be run again.
Run from the repository root, after the other chapter generators."""
import json

P = 'jars/11-guide/src/main/resources/assets/heliosphere/guide/chapters.json'
TIPS = [
    '§6余热发电：§r原版熔炉、烟熏炉和高炉在燃烧燃料时会把余热变成电：熔炉 10 FE/t、烟熏炉 8 FE/t、高炉 15 FE/t（约为燃煤发电机的四分之一）。'
    '把电缆贴着它们铺，电缆会自动接上，电力送往同一电网上的机器和电池组；炉子直接贴着机器也可以。冶炼本身不受影响。',
    '§b机器配方怎么选：§r机器总是做「最具体」的那个配方——用到的原料种类最多、数量最多的那个，而不是排在前面的便宜配方'
    '（以前把栖息地发生器的原料放进总装台会做出喷管）。原料完全相同的配方已经改成各不相同：COPV 改为钢板×2+铜线×4，鼻锥改为钢板×3+铁锭×2，'
    '三套迷彩护甲分别多加一个棕色/绿色/青色染料。要做小配件时，只放它需要的原料即可。行星地核钻机会按所在星球自动选对应的地核。',
    '§a总装台启动按钮：§r总装台不会在你放原料的途中自己开工。把原料全部放好后，点界面上的「▶ 开始」：按此刻放入的原料选定配方，'
    '原料够就一直做下去，用完自动停下，等你下一次按开始；「■」立即停止（进度清零，不消耗原料）。其他机器仍然放入原料就自动工作。',
    '§d机械掉落：§r被摧毁的机械按技术等级掉落：低级机械掉少量零件，5 级以上掉处理器、钛合金、外星合金与芯片、防御塔升级套件和枪械配件，'
    '7～8 级掉反物质电池、下界合金锭、零点能量核和 Mk IV～V 套件，偶尔整把武器；Boss 必掉一把对应等级的武器和一批稀有零件，末日巨像还掉下界之星。'
    '高级机械只掉自己等级附近的东西，不会再掉一堆基础材料。堡垒制造站造出的机械和没有玩家参与击杀（防御塔、防空、坠落）的机械掉落减半。'
    '堡垒与围攻奖励箱同样只装接近该难度的物品。',
]


def main():
    d = json.load(open(P, encoding='utf-8'))
    tips = d[0]['tips']
    heads = tuple(t.split('：')[0] for t in TIPS)
    tips[:] = [t for t in tips if not t.startswith(heads)] + TIPS
    with open(P, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=2); f.write('\n')
    print(len(TIPS), 'tips written')


main()
