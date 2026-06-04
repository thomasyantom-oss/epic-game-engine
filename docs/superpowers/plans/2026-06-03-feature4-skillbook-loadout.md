# Feature #4 — Skillbook 出战配置 + 指令栏外壳 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: 逐 task TDD(先写失败测试→跑红→最小实现→跑绿→commit),频繁提交。后端用 JUnit(`cd backend && mvn test`);前端本仓无测试框架,按"实现 + 手动走查"做,步骤里写清验证动作。完成全部 task 后发 `requesting-code-review`,等 Claude APPROVE 才 merge。

**Goal:** 把技能从"扁平已知列表"升级成 Skillbook 实例模型(`base/node/equipped` + 出战格),战斗指令栏只放出战技能,新增模态技能书配出战 + 右下角常驻菜单入口。

**Architecture:** 后端 `Skills` 组件改 `{slots, known:[{base,node,equipped}]}`;通用技能(攻击/防御/逃跑)移出 known、由 `actions.js` 在战斗中永远发;新增 equip/unequip action + snapshot `skillbook` 块(走引擎现成的"JS handler 填类型化 record"管道)。前端加可复用模态外壳 + 技能书主动 tab,重排 BattleGrid 指令栏为固定 6 格 + 技能子菜单。

**Tech Stack:** Java 21 / Spring Boot 3 / GraalJS(mod handlers)/ JUnit + AssertJ / Vue 3(无 TS)。

**关键参考:**
- 数据模型 / 范围 / 一致性自查:`docs/superpowers/specs/2026-06-03-feature4-skillbook-loadout-design.md`(spec,务必先读)。
- 架构决策(必须遵守):`docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md` §1.4/1.5/1.2。
- 测试 harness 范本:`backend/.../ui/UiHandlersTest.java`(handler 级,轻量)、`backend/.../character/CharacterFlowTest.java`(REST 级,SpringBootTest)。
- 引擎暴露给 JS 的工厂:`backend/.../script/ScriptRuntime.java`(`newStatusBar` / `newActionOptionStyled` / `newMap` / `newList` 等;本计划新增 `newSkillEntry`)。

---

## File Structure(改动地图)

**后端 / mod:**
- `mods/base-rules/handlers/character/select.js` — Skills 改新模型(known 不含通用技能)。
- `mods/base-rules/handlers/ui/actions.js` — 战斗指令:永远发通用技能 + 只发出战技能。
- `mods/base-rules/handlers/ui/skillbook.js`(新)— `ui.render_skillbook` 填 skillbook 块。
- `mods/base-rules/handlers/skill/03_skillbook.js`(新)— `action.skillbook_equip` / `action.skillbook_unequip` handler。
- `backend/.../script/ScriptRuntime.java` — 加 `newSkillEntry(...)` 工厂。
- `backend/.../snapshot/WorldSnapshot.java` — 加 `SkillEntry` record + `skillbook` 字段 + `inGame(...)` 形参。
- `backend/.../snapshot/SnapshotService.java` — fire `ui.render_skillbook`,塞进 `inGame`。

**前端:**
- `frontend/src/composables/useModal.js`(新)— 全局模态开关状态。
- `frontend/src/components/Modal.vue`(新)— 可复用模态外壳。
- `frontend/src/components/SkillbookPanel.vue`(新)— 技能书内容(主动 tab 全功能)。
- `frontend/src/components/SnapshotRenderer.vue` — panel-nav 改常驻菜单 + 挂模态。
- `frontend/src/components/combat/BattleGrid.vue` — command-row 重排。

**测试:**
- `backend/.../character/CharacterFlowTest.java` — 更新 `warriorGetsClassStartingSkills` / `mageGetsClassStartingSkills`(旧 `list` → 新 `known`)。
- `backend/.../ui/SkillbookActionsTest.java`(新)— actions 战斗输出 + equip/unequip 校验(handler 级)。
- `backend/.../snapshot/SkillbookSnapshotTest.java`(新)— snapshot skillbook 块(REST 级)。

---

## Task 1: Skills 新数据模型(select.js + 更新现有测试)

**Files:**
- Modify: `mods/base-rules/handlers/character/select.js`(当前 L108–129 构建 Skills 那段)
- Modify: `backend/src/test/java/com/epic/engine/character/CharacterFlowTest.java`(`warriorGetsClassStartingSkills`、`mageGetsClassStartingSkills`)

- [ ] **Step 1: 改两个现有测试,表达新模型(先让它们红)**

把 `CharacterFlowTest` 两个技能测试改成断言新结构:`Skills` 组件有 `slots`(=6)和 `known`(列表,每项含 `base`/`node`/`equipped`);通用技能**不在** known;职业技能在 known 且 `equipped==true`、`node==null`。

