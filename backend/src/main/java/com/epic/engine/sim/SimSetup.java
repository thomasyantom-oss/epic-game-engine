package com.epic.engine.sim;

import java.util.List;
import java.util.Map;

public record SimSetup(
        String playerClass,
        int playerLevel,
        String encounterId,
        PolicyKind policyKind,
        List<String> scriptedSteps,
        int maxTurns,
        List<Map<String, Object>> sources) {

    public SimSetup(String playerClass, int playerLevel, String encounterId,
                    PolicyKind policyKind, List<String> scriptedSteps, int maxTurns) {
        this(playerClass, playerLevel, encounterId, policyKind, scriptedSteps, maxTurns, List.of());
    }

    Policy newPlayerPolicy() {
        return policyKind == PolicyKind.SCRIPTED
                ? new ScriptedPolicy(scriptedSteps)
                : new HeuristicPolicy();
    }
}
