# World Map Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render a 10×10 colored grid world map in the map panel with player-centered viewport, keyboard/button/click movement, and A* pathfinding with animated step-by-step traversal.

**Architecture:** Backend loads terrain definitions and map data from Mod YAML, exposes REST APIs for map data retrieval and movement (single-step + pathfinding). Frontend renders a CSS Grid with colored cells and Chinese characters, handles three input modes, and animates pathfinding with interrupt support.

**Tech Stack:** Java 21 / Spring Boot 3 / SnakeYAML (backend), Vue 3 / Vite / CSS Grid (frontend)

---

## File Structure

### Backend (new files)
- `backend/src/main/java/com/epic/engine/map/TerrainDefinition.java` — terrain data record (id, char, requires, color, textColor, moveCost)
- `backend/src/main/java/com/epic/engine/map/MapData.java` — map model (grid, POIs, spawn, dimensions)
- `backend/src/main/java/com/epic/engine/map/MapService.java` — load maps/terrains, movement logic, pathfinding
- `backend/src/main/java/com/epic/engine/map/MapController.java` — REST endpoints
- `backend/src/main/java/com/epic/engine/map/Pathfinder.java` — A* implementation
- `backend/src/test/java/com/epic/engine/map/PathfinderTest.java` — pathfinder unit tests
- `backend/src/test/java/com/epic/engine/map/MapServiceTest.java` — service unit tests
- `backend/src/test/java/com/epic/engine/map/MapIntegrationTest.java` — full API tests

### Backend (modified files)
- `backend/src/main/java/com/epic/engine/mod/ModRegistry.java` — add maps + terrains loading
- `backend/src/main/java/com/epic/engine/save/PlayerState.java` — add mapId, mapX, mapY fields

### Frontend (new files)
- `frontend/src/components/MapGrid.vue` — grid renderer + click handler
- `frontend/src/composables/useMap.js` — map state, movement, pathfinding animation

### Frontend (modified files)
- `frontend/src/App.vue` — wire MapGrid into map tab
- `frontend/src/api/client.js` — add map API calls
- `frontend/src/components/NavigationPanel.vue` — add direction buttons when in map mode
- `frontend/src/components/SettingsPanel.vue` — add map size slider
- `frontend/src/composables/useSettings.js` — add mapSize setting

### Content (new files)
- `mods/base/terrains.yaml` — terrain type definitions
- `mods/base/maps/world_map.yaml` — 10×10 world map

---

### Task 1: Terrain and Map Data Models

**Files:**
- Create: `backend/src/main/java/com/epic/engine/map/TerrainDefinition.java`
- Create: `backend/src/main/java/com/epic/engine/map/MapData.java`

- [ ] **Step 1: Create TerrainDefinition record**

```java
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
```

- [ ] **Step 2: Create MapData record**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/epic/engine/map/TerrainDefinition.java backend/src/main/java/com/epic/engine/map/MapData.java
git commit -m "feat: add terrain and map data models"
```

---

### Task 2: Mod Content — Terrain Definitions and World Map

**Files:**
- Create: `mods/base/terrains.yaml`
- Create: `mods/base/maps/world_map.yaml`

- [ ] **Step 1: Create terrain definitions YAML**

```yaml
# mods/base/terrains.yaml
terrains:
  林:
    id: forest
    requires: []
    color: "#2d6a4f"
    text-color: "#a0d0b0"
    move-cost: 1.2
  草:
    id: grass
    requires: []
    color: "#40916c"
    text-color: "#c0e8d0"
    move-cost: 1.0
  路:
    id: road
    requires: []
    color: "#6b705c"
    text-color: "#b0b5a0"
    move-cost: 0.5
  沙:
    id: sand
    requires: []
    color: "#a68a64"
    text-color: "#d4c4a0"
    move-cost: 1.5
  水:
    id: water
    requires: [swim]
    color: "#4a90d9"
    text-color: "#cce4ff"
    move-cost: 2.0
  城:
    id: town
    requires: []
    color: "#ffd93d"
    text-color: "#333333"
    move-cost: 0.5
  山:
    id: mountain
    requires: [fly]
    color: "#8b7355"
    text-color: "#d4c4a0"
    move-cost: 3.0
```

- [ ] **Step 2: Create world map YAML**

```yaml
# mods/base/maps/world_map.yaml
id: world_map
name: "主世界"
width: 10
height: 10
terrain:
  - "林林草草路路草水水林"
  - "林草草路路路草水水水"
  - "草草路路城城路草水草"
  - "草路路沙城城路草草草"
  - "草草沙沙路路草草林林"
  - "沙沙沙草路草草林林林"
  - "草草草草路林林林水水"
  - "草林林林路山山水水水"
  - "林林山山路山水水水水"
  - "林山山山路水水水水水"
