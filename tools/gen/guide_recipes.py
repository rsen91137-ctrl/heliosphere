#!/usr/bin/env python3
"""
Brings the Star Scroll's encyclopedia (guide/recipes.json), item pages (guide/purposes.json) and chapters up to date
with everything the overlays added: air defence, turrets, armour, jetpacks, the new generators and batteries, the
guns renamed after the player's sketches. Machine recipes are taken from the running game (the "dump" part of the
world test writes jars/testrun/run-fx/game-recipes.json); crafting-table recipes from the overlays' data. Every item
of the mod is checked: one with neither a recipe nor a source is reported.

Run from the repository root, after tools/gen/hostiles_content.py (which rewrites chapters.json):
    python3 tools/gen/guide_recipes.py
"""
import glob, json, os, sys, zipfile

G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'
DUMP = 'jars/testrun/run-fx/game-recipes.json'


def released(path):
    jar = glob.glob('base/11-*.jar')[0]
    return json.loads(zipfile.ZipFile(jar).read(path))


def write(path, obj, indent=None):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        if indent: f.write('\n')


H = lambda s: s if ':' in s else 'heliosphere:' + s

# ---- categories and sources of the new items -------------------------------------------------------------------
SAM_VEHICLES = ['buk_telar', 'buk_tads', 'samp_tel', 'samp_tads', 'iris_tel', 'iris_tads']
SAM_PARTS = ['sam_chassis', 'phased_array_antenna', 'fire_control_computer', 'launcher_module']
ROUNDS = ['missile_9m317ma', 'missile_aster30', 'missile_iris_t_sl']
POWER = ['wind_turbine', 'geothermal_generator', 'rtg', 'hydrogen_fuel_cell', 'fission_reactor', 'lithium_battery_cabinet', 'supercapacitor_bank', 'grid_storage_array']
JETPACKS = ['jetpack', 'heavy_jetpack', 'rcs_pack', 'adaptive_jetpack']
ARMOUR_SETS = ['us_ocp', 'ru_ratnik', 'pla_starry', 'swat', 'eod', 'ghillie', 'hazmat', 'exo', 'alien']
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']

CATEGORY = {}
for i in SAM_VEHICLES + ROUNDS + ['sam_ammo_crate', 'sam_battery', 'creative_ammo_box', 'autocannon_turret', 'flak_turret', 'laser_turret', 'sonic_turret',
          'missile_turret', 'rail_turret', 'tesla_turret', 'turret_kit_mk1', 'turret_kit_mk2', 'turret_kit_mk3', 'turret_kit_mk4', 'turret_kit_mk5']: CATEGORY[i] = '武器与弹药'
for i in SAM_PARTS: CATEGORY[i] = '零部件'
for i in POWER: CATEGORY[i] = '机器'
for i in JETPACKS + ['sam_wrench']: CATEGORY[i] = '工具装备'
for s in ARMOUR_SETS:
    for p in PIECES: CATEGORY[f'{s}_{p}'] = '工具装备'

SOURCES = {
    'fallout_ash': ['战术核弹爆炸后落在爆心周围的辐射尘（不能合成，用铲子挖取）'],
    'trinitite': ['核爆坑底被烧成玻璃的沙土（三位一体石，用镐挖取）'],
    'scorched_earth': ['核爆与小行星撞击烧焦的地表（用铲子挖取）'],
    'deep_haze': ['气态巨行星（木星、土星）大气深处的雾层（星球结构的一部分）'],
    'hydrogen_sulfide_cloud': ['气态巨行星的硫化氢云层（星球结构的一部分）'],
    'water_cloud': ['气态巨行星的水云层（星球结构的一部分）'],
    'ionic_ocean': ['冰巨星（天王星、海王星）深处的离子海（星球结构的一部分）'],
    'metallic_hydrogen': ['木星约 3000 格深处的金属氢海（星球结构的一部分）'],
    'robot_fabricator': ['机械堡垒里的机器人制造站：不能获取，摧毁它们才能攻破堡垒'],
    'creative_ammo_box': ['创造模式专用：在创造物品栏中取得；生存模式请用弹药和枪匠台'],
    'point_defence_launcher': ['只出现在 7 级以上机械堡垒的塔楼和主楼楼顶：不能获取，摧毁它'],
}
SPAWNER = ['创造模式专用：放出对应的机器敌人（测试和做地图用）；生存模式里它们由机器自己派来']

