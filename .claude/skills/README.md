# Claude Code 技能（Skills）

这个目录里的技能会在 Claude Code 打开本仓库时自动加载，包括网页版 claude.ai/code 的云端会话。

| 技能 | 用途 | 来源 |
|---|---|---|
| `procedural-texture` | 材质绘制：生成无缝 PBR 贴图（albedo/normal/roughness/height）或 16x16/32x32 像素风贴图 | 自写，numpy + Pillow |
| `remotion-best-practices` | 视频制作：用 Remotion（React）写视频、加字幕、渲染 MP4 | [remotion-dev/skills](https://github.com/remotion-dev/skills)（Remotion 官方） |
| `slack-gif-creator` | 动画制作：用 Python 逐帧画动画并导出 GIF（不只限 Slack 用） | [anthropics/skills](https://github.com/anthropics/skills)，Apache-2.0 |
| `algorithmic-art` | 动画/生成艺术：p5.js 粒子、流场等可交互动画 | [anthropics/skills](https://github.com/anthropics/skills)，Apache-2.0 |

> 本仓库已有自己的贴图工具 `tools/gen/paint.py` 和片头视频脚本 `tools/media/intro.py`，技能会优先沿用它们的风格。

## 怎么调用

**自动调用**：直接用中文描述需求，Claude 会根据技能的 description 自己判断要不要用。例如：
- 「帮我生成一套 1024 的锈蚀金属 PBR 贴图，放到 textures/ 下」
- 「做一个 16x16 的火星岩石方块贴图」
- 「用 Remotion 做一个 15 秒的产品介绍视频，导出 MP4」
- 「做一个旋转行星的循环 GIF 动画」

**手动调用**：输入斜杠加技能名，后面接需求：
```
/procedural-texture 生成一张像素风的冰块贴图
/remotion-best-practices 做一个标题动画视频
/slack-gif-creator 做一个火箭发射的 GIF
```

在会话里输入 `/` 可以看到当前可用的技能列表。

## 云端会话要准备的环境

在 claude.ai/code 的环境设置（Environment）里：
- **网络访问**：Remotion 需要 `npm install` 和下载无头浏览器，网络访问级别不能设成"无"。
- **Setup script**（可选，能省掉每次安装的时间）：
  ```bash
  pip install --break-system-packages numpy pillow imageio
  ```
