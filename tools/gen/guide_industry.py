#!/usr/bin/env python3
"""Writes clear descriptions of every industrial facility, ore and raw material into the scroll (overlay 11-guide,
guide/purposes.json, shown on each item's page) and an appendix chapter that summarises the production chains.
The hand-written part says what a thing is and why it matters; the processing chains, power draw, products and
uses are read from the released guide/recipes.json so they always match the game. Run from the repository root."""
import glob, json, os, zipfile

G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'


def released(prefix, path):
    return json.loads(zipfile.ZipFile(glob.glob(f'base/{prefix}-*.jar')[0]).read(path).decode('utf-8'))


ZH = {}
for j in sorted(glob.glob('base/*.jar')):
    z = zipfile.ZipFile(j)
    if 'assets/heliosphere/lang/zh_cn.json' in z.namelist():
        ZH.update(json.loads(z.read('assets/heliosphere/lang/zh_cn.json')))
for f in glob.glob('jars/*/src/main/resources/assets/heliosphere/lang/zh_cn.json'):
    ZH.update(json.load(open(f, encoding='utf-8')))
MC = {'minecraft:water_bucket': '水桶', 'minecraft:iron_ingot': '铁锭', 'minecraft:coal': '煤炭', 'minecraft:redstone': '红石',
      'minecraft:copper_ingot': '铜锭', 'minecraft:gold_ingot': '金锭', 'minecraft:bucket': '桶', 'minecraft:glass': '玻璃',
      'minecraft:sand': '沙子', 'minecraft:quartz': '下界石英', 'minecraft:diamond': '钻石', 'minecraft:charcoal': '木炭',
      'minecraft:lava_bucket': '熔岩桶', 'minecraft:clay_ball': '黏土球', 'minecraft:iron_nugget': '铁粒', 'minecraft:obsidian': '黑曜石',
      'minecraft:emerald': '绿宝石', 'minecraft:lapis_lazuli': '青金石', 'minecraft:bone_meal': '骨粉', 'minecraft:gunpowder': '火药',
      'minecraft:netherite_ingot': '下界合金锭', 'minecraft:amethyst_shard': '紫水晶碎片', 'minecraft:blaze_powder': '烈焰粉',
      'minecraft:ender_pearl': '末影珍珠', 'minecraft:paper': '纸', 'minecraft:string': '线', 'minecraft:leather': '皮革',
      'minecraft:slime_ball': '黏液球', 'minecraft:stick': '木棍', 'minecraft:cobblestone': '圆石', 'minecraft:stone': '石头', 'minecraft:bowl': '碗'}


def name(i):
    ns, p = i.split(':')
    if ns == 'minecraft': return MC.get(i, p)
    return ZH.get(f'block.heliosphere.{p}') or ZH.get(f'item.heliosphere.{p}') or p


R = {e['id']: e for e in released('11', 'assets/heliosphere/guide/recipes.json')}
P = released('11', 'assets/heliosphere/guide/purposes.json')


def fmt_recipe(r):
    ins = ' + '.join(f'{name(i)}×{n}' if n > 1 else name(i) for i, n in r['i'])
    outs = ' + '.join((f'{name(o)}×{n}' if n > 1 else name(o)) + (f'（{int(c * 100)}%）' if c < 1 else '') for o, n, c in r['o'])
    m = r['m']
    where = name(m) if ':' in m else m
    extra = f'，{r["t"]:g} 秒' if r.get('t') else ''
    extra += f'，{r["p"]} FE/t' if r.get('p') else ''
    return f'{where}：{ins} → {outs}（{extra.lstrip("，")}）' if extra else f'{where}：{ins} → {outs}'


def recipes_using(item):
    out = []
    for e in R.values():
        for r in e['r']:
            if any(i == item for i, _ in r['i']) and r not in out: out.append(r)
    return out


def uses_of(item, depth=1, seen=None):
    seen = seen if seen is not None else set()
    out = []
    for u in R.get(item, {}).get('u', []):
        if u in seen: continue
        seen.add(u); out.append(u)
    return out