PURPOSES = {
    'buk_telar': '「山毛榉-M3」发射车：6 枚 9M317MA，自带跟踪雷达（无雷达车时 96 格，联网 500 格）。对地面使用放下；空手左键上车，右键控制面板（自动/手动、敌我识别）。手动模式炮塔跟随视角，可转到任意方向发射。',
    'buk_tads': '「山毛榉-M3」雷达车：搜索半径 520 格，为 48 格内同系统的发射车提供目标。',
    'samp_tel': '「SAMP/T」紫菀-30 发射车：8 联装垂直发射，无自带传感器，需雷达车联网（500 格）。',
    'samp_tads': '「SAMP/T」雷达车：搜索半径 520 格。',
    'iris_tel': '「IRIS-T SLM」发射车：8 联装，红外导引头（无雷达车时 64 格，联网 500 格），低烟尾迹。',
    'iris_tads': '「IRIS-T SLM」雷达车：搜索半径 520 格。',
    'sam_chassis': '防空车底盘：所有防空车辆的四大总成之一。',
    'phased_array_antenna': '相控阵天线：雷达车和发射车的探测总成。',
    'fire_control_computer': '火控计算机：防空车辆的指挥总成。',
    'launcher_module': '发射模块：发射车的发射总成。',
    'missile_9m317ma': '9M317MA 防空导弹：「山毛榉」发射车用，放进 16 格内的防空弹药箱，发射车会自动补弹。',
    'missile_aster30': '紫菀-30 防空导弹：SAMP/T 发射车用。',
    'missile_iris_t_sl': 'IRIS-T SL 防空导弹：IRIS-T SLM 发射车用。',
    'sam_ammo_crate': '防空弹药箱：手持导弹右键装入；16 格内的发射车自动从箱中补弹（像战争雷霆的弹药箱）。',
    'sam_battery': '防空电池组：20 格内的防空车无线供电，无需电缆；顶部太阳能板白天自充，出厂满电。',
    'sam_wrench': '回收扳手：右键防空车、电池组或弹药箱，连同导弹、电量、弹药一起收回背包，方便换地方部署。',
    'wind_turbine': '风力发电机：最高约 90 FE/t（雷雨天更多），越高越强，周围 2 格要空旷；没有大气的星球不能用。电缆连接用电设备。',
    'geothermal_generator': '地热发电机：放在岩浆或岩浆块上方（下方 4 格内），最高 160 FE/t，昼夜不停。',
    'rtg': '放射性同位素热电机：手持钚-238 热源右键装填（最多 4 份，每份约 5 天），90 FE/t 昼夜不停，月夜和外太阳系的好帮手。',
    'hydrogen_fuel_cell': '氢燃料电池：手持氢气罐右键装填（最多 16 罐，每罐 1 分钟），240 FE/t，只在需要电的时候烧。',
    'fission_reactor': '小型裂变反应堆：手持浓缩铀燃料芯块右键装填（最多 8 份，每份 1 天），640 FE/t；旁边必须有水冷却，否则停机。',
    'lithium_battery_cabinet': '锂电池柜：储能 800 万 FE，最大输出 1000 FE/t。电缆接上即可充放电，电池之间不互相倒电。',
    'supercapacitor_bank': '超级电容组：储能 150 万 FE，但瞬时输出高达 6000 FE/t，适合给大功率机器和防御塔顶峰值。',
    'grid_storage_array': '大型储能阵列：储能 5000 万 FE，最大输出 3000 FE/t。',
    'jetpack': '喷气背包（胸甲槽）：地球级重力（1.6g 以内），空中按住跳跃上升。背包里的氢气罐自动补充燃料。',
    'heavy_jetpack': '重型喷气背包：高重力星球用（4g 以内，如木星、土星深处），推力大、耗氢快。',
    'rcs_pack': '低重力机动背包：月球、火卫、小行星等极低重力用；移动键侧向推进，潜行悬停防止飘走，非常省燃料；在地球上推不动你。',
    'adaptive_jetpack': '自适应喷气背包：任何重力下自动匹配推力，带侧推与悬停。',
    'point_defence_launcher': '要塞点防御拦截阵地：7 级以上机械堡垒的防空。9 级 2 座、11 级 3 座……15 级 6 座，射程 200~440 格，每座 4~8 发拦截弹、慢慢装填；'
                              '会把飞向堡垒的战术核弹（齐射两发）和各种导弹在空中击毁，核弹不会核爆。先摧毁它们，或趁装填时用多枚导弹饱和攻击。',
}

