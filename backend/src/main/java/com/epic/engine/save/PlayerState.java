package com.epic.engine.save;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PlayerState {

    @Id
    private String playerId;
    private String currentScene;

    public PlayerState() {}

    public PlayerState(String playerId, String currentScene) {
        this.playerId = playerId;
        this.currentScene = currentScene;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getCurrentScene() { return currentScene; }
    public void setCurrentScene(String currentScene) { this.currentScene = currentScene; }
}
