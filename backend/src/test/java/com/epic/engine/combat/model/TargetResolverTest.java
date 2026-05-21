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
