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
