#!/usr/bin/env python3
"""
Gun skins after the player's own sketches (overlay 06-weapons): each conventional gun is rebuilt part by part as a
block-element model (receiver, handguard, barrel, magazine, grip, stock, sights), and every face is painted in model
space (cooling ribs, vent slots, perforated jackets, checkering, wood grain, stamped markings), then packed into one
texture. Anchors the game relies on are kept from the released models: the muzzle, the bore line, the rail and
iron-sight heights, the hand position, the magazine well (attachments and aiming stay right).

  LT-70 5.56x45 assault rifle        -> assault_rifle
  LT-77 7.62x39 rifle                -> battle_rifle
  C-36X 9x39 carbine                 -> burst_carbine
  C-32 9x21 compact SMG              -> compact_smg
  suppressed SMG (long integral can) -> smg
  small SMG, magazine in the grip    -> machine_pistol
  M445 semi-automatic rifle          -> designated_marksman_rifle
  M77A 7.7x54 bolt-action rifle      -> bolt_rifle
  thumbhole anti-materiel rifle      -> sniper_rifle
  WRK-4 8x50 general-purpose MG      -> light_machine_gun
  HM14.5/1930 heavy machine gun      -> rotary_cannon

Run from the repository root:  python3 tools/gen/gun_skins.py  (reads the released models for their display blocks)
"""
import json, math, os, sys, zipfile, glob
import numpy as np
from PIL import Image

OUT = 'jars/06-weapons/src/main/resources/assets/heliosphere'
PX = 4                      # texels per model unit
W = 256                     # atlas width
rng = np.random.default_rng(1930)


def C(*v): return np.array(v, float)


MAT = {
    'blk':   (C(36, 37, 40), 3),   'steel': (C(64, 66, 70), 4),  'blue':  (C(46, 50, 60), 4),  'park': (C(72, 74, 70), 5),
    'lsteel': (C(132, 136, 142), 4), 'wood': (C(124, 76, 42), 5), 'woodl': (C(156, 106, 62), 5), 'bake': (C(108, 44, 32), 4),
    'od':    (C(84, 92, 62), 4),   'tan':  (C(158, 136, 100), 4), 'brass': (C(204, 162, 72), 4), 'rubber': (C(28, 28, 30), 2),
    'red':   (C(172, 44, 34), 3),  'earth': (C(112, 98, 76), 4), 'alu':  (C(152, 154, 158), 4), 'cord': (C(150, 126, 88), 6),
    'dark':  (C(22, 22, 24), 2),   'green': (C(66, 78, 58), 4),
}


class Box:
    def __init__(self, f, t, mat, feats=(), rot=None, icon=True):
        self.f, self.t, self.mat, self.feats, self.rot, self.icon = list(f), list(t), mat, list(feats), rot, icon


def B(x0, y0, x1, y1, w, mat, *feats, z=8.0, rot=None):
    return [Box((x0, y0, z - w / 2), (x1, y1, z + w / 2), mat, feats, rot)]


def BZ(x0, y0, z0, x1, y1, z1, mat, *feats, rot=None):
    return [Box((x0, y0, z0), (x1, y1, z1), mat, feats, rot)]


def barrel(x0, x1, yc, d, mat, *feats, z=8.0):
    """A round part: a square box and the same box turned 45 degrees about its axis (an octagon)."""
    a = Box((x0, yc - d / 2, z - d / 2), (x1, yc + d / 2, z + d / 2), mat, feats)
    b = Box((x0, yc - d / 2, z - d / 2), (x1, yc + d / 2, z + d / 2), mat, feats, rot=('x', 45, (x0, yc, z)), icon=False)
    return [a, b]


def slant(xc, ytop, length, w, depth, angle, mat, *feats, z=8.0):
    """A part hanging from (xc, ytop), turned by `angle` degrees (positive: the lower end goes forward)."""
    return [Box((xc - w / 2, ytop - length, z - depth / 2), (xc + w / 2, ytop, z + depth / 2), mat, feats, rot=('z', angle, (xc, ytop, z)))]


def chain(points, thick, depth, mat, *feats, z=8.0):
    """A curved magazine: segments from point to point (top first), each turned to follow the curve."""
    out = []
    for (xa, ya), (xb, yb) in zip(points, points[1:]):
        L = math.hypot(xb - xa, yb - ya)
        ang = math.degrees(math.atan2(xb - xa, ya - yb))
        out += slant(xa, ya + 0.15, L + 0.3, thick, depth, ang, mat, *feats, z=z)
    return out


def guard(x0, x1, ybot, ytop, mat='steel'):
    """Trigger guard (loop) and trigger."""
    t = 0.35
    return (B(x0, ybot, x1, ybot + t, 0.6, mat) + B(x1 - t, ybot, x1, ytop, 0.6, mat)
            + slant(x0 + (x1 - x0) * 0.45, ytop, ytop - ybot - 0.5, 0.3, 0.3, 12, 'lsteel'))


def post(x, ybase, ytop, w=0.5, mat='blk'):
    """A sight post with its protective ears."""
    return (B(x - 0.5, ybase, x + 0.5, ybase + 0.6, 1.2, mat) + B(x - w / 2, ybase, x + w / 2, ytop, 0.35, mat)
            + BZ(x - 0.3, ybase, 7.2, x + 0.3, ytop - 0.2, 7.45, mat) + BZ(x - 0.3, ybase, 8.55, x + 0.3, ytop - 0.2, 8.8, mat))


def rear(x, ybase, ytop, mat='blk'):
    """A rear sight block with an aperture notch."""
    return (B(x - 0.6, ybase, x + 0.6, ytop - 0.45, 1.4, mat) + BZ(x - 0.5, ytop - 0.5, 7.25, x + 0.5, ytop, 7.75, mat)
            + BZ(x - 0.5, ytop - 0.5, 8.25, x + 0.5, ytop, 8.75, mat))


def rail(x0, x1, y0, y1, w=1.5):
    return B(x0, y0, x1, y1, w, 'blk', ('ribs', 0.8, 0.45, 'yz'))


# ---- the guns ------------------------------------------------------------------------------------------------