# the guns rebuilt after the player's sketches, and the rotary cannon that became a heavy machine gun
RENAMES = [('PDW短突击枪', 'C-32冲锋枪'), ('C-36卡宾枪', 'C-36X卡宾枪'), ('BR-762战斗步枪', 'LT-77步枪'), ('LMG-60轻机枪', 'WRK-4通用机枪'),
           ('DMR-20精确射手步枪', 'M445半自动步枪'), ('M-80旋转机炮', 'HM14.5/1930重机枪')]
GUN_FIRST = {'assault_rifle': 'LT-70突击步枪', 'bolt_rifle': 'M77A栓动步枪'}
HMG = ('HM14.5/1930重机枪（14.5×130.5 毫米）：伤害18（穿甲55%），全自动约 6.7 发/秒，无需预热，弹链箱 60 发，后坐力大。'
       '按玩家手稿制作：散热片枪管、提把远射瞄具、铲形握把、折叠三脚架。')


TURRETS = ['autocannon_turret', 'flak_turret', 'laser_turret', 'sonic_turret', 'missile_turret', 'rail_turret', 'tesla_turret']
KITS = ['turret_kit_mk1', 'turret_kit_mk2', 'turret_kit_mk3', 'turret_kit_mk4', 'turret_kit_mk5']

