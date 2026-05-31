package com.epic.engine.combat;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SkillFidelityTest {
    static final String[][] CASES = {
        {"basic_attack", "goblin1"}, {"crescent_slash", "goblin1"}, {"ice_beam", "goblin1"},
        {"pulse_wave",   "goblin1"}, {"fireball",       "goblin1"}, {"poison_dart", "goblin1"},
        {"cleave",       "goblin1"}, {"piercing_ray",   "goblin1"}, {"cross_blast", "goblin1"},
        {"light_field",  "goblin1"}, {"heal",  null}, {"defend", null},
        {"war_cry",      null},      {"curse",  null}
    };

    @Test
    void allSkills_matchGolden() throws Exception {
        StringBuilder diffs = new StringBuilder();
        for (String[] c : CASES) {
            try (SkillFidelityHarness h = new SkillFidelityHarness()) {
                String actual = h.fire(c[0], c[1]);
                Path golden = Path.of(h.goldenPath(c[0]));
                if (!Files.exists(golden)) {
                    Files.createDirectories(golden.getParent());
                    Files.writeString(golden, actual);
                    diffs.append("CAPTURED new golden: ").append(c[0]).append("\n");
                } else {
                    String expected = Files.readString(golden);
                    if (!expected.equals(actual)) {
                        diffs.append("MISMATCH ").append(c[0]).append(":\n")
                             .append("--- expected ---\n").append(expected)
                             .append("\n--- actual ---\n").append(actual).append("\n");
                    }
                }
            }
        }
        assertThat(diffs.toString()).isEmpty();
    }
}
