#!/usr/bin/env python3
"""Survival recipes for the ten pre-assembled rockets (overlay 09-rocketry).

The assembly station has nine input slots and a slot holds at most 64 items, while a big rocket is made of 15-17
kinds of parts and up to 135 of one kind. So each rocket is built in two steps: its parts go into numbered
"section kits" (each at most nine kinds, at most 64 of a kind), and the kits plus the crew capsule, nose cone and
escape tower go into the rocket itself. The part lists are read from the game (WorldGameTest part "prefabs" writes
jars/testrun/run-fx/prefab-parts.json), so a kit holds exactly what the placed rocket is made of.

Writes: RocketKits.java (items and recipes), kit item models and icons, language entries, and the scroll's
chapter tip. Run from the repository root."""
import json, os, zipfile, glob
import numpy as np
from PIL import Image

PARTS = 'tools/gen/data/prefab-parts.json'          # from WorldGameTest part "prefabs" (run-fx/prefab-parts.json)
R = 'jars/09-rocketry/src/main/resources/assets/heliosphere'
JAVA = 'jars/09-rocketry/src/main/java/com/heliosphere/rocketry/cabin/RocketKits.java'
G = 'jars/11-guide/src/main/resources/assets/heliosphere/guide'
FINAL = ['crew_capsule', 'nose_cone', 'escape_tower']           # fitted in the last step, not packed in a kit
ORDER = ['engine_kerolox', 'engine_hydrolox', 'engine_methalox', 'engine_methalox_vac', 'plasma_thruster', 'srb_nozzle', 'srb_segment',
         'rocket_fin', 'rocket_frame', 'tank_rp1', 'tank_lox', 'tank_lh2', 'tank_ch4', 'tank_argon', 'decoupler', 'landing_leg',
         'rocket_battery', 'avionics_t1', 'avionics_t2', 'parachute_pack', 'heat_shield', 'heat_shield_t2', 'heat_shield_t3',
         'life_support', 'closed_loop_life_support', 'light_sail']
BAND = {'rocket_sounding': (220, 120, 40), 'rocket_qiming_1': (210, 60, 50), 'rocket_qiming_2': (200, 90, 40), 'rocket_dawn_3': (230, 180, 60),
        'rocket_prospector': (150, 120, 90), 'rocket_europa_surveyor': (90, 170, 220), 'rocket_titan_explorer': (220, 150, 60),
        'rocket_horizon_x': (90, 110, 230), 'rocket_phobos_clipper': (190, 80, 70), 'rocket_enceladus_pioneer': (150, 220, 230)}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def released_lang(code):
    out = {}
    for j in sorted(glob.glob('base/*.jar')):
        z = zipfile.ZipFile(j)
        p = f'assets/heliosphere/lang/{code}.json'
        if p in z.namelist(): out.update(json.loads(z.read(p).decode('utf-8')))
    return out


def plan(parts):
    """-> (kits: list of [(item, count)], final: [(item, count)])"""
    entries = []
    for item in sorted(parts, key=lambda k: (ORDER.index(k) if k in ORDER else 99, k)):
        if item in FINAL: continue
        n = parts[item]
        while n > 0:
            entries.append((item, min(64, n))); n -= 64
    kits = []
    for item, n in entries:
        for kit in kits:
            if len(kit) < 9 and all(i != item for i, _ in kit): kit.append((item, n)); break
        else: kits.append([(item, n)])
    final = [(i, parts[i]) for i in FINAL if i in parts]
    assert len(kits) + len(final) <= 9, (kits, final)
    return kits, final


def short(name):
    for sep in (' · ', '（', '('):
        if sep in name: name = name.split(sep)[0]
    return name.strip()


def icon(path, band, idx, total):
    img = np.zeros((16, 16, 4), np.uint8)
    for y in range(1, 15):
        for x in range(4, 12):
            shade = 150 + int(60 * np.cos((x - 7.5) / 4.5 * 1.3))
            c = (shade, shade, min(255, shade + 8))
            if y in (4, 5) or y in (11,): c = band
            if x in (4, 11): c = tuple(int(v * 0.6) for v in c)
            img[y, x] = (*c, 255)
    for y in range(2, 14, 4):                                   # rivet seams
        for x in range(5, 11, 2): img[y, x, :3] = (img[y, x, :3] * 0.8).astype(np.uint8)
    for k in range(total):                                      # pips: which kit of how many
        col = (255, 230, 120) if k == idx else (90, 90, 100)
        img[14, 5 + k * 2] = (*col, 255)
    Image.fromarray(img, 'RGBA').save(path)