```java
@Test
@SuppressWarnings("unchecked")
void warriorSkillbook_hasClassSkills_excludesUniversal() {
    String token = sessionService.createSession();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Session-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);

    var confirmReq = Map.of("type", "confirm_character",
            "params", Map.of("name", "战士出战测试", "class", "warrior"));
    ResponseEntity<Map> resp = rest.exchange("/api/action", HttpMethod.POST,
            new HttpEntity<>(confirmReq, headers), Map.class);
    String playerId = (String) resp.getBody().get("playerId");

    var skills = entityStore.get(playerId).getComponent("Skillbook");
    assertThat(((Number) skills.get("slots")).intValue()).isEqualTo(6);

    List<Map<String,Object>> known = (List<Map<String,Object>>) skills.get("known");
    List<String> bases = known.stream().map(m -> String.valueOf(m.get("base"))).toList();
    assertThat(bases).contains("cleave", "war_cry");           // 职业 starting_skills 进 known
    assertThat(bases).doesNotContain("basic_attack", "defend", "flee"); // 通用技能移出 known
    for (Map<String,Object> k : known) {
        assertThat(k.get("equipped")).isEqualTo(Boolean.TRUE); // 起手全出战
        assertThat(k.get("node")).isNull();                    // 进化节点预留,本轮恒 null
    }
}
```

`mageGetsClassStartingSkills` 同样改:断言 `known` 的 `base` 含 `fireball`、`light_field`,不含通用技能。(删掉旧的 `warriorGetsClassStartingSkills`、`mageGetsClassStartingSkills` 旧体,换成新断言。)

- [ ] **Step 2: 跑测试确认红**

Run: `cd backend && mvn test -Dtest=CharacterFlowTest`
Expected: FAIL（`skills.get("slots")` / `get("known")` 为 null 或类型不符）

- [ ] **Step 3: 改 select.js 产出新模型**

替换 `select.js` 当前构建 Skills 的段(L108–129):

```js
// Skillbook（Feature #4）：known = 玩家拥有的主动技能实例 {base,node,equipped}
// 组件名 Skillbook(旧名 Skills,review 拍定改名);通用技能(basic_attack/defend/flee)不进 known —— 由 actions.js 在战斗中永远发
var skills = engine.newComponent("Skillbook");
var known = engine.newList();
if (classSchema !== null && classSchema.raw().get("starting_skills") !== null) {
    var startSkills = classSchema.raw().get("starting_skills");
    for (var sk = 0; sk < startSkills.size(); sk++) {
        var inst = engine.newMap();
        inst.put("base", String(startSkills.get(sk)));
        inst.put("node", null);          // 进化节点,Feature #7 用,本轮恒 null
        inst.put("equipped", true);      // 起手默认全出战(数量 ≤ slots)
        known.add(inst);
    }
}
// 出战格上限:读 class schema skill_slots,缺省 6(留好"职业可改"口子,本轮统一 6)
var slots = 6;
if (classSchema !== null && classSchema.raw().get("skill_slots") !== null) {
    slots = parseInt(String(classSchema.raw().get("skill_slots")));
}
skills.set("slots", slots);
skills.set("known", known);
entity.addComponent(skills);
```

- [ ] **Step 4: 跑测试确认绿**

Run: `cd backend && mvn test -Dtest=CharacterFlowTest`
Expected: PASS

- [ ] **Step 5: 跑全量,确认没连带打挂(actions.js 还读旧 list,下个 task 修)**

Run: `cd backend && mvn test`
Expected: 仅与"战斗中读 Skills.list"相关的测试可能红（若有）。记录红的测试名,Task 4 修复。其余绿。

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/handlers/character/select.js backend/src/test/java/com/epic/engine/character/CharacterFlowTest.java
git commit -m "feat(skillbook): Skills 升级为 base/node/equipped 实例模型 + slots(默认6)"
```

---

## Task 2: equip / unequip action handler

**Files:**
- Create: `mods/base-rules/handlers/skill/03_skillbook.js`
- Create: `backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java`

校验三道:① 满格(`equippedCount >= slots`)拒 equip;② 战斗中(实体有 `combat:` tag)拒 equip/unequip;③ `base` 不在 known 拒。改动后 `persistence.save`。

- [ ] **Step 1: 写失败测试(handler 级,仿 UiHandlersTest)**

新建 `SkillbookActionsTest`,setUp 仿 `UiHandlersTest`:手建 `EventBus`/`EntityStore`/`ScriptRuntime`,`runtime.execute` 加载 `handlers/skill/03_skillbook.js`;造一个 player 实体带 `Skillbook` 组件(`slots=2`,`known` = 两个 equipped 的 + 一个未 equipped 的),无 combat tag。注意:`persistence` 在该轻量 harness 不存在 —— handler 里 `persistence.save` 要在 `persistence` 为 undefined 时跳过(见 Step 3 实现的 typeof 守卫),否则测试报错。

> **失败语义 = 静默拒绝(review 拍定)**:校验不过只是不改状态、直接 return,**不** `event.set("error")`(该通道没接,见 spec §3.2)。测试一律断言**状态不变**。

```java
@Test
void unequip_thenEquip_togglesEquipped() {
    fireSkillbook("skillbook_unequip", "skA");
    assertThat(equippedBases()).doesNotContain("skA");
    fireSkillbook("skillbook_equip", "skA");
    assertThat(equippedBases()).contains("skA");
}

@Test
void equip_whenSlotsFull_rejected() {
    // slots=2,已 2 个 equipped;再 equip 第三个(skC,未出战)应被拒
    fireSkillbook("skillbook_equip", "skC");
    assertThat(equippedBases()).hasSize(2).doesNotContain("skC");
}

