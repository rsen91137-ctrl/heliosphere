# jars/：按发布 jar 分类的叠加模块

发布出去的 12 个 jar（`base/01-*.jar` … `base/12-*.jar`）比仓库根目录下的旧源码新，
所以新功能按 jar 分类写在这里：每个子目录对应一个发布 jar，只放**改动和新增**的类与资源。

| 目录 | 对应 jar | 内容 |
|---|---|---|
| `01-core/` | 01 基础核心 | 特效引擎 `core/client/fx`、特效模型 `core/fx/sim`（蘑菇云、小行星撞击、各类爆炸与射击特效） |
| `06-weapons/` | 06 武器 | 核爆重写（`Nuke`、`nuke/`）、射击/爆炸特效收发（`fx/`）、枪械平衡与特效挂钩（`fxmixin/`） |
| `10-disasters/` | 10 自然灾害 | 全球熔岩蔓延（`tide/MagmaTide`）、小行星撞击特效收发（`tide/ImpactFx*`） |
| `testrun/` | — | 游戏内自动测试：开枪、核爆、小行星撞击并截图（不发布） |

## 构建

```bash
tools/fetch_base.sh                 # 下载发布 jar 到 base/（只需一次）
gradle :jars:01-core:mergeJar :jars:06-weapons:mergeJar :jars:10-disasters:mergeJar
```

产物在 `build/dist-overlay/`，文件名和原 jar 相同，直接替换 mods 文件夹里的同名 jar 即可。

`mergeJar` 的规则（见 `overlay.gradle`）：
- 同名类、贴图、模型等：叠加模块的版本覆盖原 jar。
- `lang/*.json`、`sounds.json`：逐条合并，叠加模块只需写新增的条目。
- `fabric.mod.json`：保留原 jar 的，只把叠加模块里写的 `mixins` 和 `entrypoints` 追加进去。

## 预览与测试

```bash
tools/fxpreview/run.sh nuke out.png          # 离线渲染蘑菇云各时刻（不需要游戏）
tools/fxpreview/run.sh impact out.png        # 离线渲染小行星撞击
gradle :jars:testrun:prodFxTest              # 游戏内测试（正式版环境，需要 xvfb-run），截图在 jars/testrun/run-fx/screenshots/
```
