package com.epic.engine.map;

import java.util.List;

public record TerrainDefinition(
        String id,
        char character,
        List<String> requires,
        String color,
        String textColor,
        double moveCost
) {
    public TerrainDefinition {
        if (requires == null) requires = List.of();
        if (moveCost <= 0) moveCost = 1.0;
    }
}
