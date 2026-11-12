---
name: procedural-texture
description: 程序化材质/贴图绘制。用户要"画材质""做贴图""生成纹理""PBR 贴图""无缝贴图""Minecraft 方块/物品贴图""像素风材质"时使用。用 Python（numpy + Pillow）生成可平铺的 albedo/normal/roughness/height 贴图，或 16x16/32x32 像素风贴图，不依赖 Substance、Blender 等桌面软件，云端会话里可直接运行。
---

# 程序化材质绘制

用代码生成材质贴图。所有输出默认**无缝平铺**（左右、上下边缘可以接上）。

## 快速开始

脚本在本技能目录下：`scripts/gen_texture.py`（只依赖 numpy 和 Pillow）。

```bash
# PBR 一整套贴图（albedo / normal / roughness / height）
python3 .claude/skills/procedural-texture/scripts/gen_texture.py \
    --material stone --size 1024 --seed 7 --out textures/stone

# 像素风（Minecraft 风格 16x16，会额外输出一张 8 倍放大的预览图）
python3 .claude/skills/procedural-texture/scripts/gen_texture.py \
    --material metal --mode pixel --size 16 --out textures/metal_px

# 自定义颜色：--colors 从暗到亮给 2~5 个十六进制色
python3 .claude/skills/procedural-texture/scripts/gen_texture.py \
    --material rock --colors "#2b1d14,#6b4a2f,#b98b5a" --out textures/mars_rock
```

内置材质：`stone` `rock` `metal` `rust` `wood` `sand` `brick` `ice` `lava` `grass`。
跑 `--help` 可以看到全部参数。

## 工作流程

1. **先确认用途**：给 three.js / 游戏引擎的 PBR，还是 Minecraft 那种像素贴图？要多大（PBR 常用 512/1024/2048，像素风 16 或 32）？
2. **选最接近的内置材质**，用 `--colors` 调色，用 `--seed` 换花纹，用 `--scale` 调花纹疏密（数字越大越细碎）。
3. **生成后一定要看图**：用 Read 工具打开生成的 PNG 检查效果；像素风看 `*_preview.png`。可以加 `--tile-preview` 输出 2x2 平铺图，检查接缝。
4. 效果不满意时，改参数重跑；内置材质都不合适时，直接改脚本里的 `MATERIALS` 字典，或仿照现有函数写一个新材质。
5. 把贴图放到项目里实际用的目录，并告诉用户怎么接入，例如：
   - three.js：`map` 用 albedo（`colorSpace = THREE.SRGBColorSpace`），`normalMap`、`roughnessMap` 保持线性色彩空间；平铺时设 `wrapS = wrapT = THREE.RepeatWrapping`。
   - Minecraft 模组：贴图放在 `assets/<modid>/textures/block|item/`，16x16，PNG。

## 原理（自己扩展时参考）

- 无缝的关键：噪声的格点按周期取模（`periodic value noise`），所以贴图边缘天然相接。
- height（高度图）是一切的源头：normal 由 height 求梯度得到，roughness 和 albedo 也是由 height 加颜色映射得到。
- 像素风：先在高分辨率算出 height，再缩小到 16x16，把颜色吸附到 4~6 级的明暗色阶，并做左上方光照，这样才像手绘像素，不会糊成一片。

## 注意

- Claude 不能像 AI 绘图模型那样画出照片级写实材质；这里是程序化生成，适合石头、金属、木纹、砖、地面、科幻面板等有规律的材质。
- 仓库里如果已经有自己的贴图生成脚本（例如 `tools/gen/paint.py`），优先沿用项目自己的风格和工具，本技能当参考。