@Test
void equip_whenInCombat_rejected() {
    store.get("player1").addTag("combat:c1");
    fireSkillbook("skillbook_unequip", "skA");
    assertThat(equippedBases()).contains("skA"); // 战斗中不允许改
}

@Test
void equip_unknownBase_rejected() {
    fireSkillbook("skillbook_equip", "nonexistent");
    assertThat(equippedBases()).hasSize(2);
}
```

辅助:`fireSkillbook(type, base)` 构 `GameEvent("action." + type)`、set `entityId`=player1（或 `playerId`,与 handler 取参一致）、set `base`，`bus.fire`。`equippedBases()` 读 player1 `Skillbook.known` 里 equipped 的 base 列表。（harness 细节照 `UiHandlersTest` 抄。）

- [ ] **Step 2: 跑红**

Run: `cd backend && mvn test -Dtest=SkillbookActionsTest`
Expected: FAIL（handler 未加载,equipped 不变 / 事件无人处理）

- [ ] **Step 3: 实现 03_skillbook.js**

```js
// Feature #4：技能书出战配置 —— equip / unequip
// 校验:① 满格拒 equip ② 战斗中拒改 ③ base 不在 known 拒
function _skillbookEntity(event) {
    var id = event.get("entityId");
    if (id == null) id = event.get("playerId");
    return id != null ? store.get(id) : null;
}
function _inCombat(entity) {
    var tags = entity.getTags().toArray();
    for (var i = 0; i < tags.length; i++) {
        if (tags[i].toString().indexOf("combat:") === 0) return true;
    }
    return false;
}
function _setEquipped(event, wantEquipped) {
    var entity = _skillbookEntity(event);
    if (entity === null || !entity.hasComponent("Skillbook")) return;
    if (_inCombat(entity)) return;                          // 静默拒绝:战斗中不改

    var skills = entity.getComponent("Skillbook");
    var known = skills.get("known");
    var slots = skills.has("slots") ? skills.getInt("slots") : 6;
    var base = event.get("base");

    var target = null, equippedCount = 0;
    for (var i = 0; i < known.size(); i++) {
        var k = known.get(i);
        if (k.get("equipped") === true) equippedCount++;
        if (String(k.get("base")) === String(base)) target = k;
    }
    if (target === null) return;                           // 静默拒绝:未拥有

    if (wantEquipped) {
        if (target.get("equipped") === true) return;       // 已出战,无操作
        if (equippedCount >= slots) return;                // 静默拒绝:满格
        target.put("equipped", true);
    } else {
        target.put("equipped", false);
    }
    if (typeof persistence !== "undefined" && persistence !== null) persistence.save(entity);
}

engine.on("action.skillbook_equip",   100, function(event) { _setEquipped(event, true);  });
engine.on("action.skillbook_unequip", 100, function(event) { _setEquipped(event, false); });
```

> 注:`known.get(i)` 是 Java Map(HashMap),用 `.get`/`.put` 读写;`String(...)` 归一化比较。

- [ ] **Step 4: 跑绿**

Run: `cd backend && mvn test -Dtest=SkillbookActionsTest`
Expected: PASS（4 个测试全过）

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/skill/03_skillbook.js backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java
git commit -m "feat(skillbook): equip/unequip action + 满格/战斗中/未拥有 三道校验"
```

---

## Task 3: snapshot `skillbook` 块

**Files:**
- Modify: `backend/.../snapshot/WorldSnapshot.java`(加 `SkillEntry` record + `skillbook` 字段 + `inGame` 形参)
- Modify: `backend/.../script/ScriptRuntime.java`(加 `newSkillEntry` 工厂)
- Create: `mods/base-rules/handlers/ui/skillbook.js`（`ui.render_skillbook` handler）
- Modify: `backend/.../snapshot/SnapshotService.java`（fire 事件 + 传入 inGame）
- Create: `backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java`

数据走引擎现成管道(同 `bars`/`actions`):SnapshotService fire `ui.render_skillbook` 带空 list → JS handler 用 `engine.newSkillEntry(...)` 填 → 塞进 `WorldSnapshot.inGame`。

- [ ] **Step 1: 写失败测试(REST 级,仿 CharacterFlowTest)**

```java
@Test
@SuppressWarnings("unchecked")
void snapshot_includesSkillbook() {
    String token = sessionService.createSession();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Session-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);

    rest.exchange("/api/action", HttpMethod.POST, new HttpEntity<>(Map.of(
            "type","confirm_character","params",Map.of("name","快照技能测试","class","mage")), headers), Map.class);

    ResponseEntity<Map> snap = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    Map<String,Object> skillbook = (Map<String,Object>) snap.getBody().get("skillbook");
    assertThat(skillbook).isNotNull();
    assertThat(((Number) skillbook.get("slots")).intValue()).isEqualTo(6);
    List<Map<String,Object>> known = (List<Map<String,Object>>) skillbook.get("known");
    List<String> bases = known.stream().map(m -> String.valueOf(m.get("base"))).toList();
    assertThat(bases).contains("fireball");
    Map<String,Object> fb = known.stream().filter(m -> "fireball".equals(m.get("base"))).findFirst().orElseThrow();
    assertThat(fb.get("name")).isEqualTo("火球");      // 从 skills/fireball.yaml 读
    assertThat(fb.get("equipped")).isEqualTo(Boolean.TRUE);
    assertThat((String) fb.get("icon")).isNotBlank();  // 占位:技能名首字
}
```

