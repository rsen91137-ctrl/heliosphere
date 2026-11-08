"""Builds the complete recipe handbook straight from the mod sources, so it can never drift from the game."""
import glob, json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import content as C

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MODULES = ['core', 'sphereworld', 'industry', 'planets', 'rocketry', 'guide']

ZH = {}
for m in MODULES:
    p = f'{ROOT}/{m}/src/main/resources/assets/heliosphere/lang/zh_cn.json'
    if os.path.exists(p):
        for k, v in json.load(open(p, encoding='utf-8')).items():
            parts = k.split('.')
            if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == 'heliosphere':
                ZH[parts[2]] = v
VANILLA = {'WATER_BUCKET': '水桶', 'BUCKET': '空桶', 'GLASS_PANE': '玻璃板', 'GLASS': '玻璃', 'IRON_INGOT': '铁锭', 'COPPER_INGOT': '铜锭',
           'GOLD_INGOT': '金锭', 'REDSTONE': '红石', 'COAL': '煤炭', 'CHARCOAL': '木炭', 'DIAMOND': '钻石', 'QUARTZ': '下界石英', 'SAND': '沙子',
           'CLAY_BALL': '黏土球', 'IRON_BLOCK': '铁块', 'PISTON': '活塞', 'LAPIS_LAZULI': '青金石', 'EMERALD': '绿宝石', 'COPPER_BLOCK': '铜块',
           'FURNACE': '熔炉', 'BLAST_FURNACE': '高炉', 'IRON_BARS': '铁栏杆', 'CRAFTING_TABLE': '工作台', 'REDSTONE_BLOCK': '红石块',
           'LOOM': '织布机', 'STRING': '线', 'LEATHER': '皮革', 'GLOWSTONE_DUST': '萤石粉', 'BONE_MEAL': '骨粉', 'FLINT': '燧石',
           'GUNPOWDER': '火药', 'SLIME_BALL': '黏液球', 'OBSIDIAN': '黑曜石', 'GRAVEL': '沙砾', 'COBBLESTONE': '圆石', 'STONE': '石头',
           'RAW_IRON': '粗铁', 'RAW_COPPER': '粗铜', 'RAW_GOLD': '粗金', 'NETHERITE_SCRAP': '下界合金碎片', 'BLAZE_POWDER': '烈焰粉'}


def name(token):
    token = token.strip()
    m = re.match(r'I\("([\w]+)"\)', token)
    if m:
        return ZH.get(m.group(1), m.group(1))
    m = re.match(r'Items\.(\w+)', token)
    if m:
        return VANILLA.get(m.group(1), m.group(1).lower())
    m = re.match(r'T\("([\w:/]+)"\)', token)
    if m:
        return f'任意 #{m.group(1)}'
    return token


def split_args(s):
    out, depth, cur = [], 0, ''
    for ch in s:
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        if ch == ',' and depth == 0:
            out.append(cur); cur = ''
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return [a.strip() for a in out]


def chain_calls(expr):
    """Yields (method, args) for r().a(..).b(..)..."""
    i = 0
    while True:
        m = re.compile(r'\.(\w+)\(').search(expr, i)
        if not m:
            return
        j, depth = m.end(), 1
        while depth and j < len(expr):
            if expr[j] == '(':
                depth += 1
            elif expr[j] == ')':
                depth -= 1
            j += 1
        yield m.group(1), split_args(expr[m.end():j - 1])
        i = j


# machine variables -> machine id
VARS = {}
java_files = []
for m in MODULES:
    java_files += glob.glob(f'{ROOT}/{m}/src/main/java/**/*.java', recursive=True)
for f in java_files:
    src = open(f, encoding='utf-8').read()
    for mm in re.finditer(r'(\w+)\s*=\s*MachineType\.\w+\("(\w+)"', src):
        VARS[mm.group(1)] = mm.group(2)
    for mm in re.finditer(r'(\w+)\s*=\s*(?:MachineType\.get|M)\("(\w+)"\)', src):
        VARS[mm.group(1)] = mm.group(2)

