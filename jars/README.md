# jars/：按发布 jar 分类的叠加模块

发布出去的 12 个 jar（`base/01-*.jar` … `base/12-*.jar`）比仓库根目录下的旧源码新，
所以新功能按 jar 分类写在这里：每个子目录对应一个发布 jar，只放**改动和新增**的类与资源。

| 目录 | 对应 jar | 内容 |
|---|---|---|
| `01-core/` | 01 基础核心 | 特效引擎 `core/client/fx`、特效模型 `core/fx/sim`（蘑菇云、小行星撞击、各类爆炸与射击特效）、方块模型工具 `BoxMesh` |
| `02-materials/` | 02 材料 | 重绘的矿石贴图（磷灰石、绿柱石、萤石、石墨、硫磺）与油砂（只有资源） |
| `05-industry/` | 05 工业 | 重绘的 17 种矿石贴图（只有资源） |
| `06-weapons/` | 06 武器 | 核爆重写（`Nuke`）、射击/爆炸特效（`fx/`、`fxmixin/`）、7 种防御塔与 5 级升级套件（`defense/mk/`）、制导导弹锁定与导引头（`guided/`） |
| `07-hostiles/` | 07 科技敌人 | 敌人名单与技术等级（`arsenal/EnemyRoster`）、武器与 Boss（`EnemyArsenal`）、导弹破坏方块（`Missiles`、`MachineBlast`）、机械堡垒与机器人制造站（`fortress/`）、避难所围攻（`siege/`）、末日教团停用（`CultRetiredMixin`）、机器人渲染器、部署器图标 |
| `09-rocketry/` | 09 火箭星门 | 右键直接登舱并暂停倒计时、座舱内鼠标自由环视（`cabin/`） |
| `10-disasters/` | 10 自然灾害 | 全球熔岩蔓延与小行星撞击（`tide/`）、气态巨行星闪电与气旋（`giant/`） |
| `11-guide/` | 11 卷轴手册 | 敌对生物图鉴、工业设施/矿石/原料说明（`purposes.json`）、附录章节、Boss 分页 |
| `12-sphereworld/` | 12 星球地形 | 气态/冰巨行星 4064 格深的分层大气与金属氢/离子海洋、深度温度（`giant/`）、重绘的星球地表与云层贴图 |
| `testrun/` | — | 游戏内自动测试（不发布）：`-Ponly=guns|bestiary|enemies|world:fortress,siege,icons,rocket,giant,neptune` |

## 构建

```bash
tools/fetch_base.sh                 # 下载发布 jar 到 base/（只需一次）
gradle mergeJar                     # 所有叠加模块
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

## 资源生成脚本

```bash
python3 tools/gen/robot_kit.py          # 机器人细节贴图集 tech_kit.png
python3 tools/gen/vanguard_skins.py     # 外星先遣队贴图（颜色取自 base/07 原贴图）
python3 tools/gen/hostiles_content.py   # 图鉴、卷轴提示、语言、新部署器模型
python3 tools/gen/nuke_assets.py        # 核爆相关方块、辐射图标、盖革计数器音效
python3 tools/gen/turret_assets.py      # 防御塔与升级套件
python3 tools/gen/fortress_assets.py    # 机器人制造站
python3 tools/gen/ore_textures.py       # 矿石、油砂、星球矿床贴图
python3 tools/gen/planet_textures.py    # 星球地表贴图
python3 tools/gen/giant_assets.py       # 巨行星云层、金属氢与离子海洋
python3 tools/gen/guide_industry.py     # 卷轴：工业设施、矿石与原料说明
python3 tools/gen/deployer_icons.py jars/testrun/run-fx/screenshots   # 部署器图标（先跑 -Ponly=world:icons）
```