def lt70():
    """LT-70, 5.56x45: ribbed round handguard, short box magazine, skeleton stock (sketch: LT-70 / LT-77 page)."""
    p = []
    p += B(-4.6, 6.3, 7.4, 9.2, 2.0, 'park', ('panel', 0.5), ('text', 'LT-70', -3.4, 8.6), ('rivets', [(5.6, 7.0), (6.6, 8.4)]))   # lower
    p += B(-5.2, 9.2, 8.8, 11.6, 2.2, 'park', ('port', 0.6, 3.4, 9.6, 10.9), ('ribs', 2.0, 0.08, 'y'))                                  # upper
    p += rail(-4.8, 8.6, 11.6, 12.4)
    p += B(-5.8, 10.8, -5.2, 11.5, 0.8, 'lsteel')                                                                                     # charging handle
    p += barrel(8.8, 18.4, 9.8, 3.6, 'blk', ('grooves', 0.55, 0.35), ('vents', [(10.2, 12.2, 8.6, 9.2), (13.4, 15.4, 8.6, 9.2)]))       # handguard
    p += B(8.8, 7.6, 18.0, 8.2, 2.0, 'blk')
    p += B(18.4, 8.9, 19.6, 11.0, 1.5, 'blk')                                                                                         # gas block
    p += post(19.0, 11.0, 13.45)
    p += barrel(19.6, 23.5, 9.9, 1.0, 'steel')
    p += barrel(23.5, 26.15, 9.9, 1.5, 'blk', ('ribs', 0.7, 0.4, 'yz'))                                                              # birdcage
    p += rear(-3.75, 12.4, 13.45)
    p += slant(-1.15, 6.4, 5.0, 2.0, 1.8, -17, 'blk', ('grooves', 0.6, 0.35))                                                          # grip
    p += guard(-0.2, 3.9, 5.7, 6.4)
    p += B(4.1, 5.7, 8.5, 6.6, 2.3, 'park')                                                                                          # magwell
    p += chain([(6.5, 6.0), (6.6, 1.2), (6.95, -3.2)], 3.3, 1.8, 'tan', ('grooves', 0.8, 0.3), ('magribs',))
    p += B(5.1, -3.6, 8.8, -3.0, 2.0, 'blk', rot=('z', 6, (6.95, -3.2, 8)))                                                         # floorplate
    # skeleton stock: buffer tube, top and bottom bars, web, butt with ribbed pad
    p += barrel(-9.0, -5.2, 10.2, 1.4, 'blk')
    p += B(-14.8, 10.4, -6.4, 11.4, 1.6, 'blk')
    p += B(-14.8, 7.0, -8.0, 8.0, 1.6, 'blk', rot=('z', -6, (-8.0, 8.0, 8)))
    p += B(-10.4, 7.6, -9.6, 10.6, 1.4, 'blk')
    p += B(-14.8, 6.6, -13.9, 11.4, 1.8, 'blk')
    p += B(-15.55, 6.2, -14.8, 11.8, 2.0, 'rubber', ('grooves', 0.5, 0.4))
    return p


def lt77():
    """LT-77, 7.62x39: stamped receiver, ribbed upper handguard, vented lower one, curved magazine, folding wire stock."""
    p = []
    p += B(-4.6, 6.6, 7.6, 11.2, 2.2, 'steel', ('rivets', [(-3.6, 7.2), (6.6, 7.2), (-3.6, 10.4)]), ('text', 'LT-77', -2.6, 8.4))
    p += B(-4.6, 11.2, 7.0, 12.33, 1.9, 'steel', ('ribs', 1.4, 0.15, 'y'))                                                          # dust cover
    p += BZ(0.4, 9.6, 9.1, 5.4, 10.1, 9.4, 'lsteel')                                                                                 # selector
    p += BZ(4.6, 10.6, 9.1, 5.6, 11.0, 9.8, 'lsteel')                                                                                # bolt handle
    p += B(7.6, 7.61, 17.6, 10.3, 2.5, 'bake', ('vents', [(9.0, 10.6, 8.2, 8.9), (11.4, 13.0, 8.2, 8.9), (13.8, 15.4, 8.2, 8.9)]), ('panel', 0.35))
    p += B(8.4, 10.3, 16.8, 11.7, 1.9, 'bake', ('ribs', 0.5, 0.35, 'yz'))                                                           # gas tube cover
    p += B(7.0, 11.2, 8.4, 12.33, 1.6, 'steel')
    p += rear(7.7, 12.33, 13.365, 'steel')
    p += B(17.6, 8.9, 18.9, 11.6, 1.6, 'steel')
    p += post(18.3, 11.6, 13.365, mat='steel')
    p += barrel(18.9, 24.0, 9.871, 1.0, 'steel')
    p += barrel(24.0, 26.238, 9.871, 1.5, 'steel', ('vents', [(24.4, 25.6, 10.3, 10.7)]))                                            # compensator
    p += slant(-1.5, 6.6, 5.1, 2.0, 1.8, -15, 'bake', ('check', 0.9))
    p += guard(-0.6, 3.4, 5.9, 6.6)
    p += chain([(5.5, 6.8), (5.6, 3.2), (5.9, 0.6), (5.45, -1.25)], 3.4, 1.8, 'bake', ('magribs',))
    # folding wire stock
    p += BZ(-14.2, 10.3, 7.0, -4.6, 10.8, 7.45, 'steel') + BZ(-14.2, 10.3, 8.55, -4.6, 10.8, 9.0, 'steel')
    p += BZ(-14.2, 7.2, 7.0, -4.6, 7.7, 7.45, 'steel', rot=('z', 8, (-4.6, 7.7, 7.2))) + BZ(-14.2, 7.2, 8.55, -4.6, 7.7, 9.0, 'steel', rot=('z', 8, (-4.6, 7.7, 8.8)))
    p += B(-15.8, 6.2, -14.2, 11.4, 1.9, 'steel', ('grooves', 0.5, 0.35))
    p += B(-15.2, 11.4, -14.4, 12.6, 1.0, 'steel')                                                                                   # shoulder hook
    return p