pois:
  - id: village
    x: 4
    y: 2
    type: enter
    target: village_square
    label: "进入村庄"
spawn:
  x: 4
  y: 3
```

- [ ] **Step 3: Commit**

```bash
git add mods/base/terrains.yaml mods/base/maps/world_map.yaml
git commit -m "content: add terrain definitions and 10x10 world map"
```

---

### Task 3: ModRegistry — Load Terrains and Maps

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/mod/ModRegistry.java`
- Test: `backend/src/test/java/com/epic/engine/map/MapServiceTest.java`

- [ ] **Step 1: Write failing test for terrain loading**

Create `backend/src/test/java/com/epic/engine/map/MapServiceTest.java`:

```java
package com.epic.engine.map;

import com.epic.engine.mod.ModRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MapServiceTest {

    @Autowired
    ModRegistry modRegistry;

    @Test
    void terrainsLoadedFromMod() {
        Map<Character, TerrainDefinition> terrains = modRegistry.getTerrains();
        assertThat(terrains).isNotEmpty();
        assertThat(terrains).containsKey('林');
        assertThat(terrains.get('林').id()).isEqualTo("forest");
        assertThat(terrains.get('林').color()).isEqualTo("#2d6a4f");
        assertThat(terrains.get('水').requires()).containsExactly("swim");
    }

    @Test
    void mapLoadedFromMod() {
        Optional<MapData> map = modRegistry.getMap("world_map");
        assertThat(map).isPresent();
        assertThat(map.get().width()).isEqualTo(10);
        assertThat(map.get().height()).isEqualTo(10);
        assertThat(map.get().terrain()[0][0]).isEqualTo('林');
        assertThat(map.get().pois()).hasSize(1);
        assertThat(map.get().spawnX()).isEqualTo(4);
        assertThat(map.get().spawnY()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=MapServiceTest -pl .`
Expected: FAIL — `getTerrains()` and `getMap()` don't exist yet

- [ ] **Step 3: Add terrain and map loading to ModRegistry**

Add fields and methods to `ModRegistry.java`:

```java
// New fields
private final Map<Character, TerrainDefinition> terrains = new LinkedHashMap<>();
private final Map<String, MapData> maps = new LinkedHashMap<>();

// New public methods
public Map<Character, TerrainDefinition> getTerrains() {
    return Collections.unmodifiableMap(terrains);
}

public Optional<MapData> getMap(String mapId) {
    return Optional.ofNullable(maps.get(mapId));
}

public Collection<String> getAllMapIds() {
    return Collections.unmodifiableSet(maps.keySet());
}
```

Add to `loadModContent(ModDescriptor mod)`:

```java
// Load terrains.yaml
Path terrainsFile = mod.path().resolve("terrains.yaml");
if (Files.exists(terrainsFile)) {
    Yaml yaml = new Yaml();
    try (InputStream is = Files.newInputStream(terrainsFile)) {
        Map<String, Object> data = yaml.load(is);
        Map<String, Map<String, Object>> terrainDefs = (Map<String, Map<String, Object>>) data.get("terrains");
        if (terrainDefs != null) {
            for (var entry : terrainDefs.entrySet()) {
                char ch = entry.getKey().charAt(0);
                Map<String, Object> def = entry.getValue();
                terrains.put(ch, new TerrainDefinition(
                        (String) def.get("id"),
                        ch,
                        def.containsKey("requires") ? (List<String>) def.get("requires") : List.of(),
                        (String) def.get("color"),
                        (String) def.get("text-color"),
                        def.containsKey("move-cost") ? ((Number) def.get("move-cost")).doubleValue() : 1.0
                ));
            }
        }
    }
}

// Load maps
Path mapsDir = mod.path().resolve("maps");
if (Files.isDirectory(mapsDir)) {
    Yaml yaml = new Yaml();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(mapsDir, "*.yaml")) {
        for (Path mapFile : stream) {
            try (InputStream is = Files.newInputStream(mapFile)) {
                Map<String, Object> data = yaml.load(is);
                String id = (String) data.get("id");
                String name = (String) data.get("name");
                int width = (int) data.get("width");
                int height = (int) data.get("height");
                List<String> terrainRows = (List<String>) data.get("terrain");
                char[][] grid = new char[height][width];
                for (int y = 0; y < height; y++) {
                    String row = terrainRows.get(y);
                    for (int x = 0; x < width; x++) {
                        grid[y][x] = row.charAt(x);
                    }
                }
                List<Map<String, Object>> poisRaw = (List<Map<String, Object>>) data.getOrDefault("pois", List.of());
                List<MapData.PointOfInterest> pois = poisRaw.stream()
                        .map(p -> new MapData.PointOfInterest(
                                (String) p.get("id"),
                                (int) p.get("x"),
                                (int) p.get("y"),
                                (String) p.get("type"),
                                (String) p.get("target"),
                                (String) p.get("label")
                        )).toList();
                Map<String, Object> spawn = (Map<String, Object>) data.get("spawn");
                maps.put(id, new MapData(id, name, width, height, grid, pois,
                        (int) spawn.get("x"), (int) spawn.get("y")));
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=MapServiceTest -pl .`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/mod/ModRegistry.java backend/src/main/java/com/epic/engine/map/ backend/src/test/java/com/epic/engine/map/MapServiceTest.java
git commit -m "feat: load terrain definitions and maps from mod YAML"
```

---

### Task 4: PlayerState — Add Map Position

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/save/PlayerState.java`

