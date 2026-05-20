package com.epic.engine.action;

import com.epic.engine.panel.PanelRefresh;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MoveActionHandlerTest {

    PlayerStateRepository repository;
    MoveActionHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(PlayerStateRepository.class);
        handler = new MoveActionHandler(repository);
    }

    @Test
    void movesPlayerToTargetScene() {
        PlayerState state = new PlayerState();
        state.setPlayerId("player1");
        state.setCurrentScene("village_square");
        when(repository.findByPlayerId("player1")).thenReturn(Optional.of(state));

        ActionResponse response = handler.handle("player1", Map.of("target", "tavern"));

        assertThat(response.success()).isTrue();
        assertThat(state.getCurrentScene()).isEqualTo("tavern");
        verify(repository).save(state);
        assertThat(response.refreshPanels()).contains(PanelRefresh.SCENE);
    }

    @Test
    void failsWhenNoTarget() {
        ActionResponse response = handler.handle("player1", Map.of());
        assertThat(response.success()).isFalse();
    }
}
