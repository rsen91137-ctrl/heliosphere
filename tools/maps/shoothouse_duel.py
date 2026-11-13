#!/usr/bin/env python3
"""
生成「战术小屋 · 单挑」风格的 1v1 / 2v2 竞技地图（原创布局，致敬 CoD 单挑模式的
训练场小屋风格），输出 Sponge Schematic v3（.schem），适用于 Minecraft 1.21.11
+ WorldEdit / FAWE / Litematica(导入) 等。

用法:
    python3 tools/maps/shoothouse_duel.py [输出目录]

坐标约定（schem 内部坐标，粘贴时以原点为左下西北角）:
    X: 0..48 (西 -> 东)   Z: 0..32 (北 -> 南)   Y: 0..9
    红方出生点在西侧，蓝方在东侧；地图对中心点 (24, 16) 做 180° 旋转对称，保证公平。
"""
import gzip
import io
import os
import struct
import sys

DATA_VERSION = 4671  # Minecraft 1.21.11

W, H, L = 49, 10, 33      # X, Y, Z
CX, CZ = 24, 16           # 旋转对称中心

grid = {}                  # (x,y,z) -> blockstate 字符串


# ---------------------------------------------------------------- 工具函数
FLIP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def rot_state(state):
    """180° 旋转方块状态中的 facing。"""
    if "[" not in state:
        return state
    name, props = state[:-1].split("[", 1)
    out = []
    for p in props.split(","):
        k, v = p.split("=")
        if k == "facing" and v in FLIP:
            v = FLIP[v]
        out.append(f"{k}={v}")
    return f"{name}[{','.join(out)}]"


def put(x, y, z, state):
    if 0 <= x < W and 0 <= y < H and 0 <= z < L:
        grid[(x, y, z)] = state


def sym(x, y, z, state):
    """放置方块并同时放置其 180° 旋转对称的副本。"""
    put(x, y, z, state)
    put(2 * CX - x, y, 2 * CZ - z, rot_state(state))


def box(x1, y1, z1, x2, y2, z2, state, mirror=True):
    f = sym if mirror else put
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                f(x, y, z, state)


def clear(x1, y1, z1, x2, y2, z2, mirror=True):
    box(x1, y1, z1, x2, y2, z2, "minecraft:air", mirror)


# ---------------------------------------------------------------- 材质
FLOOR = "minecraft:polished_andesite"
FLOOR_LINE = "minecraft:andesite"
WALL = "minecraft:gray_concrete"
WALL_TOP = "minecraft:light_gray_concrete"
HAZ_A = "minecraft:yellow_concrete"
HAZ_B = "minecraft:black_concrete"
PLY = "minecraft:birch_planks"            # 胶合板
FRAME = "minecraft:stripped_spruce_log[axis=y]"
ROOF = "minecraft:smooth_stone"
SANDBAG = "minecraft:packed_mud"
SANDBAG_TOP = "minecraft:mud_brick_slab[type=bottom,waterlogged=false]"
TIRE = "minecraft:black_concrete"
TIRE_TOP = "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]"
CRATE = "minecraft:barrel[facing=up,open=false]"
CRATE2 = "minecraft:spruce_planks"
LIGHT = "minecraft:sea_lantern"
BARRIER = "minecraft:barrier"
LADDER = "minecraft:ladder[facing={f},waterlogged=false]"