# ---- hand-written: what each facility does -------------------------------------------------------------------------
MACHINES = {
    'coal_generator': '燃煤发电机：烧煤炭、木炭或其他可燃物发电，是整个工业的起点。把燃料放进槽里，用电缆把电送到旁边的机器。产能低，前期够用，后期换太阳能和储能。',
    'solar_panel': '太阳能板：白天在露天自动发电，夜晚和地下不发电；在月球、火星等没有大气遮挡的地方效率更高。多块并排接电缆，配合储能电池组过夜。',
    'battery_bank': '储能电池组：把多余的电存起来，在发电不足（夜晚、燃料耗尽）时放出，保证机器不断电。串在发电机和机器之间。',
    'cable': '电缆：把发电机、电池组和机器连成电网。相邻放置自动连接；机器只要碰到电网中的任意一段就能取电。',
    'electric_arc_furnace': '电弧炉：用电弧把矿石直接熔炼成金属，是最早能用的电力冶炼设备。铁、铝、铜、镍、锡、铅、锌等普通金属都靠它，部分矿石有更高效的化学路线。',
    'alloy_furnace': '合金冶炼炉：按比例熔合多种金属，产出航天合金：铝锂合金（贮箱）、Ti-6Al-4V 钛合金（结构）、因科镍 718（涡轮叶片）、GRCop 铜合金（燃烧室）和不锈钢。',
    'electrolyzer': '电解槽：用电把化合物拆开：氯碱电解（岩盐 → 烧碱 + 氯气）、霍尔-埃鲁法电解铝、熔盐电解钠和锂。耗电很大，先保证电力。',
    'chemical_reactor': '化工反应釜：几乎所有化学品都从这里出来：硫酸、氧化铝（拜耳法）、冰晶石、碳酸锂、推进剂和炸药等。是从冶金走向航天的关键设备。',
    'refinery': '炼油厂：把油砂和原油炼成燃料（煤油 RP-1 等）、沥青和塑料原料。油砂用铲子挖，在地表浅层的油砂层里。',
    'air_separator': '空气分离装置：把空气冷却分离成液氧、液氮和氩气，液氧是火箭氧化剂，氩气用于保护气熔炼。需要空压力罐装气。',
    'vacuum_furnace': '真空熔炼炉：在真空中熔炼活泼金属，海绵钛 → 钛锭、铌、钨等难熔金属都靠它，防止金属被氧化。',
    'cnc_machine': '精密数控加工中心：把金属锭加工成精密零件：涡轮叶片、喷管、阀门、轴承、齿轮等发动机和结构件。',
    'metal_3d_printer': '金属 3D 打印机：直接打印形状复杂的零件（再生冷却燃烧室、喷注器），是先进发动机必需的设备。',
    'electronics_assembler': '电子装配台：用硅片、铜线、金和稀土等制造电路板、处理器、制导计算机、传感器，火箭、宇航服和防御塔都要用。',
    'assembly_station': '总装台：把零部件组装成大型成品：火箭各级、发动机、着陆器、防御塔和升级套件等，是最常用的终端设备（配方最多）。',
    'centrifuge': '离心机：按密度分离物料：浓缩铀、分离重水、从月壤和矿渣中提取稀有元素。',
    'oxygen_compressor': '氧气压缩机：把氧气压进气罐，给宇航服和氧气扩散器补氧。',
    'oxygen_distributor': '氧气扩散器：向周围封闭空间释放氧气，在月球、火星等没有空气的地方建可以脱下头盔的基地。房间必须密封。',
    'regolith_processor': '月壤处理器：加工月壤，产出月壤玻璃、月壤陶瓷、还原铁、KREEP 精矿和月球稀土，是月面基地的基础工业。',
    'ilmenite_reducer': '钛铁矿氢还原炉：用氢气还原月球钛铁矿，同时得到铁、钛和水（氧），月球就地取氧的核心设备。',
    'vacuum_deposition': '真空沉积炉：利用月面天然高真空制造超纯晶圆，是抗辐射芯片和深空计算机的原料。',
    'isru_electrolyzer': '冰水电解站：把月球/火星的冰融化后电解成氢和氧，就地生产推进剂和呼吸用氧。',
    'atmosphere_extractor': '大气提取器：从火星等行星的大气中抽取二氧化碳、氮气、氩气。',
    'sabatier_reactor': '萨巴蒂尔反应器：二氧化碳 + 氢 → 甲烷 + 水，在火星就地生产甲烷推进剂，返回地球的关键。',
    'suit_fabricator': '宇航服制造台：制造各级宇航服部件、氧气背包和温控系统，去不同星球需要不同等级的防护。',
    'engine_test_stand': '发动机试车台：试车新造的火箭发动机，合格后才能装上火箭；试车失败会损坏发动机。',
    'deep_space_radio_array': '深空射电阵：接收深空信号、解锁远方目标的导航数据，是第二部剧情（比邻星信号）的关键设施。',
    'storm_shield_generator': '风暴护盾发生器：在基地周围展开护盾，挡住沙尘暴、辐射风暴等灾害的伤害。需要持续供电。',
    'launch_pad': '发射台：火箭必须竖立在发射台上才能加注和发射。周围要留出足够空地，起飞时尾焰会烧毁附近的方块。',
    'launch_console': '发射控制台：放在发射台附近，用来检查火箭状态、加注燃料、选择目的地并倒计时发射。',
    'planet_core_drill': '行星核心钻机：钻穿行星地幔取得行星核心样本和稀有物质，是星门和方舟的原料来源。',
}