CHAPTERS = [
    {'title': '第一部 · 外篇一 · 电网与喷气背包',
     'intro': '§o工程日志：远征越走越远，煤炉和太阳能板已经喂不饱整座基地。月夜要熬十四天，木星深处重力是地球的两倍半，小行星上一跳就会飘走。§r\n\n'
              '新的发电机和储能设备都用电缆连接，接上就能用；喷气背包要按星球的重力选型。每样东西的配方和全部材料都能在配方百科（H）里一路查到原矿。',
     'objectives': POWER + JETPACKS,
     'tips': [
         '电缆：任意长度的电缆网络都能把电送到头（过去电缆超过约两百格、末端机器就收不到电，这个问题已经修好）。网络里先供机器，余电充进电池，电池之间不互相倒电。',
         '风力发电机（钢板、铜导线、钕铁硼磁铁、钢锭、电路板）：放得越高越强，周围 2 格保持空旷，雷雨天发电最多；没有大气的星球（月球、水星……）不能用。',
         '地热发电机（因科镍板、单晶涡轮叶片、铜导线、电动机、钢板）：放在岩浆或岩浆块上方 4 格以内，最高 160 FE/t。适合下界、金星、木卫一。',
         'RTG 放射性同位素热电机（铅锭、钨锭、电路板、铜导线）：手持「钚-238 热源」（离心机处理黄饼得到）右键装填，一份可用约 5 天，昼夜不停——月球的长夜和外太阳系都靠它。',
         '氢燃料电池（不锈钢板、石墨、铜导线、电路板、锂电池芯）：手持氢气罐（电解槽电解水，或在气态巨行星云层里用空罐收集）右键装填。只在需要电时才烧。',
         '小型裂变反应堆（不锈钢板、铅锭、石墨、处理器、因科镍板、单晶涡轮叶片）：浓缩铀燃料芯块（离心机处理黄饼）右键装填，640 FE/t；旁边必须有水冷却，否则自动停机。',
         '储能：锂电池柜 800 万 FE；超级电容组 150 万 FE，但瞬时输出 6000 FE/t，适合顶住大功率机器和防御塔的峰值；大型储能阵列（四个锂电池柜合成）5000 万 FE。',
         '喷气背包穿在胸甲槽，空中按住跳跃上升，身上的氢气罐会自动加进油箱。标准型：地球级重力（1.6g 以内）；重型：高重力（4g 以内，如木星深处）；'
         '低重力机动背包：月球、火卫、小行星——移动键侧推，潜行悬停，非常省油，但在地球上推不动你；自适应型：任何重力自动匹配推力。',
     ]},
    {'title': '第一部 · 外篇二 · 防御塔',
     'intro': '§o工程日志：机器越来越多，夜里的围攻也越来越凶。§r\n\n七种防御塔各有专长，用电缆接上电网就能工作；升级套件让它们射得更远、打得更狠。创造模式放下的防御塔不需要电，直接开火。',
     'objectives': TURRETS + KITS,
     'tips': [
         '机炮塔：射程 48 格、射速最快，打地面杂兵；防空高炮塔：64 格，只打无人机等空中目标；激光塔：40 格；声波塔：22 格近距离范围攻击；特斯拉线圈塔：24 格近距离电击；导弹塔：90 格，地面和空中都打，一个目标只派一枚导弹；电磁轨道塔：120 格远距离重击，耗电最多。',
         '供电：电缆接到任意发电机或电池即可，网络多长都行；也可以在旁边放储能设备。创造模式放置的塔永远满电。',
         '升级套件 Mk Ⅰ~Ⅴ：手持右键防御塔升级（射程、伤害、射速、储能），Mk Ⅳ 需要外星合金与外星芯片，Mk Ⅴ 需要零点能电池等终局材料。',
     ]},
    {'title': '第一部 · 外篇三 · 防空系统',
     'intro': '§o情报：机器的无人机群和超视距打击越来越多，单靠防御塔守不住整片基地。§r\n\n三套防空系统（山毛榉-M3、SAMP/T、IRIS-T SLM）各由雷达车和发射车组成，覆盖半径 500 格。车辆很强，所以要先造四大总成：底盘、相控阵天线、火控计算机、发射模块；导弹弹药则很简单。',
     'objectives': SAM_PARTS + SAM_VEHICLES + ROUNDS + ['sam_ammo_crate', 'sam_battery', 'sam_wrench'],
     'tips': [
         '部署：手持车辆对着地面使用，车会朝你面对的方向放下；它是一辆真车，受重力、能开动。发射车在 48 格内找同系统的雷达车联网，射程 500 格；没有雷达车时山毛榉靠自带雷达（96 格），IRIS-T 靠红外导引头（64 格），SAMP/T 必须联网。',
         '供电与补弹：「防空电池组」20 格内无线供电；导弹放进 16 格内的「防空弹药箱」（手持导弹右键箱子装入），发射车会自动补弹。创造模式放下的车辆不需要电。',
         '操控：空手左键车辆上车，右键打开控制面板切换自动/手动和敌我识别。手动模式下天空中会出现雷达框，R 切换目标，F 发射；山毛榉的炮塔跟随你的视角转到任意方向。W/A/S/D 开车（先收起发射架），停车 2 秒自动展开，Shift 下车。',
         '敌我识别：开启时只打机器和怪物；关闭后手动模式可以锁定任何看得见的玩家（地面或空中），自动模式只打空中的玩家且不打车主。防空车只打空中目标，不会浪费导弹在僵尸和地面机器人身上。',
         '回收扳手：右键车辆、电池组或弹药箱，连同导弹、电量、弹药一起收回背包。车辆停放时会保持周围区块加载，主人走远了也照样警戒。',
         '拦截：防空车会优先拦截飞来的机器导弹。反过来，7 级以上的机械堡垒楼顶布有「要塞点防御拦截阵地」（越高级越多，最多 6 座，射程最远 440 格），'
         '会在空中击落你的战术核弹和导弹（核弹不会核爆）——先用枪和炸药摧毁楼顶的阵地，或趁它们装填时多枚导弹齐射饱和攻击。',
     ]},
    {'title': '第一部 · 外篇四 · 军用护甲',
     'intro': '§o军需处：地面的仗越打越硬，宇航服挡不住子弹。§r\n\n九套军用与特种护甲，强度在下界合金附近，各有专长，在宇航服制造台（没有时用总装台）制作；每套四件的全部材料见配方百科。',
     'objectives': [f'{s}_chestplate' for s in ARMOUR_SETS],
     'tips': [
         '美军 OCP、俄军「战士」、星空迷彩：全面的步兵护甲；SWAT：防弹插板；EOD 排爆服：极高的爆炸防护但很重；吉利服：伏在草丛里机器很难发现你；防化服：防辐射与毒气；外骨骼：力量与负重；外星护甲：终局，需要外星芯片。',
     ]},
]


