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
        // Mage (speed 8) goes first
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

        // With defend: goblin (5-5)/2=min1, archer (7-5)/2=1 → 2 damage
        // Without defend would be: (5-5)=min1 + (7-5)=2 → 3 damage
        assertThat(hpBefore - hpAfter).isEqualTo(2);
    }

    @Test
    void deadUnitsDoNotAct() {
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