# ---- hand-written: what each mineral is ------------------------------------------------------------------------------
MINERALS = {
    'bauxite': ('铝土矿', '铝的主要矿石，含水氧化铝。', '铝 → 铝锂合金（火箭贮箱）、钛合金和铝热剂。'),
    'ilmenite': ('钛铁矿', '钛和铁的氧化物，最重要的钛矿。', '钛 → 钛合金结构件、涡轮泵和高级装甲。'),
    'spodumene': ('锂辉石矿', '锂铝硅酸盐，提锂的主要原料。', '锂 → 铝锂合金和锂电池（储能、宇航服、武器）。'),
    'graphite': ('石墨矿', '纯碳的层状晶体。', '电解铝的电极、核反应堆慢化剂、碳纤维和高温喷管。'),
    'pentlandite': ('镍黄铁矿', '铁镍硫化物，最主要的镍矿。', '镍 → 因科镍高温合金（涡轮、燃烧室）和不锈钢。'),
    'chromite': ('铬铁矿', '铬的唯一工业矿石。', '铬 → 不锈钢、因科镍合金和耐腐蚀镀层。'),
    'pyrochlore': ('烧绿石矿', '铌和钽的氧化物。', '铌 → 超导磁体、铌合金喷管和轨道炮线圈。'),
    'cassiterite': ('锡石矿', '二氧化锡，几乎所有锡都来自它。', '锡 → 焊料（电路板）、青铜和电子元件。'),
    'galena': ('方铅矿', '硫化铅，亮灰色立方晶体，常伴生银。', '铅 → 辐射屏蔽、电池和弹药。'),
    'sphalerite': ('闪锌矿', '硫化锌，常伴生镉和镓。', '锌 → 镀锌钢、黄铜和电池。'),
    'molybdenite': ('辉钼矿', '硫化钼，软而有金属光泽的片状矿物。', '钼 → 高温合金、润滑剂和高强度钢。'),
    'wolframite': ('黑钨矿', '铁锰钨酸盐，黑色刀片状晶体。', '钨 → 穿甲弹芯、喷管喉衬和耐高温部件。'),
    'cobaltite': ('辉钴矿', '钴砷硫化物，带粉色调的银白立方晶体。', '钴 → 高温合金、磁体和电池正极。'),
    'sperrylite': ('砷铂矿', '砷化铂，极罕见，在地底最深处。', '铂 → 催化剂（萨巴蒂尔反应、推进剂分解）和精密电子。'),
    'beryl': ('绿柱石矿', '铍铝硅酸盐，绿色六方柱状晶体。', '铍 → 轻质结构件、中子反射层和 X 射线窗口。'),
    'fluorite': ('萤石矿', '氟化钙，紫色或绿色立方晶体。', '氟 → 冰晶石（电解铝助熔剂）、氟化物推进剂和光学元件。'),
    'titanomagnetite': ('钒钛磁铁矿', '含钒和钛的磁铁矿，黑色带蓝灰光泽。', '铁、钒、钛 → 钒钢、高强度结构件和钒电池。'),
    'halite': ('岩盐矿', '氯化钠，白色或粉色立方晶体。', '氯碱电解 → 烧碱、氯气、钠，是化工的源头。'),
    'monazite': ('独居石矿', '稀土磷酸盐，含钍。', '稀土 → 钕铁硼磁铁（电机、轨道炮）；钍 → 核燃料。'),
    'uraninite': ('沥青铀矿', '二氧化铀，黑色沥青状，具有放射性。', '铀 → 浓缩铀（核动力、战术核弹）和钚-238（同位素电池）。'),
    'sulfur': ('硫磺矿', '自然硫，亮黄色。', '硫酸（提锂、化工）、火药和硫化物电池。'),
    'apatite': ('磷灰石矿', '钙的磷酸盐，蓝绿色柱状晶体。', '磷 → 肥料（太空农业）、磷酸和阻燃剂。'),
}
RAW = {'bauxite': 'raw_bauxite', 'ilmenite': 'raw_ilmenite', 'spodumene': 'raw_spodumene', 'graphite': 'graphite',
       'pentlandite': 'raw_pentlandite', 'chromite': 'raw_chromite', 'pyrochlore': 'raw_pyrochlore', 'cassiterite': 'raw_cassiterite',
       'galena': 'raw_galena', 'sphalerite': 'raw_sphalerite', 'molybdenite': 'raw_molybdenite', 'wolframite': 'raw_wolframite',
       'cobaltite': 'raw_cobaltite', 'sperrylite': 'raw_sperrylite', 'beryl': 'beryl', 'fluorite': 'fluorite',
       'titanomagnetite': 'raw_titanomagnetite', 'halite': 'rock_salt', 'monazite': 'raw_monazite', 'uraninite': 'raw_uraninite',
       'sulfur': 'sulfur', 'apatite': 'apatite'}

