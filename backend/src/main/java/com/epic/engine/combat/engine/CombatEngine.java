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