def c36x():
    """C-36X, 9x39: long finned handguard with round vents, short curved magazine with windows, folding stock."""
    p = []
    p += B(-3.8, 6.6, 8.8, 11.6, 2.2, 'blk', ('panel', 0.45), ('text', 'C-36X', -2.7, 8.2), ('port', 1.4, 4.0, 9.8, 11.0))
    p += rail(-3.6, 8.6, 11.6, 12.4)
    p += B(8.8, 7.6, 20.6, 11.4, 2.5, 'od', ('ribs', 0.55, 0.42, 'y', 9.0), ('perf', 1.5, 9.0, 0.32, 8.3))
    p += B(20.6, 8.9, 21.8, 11.6, 1.6, 'blk')
    p += post(21.2, 11.6, 13.45)
    p += barrel(21.8, 24.4, 9.9, 1.0, 'steel')
    p += barrel(24.4, 26.15, 9.9, 1.4, 'blk', ('ribs', 0.5, 0.4, 'yz'))
    p += rear(-1.75, 12.4, 13.45)
    p += slant(0.85, 6.6, 5.1, 2.0, 1.8, -16, 'od', ('check', 0.8))
    p += guard(1.8, 5.6, 5.9, 6.6)
    p += chain([(8.1, 6.7), (8.25, 2.0), (8.59, -3.23)], 3.2, 1.9, 'od', ('windows', [(1.0, 3.6), (-2.4, 0.2)]), ('magribs',))
    p += barrel(-12.6, -3.8, 10.4, 1.0, 'blk')                                                                                      # stock tube
    p += B(-12.8, 6.0, -9.0, 6.7, 1.4, 'blk', rot=('z', 12, (-9.0, 6.7, 8)))
    p += B(-12.9, 6.0, -12.2, 11.0, 1.4, 'blk')
    p += B(-13.55, 5.8, -12.8, 11.4, 2.0, 'rubber', ('grooves', 0.5, 0.4))
    p += B(-4.6, 9.4, -3.8, 11.4, 1.6, 'blk')                                                                                        # hinge
    return p


def c32():
    """C-32, 9x21 compact SMG: short ribbed shroud, long straight magazine, collapsed stock."""
    p = []
    p += B(0.6, 6.6, 12.4, 11.6, 2.2, 'blk', ('panel', 0.45), ('text', 'C-32', 1.6, 8.0), ('port', 5.6, 8.0, 9.9, 11.0))
    p += rail(1.0, 12.2, 11.6, 12.4)
    p += B(12.4, 7.6, 18.6, 11.6, 2.2, 'steel', ('ribs', 0.55, 0.45, 'yz'))
    p += post(17.9, 11.6, 13.45)
    p += barrel(18.6, 20.5, 9.9, 1.0, 'steel')
    p += barrel(20.5, 21.6, 9.9, 1.4, 'blk', ('knurl',))
    p += rear(0.7 + 1.0, 12.4, 13.45)
    p += slant(2.3, 6.6, 5.0, 2.0, 1.8, -14, 'blk', ('stipple',))
    p += guard(3.3, 6.0, 5.9, 6.6)
    p += B(6.3, 5.6, 9.7, 6.7, 2.3, 'blk')
    p += B(6.4, -2.9, 9.6, 5.7, 1.7, 'steel', ('grooves', 1.2, 0.18), ('magribs',))
    p += B(6.2, -3.3, 9.8, -2.9, 2.0, 'blk')
    p += BZ(-1.2, 10.1, 7.1, 0.6, 10.7, 7.5, 'steel') + BZ(-1.2, 10.1, 8.5, 0.6, 10.7, 8.9, 'steel')                             # collapsed stock rods
    p += B(-2.0, 6.4, -1.2, 11.2, 2.0, 'rubber', ('grooves', 0.5, 0.4))
    return p


def smg_suppressed():
    """Suppressed SMG (sketch with the long wrapped can): integral suppressor, long curved magazine, wire stock."""
    p = []
    p += B(-0.6, 7.0, 10.6, 11.4, 2.2, 'steel', ('panel', 0.45), ('text', 'S-9', 0.6, 8.2), ('port', 5.2, 8.0, 9.8, 10.9))
    p += rail(-0.4, 10.4, 11.4, 12.4)
    p += barrel(10.6, 23.6, 9.9, 2.6, 'blk', ('zig', 1.2))                                                                          # integral suppressor
    p += barrel(22.9, 23.6, 9.9, 2.8, 'steel')
    p += post(16.5, 11.2, 13.45)
    p += rear(0.7 + 0.5, 12.4, 13.45)
    p += slant(2.3, 7.0, 5.2, 2.0, 1.8, -14, 'blk', ('grooves', 0.6, 0.35))
    p += guard(3.4, 6.6, 6.3, 7.0)
    p += chain([(8.4, 7.1), (8.6, 2.5), (9.33, -2.36)], 3.0, 1.7, 'steel', ('zig', 1.0), ('magribs',))
    # wire stock with a hook
    p += BZ(-9.4, 10.4, 7.0, -0.6, 10.9, 7.45, 'steel') + BZ(-9.4, 10.4, 8.55, -0.6, 10.9, 9.0, 'steel')
    p += BZ(-9.4, 7.6, 7.0, -0.6, 8.1, 7.45, 'steel', rot=('z', 6, (-0.6, 8.1, 7.2))) + BZ(-9.4, 7.6, 8.55, -0.6, 8.1, 9.0, 'steel', rot=('z', 6, (-0.6, 8.1, 8.8)))
    p += B(-10.05, 6.4, -9.4, 11.6, 2.0, 'steel')
    p += B(-10.05, 11.6, -9.0, 12.2, 0.8, 'steel')
    return p


def mp_small():
    """Small SMG (sketch: magazine through the grip, cord-wrapped stock)."""
    p = []
    p += B(-3.0, 5.0, 11.0, 9.6, 2.0, 'steel', ('panel', 0.4), ('text', 'MP-9', -1.8, 6.4), ('port', 4.0, 6.6, 7.8, 9.0))
    p += rail(-2.6, 10.6, 9.6, 9.97, 1.3)
    p += B(8.0, 4.5, 11.0, 5.0, 1.8, 'steel')
    p += barrel(11.0, 13.4, 8.135, 0.9, 'steel')
    p += barrel(13.4, 14.7, 8.135, 1.3, 'blk', ('knurl',))
    p += B(11.0, 9.6, 11.4, 10.62, 0.35, 'blk') + B(-0.4, 9.97, 0.4, 10.62, 1.0, 'blk')                                         # sights
    p += B(-0.6, -1.2, 2.4, 5.0, 2.0, 'blk', ('stipple',))                                                                         # grip with magazine
    p += B(-0.2, -4.96, 2.0, -1.2, 1.6, 'steel', ('magribs',))
    p += B(-0.4, -5.3, 2.2, -4.9, 1.8, 'blk')
    p += guard(2.4, 5.6, 3.8, 5.0)
    p += BZ(-6.0, 8.6, 7.1, -3.0, 9.1, 7.5, 'cord', ('wrap', 0.6)) + BZ(-6.0, 8.6, 8.5, -3.0, 9.1, 8.9, 'cord', ('wrap', 0.6))
    p += BZ(-6.0, 5.6, 7.1, -3.0, 6.1, 7.5, 'cord', ('wrap', 0.6)) + BZ(-6.0, 5.6, 8.5, -3.0, 6.1, 8.9, 'cord', ('wrap', 0.6))
    p += B(-6.6, 5.0, -6.0, 9.6, 1.8, 'steel', ('grooves', 0.5, 0.4))
    return p