def build():
    # ---------- 地板（全部，非镜像）
    for x in range(W):
        for z in range(L):
            line = (x % 8 == 0) or (z % 8 == 0)
            put(x, 0, z, FLOOR_LINE if line else FLOOR)

    # ---------- 外墙 y1..8 + 顶部屏障
    for x in range(W):
        for z in range(L):
            edge = x in (0, W - 1) or z in (0, L - 1)
            if edge:
                for y in range(1, 9):
                    if y == 1:
                        put(x, y, z, HAZ_A if (x + z) % 2 == 0 else HAZ_B)
                    elif y >= 6:
                        put(x, y, z, WALL_TOP)
                    else:
                        put(x, y, z, WALL)
            put(x, 9, z, BARRIER)

    # 外墙灯带
    for x in range(4, W - 1, 6):
        put(x, 5, 0, LIGHT)
        put(x, 5, L - 1, LIGHT)
    for z in range(4, L - 1, 6):
        put(0, 5, z, LIGHT)
        put(W - 1, 5, z, LIGHT)

    # ---------- 出生区（红=西，蓝=东）
    for x in range(1, 7):
        for z in range(1, L - 1):
            put(x, 0, z, "minecraft:red_concrete" if (x + z) % 2 else "minecraft:red_terracotta")
            put(W - 1 - x, 0, L - 1 - z,
                "minecraft:blue_concrete" if (x + z) % 2 else "minecraft:blue_terracotta")
    # 出生点标记
    put(3, 0, CZ, "minecraft:gold_block")
    put(W - 4, 0, CZ, "minecraft:gold_block")
    put(3, 0, CZ - 6, "minecraft:gold_block")
    put(W - 4, 0, CZ + 6, "minecraft:gold_block")

    # 出生区掩体墙 x=8，三个 3 格缺口
    for z in range(3, 30):
        if z in (6, 7, 8, 15, 16, 17, 24, 25, 26):
            continue
        box(8, 1, z, 8, 2, z, PLY)
    # 墙柱
    for z in (3, 5, 9, 14, 18, 23, 27, 29):
        box(8, 1, z, 8, 3, z, FRAME)
    # 出生区角落补给箱
    box(1, 1, 1, 2, 1, 2, CRATE)
    box(1, 2, 1, 1, 2, 1, CRATE)
    box(1, 1, 30, 2, 1, 31, CRATE2)

    # ---------- 中央战术小屋 x18..30, z11..21, 墙 y1..4, 屋顶 y5
    X1, X2, Z1, Z2 = 18, 30, 11, 21
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            put(x, 0, z, "minecraft:spruce_planks")   # 室内木地板
            if x in (X1, X2) or z in (Z1, Z2):
                corner = x in (X1, X2) and z in (Z1, Z2)
                for y in range(1, 5):
                    put(x, y, z, FRAME if corner else PLY)
            put(x, 5, z, ROOF)
    # 屋顶室内照明
    for (x, z) in ((20, 14), (22, 18), (24, 13), (24, 19)):
        sym(x, 5, z, LIGHT)
    # 屋顶女儿墙 y6，留缺口
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            if x in (X1, X2) or z in (Z1, Z2):
                put(x, 6, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    clear(23, 6, Z1, 25, 6, Z1)        # 北/南缺口
    clear(X1, 6, 15, X1, 6, 17)        # 西/东缺口

    # 西门 (x=18, z13..14) / 东门镜像
    clear(X1, 1, 13, X1, 3, 14)
    # 西窗 (x=18, z18..19, y2..3)
    box(X1, 2, 18, X1, 3, 19, "minecraft:air")
    # 北门 (z=11, x21..22) / 南门镜像
    clear(21, 1, Z1, 22, 3, Z1)
    # 北窗 (z=11, x26..27, y2..3)
    clear(26, 2, Z1, 27, 3, Z1)

    # 中央隔断 x=24，缺口 z15..17
    for z in range(Z1 + 1, Z2):
        if 15 <= z <= 17:
            continue
        box(24, 1, z, 24, 4, z, PLY)
    # 门楣
    box(24, 4, 15, 24, 4, 17, FRAME)

    # 室内半墙/桌子（蹲伏掩体）
    box(20, 1, 17, 22, 1, 17, "minecraft:spruce_slab[type=top,waterlogged=false]")
    sym(19, 1, 20, CRATE)
    sym(23, 1, 12, CRATE)
    sym(23, 2, 12, CRATE)

    # 梯子通往屋顶（贴北墙），屋顶开口
    for y in range(1, 6):
        sym(20, y, 12, LADDER.format(f="south"))
    sym(20, 5, 12, LADDER.format(f="south"))

    # 中心旗点（加时占点）
    put(CX, 0, CZ, "minecraft:gold_block")
    put(CX, 1, CZ, "minecraft:light_weighted_pressure_plate[power=0]")

    # ---------- 西半场掩体（全部自动旋转镜像到东半场）
    # 北侧长胶合板墙 z=8, x13..18，高 3，带射击窗
    box(13, 1, 8, 18, 3, 8, PLY)
    box(13, 1, 8, 13, 3, 8, FRAME)
    box(18, 1, 8, 18, 3, 8, FRAME)
    clear(15, 2, 8, 16, 2, 8)
    # 北侧通道：堆叠木箱
    box(11, 1, 3, 12, 2, 4, CRATE)
    box(11, 3, 3, 11, 3, 3, CRATE)
    box(13, 1, 3, 13, 1, 3, CRATE2)
    # 北侧通道近中线的轮胎堆
    box(20, 1, 4, 21, 1, 5, TIRE)
    sym(20, 2, 4, TIRE_TOP)
    sym(21, 2, 5, TIRE_TOP)
    # 中路：西门前沙袋（低掩体）
    box(14, 1, 15, 14, 1, 19, SANDBAG)
    for z in range(15, 20):
        sym(14, 2, z, SANDBAG_TOP)
    # 中路轮胎
    box(11, 1, 12, 11, 1, 13, TIRE)
    sym(11, 2, 12, TIRE_TOP)
    box(11, 1, 20, 11, 1, 20, TIRE)
    # 南侧 L 形胶合板墙
    box(15, 1, 25, 18, 3, 25, PLY)
    box(15, 1, 25, 15, 3, 28, PLY)
    box(15, 1, 25, 15, 3, 25, FRAME)
    clear(17, 2, 25, 17, 2, 25)
    # 南侧高台（脚手架感：可跳上的木箱 + 台阶）
    box(10, 1, 28, 12, 1, 30, CRATE2)
    box(10, 2, 29, 11, 2, 30, CRATE2)
    sym(13, 1, 29, "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
    sym(12, 2, 29, "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
    # 中线附近的独立掩体柱（北/南通道中间）
    box(24, 1, 6, 24, 2, 7, FRAME)
    box(23, 1, 6, 23, 1, 7, SANDBAG)
    # 小屋西北角外的木箱 & 南侧中场短墙
    box(16, 1, 11, 16, 2, 11, CRATE)
    box(16, 1, 12, 16, 1, 12, CRATE)
    box(11, 1, 22, 11, 2, 23, PLY)


# ---------------------------------------------------------------- NBT 写出
TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT = 0, 1, 2, 3
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY = 7, 8, 9, 10, 11


class Short(int):
    pass


class IntArray(list):
    pass


def w_str(b, s):
    data = s.encode("utf-8")
    b.write(struct.pack(">H", len(data)))
    b.write(data)


def tag_type(v):
    if isinstance(v, Short):
        return TAG_SHORT
    if isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, (bytes, bytearray)):
        return TAG_BYTE_ARRAY
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, IntArray):
        return TAG_INT_ARRAY
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, dict):
        return TAG_COMPOUND
    raise TypeError(type(v))


