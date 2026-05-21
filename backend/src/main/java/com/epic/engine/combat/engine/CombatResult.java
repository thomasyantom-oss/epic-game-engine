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