RECIPES = []  # (machine, inputs[(name,n)], outputs[(name,n,chance)], seconds, power, notes)
for f in java_files:
    src = open(f, encoding='utf-8').read()
    for mm in re.finditer(r'(M\("(\w+)"\)|(\w+))\.recipe\(r\(\)', src):
        machine = mm.group(2) or VARS.get(mm.group(3))
        if machine is None:
            continue
        j, depth = mm.end() - 3, 0
        start = mm.end() - 3  # at "r()"
        k = mm.end() - len('r()') - 1  # index of "(" after recipe
        depth, j = 1, k + 1
        while depth and j < len(src):
            if src[j] == '(':
                depth += 1
            elif src[j] == ')':
                depth -= 1
            j += 1
        body = src[k + 1:j - 1]
        loop = None
        if 'I(engine)' in body:
            pre = src[max(0, mm.start() - 400):mm.start()]
            lm = re.findall(r'new String\[\]\{([^}]*)\}', pre)
            loop = re.findall(r'"(\w+)"', lm[-1]) if lm else []
        for eng in (loop or [None]):
            b = body.replace('I(engine)', f'I("{eng}")') if eng else body
            ins, outs, secs, power, notes = [], [], 0, 0, []
            for meth, args in chain_calls(b):
                if meth == 'in':
                    ins.append((name(args[0]), int(args[1]) if len(args) > 1 else 1))
                elif meth == 'out':
                    outs.append((name(args[0]), int(args[1]) if len(args) > 1 else 1, 1.0))
                elif meth == 'chance':
                    outs.append((name(args[0]), int(args[1]), float(args[2].rstrip('f'))))
                elif meth == 'time':
                    secs = int(args[0]) / 20
                elif meth == 'power':
                    power = int(args[0])
                elif meth == 'precise':
                    notes.append('精密件：产出带公差，需上试车台/检验')
                elif meth == 'tested':
                    notes.append('试车：通过后部件标记「已试车」')
                elif meth == 'fail':
                    notes.append(f'失败率 {float(args[0].rstrip("f")) * 100:.0f}%')
                elif meth == 'when':
                    cm = re.search(r'condition\.heliosphere\.(\w+)', args[0])
                    notes.append({'needs_air': '只能在有氧大气中运行（地球）'}.get(cm.group(1), cm.group(1)) if cm else '有运行条件')
            RECIPES.append((machine, ins, outs, secs, power, notes))

PART_IDS = {p[0] for p in C.PARTS}
PART_NAMES = {ZH.get(p, p) for p in PART_IDS}
MACHINE_NAMES = {m[1]: m[2] for m in C.MACHINES}


def fmt_in(ins):
    return ' + '.join(f'{n}×{c}' if c != 1 else n for n, c in ins)


def fmt_out(outs):
    return ' + '.join((f'{n}×{c}' if c != 1 else n) + (f'（{p * 100:.0f}% 几率）' if p < 1 else '') for n, c, p in outs)


L = []
L.append('# Heliosphere 完整配方手册\n')
L.append('本手册由模组源码自动生成，与游戏内数据完全一致。时间单位为秒，耗电单位为 EU/tick。机器界面右侧同样列出该机器的全部配方。\n')

# --- 1. rocket parts
L.append('\n## 一、火箭部件（全部）\n')
L.append('| 部件 | 机器 | 原料 | 时间 | 耗电 | 备注 |\n|---|---|---|---|---|---|')
for mch, ins, outs, secs, power, notes in RECIPES:
    if any(o[0] in PART_NAMES for o in outs):
        L.append(f'| **{fmt_out(outs)}** | {MACHINE_NAMES.get(mch, mch)} | {fmt_in(ins)} | {secs:g} | {power} | {"；".join(notes)} |')

# --- 2. blueprints
bp_src = open(f'{ROOT}/rocketry/src/main/java/com/heliosphere/rocketry/physics/Blueprints.java', encoding='utf-8').read()
codes = dict(re.findall(r"CODES\.put\('(\w)', PartSpec\.(\w+)\)", bp_src))
spec_ids = dict(re.findall(r'public static final PartSpec (\w+) = reg\(b\("(\w+)"', open(f'{ROOT}/rocketry/src/main/java/com/heliosphere/rocketry/physics/PartSpec.java', encoding='utf-8').read()))
L.append('\n## 二、预设火箭逐层拼装图\n')
L.append('从下往上逐层摆放，每层为俯视图（上北下南），「.」为空。整枚火箭要立在一片相连、同高度的发射台中央；发射控制台放在与发射台相连的位置（可以直接放在角上那块发射台的顶上）。\n')
for bm in re.finditer(r'reg\(new Blueprint\("(\w+)", "([^"]+)", "([^"]+)", new String\[\]\[\]\{(.*?)\}\)\);', bp_src, re.S):
    bid, nm, desc, layers_src = bm.groups()
    layers = [re.findall(r'"([^"]*)"', l) for l in re.findall(r'L\(([^)]*)\)', layers_src)]
    L.append(f'\n### {nm}（{desc}）\n')
    used = set()
    for y, rows in enumerate(layers):
        cells = ' / '.join(rows)
        L.append(f'- 第 {y + 1} 层：`{cells}`')
        used.update(ch for r in rows for ch in r if ch != '.')
    L.append('\n图例：' + '，'.join(f'`{ch}` = {ZH.get(spec_ids.get(codes.get(ch, ""), ""), ch)}' for ch in sorted(used)))
    count = {}
    for rows in layers:
        for r in rows:
            for ch in r:
                if ch != '.':
                    count[ch] = count.get(ch, 0) + 1
    L.append('\n部件清单：' + '，'.join(f'{ZH.get(spec_ids.get(codes.get(ch, ""), ""), ch)} ×{n}' for ch, n in sorted(count.items(), key=lambda x: -x[1])) + '\n')

