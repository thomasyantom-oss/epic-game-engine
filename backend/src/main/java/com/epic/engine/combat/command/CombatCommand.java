package com.epic.engine.combat.command;

public record CombatCommand(String actorId, CommandType type, String targetId) {}