- [ ] **Step 2: 跑红**

Run: `cd backend && mvn test -Dtest=SkillbookSnapshotTest`
Expected: FAIL（snapshot 无 `skillbook` 键）

- [ ] **Step 3a: WorldSnapshot 加 record + 字段**

在 `WorldSnapshot` 加:
```java
public record SkillEntry(String base, String name, String description, String icon, boolean equipped, String node) {}
public record Skillbook(int slots, int equippedCount, List<SkillEntry> known) {}
```
给 `inGame(...)` 工厂(及对应 `WorldSnapshot` 字段/构造)加一个 `Skillbook skillbook` 形参,塞进返回的快照对象。（按该文件现有 record 字段顺序追加;序列化为 JSON `skillbook`。）

- [ ] **Step 3b: ScriptRuntime 加工厂**

仿 `newStatusBar`(L156 附近):
```java
public WorldSnapshot.SkillEntry newSkillEntry(String base, String name, String description, String icon, boolean equipped, String node) {
    return new WorldSnapshot.SkillEntry(base, name, description, icon, equipped, node);
}
```

- [ ] **Step 3c: ui/skillbook.js handler**

```js
engine.on("ui.render_skillbook", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null || !entity.hasComponent("Skillbook")) return;

    var skills = entity.getComponent("Skillbook");
    var known = skills.get("known");
    var out = event.get("known");            // 空 list,SnapshotService 传入
    if (known === null) return;

    for (var i = 0; i < known.size(); i++) {
        var k = known.get(i);
        var base = String(k.get("base"));
        var def = engine.loadYaml("skills/" + base + ".yaml");
        if (def === null) continue;
        var name = def.get("name") !== null ? String(def.get("name")) : base;
        var description = def.get("description") !== null ? String(def.get("description")) : "";
        var icon = def.get("icon") !== null ? String(def.get("icon")) : name.substring(0, 1); // 占位:首字
        var node = k.get("node") !== null ? String(k.get("node")) : null;
        var equipped = k.get("equipped") === true;
        out.add(engine.newSkillEntry(base, name, description, icon, equipped, node));
    }
    event.set("slots", skills.has("slots") ? skills.getInt("slots") : 6);
});
```

- [ ] **Step 3d: SnapshotService 接线**

在 `buildInGameSnapshot`(L61–98)加,fire 事件并组装 Skillbook,传给 `inGame`(只对玩家有 `Skillbook` 组件时给值,否则 null):
```java
WorldSnapshot.Skillbook skillbook = buildSkillbook(playerId);
// ...inGame(...) 末尾加 skillbook 实参
```
新增方法:
```java
private WorldSnapshot.Skillbook buildSkillbook(String playerId) {
    Entity player = entityStore.get(playerId);
    if (player == null || !player.hasComponent("Skillbook")) return null;
    GameEvent ev = new GameEvent("ui.render_skillbook");
    ev.set("entityId", playerId);
    ev.set("known", new ArrayList<WorldSnapshot.SkillEntry>());
    ev.set("slots", 6);
    eventBus.fire("ui.render_skillbook", ev);
    @SuppressWarnings("unchecked")
    List<WorldSnapshot.SkillEntry> known = ev.get("known");
    int slots = ev.get("slots") != null ? ((Number) ev.get("slots")).intValue() : 6;
    int equipped = (int) known.stream().filter(WorldSnapshot.SkillEntry::equipped).count();
    return new WorldSnapshot.Skillbook(slots, equipped, known);
}
```

- [ ] **Step 4: 跑绿 + 全量**

Run: `cd backend && mvn test -Dtest=SkillbookSnapshotTest` → PASS
Run: `cd backend && mvn test` → 全绿(若 WorldSnapshot 构造变更牵动其他 inGame 调用处,一并修)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java backend/src/main/java/com/epic/engine/script/ScriptRuntime.java mods/base-rules/handlers/ui/skillbook.js backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java
git commit -m "feat(skillbook): snapshot 增 skillbook 块(slots/known/equipped,名描标从 yaml)"
```

---

## Task 4: 战斗指令 actions.js 改写

**Files:**
- Modify: `mods/base-rules/handlers/ui/actions.js`（战斗分支 L18–71）
- Test: `backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java`（加战斗输出测试)

战斗中:① 永远发 `basic_attack`/`defend`/`flee`(不依赖 known);② 只发 `known` 里 `equipped` 的技能,category 标 `skill`;③ 移动/道具**不由后端发**(前端静态禁用占位,Task 8)。

- [ ] **Step 1: 加失败测试(handler 级)**

在 `SkillbookActionsTest` 加(setUp 需另加载 `handlers/ui/actions.js`,并给 player 加 `combat:c1` tag + 一个未 equipped 的技能):

```java
@Test
@SuppressWarnings("unchecked")
void combatActions_universalAlways_onlyEquippedSkills() {
    Entity p = store.get("player1");
    p.addTag("combat:c1");                       // 进入战斗
    // known: skA(equipped,真技能如 fireball), skC(未 equipped)
    GameEvent ev = new GameEvent("ui.render_actions");
    ev.set("entityId", "player1");
    ev.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
    bus.fire("ui.render_actions", ev);

    List<WorldSnapshot.ActionOption> actions = ev.get("actions");
    List<String> commands = actions.stream()
            .map(a -> String.valueOf(a.params().get("command"))).toList();
    assertThat(commands).contains("basic_attack", "defend", "flee"); // 通用永远在
    assertThat(commands).contains("fireball");      // equipped 技能在(用真实 equipped base)
    assertThat(commands).doesNotContain("skC");     // 未 equipped 不出
}
```
> 注:测试里 known 的 equipped base 用真实存在 yaml 的技能(如 `fireball`),否则 `loadYaml` 在 actions 里取不到 name 会 skip。harness 里 `engine.loadYaml` 走 mod 目录,确保 ScriptRuntime 能解析路径(参考其他加载 skills yaml 的测试,如 `SkillCombatIntegrationTest` 的 setUp)。

- [ ] **Step 2: 跑红**

Run: `cd backend && mvn test -Dtest=SkillbookActionsTest#combatActions_universalAlways_onlyEquippedSkills`
Expected: FAIL

