# Combat Engine Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the core combat loop — position system, turn flow (issue commands → speed-ordered resolution), basic attack/defend, combat triggers from scenes, and combat UI in the existing 4×3 panel layout.

**Architecture:** A `combat` package handles all battle logic: combatants occupy positions on a 3×3 grid per side, take turns based on speed, and execute commands. A `CombatActionHandler` bridges the existing action system to combat. The frontend adds a `CombatPanel` (main view), `CommandPanel` (right-top), `CombatLog` (bottom), and `CombatStatus` (right-bottom) that replace normal panels during battle.

**Tech Stack:** Java 21, Spring Boot 3, Vue 3 (Composition API), existing Mod YAML loading

---

## File Structure

```
backend/src/main/java/com/epic/engine/combat/
├── model/
│   ├── Combatant.java            — A single unit in battle (hp, speed, position, side)
│   ├── Position.java             — Row + slot (row: FRONT/MID/BACK, slot: 0-2)
│   ├── Side.java                 — Enum: PLAYER, ENEMY
│   ├── CombatState.java          — Full battle state (combatants, turn order, round, phase)
│   └── CombatPhase.java          — Enum: COMMAND, RESOLVE, VICTORY, DEFEAT
├── command/
│   ├── CombatCommand.java        — Record: who does what to whom
│   ├── CommandType.java          — Enum: ATTACK, DEFEND
│   └── TargetResolver.java       — Finds valid targets (nearest row logic)
├── engine/
│   ├── CombatEngine.java         — Orchestrates turn flow, resolves commands
│   ├── DamageCalculator.java     — Computes damage (attack - defense, min 1)
│   └── CombatResult.java         — Record: what happened (for log display)
├── CombatService.java            — Manages active combats, bridges to REST layer
├── CombatController.java         — REST endpoints for combat
└── StartCombatActionHandler.java — ActionHandler that triggers combat from scenes

backend/src/test/java/com/epic/engine/combat/
├── model/TargetResolverTest.java
├── engine/CombatEngineTest.java
└── CombatIntegrationTest.java

frontend/src/components/combat/
├── CombatView.vue                — Top-level combat layout (replaces normal panels)
├── BattleField.vue               — Main panel: renders both sides' formations
├── CommandPanel.vue              — Right panel: action menu for current combatant
├── CombatLogPanel.vue            — Bottom panel: battle text log
└── CombatStatusPanel.vue         — Bottom-right: current combatant stats

frontend/src/composables/
└── useCombat.js                  — Combat state management

frontend/src/api/client.js        — Add combat API calls (modify)
frontend/src/App.vue              — Switch between exploration/combat views (modify)

mods/base/encounters/
└── forest_goblin.yaml            — Sample encounter definition
```

---

### Task 1: Combat Data Models

**Files:**
- Create: `backend/src/main/java/com/epic/engine/combat/model/Side.java`
- Create: `backend/src/main/java/com/epic/engine/combat/model/Position.java`
- Create: `backend/src/main/java/com/epic/engine/combat/model/Combatant.java`
- Create: `backend/src/main/java/com/epic/engine/combat/model/CombatPhase.java`
- Create: `backend/src/main/java/com/epic/engine/combat/model/CombatState.java`

- [ ] **Step 1: Create Side enum**

```java
// backend/src/main/java/com/epic/engine/combat/model/Side.java
package com.epic.engine.combat.model;

public enum Side {
    PLAYER,
    ENEMY
}
```

- [ ] **Step 2: Create Position record**

```java
// backend/src/main/java/com/epic/engine/combat/model/Position.java
package com.epic.engine.combat.model;

public record Position(Row row, int slot) {

    public enum Row {
        FRONT(0),
        MID(1),
        BACK(2);

        private final int distance;

        Row(int distance) {
            this.distance = distance;
        }

        public int distance() {
            return distance;
        }
    }
}
```

- [ ] **Step 3: Create Combatant class**

```java
// backend/src/main/java/com/epic/engine/combat/model/Combatant.java
package com.epic.engine.combat.model;

public class Combatant {

    private final String id;
    private final String name;
    private final Side side;
    private final Position position;
    private int maxHp;
    private int hp;
    private int attack;
    private int defense;
    private int speed;
    private boolean defending;

    public Combatant(String id, String name, Side side, Position position,
                     int maxHp, int attack, int defense, int speed) {
        this.id = id;
        this.name = name;
        this.side = side;
        this.position = position;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.speed = speed;
        this.defending = false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Side getSide() { return side; }
    public Position getPosition() { return position; }
    public int getMaxHp() { return maxHp; }
    public int getHp() { return hp; }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public int getSpeed() { return speed; }
    public boolean isDefending() { return defending; }
    public boolean isAlive() { return hp > 0; }

    public void takeDamage(int amount) {
        this.hp = Math.max(0, this.hp - amount);
    }

    public void setDefending(boolean defending) {
        this.defending = defending;
    }
}
```

- [ ] **Step 4: Create CombatPhase enum**

```java
// backend/src/main/java/com/epic/engine/combat/model/CombatPhase.java
package com.epic.engine.combat.model;

public enum CombatPhase {
    COMMAND,
    RESOLVE,
    VICTORY,
    DEFEAT
}
```

- [ ] **Step 5: Create CombatState class**

```java
// backend/src/main/java/com/epic/engine/combat/model/CombatState.java
package com.epic.engine.combat.model;

import java.util.ArrayList;
import java.util.List;

public class CombatState {

    private final String combatId;
    private final List<Combatant> combatants;
    private int round;
    private CombatPhase phase;
    private int currentCommandIndex;

    public CombatState(String combatId, List<Combatant> combatants) {
        this.combatId = combatId;
        this.combatants = new ArrayList<>(combatants);
        this.round = 1;
        this.phase = CombatPhase.COMMAND;
        this.currentCommandIndex = 0;
    }

    public String getCombatId() { return combatId; }
    public List<Combatant> getCombatants() { return combatants; }
    public int getRound() { return round; }
    public CombatPhase getPhase() { return phase; }
    public void setPhase(CombatPhase phase) { this.phase = phase; }
    public void nextRound() { this.round++; this.currentCommandIndex = 0; }
    public int getCurrentCommandIndex() { return currentCommandIndex; }
    public void advanceCommandIndex() { this.currentCommandIndex++; }

    public List<Combatant> getPlayerCombatants() {
        return combatants.stream()
                .filter(c -> c.getSide() == Side.PLAYER && c.isAlive())
                .toList();
    }

    public List<Combatant> getEnemyCombatants() {
        return combatants.stream()
                .filter(c -> c.getSide() == Side.ENEMY && c.isAlive())
                .toList();
    }

    public Combatant getCombatantById(String id) {
        return combatants.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public boolean isPlayerVictory() {
        return getEnemyCombatants().isEmpty();
    }

    public boolean isPlayerDefeat() {
        return getPlayerCombatants().isEmpty();
    }
}
```

- [ ] **Step 6: Compile check**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/epic/engine/combat/
git commit -m "feat: add combat data models (Combatant, Position, CombatState)"
```

---

### Task 2: Target Resolution

**Files:**
- Create: `backend/src/main/java/com/epic/engine/combat/command/TargetResolver.java`
- Create: `backend/src/test/java/com/epic/engine/combat/model/TargetResolverTest.java`

- [ ] **Step 1: Write failing test for TargetResolver**

```java
// backend/src/test/java/com/epic/engine/combat/model/TargetResolverTest.java
package com.epic.engine.combat.model;