OTHER = {
    'oil_sand': '油砂：浸透了沥青（重质原油）的砂层，在地表浅层成片出现，用铲子挖。送进炼油厂提炼燃料（RP-1 煤油）、沥青和塑料原料，是第一枚火箭燃料的来源。',
    'hematite_deposit': '赤铁矿沉积（火星）：氧化铁结核，火星红色的来源。挖出赤铁矿结核，可就地炼铁，也是火星曾有液态水的证据。',
    'gypsum_deposit': '石膏沉积（火星）：含水硫酸钙晶体，加热可以释放水分；也用于建材。',
    'perchlorate_deposit': '高氯酸盐沉积（火星）：强氧化性盐类，对人体有毒；可以提取氧气和制造固体推进剂氧化剂。',
    'natron_deposit': '泡碱沉积：天然碳酸钠，可作为化工原料（制碱、玻璃）。',
    'hollows_deposit': '凹陷亮斑（水星）：水星表面挥发性物质升华留下的亮色坑洼，挖出挥发性硫化物。',
    'tholin_deposit': '托林有机质沉积（土卫六/冥王星）：紫外线照射甲烷形成的复杂有机物，可提炼成有机原料和燃料。',
    'meteoric_iron_ore': '陨铁矿（月球）：陨石带来的天然铁镍合金，直接冶炼得到高纯度铁镍，月面早期最方便的金属来源。',
}


DROPS = {'hematite_deposit': 'hematite_nodule', 'gypsum_deposit': 'gypsum_crystal', 'perchlorate_deposit': 'perchlorate_salt',
         'natron_deposit': 'sodium_carbonate', 'hollows_deposit': 'volatile_sulfide', 'tholin_deposit': 'tholin', 'meteoric_iron_ore': 'meteoric_iron'}


def mineral_text(key, ore_entry):
    zh, what, why = MINERALS[key]
    raw = 'heliosphere:' + RAW[key]
    lines = [f'「{zh}」：{what}']
    found = (P.get(ore_entry) or '').split('\n')
    lines += [l for l in found if '生成' in l or '采集工具' in l]
    lines.append(f'挖掘掉落：{name(raw)}。')
    chain = recipes_using(raw)
    if chain:
        lines.append('加工：' + '；'.join(fmt_recipe(r) for r in chain[:4]) + '。')
    products = []
    for r in chain:
        for o, _n, _c in r['o']:
            if o not in products: products.append(o)
    downstream = []
    for o in products:
        for u in uses_of(o):
            if u not in downstream and u not in products: downstream.append(u)
    if downstream:
        lines.append('产物再用于：' + '、'.join(name(u) for u in downstream[:8]) + '。')
    lines.append(f'作用：{why}')
    return '\n'.join(lines)


def machine_text(mid):
    key = mid.split(':')[1]
    lines = [MACHINES.get(key, name(mid))]
    rs = [r for e in R.values() for r in e['r'] if r['m'] == mid]
    uniq = []
    for r in rs:
        if r not in uniq: uniq.append(r)
    if uniq:
        powers = sorted({r['p'] for r in uniq if r.get('p')})
        if powers:
            lines.append(f'耗电：{powers[0]}～{powers[-1]} FE/t（需接入电网）。' if len(powers) > 1 else f'耗电：{powers[0]} FE/t（需接入电网）。')
        outs = []
        for r in uniq:
            for o, _n, _c in r['o']:
                if o not in outs: outs.append(o)
        lines.append(f'共 {len(uniq)} 种配方，产物：' + '、'.join(name(o) for o in outs[:12]) + ('等。' if len(outs) > 12 else '。'))
        lines.append('示例：' + '；'.join(fmt_recipe(r) for r in uniq[:3]) + '。')
    old = P.get(mid, '')
    lines += [l for l in old.split('\n') if '采集工具' in l]
    return '\n'.join(lines)