- [ ] **Step 3: 改 actions.js 战斗分支**

把 `if (inCombat) { ... }` 内(当前读 `Skills.list` 全平铺那段)替换为(读 `Skillbook.known`):

```js
if (inCombat) {
    // 1. 通用技能:永远可用,不依赖 known
    var universalIds = ["basic_attack", "defend", "flee"];
    for (var u = 0; u < universalIds.length; u++) {
        emitCombatCommand(entityId, entity, actions, universalIds[u], "action");
    }
    // 2. 出战技能:known 里 equipped 的,category=skill(进"技能"子菜单)
    var skillsComp = entity.hasComponent("Skillbook") ? entity.getComponent("Skillbook") : null;
    var known = skillsComp !== null ? skillsComp.get("known") : null;
    if (known !== null) {
        for (var j = 0; j < known.size(); j++) {
            var k = known.get(j);
            if (k.get("equipped") !== true) continue;
            emitCombatCommand(entityId, entity, actions, String(k.get("base")), "skill");
        }
    }
    // 移动/道具 = 前端静态禁用占位(BattleGrid),后端不发
}
```

把原来内联的"读 skillDef → can_use → 拼 params → actions.add"逻辑抽成文件内函数 `emitCombatCommand(entityId, entity, actions, skillId, categoryOverride)`,保留原有:`skill.can_use` 事件、targeting/prompt、description、aoeOffsets、allowEmpty、disabled vs requires_target/instant 样式;`category` 用传入的 `categoryOverride`(通用=`action`,技能=`skill`),`flee` 仍以 `command==='flee'` 被前端识别。

- [ ] **Step 4: 跑绿 + 全量**

Run: `cd backend && mvn test -Dtest=SkillbookActionsTest` → PASS
Run: `cd backend && mvn test` → 全绿(Task 1 Step 5 记录的"读旧 list"红测试此时应转绿;若有 golden 涉及 combat 指令列表,确认是否需有意重生 —— 本仓 golden 为技能保真(SkillFidelity),通常不含指令列表,若无相关 golden 则跳过)。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/ui/actions.js backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java
git commit -m "feat(skillbook): 战斗指令=永远发通用技能+仅出战技能(category=skill)"
```

---

## Task 5: 前端通用模态外壳

**Files:**
- Create: `frontend/src/composables/useModal.js`
- Create: `frontend/src/components/Modal.vue`

无前端测试框架 —— 实现后用 `cd frontend && npm run dev` 手动验证。

- [ ] **Step 1: useModal composable**

```js
// 全局模态状态:哪个模态开着(null=关)。技能书是第一个租户;背包/专精等后续复用。
import { ref } from 'vue'
const active = ref(null)               // 'skillbook' | null
export function useModal() {
  return {
    active,
    open:  (id) => { active.value = id },
    close: ()   => { active.value = null },
  }
}
```

- [ ] **Step 2: Modal.vue 外壳**

可复用浮层:遮罩(半透明压暗)+ 居中内容框 + 关闭(ESC/点遮罩/关闭按钮)。边框 2px+(项目规范,禁 1px)。具名 slot 放内容。

```vue
<template>
  <div v-if="visible" class="modal-mask" @click.self="$emit('close')">
    <div class="modal-frame">
      <button class="modal-close" @click="$emit('close')">✕</button>
      <slot />
    </div>
  </div>
</template>
<script setup>
import { onMounted, onUnmounted } from 'vue'
const props = defineProps({ visible: Boolean })
const emit = defineEmits(['close'])
function onKey(e) { if (e.key === 'Escape' && props.visible) emit('close') }
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>
<style scoped>
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal-frame { position: relative; min-width: 32rem; max-width: 80vw; max-height: 80vh;
  overflow: auto; background: var(--panel-bg); border: 2px solid var(--panel-border-color);
  border-radius: 4px; padding: 1rem; }
