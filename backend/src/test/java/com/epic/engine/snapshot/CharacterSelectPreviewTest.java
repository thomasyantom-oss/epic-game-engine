package com.epic.engine.snapshot;

import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CharacterSelectPreviewTest {

    @Autowired
    SessionService sessionService;

    @Autowired
    SnapshotService snapshotService;

    @Test
    void select_snapshot_includes_class_previews() {
        // A fresh session has no active character → buildSnapshot returns character_select
        String token = sessionService.createSession();
        WorldSnapshot snap = snapshotService.buildSnapshot(token);

        assertThat(snap.phase()).isEqualTo("character_select");
        assertThat(snap.classPreviews()).isNotNull();
        assertThat(snap.classPreviews()).isNotEmpty();

        var warrior = snap.classPreviews().stream()
                .filter(p -> p.id().equals("warrior"))
                .findFirst()
                .orElse(null);
        assertThat(warrior).isNotNull();
        assertThat(warrior.label()).isEqualTo("战士");
        // SnakeYAML loads plain integers as Integer
        assertThat(warrior.growth().get("力量")).isEqualTo(3);
    }
}