- [ ] **Step 1: Add mapId, mapX, mapY fields to PlayerState**

```java
package com.epic.engine.save;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PlayerState {

    @Id
    private String playerId;
    private String currentScene;
    private String mapId;
    private int mapX;
    private int mapY;

    public PlayerState() {}

    public PlayerState(String playerId, String currentScene) {
        this.playerId = playerId;
        this.currentScene = currentScene;
        this.mapId = "world_map";
        this.mapX = 4;
        this.mapY = 3;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getCurrentScene() { return currentScene; }
    public void setCurrentScene(String currentScene) { this.currentScene = currentScene; }
    public String getMapId() { return mapId; }
    public void setMapId(String mapId) { this.mapId = mapId; }
    public int getMapX() { return mapX; }
    public void setMapX(int mapX) { this.mapX = mapX; }
    public int getMapY() { return mapY; }
    public void setMapY(int mapY) { this.mapY = mapY; }
}
```

- [ ] **Step 2: Run existing tests to verify nothing broke**

Run: `cd backend && mvn test`
Expected: All existing tests PASS (H2 with ddl-auto=create-drop handles new columns)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/epic/engine/save/PlayerState.java
git commit -m "feat: add map position fields to PlayerState"
```

---

### Task 5: A* Pathfinder

**Files:**
- Create: `backend/src/main/java/com/epic/engine/map/Pathfinder.java`
- Create: `backend/src/test/java/com/epic/engine/map/PathfinderTest.java`

- [ ] **Step 1: Write failing pathfinder tests**

```java
package com.epic.engine.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PathfinderTest {

    @Test
    void directPathNoObstacles() {
        // 3x3 all passable
        boolean[][] passable = {
                {true, true, true},
                {true, true, true},
                {true, true, true}
        };
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 2, 2);
        assertThat(path).isNotNull();
        assertThat(path.get(0)).containsExactly(0, 0);
        assertThat(path.get(path.size() - 1)).containsExactly(2, 2);
        // Should be 5 steps (diagonal not allowed: right,right,down,down or similar)
        assertThat(path).hasSize(5);
    }

    @Test
    void pathAroundObstacle() {
        // Wall in the middle
        boolean[][] passable = {
                {true,  true,  true,  true},
                {true,  false, false, true},
                {true,  false, false, true},
                {true,  true,  true,  true}
        };
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 3, 3);
        assertThat(path).isNotNull();
        assertThat(path.get(path.size() - 1)).containsExactly(3, 3);
        // Verify path doesn't go through obstacles
        for (int[] pos : path) {
            assertThat(passable[pos[1]][pos[0]]).isTrue();
        }
    }

    @Test
    void noPathWhenBlocked() {
        // Target surrounded by walls
        boolean[][] passable = {
                {true,  true,  true},
                {true,  false, true},
                {true,  true,  true}
        };
        // Target is the impassable cell itself
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 1, 1);
        assertThat(path).isNull();
    }

    @Test
    void sameStartAndEnd() {
        boolean[][] passable = {{true, true}, {true, true}};
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 0, 0);
        assertThat(path).hasSize(1);
        assertThat(path.get(0)).containsExactly(0, 0);
    }

    @Test
    void adjacentMove() {
        boolean[][] passable = {{true, true}, {true, true}};
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 1, 0);
        assertThat(path).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PathfinderTest`
Expected: FAIL — `Pathfinder` class doesn't exist

- [ ] **Step 3: Implement A* pathfinder**

```java
package com.epic.engine.map;

import java.util.*;

public class Pathfinder {

