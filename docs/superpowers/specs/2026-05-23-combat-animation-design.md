# 战斗动画系统设计

## 设计方向

参考 Into the Breach：信息优先、极简精确、网格对齐。所有动画严格限制在格子/战场边界内，不超出。

## 架构

- **引擎（前端）**：实现有限数量的动画原语播放器
- **Mod（数据）**：在技能 YAML 中组合原语定义动画序列
- **后端 JS handler**：结算时根据技能定义，把动画序列塞进 combat event 传给前端

前端拿到事件后按序列逐条播放原语，每个原语有 duration 和可选 delay。

## 数据格式

### 技能定义动画序列（Mod YAML）

```yaml
id: fire_bolt
name: 烈焰箭
animation:
  - type: projectile
    from: actor
    to: target
    shape: circle
    trail: glow
    speed: fast
    color: "#ff4400"
  - type: impact
    target: target
    color: "#ff4400"
  - type: shake
    target: target
    intensity: normal
  - type: damage_number
    target: target
    color: damage
```

### Combat Event 中的动画数据（后端→前端）

```json
{
  "segments": [...],
  "effects": [...],
  "animation": [
    { "type": "projectile", "from": "player1", "to": "goblin_0", "shape": "circle", "color": "#ff4400", "speed": "fast", "trail": "glow" },
    { "type": "impact", "target": "goblin_0", "color": "#ff4400" },
    { "type": "shake", "target": "goblin_0", "intensity": "normal" },
    { "type": "damage_number", "target": "goblin_0", "value": -12, "color": "damage" }
  ]
}
```

## 动画原语完整列表

### 发起类

| 原语 | 参数 | 视觉效果 |
|------|------|----------|
| pulse | actor, color | 攻击者格子扩散方框波纹（波纹不超出格子） |
| flash_sequence | path[], color | 沿路径逐格闪白，光传递暗示方向 |
| projectile | actor, target, shape, trail, speed, color, size | 投射物从施法者格子中心飞到目标格子中心 |
| beam | actor, target, color, width | 光线从源中心延伸到目标中心，瞬间到达 |
| slash | target(s), color, arc, direction | 月牙弧形斩击，在目标格子内展开 |

### 命中类

| 原语 | 参数 | 视觉效果 |
|------|------|----------|
| impact | target, color(default: white) | 目标格短暂全白闪 100ms |
| shake | target, intensity(light/normal/heavy) | 目标 token 水平抖动 2-3 次 200ms |
| damage_number | target, value, color(damage/heal/miss) | 大号数字弹出，带黑色描边，停留后淡出 400ms |

### 状态类

| 原语 | 参数 | 视觉效果 |
|------|------|----------|
| buff_up | target/field, color, intensity(normal/big) | 绿色双三角上升（单体在格子内，全场在3x3内） |
| debuff_down | target/field, color, intensity(normal/big) | 红色双倒三角下沉（单体在格子内，全场在3x3内） |

### 结果类

| 原语 | 参数 | 视觉效果 |
|------|------|----------|
| mark_dead | target | X 标记叠加 + token 变暗淡 |
| indicator_add | target, corner, color | 格子角标色块亮起 |

## 原语详细规格

### projectile 投射物

从施法者格子正中心飞向目标格子正中心。

**shape 可选值：**
- `circle` — 实心圆弹丸 + 光晕拖尾
- `diamond` — 旋转菱形 + 发光
- `crescent` — 月牙形投射物，弧口朝飞行方向，随源→目标角度旋转

**trail 可选值：** none / fade / glow

**speed 可选值：** fast(100ms) / normal(200ms) / slow(400ms)

**size 可选值：** small / medium / large

**color：** hex 色值或语义色名（player/enemy/damage/heal）

### slash 月牙斩

弧形斩击在目标格子内展开：
- 端点从格子左上角和左下角出发
- 弧边最远触及格子右边框，不超出
- AOE 版本跨多格但不超出列宽
- direction 控制朝向，可随攻击角度旋转

### buff_up / debuff_down

**单体（target 指定单个实体）：**
- 两个三角上下紧贴叠放（快进符号样式）
- 三角底边宽度 = 格子边长
- 整组从格子底边出发向上飘出（buff）/ 从顶边向下沉（debuff）
- 用 overflow:hidden 确保不超出格子
- intensity=big 时加第三个三角

**全场（target = "field"）：**
- 3-4 个大三角，大小不一（适当重叠），分散在 3x3 网格内
- 从底部升起到顶部消失，时间错开
- 用 overflow:hidden 确保不超出 3x3 网格边界

## 命中时序规则

投射物/beam/slash 到达目标中心的那一帧：
1. 投射物消失
2. 同帧触发 impact（白闪）
3. 同帧触发 shake（颤抖）
4. 略延后触发 damage_number（数字弹出）

**时序图：**
```
飞行 (0~400ms) → 到达 → impact(100ms) + shake(200ms) + damage_number(400ms)
```

受击反馈严格在投射物到达后才开始，飞行期间目标静止不动。

## 格子四角指示系统

格子四角可放置色块三角指示器，由 mod 定义：
- 用 clip-path 三角形实现
- 四个位置：top-left / top-right / bottom-left / bottom-right
- 颜色 = 语义（绿=增益，红=减益，紫=特殊，橙=标记等）
- 多 buff 叠加时占用不同角位

```yaml
indicators:
  - corner: top-left
    color: "#66bb6a"
  - corner: bottom-left
    color: "#e57373"
```

## 约束规则

1. **所有动画不超出格子边界**（单体动画用 overflow:hidden）
2. **全场动画不超出 3x3 网格边界**
3. **投射物起点 = 源格子中心，终点 = 目标格子中心**
4. **月牙斩弧边不超出目标格子右边框**
5. **无 emoji**，纯几何色块 + SVG
6. **边框用粗线**（3-4px），避免细边框
7. **选中目标：4px 粗橙色边框 + glow**

## 攻击场景支持

系统支持以下攻击模式，通过原语组合实现：

- **单体同行**：投射物水平直线飞行
- **单体跨行**：投射物沿斜线飞行，月牙随角度旋转
- **多目标**：多个投射物分裂飞向各目标 / 一次 pulse 多个命中
- **直线 AOE（整排）**：宽 beam 贯穿 / 逐格扫过 / 大月牙覆盖
- **顺序攻击（前→中→后排）**：同排内同时命中，排间 300ms 间隔

## 实现技术

- 纯 CSS 动画 + SVG，无额外依赖
- Vue 3 组件，通过 props 接收动画序列
- 动画播放器按序列逐条执行，支持 delay 字段错开
- 格子使用 position:relative + overflow:hidden 约束动画边界
