package com.epic.engine.sim;

/** Single fight result. rounds is the number of resolved rounds. */
public record FightResult(
        FightOutcome outcome,
        int rounds,
        double playerHpFrac,
        double enemyHpFrac,
        FightTrace trace) {

    public FightResult(FightOutcome outcome, int rounds, double playerHpFrac, double enemyHpFrac) {
        this(outcome, rounds, playerHpFrac, enemyHpFrac, null);
    }
}