def w_payload(b, v):
    t = tag_type(v)
    if t == TAG_SHORT:
        b.write(struct.pack(">h", v))
    elif t == TAG_BYTE:
        b.write(struct.pack(">b", int(v)))
    elif t == TAG_INT:
        b.write(struct.pack(">i", v))
    elif t == TAG_BYTE_ARRAY:
        b.write(struct.pack(">i", len(v)))
        b.write(bytes(v))
    elif t == TAG_STRING:
        w_str(b, v)
    elif t == TAG_INT_ARRAY:
        b.write(struct.pack(">i", len(v)))
        for i in v:
            b.write(struct.pack(">i", i))
    elif t == TAG_LIST:
        et = tag_type(v[0]) if v else TAG_END
        b.write(struct.pack(">bi", et, len(v)))
        for e in v:
            w_payload(b, e)
    elif t == TAG_COMPOUND:
        for k, e in v.items():
            b.write(struct.pack(">b", tag_type(e)))
            w_str(b, k)
            w_payload(b, e)
        b.write(b"\x00")


def varint(n):
    out = bytearray()
    while True:
        byte = n & 0x7F
        n >>= 7
        if n:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return out


def write_schem(path):
    palette = {"minecraft:air": 0}
    data = bytearray()
    for y in range(H):
        for z in range(L):
            for x in range(W):
                s = grid.get((x, y, z), "minecraft:air")
                if s not in palette:
                    palette[s] = len(palette)
                data += varint(palette[s])
    root = {
        "Schematic": {
            "Version": 3,
            "DataVersion": DATA_VERSION,
            "Metadata": {
                "Name": "Shoot House Duel",
                "Date": 0,
                "WorldEdit": {"Version": "7.3.0", "Origin": IntArray([0, 0, 0])},
            },
            "Width": Short(W),
            "Height": Short(H),
            "Length": Short(L),
            "Offset": IntArray([0, 0, 0]),
            "Blocks": {
                "Palette": palette,
                "Data": bytes(data),
                "BlockEntities": [],
            },
        }
    }
    buf = io.BytesIO()
    buf.write(struct.pack(">b", TAG_COMPOUND))
    w_str(buf, "")
    w_payload(buf, root)
    with gzip.open(path, "wb") as f:
        f.write(buf.getvalue())
    return palette