def m445():
    """M445 semi-automatic rifle: wooden stock and full-length fore-end, blued receiver, 10-round magazine, ported brake."""
    p = []
    p += B(-15.8, 4.8, -15.2, 10.8, 2.0, 'rubber', ('grooves', 0.5, 0.4))
    p += B(-15.2, 5.0, -9.6, 10.6, 1.9, 'wood', ('grain',))
    p += B(-9.8, 6.9, -3.4, 10.1, 1.7, 'wood', ('grain',))
    p += B(-12.0, 5.0, -6.0, 7.4, 1.7, 'wood', ('grain',), rot=('z', 16, (-6.0, 7.4, 8)))
    p += slant(-2.6, 7.2, 3.8, 2.2, 1.7, -20, 'wood', ('check', 0.7))
    p += B(-3.6, 6.4, 9.0, 9.2, 2.1, 'wood', ('grain',))
    p += B(-3.4, 9.2, 8.8, 11.6, 1.8, 'blue', ('panel', 0.4), ('text', 'M445', -2.4, 9.8), ('port', 2.2, 5.0, 10.2, 11.2))
    p += rail(-2.8, 8.2, 11.6, 12.24)
    p += B(9.0, 7.61, 20.4, 10.6, 2.0, 'wood', ('grain',))
    p += B(9.0, 10.6, 19.6, 11.4, 1.5, 'wood', ('grain',), ('vents', [(11.0, 12.6, 10.8, 11.2), (15.0, 16.6, 10.8, 11.2)]))
    p += B(13.0, 7.4, 13.6, 11.6, 2.2, 'blue') + B(19.6, 7.4, 20.4, 11.0, 2.2, 'blue')                                          # barrel bands
    p += barrel(20.4, 23.8, 9.833, 1.0, 'blue')
    p += post(22.9, 10.4, 13.257, mat='blue')
    p += rear(-1.911 - 1.5, 12.24, 13.257, 'blue')
    p += barrel(23.8, 26.35, 9.833, 1.6, 'blue', ('perf', 0.8, 1.0, 0.3, 10.2))                                                    # brake
    p += guard(-1.0, 2.6, 5.6, 6.4, 'blue')
    p += B(3.6, 0.38, 7.0, 6.5, 1.7, 'blue', ('grooves', 1.0, 0.18), ('magribs',))
    p += B(3.4, 0.2, 7.2, 0.6, 1.9, 'blue')
    p += B(15.0, 6.9, 15.6, 7.61, 0.3, 'lsteel')                                                                                    # sling swivel
    return p


def m77a():
    """M77A bolt-action rifle, 7.7x54: full wooden stock to near the muzzle, barrel bands, turned-down bolt handle."""
    p = []
    p += B(-15.41, 4.81, -15.0, 10.4, 2.0, 'steel')
    p += B(-15.0, 5.0, -8.0, 10.4, 1.9, 'wood', ('grain',))
    p += B(-8.2, 6.9, -3.0, 10.0, 1.7, 'wood', ('grain',))
    p += B(-12.4, 5.0, -6.2, 7.2, 1.7, 'wood', ('grain',), rot=('z', 14, (-6.2, 7.2, 8)))
    p += B(-3.0, 7.06, 21.0, 9.6, 1.9, 'wood', ('grain',))
    p += B(6.0, 9.6, 20.2, 10.6, 1.5, 'wood', ('grain',))
    p += barrel(-5.0, 5.2, 10.2, 2.0, 'blue', ('text', 'M77A', -3.6, 9.6))                                                          # receiver
    p += barrel(-6.2, -5.0, 10.2, 1.2, 'blue')                                                                                      # bolt sleeve
    p += BZ(-3.2, 9.6, 8.8, -2.6, 10.2, 10.4, 'blue') + BZ(-3.4, 8.6, 10.0, -2.4, 9.6, 11.0, 'lsteel')                              # bolt handle
    p += B(-4.2, 11.2, -2.8, 11.75, 1.2, 'blue') + B(3.0, 11.2, 4.4, 11.75, 1.2, 'blue')                                          # scope bases
    p += barrel(5.2, 24.8, 9.875, 1.0, 'blue')
    p += barrel(24.8, 25.94, 9.875, 1.2, 'blue')
    p += B(12.0, 6.9, 12.6, 10.8, 2.1, 'blue') + B(19.6, 6.9, 20.4, 10.8, 2.1, 'blue')
    p += B(6.6, 10.6, 8.0, 11.4, 1.2, 'blue') + B(6.8, 11.4, 7.8, 12.454, 0.6, 'blue')                                          # ladder rear sight
    p += post(24.3, 10.6, 12.454, 0.4, 'blue')
    p += barrel(21.0, 25.2, 8.4, 0.3, 'lsteel')                                                                                     # cleaning rod
    p += guard(-2.2, 0.6, 6.2, 6.9, 'blue')
    p += B(0.2, 6.5, 3.8, 7.06, 1.4, 'blue')
    return p


