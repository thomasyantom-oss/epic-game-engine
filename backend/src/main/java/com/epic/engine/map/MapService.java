package com.epic.engine.map;

import com.epic.engine.mod.ModRegistry;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MapService {

    private final ModRegistry modRegistry;
    private final PlayerStateRepository playerStateRepository;

    public MapService(ModRegistry modRegistry, PlayerStateRepository playerStateRepository) {
        this.modRegistry = modRegistry;
        this.playerStateRepository = playerStateRepository;
    }

    public MoveResult move(String playerId, String direction) {
        PlayerState state = getOrCreateState(playerId);
        MapData map = modRegistry.getMap(state.getMapId()).orElse(null);
        if (map == null) {
            return MoveResult.fail("地图不存在");
        }

        int dx = 0, dy = 0;
        switch (direction.toUpperCase()) {
            case "UP" -> dy = -1;
            case "DOWN" -> dy = 1;
            case "LEFT" -> dx = -1;
            case "RIGHT" -> dx = 1;
            default -> { return MoveResult.fail("无效方向"); }
        }

        int newX = state.getMapX() + dx;
        int newY = state.getMapY() + dy;

        if (!isPassable(map, newX, newY)) {
            return MoveResult.fail("无法通行");
        }

        state.setMapX(newX);
        state.setMapY(newY);
        playerStateRepository.save(state);

        MapData.PointOfInterest poi = findPoi(map, newX, newY);
        return MoveResult.success(newX, newY, poi);
    }

    public PathResult moveTo(String playerId, int targetX, int targetY) {
        PlayerState state = getOrCreateState(playerId);
        MapData map = modRegistry.getMap(state.getMapId()).orElse(null);
        if (map == null) {
            return PathResult.fail("地图不存在");
        }

        boolean[][] passable = buildPassableGrid(map);
        List<int[]> rawPath = Pathfinder.findPath(passable, state.getMapX(), state.getMapY(), targetX, targetY);
        if (rawPath == null) {
            return PathResult.fail("无法到达目标");
        }

        List<PathResult.Position> path = rawPath.stream()
                .map(p -> new PathResult.Position(p[0], p[1]))
                .toList();

        // Find first POI along path (excluding start)
        MapData.PointOfInterest firstPoi = null;
        int poiIndex = -1;
        for (int i = 1; i < path.size(); i++) {
            MapData.PointOfInterest poi = findPoi(map, path.get(i).x(), path.get(i).y());
            if (poi != null) {
                firstPoi = poi;
                poiIndex = i;
                break;
            }
        }

        // If POI found, truncate path to stop there
        if (firstPoi != null) {
            path = path.subList(0, poiIndex + 1);
        }

        return PathResult.success(path, firstPoi);
    }

    public MapData getMapData(String mapId) {
        return modRegistry.getMap(mapId).orElse(null);
    }

    public Map<Character, TerrainDefinition> getTerrains() {
        return modRegistry.getTerrains();
    }

    private boolean isPassable(MapData map, int x, int y) {
        if (x < 0 || x >= map.width() || y < 0 || y >= map.height()) {
            return false;
        }
        char terrainChar = map.terrain()[y][x];
        TerrainDefinition terrain = modRegistry.getTerrains().get(terrainChar);
        if (terrain == null) return true;
        return terrain.requires().isEmpty();
    }

    private boolean[][] buildPassableGrid(MapData map) {
        boolean[][] grid = new boolean[map.height()][map.width()];
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                grid[y][x] = isPassable(map, x, y);
            }
        }
        return grid;
    }

    private MapData.PointOfInterest findPoi(MapData map, int x, int y) {
        return map.pois().stream()
                .filter(p -> p.x() == x && p.y() == y)
                .findFirst()
                .orElse(null);
    }

    private PlayerState getOrCreateState(String playerId) {
        return playerStateRepository.findByPlayerId(playerId)
                .orElseGet(() -> {
                    PlayerState s = new PlayerState(playerId, "village_square");
                    playerStateRepository.save(s);
                    return s;
                });
    }

    public record MoveResult(boolean success, String message, int newX, int newY, MapData.PointOfInterest poi) {
        static MoveResult success(int x, int y, MapData.PointOfInterest poi) {
            return new MoveResult(true, null, x, y, poi);
        }
        static MoveResult fail(String msg) {
            return new MoveResult(false, msg, -1, -1, null);
        }
    }

    public record PathResult(boolean success, String message, List<Position> path, MapData.PointOfInterest poi) {
        public record Position(int x, int y) {}
        static PathResult success(List<Position> path, MapData.PointOfInterest poi) {
            return new PathResult(true, null, path, poi);
        }
        static PathResult fail(String msg) {
            return new PathResult(false, msg, List.of(), null);
        }
    }
}