# ---------------------------------------------------------------- 俯视预览图
COLORS = {
    "polished_andesite": (132, 135, 134), "andesite": (110, 112, 112), "gray_concrete": (54, 57, 61),
    "light_gray_concrete": (125, 125, 115), "yellow_concrete": (240, 175, 21),
    "black_concrete": (20, 21, 25), "birch_planks": (192, 175, 121),
    "stripped_spruce_log": (115, 89, 52), "smooth_stone": (158, 158, 158),
    "packed_mud": (142, 106, 79), "mud_brick_slab": (137, 103, 79),
    "polished_blackstone_slab": (53, 48, 56), "barrel": (134, 100, 58),
    "spruce_planks": (114, 84, 48), "sea_lantern": (172, 199, 190),
    "red_concrete": (142, 32, 32), "red_terracotta": (143, 61, 46),
    "blue_concrete": (44, 46, 143), "blue_terracotta": (74, 59, 91),
    "gold_block": (246, 208, 61), "ladder": (160, 120, 70),
    "stone_brick_wall": (122, 121, 122), "spruce_slab": (114, 84, 48),
    "spruce_stairs": (114, 84, 48), "light_weighted_pressure_plate": (246, 208, 61),
}


def render_preview(path, scale=16):
    from PIL import Image, ImageDraw
    img = Image.new("RGB", (W * scale, L * scale))
    d = ImageDraw.Draw(img)
    for x in range(W):
        for z in range(L):
            top, ty = None, 0
            for y in range(8, -1, -1):
                s = grid.get((x, y, z))
                if s and s not in ("minecraft:air", BARRIER):
                    top, ty = s, y
                    break
            name = top.split("[")[0].split(":")[1] if top else "air"
            r, g, b = COLORS.get(name, (255, 0, 255))
            k = 0.75 + 0.06 * min(ty, 6)       # 越高越亮
            c = tuple(min(255, int(v * k)) for v in (r, g, b))
            d.rectangle([x * scale, z * scale, (x + 1) * scale - 1, (z + 1) * scale - 1], fill=c)
            if ty > 0:
                d.text((x * scale + 4, z * scale + 2), str(ty), fill=(255, 255, 255))
    img.save(path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    build()
    pal = write_schem(os.path.join(out, "shoothouse_duel.schem"))
    render_preview(os.path.join(out, "shoothouse_duel_preview.png"))
    print(f"OK: {W}x{H}x{L}, {len(pal)} palette entries, "
          f"{sum(1 for s in grid.values() if s != 'minecraft:air')} blocks")