    private static final int[][] DIRECTIONS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    public static List<int[]> findPath(boolean[][] passable, int startX, int startY, int endX, int endY) {
        int height = passable.length;
        int width = passable[0].length;

        if (endX < 0 || endX >= width || endY < 0 || endY >= height || !passable[endY][endX]) {
            return null;
        }

        if (startX == endX && startY == endY) {
            return List.of(new int[]{startX, startY});
        }

        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        open.add(new int[]{startX, startY, 0});
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> gScore = new HashMap<>();
        long startKey = key(startX, startY, width);
        gScore.put(startKey, 0);

        while (!open.isEmpty()) {
            int[] current = open.poll();
            int cx = current[0], cy = current[1];

            if (cx == endX && cy == endY) {
                return reconstructPath(cameFrom, cx, cy, startX, startY, width);
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0], ny = cy + dir[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height || !passable[ny][nx]) {
                    continue;
                }
                long nKey = key(nx, ny, width);
                int tentativeG = gScore.getOrDefault(key(cx, cy, width), Integer.MAX_VALUE) + 1;
                if (tentativeG < gScore.getOrDefault(nKey, Integer.MAX_VALUE)) {
                    cameFrom.put(nKey, key(cx, cy, width));
                    gScore.put(nKey, tentativeG);
                    int h = Math.abs(nx - endX) + Math.abs(ny - endY);
                    open.add(new int[]{nx, ny, tentativeG + h});
                }
            }
        }

        return null;
    }

    private static long key(int x, int y, int width) {
        return (long) y * width + x;
    }

    private static List<int[]> reconstructPath(Map<Long, Long> cameFrom, int endX, int endY, int startX, int startY, int width) {
        List<int[]> path = new ArrayList<>();
        long current = key(endX, endY, width);
        long startKey = key(startX, startY, width);
        path.add(new int[]{endX, endY});

        while (current != startKey) {
            current = cameFrom.get(current);
            int x = (int) (current % width);
            int y = (int) (current / width);
            path.add(new int[]{x, y});
        }

        Collections.reverse(path);
        return path;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PathfinderTest`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/map/Pathfinder.java backend/src/test/java/com/epic/engine/map/PathfinderTest.java
git commit -m "feat: implement A* pathfinder with 4-directional movement"
```

---

### Task 6: MapService — Movement Logic

**Files:**
- Create: `backend/src/main/java/com/epic/engine/map/MapService.java`
- Modify: `backend/src/test/java/com/epic/engine/map/MapServiceTest.java`

- [ ] **Step 1: Write failing tests for movement**

Add to `MapServiceTest.java`:

```java
@Autowired
MapService mapService;

@Autowired
PlayerStateRepository playerStateRepository;

@Test
void moveInDirection() {
    // Setup player at spawn (4,3)
    PlayerState state = new PlayerState("maptest1", "village_square");
    playerStateRepository.save(state);

    var result = mapService.move("maptest1", "RIGHT");
    assertThat(result.success()).isTrue();
    assertThat(result.newX()).isEqualTo(5);
    assertThat(result.newY()).isEqualTo(3);
}

@Test
void moveBlockedByImpassableTerrain() {
    // Put player next to water (water requires swim)
    PlayerState state = new PlayerState("maptest2", "village_square");
    state.setMapX(6);
    state.setMapY(0);
    playerStateRepository.save(state);

    var result = mapService.move("maptest2", "RIGHT");
    assertThat(result.success()).isFalse();
}

@Test
void moveToWithPathfinding() {
    PlayerState state = new PlayerState("maptest3", "village_square");
    playerStateRepository.save(state);

    var result = mapService.moveTo("maptest3", 6, 3);
    assertThat(result.success()).isTrue();
    assertThat(result.path()).isNotEmpty();
    assertThat(result.path().get(0).x()).isEqualTo(4);
    assertThat(result.path().get(0).y()).isEqualTo(3);
    assertThat(result.path().get(result.path().size() - 1).x()).isEqualTo(6);
    assertThat(result.path().get(result.path().size() - 1).y()).isEqualTo(3);
}

@Test
void moveDetectsPoi() {
    // Player at (4,3), POI at (4,2) — move UP
    PlayerState state = new PlayerState("maptest4", "village_square");
    playerStateRepository.save(state);

    var result = mapService.move("maptest4", "UP");
    assertThat(result.success()).isTrue();
    assertThat(result.poi()).isNotNull();
    assertThat(result.poi().id()).isEqualTo("village");
}
```

Add the necessary import for `PlayerStateRepository` at the top.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=MapServiceTest`
Expected: FAIL — `MapService` doesn't exist

- [ ] **Step 3: Implement MapService**

```java
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
        // For now, player has no abilities — only passable if requires is empty
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=MapServiceTest`
Expected: PASS (all 6 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/map/MapService.java backend/src/test/java/com/epic/engine/map/MapServiceTest.java
git commit -m "feat: implement map service with movement and pathfinding"
```

---

### Task 7: MapController — REST API

**Files:**
- Create: `backend/src/main/java/com/epic/engine/map/MapController.java`
- Create: `backend/src/test/java/com/epic/engine/map/MapIntegrationTest.java`

- [ ] **Step 1: Write failing integration test**

```java
package com.epic.engine.map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MapIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void getMapData() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.getForObject("/api/map/world_map", Map.class);
        assertThat(response.get("id")).isEqualTo("world_map");
        assertThat(response.get("width")).isEqualTo(10);
        assertThat(response.get("height")).isEqualTo(10);
        assertThat(response).containsKey("terrain");
        assertThat(response).containsKey("terrains");
        assertThat(response).containsKey("pois");
    }

    @Test
    void moveDirection() {
        Map<String, Object> request = Map.of("playerId", "mapapi1", "direction", "RIGHT");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject("/api/map/move", request, Map.class);
        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response.get("newX")).isEqualTo(5);
        assertThat(response.get("newY")).isEqualTo(3);
    }

    @Test
    void moveToWithPath() {
        Map<String, Object> request = Map.of("playerId", "mapapi2", "targetX", 6, "targetY", 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject("/api/map/moveTo", request, Map.class);
        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response).containsKey("path");
    }

    @Test
    void getPosition() {
        // Trigger a move first to create the player
        rest.postForObject("/api/map/move",
                Map.of("playerId", "mapapi3", "direction", "DOWN"), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> pos = rest.getForObject("/api/map/position/mapapi3", Map.class);
        assertThat(pos.get("mapId")).isEqualTo("world_map");
        assertThat(pos.get("x")).isEqualTo(4);
        assertThat(pos.get("y")).isEqualTo(4);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=MapIntegrationTest`
Expected: FAIL — endpoints don't exist

- [ ] **Step 3: Implement MapController**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=MapIntegrationTest`
Expected: PASS (all 4 tests)

- [ ] **Step 5: Run all backend tests**

Run: `cd backend && mvn test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/epic/engine/map/MapController.java backend/src/test/java/com/epic/engine/map/MapIntegrationTest.java
git commit -m "feat: add map REST API (get map, move, moveTo, position)"
```

---

### Task 8: Frontend — API Client and Map State

**Files:**
- Modify: `frontend/src/api/client.js`
- Create: `frontend/src/composables/useMap.js`

- [ ] **Step 1: Add map API calls to client.js**

Add to the end of `frontend/src/api/client.js`:

```javascript
export async function fetchMap(mapId) {
    const response = await fetch(`${BASE_URL}/map/${mapId}`)
    if (!response.ok) return null
    return response.json()
}

export async function mapMove(playerId, direction) {
    const response = await fetch(`${BASE_URL}/map/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, direction })
    })
    return response.json()
}

export async function mapMoveTo(playerId, targetX, targetY) {
    const response = await fetch(`${BASE_URL}/map/moveTo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, targetX, targetY })
    })
    return response.json()
}