.modal-close { position: absolute; top: 0.5rem; right: 0.5rem; background: transparent;
  border: 2px solid var(--color-border); color: var(--color-text); cursor: pointer;
  border-radius: 3px; width: 1.8rem; height: 1.8rem; }
.modal-close:hover { border-color: var(--color-enemy); color: var(--color-enemy); }
</style>
```

- [ ] **Step 3: 手动验证 + Commit**

临时在某处 `useModal().open('skillbook')` + 渲染 `<Modal :visible="active==='skillbook'">测试</Modal>`,`npm run dev` 看浮层开关/ESC/点遮罩关闭正常,再撤掉临时代码。

```bash
git add frontend/src/composables/useModal.js frontend/src/components/Modal.vue
git commit -m "feat(ui): 可复用模态外壳 Modal + useModal(技能书首个租户)"
```

---

## Task 6: 技能书内容 SkillbookPanel.vue

**Files:**
- Create: `frontend/src/components/SkillbookPanel.vue`

tab `主动 | 被动 | 通用`,只 `主动` 全功能。读 `snapshot.skillbook`;emit `action`(equip/unequip)。战斗中只读(无出战/移除按钮)。

- [ ] **Step 1: 实现 SkillbookPanel**

```vue
<template>
  <div class="skillbook">
    <div class="sb-tabs">
      <button v-for="t in tabs" :key="t.id" class="sb-tab"
              :class="{ active: tab === t.id }" @click="tab = t.id">{{ t.label }}</button>
      <span class="sb-count" v-if="tab === 'active'">出战 {{ equippedCount }}/{{ slots }}</span>
    </div>

    <div v-if="tab === 'active'" class="sb-list">
      <div v-for="s in sortedKnown" :key="s.base" class="sb-row" :class="{ equipped: s.equipped }">
        <div class="sb-icon">{{ s.icon }}</div>
        <div class="sb-info">
          <div class="sb-name">{{ s.name }}</div>
          <div class="sb-desc">{{ s.description }}</div>
        </div>
        <span class="sb-tree" title="进化树（待 Feature #7）">→树</span>
        <template v-if="!readonly">
          <button v-if="s.equipped" class="sb-btn sb-remove" @click="emitAction('skillbook_unequip', s.base)">移除</button>
          <button v-else class="sb-btn sb-equip" :disabled="equippedCount >= slots"
                  @click="emitAction('skillbook_equip', s.base)">出战</button>
        </template>
      </div>
      <div v-if="sortedKnown.length === 0" class="sb-empty">暂无主动技能</div>
    </div>

    <div v-else class="sb-empty">暂未开放</div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
const props = defineProps({ skillbook: Object, readonly: Boolean })
const emit = defineEmits(['action'])
const tab = ref('active')
const tabs = [{ id:'active', label:'主动' }, { id:'passive', label:'被动' }, { id:'general', label:'通用' }]
const slots = computed(() => props.skillbook?.slots ?? 6)
const known = computed(() => props.skillbook?.known ?? [])
const equippedCount = computed(() => known.value.filter(s => s.equipped).length)
// 出战的高亮置顶
const sortedKnown = computed(() =>
  [...known.value].sort((a, b) => (b.equipped === true) - (a.equipped === true)))
function emitAction(type, base) {
  emit('action', { type, params: { base } })
}
</script>
<style scoped>
.sb-tabs { display: flex; align-items: center; gap: 0.4rem; border-bottom: 2px solid var(--panel-border-color); padding-bottom: 0.4rem; margin-bottom: 0.6rem; }
.sb-tab { background: transparent; border: 2px solid var(--color-border); color: var(--color-text); padding: 0.2rem 0.7rem; border-radius: 3px; cursor: pointer; }
.sb-tab.active { border-color: var(--color-highlight); color: var(--color-highlight); }
.sb-count { margin-left: auto; color: var(--color-highlight); font-weight: 600; }
.sb-list { display: flex; flex-direction: column; gap: 0.4rem; }
.sb-row { display: flex; align-items: center; gap: 0.6rem; border: 2px solid var(--color-border); border-radius: 3px; padding: 0.4rem 0.6rem; }
.sb-row.equipped { border-color: var(--color-highlight); background: color-mix(in srgb, var(--color-highlight) 8%, transparent); }
.sb-icon { width: 2.2rem; height: 2.2rem; flex-shrink: 0; display: flex; align-items: center; justify-content: center; border: 2px solid var(--color-border); border-radius: 4px; font-weight: 700; }
.sb-info { flex: 1; min-width: 0; }
.sb-name { font-weight: 600; }
.sb-desc { font-size: 0.8rem; opacity: 0.7; }
.sb-tree { font-size: 0.75rem; opacity: 0.4; cursor: default; flex-shrink: 0; }
.sb-btn { border: 2px solid var(--color-border); border-radius: 3px; padding: 0.25rem 0.7rem; cursor: pointer; background: transparent; color: var(--color-text); flex-shrink: 0; }
.sb-equip:disabled { opacity: 0.35; cursor: not-allowed; }
.sb-remove { border-color: color-mix(in srgb, var(--color-enemy) 50%, transparent); color: var(--color-enemy); }
.sb-empty { opacity: 0.5; padding: 1rem; text-align: center; }
</style>
```

- [ ] **Step 2: 手动验证(接线在 Task 7,先存盘) + Commit**

```bash
git add frontend/src/components/SkillbookPanel.vue
git commit -m "feat(ui): 技能书 SkillbookPanel(主动 tab 全功能,被动/通用占位,只读态)"
```

---

## Task 7: 右下角常驻菜单 + 挂模态(SnapshotRenderer)

**Files:**
- Modify: `frontend/src/components/SnapshotRenderer.vue`（`panel-nav` 段 L72–78 + 模板末尾挂 Modal）

`panel-nav` 从"快捷"`ActionPanel(filteredActions)` 改为常驻菜单(战斗内外都在)。布局拍定(spec §5.4):`[背包(disabled)][技能][专精(disabled)]` + dev 门控的 `退出角色`。**撤掉旧 `ActionPanel(filteredActions)`** —— 实测它只承载 logout 且战斗中为空(卡死根因);改为独立 `退出角色` 按钮直接 emit `logout`,战斗中也能退。`取消` 预留位本轮不渲染。

- [ ] **Step 1: 改 panel-nav**

把 `panel-nav` 内容替换为:

```vue
<div class="panel-nav">
  <div class="menu-bar">
    <button class="menu-btn" disabled title="待后续">背包</button>
    <button class="menu-btn" @click="openSkillbook">技能</button>
    <button class="menu-btn" disabled title="待后续">专精</button>
  </div>
  <!-- 退出角色:dev 构建显示(防卡战斗退不出);直接 emit logout,不走 filteredActions -->
  <button v-if="isDev" class="menu-btn menu-exit"
          @click="$emit('action', { type: 'logout', params: {} })">退出角色</button>
  <!-- 取消:预留位,本轮不渲染(等有主菜单层级再定) -->
