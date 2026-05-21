package com.epic.engine.combat;

import com.epic.engine.combat.CombatController.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CombatIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void fullCombatFlow() {
        // 1. Start combat via action endpoint
        var actionRequest = Map.of(
                "playerId", "player1",
                "type", "combat",
                "params", Map.of("target", "forest_goblin")
        );
        rest.postForObject("/api/action", actionRequest, Object.class);

        // 2. Get combat state
        ResponseEntity<CombatStateDto> stateResponse =
                rest.getForEntity("/api/combat/player1", CombatStateDto.class);
        assertThat(stateResponse.getStatusCode().is2xxSuccessful()).isTrue();
        CombatStateDto state = stateResponse.getBody();
        assertThat(state).isNotNull();
        assertThat(state.phase()).isEqualTo("COMMAND");
        assertThat(state.combatants()).hasSizeGreaterThanOrEqualTo(3);

        // 3. Get valid targets
        ResponseEntity<List<TargetDto>> targetsResponse = rest.exchange(
                "/api/combat/player1/targets/player1",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        assertThat(targetsResponse.getBody()).isNotEmpty();

        // 4. Submit commands (attack first available target)
        String targetId = targetsResponse.getBody().get(0).id();
        List<CommandDto> commands = List.of(
                new CommandDto("player1", "ATTACK", targetId)
        );
        ResponseEntity<RoundResultDto> resultResponse =
                rest.postForEntity("/api/combat/player1/commands", commands, RoundResultDto.class);
        assertThat(resultResponse.getBody()).isNotNull();
        assertThat(resultResponse.getBody().results()).isNotEmpty();
    }
}