# --- 3. all machine recipes grouped
L.append('\n## 三、全部加工配方（按机器）\n')
by = {}
for r in RECIPES:
    by.setdefault(r[0], []).append(r)
for mch in [m[1] for m in C.MACHINES] + [k for k in by if k not in MACHINE_NAMES]:
    if mch not in by:
        continue
    L.append(f'\n### {MACHINE_NAMES.get(mch, mch)}\n')
    L.append('| 产物 | 原料 | 时间 | 耗电 | 备注 |\n|---|---|---|---|---|')
    for _, ins, outs, secs, power, notes in by[mch]:
        L.append(f'| {fmt_out(outs)} | {fmt_in(ins)} | {secs:g} | {power} | {"；".join(notes)} |')

# --- 4. crafting table recipes
L.append('\n## 四、工作台 / 熔炉配方\n')
for m in MODULES:
    for f in sorted(glob.glob(f'{ROOT}/{m}/src/main/resources/data/heliosphere/recipe/*.json')):
        j = json.load(open(f, encoding='utf-8'))
        res = j.get('result', {})
        rid = res.get('id', '') if isinstance(res, dict) else str(res)
        rn = ZH.get(rid.split(':')[-1], rid)
        cnt = res.get('count', 1) if isinstance(res, dict) else 1
        def key_name(v):
            if isinstance(v, list):
                v = v[0]
            if isinstance(v, dict):
                v = v.get('item') or v.get('tag', '')
            v = str(v)
            if v.startswith('#'):
                return '任意 ' + v
            ns, _, p = v.partition(':')
            return ZH.get(p, p) if ns == 'heliosphere' else VANILLA.get(p.upper(), p)
        t = j.get('type', '')
        if t.endswith('crafting_shaped'):
            grid = ' / '.join(r.replace(' ', '.') for r in j['pattern'])
            keys = '，'.join(f'`{k}`={key_name(v)}' for k, v in j['key'].items())
            L.append(f'- **{rn}**{"×" + str(cnt) if cnt != 1 else ""}：有序合成 `{grid}`；{keys}')
        elif t.endswith('crafting_shapeless'):
            L.append(f'- **{rn}**{"×" + str(cnt) if cnt != 1 else ""}：无序合成 ' + ' + '.join(key_name(i) for i in j['ingredients']))
        elif t.endswith('smelting') or t.endswith('blasting'):
            L.append(f'- **{rn}**：{"高炉" if "blast" in t else "熔炉"}烧炼 {key_name(j["ingredient"])}')

# --- 0. ores
L.append('\n## 六、地球矿石分布\n')
L.append('| 矿石 | 掉落 | 高度范围（Y） | 所需工具 |\n|---|---|---|---|')
for o in C.ORES:
    L.append(f'| {o[1]} | {o[4]} | {o[8]} ~ {o[9]} | {{"stone": "石镐", "iron": "铁镐", "diamond": "钻石镐"}}.get(o[12], o[12]) |'.replace('{{', '{').replace('}}', '}') if False else f'| {o[1]} | {o[4]} | {o[8]} ~ {o[9]} | ' + {"stone": "石镐", "iron": "铁镐", "diamond": "钻石镐", "wood": "木镐"}.get(o[12], o[12]) + ' |')

# --- 5. planet resources
L.append('\n## 五、各天体特产方块与掉落\n')
L.append('| 方块 | 掉落 |\n|---|---|')
for row in C.WORLD_TERRAIN:
    bid, zh, drop = row[0], row[1], row[9]
    if drop == 'none':
        continue
    L.append(f'| {zh} | {(ZH.get(drop[0], drop[0]) + f" ×{drop[1]}~{drop[2]}") if drop else "方块本身"} |')
L.append('\n- 液态甲烷（土卫六的海）：手持空罐右键 → 液甲烷罐。\n- 气态巨行星的云：手持空罐右键 → 氢气罐；木星和土星的云有 12% 几率得到氦-3 罐。')

out_dir = sys.argv[1] if len(sys.argv) > 1 else f'{ROOT}/build'
os.makedirs(out_dir, exist_ok=True)
open(f'{out_dir}/Heliosphere-配方手册.md', 'w', encoding='utf-8').write('\n'.join(L) + '\n')
print('recipes', len(RECIPES), 'lines', len(L))
