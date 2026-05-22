package com.epic.engine.combat;

import com.epic.engine.combat.command.CombatCommand;
import com.epic.engine.combat.command.CommandType;
import com.epic.engine.combat.command.TargetResolver;
import com.epic.engine.combat.engine.CombatResult;
import com.epic.engine.combat.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/{playerId}/end")
    public ResponseEntity<Void> endCombat(@PathVariable String playerId) {
        combatService.endCombat(playerId);
        return ResponseEntity.ok().build();
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
