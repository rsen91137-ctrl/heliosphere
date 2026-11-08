"""Generates every texture, model, blockstate, lang entry, loot table, tag, recipe, worldgen and dimension file.

Usage: python3 tools/gen/generate.py
Writes 16x textures into each module's resources and a 32x HD resource pack into build/resourcepack/.
"""
import json
import os
import shutil
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
import blocks as B  # noqa: E402
import content as C  # noqa: E402
import guide_content as G  # noqa: E402
import icons  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
NS = 'heliosphere'
HD = os.path.join(ROOT, 'build', 'resourcepack', 'Heliosphere-HD')
LANG = {m: {'zh_cn': {}, 'en_us': {}} for m in ('core', 'sphereworld', 'industry', 'planets', 'rocketry', 'guide')}
TAGS = {}
WRITTEN = set()


def res(module):
    return os.path.join(ROOT, module, 'src', 'main', 'resources')


def wjson(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    WRITTEN.add(path)


def tex(module, rel, painter):
    """painter(n) -> PIL image; writes 16x into the module and 32x into the HD pack."""
    for n, base in ((16, os.path.join(res(module), 'assets', NS, 'textures')), (32, os.path.join(HD, 'assets', NS, 'textures'))):
        path = os.path.join(base, rel + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        painter(n).save(path)


def asset(module, rel, data):
    wjson(os.path.join(res(module), 'assets', NS, rel), data)


def data(module, rel, d, ns=NS):
    wjson(os.path.join(res(module), 'data', ns, rel), d)


def lang(module, key, zh, en):
    LANG[module]['zh_cn'][key] = zh
    LANG[module]['en_us'][key] = en


def tag(kind, name, value, ns='minecraft'):
    TAGS.setdefault((ns, kind, name), []).append(value)


def seed(s):
    return zlib.crc32(str(s).encode()) & 0xFFFFFF


# -------------------------------------------------------------------------------------------- helpers

def simple_item(module, iid, painter):
    tex(module, f'item/{iid}', painter)
    asset(module, f'models/item/{iid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
    asset(module, f'items/{iid}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})


def block_item(module, bid, model=None):
    asset(module, f'items/{bid}.json', {'model': {'type': 'minecraft:model', 'model': model or f'{NS}:block/{bid}'}})


def cube_block(module, bid, painter=None, textures=None, parent='minecraft:block/cube_all'):
    if painter:
        tex(module, f'block/{bid}', painter)
    asset(module, f'blockstates/{bid}.json', {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
    asset(module, f'models/block/{bid}.json', {'parent': parent, 'textures': textures or {'all': f'{NS}:block/{bid}'}})
    block_item(module, bid)


def loot_self(module, bid, extra_functions=None):
    entry = {'type': 'minecraft:item', 'name': f'{NS}:{bid}'}
    if extra_functions:
        entry['functions'] = extra_functions
    data(module, f'loot_table/blocks/{bid}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [entry],
                                                                                         'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
                                                  'random_sequence': f'{NS}:blocks/{bid}'})


def loot_drop(module, bid, item, mn=1, mx=1, fortune=False, silk=True, extra=None):
    drop = {'type': 'minecraft:item', 'name': item, 'functions': []}
    if mn != 1 or mx != 1:
        drop['functions'].append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': mn, 'max': mx}})
    if fortune:
        drop['functions'].append({'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'})
    drop['functions'].append({'function': 'minecraft:explosion_decay'})
    children = []
    if silk:
        children.append({'type': 'minecraft:item', 'name': f'{NS}:{bid}', 'conditions': [{'condition': 'minecraft:match_tool', 'predicate': {
            'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]})
    children.append(drop)
    pools = [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': children}]}]
    if extra:
        pools.append(extra)
    data(module, f'loot_table/blocks/{bid}.json', {'type': 'minecraft:block', 'pools': pools, 'random_sequence': f'{NS}:blocks/{bid}'})


def mineable(bid, tool, level=None):
    tag('block', f'mineable/{tool}', f'{NS}:{bid}')
    if level in ('stone', 'iron', 'diamond'):
        tag('block', f'needs_{level}_tool', f'{NS}:{bid}')


# -------------------------------------------------------------------------------------------- core

def gen_core():
    m = 'core'
    tex(m, 'block/stellar_plasma', lambda n: B.plasma_strip(n))
    for n, base in ((16, res(m)), (32, HD)):
        wjson(os.path.join(base, 'assets', NS, 'textures', 'block', 'stellar_plasma.png.mcmeta'),
              {'animation': {'frametime': 3, 'interpolate': True}})
    cube_block(m, 'stellar_plasma')
    lang(m, 'block.heliosphere.stellar_plasma', '恒星等离子体', 'Stellar Plasma')
    # power cable: multipart
    tex(m, 'block/cable', lambda n: B.to_img(B.panel(n, '#303034', 'cable', rivets=False, seam=False)))
    tex(m, 'block/cable_core', lambda n: B.to_img(B.panel(n, '#D07848', 'cablec', rivets=False, seam=False)))
    asset(m, 'models/block/cable_core.json', {'textures': {'particle': f'{NS}:block/cable', 'core': f'{NS}:block/cable_core'},
                                              'elements': [{'from': [5, 5, 5], 'to': [11, 11, 11], 'faces': {d: {'texture': '#core'} for d in
                                                                                                             ('north', 'south', 'east', 'west', 'up', 'down')}}]})
    asset(m, 'models/block/cable_side.json', {'textures': {'particle': f'{NS}:block/cable', 'side': f'{NS}:block/cable'},
                                              'elements': [{'from': [6, 6, 0], 'to': [10, 10, 5], 'faces': {
                                                  'north': {'texture': '#side'}, 'east': {'texture': '#side'}, 'west': {'texture': '#side'},
                                                  'up': {'texture': '#side'}, 'down': {'texture': '#side'}}}]})
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    parts = [{'apply': {'model': f'{NS}:block/cable_core'}}]
    for d, r in rot.items():
        parts.append({'when': {d: 'true'}, 'apply': dict({'model': f'{NS}:block/cable_side', 'uvlock': False}, **r)})
    asset(m, 'blockstates/cable.json', {'multipart': parts})
    asset(m, 'models/item/cable.json', {'parent': f'{NS}:block/cable_core'})
    block_item(m, 'cable', f'{NS}:item/cable')
    loot_self(m, 'cable')
    loot_self(m, 'stellar_plasma')
    mineable('cable', 'pickaxe')
    lang(m, 'block.heliosphere.cable', '电缆', 'Power Cable')
    # icon
    os.makedirs(os.path.join(res(m), 'assets', NS), exist_ok=True)
    icons.rocket(128).save(os.path.join(res(m), 'assets', NS, 'icon.png'))
    for mm in LANG:
        if mm != 'core':
            os.makedirs(os.path.join(res(mm), 'assets', NS), exist_ok=True)
            shutil.copy(os.path.join(res(m), 'assets', NS, 'icon.png'), os.path.join(res(mm), 'assets', NS, 'icon.png'))
    ui = {
        'gui.heliosphere.recipes': ('配方', 'Recipes'),
        'gui.heliosphere.destination': ('目的地', 'Destination'),
        'gui.heliosphere.landing_site': ('着陆点', 'Landing site'),
        'gui.heliosphere.refuel': ('加注', 'Refuel'),
        'gui.heliosphere.launch': ('发射', 'Launch'),
        'gui.heliosphere.force_launch': ('强行发射', 'Force launch'),
        'gui.heliosphere.disassemble': ('拆解为方块', 'Disassemble'),
        'gui.heliosphere.fuel': ('贮箱：%s', 'Tanks: %s'),
        'gui.heliosphere.scroll_title': ('星际航行卷轴', 'The Star Scroll'),
        'gui.heliosphere.objectives': ('关键物品（完成 %s%%）', 'Key items (%s%% done)'),
        'gui.heliosphere.tips': ('提示', 'Hints'),
        'itemGroup.heliosphere.industry': ('日球层 · 工业', 'Heliosphere · Industry'),
        'itemGroup.heliosphere.planets': ('日球层 · 太空', 'Heliosphere · Space'),
        'itemGroup.heliosphere.rocketry': ('日球层 · 火箭', 'Heliosphere · Rocketry'),
        'entity.heliosphere.rocket': ('火箭', 'Rocket'),
        'generator.heliosphere.solar_system': ('太阳系', 'Solar System'),
        'generator.heliosphere.solar_system.info': ('有限的球形地球（1/100 比例，赤道一周 40 万格）。', 'A finite spherical Earth at 1/100 scale.'),
        'tooltip.heliosphere.prebuilt': ('右键放置：已加满燃料、发动机已试车', 'Right-click to place: fuelled and test-fired'),
        'message.heliosphere.no_suit': ('§c真空/有毒大气！没有密封宇航服', '§cNo breathable air and no sealed suit!'),
        'message.heliosphere.no_oxygen': ('§c宇航服氧气耗尽！', '§cSuit oxygen depleted!'),
        'message.heliosphere.low_oxygen': ('§e氧气不足 1 分钟', '§eLess than a minute of oxygen left'),
        'message.heliosphere.suit_leak': ('§e高氯酸盐尘埃正在侵蚀初代宇航服的密封件（耗氧×3）', '§ePerchlorate dust is eroding your suit seals (oxygen use x3)'),
        'message.heliosphere.too_hot': ('§c温度 %s°C，超出宇航服耐受范围', '§c%s°C exceeds your suit rating'),
        'message.heliosphere.too_cold': ('§b温度 %s°C，超出宇航服耐受范围', '§b%s°C exceeds your suit rating'),
        'message.heliosphere.crossed_pole': ('§7你越过了极点，现在位于另一侧经线上', '§7You crossed the pole onto the far meridian'),
        'message.heliosphere.crossed_meridian': ('§7你绕行了这颗星球的东西接缝（±180° 经线）', '§7You crossed the ±180° meridian'),
        'message.heliosphere.test_failed': ('§c试车失败：发动机在试车台上爆炸了！', '§cTest firing failed: the engine exploded!'),
        'message.heliosphere.oxygen_refilled': ('§b氧气已补充', '§bOxygen refilled'),
        'message.heliosphere.tank_full': ('贮箱已满', 'Tank is full'),
        'message.heliosphere.no_room': ('§c空间不足，无法放置火箭', '§cNot enough room for the rocket'),
        'message.heliosphere.countdown': ('§e倒计时开始', '§eCountdown started'),
        'message.heliosphere.liftoff': ('§a点火，起飞！', '§aIgnition, liftoff!'),
        'message.heliosphere.no_ignition': ('§c发动机无法点火', '§cEngines failed to ignite'),
        'message.heliosphere.no_liftoff': ('§c推力不足，火箭没能离开发射台', '§cNot enough thrust to leave the pad'),
        'message.heliosphere.pad_explosion': ('§4发射台爆炸！', '§4Explosion on the pad!'),
        'message.heliosphere.breakup': ('§4火箭在最大动压区解体！', '§4Structural failure at Max-Q!'),
        'message.heliosphere.tumble': ('§c推力偏心——火箭失控翻滚！', '§cAsymmetric thrust - the rocket is tumbling!'),
        'message.heliosphere.out_of_fuel': ('§c推进剂耗尽，火箭开始下坠', '§cOut of propellant, falling back'),
        'message.heliosphere.staging': ('§b一级分离', '§bStage separation'),
        'message.heliosphere.karman': ('§b已越过卡门线！', '§bAbove the Kármán line!'),
        'message.heliosphere.abort': ('§eΔv 不足以完成转移，任务中止返航', '§eNot enough delta-v for the transfer - aborting'),
        'message.heliosphere.transfer': ('§b转移轨道：前往%s，预计 %s 秒', '§bTransfer to %s, about %s s'),
        'message.heliosphere.arrival': ('§b抵达%s，开始进入与下降', '§bArriving at %s, beginning descent'),
        'message.heliosphere.burn_up': ('§4隔热不足——火箭在再入时烧毁！', '§4Burned up on entry!'),
        'message.heliosphere.parachute': ('§a降落伞展开', '§aParachutes deployed'),
        'message.heliosphere.crash': ('§4坠毁！', '§4Crash!'),
        'message.heliosphere.tip_over': ('§c着陆腿不足，火箭着陆后翻倒爆炸', '§cThe rocket tipped over on landing'),
        'message.heliosphere.landed': ('§a成功着陆：%s', '§aLanded on %s'),
        'message.heliosphere.storm': ('§c风暴！时速上千公里的急流把你吹离平台——待在风暴护盾发生器的力场里', '§cStorm! Supersonic jet streams — stay inside a storm shield'),
        'message.heliosphere.crushed': ('§4巨大的大气压正在压垮你——需要密封等级 3 的宇航服或护盾', '§4The crushing atmosphere is collapsing your suit — tier 3 suit or shield needed'),
        'message.heliosphere.radiation': ('§d木星磁层强辐射——需要密封等级 3 的宇航服', '§dJovian radiation belt — tier 3 suit needed'),
        'message.heliosphere.science': ('§b获得科学数据：%s', '§bScience collected: %s'),
        'message.heliosphere.escape_tower': ('§e逃逸塔点火！乘员舱被弹射出去', '§eEscape tower fired - crew ejected!'),
        'message.heliosphere.life_support_failed': ('§c生命维持不足，舱内二氧化碳浓度升高', '§cLife support exhausted'),
        'message.heliosphere.cannot_launch_here': ('§c这里无法发射', '§cYou cannot launch from here'),
        'message.heliosphere.checks_failed': ('§c检查未通过：修好红色项目，或使用「强行发射」', '§cPre-flight checks failed: fix the red items or force the launch'),
        'message.heliosphere.scroll_given': ('§6你获得了「星际航行卷轴」。右键展开，它会指引你从地球矿石一路走到火星。',
                                             '§6You received the Star Scroll. Right-click it for your path from Earth to Mars.'),
        'message.heliosphere.objective': ('§a卷轴目标达成：%s', '§aScroll objective: %s'),
        'condition.heliosphere.needs_air': ('需要可呼吸的大气', 'Needs breathable air'),
        'condition.heliosphere.needs_oxygen': ('燃烧需要氧气', 'Combustion needs oxygen'),
        'condition.heliosphere.needs_vacuum': ('只能在真空中工作', 'Works only in vacuum'),
        'condition.heliosphere.needs_foreign_air': ('需要非地球的大气（如火星）', 'Needs a foreign atmosphere such as Mars'),
    }
    for k, (zh, en) in ui.items():
        lang(m, k, zh, en)


# -------------------------------------------------------------------------------------------- terrain

STYLES = {'moon_regolith': 'regolith', 'mare_regolith': 'regolith', 'helium3_regolith': 'regolith', 'anorthosite': 'crystal',
          'mare_basalt': 'basalt', 'high_ti_basalt': 'basalt', 'kreep_rock': 'deposit', 'moon_ice': 'ice', 'meteoric_iron_ore': 'deposit',
          'mars_regolith': 'regolith', 'mars_sand': 'sand', 'mars_rock': 'soil', 'mars_basalt': 'basalt', 'perchlorate_deposit': 'deposit',
          'hematite_deposit': 'deposit', 'gypsum_deposit': 'layered', 'smectite_clay_block': 'layered', 'mars_ice': 'ice', 'dry_ice': 'frost',
          'olivine_rock': 'crystal'}
TERRAIN_DROPS = {'moon_ice': ('lunar_ice_chunk', 2, 3), 'mars_ice': ('mars_ice_chunk', 2, 3), 'meteoric_iron_ore': ('meteoric_iron', 1, 2),
                 'perchlorate_deposit': ('perchlorate_salt', 2, 4), 'hematite_deposit': ('hematite_nodule', 1, 3),
                 'gypsum_deposit': ('gypsum_crystal', 1, 3), 'smectite_clay_block': ('smectite_clay', 3, 4), 'olivine_rock': ('olivine', 1, 2)}


def gen_terrain():
    m = 'sphereworld'
    for (bid, zh, en, base, speck, dens, hard, tool) in C.TERRAIN:
        if bid in ('moon_bricks', 'mars_bricks'):
            cube_block(m, bid, lambda n, b=base, s=speck, i=bid: B.bricks(n, b, s, seed(i)))
        else:
            style = STYLES.get(bid, 'soil')
            cube_block(m, bid, lambda n, b=base, s=speck, d=dens, i=bid, st=style: B.rock(n, b, s, d, seed(i), st))
        lang(m, f'block.heliosphere.{bid}', zh, en)
        soft = tool == 'none'
        mineable(bid, 'shovel' if soft else 'pickaxe', None if tool in ('none', 'wood') else tool)
        if bid in TERRAIN_DROPS:
            it, mn, mx = TERRAIN_DROPS[bid]
            extra = None
            if bid == 'smectite_clay_block':
                extra = {'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:microfossil'}],
                         'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.06}]}
            loot_drop(m, bid, f'{NS}:{it}', mn, mx, fortune=True, extra=extra)
        else:
            loot_self(m, bid)
    for (bid, zh, en, base, speck, dens, hard, tool, style, drop) in C.WORLD_TERRAIN:
        if style == 'bricks':
            cube_block(m, bid, lambda n, b=base, s=speck, i=bid: B.bricks(n, b, s, seed(i)))
        else:
            cube_block(m, bid, lambda n, b=base, s=speck, d=dens, i=bid, st=style: B.rock(n, b, s, d, seed(i), st))
        lang(m, f'block.heliosphere.{bid}', zh, en)
        if drop == 'none':
            continue
        mineable(bid, 'shovel' if tool == 'none' else 'pickaxe', None if tool in ('none', 'wood') else tool)
        if drop:
            loot_drop(m, bid, f'{NS}:{drop[0]}', drop[1], drop[2], fortune=True)
        else:
            loot_self(m, bid)
    gen_dimensions(m)


def gen_dimensions(m):
    def attrs(sky, fog, extra=None):
        a = {'minecraft:visual/sky_color': sky, 'minecraft:visual/fog_color': fog,
             'minecraft:gameplay/bed_rule': {'can_set_spawn': 'always', 'can_sleep': 'never', 'error_message': {'translate': 'message.heliosphere.no_sleep'}},
             'minecraft:gameplay/respawn_anchor_works': False}
        if extra:
            a.update(extra)
        return a

    common = {'coordinate_scale': 1.0, 'has_ceiling': False, 'has_skylight': True, 'infiniburn': '#minecraft:infiniburn_overworld',
              'monster_spawn_block_light_limit': 0, 'monster_spawn_light_level': 0}
    data(m, 'dimension_type/moon.json', dict(common, ambient_light=0.0, min_y=-256, height=768, logical_height=768,
                                             attributes=attrs('#000000', '#000000', {'minecraft:visual/cloud_height': 2000.0}),
                                             timelines=[f'{NS}:moon_day']))
    data(m, 'dimension_type/mars.json', dict(common, ambient_light=0.0, min_y=-128, height=1088, logical_height=1088,
                                             attributes=attrs('#C99A6E', '#B7896A', {'minecraft:visual/cloud_height': 2000.0}),
                                             timelines=[f'{NS}:mars_day']))
    data(m, 'dimension_type/space.json', dict(common, ambient_light=0.6, min_y=0, height=256, logical_height=256, has_fixed_time=True,
                                              attributes=attrs('#000000', '#000000', {'minecraft:visual/cloud_height': 2000.0})))
    data(m, 'dimension/moon.json', {'type': f'{NS}:moon', 'generator': {'type': f'{NS}:planet', 'planet': 'moon',
                                                                        'biome_source': {'type': 'minecraft:fixed', 'biome': f'{NS}:lunar_surface'}}})
    data(m, 'dimension/mars.json', {'type': f'{NS}:mars', 'generator': {'type': f'{NS}:planet', 'planet': 'mars',
                                                                        'biome_source': {'type': 'minecraft:fixed', 'biome': f'{NS}:martian_surface'}}})
    data(m, 'dimension/space.json', {'type': f'{NS}:space', 'generator': {'type': 'minecraft:flat', 'settings': {
        'biome': f'{NS}:orbit', 'features': False, 'lakes': False, 'layers': [], 'structure_overrides': []}}})

    def biome(sky, fog, temp):
        return {'attributes': {'minecraft:visual/sky_color': sky, 'minecraft:visual/fog_color': fog},
                'carvers': [], 'downfall': 0.0, 'effects': {'water_color': '#3f76e4'}, 'features': [[] for _ in range(11)],
                'has_precipitation': False, 'spawn_costs': {}, 'spawners': {k: [] for k in ('ambient', 'axolotls', 'creature', 'misc', 'monster',
                                                                                           'underground_water_creature', 'water_ambient', 'water_creature')},
                'temperature': temp}
    data(m, 'worldgen/biome/lunar_surface.json', biome('#000000', '#000000', 0.5))
    data(m, 'worldgen/biome/martian_surface.json', biome('#C99A6E', '#B7896A', 0.0))
    data(m, 'worldgen/biome/orbit.json', biome('#000000', '#000000', 0.5))

    def day_timeline(period, night_light, sunset=None, stars_day=0.0):
        p = period
        k = lambda t: int(round(t * p / 24000.0))  # noqa: E731
        tracks = {
            'minecraft:visual/sun_angle': {'keyframes': [{'ticks': k(6000), 'value': 360.0}, {'ticks': k(6000), 'value': 0.0}]},
            'minecraft:visual/star_angle': {'keyframes': [{'ticks': k(6000), 'value': 360.0}, {'ticks': k(6000), 'value': 0.0}]},
            'minecraft:visual/moon_angle': {'keyframes': [{'ticks': k(6000), 'value': 540.0}, {'ticks': k(6000), 'value': 180.0}]},
            'minecraft:gameplay/sky_light_level': {'keyframes': [{'ticks': k(300), 'value': 1.0}, {'ticks': k(11700), 'value': 1.0},
                                                                 {'ticks': k(13000), 'value': night_light}, {'ticks': k(23000), 'value': night_light}],
                                                   'modifier': 'multiply'},
            'minecraft:visual/sky_light_factor': {'keyframes': [{'ticks': k(300), 'value': 1.0}, {'ticks': k(11700), 'value': 1.0},
                                                                {'ticks': k(13000), 'value': max(0.05, night_light)}, {'ticks': k(23000), 'value': max(0.05, night_light)}],
                                                  'modifier': 'multiply'},
            'minecraft:visual/star_brightness': {'keyframes': [{'ticks': k(300), 'value': stars_day}, {'ticks': k(11700), 'value': stars_day},
                                                               {'ticks': k(13000), 'value': 0.6}, {'ticks': k(23000), 'value': 0.6}]},
        }
        if sunset:
            tracks['minecraft:visual/sunrise_sunset_color'] = {'keyframes': [
                {'ticks': k(0), 'value': sunset}, {'ticks': k(700), 'value': '#00000000'}, {'ticks': k(11300), 'value': '#00000000'},
                {'ticks': k(12000), 'value': sunset}, {'ticks': k(12800), 'value': '#00000000'}, {'ticks': k(23200), 'value': '#00000000'}]}
            tracks['minecraft:visual/sky_color'] = {'keyframes': [{'ticks': k(300), 'value': '#ffffff'}, {'ticks': k(11700), 'value': '#ffffff'},
                                                                  {'ticks': k(13000), 'value': '#000000'}, {'ticks': k(23000), 'value': '#000000'}],
                                                    'modifier': 'multiply'}
            tracks['minecraft:visual/fog_color'] = {'keyframes': [{'ticks': k(300), 'value': '#ffffff'}, {'ticks': k(11700), 'value': '#ffffff'},
                                                                  {'ticks': k(13000), 'value': '#0f0f16'}, {'ticks': k(23000), 'value': '#0f0f16'}],
                                                    'modifier': 'multiply'}
        return {'period_ticks': period, 'tracks': tracks}

    # Moon: one solar day = 29.53 Earth days; Mars: one sol = 24 h 39 min (blue sunsets!)
    data(m, 'timeline/moon_day.json', day_timeline(int(24000 * 29.53), 0.12, None, 0.0))
    data(m, 'timeline/mars_day.json', day_timeline(int(24000 * 1.0275), 0.22, '#6a9ad8ff', 0.0))
    import math
    for (bid, sky, fog, amb, night, sunset) in C.BODY_DIMS:
        gas = bid in ('jupiter', 'saturn', 'uranus', 'neptune')
        lo, hi = (-64, 512) if gas else (-256, 1280)
        data(m, f'dimension_type/{bid}.json', dict(common, ambient_light=amb, min_y=lo, height=hi, logical_height=hi,
                                                   attributes=attrs(sky, fog, {'minecraft:visual/cloud_height': 2000.0}),
                                                   timelines=[f'{NS}:{bid}_day']))
        data(m, f'dimension/{bid}.json', {'type': f'{NS}:{bid}', 'generator': {'type': f'{NS}:planet', 'planet': bid,
                                          'biome_source': {'type': 'minecraft:fixed', 'biome': f'{NS}:{bid}_surface'}}})
        data(m, f'worldgen/biome/{bid}_surface.json', biome(sky, fog, -0.5))
        data(m, f'timeline/{bid}_day.json', day_timeline(max(2400, int(round(C.SOLAR_DAY_HOURS[bid] * 1000))), night, sunset, 0.0))

    preset = {'dimensions': {
        'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {'type': 'minecraft:noise', 'biome_source': {
            'type': f'{NS}:earth', 'base': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:overworld'}}, 'settings': 'minecraft:overworld'}},
        'minecraft:the_end': {'type': 'minecraft:the_end', 'generator': {'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:the_end'},
                                                                         'settings': 'minecraft:end'}},
        'minecraft:the_nether': {'type': 'minecraft:the_nether', 'generator': {'type': 'minecraft:noise', 'biome_source': {
            'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}, 'settings': 'minecraft:nether'}}}}
    data(m, 'worldgen/world_preset/solar_system.json', preset)
    tag('worldgen/world_preset', 'normal', f'{NS}:solar_system')
    lang(m, 'message.heliosphere.no_sleep', '这里的一天太长了，无法靠睡觉跳过', 'Days here are too long to sleep through')
    lang(m, 'dimension.heliosphere.moon', '月球', 'The Moon')
    lang(m, 'dimension.heliosphere.mars', '火星', 'Mars')
    lang(m, 'dimension.heliosphere.space', '太空', 'Space')


# -------------------------------------------------------------------------------------------- industry

def gen_industry():
    m = 'industry'
    for (oid, zh, en, drop, dzh, den, c1, c2, ymin, ymax, size, count, tool) in C.ORES:
        tex(m, f'block/ore/{oid}', lambda n, a=c1, b=c2, i=oid: B.ore_overlay(n, a, b, seed(i)))
        for prefix, base, bzh, ben in (('', 'minecraft:block/stone', '', ''), ('deepslate_', 'minecraft:block/deepslate', '深层', 'Deepslate ')):
            bid = f'{prefix}{oid}_ore'
            asset(m, f'blockstates/{bid}.json', {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
            asset(m, f'models/block/{bid}.json', {'parent': 'minecraft:block/block', 'textures': {'particle': base, 'base': base, 'ore': f'{NS}:block/ore/{oid}'},
                                                 'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: {'texture': '#base', 'cullface': d} for d in
                                                                                                                 ('north', 'south', 'east', 'west', 'up', 'down')}},
                                                              {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: {'texture': '#ore', 'cullface': d} for d in
                                                                                                                 ('north', 'south', 'east', 'west', 'up', 'down')}}]})
            block_item(m, bid)
            lang(m, f'block.heliosphere.{bid}', f'{bzh}{zh}', f'{ben}{en}')
            mineable(bid, 'pickaxe', tool)
            loot_drop(m, bid, f'{NS}:{drop}', fortune=True)
            tag('block', 'ores', f'{NS}:{bid}', 'c')
            tag('item', 'ores', f'{NS}:{bid}', 'c')
        data(m, f'worldgen/configured_feature/ore_{oid}.json', {'type': 'minecraft:ore', 'config': {'discard_chance_on_air_exposure': 0.0, 'size': size, 'targets': [
            {'state': {'Name': f'{NS}:{oid}_ore'}, 'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}},
            {'state': {'Name': f'{NS}:deepslate_{oid}_ore'}, 'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'}}]}})
        data(m, f'worldgen/placed_feature/ore_{oid}.json', {'feature': f'{NS}:ore_{oid}', 'placement': [
            {'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': ymin}, 'max_inclusive': {'absolute': ymax}}},
            {'type': 'minecraft:biome'}]})
    cube_block(m, 'oil_sand', lambda n: B.oil_sand(n, seed('oil')))
    lang(m, 'block.heliosphere.oil_sand', '油砂', 'Oil Sand')
    mineable('oil_sand', 'shovel')
    loot_self(m, 'oil_sand')
    data(m, 'worldgen/configured_feature/oil_sand.json', {'type': 'minecraft:ore', 'config': {'discard_chance_on_air_exposure': 0.0, 'size': 24, 'targets': [
        {'state': {'Name': f'{NS}:oil_sand'}, 'target': {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:sand'}},
        {'state': {'Name': f'{NS}:oil_sand'}, 'target': {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:sandstone'}},
        {'state': {'Name': f'{NS}:oil_sand'}, 'target': {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:red_sand'}},
        {'state': {'Name': f'{NS}:oil_sand'}, 'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}}]}})
    data(m, 'worldgen/placed_feature/oil_sand.json', {'feature': f'{NS}:oil_sand', 'placement': [
        {'type': 'minecraft:count', 'count': 6}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': 30}, 'max_inclusive': {'absolute': 100}}},
        {'type': 'minecraft:biome'}]})


def gen_items():
    for (module, iid, zh, en, icon) in C.ITEMS:
        simple_item(module, iid, lambda n, i=iid, s=icon: icons.item_icon(n, i, s, seed(i)))
        lang(module, f'item.heliosphere.{iid}', zh, en)
        if iid.endswith('_ingot'):
            tag('item', 'ingots', f'{NS}:{iid}', 'c')
    tag('item', 'repairs_space_suit', f'{NS}:nylon', NS)


def gen_machines():
    for (module, mid, zh, en, casing, accent, glyph) in C.MACHINES:
        faces = {}
        for n in (16, 32):
            faces[n] = B.machine_faces(n, casing, accent, glyph, seed(mid))
        for i, name in enumerate(('side', 'top', 'front', 'front_on')):
            tex(module, f'block/machine/{mid}_{name}', lambda n, i=i: faces[n][i])
        for lit, sfx in ((False, ''), (True, '_on')):
            asset(module, f'models/block/{mid}{sfx}.json', {'parent': 'minecraft:block/orientable', 'textures': {
                'top': f'{NS}:block/machine/{mid}_top', 'front': f'{NS}:block/machine/{mid}_front{sfx}', 'side': f'{NS}:block/machine/{mid}_side'}})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for lit in ('false', 'true'):
                v = {'model': f'{NS}:block/{mid}' + ('_on' if lit == 'true' else '')}
                if y:
                    v['y'] = y
                variants[f'facing={facing},lit={lit}'] = v
        asset(module, f'blockstates/{mid}.json', {'variants': variants})
        block_item(module, mid)
        lang(module, f'block.heliosphere.{mid}', zh, en)
        mineable(mid, 'pickaxe', 'stone')
        loot_self(module, mid)


# -------------------------------------------------------------------------------------------- rocketry

def faces_all(t):
    return {d: {'texture': t} for d in ('north', 'south', 'east', 'west', 'up', 'down')}


def part_models(m, pid, kind, color, accent):
    s = seed(pid)
    if kind == 'tank':
        tex(m, f'block/part/{pid}_side', lambda n: B.tank_side(n, color, accent, s))
        tex(m, f'block/part/{pid}_end', lambda n: B.tank_cap(n, color, s))
        return {'parent': 'minecraft:block/cube_column', 'textures': {'side': f'{NS}:block/part/{pid}_side', 'end': f'{NS}:block/part/{pid}_end'}}
    if kind in ('engine', 'engine_vac'):
        tex(m, f'block/part/{pid}_body', lambda n: B.to_img(B.panel(n, color, s)))
        tex(m, f'block/part/{pid}_nozzle', lambda n: B.nozzle_tex(n, accent if kind == 'engine' else '#5A5E68', s))
        bell_lo = 1 if kind == 'engine_vac' else 3
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/part/{pid}_body', 'body': f'{NS}:block/part/{pid}_body',
                                                                'nozzle': f'{NS}:block/part/{pid}_nozzle'},
                'elements': [
                    {'from': [2, 10, 2], 'to': [14, 16, 14], 'faces': faces_all('#body')},
                    {'from': [5, 7, 5], 'to': [11, 10, 11], 'faces': faces_all('#nozzle')},
                    {'from': [4, 4, 4], 'to': [12, 7, 12], 'faces': faces_all('#nozzle')},
                    {'from': [bell_lo, 0, bell_lo], 'to': [16 - bell_lo, 4, 16 - bell_lo], 'faces': faces_all('#nozzle')}]}
    if kind == 'band':
        tex(m, f'block/part/{pid}', lambda n: B.stripes(n, accent, color, s))
        tex(m, f'block/part/{pid}_end', lambda n: B.tank_cap(n, '#5A5A60', s))
        return {'parent': 'minecraft:block/cube_column', 'textures': {'side': f'{NS}:block/part/{pid}', 'end': f'{NS}:block/part/{pid}_end'}}
    if kind == 'truss':
        tex(m, f'block/part/{pid}', lambda n: B.truss(n, color, s))
        return {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/part/{pid}'}}
    if kind == 'capsule':
        tex(m, f'block/part/{pid}_side', lambda n: B.capsule_side(n, color, accent, s))
        tex(m, f'block/part/{pid}_end', lambda n: B.tank_cap(n, color, s))
        return {'parent': 'minecraft:block/cube_column', 'textures': {'side': f'{NS}:block/part/{pid}_side', 'end': f'{NS}:block/part/{pid}_end'}}
    if kind == 'slab':
        tex(m, f'block/part/{pid}', lambda n: B.ablative(n, color, s))
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/part/{pid}', 't': f'{NS}:block/part/{pid}'},
                'elements': [{'from': [0, 0, 0], 'to': [16, 6, 16], 'faces': faces_all('#t')}]}
    if kind == 'cone':
        tex(m, f'block/part/{pid}', lambda n: B.to_img(B.panel(n, color, s, rivets=False)))
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/part/{pid}', 't': f'{NS}:block/part/{pid}'},
                'elements': [{'from': [1, 0, 1], 'to': [15, 6, 15], 'faces': faces_all('#t')},
                             {'from': [3, 6, 3], 'to': [13, 11, 13], 'faces': faces_all('#t')},
                             {'from': [6, 11, 6], 'to': [10, 15, 10], 'faces': faces_all('#t')},
                             {'from': [7.5, 15, 7.5], 'to': [8.5, 16, 8.5], 'faces': faces_all('#t')}]}
    if kind == 'fin':
        tex(m, f'block/part/{pid}', lambda n: B.to_img(B.panel(n, color, s, rivets=False)))
        tex(m, f'block/part/{pid}_edge', lambda n: B.stripes(n, accent, color, s))
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/part/{pid}', 't': f'{NS}:block/part/{pid}', 'e': f'{NS}:block/part/{pid}_edge'},
                'elements': [{'from': [7, 0, 0], 'to': [9, 14, 16], 'faces': {'east': {'texture': '#t'}, 'west': {'texture': '#t'}, 'north': {'texture': '#e'},
                                                                                  'south': {'texture': '#e'}, 'up': {'texture': '#e'}, 'down': {'texture': '#e'}}},
                             {'from': [0, 0, 7], 'to': [16, 14, 9], 'faces': {'north': {'texture': '#t'}, 'south': {'texture': '#t'}, 'east': {'texture': '#e'},
                                                                                  'west': {'texture': '#e'}, 'up': {'texture': '#e'}, 'down': {'texture': '#e'}}}]}
    if kind == 'leg':
        tex(m, f'block/part/{pid}', lambda n: B.to_img(B.panel(n, color, s, rivets=False, seam=False)))
        tex(m, f'block/part/{pid}_foot', lambda n: B.to_img(B.panel(n, accent, s)))
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/part/{pid}', 't': f'{NS}:block/part/{pid}', 'f': f'{NS}:block/part/{pid}_foot'},
                'elements': [{'from': [6, 2, 6], 'to': [10, 16, 10], 'faces': faces_all('#t')},
                             {'from': [3, 2, 7], 'to': [6, 10, 9], 'faces': faces_all('#t')},
                             {'from': [10, 2, 7], 'to': [13, 10, 9], 'faces': faces_all('#t')},
                             {'from': [2, 0, 2], 'to': [14, 2, 14], 'faces': faces_all('#f')}]}
    if kind == 'tower':
        tex(m, f'block/part/{pid}', lambda n: B.truss(n, color, s))
        tex(m, f'block/part/{pid}_motor', lambda n: B.to_img(B.panel(n, accent, s, rivets=False)))
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/part/{pid}', 't': f'{NS}:block/part/{pid}', 'm': f'{NS}:block/part/{pid}_motor'},
                'elements': [{'from': [5, 0, 5], 'to': [11, 10, 11], 'faces': faces_all('#t')},
                             {'from': [6, 10, 6], 'to': [10, 16, 10], 'faces': faces_all('#m')}]}
    # box (avionics, batteries, life support, parachute pack)
    tex(m, f'block/part/{pid}', lambda n: B.box_face(n, color, accent, s))
    tex(m, f'block/part/{pid}_top', lambda n: B.to_img(B.panel(n, color, s, seam=False)))
    return {'parent': 'minecraft:block/cube_column', 'textures': {'side': f'{NS}:block/part/{pid}', 'end': f'{NS}:block/part/{pid}_top'}}


def gen_rocketry():
    m = 'rocketry'
    simple_item(m, 'rocket_recovery_tool', lambda n: icons.wrench(n))
    lang(m, 'item.heliosphere.rocket_recovery_tool', '火箭回收扳手', 'Rocket Recovery Wrench')
    lang(m, 'message.heliosphere.recovered', '§a已回收 %s 个火箭部件', '§aRecovered %s rocket parts')
    data(m, 'recipe/rocket_recovery_tool.json', {'type': 'minecraft:crafting_shaped', 'category': 'tools', 'pattern': [' SS', ' CS', 'S  '],
         'key': {'S': f'{NS}:steel_ingot', 'C': f'{NS}:circuit_board'}, 'result': {'id': f'{NS}:rocket_recovery_tool', 'count': 1}})
    for (pid, zh, en, kind, color, accent) in C.PARTS:
        model = part_models(m, pid, kind, color, accent)
        asset(m, f'models/block/{pid}.json', model)
        asset(m, f'blockstates/{pid}.json', {'variants': {'': {'model': f'{NS}:block/{pid}'}}})
        block_item(m, pid)
        lang(m, f'block.heliosphere.{pid}', zh, en)
        mineable(pid, 'pickaxe')
        loot_self(m, pid, [{'function': 'minecraft:copy_components', 'source': 'block_entity',
                            'include': [f'{NS}:quality', f'{NS}:fill']}])
    tex(m, 'block/launch_pad_top', lambda n: B.pad_top(n, seed('pad')))
    tex(m, 'block/launch_pad_side', lambda n: B.to_img(B.panel(n, '#5A5A5E', 'padside')))
    asset(m, 'blockstates/launch_pad.json', {'variants': {'': {'model': f'{NS}:block/launch_pad'}}})
    asset(m, 'models/block/launch_pad.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': f'{NS}:block/launch_pad_top', 'bottom': f'{NS}:block/launch_pad_side', 'side': f'{NS}:block/launch_pad_side'}})
    block_item(m, 'launch_pad')
    tex(m, 'block/launch_console_front', lambda n: B.console_front(n, seed('console')))
    tex(m, 'block/launch_console_side', lambda n: B.to_img(B.panel(n, '#4A5058', 'consoleside')))
    asset(m, 'models/block/launch_console.json', {'parent': 'minecraft:block/orientable', 'textures': {
        'top': f'{NS}:block/launch_console_side', 'front': f'{NS}:block/launch_console_front', 'side': f'{NS}:block/launch_console_side'}})
    asset(m, 'blockstates/launch_console.json', {'variants': {f'facing={f}': dict({'model': f'{NS}:block/launch_console'}, **({'y': y} if y else {}))
                                                              for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    block_item(m, 'launch_console')
    for bid, zh, en in (('launch_pad', '发射台', 'Launch Pad'), ('launch_console', '发射控制台', 'Launch Control Console')):
        lang(m, f'block.heliosphere.{bid}', zh, en)
        mineable(bid, 'pickaxe', 'stone')
        loot_self(m, bid)
    blueprints = (('sounding', '探空一号（预组装）', 'Sounding-1 (prebuilt)', '#F0F0F0', '#2080C0'),
                  ('qiming_1', '启明一号 · 地月火箭（预组装）', 'Qiming-1 Moon Rocket (prebuilt)', '#F0F0F0', '#C03020'),
                  ('qiming_2', '启明二号 · 火星飞船（预组装）', 'Qiming-2 Mars Ship (prebuilt)', '#D8DCE4', '#6070A0'))
    for bid, zh, en, body, acc in blueprints:
        simple_item(m, f'rocket_{bid}', lambda n, b=body, a=acc: icons.rocket(n, b, a))
        lang(m, f'item.heliosphere.rocket_{bid}', zh, en)


def gen_planets():
    m = 'planets'
    for (module, sid, zh, en, color, accent) in C.ARMOR:
        for piece, pzh, pen in C.ARMOR_PIECES:
            iid = f'{sid}_{piece}'
            painter = {'helmet': icons.helmet, 'chestplate': icons.chest, 'leggings': icons.legs, 'boots': icons.boots}[piece]
            simple_item(m, iid, lambda n, p=painter, c=color, a=accent: p(n, c, a))
            lang(m, f'item.heliosphere.{iid}', f'{zh}{pzh}', f'{en} {pen}')
        asset(m, f'equipment/{sid}.json', {'layers': {'humanoid': [{'texture': f'{NS}:{sid}'}], 'humanoid_leggings': [{'texture': f'{NS}:{sid}'}]}})
        for w, base in ((64, res(m)), (128, HD)):
            for layer, legs in (('humanoid', False), ('humanoid_leggings', True)):
                path = os.path.join(base, 'assets', NS, 'textures', 'entity', 'equipment', layer, f'{sid}.png')
                os.makedirs(os.path.dirname(path), exist_ok=True)
                B.suit_layer(w, color, accent, legs).save(path)


# -------------------------------------------------------------------------------------------- recipes

def shaped(module, rid, pattern, key, result, count=1):
    data(module, f'recipe/{rid}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key,
                                        'result': {'id': result, 'count': count}})


def shapeless(module, rid, ingredients, result, count=1):
    data(module, f'recipe/{rid}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients,
                                        'result': {'id': result, 'count': count}})


def smelt(module, rid, ingredient, result, xp=0.5, blast=True):
    data(module, f'recipe/{rid}.json', {'type': 'minecraft:smelting', 'category': 'misc', 'cookingtime': 200, 'experience': xp,
                                        'ingredient': ingredient, 'result': {'id': result}})
    if blast:
        data(module, f'recipe/{rid}_blasting.json', {'type': 'minecraft:blasting', 'category': 'misc', 'cookingtime': 100, 'experience': xp,
                                                     'ingredient': ingredient, 'result': {'id': result}})


def h(i):
    return f'{NS}:{i}'


def gen_recipes():
    i, r = 'industry', 'rocketry'
    shapeless(i, 'copper_wire', ['minecraft:copper_ingot'], h('copper_wire'), 3)
    shaped('core', 'cable', ['KKK', 'WWW', 'KKK'], {'K': 'minecraft:dried_kelp', 'W': h('copper_wire')}, h('cable'), 8)
    shaped(i, 'empty_canister', [' I ', 'I I', ' I '], {'I': 'minecraft:iron_ingot'}, h('empty_canister'), 2)
    shaped(i, 'circuit_board', ['WRW', 'QIQ', 'WRW'], {'W': h('copper_wire'), 'R': 'minecraft:redstone', 'Q': 'minecraft:quartz', 'I': 'minecraft:iron_nugget'},
           h('circuit_board'), 2)
    M = {
        'coal_generator': (['III', 'IFI', 'CRC'], {'I': 'minecraft:iron_ingot', 'F': 'minecraft:furnace', 'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'}),
        'electric_arc_furnace': (['GKG', 'IBI', 'IRI'], {'G': h('graphite'), 'K': h('cable'), 'I': 'minecraft:iron_ingot', 'B': 'minecraft:blast_furnace',
                                                        'R': 'minecraft:redstone'}),
        'alloy_furnace': (['SSS', 'SBS', 'CRC'], {'S': h('steel_ingot'), 'B': 'minecraft:blast_furnace', 'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'}),
        'electrolyzer': (['SGS', 'WCW', 'SRS'], {'S': h('steel_ingot'), 'G': 'minecraft:glass', 'W': h('copper_wire'), 'C': 'minecraft:cauldron', 'R': 'minecraft:redstone'}),
        'chemical_reactor': (['SGS', 'PCP', 'SRS'], {'S': h('stainless_steel_ingot'), 'G': 'minecraft:glass', 'P': 'minecraft:copper_ingot', 'C': 'minecraft:cauldron',
                                                    'R': 'minecraft:redstone'}),
        'refinery': (['SPS', 'PFP', 'SCS'], {'S': h('steel_ingot'), 'P': h('stainless_steel_ingot'), 'F': 'minecraft:furnace', 'C': 'minecraft:cauldron'}),
        'air_separator': (['SGS', 'WPW', 'SRS'], {'S': h('stainless_steel_ingot'), 'G': 'minecraft:glass', 'W': h('copper_wire'), 'P': 'minecraft:piston',
                                                 'R': 'minecraft:redstone'}),
        'vacuum_furnace': (['TMT', 'SBS', 'SRS'], {'T': h('tungsten_ingot'), 'M': h('molybdenum_ingot'), 'S': h('steel_ingot'), 'B': 'minecraft:blast_furnace',
                                                  'R': 'minecraft:redstone_block'}),
        'cnc_machine': (['TDT', 'SPS', 'SRS'], {'T': h('tungsten_ingot'), 'D': 'minecraft:diamond', 'S': h('steel_ingot'), 'P': 'minecraft:piston',
                                               'R': 'minecraft:redstone_block'}),
        'metal_3d_printer': (['SDS', 'GPG', 'SRS'], {'S': h('stainless_steel_ingot'), 'D': 'minecraft:diamond', 'G': 'minecraft:glowstone', 'P': h('processor'),
                                                    'R': 'minecraft:redstone_block'}),
        'electronics_assembler': (['SGS', 'WCW', 'SRS'], {'S': h('steel_ingot'), 'G': 'minecraft:gold_ingot', 'W': h('copper_wire'), 'C': h('circuit_board'),
                                                         'R': 'minecraft:redstone'}),
        'assembly_station': (['PCP', 'SKS', 'SRS'], {'P': 'minecraft:piston', 'C': h('circuit_board'), 'S': h('steel_ingot'), 'K': 'minecraft:crafting_table',
                                                    'R': 'minecraft:redstone_block'}),
        'engine_test_stand': (['B B', 'BCB', 'III'], {'B': 'minecraft:iron_bars', 'C': h('circuit_board'), 'I': 'minecraft:iron_block'}),
        'centrifuge': (['SWS', 'WCW', 'SIS'], {'S': h('steel_ingot'), 'W': h('copper_wire'), 'C': h('circuit_board'), 'I': 'minecraft:iron_block'}),
        'solar_panel': (['CCC', 'SBS', 'SWS'], {'C': h('solar_cell'), 'S': h('steel_ingot'), 'B': h('circuit_board'), 'W': h('copper_wire')}),
        'battery_bank': (['BBB', 'SCS', 'SSS'], {'B': h('battery_cell'), 'S': h('steel_ingot'), 'C': h('circuit_board')}),
    }
    P = {
        'storm_shield_generator': (['CMC', 'NPN', 'SBS'], {'C': h('copper_wire'), 'M': h('ndfeb_magnet'), 'N': h('niobium_ingot'), 'P': h('processor'),
                                                         'S': h('stainless_steel_plate'), 'B': h('battery_cell')}),
        'suit_fabricator': (['NGN', 'CLC', 'SBS'], {'N': h('nylon'), 'G': 'minecraft:glass', 'C': h('circuit_board'), 'L': 'minecraft:loom',
                                                  'S': h('stainless_steel_ingot'), 'B': h('battery_cell')}),
        'oxygen_compressor': (['SGS', 'WPW', 'SCS'], {'S': h('steel_ingot'), 'G': 'minecraft:glass', 'W': h('copper_wire'), 'P': 'minecraft:piston', 'C': h('circuit_board')}),
        'oxygen_distributor': (['GGG', 'WPW', 'SCS'], {'S': h('stainless_steel_ingot'), 'G': 'minecraft:glass', 'W': h('copper_wire'), 'P': 'minecraft:piston',
                                                      'C': h('circuit_board')}),
        'regolith_processor': (['SPS', 'WCW', 'SSS'], {'S': h('steel_ingot'), 'P': 'minecraft:piston', 'W': h('copper_wire'), 'C': h('circuit_board')}),
        'ilmenite_reducer': (['TGT', 'SBS', 'SCS'], {'T': h('tungsten_ingot'), 'G': 'minecraft:glass', 'S': h('stainless_steel_ingot'), 'B': 'minecraft:blast_furnace',
                                                    'C': h('circuit_board')}),
        'vacuum_deposition': (['SGS', 'GPG', 'SGS'], {'S': h('stainless_steel_ingot'), 'G': 'minecraft:glass', 'P': h('processor')}),
        'sabatier_reactor': (['SNS', 'WCW', 'SGS'], {'S': h('stainless_steel_ingot'), 'N': h('nickel_ingot'), 'W': h('copper_wire'), 'C': h('circuit_board'),
                                                    'G': 'minecraft:glass'}),
        'atmosphere_extractor': (['SPS', 'GCG', 'SPS'], {'S': h('stainless_steel_ingot'), 'P': 'minecraft:piston', 'G': 'minecraft:glass', 'C': h('circuit_board')}),
        'isru_electrolyzer': (['SLS', 'WCW', 'SGS'], {'S': h('stainless_steel_ingot'), 'L': h('platinum_ingot'), 'W': h('copper_wire'), 'C': h('circuit_board'),
                                                     'G': 'minecraft:glass'}),
    }
    for mid, (pat, key) in M.items():
        shaped(i, mid, pat, key, h(mid))
    for mid, (pat, key) in P.items():
        shaped('planets', mid, pat, key, h(mid))
    shaped(r, 'launch_pad', ['PPP', 'CCC'], {'P': h('steel_plate'), 'C': 'minecraft:smooth_stone'}, h('launch_pad'), 4)
    shaped(r, 'launch_console', ['SGS', 'PCP', 'SRS'], {'S': h('steel_ingot'), 'G': 'minecraft:glass_pane', 'P': h('processor'), 'C': h('circuit_board'),
                                                         'R': 'minecraft:redstone'}, h('launch_console'))
    shapeless('guide', 'star_scroll', ['minecraft:paper', 'minecraft:paper', 'minecraft:compass'], h('star_scroll'))
    for raw_, ing in (('raw_cassiterite', 'tin_ingot'), ('raw_galena', 'lead_ingot'), ('raw_sphalerite', 'zinc_ingot')):
        smelt(i, f'{ing}_from_{raw_}', h(raw_), h(ing))
    smelt('sphereworld', 'moon_bricks', h('moon_regolith'), h('moon_bricks'), 0.1, False)
    smelt('sphereworld', 'mars_bricks', h('mars_regolith'), h('mars_bricks'), 0.1, False)


# -------------------------------------------------------------------------------------------- guide

def gen_guide():
    m = 'guide'
    simple_item(m, 'star_scroll', lambda n: icons.scroll(n))
    lang(m, 'item.heliosphere.star_scroll', '星际航行卷轴', 'The Star Scroll')
    asset(m, 'guide/chapters.json', [{'title': c['title'], 'intro': c['intro'], 'objectives': c['objectives'], 'tips': c['tips']} for c in G.CHAPTERS])
    data(m, 'advancement/root.json', {
        'display': {'icon': {'id': h('star_scroll')}, 'title': {'translate': 'advancement.heliosphere.root'},
                    'description': {'translate': 'advancement.heliosphere.root.desc'}, 'background': 'heliosphere:block/anorthosite',
                    'show_toast': False, 'announce_to_chat': False},
        'criteria': {'tick': {'trigger': 'minecraft:tick'}}, 'requirements': [['tick']]})
    lang(m, 'advancement.heliosphere.root', '日球层', 'Heliosphere')
    lang(m, 'advancement.heliosphere.root.desc', '从地球的矿脉到红色星球', 'From Earth\'s ores to the red planet')
    parent = 'heliosphere:root'
    for c in G.CHAPTERS:
        crit = {o: {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': h(o)}]}} for o in c['objectives']}
        data(m, f'advancement/{c["id"]}.json', {
            'parent': parent,
            'display': {'icon': {'id': h(c['icon'])}, 'title': {'translate': f'advancement.heliosphere.{c["id"]}'},
                        'description': {'translate': f'advancement.heliosphere.{c["id"]}.desc'}, 'frame': 'goal' if c['id'] in ('moon', 'mars') else 'task'},
            'criteria': crit, 'requirements': [[o] for o in c['objectives']]})
        lang(m, f'advancement.heliosphere.{c["id"]}', c['title'], c['id'].replace('_', ' ').title())
        lang(m, f'advancement.heliosphere.{c["id"]}.desc', c['intro'][:60] + ('…' if len(c['intro']) > 60 else ''), c['id'])
        parent = f'heliosphere:{c["id"]}'


# -------------------------------------------------------------------------------------------- output

def write_lang_and_tags():
    for module, langs in LANG.items():
        for code, d in langs.items():
            if d:
                wjson(os.path.join(res(module), 'assets', NS, 'lang', f'{code}.json'), dict(sorted(d.items())))
    # tags: put block/item tags into the core module (always installed)
    for (ns, kind, name), values in TAGS.items():
        module = 'core' if kind in ('block', 'item') else 'sphereworld'
        wjson(os.path.join(res(module), 'data', ns, 'tags', kind, f'{name}.json'), {'replace': False, 'values': sorted(set(values))})


def main():
    for module in LANG:
        r = res(module)
        for sub in ('assets', 'data'):
            p = os.path.join(r, sub)
            if os.path.isdir(p):
                shutil.rmtree(p)
    if os.path.isdir(HD):
        shutil.rmtree(HD)
    gen_core()
    gen_terrain()
    gen_industry()
    gen_items()
    gen_machines()
    gen_rocketry()
    gen_planets()
    gen_recipes()
    gen_guide()
    write_lang_and_tags()
    os.makedirs(HD, exist_ok=True)
    wjson(os.path.join(HD, 'pack.mcmeta'), {'pack': {'description': '日球层 Heliosphere · 32x 高清材质', 'min_format': [75, 0], 'max_format': [75, 0]}})
    icons.rocket(64).save(os.path.join(HD, 'pack.png'))
    print(f'wrote {len(WRITTEN)} json files')


if __name__ == '__main__':
    main()