def chapters():
    path = f'{G}/chapters.json'
    ch = json.load(open(path, encoding='utf-8'))
    titles = {c['title'] for c in CHAPTERS}
    ch = [c for c in ch if c['title'] not in titles]
    at = next((i for i, c in enumerate(ch) if c['title'].startswith('第二部')), len(ch))
    ch[at:at] = [dict(c) for c in CHAPTERS]
    write(path, ch, indent=1)
    print('chapters:', len(ch))


def recipe_files():
    """Crafting recipes of the overlays, then of the released jars (the overlays win)."""
    seen = set()
    for path in glob.glob('jars/*/src/main/resources/data/heliosphere/recipe/*.json'):
        seen.add(os.path.basename(path)); yield json.load(open(path, encoding='utf-8'))
    for jar in sorted(glob.glob('base/*.jar')):
        z = zipfile.ZipFile(jar)
        for n in z.namelist():
            if n.startswith('data/heliosphere/recipe/') and n.endswith('.json') and os.path.basename(n) not in seen:
                yield json.loads(z.read(n))


def crafting():
    """Crafting-table recipes: id -> list of guide recipes."""
    out = {}
    for r in recipe_files():
        if r.get('type') != 'minecraft:crafting_shaped': continue
        key = {c: (v if isinstance(v, str) else v[0] if isinstance(v, list) and v and isinstance(v[0], str) else str(v)) for c, v in r['key'].items()}
        grid = [[key[c] if c in key else '' for c in row.ljust(3)] for row in r['pattern']]
        while len(grid) < 3: grid.append(['', '', ''])
        counts = {}
        for row in grid:
            for c in row:
                if c: counts[c] = counts.get(c, 0) + 1
        res = r['result']
        out.setdefault(res['id'], []).append({'m': '工作台（有序）', 'i': [[k, v] for k, v in counts.items()],
                                              'o': [[res['id'], res.get('count', 1), 1.0]], 'g': grid})
    return out


def main():
    base = released('assets/heliosphere/guide/recipes.json')
    by = {e['id']: e for e in base}
    if not os.path.exists(DUMP):
        sys.exit(f'{DUMP} missing: run the world test part "dump" first')
    dump = json.load(open(DUMP, encoding='utf-8'))
    machine = {}
    for r in dump['machines']:
        for o in r['o']:
            if o: machine.setdefault(o[0], []).append({'m': r['m'], 'i': r['i'], 'o': r['o'], 't': r['t'], 'p': r['p']})
    craft = crafting()

    def same(a, b): return a['m'] == b['m'] and sorted(map(tuple, a['i'])) == sorted(map(tuple, b['i']))

    added, missing = [], []
    for gid in dump['items']:
        e = by.get(gid)
        new = e is None
        if new:
            short = gid.split(':')[1]
            e = {'id': gid, 'c': CATEGORY.get(short, '其他物品'), 'r': [], 's': list(SOURCES.get(short, SPAWNER if short.endswith('_deployment') else [])), 'u': []}
        for r in machine.get(gid, []) + craft.get(gid, []):
            if not any(same(r, x) for x in e['r']): e['r'].append(r)
        if new:
            by[gid] = e; added.append(gid)
        if not e['r'] and not e['s']: missing.append(gid)
    # uses: every recipe's inputs point to what it makes
    for e in by.values():
        for r in e['r']:
            for i in r['i']:
                src = i[0]
                if src in by and e['id'] not in by[src]['u'] and src != e['id']: by[src]['u'].append(e['id'])
    entries = [by[e['id']] for e in base] + [by[g] for g in added]
    write(f'{G}/recipes.json', entries)
    print(f'encyclopedia: {len(base)} released + {len(added)} new entries')
    print('no recipe and no source:', [m for m in missing])

    # item pages
    pp = f'{G}/purposes.json'
    purposes = json.load(open(pp, encoding='utf-8')) if os.path.exists(pp) else released('assets/heliosphere/guide/purposes.json')
    for k, v in list(purposes.items()):
        for a, b in RENAMES: v = v.replace(a, b)
        purposes[k] = v
    for k, name in GUN_FIRST.items():
        v = purposes.get(H(k), '')
        if v and not v.startswith(name): purposes[H(k)] = name + v[v.index('：'):] if '：' in v else name + '：' + v
    purposes['heliosphere:rotary_cannon'] = HMG
    for k, v in PURPOSES.items(): purposes[H(k)] = v
    write(pp, purposes, indent=1)
    print('item pages:', len(purposes))


if __name__ == '__main__':
    main()
    chapters()