import com.epic.engine.combat.command.TargetResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TargetResolverTest {

    @Test
    void attacksNearestRow_frontRowAlive() {
        Combatant frontEnemy = new Combatant("e1", "哥布林", Side.ENEMY,
                new Position(Position.Row.FRONT, 0), 20, 5, 2, 3);
        Combatant backEnemy = new Combatant("e2", "弓手", Side.ENEMY,
                new Position(Position.Row.BACK, 0), 15, 7, 1, 5);
        List<Combatant> enemies = List.of(frontEnemy, backEnemy);

        List<Combatant> targets = TargetResolver.getBasicAttackTargets(enemies);

        assertThat(targets).containsExactly(frontEnemy);
    }

    @Test
    void attacksMiddleRow_whenFrontRowEmpty() {
        Combatant midEnemy = new Combatant("e1", "萨满", Side.ENEMY,
                new Position(Position.Row.MID, 0), 18, 6, 3, 4);
        Combatant backEnemy = new Combatant("e2", "弓手", Side.ENEMY,
                new Position(Position.Row.BACK, 0), 15, 7, 1, 5);
        List<Combatant> enemies = List.of(midEnemy, backEnemy);

        List<Combatant> targets = TargetResolver.getBasicAttackTargets(enemies);

        assertThat(targets).containsExactly(midEnemy);
    }

    @Test
    void returnsAllCombatantsInNearestRow() {
        Combatant front1 = new Combatant("e1", "哥布林A", Side.ENEMY,
                new Position(Position.Row.FRONT, 0), 20, 5, 2, 3);
        Combatant front2 = new Combatant("e2", "哥布林B", Side.ENEMY,
                new Position(Position.Row.FRONT, 1), 20, 5, 2, 3);
        Combatant backEnemy = new Combatant("e3", "弓手", Side.ENEMY,
                new Position(Position.Row.BACK, 0), 15, 7, 1, 5);
        List<Combatant> enemies = List.of(front1, front2, backEnemy);

        List<Combatant> targets = TargetResolver.getBasicAttackTargets(enemies);

        assertThat(targets).containsExactlyInAnyOrder(front1, front2);
    }

    @Test
    void returnsEmptyWhenNoAliveEnemies() {
        List<Combatant> enemies = List.of();
        List<Combatant> targets = TargetResolver.getBasicAttackTargets(enemies);
        assertThat(targets).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test -Dtest=TargetResolverTest -pl .`
Expected: Compilation error — TargetResolver doesn't exist.

- [ ] **Step 3: Implement TargetResolver**

```java
// backend/src/main/java/com/epic/engine/combat/command/TargetResolver.java
package com.epic.engine.combat.command;

import com.epic.engine.combat.model.Combatant;
import com.epic.engine.combat.model.Position;

import java.util.Comparator;
import java.util.List;

public class TargetResolver {

    public static List<Combatant> getBasicAttackTargets(List<Combatant> enemies) {
        if (enemies.isEmpty()) {
            return List.of();
        }

        Position.Row nearestRow = enemies.stream()
                .map(c -> c.getPosition().row())
                .min(Comparator.comparingInt(Position.Row::distance))
                .orElse(Position.Row.FRONT);

        return enemies.stream()
                .filter(c -> c.getPosition().row() == nearestRow)
                .toList();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test -Dtest=TargetResolverTest -pl .`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/combat/command/TargetResolver.java backend/src/test/java/com/epic/engine/combat/model/TargetResolverTest.java
git commit -m "feat: implement target resolution (nearest row logic)"
```

---

### Task 3: Combat Commands and Damage Calculation

**Files:**
- Create: `backend/src/main/java/com/epic/engine/combat/command/CommandType.java`
- Create: `backend/src/main/java/com/epic/engine/combat/command/CombatCommand.java`
- Create: `backend/src/main/java/com/epic/engine/combat/engine/DamageCalculator.java`
- Create: `backend/src/main/java/com/epic/engine/combat/engine/CombatResult.java`

- [ ] **Step 1: Create CommandType enum**

```java
// backend/src/main/java/com/epic/engine/combat/command/CommandType.java
package com.epic.engine.combat.command;

public enum CommandType {
    ATTACK,
    DEFEND
}
```

- [ ] **Step 2: Create CombatCommand record**

```java
// backend/src/main/java/com/epic/engine/combat/command/CombatCommand.java
package com.epic.engine.combat.command;

public record CombatCommand(String actorId, CommandType type, String targetId) {}
```

- [ ] **Step 3: Create CombatResult record**

```java
// backend/src/main/java/com/epic/engine/combat/engine/CombatResult.java
package com.epic.engine.combat.engine;

public record CombatResult(String actorId, String actorName, String targetId, String targetName,
                           String action, int damage, boolean targetDefeated) {

    public static CombatResult attack(String actorId, String actorName,
                                      String targetId, String targetName,
                                      int damage, boolean defeated) {
        return new CombatResult(actorId, actorName, targetId, targetName, "attack", damage, defeated);
    }

    public static CombatResult defend(String actorId, String actorName) {
        return new CombatResult(actorId, actorName, null, null, "defend", 0, false);
    }
}
```

- [ ] **Step 4: Create DamageCalculator**

```java
// backend/src/main/java/com/epic/engine/combat/engine/DamageCalculator.java
package com.epic.engine.combat.engine;

import com.epic.engine.combat.model.Combatant;

public class DamageCalculator {

    public static int calculate(Combatant attacker, Combatant defender) {
        int baseDamage = attacker.getAttack() - defender.getDefense();
        if (defender.isDefending()) {
            baseDamage = baseDamage / 2;
        }
        return Math.max(1, baseDamage);
    }
}
```

- [ ] **Step 5: Compile check**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/epic/engine/combat/command/ backend/src/main/java/com/epic/engine/combat/engine/
git commit -m "feat: add combat commands, damage calculator, and combat results"
```

---

### Task 4: Combat Engine (Turn Resolution)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/combat/engine/CombatEngine.java`
- Create: `backend/src/test/java/com/epic/engine/combat/engine/CombatEngineTest.java`

- [ ] **Step 1: Write failing test for CombatEngine**

```java
// backend/src/test/java/com/epic/engine/combat/engine/CombatEngineTest.java
package com.epic.engine.combat.engine;

import com.epic.engine.combat.command.CombatCommand;
import com.epic.engine.combat.command.CommandType;
import com.epic.engine.combat.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombatEngineTest {

    CombatState state;
    CombatEngine engine;

    @BeforeEach
    void setUp() {
        Combatant warrior = new Combatant("p1", "战士", Side.PLAYER,
                new Position(Position.Row.FRONT, 0), 50, 10, 5, 6);
        Combatant mage = new Combatant("p2", "法师", Side.PLAYER,
                new Position(Position.Row.BACK, 0), 30, 15, 2, 8);
        Combatant goblin = new Combatant("e1", "哥布林", Side.ENEMY,
                new Position(Position.Row.FRONT, 0), 20, 5, 2, 3);
        Combatant archer = new Combatant("e2", "弓手", Side.ENEMY,
                new Position(Position.Row.BACK, 0), 15, 7, 1, 5);

        state = new CombatState("test-combat", List.of(warrior, mage, goblin, archer));
        engine = new CombatEngine();
    }

    @Test
    void resolvesCommandsInSpeedOrder() {
        List<CombatCommand> playerCommands = List.of(
                new CombatCommand("p1", CommandType.ATTACK, "e1"),
                new CombatCommand("p2", CommandType.ATTACK, "e1")
        );
        List<CombatCommand> enemyCommands = List.of(
                new CombatCommand("e1", CommandType.ATTACK, "p1"),
                new CombatCommand("e2", CommandType.ATTACK, "p1")
        );

        List<CombatResult> results = engine.resolveRound(state, playerCommands, enemyCommands);

        assertThat(results).isNotEmpty();
        // Mage (speed 8) goes first, then warrior (6), archer (5), goblin (3)
        assertThat(results.get(0).actorName()).isEqualTo("法师");
    }

    @Test
    void defendReducesDamage() {
        List<CombatCommand> playerCommands = List.of(
                new CombatCommand("p1", CommandType.DEFEND, null),
                new CombatCommand("p2", CommandType.ATTACK, "e1")
        );
        List<CombatCommand> enemyCommands = List.of(
                new CombatCommand("e1", CommandType.ATTACK, "p1"),
                new CombatCommand("e2", CommandType.ATTACK, "p1")
        );

        Combatant warrior = state.getCombatantById("p1");
        int hpBefore = warrior.getHp();
        engine.resolveRound(state, playerCommands, enemyCommands);
        int hpAfter = warrior.getHp();

        // Goblin: (5-5)/2 = min 1, Archer: (7-5)/2 = 1 → total 2 damage
        // Without defend it would be: (5-5)=min1 + (7-5)=2 → 3 damage
        assertThat(hpBefore - hpAfter).isLessThan(3);
    }

    @Test
    void deadUnitsDoNotAct() {
        // Kill goblin first
        state.getCombatantById("e1").takeDamage(20);
        assertThat(state.getCombatantById("e1").isAlive()).isFalse();

        List<CombatCommand> playerCommands = List.of(
                new CombatCommand("p1", CommandType.ATTACK, "e2"),
                new CombatCommand("p2", CommandType.ATTACK, "e2")
        );
        List<CombatCommand> enemyCommands = List.of(
                new CombatCommand("e1", CommandType.ATTACK, "p1"),
                new CombatCommand("e2", CommandType.ATTACK, "p1")
        );

        List<CombatResult> results = engine.resolveRound(state, playerCommands, enemyCommands);

        assertThat(results.stream().noneMatch(r -> r.actorId().equals("e1"))).isTrue();
    }

    @Test
    void detectsVictoryWhenAllEnemiesDead() {
        state.getCombatantById("e1").takeDamage(20);
        state.getCombatantById("e2").takeDamage(15);

        assertThat(state.isPlayerVictory()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test -Dtest=CombatEngineTest -pl .`
Expected: Compilation error — CombatEngine doesn't exist.

- [ ] **Step 3: Implement CombatEngine**

```java
// backend/src/main/java/com/epic/engine/combat/engine/CombatEngine.java
package com.epic.engine.combat.engine;

import com.epic.engine.combat.command.CombatCommand;
import com.epic.engine.combat.command.CommandType;
import com.epic.engine.combat.model.CombatState;
import com.epic.engine.combat.model.Combatant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CombatEngine {

    public List<CombatResult> resolveRound(CombatState state,
                                           List<CombatCommand> playerCommands,
                                           List<CombatCommand> enemyCommands) {
        // Reset defending status
        state.getCombatants().forEach(c -> c.setDefending(false));

        // Merge all commands and sort by actor speed (descending)
        Map<String, CombatCommand> commandMap = Stream.concat(
                playerCommands.stream(), enemyCommands.stream()
        ).collect(Collectors.toMap(CombatCommand::actorId, c -> c));

        List<Combatant> turnOrder = state.getCombatants().stream()
                .filter(Combatant::isAlive)
                .sorted(Comparator.comparingInt(Combatant::getSpeed).reversed())
                .toList();

        // First pass: apply defend commands
        for (Combatant actor : turnOrder) {
            CombatCommand cmd = commandMap.get(actor.getId());
            if (cmd != null && cmd.type() == CommandType.DEFEND) {
                actor.setDefending(true);
            }
        }

        // Second pass: resolve actions in speed order
        List<CombatResult> results = new ArrayList<>();
        for (Combatant actor : turnOrder) {
            if (!actor.isAlive()) continue;

            CombatCommand cmd = commandMap.get(actor.getId());
            if (cmd == null) continue;

            CombatResult result = executeCommand(state, actor, cmd);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    private CombatResult executeCommand(CombatState state, Combatant actor, CombatCommand cmd) {
        return switch (cmd.type()) {
            case ATTACK -> executeAttack(state, actor, cmd);
            case DEFEND -> CombatResult.defend(actor.getId(), actor.getName());
        };
    }

    private CombatResult executeAttack(CombatState state, Combatant actor, CombatCommand cmd) {
        Combatant target = state.getCombatantById(cmd.targetId());
        if (target == null || !target.isAlive()) {
            return null;
        }

        int damage = DamageCalculator.calculate(actor, target);
        target.takeDamage(damage);

        return CombatResult.attack(
                actor.getId(), actor.getName(),
                target.getId(), target.getName(),
                damage, !target.isAlive()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test -Dtest=CombatEngineTest -pl .`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/combat/engine/CombatEngine.java backend/src/test/java/com/epic/engine/combat/engine/CombatEngineTest.java
git commit -m "feat: implement combat engine with speed-ordered turn resolution"
```

---

### Task 5: Combat Service and REST API

**Files:**
- Create: `backend/src/main/java/com/epic/engine/combat/CombatService.java`
- Create: `backend/src/main/java/com/epic/engine/combat/CombatController.java`
- Create: `backend/src/main/java/com/epic/engine/combat/StartCombatActionHandler.java`
- Create: `mods/base/encounters/forest_goblin.yaml`

- [ ] **Step 1: Create encounter definition**

```yaml
# mods/base/encounters/forest_goblin.yaml
id: forest_goblin
name: "森林哥布林遭遇战"
enemies:
  - id: goblin1
    name: "哥布林"
    row: FRONT
    slot: 0
    hp: 20
    attack: 5
    defense: 2
    speed: 3
  - id: goblin2
    name: "哥布林弓手"
    row: BACK
    slot: 0
    hp: 15
    attack: 7
    defense: 1
    speed: 5
```

- [ ] **Step 2: Implement CombatService**

```java
// backend/src/main/java/com/epic/engine/combat/CombatService.java
package com.epic.engine.combat;

import com.epic.engine.combat.command.CombatCommand;
import com.epic.engine.combat.command.CommandType;
import com.epic.engine.combat.command.TargetResolver;
import com.epic.engine.combat.engine.CombatEngine;
import com.epic.engine.combat.engine.CombatResult;
import com.epic.engine.combat.model.*;
import com.epic.engine.mod.ModRegistry;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CombatService {

    private final Map<String, CombatState> activeCombats = new ConcurrentHashMap<>();
    private final CombatEngine engine = new CombatEngine();
    private final ModRegistry modRegistry;

    public CombatService(ModRegistry modRegistry) {
        this.modRegistry = modRegistry;
    }

    @SuppressWarnings("unchecked")
    public CombatState startCombat(String playerId, String encounterId) {
        Optional<Map<String, Object>> encounterData = modRegistry.getEncounter(encounterId);
        if (encounterData.isEmpty()) {
            return null;
        }

        Map<String, Object> data = encounterData.get();
        List<Combatant> combatants = new ArrayList<>();

        // Add player character (placeholder stats for now)
        combatants.add(new Combatant(
                playerId, "冒险者", Side.PLAYER,
                new Position(Position.Row.FRONT, 0),
                50, 10, 5, 6
        ));

        // Add enemies from encounter definition
        List<Map<String, Object>> enemies = (List<Map<String, Object>>) data.get("enemies");
        for (Map<String, Object> enemy : enemies) {
            combatants.add(new Combatant(
                    (String) enemy.get("id"),
                    (String) enemy.get("name"),
                    Side.ENEMY,
                    new Position(Position.Row.valueOf((String) enemy.get("row")),
                            (int) enemy.get("slot")),
                    (int) enemy.get("hp"),
                    (int) enemy.get("attack"),
                    (int) enemy.get("defense"),
                    (int) enemy.get("speed")
            ));
        }

        String combatId = playerId + "-" + UUID.randomUUID().toString().substring(0, 8);
        CombatState state = new CombatState(combatId, combatants);
        activeCombats.put(playerId, state);
        return state;
    }

    public CombatState getCombatState(String playerId) {
        return activeCombats.get(playerId);
    }

    public List<CombatResult> submitCommands(String playerId, List<CombatCommand> playerCommands) {
        CombatState state = activeCombats.get(playerId);
        if (state == null || state.getPhase() != CombatPhase.COMMAND) {
            return List.of();
        }

        // Generate enemy commands (simple AI: attack nearest)
        List<CombatCommand> enemyCommands = generateEnemyCommands(state);

        // Resolve the round
        state.setPhase(CombatPhase.RESOLVE);
        List<CombatResult> results = engine.resolveRound(state, playerCommands, enemyCommands);

        // Check victory/defeat
        if (state.isPlayerVictory()) {
            state.setPhase(CombatPhase.VICTORY);
        } else if (state.isPlayerDefeat()) {
            state.setPhase(CombatPhase.DEFEAT);
        } else {
            state.nextRound();
            state.setPhase(CombatPhase.COMMAND);
        }

        return results;
    }

    public void endCombat(String playerId) {
        activeCombats.remove(playerId);
    }

    private List<CombatCommand> generateEnemyCommands(CombatState state) {
        List<Combatant> enemies = state.getEnemyCombatants();
        List<Combatant> playerTargets = TargetResolver.getBasicAttackTargets(state.getPlayerCombatants());

        List<CombatCommand> commands = new ArrayList<>();
        Random random = new Random();
        for (Combatant enemy : enemies) {
            if (!playerTargets.isEmpty()) {
                Combatant target = playerTargets.get(random.nextInt(playerTargets.size()));
                commands.add(new CombatCommand(enemy.getId(), CommandType.ATTACK, target.getId()));
            }
        }
        return commands;
    }
}
```

- [ ] **Step 3: Implement CombatController**

```java
// backend/src/main/java/com/epic/engine/combat/CombatController.java
package com.epic.engine.combat;

import com.epic.engine.combat.command.CombatCommand;
import com.epic.engine.combat.command.CommandType;
import com.epic.engine.combat.command.TargetResolver;
import com.epic.engine.combat.engine.CombatResult;
import com.epic.engine.combat.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/combat")
public class CombatController {

    private final CombatService combatService;

    public CombatController(CombatService combatService) {
        this.combatService = combatService;
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<CombatStateDto> getState(@PathVariable String playerId) {
        CombatState state = combatService.getCombatState(playerId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(state));
    }

    @PostMapping("/{playerId}/commands")
    public ResponseEntity<RoundResultDto> submitCommands(
            @PathVariable String playerId,
            @RequestBody List<CommandDto> commands) {
        List<CombatCommand> combatCommands = commands.stream()
                .map(dto -> new CombatCommand(dto.actorId(), CommandType.valueOf(dto.type()), dto.targetId()))
                .toList();

        List<CombatResult> results = combatService.submitCommands(playerId, combatCommands);
        CombatState state = combatService.getCombatState(playerId);

        return ResponseEntity.ok(new RoundResultDto(results, toDto(state)));
    }

    @GetMapping("/{playerId}/targets/{combatantId}")
    public List<TargetDto> getValidTargets(@PathVariable String playerId,
                                           @PathVariable String combatantId) {
        CombatState state = combatService.getCombatState(playerId);
        if (state == null) return List.of();

        Combatant actor = state.getCombatantById(combatantId);
        if (actor == null) return List.of();

        List<Combatant> enemies = actor.getSide() == Side.PLAYER
                ? state.getEnemyCombatants()
                : state.getPlayerCombatants();
        List<Combatant> targets = TargetResolver.getBasicAttackTargets(enemies);

        return targets.stream()
                .map(t -> new TargetDto(t.getId(), t.getName(), t.getHp(), t.getMaxHp()))
                .toList();
    }

    private CombatStateDto toDto(CombatState state) {
        List<CombatantDto> combatants = state.getCombatants().stream()
                .map(c -> new CombatantDto(c.getId(), c.getName(), c.getSide().name(),
                        c.getPosition().row().name(), c.getPosition().slot(),
                        c.getHp(), c.getMaxHp(), c.getAttack(), c.getDefense(),
                        c.getSpeed(), c.isAlive()))
                .toList();
        return new CombatStateDto(state.getCombatId(), state.getRound(),
                state.getPhase().name(), combatants);
    }

    public record CommandDto(String actorId, String type, String targetId) {}
    public record TargetDto(String id, String name, int hp, int maxHp) {}
    public record CombatantDto(String id, String name, String side, String row, int slot,
                               int hp, int maxHp, int attack, int defense, int speed,
                               boolean alive) {}
    public record CombatStateDto(String combatId, int round, String phase,
                                 List<CombatantDto> combatants) {}
    public record RoundResultDto(List<CombatResult> results, CombatStateDto state) {}
}
```

- [ ] **Step 4: Implement StartCombatActionHandler**

```java
// backend/src/main/java/com/epic/engine/combat/StartCombatActionHandler.java
package com.epic.engine.combat;

import com.epic.engine.action.ActionHandler;
import com.epic.engine.action.ActionResponse;
import com.epic.engine.combat.model.CombatState;
import com.epic.engine.panel.PanelRefresh;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StartCombatActionHandler implements ActionHandler {

    private final CombatService combatService;

    public StartCombatActionHandler(CombatService combatService) {
        this.combatService = combatService;
    }

    @Override
    public String getType() {
        return "combat";
    }

    @Override
    public ActionResponse handle(String playerId, Map<String, String> params) {
        String encounterId = params.get("encounter");
        if (encounterId == null || encounterId.isBlank()) {
            return new ActionResponse(false, "未指定遭遇战", List.of());
        }

        CombatState state = combatService.startCombat(playerId, encounterId);
        if (state == null) {
            return new ActionResponse(false, "遭遇战不存在: " + encounterId, List.of());
        }

        return new ActionResponse(true, null, List.of(PanelRefresh.SCENE));
    }
}
```

- [ ] **Step 5: Add encounter loading to ModRegistry**

Modify `backend/src/main/java/com/epic/engine/mod/ModRegistry.java` — add encounter support:

```java
// Add this field after the scenes field:
private final Map<String, Map<String, Object>> encounters = new LinkedHashMap<>();

// Add this method:
public Optional<Map<String, Object>> getEncounter(String encounterId) {
    return Optional.ofNullable(encounters.get(encounterId));
}

// Add this to the loadModContent method, after the scenes loading block:
Path encountersDir = mod.path().resolve("encounters");
if (Files.isDirectory(encountersDir)) {
    Yaml yaml = new Yaml();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(encountersDir, "*.yaml")) {
        for (Path encounterFile : stream) {
            try (InputStream is = Files.newInputStream(encounterFile)) {
                Map<String, Object> encounterData = yaml.load(is);
                String id = (String) encounterData.get("id");
                encounters.put(id, encounterData);
            }
        }
    }
}
```

- [ ] **Step 6: Add combat action to forest scene**

Modify `mods/base/scenes/forest_entrance.yaml` — add a combat action:

```yaml
id: forest_entrance
description:
  - text: "你来到"
    color: "#e0e0e0"
  - text: "幽暗森林"
    color: "#2d6a4f"
  - text: "的入口。高大的古树遮天蔽日，只有零星的"
    color: "#e0e0e0"
  - text: "光线"
    color: "#ffd93d"
  - text: "透过树冠洒下。远处传来不明生物的"
    color: "#e0e0e0"
  - text: "低吼"
    color: "#e94560"
  - text: "。"
    color: "#e0e0e0"
actions:
  - id: go-square
    label: "返回村庄"
    type: move
    target: village_square
  - id: fight-goblins
    label: "迎战哥布林"
    type: combat
    target: forest_goblin
```

- [ ] **Step 7: Run all tests**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test`
Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/epic/engine/combat/ backend/src/main/java/com/epic/engine/mod/ModRegistry.java mods/
git commit -m "feat: add combat service, REST API, encounter loading, and scene trigger"
```

---

### Task 6: Combat Integration Test

**Files:**
- Create: `backend/src/test/java/com/epic/engine/combat/CombatIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
// backend/src/test/java/com/epic/engine/combat/CombatIntegrationTest.java
package com.epic.engine.combat;

import com.epic.engine.combat.CombatController.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CombatIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void fullCombatFlow() {
        // 1. Start combat via action endpoint
        var actionRequest = Map.of(
                "playerId", "player1",
                "type", "combat",
                "params", Map.of("encounter", "forest_goblin")
        );
        rest.postForObject("/api/action", actionRequest, Object.class);

        // 2. Get combat state
        ResponseEntity<CombatStateDto> stateResponse =
                rest.getForEntity("/api/combat/player1", CombatStateDto.class);
        assertThat(stateResponse.getStatusCode().is2xxSuccessful()).isTrue();
        CombatStateDto state = stateResponse.getBody();
        assertThat(state).isNotNull();
        assertThat(state.phase()).isEqualTo("COMMAND");
        assertThat(state.combatants()).hasSizeGreaterThanOrEqualTo(3);

        // 3. Get valid targets
        ResponseEntity<List<TargetDto>> targetsResponse = rest.exchange(
                "/api/combat/player1/targets/player1",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        assertThat(targetsResponse.getBody()).isNotEmpty();

        // 4. Submit commands (attack first available target)
        String targetId = targetsResponse.getBody().get(0).id();
        List<CommandDto> commands = List.of(
                new CommandDto("player1", "ATTACK", targetId)
        );
        ResponseEntity<RoundResultDto> resultResponse =
                rest.postForEntity("/api/combat/player1/commands", commands, RoundResultDto.class);
        assertThat(resultResponse.getBody()).isNotNull();
        assertThat(resultResponse.getBody().results()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test -Dtest=CombatIntegrationTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/epic/engine/combat/CombatIntegrationTest.java
git commit -m "test: add combat integration test (full flow)"
```

---

### Task 7: Frontend — Combat API and State

**Files:**
- Modify: `frontend/src/api/client.js`
- Create: `frontend/src/composables/useCombat.js`

- [ ] **Step 1: Add combat API calls to client.js**

Append to `frontend/src/api/client.js`:

```javascript
export async function getCombatState(playerId) {
    const response = await fetch(`${BASE_URL}/combat/${playerId}`)
    if (!response.ok) return null
    return response.json()
}

export async function submitCombatCommands(playerId, commands) {
    const response = await fetch(`${BASE_URL}/combat/${playerId}/commands`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(commands)
    })
    return response.json()
}

export async function getCombatTargets(playerId, combatantId) {
    const response = await fetch(`${BASE_URL}/combat/${playerId}/targets/${combatantId}`)
    if (!response.ok) return []
    return response.json()
}
```

- [ ] **Step 2: Create useCombat composable**

```javascript
// frontend/src/composables/useCombat.js
import { reactive } from 'vue'
import { getCombatState, submitCombatCommands, getCombatTargets } from '../api/client.js'

const combat = reactive({
    active: false,
    state: null,
    results: [],
    pendingCommands: [],
    validTargets: [],
    currentActorIndex: 0
})

export function useCombat() {
    async function enterCombat(playerId) {
        const state = await getCombatState(playerId)
        if (state) {
            combat.active = true
            combat.state = state
            combat.results = []
            combat.pendingCommands = []
            combat.currentActorIndex = 0
            await loadTargets(playerId)
        }
    }

    async function loadTargets(playerId) {
        const playerUnits = combat.state.combatants.filter(
            c => c.side === 'PLAYER' && c.alive
        )
        if (combat.currentActorIndex < playerUnits.length) {
            const actor = playerUnits[combat.currentActorIndex]
            combat.validTargets = await getCombatTargets(playerId, actor.id)
        }
    }

    function getCurrentActor() {
        if (!combat.state) return null
        const playerUnits = combat.state.combatants.filter(
            c => c.side === 'PLAYER' && c.alive
        )
        return playerUnits[combat.currentActorIndex] || null
    }

    function addCommand(actorId, type, targetId) {
        combat.pendingCommands.push({ actorId, type, targetId })
        combat.currentActorIndex++
    }

    function allCommandsIssued() {
        const playerUnits = combat.state.combatants.filter(
            c => c.side === 'PLAYER' && c.alive
        )
        return combat.pendingCommands.length >= playerUnits.length
    }

    async function executeRound(playerId) {
        const result = await submitCombatCommands(playerId, combat.pendingCommands)
        combat.results = result.results
        combat.state = result.state
        combat.pendingCommands = []
        combat.currentActorIndex = 0

        if (combat.state.phase === 'COMMAND') {
            await loadTargets(playerId)
        }
    }

    function exitCombat() {
        combat.active = false
        combat.state = null
        combat.results = []
        combat.pendingCommands = []
        combat.currentActorIndex = 0
        combat.validTargets = []
    }

    return {
        combat,
        enterCombat,
        loadTargets,
        getCurrentActor,
        addCommand,
        allCommandsIssued,
        executeRound,
        exitCombat
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/client.js frontend/src/composables/useCombat.js
git commit -m "feat: add combat API client and state management composable"
```

---

### Task 8: Frontend — Combat UI Components

**Files:**
- Create: `frontend/src/components/combat/BattleField.vue`
- Create: `frontend/src/components/combat/CommandPanel.vue`
- Create: `frontend/src/components/combat/CombatLogPanel.vue`
- Create: `frontend/src/components/combat/CombatStatusPanel.vue`
- Create: `frontend/src/components/combat/CombatView.vue`

- [ ] **Step 1: Create BattleField component (main panel — shows formations)**

```vue
<!-- frontend/src/components/combat/BattleField.vue -->
<template>
  <div class="battlefield">
    <div class="side player-side">
      <div class="side-label">【我方】</div>
      <div class="formation">
        <div class="row" v-for="row in ['BACK', 'MID', 'FRONT']" :key="'p-'+row">
          <span class="row-label">{{ rowLabel(row) }}</span>
          <span
            v-for="unit in getUnits('PLAYER', row)"
            :key="unit.id"
            class="unit"
            :class="{ dead: !unit.alive, active: unit.id === currentActorId }"
            :style="{ color: unitColor(unit) }"
          >{{ unit.name.charAt(0) }}</span>
        </div>
      </div>
    </div>
    <div class="versus">⚔</div>
    <div class="side enemy-side">
      <div class="side-label">【敌方】</div>
      <div class="formation">
        <div class="row" v-for="row in ['FRONT', 'MID', 'BACK']" :key="'e-'+row">
          <span class="row-label">{{ rowLabel(row) }}</span>
          <span
            v-for="unit in getUnits('ENEMY', row)"
            :key="unit.id"
            class="unit"
            :class="{ dead: !unit.alive, targeted: targetIds.includes(unit.id) }"
            :style="{ color: unitColor(unit) }"
          >{{ unit.name.charAt(0) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
    combatants: { type: Array, required: true },
    currentActorId: { type: String, default: '' },
    targetIds: { type: Array, default: () => [] }
})

function getUnits(side, row) {
    return (arguments[2] || []).length ? [] :
        // using props directly via template context
        []
}

function rowLabel(row) {
    return { FRONT: '前', MID: '中', BACK: '后' }[row]
}

function unitColor(unit) {
    if (!unit.alive) return '#555'
    const ratio = unit.hp / unit.maxHp
    if (ratio > 0.6) return '#4ecdc4'
    if (ratio > 0.3) return '#ffd93d'
    return '#e94560'
}
</script>

<script>
export default {
    methods: {
        getUnits(side, row) {
            return this.combatants.filter(
                c => c.side === side && c.row === row
            )
        }
    }
}
</script>

<style scoped>
.battlefield {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 2rem;
}

.side {
    flex: 1;
}

.side-label {
    text-align: center;
    margin-bottom: 0.8rem;
    font-size: 0.9em;
    color: #999;
}

.formation {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.row-label {
    color: #666;
    font-size: 0.8em;
    width: 1.5em;
}

.unit {
    display: inline-block;
    width: 2em;
    height: 2em;
    line-height: 2em;
    text-align: center;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    font-weight: bold;
}

.unit.dead {
    opacity: 0.3;
    text-decoration: line-through;
}

.unit.active {
    border-color: var(--link-color);
    box-shadow: 0 0 6px rgba(233, 69, 96, 0.4);
}

.unit.targeted {
    border-color: #ffd93d;
}

.versus {
    font-size: 1.5rem;
    color: #666;
}
</style>
```

Wait — that component has a confused pattern mixing Composition API and Options API. Let me fix it properly:

- [ ] **Step 1 (revised): Create BattleField component**

```vue
<!-- frontend/src/components/combat/BattleField.vue -->
<template>
  <div class="battlefield">
    <div class="side player-side">
      <div class="side-label">【我方】</div>
      <div class="formation">
        <div class="row" v-for="row in ['BACK', 'MID', 'FRONT']" :key="'p-'+row">
          <span class="row-label">{{ rowLabel(row) }}</span>
          <span
            v-for="unit in getUnits('PLAYER', row)"
            :key="unit.id"
            class="unit"
            :class="{ dead: !unit.alive, active: unit.id === currentActorId }"
            :style="{ color: unitColor(unit) }"
          >{{ unit.name.charAt(0) }}</span>
        </div>
      </div>
    </div>
    <div class="versus">⚔</div>
    <div class="side enemy-side">
      <div class="side-label">【敌方】</div>
      <div class="formation">
        <div class="row" v-for="row in ['FRONT', 'MID', 'BACK']" :key="'e-'+row">
          <span class="row-label">{{ rowLabel(row) }}</span>
          <span
            v-for="unit in getUnits('ENEMY', row)"
            :key="unit.id"
            class="unit"
            :class="{ dead: !unit.alive, targeted: targetIds.includes(unit.id) }"
            :style="{ color: unitColor(unit) }"
          >{{ unit.name.charAt(0) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
    combatants: { type: Array, required: true },
    currentActorId: { type: String, default: '' },
    targetIds: { type: Array, default: () => [] }
})

function getUnits(side, row) {
    return props.combatants.filter(c => c.side === side && c.row === row)
}

function rowLabel(row) {
    return { FRONT: '前', MID: '中', BACK: '后' }[row]
}

function unitColor(unit) {
    if (!unit.alive) return '#555'
    const ratio = unit.hp / unit.maxHp
    if (ratio > 0.6) return '#4ecdc4'
    if (ratio > 0.3) return '#ffd93d'
    return '#e94560'
}
</script>

<style scoped>
.battlefield {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 2rem;
}

.side { flex: 1; }

.side-label {
    text-align: center;
    margin-bottom: 0.8rem;
    font-size: 0.9em;
    color: #999;
}

.formation {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.row-label {
    color: #666;
    font-size: 0.8em;
    width: 1.5em;
}

.unit {
    display: inline-block;
    width: 2em;
    height: 2em;
    line-height: 2em;
    text-align: center;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    font-weight: bold;
}

.unit.dead { opacity: 0.3; text-decoration: line-through; }
.unit.active { border-color: var(--link-color); box-shadow: 0 0 6px rgba(233, 69, 96, 0.4); }
.unit.targeted { border-color: #ffd93d; }
.versus { font-size: 1.5rem; color: #666; }
</style>
```

- [ ] **Step 2: Create CommandPanel component**

```vue
<!-- frontend/src/components/combat/CommandPanel.vue -->
<template>
  <div class="command-panel">
    <div v-if="currentActor" class="commands">
      <div class="actor-name">{{ currentActor.name }} 的行动：</div>
      <div v-if="!choosingTarget" class="command-list">
        <div class="command-item" @click="startAttack">攻击</div>
        <div class="command-item" @click="defend">防御</div>
      </div>
      <div v-else class="target-list">
        <div class="target-header">选择目标：</div>
        <div
          v-for="target in targets"
          :key="target.id"
          class="command-item target-item"
          @click="selectTarget(target.id)"
        >{{ target.name }} ({{ target.hp }}/{{ target.maxHp }})</div>
        <div class="command-item cancel" @click="choosingTarget = false">取消</div>
      </div>
    </div>
    <div v-else-if="allDone" class="commands">
      <div class="command-item execute" @click="$emit('execute')">开始结算</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
    currentActor: { type: Object, default: null },
    targets: { type: Array, default: () => [] },
    allDone: { type: Boolean, default: false }
})

const emit = defineEmits(['command', 'execute'])
const choosingTarget = ref(false)

function startAttack() {
    choosingTarget.value = true
}

function selectTarget(targetId) {
    emit('command', { actorId: props.currentActor.id, type: 'ATTACK', targetId })
    choosingTarget.value = false
}

function defend() {
    emit('command', { actorId: props.currentActor.id, type: 'DEFEND', targetId: null })
}
</script>

<style scoped>
.command-panel { height: 100%; }

.actor-name {
    color: var(--link-color);
    margin-bottom: 0.8rem;
    font-size: 0.9em;
}

.command-list, .target-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
}

.target-header {
    color: #999;
    font-size: 0.85em;
    margin-bottom: 0.3rem;
}

.command-item {
    padding: 0.4rem 0.8rem;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    transition: border-color 0.2s, background-color 0.2s;
}

.command-item:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.command-item.execute {
    border-color: var(--link-color);
    color: var(--link-color);
    text-align: center;
    font-weight: bold;
}

.command-item.cancel {
    color: #999;
    border-color: #555;
}
</style>
```

- [ ] **Step 3: Create CombatLogPanel component**

```vue
<!-- frontend/src/components/combat/CombatLogPanel.vue -->
<template>
  <div class="combat-log" ref="logEl">
    <div class="round-header" v-if="round">第 {{ round }} 回合</div>
    <div v-for="(result, i) in results" :key="i" class="log-entry">
      <span v-if="result.action === 'attack'" :style="{ color: '#e0e0e0' }">
        <span style="color: #4ecdc4">{{ result.actorName }}</span>
        对
        <span style="color: #ffd93d">{{ result.targetName }}</span>
        发动攻击，造成
        <span style="color: #e94560">{{ result.damage }}</span>
        点伤害<span v-if="result.targetDefeated" style="color: #e94560">（击败！）</span>
      </span>
      <span v-else-if="result.action === 'defend'">
        <span style="color: #4ecdc4">{{ result.actorName }}</span>
        <span style="color: #999"> 进入防御姿态</span>
      </span>
    </div>
    <div v-if="phase === 'VICTORY'" class="result-msg victory">战斗胜利！</div>
    <div v-if="phase === 'DEFEAT'" class="result-msg defeat">战斗失败...</div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
    results: { type: Array, default: () => [] },
    round: { type: Number, default: 1 },
    phase: { type: String, default: 'COMMAND' }
})

const logEl = ref(null)

watch(() => props.results.length, async () => {
    await nextTick()
    if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
})
</script>

<style scoped>
.combat-log {
    height: 100%;
    overflow-y: auto;
    font-size: 0.85em;
    line-height: 1.8;
}

.round-header {
    color: #666;
    border-bottom: 1px solid var(--panel-border-color);
    margin-bottom: 0.3rem;
    padding-bottom: 0.2rem;
}

.log-entry { margin-bottom: 0.2rem; }

.result-msg {
    margin-top: 1rem;
    font-weight: bold;
    font-size: 1.1em;
}

.victory { color: #4ecdc4; }
.defeat { color: #e94560; }
</style>
```

- [ ] **Step 4: Create CombatStatusPanel component**

```vue
<!-- frontend/src/components/combat/CombatStatusPanel.vue -->
<template>
  <div class="combat-status" v-if="actor">
    <div class="status-name">{{ actor.name }}</div>
    <div class="status-row">
      <span class="label">HP</span>
      <span class="value" :style="{ color: hpColor }">{{ actor.hp }} / {{ actor.maxHp }}</span>
    </div>
    <div class="status-row">
      <span class="label">攻击</span>
      <span class="value">{{ actor.attack }}</span>
    </div>
    <div class="status-row">
      <span class="label">防御</span>
      <span class="value">{{ actor.defense }}</span>
    </div>
    <div class="status-row">
      <span class="label">速度</span>
      <span class="value">{{ actor.speed }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
    actor: { type: Object, default: null }
})

const hpColor = computed(() => {
    if (!props.actor) return '#e0e0e0'
    const ratio = props.actor.hp / props.actor.maxHp
    if (ratio > 0.6) return '#4ecdc4'
    if (ratio > 0.3) return '#ffd93d'
    return '#e94560'
})
</script>

<style scoped>
.combat-status { font-size: 0.9em; }

.status-name {
    color: var(--link-color);
    font-weight: bold;
    margin-bottom: 0.6rem;
}

.status-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 0.3rem;
    padding: 0.2rem 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.label { color: #999; }
.value { color: var(--text-color); }
</style>
```

- [ ] **Step 5: Create CombatView (top-level combat layout)**

```vue
<!-- frontend/src/components/combat/CombatView.vue -->
<template>
  <div class="combat-grid">
    <div class="panel-main">
      <TabPanel :tabs="[{ id: 'battle', label: '战场' }]" default-tab="battle">
        <template #battle>
          <BattleField
            :combatants="combat.state?.combatants || []"
            :current-actor-id="currentActor?.id || ''"
            :target-ids="combat.validTargets.map(t => t.id)"
          />
        </template>
      </TabPanel>
    </div>

    <div class="panel-cmd">
      <TabPanel :tabs="[{ id: 'command', label: '指令' }]" default-tab="command">
        <template #command>
          <CommandPanel
            :current-actor="currentActor"
            :targets="combat.validTargets"
            :all-done="allCommandsIssued()"
            @command="onCommand"
            @execute="onExecute"
          />
        </template>
      </TabPanel>
    </div>

    <div class="panel-log">
      <TabPanel :tabs="[{ id: 'log', label: '战斗' }]" default-tab="log">
        <template #log>
          <CombatLogPanel
            :results="combat.results"
            :round="combat.state?.round || 1"
            :phase="combat.state?.phase || 'COMMAND'"
          />
        </template>
      </TabPanel>
    </div>

    <div class="panel-status">
      <TabPanel :tabs="[{ id: 'status', label: '状态' }]" default-tab="status">
        <template #status>
          <CombatStatusPanel :actor="currentActor" />
        </template>
      </TabPanel>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TabPanel from '../TabPanel.vue'
import BattleField from './BattleField.vue'
import CommandPanel from './CommandPanel.vue'
import CombatLogPanel from './CombatLogPanel.vue'
import CombatStatusPanel from './CombatStatusPanel.vue'
import { useCombat } from '../../composables/useCombat.js'
import { useGameState } from '../../composables/useGameState.js'

const { combat, getCurrentActor, addCommand, allCommandsIssued, executeRound, loadTargets, exitCombat } = useCombat()
const { state, initialize } = useGameState()

const currentActor = computed(() => getCurrentActor())

async function onCommand(cmd) {
    addCommand(cmd.actorId, cmd.type, cmd.targetId)
    if (!allCommandsIssued()) {
        await loadTargets(state.playerId)
    }
}

async function onExecute() {
    await executeRound(state.playerId)
    if (combat.state?.phase === 'VICTORY' || combat.state?.phase === 'DEFEAT') {
        setTimeout(() => {
            exitCombat()
            initialize()
        }, 2000)
    }
}
</script>

<style scoped>
.combat-grid {
    display: grid;
    grid-template-columns: 3fr 1fr;
    grid-template-rows: 2fr 1fr;
    gap: 0.5rem;
    height: 100%;
}

.panel-main { grid-column: 1; grid-row: 1; }
.panel-cmd { grid-column: 2; grid-row: 1; }
.panel-log { grid-column: 1; grid-row: 2; }
.panel-status { grid-column: 2; grid-row: 2; }
</style>
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/combat/
git commit -m "feat: add combat UI components (battlefield, commands, log, status)"
```

---

### Task 9: Frontend — Integrate Combat View into App

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/composables/useGameState.js`

- [ ] **Step 1: Update useGameState to detect combat start**

Replace `frontend/src/composables/useGameState.js`:

```javascript
import { reactive } from 'vue'
import { getPlayerState, fetchScene, performAction, getCombatState } from '../api/client.js'

const state = reactive({
    playerId: 'player1',
    currentScene: null,
    loading: false
})

export function useGameState() {
    async function initialize() {
        state.loading = true
        const playerState = await getPlayerState(state.playerId)
        const scene = await fetchScene(playerState.currentScene)
        state.currentScene = scene
        state.loading = false
    }

    async function doAction(type, params) {
        state.loading = true
        const response = await performAction(state.playerId, type, params)
        return response
    }

    async function refreshScene(sceneId) {
        const scene = await fetchScene(sceneId)
        state.currentScene = scene
    }

    async function checkCombat() {
        const combatState = await getCombatState(state.playerId)
        return combatState !== null
    }

    return { state, initialize, doAction, refreshScene, checkCombat }
}
```

- [ ] **Step 2: Update App.vue to switch between exploration and combat**

Replace `frontend/src/App.vue`:

```vue
<template>
  <div class="app-container">
    <CombatView v-if="combat.active" />
    <div v-else class="game-grid">
      <!-- 主窗口 (3×2) -->
      <div class="panel-main">
        <TabPanel :tabs="mainTabs" default-tab="scene">
          <template #scene>
            <ScenePanel />
          </template>
          <template #map>
            <div class="placeholder-content">大地图（待实现）</div>
          </template>
        </TabPanel>
      </div>

      <!-- 功能页面 (1×2) -->
      <div class="panel-func">
        <TabPanel :tabs="funcTabs" default-tab="status">
          <template #status>
            <StatusPanel :current-scene="state.currentScene?.id" />
          </template>
          <template #settings>
            <SettingsPanel :visible="true" />
          </template>
        </TabPanel>
      </div>

      <!-- 日志 (3×1) -->
      <div class="panel-log">
        <TabPanel :tabs="logTabs" default-tab="all">
          <template #all>
            <GameLog :entries="logEntries" />
          </template>
        </TabPanel>
      </div>

      <!-- 额外区域 (1×1) -->
      <div class="panel-extra">
        <TabPanel :tabs="extraTabs" default-tab="placeholder">
          <template #placeholder>
            <div class="placeholder-content">待规划</div>
          </template>
        </TabPanel>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import TabPanel from './components/TabPanel.vue'
import ScenePanel from './components/ScenePanel.vue'
import StatusPanel from './components/StatusPanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import GameLog from './components/GameLog.vue'
import CombatView from './components/combat/CombatView.vue'
import { useGameState } from './composables/useGameState.js'
import { useSettings } from './composables/useSettings.js'
import { useCombat } from './composables/useCombat.js'

const { state, initialize } = useGameState()
const { combat } = useCombat()
useSettings()

const logEntries = reactive([])

const mainTabs = [
  { id: 'scene', label: '场景' },
  { id: 'map', label: '地图' }
]

const funcTabs = [
  { id: 'status', label: '人物' },
  { id: 'settings', label: '设置' }
]

const logTabs = [
  { id: 'all', label: '全部' }
]

const extraTabs = [
  { id: 'placeholder', label: '...' }
]

onMounted(() => {
  initialize()
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  padding: 0.5rem;
  box-sizing: border-box;
}

.game-grid {
  display: grid;
  grid-template-columns: 3fr 1fr;
  grid-template-rows: 2fr 1fr;
  gap: 0.5rem;
  height: 100%;
}

.panel-main { grid-column: 1; grid-row: 1; }
.panel-func { grid-column: 2; grid-row: 1; }
.panel-log { grid-column: 1; grid-row: 2; }
.panel-extra { grid-column: 2; grid-row: 2; }

.placeholder-content {
  color: #666;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
```

- [ ] **Step 3: Update ScenePanel to trigger combat entry**

Modify `frontend/src/components/ScenePanel.vue` — update the `handleAction` function:

```javascript
import { useCombat } from '../composables/useCombat.js'

const { enterCombat } = useCombat()

async function handleAction(action) {
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}
```

The full ScenePanel.vue becomes:

```vue
<template>
  <div class="scene-panel" v-if="scene">
    <div class="scene-description">
      <TextRenderer :segments="scene.description" />
    </div>
    <div class="scene-actions">
      <ActionLink
        v-for="action in scene.actions"
        :key="action.id"
        :action="action"
        @execute="handleAction(action)"
      />
    </div>
  </div>
  <div class="scene-panel loading" v-else>
    <span style="color: #666">加载中...</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TextRenderer from './TextRenderer.vue'
import ActionLink from './ActionLink.vue'
import { useGameState } from '../composables/useGameState.js'
import { usePanelRefresh } from '../composables/usePanelRefresh.js'
import { useCombat } from '../composables/useCombat.js'

const { state, doAction } = useGameState()
const { handleRefresh } = usePanelRefresh()
const { enterCombat } = useCombat()

const scene = computed(() => state.currentScene)

async function handleAction(action) {
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}
</script>

<style scoped>
.scene-panel { height: 100%; }
.scene-description { margin-bottom: 1.5rem; }
.scene-actions { border-top: 1px solid var(--panel-border-color); padding-top: 1rem; }
</style>
```

- [ ] **Step 4: Verify frontend builds**

Run: `cd /Volumes/workplace/epic/frontend && npm run build`
Expected: Build succeeds with no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/
git commit -m "feat: integrate combat view into app with exploration/combat switching"
```

---

### Task 10: End-to-End Verification

**Files:** None new — verification task.

- [ ] **Step 1: Run all backend tests**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn test`
Expected: All tests PASS (including new combat tests).

- [ ] **Step 2: Start backend**

Run: `cd /Volumes/workplace/epic/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn spring-boot:run`

- [ ] **Step 3: Start frontend**

Run: `cd /Volumes/workplace/epic/frontend && npm run dev`

- [ ] **Step 4: Verify in browser at http://localhost:5173**

Checklist:
- [ ] Village square loads normally (exploration mode)
- [ ] Navigate to forest entrance — see "迎战哥布林" action
- [ ] Click "迎战哥布林" — UI switches to combat view
- [ ] See battlefield with player unit (前排) and enemies (前排: 哥布林, 后排: 弓手)
- [ ] Command panel shows current actor's options
- [ ] Select "攻击" → target list appears
- [ ] Select target → moves to next actor (or shows "开始结算")
- [ ] Click "开始结算" → combat log shows results
- [ ] Continue until victory/defeat
- [ ] After battle ends, returns to exploration mode

- [ ] **Step 5: Commit any fixes**

```bash
git add -A
git commit -m "fix: end-to-end combat integration fixes"
```

---

## Summary

This plan delivers:
1. **Combat data models** — Combatant, Position (3×3 grid), Side, CombatState
2. **Target resolution** — nearest row logic for basic attacks
3. **Combat engine** — speed-ordered turn resolution with attack/defend
4. **REST API** — start combat, get state, submit commands, get targets
5. **Scene trigger** — "combat" action type starts encounters from scenes
6. **Encounter loading** — YAML encounter definitions via mod system
7. **Combat UI** — 4-panel layout (battlefield, commands, log, status)
8. **App integration** — switches between exploration and combat views

**Future plans (not in scope):**
- Plan: Skills system (skill definitions, MP/resource costs, varied ranges)
- Plan: Combo systems (state tags, resource accumulation, skill chains)
- Plan: Advanced AI (pattern-based, per-encounter behaviors)
- Plan: Items in combat
- Plan: Multi-character party