def amr():
    """Anti-materiel rifle (sketch: thumbhole stock, checkered vent panel, big brake, folded bipod, shell holder)."""
    p = []
    p += B(-15.8, 5.0, -15.2, 11.2, 2.0, 'rubber', ('grooves', 0.5, 0.4))
    p += B(-15.2, 5.2, -12.6, 11.0, 1.9, 'earth')
    p += B(-12.6, 9.6, -4.0, 11.0, 1.8, 'earth')
    p += B(-12.6, 5.6, -6.0, 7.0, 1.8, 'earth', rot=('z', 10, (-6.0, 7.0, 8)))
    p += slant(-4.8, 9.8, 6.0, 2.2, 1.8, -12, 'earth', ('check', 0.75))                                                           # thumbhole grip
    p += B(-12.2, 11.0, -6.0, 11.8, 1.4, 'earth')                                                                                  # cheek riser
    for i in range(4):                                                                                                             # spare rounds
        p += BZ(-13.4 + i * 0.9, 6.9, 9.0, -12.8 + i * 0.9, 9.3, 9.45, 'brass')
    p += B(-4.0, 8.0, 8.6, 11.6, 2.2, 'steel', ('panel', 0.45), ('text', 'AMR-14', -2.8, 8.6), ('port', 1.0, 4.4, 9.9, 11.1))
    p += rail(-3.8, 14.0, 11.6, 12.17)
    p += B(8.6, 7.62, 19.0, 11.6, 2.4, 'earth', ('checkbox', 9.6, 18.0, 8.6, 10.8))
    p += barrel(19.0, 23.4, 9.799, 1.4, 'steel', ('grooves', 0.35, 0.3))
    p += barrel(23.4, 26.45, 9.799, 2.3, 'steel', ('vents', [(23.8, 24.6, 9.2, 10.4), (25.0, 25.8, 9.2, 10.4)]))
    p += post(18.4, 11.6, 13.161)
    p += rear(-2.809, 12.17, 13.161)
    p += B(17.6, 6.8, 18.8, 7.62, 1.6, 'steel')
    p += BZ(18.0, 6.9, 7.0, 24.0, 7.4, 7.4, 'steel') + BZ(18.0, 6.9, 8.6, 24.0, 7.4, 9.0, 'steel')                                # folded bipod
    p += guard(-2.6, 0.6, 7.3, 8.0)
    p += B(4.4, 1.37, 7.2, 8.0, 1.9, 'steel', ('grooves', 1.4, 0.18), ('magribs',))
    return p


def wrk4():
    """WRK-4 general-purpose MG, 8x50 (D107): box receiver, perforated full-length jacket, cone flash hider, ammo box."""
    p = []
    p += B(-6.0, 6.6, 7.0, 11.6, 2.4, 'steel', ('panel', 0.5), ('rivets', [(-5.0, 7.2), (6.0, 7.2), (-5.0, 11.0), (6.0, 11.0)]), ('text', 'WRK-4', -4.0, 8.4))
    p += B(-4.8, 11.6, 6.4, 12.33, 2.2, 'steel', ('ribs', 1.6, 0.12, 'y'))                                                          # feed cover
    p += B(4.6, 12.33, 5.2, 13.8, 1.2, 'steel')                                                                                     # flip-up rear sight
    p += B(-6.0, 10.4, 2.0, 11.4, 0.4, 'steel', z=6.6)                                                                              # feed tray (left)
    p += BZ(4.0, 8.6, 9.2, 5.4, 9.4, 9.9, 'lsteel')                                                                                 # charging handle
    p += barrel(7.0, 22.6, 9.871, 2.2, 'steel', ('perf', 0.9, 0.9, 0.3, 9.4))                                                       # perforated jacket
    p += post(21.3, 10.9, 13.365, mat='steel')
    p += barrel(22.6, 24.4, 9.871, 1.4, 'steel')
    p += barrel(24.4, 26.238, 9.871, 1.9, 'blk', ('ribs', 0.6, 0.4, 'yz'))                                                         # cone hider
    p += BZ(7.6, 9.6, 6.1, 11.6, 10.6, 6.7, 'blk')                                                                                  # carrying handle
    p += BZ(19.6, 8.4, 7.0, 20.6, 8.9, 9.0, 'steel') + BZ(13.0, 8.3, 7.0, 20.0, 8.8, 7.4, 'steel') + BZ(13.0, 8.3, 8.6, 20.0, 8.8, 9.0, 'steel')   # bipod
    p += slant(-2.116, 6.6, 5.0, 2.0, 1.8, -15, 'blk', ('grooves', 0.6, 0.35))
    p += guard(-1.2, 2.6, 5.9, 6.6)
    p += B(1.0, 0.81, 7.2, 6.6, 2.6, 'od', ('panel', 0.35), ('text', 'D107', 2.0, 3.0, (220, 200, 120)))                         # ammo box
    p += B(3.0, 6.6, 5.2, 6.9, 0.6, 'steel')
    p += B(-14.8, 7.4, -6.0, 11.0, 1.9, 'blk', rot=('z', -3, (-6.0, 11.0, 8)))
    p += B(-15.8, 6.2, -14.8, 11.6, 2.0, 'rubber', ('grooves', 0.5, 0.4))
    return p


def hm145():
    """HM14.5/1930 heavy MG, 14.5x130.5: radiator fins, long barrel, slotted brake, box receiver, spade grips, 60-round box."""
    p = []
    p += B(-6.0, 5.4, 2.0, 11.4, 3.0, 'green', ('panel', 0.5), ('rivets', [(-5.0, 6.0), (1.0, 6.0), (-5.0, 10.8), (1.0, 10.8)]), ('text', 'HM14.5', -4.8, 7.6))
    p += B(-5.6, 11.4, 1.6, 12.4, 2.6, 'green', ('ribs', 1.2, 0.12, 'y'))
    p += BZ(-1.0, 8.0, 9.5, 0.6, 8.6, 10.4, 'lsteel')
    p += barrel(2.0, 6.6, 8.6, 1.8, 'steel')
    for i in range(6):                                                                                                             # radiator discs
        x = 2.4 + i * 0.75
        p += barrel(x, x + 0.4, 8.6, 3.6, 'steel')
    p += barrel(6.6, 21.6, 8.6, 1.2, 'steel')
    p += barrel(21.6, 23.55, 8.6, 2.0, 'steel', ('vents', [(21.9, 22.5, 8.0, 9.2), (22.8, 23.3, 8.0, 9.2)]))
    p += post(19.6, 9.2, 14.0, 0.5, 'steel')
    # carry handle with the long-range sight on top (scopes sit here)
    p += B(-5.0, 12.4, -4.4, 15.6, 0.8, 'steel') + B(0.4, 12.4, 1.0, 15.6, 0.8, 'steel') + B(-5.2, 15.6, 1.2, 16.4, 1.0, 'steel')
    p += B(-4.2, 12.4, -3.4, 14.0, 1.2, 'steel')                                                                                    # rear sight
    # back plate and spade grips with the butterfly trigger
    p += B(-6.8, 5.6, -6.0, 11.2, 2.6, 'green')
    p += BZ(-8.45, 5.2, 5.6, -7.0, 10.4, 6.6, 'rubber', ('grooves', 0.6, 0.35)) + BZ(-8.45, 5.2, 9.4, -7.0, 10.4, 10.4, 'rubber', ('grooves', 0.6, 0.35))
    p += BZ(-7.0, 9.6, 5.6, -6.8, 10.4, 10.4, 'steel') + BZ(-7.0, 5.2, 5.6, -6.8, 6.0, 10.4, 'steel')
    p += B(-7.6, 7.4, -7.0, 8.4, 1.6, 'lsteel')
    p += B(-4.0, 1.0, 1.0, 5.4, 3.0, 'od', ('panel', 0.35), ('text', '60', -3.0, 3.6, (220, 200, 120)))                         # 60-round box
    p += B(-2.6, 0.6, -0.4, 1.0, 1.0, 'steel')
    p += B(1.0, 4.6, 2.4, 5.4, 1.6, 'steel')                                                                                        # tripod pivot
    p += BZ(2.0, 5.0, 6.6, 14.0, 5.6, 7.2, 'green') + BZ(2.0, 5.0, 8.8, 14.0, 5.6, 9.4, 'green')                                  # folded tripod legs
    return p