def main():
    parts = json.load(open(PARTS, encoding='utf-8'))
    zh, en, tw = released_lang('zh_cn'), released_lang('en_us'), released_lang('zh_tw')
    java_kits, java_final = [], []
    lz, le, lt = {}, {}, {}
    tip_lines = []
    for rocket, p in parts.items():
        kits, final = plan(p)
        nz, ne, nt = short(zh.get(f'item.heliosphere.{rocket}', rocket)), short(en.get(f'item.heliosphere.{rocket}', rocket)), short(tw.get(f'item.heliosphere.{rocket}', rocket))
        base = rocket.replace('rocket_', '')
        ids = []
        for i, kit in enumerate(kits):
            kid = f'rocket_kit_{base}_{i + 1}'
            ids.append(kid)
            java_kits.append((kid, kit))
            lz[f'item.heliosphere.{kid}'] = f'{nz} · 分段组件 {i + 1}/{len(kits)}'
            le[f'item.heliosphere.{kid}'] = f'{ne} · Section Kit {i + 1}/{len(kits)}'
            lt[f'item.heliosphere.{kid}'] = f'{nt} · 分段組件 {i + 1}/{len(kits)}'
            os.makedirs(f'{R}/textures/item', exist_ok=True)
            icon(f'{R}/textures/item/{kid}.png', BAND.get(rocket, (200, 200, 200)), i, len(kits))
            write(f'{R}/models/item/{kid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{kid}'}})
            write(f'{R}/items/{kid}.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{kid}'}})
        java_final.append((rocket, ids, final))
        tip_lines.append(f'{nz}：{len(kits)} 个分段组件' + ('' if not final else ' + ' + ' + '.join(zh.get(f"block.heliosphere.{i}", zh.get(f"item.heliosphere.{i}", i)) + (f"×{n}" if n > 1 else "") for i, n in final)))
    lz['tooltip.heliosphere.rocket_kit'] = '在总装台里与同一枚火箭的其他分段组件、载人舱、鼻锥（和逃逸塔）合成整枚预组装火箭'
    le['tooltip.heliosphere.rocket_kit'] = 'Combine in the assembly station with the rocket\'s other kits, crew capsule, nose cone (and escape tower) into the pre-assembled rocket'
    lt['tooltip.heliosphere.rocket_kit'] = '在總裝台裡與同一枚火箭的其他分段組件、載人艙、鼻錐（和逃逸塔）合成整枚預組裝火箭'
    for code, d in (('zh_cn', lz), ('en_us', le), ('zh_tw', lt)):
        path = f'{R}/lang/{code}.json'
        have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
        have.update(d)
        write(path, have)

    # Java: items and recipes
    def ins(lst): return ''.join(f'.in(I("{i}"), {n})' for i, n in lst)
    kit_lines = []
    for kid, kit in java_kits:
        total = sum(n for _, n in kit)
        kit_lines.append(f'        asm.recipe(MachineRecipe.of(){ins(kit)}.out(I("{kid}")).time({min(4000, 600 + total * 8)}).power(160));')
    fin_lines = []
    for rocket, ids, final in java_final:
        fin_lines.append(f'        asm.recipe(MachineRecipe.of(){ins([(k, 1) for k in ids] + final)}.out(I("{rocket}")).time(1200).power(240));')
    reg = '\n'.join(f'        kit("{kid}");' for kid, _ in java_kits)
    src = f'''package com.heliosphere.rocketry.cabin;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.fabricmc.api.ModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Survival recipes for the pre-assembled rockets, made in the assembly station in two steps: the rocket's parts
 * into numbered section kits (the station has nine slots of at most 64), then the kits with the crew capsule, nose
 * cone and escape tower into the rocket, which is placed on a launch pad like the creative one. The kits hold
 * exactly the parts the placed rocket is made of. Generated by tools/gen/rocket_kits.py: edit that, not this.
 */
public final class RocketKits implements ModInitializer {{
    @Override
    public void onInitialize() {{
        Reg.tab("rocketry");
{reg}
        MachineType.addProvider(RocketKits::recipes);
    }}

    private static void kit(String id) {{
        Reg.item(id, p -> new Item(p) {{
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {{
                out.accept(Component.translatable("tooltip.heliosphere.rocket_kit").withStyle(ChatFormatting.GRAY));
            }}
        }}, new Item.Properties().stacksTo(16));
    }}

    private static Item I(String id) {{ return Reg.get(id); }}

    private static void recipes() {{
        MachineType asm = MachineType.get("assembly_station");
{chr(10).join(kit_lines)}
{chr(10).join(fin_lines)}
    }}
}}
'''
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    open(JAVA, 'w', encoding='utf-8').write(src)

    # the scroll: a clear tip in the sounding-rocket chapter (chapter 4) and the first chapter
    path = f'{G}/chapters.json'
    ch = json.load(open(path, encoding='utf-8'))
    head = '§a整枚火箭的合成（生存模式）：§r'
    tip = (head + '不想一层层自己搭，可以在「总装台」里直接造出与创造模式相同的预组装火箭，外形是完整的流线型火箭，放在发射台上即可用控制台加注发射。'
           '分两步：① 先把火箭的全部零件放进总装台做成「分段组件」（每个组件最多 9 种零件、每种最多 64 个，点组件可在卷轴里看到配方）；'
           '② 再把同一枚火箭的全部分段组件和载人舱、鼻锥（多数还要逃逸塔）放进总装台，合成整枚火箭。\n' + '；\n'.join(tip_lines) + '。'
           '\n零件本身照常在总装台制造（见各零件页面）。也可以照「火箭蓝图」自己在发射台上逐层搭建，两种火箭飞起来完全一样。')
    old = '创造模式物品栏的「火箭」页提供十种预制火箭。'
    for c in ch:
        c['tips'] = [t.replace(old, '十种预组装火箭：创造模式物品栏「火箭」页可直接拿，生存模式按「整枚火箭的合成」在总装台造出。') for t in c['tips']]
    for idx in (0, 3):
        tips = ch[idx]['tips']
        tips[:] = [t for t in tips if not t.startswith(head)]
        tips.insert(0 if idx == 3 else len(tips), tip)
    write(path, ch)
    print(len(java_kits), 'kits for', len(java_final), 'rockets')


if __name__ == '__main__':
    main()
