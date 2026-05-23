package com.epic.engine.session;

import java.time.Instant;

public record SessionData(
        String token,
        String activeCharacterId,
        Instant lastActivity
) {
    public SessionData withActiveCharacter(String characterId) {
        return new SessionData(token, characterId, Instant.now());
    }

    public SessionData withTouch() {
        return new SessionData(token, activeCharacterId, Instant.now());
    }

    public SessionData withClearedCharacter() {
        return new SessionData(token, null, lastActivity);
    }
}