GUNS = {
    'assault_rifle': lt70, 'battle_rifle': lt77, 'burst_carbine': c36x, 'compact_smg': c32, 'smg': smg_suppressed,
    'machine_pistol': mp_small, 'designated_marksman_rifle': m445, 'bolt_rifle': m77a, 'sniper_rifle': amr,
    'light_machine_gun': wrk4, 'rotary_cannon': hm145,
}


# ---- painting --------------------------------------------------------------------------------------------------

FONT = {
    'A': ['010', '101', '111', '101', '101'], 'C': ['011', '100', '100', '100', '011'], 'D': ['110', '101', '101', '101', '110'],
    'H': ['101', '101', '111', '101', '101'], 'K': ['101', '110', '100', '110', '101'], 'L': ['100', '100', '100', '100', '111'],
    'M': ['101', '111', '111', '101', '101'], 'P': ['110', '101', '110', '100', '100'], 'R': ['110', '101', '110', '101', '101'],
    'S': ['011', '100', '010', '001', '110'], 'T': ['111', '010', '010', '010', '010'], 'W': ['101', '101', '111', '111', '101'],
    'X': ['101', '101', '010', '101', '101'], '-': ['000', '000', '111', '000', '000'], '.': ['000', '000', '000', '000', '010'],
    '0': ['111', '101', '101', '101', '111'], '1': ['010', '110', '010', '010', '111'], '2': ['110', '001', '010', '100', '111'],
    '3': ['110', '001', '010', '001', '110'], '4': ['101', '101', '111', '001', '001'], '5': ['111', '100', '110', '001', '110'],
    '6': ['011', '100', '110', '101', '010'], '7': ['111', '001', '010', '010', '010'], '8': ['010', '101', '010', '101', '010'],
    '9': ['010', '101', '011', '001', '110'], '/': ['001', '001', '010', '100', '100'],
}


def face_coords(b, face):
    (x0, y0, z0), (x1, y1, z1) = b.f, b.t
    if face in ('north', 'south'): w, h = x1 - x0, y1 - y0
    elif face in ('east', 'west'): w, h = z1 - z0, y1 - y0
    else: w, h = x1 - x0, z1 - z0
    pw, ph = max(1, round(w * PX)), max(1, round(h * PX))
    U, V = np.meshgrid((np.arange(pw) + 0.5) / pw, (np.arange(ph) + 0.5) / ph)
    if face == 'north': X, Y, Z = x1 - U * w, y1 - V * h, np.full(U.shape, z0)
    elif face == 'south': X, Y, Z = x0 + U * w, y1 - V * h, np.full(U.shape, z1)
    elif face == 'east': X, Y, Z = np.full(U.shape, x1), y1 - V * h, z1 - U * (z1 - z0)
    elif face == 'west': X, Y, Z = np.full(U.shape, x0), y1 - V * h, z0 + U * (z1 - z0)
    elif face == 'up': X, Y, Z = x0 + U * w, np.full(U.shape, y1), z0 + V * (z1 - z0)
    else: X, Y, Z = x0 + U * w, np.full(U.shape, y0), z1 - V * (z1 - z0)
    return pw, ph, X, Y, Z


