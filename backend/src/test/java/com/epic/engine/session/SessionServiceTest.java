package com.epic.engine.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SessionServiceTest {

    SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(30, 5);
    }

    @Test
    void createSession_returnsToken() {
        String token = service.createSession();
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void getSession_withValidToken_returnsData() {
        String token = service.createSession();
        SessionData data = service.getSession(token);
        assertThat(data).isNotNull();
        assertThat(data.activeCharacterId()).isNull();
    }

    @Test
    void getSession_withInvalidToken_returnsNull() {
        assertThat(service.getSession("bogus")).isNull();
    }

    @Test
    void setActiveCharacter_updatesSession() {
        String token = service.createSession();
        service.setActiveCharacter(token, "char_1");
        SessionData data = service.getSession(token);
        assertThat(data.activeCharacterId()).isEqualTo("char_1");
    }

    @Test
    void clearActiveCharacter_removesCharacter() {
        String token = service.createSession();
        service.setActiveCharacter(token, "char_1");
        service.clearActiveCharacter(token);
        SessionData data = service.getSession(token);
        assertThat(data.activeCharacterId()).isNull();
    }

    @Test
    void isTimedOut_afterTimeout_returnsTrue() {
        SessionService shortTimeout = new SessionService(0, 5);
        String token = shortTimeout.createSession();
        shortTimeout.setActiveCharacter(token, "char_1");
        assertThat(shortTimeout.isTimedOut(token)).isTrue();
    }

    @Test
    void touchSession_resetsTimeout() {
        String token = service.createSession();
        service.setActiveCharacter(token, "char_1");
        service.touchSession(token);
        assertThat(service.isTimedOut(token)).isFalse();
    }

    @Test
    void getMaxSlots_returnsConfigured() {
        assertThat(service.getMaxSlots()).isEqualTo(5);
    }
}
