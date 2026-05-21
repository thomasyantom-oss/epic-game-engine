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