def paint(b, face):
    pw, ph, X, Y, Z = face_coords(b, face)
    base, noise = MAT[b.mat]
    img = np.zeros((ph, pw, 3)) + base + rng.normal(0, noise, (ph, pw, 1))
    side = face in ('north', 'south')
    along = face in ('north', 'south', 'up', 'down')            # faces where X varies
    for ft in b.feats:
        k = ft[0]
        if k == 'grain':
            g = np.sin(Y * 7.0 + Z * 3.0 + 1.6 * np.sin(X * 0.55) + rng.normal(0, 0.08, Y.shape))
            img *= (1 + 0.09 * g)[..., None]
            img[(np.abs(g) > 0.96)] *= 0.86
        elif k == 'ribs' and along:
            period, duty = ft[1], ft[2]
            x0 = ft[4] if len(ft) > 4 else b.f[0]
            ph_ = ((X - x0) / period) % 1.0
            m = ph_ < duty
            if len(ft) > 3 and ft[3] == 'y' and side:
                m = m & (Y > (ft[4] if len(ft) > 4 else -99))
            img[m] *= 0.55
            img[(ph_ >= duty) & (ph_ < duty + 0.12)] *= 1.18
        elif k == 'grooves':
            period, duty = ft[1], ft[2]
            if side: q = Y
            elif face in ('up', 'down'): q = Z
            elif face in ('east', 'west'): q = Y
            else: continue
            ph_ = (q / period) % 1.0
            img[ph_ < duty] *= 0.62
        elif k == 'perf':
            px_, py_, r = ft[1], ft[2], ft[3]
            if side: q = Y
            elif face in ('up', 'down'): q = Z
            else: continue
            dx = ((X - b.f[0]) % px_) - px_ / 2
            dy = ((q - (ft[4] if len(ft) > 4 else 0)) % py_) - py_ / 2
            if py_ > 5: dy = q - ft[4]                               # a single row
            hole = dx * dx + dy * dy < r * r
            img[hole] = C(16, 16, 18)
            rim = (dx * dx + dy * dy < (r + 0.2) ** 2) & ~hole & (dy < 0)
            img[rim] *= 1.25
        elif k == 'vents' and side:
            for xa, xb, ya, yb in ft[1]:
                m = (X >= xa) & (X <= xb) & (Y >= ya) & (Y <= yb)
                img[m] = C(18, 18, 20)
                img[m & (Y > yb - 0.25)] = C(10, 10, 12)
        elif k == 'check':
            s = ft[1]
            a = ((X + Y) / s) % 1.0 if side else ((Z + Y) / s) % 1.0 if face in ('east', 'west') else ((X + Z) / s) % 1.0
            c = ((X - Y) / s) % 1.0 if side else ((Z - Y) / s) % 1.0 if face in ('east', 'west') else ((X - Z) / s) % 1.0
            img[(a < 0.22) | (c < 0.22)] *= 0.62
        elif k == 'checkbox' and side:
            xa, xb, ya, yb = ft[1:5]
            m = (X >= xa) & (X <= xb) & (Y >= ya) & (Y <= yb)
            a = ((X + Y) / 0.6) % 1.0; c = ((X - Y) / 0.6) % 1.0
            img[m] *= 0.8
            img[m & ((a < 0.3) | (c < 0.3))] = C(30, 28, 26)
        elif k == 'stipple':
            m = rng.random(img.shape[:2]) < 0.28
            img[m] *= 0.72
        elif k == 'knurl':
            a = ((X + Y + Z) / 0.35) % 1.0
            img[a < 0.4] *= 0.65
        elif k == 'zig':
            s = ft[1]
            q = Y if side else Z if face in ('up', 'down') else Y
            tri = np.abs(((X / s) % 1.0) - 0.5) * 2
            m = np.abs(((q / s) % 1.0) - tri) < 0.18
            img[m] *= 0.5
            img[~m] *= 1.08
        elif k == 'wrap':
            s = ft[1]
            a = ((X + Y + Z * 0.7) / s) % 1.0
            img[a < 0.3] *= 0.7
        elif k == 'magribs' and side:
            cx = (b.f[0] + b.t[0]) / 2
            img[np.abs(X - cx) < 0.18] *= 1.22                     # the centre rib pressed into the sides
        elif k == 'windows' and side:
            cx = (b.f[0] + b.t[0]) / 2
            for ya, yb in ft[1]:
                m = (np.abs(X - cx) < 0.45) & (Y >= ya) & (Y <= yb)
                img[m] = MAT['brass'][0] * 0.85                    # cartridges showing through
                img[m & (np.abs(X - cx) > 0.32)] = C(20, 20, 22)
        elif k == 'panel' and side:
            d = ft[1]
            (x0, y0, _), (x1, y1, _) = b.f, b.t
            m = ((np.abs(X - (x0 + d)) < 0.13) | (np.abs(X - (x1 - d)) < 0.13)) & (Y > y0 + d - 0.13) & (Y < y1 - d + 0.13)
            m |= ((np.abs(Y - (y0 + d)) < 0.13) | (np.abs(Y - (y1 - d)) < 0.13)) & (X > x0 + d - 0.13) & (X < x1 - d + 0.13)
            img[m] *= 0.72
        elif k == 'port' and face == 'south':
            xa, xb, ya, yb = ft[1:5]
            m = (X >= xa) & (X <= xb) & (Y >= ya) & (Y <= yb)
            img[m] = C(20, 20, 22)
            img[m & (X > xa + 0.5) & (X < xb - 0.5) & (Y > ya + 0.3) & (Y < yb - 0.3)] = C(150, 150, 154)   # the bolt face
        elif k == 'rivets' and side:
            for rx, ry in ft[1]:
                m = (np.abs(X - rx) < 0.2) & (np.abs(Y - ry) < 0.2)
                img[m] = np.minimum(base * 1.6 + 30, 255)
    # bevel: light from above
    if pw >= 3 and ph >= 3:
        img[0, :] *= 1.14; img[-1, :] *= 0.8; img[:, 0] *= 1.04; img[:, -1] *= 0.9
    # stamped markings, readable on both sides
    for ft in b.feats:
        if ft[0] == 'text' and side:
            text, tx, ty = ft[1], ft[2], ft[3]
            col = C(*ft[4]) if len(ft) > 4 else np.minimum(base * 1.9 + 40, 235)
            stamp(img, X, Y, face, text, tx, ty, col)
    return np.clip(img, 0, 255)


def stamp(img, X, Y, face, text, tx, ty, col):
    ph, pw = img.shape[:2]
    # pixel of the model point (tx, ty + 1.25) on this face (text written left to right as seen)
    xs = X[0]; ys = Y[:, 0]
    if face == 'south': px0 = int(np.argmin(np.abs(xs - tx)))
    else: px0 = int(np.argmin(np.abs(xs - (tx + len(text) * 4 / PX))))       # mirrored: start further along x
    py0 = int(np.argmin(np.abs(ys - (ty + 5 / PX))))
    for i, ch in enumerate(text):
        g = FONT.get(ch)
        if g is None: continue
        for r, row in enumerate(g):
            for c_, bit in enumerate(row):
                if bit == '1':
                    yy, xx = py0 + r, px0 + i * 4 + c_
                    if 0 <= yy < ph and 0 <= xx < pw: img[yy, xx] = col


# ---- packing and writing ---------------------------------------------------------------------------------------