export async function getMapPosition(playerId) {
    const response = await fetch(`${BASE_URL}/map/position/${playerId}`)
    if (!response.ok) return null
    return response.json()
}
```

- [ ] **Step 2: Create useMap composable**

```javascript
import { reactive, ref } from 'vue'
import { fetchMap, mapMove, mapMoveTo, getMapPosition } from '../api/client.js'
import { useGameState } from './useGameState.js'

const mapState = reactive({
    mapData: null,
    playerX: 0,
    playerY: 0,
    moving: false,
    currentPath: [],
    poi: null
})

let pathTimer = null

export function useMap() {
    const { state } = useGameState()

    async function loadMap() {
        const pos = await getMapPosition(state.playerId)
        if (pos) {
            mapState.playerX = pos.x
            mapState.playerY = pos.y
        }
        mapState.mapData = await fetchMap(pos?.mapId || 'world_map')
    }

    async function moveDirection(direction) {
        if (mapState.moving) return
        cancelPath()

        const result = await mapMove(state.playerId, direction)
        if (result.success) {
            mapState.playerX = result.newX
            mapState.playerY = result.newY
            mapState.poi = result.poi || null
        }
        return result
    }

    async function moveToTarget(targetX, targetY) {
        if (mapState.moving) return
        cancelPath()

        const result = await mapMoveTo(state.playerId, targetX, targetY)
        if (!result.success || !result.path || result.path.length <= 1) return result

        mapState.moving = true
        mapState.currentPath = result.path
        animatePath(result.path, 1)
        return result
    }

    function animatePath(path, index) {
        if (index >= path.length || !mapState.moving) {
            mapState.moving = false
            mapState.currentPath = []
            return
        }

        pathTimer = setTimeout(async () => {
            const step = path[index]
            const dx = step.x - mapState.playerX
            const dy = step.y - mapState.playerY
            const dir = dx > 0 ? 'RIGHT' : dx < 0 ? 'LEFT' : dy > 0 ? 'DOWN' : 'UP'

            const result = await mapMove(state.playerId, dir)
            if (result.success) {
                mapState.playerX = result.newX
                mapState.playerY = result.newY
                if (result.poi) {
                    mapState.poi = result.poi
                    mapState.moving = false
                    mapState.currentPath = []
                    return
                }
            } else {
                mapState.moving = false
                mapState.currentPath = []
                return
            }

            animatePath(path, index + 1)
        }, 200)
    }

    function cancelPath() {
        if (pathTimer) {
            clearTimeout(pathTimer)
            pathTimer = null
        }
        mapState.moving = false
        mapState.currentPath = []
    }

    function clearPoi() {
        mapState.poi = null
    }

    return { mapState, loadMap, moveDirection, moveToTarget, cancelPath, clearPoi }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/client.js frontend/src/composables/useMap.js
git commit -m "feat: add map API client and useMap composable with pathfinding animation"
```

---

### Task 9: Frontend — MapGrid Component

**Files:**
- Create: `frontend/src/components/MapGrid.vue`

- [ ] **Step 1: Create MapGrid component**

```vue
<template>
  <div class="map-container" ref="containerEl">
    <div
      class="map-grid"
      :style="gridStyle"
    >
      <div
        v-for="(cell, idx) in visibleCells"
        :key="idx"
        class="map-cell"
        :style="cellStyle(cell)"
        @click="onCellClick(cell)"
      >
        <template v-if="cell.x === playerX && cell.y === playerY">
          <span class="player-marker">★</span>
        </template>
        <template v-else>
          {{ cell.char }}
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useMap } from '../composables/useMap.js'
import { useSettings } from '../composables/useSettings.js'

