package com.epic.engine.map;

import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;
    private final PlayerStateRepository playerStateRepository;

    public MapController(MapService mapService, PlayerStateRepository playerStateRepository) {
        this.mapService = mapService;
        this.playerStateRepository = playerStateRepository;
    }

    @GetMapping("/{mapId}")
    public ResponseEntity<Map<String, Object>> getMap(@PathVariable String mapId) {
        MapData map = mapService.getMapData(mapId);
        if (map == null) {
            return ResponseEntity.notFound().build();
        }
        Map<Character, TerrainDefinition> terrains = mapService.getTerrains();

        List<String> terrainRows = new ArrayList<>();
        for (char[] row : map.terrain()) {
            terrainRows.add(new String(row));
        }

        List<Map<String, Object>> terrainDefs = new ArrayList<>();
        for (var entry : terrains.entrySet()) {
            TerrainDefinition td = entry.getValue();
            terrainDefs.add(Map.of(
                    "char", String.valueOf(entry.getKey()),
                    "id", td.id(),
                    "requires", td.requires(),
                    "color", td.color(),
                    "textColor", td.textColor(),
                    "moveCost", td.moveCost()
            ));
        }

        List<Map<String, Object>> pois = map.pois().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.id(), "x", p.x(), "y", p.y(),
                        "type", p.type(), "target", p.target(), "label", p.label()
                )).toList();

        return ResponseEntity.ok(Map.of(
                "id", map.id(),
                "name", map.name(),
                "width", map.width(),
                "height", map.height(),
                "terrain", terrainRows,
                "terrains", terrainDefs,
                "pois", pois,
                "spawnX", map.spawnX(),
                "spawnY", map.spawnY()
        ));
    }

    @PostMapping("/move")
    public MapService.MoveResult move(@RequestBody MoveRequest request) {
        return mapService.move(request.playerId(), request.direction());
    }

    @PostMapping("/moveTo")
    public MapService.PathResult moveTo(@RequestBody MoveToRequest request) {
        return mapService.moveTo(request.playerId(), request.targetX(), request.targetY());
    }

    @GetMapping("/position/{playerId}")
    public ResponseEntity<Map<String, Object>> getPosition(@PathVariable String playerId) {
        Optional<PlayerState> state = playerStateRepository.findByPlayerId(playerId);
        if (state.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlayerState s = state.get();
        return ResponseEntity.ok(Map.of(
                "mapId", s.getMapId() != null ? s.getMapId() : "world_map",
                "x", s.getMapX(),
                "y", s.getMapY()
        ));
    }

    record MoveRequest(String playerId, String direction) {}
    record MoveToRequest(String playerId, int targetX, int targetY) {}
}
