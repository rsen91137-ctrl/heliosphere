#!/usr/bin/env python3
"""Creative deployer icons from in-game photographs (overlay 07-hostiles). The test run (WorldGameTest, part
"icons") photographs every machine against a white and then a black backdrop; the two shots give each pixel's
colour and transparency (difference matting). The machine is cropped, scaled to 32x32 and written as the
deployer's item texture with a plain generated item model, so each deployer shows its own machine.
Usage: tools/gen/deployer_icons.py <screenshot dir>"""
import glob, json, os, re, sys
import numpy as np
from PIL import Image

A = 'jars/07-hostiles/src/main/resources/assets/heliosphere'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def matte(white, black):
    w = white.astype(float) / 255; b = black.astype(float) / 255
    alpha = 1 - (w - b).mean(axis=2)
    alpha = np.clip((alpha - 0.08) / 0.84, 0, 1)
    col = np.where(alpha[..., None] > 0.02, b / np.maximum(alpha[..., None], 0.02), 0)
    return np.clip(col, 0, 1), alpha


def components(m):
    """Labels 4-connected regions of a boolean mask (iterative flood fill)."""
    lab = np.zeros(m.shape, np.int32); n = 0
    H, W = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if lab[y0, x0]: continue
        n += 1; stack = [(y0, x0)]; lab[y0, x0] = n
        while stack:
            y, x = stack.pop()
            for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= yy < H and 0 <= xx < W and m[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n; stack.append((yy, xx))
    return lab, n


def icon(white, black, size=32):
    col, a = matte(white, black)
    solid = a > 0.8
    # the machine is the big connected shape near the middle; stray particles and the backdrop seam are not
    lab, n = components(solid)
    if n == 0: return None
    sizes = np.bincount(lab.ravel())[1:]
    H, W = a.shape
    best = max(range(1, n + 1), key=lambda k: sizes[k - 1] / (1 + np.hypot(*(np.mean(np.nonzero(lab == k), axis=1) - (H / 2, W / 2))) / 200))
    keep = np.zeros_like(solid)
    by, bx = np.nonzero(lab == best)
    y0b, y1b, x0b, x1b = by.min(), by.max(), bx.min(), bx.max()
    pad = max(y1b - y0b, x1b - x0b) // 8
    for k in range(1, n + 1):
        if sizes[k - 1] < sizes[best - 1] * 0.1 and k != best: continue
        ky, kx = np.nonzero(lab == k)
        if ky.min() > y1b + pad or ky.max() < y0b - pad or kx.min() > x1b + pad or kx.max() < x0b - pad: continue
        if (ky.max() - ky.min()) < 6 and (kx.max() - kx.min()) > 4 * (ky.max() - ky.min() + 1) and k != best: continue   # thin seams
        keep |= lab == k
    a = np.where(keep, a, 0)
    col = np.clip(col ** 0.8 * 1.1, 0, 1)
    ys, xs = np.nonzero(a > 0.35)
    if len(ys) == 0: return None
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    side = int(max(y1 - y0, x1 - x0) * 1.06) + 2
    cy, cx = (y0 + y1) // 2, (x0 + x1) // 2
    rgba = np.zeros((side, side, 4))
    sy, sx = cy - side // 2, cx - side // 2
    for yy in range(side):
        ry = sy + yy
        if not 0 <= ry < a.shape[0]: continue
        lo, hi = max(0, sx), min(a.shape[1], sx + side)
        rgba[yy, lo - sx:hi - sx, :3] = col[ry, lo:hi]
        rgba[yy, lo - sx:hi - sx, 3] = a[ry, lo:hi]
    # premultiplied downscale keeps edges clean
    pm = rgba.copy(); pm[..., :3] *= pm[..., 3:4]
    img = Image.fromarray((pm * 255).astype(np.uint8), 'RGBA').resize((size, size), Image.BOX)
    out = np.asarray(img).astype(float) / 255
    al = out[..., 3:4]
    out[..., :3] = np.where(al > 0.01, out[..., :3] / np.maximum(al, 0.01), 0)
    out[..., 3] = (out[..., 3] > 0.4).astype(float)          # crisp item edges
    # a dark outline makes small machines readable in the inventory
    m = out[..., 3] > 0
    ring = np.zeros_like(m)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(np.roll(m, dy, 0), dx, 1)
    ring &= ~m
    out[ring] = (0.08, 0.08, 0.1, 1)
    return (out * 255).astype(np.uint8)


def main(shots):
    pairs = {}
    for f in glob.glob(os.path.join(shots, '*_icon_*_[wb].png')):
        m = re.match(r'\d+_icon_(.+)_([wb])\.png', os.path.basename(f))
        if m: pairs.setdefault(m.group(1), {})[m.group(2)] = f
    done = []
    for rid, p in sorted(pairs.items()):
        if 'w' not in p or 'b' not in p: continue
        w = np.asarray(Image.open(p['w']).convert('RGB')); b = np.asarray(Image.open(p['b']).convert('RGB'))
        img = icon(w, b)
        if img is None: print('empty', rid); continue
        os.makedirs(f'{A}/textures/item', exist_ok=True)
        Image.fromarray(img, 'RGBA').save(f'{A}/textures/item/{rid}_deployment.png')
        write(f'{A}/models/item/{rid}_deployment.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heliosphere:item/{rid}_deployment'}})
        write(f'{A}/items/{rid}_deployment.json', {'model': {'type': 'minecraft:model', 'model': f'heliosphere:item/{rid}_deployment'}})
        done.append(rid)
    # contact sheet for checking
    if done:
        sheet = Image.new('RGBA', (len(done) * 72, 72), (139, 139, 139, 255))
        for i, rid in enumerate(done):
            t = Image.open(f'{A}/textures/item/{rid}_deployment.png').resize((64, 64), Image.NEAREST)
            sheet.paste(t, (i * 72 + 4, 4), t)
        sheet.save('build/deployer_icons.png')
    print(len(done), 'icons:', ' '.join(done))


if __name__ == '__main__':
    main(sys.argv[1])