const { mapState, moveDirection, moveToTarget } = useMap()
const { settings } = useSettings()
const containerEl = ref(null)

const props = defineProps({
  mapSize: { type: Number, default: 10 }
})

const playerX = computed(() => mapState.playerX)
const playerY = computed(() => mapState.playerY)

const viewportSize = computed(() => props.mapSize)

const visibleCells = computed(() => {
  if (!mapState.mapData) return []
  const map = mapState.mapData
  const cells = []
  const halfView = Math.floor(viewportSize.value / 2)

  let offsetX = playerX.value - halfView
  let offsetY = playerY.value - halfView

  // Clamp to map edges
  offsetX = Math.max(0, Math.min(offsetX, map.width - viewportSize.value))
  offsetY = Math.max(0, Math.min(offsetY, map.height - viewportSize.value))

  // If map smaller than viewport, start at 0
  if (map.width <= viewportSize.value) offsetX = 0
  if (map.height <= viewportSize.value) offsetY = 0

  const maxX = Math.min(offsetX + viewportSize.value, map.width)
  const maxY = Math.min(offsetY + viewportSize.value, map.height)

  for (let y = offsetY; y < maxY; y++) {
    const row = map.terrain[y]
    for (let x = offsetX; x < maxX; x++) {
      const ch = row.charAt(x)
      const terrain = map.terrains.find(t => t.char === ch)
      const poi = map.pois.find(p => p.x === x && p.y === y)
      cells.push({ x, y, char: ch, terrain, poi })
    }
  }
  return cells
})

const gridStyle = computed(() => {
  const cols = Math.min(viewportSize.value, mapState.mapData?.width || viewportSize.value)
  return {
    display: 'grid',
    gridTemplateColumns: `repeat(${cols}, 1fr)`,
    gap: '0px',
    width: '100%',
    height: '100%',
    aspectRatio: '1'
  }
})

function cellStyle(cell) {
  const bg = cell.terrain?.color || '#333'
  const color = cell.terrain?.textColor || '#ccc'
  return {
    backgroundColor: bg,
    color: color,
    border: '1px solid #333',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 'clamp(10px, 2vw, 16px)',
    cursor: 'pointer',
    aspectRatio: '1'
  }
}

function onCellClick(cell) {
  if (cell.x === playerX.value && cell.y === playerY.value) return
  moveToTarget(cell.x, cell.y)
}

