package com.epic.engine.map;

import java.util.List;

public record MapData(
        String id,
        String name,
        int width,
        int height,
        char[][] terrain,
        List<PointOfInterest> pois,
        int spawnX,
        int spawnY
) {
    public record PointOfInterest(String id, int x, int y, String type, String target, String label) {}
}
