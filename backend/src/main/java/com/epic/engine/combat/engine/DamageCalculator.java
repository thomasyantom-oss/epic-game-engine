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