</div>
```
CSS(scoped)加:`.menu-bar { display: flex; gap: 0.4rem; } .menu-btn { border: 2px solid var(--color-border); border-radius: 3px; padding: 0.3rem 0.7rem; background: transparent; color: var(--color-text); cursor: pointer; } .menu-btn:disabled { opacity: 0.35; cursor: not-allowed; } .menu-exit { margin-top: 0.4rem; border-color: color-mix(in srgb, var(--color-enemy) 50%, transparent); color: var(--color-enemy); }`

script 里:
```js
import Modal from './Modal.vue'
import SkillbookPanel from './SkillbookPanel.vue'
import { useModal } from '../composables/useModal.js'
const { active: modalActive, open, close } = useModal()
function openSkillbook() { open('skillbook') }
const isDev = import.meta.env.DEV   // Vite 内置:dev 构建 true、prod build false
```
> 若 `ActionPanel` import / `filteredActions` computed 此后无其他用处,一并删除(撤干净)。`logout` 不再经 `filteredActions`,改由 `退出角色` 按钮直接 emit。

- [ ] **Step 2: 模板末尾挂模态**

在 `<ItemTooltip />` 旁加:
```vue
<Modal :visible="modalActive === 'skillbook'" @close="close">
  <SkillbookPanel :skillbook="snapshot.skillbook" :readonly="!!snapshot.combat"
                  @action="$emit('action', $event)" />
</Modal>
```

- [ ] **Step 3: 手动验证 + Commit**

`npm run dev`:右下角出现 `背包/技能/专精`(背包/专精灰)+ `退出角色`(dev 可见);点技能开模态,主动 tab 见职业技能 + `出战 N/6`;移除/出战切换,满格出战禁用;进战斗后开技能书 = 只读(无按钮);**战斗中点退出角色能退出(不卡死)**。

```bash
git add frontend/src/components/SnapshotRenderer.vue
git commit -m "feat(ui): 右下角常驻菜单(背包/技能/专精)+ 挂技能书模态"
```

---

## Task 8: 战斗指令栏 command-row 重排(BattleGrid)

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`（`command-row` 模板 L100–148 + 相关 computed/CSS）

主指令区固定 6 按钮(攻击/防御/技能/移动/道具/逃跑,3×2 缩窄);移动/道具静态禁用;点技能子菜单出 6 出战技能(3×2)+ 纵跨两行正方形取消键。

- [ ] **Step 1: 改 computed 选指令(按 command id 精确取,替代 first/other 启发式)**

```js
const attackAction = computed(() => props.commands.find(c => c.params?.command === 'basic_attack') || null)
const defendAction = computed(() => props.commands.find(c => c.params?.command === 'defend') || null)
const fleeAction   = computed(() => props.commands.find(c => c.params?.command === 'flee') || null)
const skillActions = computed(() => props.commands.filter(c => c.params?.category === 'skill'))
```
（删除旧的 `firstMainAction` / `otherMainActions`。）

- [ ] **Step 2: 改 cmd-actions 模板为固定 6 按钮(3×2)**