def build(name, parts, display, extra):
    faces = []
    for bi, b in enumerate(parts):
        for face in ('north', 'south', 'east', 'west', 'up', 'down'):
            faces.append((bi, face, paint(b, face)))
    order = sorted(range(len(faces)), key=lambda i: -faces[i][2].shape[0])
    x = y = rowh = 0
    place = {}
    for i in order:
        h, w = faces[i][2].shape[:2]
        if x + w > W: x, y, rowh = 0, y + rowh, 0
        place[i] = (x, y)
        x += w; rowh = max(rowh, h)
    H = 64
    while H < y + rowh: H *= 2
    atlas = np.zeros((H, W, 4), np.uint8)
    elements = [{'from': [round(v, 4) for v in b.f], 'to': [round(v, 4) for v in b.t], 'faces': {}} for b in parts]
    for i, (bi, face, img) in enumerate(faces):
        px, py = place[i]
        h, w = img.shape[:2]
        atlas[py:py + h, px:px + w, :3] = img.astype(np.uint8)
        atlas[py:py + h, px:px + w, 3] = 255
        elements[bi]['faces'][face] = {'uv': [round(16 * px / W, 4), round(16 * py / H, 4), round(16 * (px + w) / W, 4), round(16 * (py + h) / H, 4)], 'texture': '#0'}
    for b, e in zip(parts, elements):
        if b.rot: e['rotation'] = {'angle': b.rot[1], 'axis': b.rot[0], 'origin': [round(v, 4) for v in b.rot[2]]}
    os.makedirs(f'{OUT}/textures/item/gun', exist_ok=True)
    Image.fromarray(atlas).save(f'{OUT}/textures/item/gun/{name}.png')
    model = dict(extra)
    model['textures'] = {'0': f'heliosphere:item/gun/{name}', 'particle': f'heliosphere:item/gun/{name}'}
    model['elements'] = elements
    model['display'] = display
    os.makedirs(f'{OUT}/models/item/armory', exist_ok=True)
    json.dump(model, open(f'{OUT}/models/item/armory/{name}.json', 'w'), indent=1)
    icon(name, parts)
    for b in parts:                                                  # stay inside what a model may hold
        assert all(-16 <= v <= 32 for v in b.f + b.t), (name, b.f, b.t)
    return H


def icon(name, parts, size=64, ss=3):
    """The inventory picture: the gun's right side, outlined."""
    xs = [v for b in parts if b.icon for v in (b.f[0], b.t[0])]
    ys = [v for b in parts if b.icon for v in (b.f[1], b.t[1])]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    s = (size - 4) / max(x1 - x0, y1 - y0)
    N = size * ss
    img = np.zeros((N, N, 3)); a = np.zeros((N, N)); depth = np.full((N, N), -1e9)
    gx, gy = np.meshgrid(np.arange(N), np.arange(N))
    X = x0 + ((gx + 0.5) / ss - 2 - (size - 4 - (x1 - x0) * s) / 2) / s
    Y = y1 - ((gy + 0.5) / ss - 2 - (size - 4 - (y1 - y0) * s) / 2) / s
    for b in parts:
        if not b.icon: continue
        lx, ly = X, Y
        if b.rot and b.rot[0] == 'z':
            ang = math.radians(-b.rot[1]); ox, oy = b.rot[2][0], b.rot[2][1]
            lx = ox + (X - ox) * math.cos(ang) - (Y - oy) * math.sin(ang)
            ly = oy + (X - ox) * math.sin(ang) + (Y - oy) * math.cos(ang)
        inside = (lx >= b.f[0]) & (lx <= b.t[0]) & (ly >= b.f[1]) & (ly <= b.t[1]) & (b.t[2] > depth)
        if not inside.any(): continue
        tex = paint(b, 'south')
        th, tw = tex.shape[:2]
        u = np.clip(((lx - b.f[0]) / max(1e-6, b.t[0] - b.f[0]) * tw).astype(int), 0, tw - 1)
        v = np.clip(((b.t[1] - ly) / max(1e-6, b.t[1] - b.f[1]) * th).astype(int), 0, th - 1)
        img[inside] = tex[v[inside], u[inside]]
        a[inside] = 1; depth[inside] = b.t[2]
    small = img.reshape(size, ss, size, ss, 3).mean(axis=(1, 3))
    alpha = a.reshape(size, ss, size, ss).mean(axis=(1, 3))
    out = np.zeros((size, size, 4))
    out[..., :3] = small; out[..., 3] = (alpha > 0.4) * 255
    solid = out[..., 3] > 0
    edge = np.zeros_like(solid)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(solid, dy, 0), dx, 1) & ~solid
    out[edge] = (24, 24, 26, 255)
    os.makedirs(f'{OUT}/textures/item', exist_ok=True)
    Image.fromarray(out.astype(np.uint8)).save(f'{OUT}/textures/item/{name}.png')


def released_models():
    jar = glob.glob('base/06-*.jar')[0]
    z = zipfile.ZipFile(jar)
    return {n: json.loads(z.read(f'assets/heliosphere/models/item/armory/{n}.json')) for n in GUNS}


def lang():
    names = {
        'assault_rifle': ('LT-70突击步枪', 'LT-70突擊步槍', 'LT-70 Assault Rifle'),
        'battle_rifle': ('LT-77步枪', 'LT-77步槍', 'LT-77 Rifle'),
        'burst_carbine': ('C-36X卡宾枪', 'C-36X卡賓槍', 'C-36X Carbine'),
        'compact_smg': ('C-32冲锋枪', 'C-32衝鋒槍', 'C-32 Submachine Gun'),
        'designated_marksman_rifle': ('M445半自动步枪', 'M445半自動步槍', 'M445 Semi-Automatic Rifle'),
        'bolt_rifle': ('M77A栓动步枪', 'M77A栓動步槍', 'M77A Bolt-Action Rifle'),
        'light_machine_gun': ('WRK-4通用机枪', 'WRK-4通用機槍', 'WRK-4 General-Purpose Machine Gun'),
        'rotary_cannon': ('HM14.5/1930重机枪', 'HM14.5/1930重機槍', 'HM14.5/1930 Heavy Machine Gun'),
    }
    for i, code in enumerate(('zh_cn', 'zh_tw', 'en_us')):
        path = f'{OUT}/lang/{code}.json'
        have = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
        for k, v in names.items(): have[f'item.heliosphere.{k}'] = v[i]
        json.dump(have, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)


if __name__ == '__main__':
    rel = released_models()
    only = sys.argv[1:] or list(GUNS)
    for n in only:
        m = rel[n]
        extra = {k: v for k, v in m.items() if k not in ('textures', 'elements', 'display')}
        H = build(n, GUNS[n](), m['display'], extra)
        print(f'{n}: {len(GUNS[n]())} parts, atlas {W}x{H}')
    lang()
