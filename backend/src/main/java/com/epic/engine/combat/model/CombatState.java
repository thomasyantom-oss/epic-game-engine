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
