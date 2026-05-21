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

        List<CombatCommand> enemyCommands = generateEnemyCommands(state);

        state.setPhase(CombatPhase.RESOLVE);
        List<CombatResult> results = engine.resolveRound(state, playerCommands, enemyCommands);

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