```vue
<div class="cmd-col cmd-actions" v-if="currentActor && phase === 'COMMAND' && !animating" :class="{ locked: showSkills || step === 'target' }">
  <button class="cmd-btn" @click="selectCommand(attackAction)" :disabled="!attackAction">攻击</button>
  <button class="cmd-btn" @click="selectCommand(defendAction)" :disabled="!defendAction">防御</button>
  <button class="cmd-btn" :class="{ active: showSkills }" :disabled="skillActions.length === 0" @click="showSkills = true">技能</button>
  <button class="cmd-btn disabled" disabled title="暂未开放">移动</button>
  <button class="cmd-btn disabled" disabled title="暂未开放">道具</button>
  <button class="cmd-btn" @click="selectCommand(fleeAction)" :disabled="!fleeAction">逃跑</button>
</div>
```
CSS:`.cmd-actions { grid-template-columns: repeat(3, 1fr); }`(从 `1fr 1fr` 改 3 列;按钮缩窄沿用 `.cmd-btn`,2 字内容自然窄)。

- [ ] **Step 3: 改 cmd-sub 技能子菜单为 3×2 + 纵跨取消键**

```vue
<template v-else-if="showSkills">
  <div class="skill-sub-grid">
    <button v-for="cmd in skillActions" :key="cmd.params?.command"
            class="cmd-btn skill-cmd-btn" :class="{ disabled: cmd.style === 'disabled' }"
            @mouseenter="onSkillHover(cmd, $event)" @mousemove="moveTooltip" @mouseleave="hideTooltip"
            @click="selectCommand(cmd)">{{ cmd.label }}</button>
    <button class="cmd-btn cancel-btn cancel-tall" @click="cancelSub">取消</button>
  </div>
</template>
```
CSS:
```css
.skill-sub-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr auto; /* 3 列技能 + 取消列 */
  grid-template-rows: 1fr 1fr;             /* 2 行 */
  gap: 0.3rem; width: 100%;
}
.cancel-tall { grid-column: 4; grid-row: 1 / 3; aspect-ratio: 1; } /* 正方形,纵跨两行 */
```
（`cmd-sub` 外层原 2 列 grid 样式由内层 `.skill-sub-grid` 接管;`target` 选目标态的取消键沿用原 `cancelSelect`。技能不足 6 个时 grid 自然留空。）

- [ ] **Step 4: 手动验证**

`npm run dev` 进战斗:
- 主指令 3×2 六格,攻击/防御/技能/逃跑 可点,移动/道具 灰。
- 点"技能"→ 子菜单 6 格(3×2)+ 右侧正方形取消键纵跨两行;不足 6 个留空;取消回主指令。
- 出战技能数量随技能书配置变化(去技能书移除一个 → 战斗子菜单少一个)。
- hover 技能 tooltip(描述/MP)正常。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/combat/BattleGrid.vue
git commit -m "feat(combat): 指令栏固定6格(3×2)+技能子菜单3×2+纵跨正方形取消键;移动/道具占位"
```

---

## 收尾:全量回归 + code review

- [ ] `cd backend && mvn test` 全绿。
- [ ] `cd frontend && npm run dev` 跑一遍 §6 spec 的 PM 手动走查清单(开模态→配出战→满格禁用→进战斗指令栏→技能子菜单→移动/道具灰→战斗中技能书只读→战斗中退出角色不卡死)。
- [ ] **后端守闸单独验**(spec §6):战斗中直接打 `skillbook_equip`/`skillbook_unequip` API → 再取 snapshot,`known` 不变。
- [ ] 发 `requesting-code-review`,等 Claude(Senior SDE)APPROVE 后再 merge。**别扫** spec OUT 清单外的东西;commit 只圈本 feature 改的文件。

---

## Self-Review(已核对)

- **Spec 覆盖:** 数据模型(T1)/ equip-unequip 三校验(T2)/ snapshot 块(T3)/ actions 通用+出战(T4)/ 模态外壳(T5)/ 主动 tab(T6)/ 常驻菜单(T7)/ 指令栏重排(T8)—— spec §2–§5 全部落到 task。
- **占位符:** 无 TBD/TODO;`→树`、移动/道具、被动/通用 tab 是**设计性占位**(spec OUT),非计划缺口。
- **类型一致:** 组件名 **`Skillbook`**`.{slots,known:[{base,node,equipped}]}` 跨 T1/T2/T3/T4 一致(已全改名,无残留 `Skills` 组件引用);`WorldSnapshot.SkillEntry(base,name,description,icon,equipped,node)` 与 `newSkillEntry` 形参、JS handler `out.add`、前端 `s.description` 一致;action 名 `skillbook_equip`/`skillbook_unequip` 跨 T2(handler)/T6/T7(前端 emit)一致;`category==='skill'`/`command==='flee'` 跨 T4(后端)/T8(前端识别)一致。
- **决策偏离记录:** spec §3.3/§5.3 move/item 占位 = **前端静态禁用**(T4 后端不发、T8 前端画)。
- **2026-06-03 review 修订(已贯穿全文):** ① 组件 `Skills`→`Skillbook`;② 失败 = 静默拒绝(T2 无 `event.set("error")`,断言状态不变;ActionResult 通道未接,YAGNI);③ snapshot 字段 `desc`→`description`(record/工厂/handler/前端全线);④ panel-nav 撤 `ActionPanel(filteredActions)`,改 `[背包][技能][专精]` + dev 门控 `退出角色`(直接 emit logout,解战斗卡死),`取消` 预留不渲染;⑤ T3 文件清单显式含 `WorldSnapshot`(新 record)+ `ScriptRuntime`。