def raw_text(item):
    e = R[item]
    lines = []
    src = [x.rstrip('。') for x in (e.get('s') or []) if '点击' not in x]
    if src: lines.append('来源：' + '；'.join(src) + '。')
    made = []
    for r in e.get('r') or []:
        if r not in made: made.append(r)
    if made: lines.append('制法：' + '；'.join(fmt_recipe(r) for r in made[:3]) + '。')
    chain = recipes_using(item)
    if chain: lines.append('加工：' + '；'.join(fmt_recipe(r) for r in chain[:4]) + '。')
    uses = e.get('u') or []
    if uses: lines.append('用于：' + '、'.join(name(u) for u in uses[:10]) + ('等。' if len(uses) > 10 else '。'))
    return '\n'.join(lines)


def main():
    out = dict(P)
    changed = 0
    for key in MINERALS:
        for variant in (f'heliosphere:{key}_ore', f'heliosphere:deepslate_{key}_ore'):
            if variant in out or variant in R:
                out[variant] = mineral_text(key, f'heliosphere:{key}_ore'); changed += 1
        raw = 'heliosphere:' + RAW[key]
        if raw in R:
            out[raw] = f'{MINERALS[key][0]}的产物。' + MINERALS[key][1] + '\n' + raw_text(raw) + f'\n作用：{MINERALS[key][2]}'
            changed += 1
    for k, text in OTHER.items():
        i = f'heliosphere:{k}'
        extra = ''
        if i in R and recipes_using(i): extra = '\n' + raw_text(i)
        drop = DROPS.get(k)
        if drop and 'heliosphere:' + drop in R: extra += f'\n挖掘掉落：{name("heliosphere:" + drop)}。\n' + raw_text('heliosphere:' + drop)
        out[i] = text + extra; changed += 1
    machines = {e['id'] for e in R.values() if e['c'] in ('机器', '发射设施')} | {r['m'] for e in R.values() for r in e['r'] if ':' in r['m']}
    for m in sorted(machines):
        out[m] = machine_text(m); changed += 1
    for e in R.values():                          # raw materials of the Moon, Mars and the outer worlds
        if e['c'] in ('月球与火星资源', '外太阳系资源', '化工产品', '金属与合金'):
            t = raw_text(e['id'])
            if t: out[e['id']] = name(e['id']) + '：\n' + t; changed += 1
    os.makedirs(G, exist_ok=True)
    with open(f'{G}/purposes.json', 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    chapter(machines)
    print(changed, 'entries rewritten')


def chapter(machines):
    path = f'{G}/chapters.json'
    ch = json.load(open(path, encoding='utf-8'))
    ch = [c for c in ch if not c['title'].startswith('附录')]
    tips = ['§6如何使用：§r在卷轴的物品页里点任意矿石或机器，都能看到它是什么、怎么加工、产物用在哪里。下面是整条工业链的概览。']
    tips.append('§6电力：§r燃煤发电机（起步）→ 太阳能板（白天，月球/火星效率更高）→ 储能电池组（过夜）；电缆把它们和机器连成电网，机器碰到电网任意一段即可取电。')
    order = ['electric_arc_furnace', 'electrolyzer', 'chemical_reactor', 'refinery', 'air_separator', 'alloy_furnace', 'vacuum_furnace',
             'centrifuge', 'cnc_machine', 'metal_3d_printer', 'electronics_assembler', 'assembly_station', 'engine_test_stand',
             'oxygen_compressor', 'oxygen_distributor', 'suit_fabricator', 'regolith_processor', 'ilmenite_reducer', 'vacuum_deposition',
             'isru_electrolyzer', 'atmosphere_extractor', 'sabatier_reactor', 'deep_space_radio_array', 'planet_core_drill',
             'storm_shield_generator', 'launch_pad', 'launch_console']
    for k in order:
        if k in MACHINES: tips.append('§b' + MACHINES[k].replace('：', '§r：', 1))
    tips.append('§6地球矿产一览：§r' + '；'.join(f'{v[0]}（{v[2].split("。")[0]}）' for v in MINERALS.values()) + '。')
    tips.append('§6' + OTHER['oil_sand'].replace('：', '§r：', 1))
    ch.append({'title': '附录 · 工业设施与矿产手册',
               'intro': '§o所有工业设施的用途、耗电与产物，所有矿石的成分、分布与加工路线。§r',
               'objectives': [], 'tips': tips})
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(ch, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    main()
