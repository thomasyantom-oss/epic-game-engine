# 配色与字体改动 Spec
**日期：** 2026-05-28
**范围：** 颜色系统重构 + 战斗按钮样式 + 单位 token 阵营染色
**约束：** 禁止改动任何布局、面板结构、尺寸、位置

---

## 1. 颜色系统

### 1.1 基色方案

采用「One Dark」基色，更新 `mods/base-rules/colors.yaml`：

```yaml
colors:
  # 基础文字
  text: "#c8d0dc"          # 普通文字（无特殊含义的文字全用此色）

  # 能量条
  hp: "#e84848"
  mp: "#2eb8cc"

  # 阵营
  player: "#66cc55"        # 我方（绿）
  enemy:  "#e84848"        # 敌方（红）
  neutral: "#ffcc33"       # 中立（黄）
  ally:   "#55aaff"        # 友军（蓝）

  # 语义色
  damage:    "#ffcc33"     # 伤害数字 / 重要事件
  highlight: "#55aaff"     # 链接 / 可交互元素
  mana:      "#2eb8cc"     # 法力相关提示文字

  # 稀有度
  rarity_common:    "#c8d0dc"
  rarity_uncommon:  "#55dd55"
  rarity_rare:      "#55aaff"
  rarity_epic:      "#cc66ff"
  rarity_legendary: "#ffcc33"

  # 元素属性
  element_fire: "#ff7733"
  element_ice:  "#66ddff"
  element_lightning: "#ffee44"

  # 面板底色（供前端 CSS 变量参考，非 colorMap 直接用）
  bg_dark:   "#0d1318"
  bg_panel:  "#141c24"
  border:    "#222c38"
```

### 1.2 前端 CSS 变量规则

- `SnapshotRenderer` watch `snapshot.colors`，动态写入 CSS 变量（现有机制，继续沿用）
- 变量命名：`--color-<key>`，例如 `--color-text`、`--color-hp`、`--color-player`
- 前端组件**禁止**出现任何颜色字面量，全部用 `var(--color-xxx)`

### 1.3 文字颜色规则

| 元素 | 颜色变量 |
|------|----------|
| 普通文字（描述、标签） | `--color-text` |
| 属性数值（攻击/防御/速度等） | `--color-text` |
| 等级数字 | `--color-text` |
| 能量条右侧数字 | 跟条颜色（HP → `--color-hp`，MP → `--color-mp`） |
| 我方角色名 | `--color-player` |
| 敌方角色名 | `--color-enemy` |
| 伤害数字 | `--color-damage` |
| 链接 / 可交互 | `--color-highlight` |
| 稀有度文字 | `--color-rarity_<tier>` |
| 元素属性 | `--color-element_<type>` |

---

## 2. 战斗按钮样式

### 2.1 统一按钮组件

现有的 `ActionLink`（▸前缀链接样式）在战斗指令区改为「暗刻按钮」样式。非战斗区域的 ActionLink 不变。

**默认状态：**
```css
background: rgba(255,255,255,0.04);
border: 2px solid var(--color-border);
color: var(--color-text);
box-shadow: inset 0 2px 6px rgba(0,0,0,0.6), inset 0 -1px 0 rgba(255,255,255,0.05);
padding: 5px 10px;
border-radius: 3px;
```

**hover 状态：**
```css
border-color: var(--color-highlight);
background: color-mix(in srgb, var(--color-highlight) 12%, transparent);
color: var(--color-highlight);
```

**激活状态（已选中技能）：**
```css
border-color: var(--color-highlight);
background: color-mix(in srgb, var(--color-highlight) 18%, transparent);
color: var(--color-highlight);
```

### 2.2 去掉 ▸ 前缀

战斗指令区按钮不显示 ▸，按钮样式本身已传达可交互语义。

### 2.3 技能描述移入 tooltip

- 技能按钮上只显示技能名
- hover 时展示 tooltip：`{名称, MP消耗, 描述, 目标范围}`
- tooltip 内容来源：skill YAML 的 `description` 字段（现有字段）
- 动态计算值（倍率 × 属性）列入 BL-001 backlog，本期不做

### 2.4 适用范围

| 位置 | 改动 |
|------|------|
| 战斗指令区（攻击/防御/逃跑/技能） | 改为暗刻按钮 |
| 技能展开列表（各技能名） | 改为暗刻按钮 |
| 非战斗区域 ActionLink | 不变，保持 ▸ 链接样式 |

---

## 3. 战斗单位 Token 阵营染色

### 3.1 染色规则

单位 token 的 border 和 background 由阵营决定，颜色从 colorMap 读取：

| 阵营 | border | background |
|------|--------|------------|
| player | `--color-player` | `color-mix(player, transparent 75%)` |
| enemy | `--color-enemy` | `color-mix(enemy, transparent 75%)` |
| neutral | `--color-neutral` | `color-mix(neutral, transparent 75%)` |
| ally | `--color-ally` | `color-mix(ally, transparent 75%)` |

实现上可用 `rgba` 手动调透明度，或 CSS `color-mix()`。

### 3.2 不改动的内容

- 格子/地块的背景色和文字（地形色不变）
- AOE 范围标识（黄框/红框/红背景染色）
- 目标选中高亮（橙框 + glow）
- buff/debuff 三角角标

单位层与格子层完全独立，token 样式不影响格子的任何标识。

---

## 4. 改动文件清单

| 文件 | 改动 |
|------|------|
| `mods/base-rules/colors.yaml` | 更新全部色值，新增 text/阵营/稀有度/元素变量 |
| `frontend/src/components/SnapshotRenderer.vue` | 确认 CSS 变量映射覆盖新 key |
| `frontend/src/components/combat/BattleGrid.vue` | token 阵营染色；去掉格子颜色 hardcode |
| `frontend/src/components/CharacterStatsTab.vue` | 文字颜色走变量；能量条数字跟条色 |
| `frontend/src/components/EquipmentBagPanel.vue` | 稀有度颜色走变量 |
| `frontend/src/styles/base.css` 或全局样式 | 去掉可能残留的颜色 hardcode |
| 战斗指令相关组件（ActionPanel / BattleGrid） | 暗刻按钮样式；去掉 ▸；技能描述改 tooltip |

---

## 5. 不在本期范围内

- 布局、面板结构、尺寸变更（任何 layout 改动）
- 技能 tooltip 动态计算值（→ BL-001）
- 头像替换（→ 未来）
- 新增阵营类型（架构已支持，内容待 mod 定义）