function onKeyDown(e) {
  const dirMap = {
    ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
    w: 'UP', W: 'UP', s: 'DOWN', S: 'DOWN', a: 'LEFT', A: 'LEFT', d: 'RIGHT', D: 'RIGHT'
  }
  const dir = dirMap[e.key]
  if (dir && !e.target.closest('input, textarea, select')) {
    e.preventDefault()
    moveDirection(dir)
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.map-grid {
  max-width: 100%;
  max-height: 100%;
}

.map-cell {
  user-select: none;
  transition: transform 0.1s;
}

.map-cell:hover {
  transform: scale(1.05);
  z-index: 1;
}

.player-marker {
  color: #ffd700;
  font-weight: bold;
  text-shadow: 0 0 6px #ffd700;
  font-size: 1.2em;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/MapGrid.vue
git commit -m "feat: add MapGrid component with viewport, click, and keyboard support"
```

---

### Task 10: Frontend — Wire Map into App + Navigation Panel

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/NavigationPanel.vue`
- Modify: `frontend/src/composables/usePanelRefresh.js`

- [ ] **Step 1: Update App.vue to use MapGrid**

Replace the map tab template content and add map initialization:

In the `<script setup>` section, add import:
```javascript
import MapGrid from './components/MapGrid.vue'
import { useMap } from './composables/useMap.js'
```

Add after other composable uses:
```javascript
const { mapState, loadMap, clearPoi } = useMap()
```

Replace the map template slot:
```html
<template #map>
  <MapGrid :map-size="settings.mapSize || 10" />
</template>
```

Update `currentActions` computed to include POI actions and direction buttons:
```javascript
const currentActions = computed(() => {
    const actions = []
    // Direction buttons
    actions.push(
        { id: 'move-up', label: '↑ 北', type: 'mapMove', params: { direction: 'UP' } },
        { id: 'move-down', label: '↓ 南', type: 'mapMove', params: { direction: 'DOWN' } },
        { id: 'move-left', label: '← 西', type: 'mapMove', params: { direction: 'LEFT' } },
        { id: 'move-right', label: '→ 东', type: 'mapMove', params: { direction: 'RIGHT' } }
    )
    // POI action
    if (mapState.poi) {
        actions.push({
            id: 'poi-' + mapState.poi.id,
            label: mapState.poi.label,
            type: 'move',
            params: { target: mapState.poi.target }
        })
    }
    // Scene actions (when in a scene, not on world map)
    if (state.currentScene?.actions) {
        actions.push(...state.currentScene.actions)
    }
    return actions
})
```

Update `handleAction` to handle mapMove type:
```javascript
async function handleAction(action) {
    sceneHistory.value.push({ type: 'action', text: action.label })
    if (action.type === 'mapMove') {
        const { moveDirection } = useMap()
        await moveDirection(action.params.direction)
        return
    }
    if (mapState.poi && action.type === 'move') {
        clearPoi()
    }
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}
```

Update `onMounted` to also load the map:
```javascript
onMounted(() => {
  initialize()
  loadMap()
})
```

- [ ] **Step 2: Add mapSize to settings**

In `frontend/src/composables/useSettings.js`, add `mapSize: 10` to `defaults`:

```javascript
const defaults = {
    fontSize: '16px',
    bgColor: '#1a1a2e',
    textColor: '#e0e0e0',
    panelBg: '#16213e',
    panelBorderColor: '#0f3460',
    linkColor: '#e94560',
    mapSize: 10
}
```

- [ ] **Step 3: Add map size slider to SettingsPanel.vue**

Add after the link color setting row:

```html
<div class="setting-row">
  <label>地图大小</label>
  <input type="range" min="8" max="20" step="1"
         :value="settings.mapSize"
         @input="settings.mapSize = parseInt($event.target.value)" />
  <span>{{ settings.mapSize }}×{{ settings.mapSize }}</span>
</div>
```

- [ ] **Step 4: Start dev servers and verify in browser**

Run backend: `cd backend && mvn spring-boot:run`
Run frontend: `cd frontend && npm run dev`

Open http://localhost:5173 — verify:
- Map renders in the top-left panel with colored grid
- Player ★ visible at spawn position
- WASD/arrow keys move player
- Direction buttons in navigation panel work
- Clicking a distant cell triggers animated movement
- Map size slider in settings changes grid size

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.vue frontend/src/composables/useSettings.js frontend/src/components/SettingsPanel.vue frontend/src/composables/usePanelRefresh.js
git commit -m "feat: wire world map into app with direction buttons and map size setting"
```

---

### Task 11: Frontend — Navigation Panel Direction Layout

**Files:**
- Modify: `frontend/src/components/NavigationPanel.vue`

- [ ] **Step 1: Update NavigationPanel for direction grid + action list**

```vue
<template>
  <div class="navigation-panel">
    <div class="direction-pad" v-if="hasDirections">
      <div class="dir-row">
        <div class="dir-spacer"></div>
        <div class="dir-btn" @click="emitDirection('UP')">↑ 北</div>
        <div class="dir-spacer"></div>
      </div>
      <div class="dir-row">
        <div class="dir-btn" @click="emitDirection('LEFT')">← 西</div>
        <div class="dir-btn dir-center">·</div>
        <div class="dir-btn" @click="emitDirection('RIGHT')">→ 东</div>
      </div>
      <div class="dir-row">
        <div class="dir-spacer"></div>
        <div class="dir-btn" @click="emitDirection('DOWN')">↓ 南</div>
        <div class="dir-spacer"></div>
      </div>
    </div>
    <div class="action-list" v-if="poiActions.length > 0">
      <div
        v-for="action in poiActions"
        :key="action.id"
        class="nav-btn"
        @click="$emit('action', action)"
      >{{ action.label }}</div>
    </div>
    <div v-if="!hasDirections && poiActions.length === 0" class="no-actions">
      <span style="color: #666">无可用操作</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
    actions: { type: Array, default: () => [] }
})

const emit = defineEmits(['action'])

const hasDirections = computed(() => {
    return props.actions.some(a => a.type === 'mapMove')
})

const poiActions = computed(() => {
    return props.actions.filter(a => a.type !== 'mapMove')
})

function emitDirection(dir) {
    const action = props.actions.find(a => a.type === 'mapMove' && a.params?.direction === dir)
    if (action) emit('action', action)
}
</script>

<style scoped>
.navigation-panel {
    display: flex;
    flex-direction: column;
    height: 100%;
    gap: 0.5rem;
}

.direction-pad {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
}

.dir-row {
    display: flex;
    gap: 2px;
}

.dir-btn {
    width: 3.5rem;
    height: 2rem;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.8em;
    transition: border-color 0.2s, background-color 0.2s;
}

.dir-btn:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.dir-center {
    cursor: default;
    border-color: transparent;
}

.dir-center:hover {
    border-color: transparent;
    background-color: transparent;
}

.dir-spacer {
    width: 3.5rem;
    height: 2rem;
}

.action-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
}

