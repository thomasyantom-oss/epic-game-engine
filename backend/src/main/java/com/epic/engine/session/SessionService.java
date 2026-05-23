package com.epic.engine.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final int timeoutMinutes;
    private final int maxSlots;

    public SessionService(
            @Value("${epic.session-timeout-minutes:30}") int timeoutMinutes,
            @Value("${epic.max-character-slots:5}") int maxSlots) {
        this.timeoutMinutes = timeoutMinutes;
        this.maxSlots = maxSlots;
    }

    public String createSession() {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionData(token, null, Instant.now()));
        return token;
    }

    public String restoreSession(String token) {
        sessions.put(token, new SessionData(token, null, Instant.now()));
        return token;
    }

    public SessionData getSession(String token) {
        return sessions.get(token);
    }

    public void setActiveCharacter(String token, String characterId) {
        SessionData data = sessions.get(token);
        if (data != null) {
            sessions.put(token, data.withActiveCharacter(characterId));
        }
    }

    public void clearActiveCharacter(String token) {
        SessionData data = sessions.get(token);
        if (data != null) {
            sessions.put(token, data.withClearedCharacter());
        }
    }

    public boolean isTimedOut(String token) {
        SessionData data = sessions.get(token);
        if (data == null || data.activeCharacterId() == null) return false;
        Duration elapsed = Duration.between(data.lastActivity(), Instant.now());
        return elapsed.toMinutes() >= timeoutMinutes;
    }

    public void touchSession(String token) {
        SessionData data = sessions.get(token);
        if (data != null) {
            sessions.put(token, data.withTouch());
        }
    }

    public int getMaxSlots() {
        return maxSlots;
    }
}