.nav-btn {
    padding: 0.4rem 0.8rem;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.9em;
    text-align: center;
    transition: border-color 0.2s, background-color 0.2s;
}

.nav-btn:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.no-actions {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
}
</style>
```

- [ ] **Step 2: Verify in browser**

Check that:
- Direction pad renders as a D-pad layout (up/down/left/right)
- POI buttons appear below the D-pad when standing on a POI
- Clicking directions moves player on the map

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/NavigationPanel.vue
git commit -m "feat: navigation panel with D-pad layout and POI actions"
```

---

### Task 12: Final Integration Test and Polish

**Files:**
- Verify all existing tests still pass
- Manual browser test

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && mvn test`
Expected: All tests PASS (existing + new map tests)

- [ ] **Step 2: Start app and do full manual test**

Run: `./start.sh`

Verify in browser (http://localhost:5173):
1. Map renders with colored terrain grid and Chinese characters
2. Player ★ at spawn (4,3) with gold glow
3. Arrow keys / WASD move player one cell at a time
4. D-pad buttons in nav panel work
5. Clicking distant passable cell → player walks there step by step (200ms per cell)
6. Clicking water/mountain cell → nothing happens (impassable)
7. Walking to POI (4,2) → "进入村庄" button appears in nav panel
8. Clicking "进入村庄" → transitions to scene view
9. Map size slider in settings changes grid dimensions
10. Moving during pathfinding animation (press a key) → interrupts path

- [ ] **Step 3: Commit any final fixes**

If needed, commit fixes found during manual testing.

- [ ] **Step 4: Final commit — update devlog**

Update `docs/devlog.md` with the new section under today's date:

```markdown
### 世界地图系统

- 10×10 彩色网格地图，色块+汉字风格
- 地形标签通行系统（requires/abilities），可扩展
- 移动速度字段预留（move-cost / move-speed）
- 三种移动方式：键盘方向键、D-pad 按钮、点击自动寻路
- A* 寻路 + 逐格动画（200ms/步）
- 寻路中断：手动中断、POI 停止、威胁停止
- 玩家居中视口，地图大小可自定义（8~20）
- POI 交互：踩到特殊点操作面板出按钮
- 玩家 ★ 金色五角星标记
```

```bash
git add docs/devlog.md
git commit -m "docs: update devlog with world map system"
```

---

Plan complete and saved to `docs/superpowers/plans/2026-05-21-world-map-display.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
