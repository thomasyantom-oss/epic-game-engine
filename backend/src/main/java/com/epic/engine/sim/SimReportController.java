package com.epic.engine.sim;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.session.SessionService;
import com.epic.engine.sim.reports.ArmorKSweepReport;
import com.epic.engine.sim.reports.BalanceCheckReport;
import com.epic.engine.sim.reports.BalanceModelReport;
import com.epic.engine.sim.reports.EarlyClassHealthCheck;
import com.epic.engine.sim.reports.ExplainReport;
import com.epic.engine.sim.reports.ResistCapSweepReport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/sim/reports")
public class SimReportController {
    private static final List<String> DEFAULT_CLASSES =
            List.of("warrior", "rogue", "mage", "druid", "guardian");

    private final EventBus bus;
    private final EntityStore store;
    private final SessionService sessions;
    private final CombatTuning tuning = new CombatTuning();

    public SimReportController(EventBus bus, EntityStore store, SessionService sessions, ScriptRuntime runtime) {
        this.bus = bus;
        this.store = store;
        this.sessions = sessions;
        runtime.bindService("tuning", tuning);
    }

    @GetMapping("/early-health")
    public List<EarlyClassHealthCheck.Row> earlyHealth(
            @RequestParam(defaultValue = "warrior,rogue,mage,druid,guardian") String classes,
            @RequestParam(defaultValue = "1-50") String levels,
            @RequestParam(defaultValue = "forest_goblin") String encounters,
            @RequestParam(defaultValue = "3") int iterations) {
        return new EarlyClassHealthCheck(bus, store, sessions)
                .run(parseStrings(classes, DEFAULT_CLASSES), parseLevels(levels),
                        parseStrings(encounters, List.of("forest_goblin")), iterations);
    }

    @GetMapping(value = "/early-health.csv", produces = "text/csv")
    public ResponseEntity<String> earlyHealthCsv(
            @RequestParam(defaultValue = "warrior,rogue,mage,druid,guardian") String classes,
            @RequestParam(defaultValue = "1-50") String levels,
            @RequestParam(defaultValue = "forest_goblin") String encounters,
            @RequestParam(defaultValue = "3") int iterations) {
        String csv = EarlyClassHealthCheck.toCsv(earlyHealth(classes, levels, encounters, iterations));
        return csv(csv);
    }

    @GetMapping(value = "/explain.csv", produces = "text/csv")
    public ResponseEntity<String> explainCsv(
            @RequestParam(defaultValue = "warrior") String playerClass,
            @RequestParam(defaultValue = "1") int level,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "") String skills,
            @RequestParam(defaultValue = "50") int maxTurns) {
        ExplainReport.Summary summary = explain(playerClass, level, encounter, iterations, skills, maxTurns);
        return csv(ExplainReport.toCsv(summary));
    }

    @GetMapping("/explain")
    public ExplainReport.Summary explain(
            @RequestParam(defaultValue = "warrior") String playerClass,
            @RequestParam(defaultValue = "1") int level,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "") String skills,
            @RequestParam(defaultValue = "50") int maxTurns) {
        List<String> scripted = parseStrings(skills, List.of());
        PolicyKind policyKind = scripted.isEmpty() ? PolicyKind.HEURISTIC : PolicyKind.SCRIPTED;
        return new ExplainReport(builder(), store, bus).run(
                new SimSetup(playerClass, level, encounter, policyKind, scripted, maxTurns), iterations);
    }

    @GetMapping(value = "/armor-k.csv", produces = "text/csv")
    public ResponseEntity<String> armorKCsv(
            @RequestParam(defaultValue = "warrior") String playerClass,
            @RequestParam(defaultValue = "10") int level,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "5,10,20,40") String armorKs,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "50") int maxTurns) {
        String csv = new ArmorKSweepReport(builder(), store, bus, tuning).runCsv(
                parseInts(armorKs), new SimSetup(playerClass, level, encounter,
                        PolicyKind.HEURISTIC, List.of(), maxTurns), iterations);
        return csv(csv);
    }

    @GetMapping(value = "/resist-cap.csv", produces = "text/csv")
    public ResponseEntity<String> resistCapCsv(
            @RequestParam(defaultValue = "mage") String playerClass,
            @RequestParam(defaultValue = "10") int level,
            @RequestParam(defaultValue = "mitigation_high_resist_test") String encounter,
            @RequestParam(defaultValue = "25,50,75,90") String caps,
            @RequestParam(defaultValue = "-50") int floor,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "50") int maxTurns) {
        List<String> scripted = repeated("fireball", maxTurns);
        String csv = new ResistCapSweepReport(builder(), store, bus, tuning).runCsv(
                parseInts(caps), floor, new SimSetup(playerClass, level, encounter,
                        PolicyKind.SCRIPTED, scripted, maxTurns), iterations);
        return csv(csv);
    }

    @GetMapping("/balance-model")
    public List<BalanceModelReport.Row> balanceModel(
            @RequestParam(defaultValue = "warrior,rogue,mage,druid,guardian") String classes,
            @RequestParam(defaultValue = "1-50") String levels,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "50") int maxTurns) {
        return new BalanceModelReport(bus, store, sessions)
                .run(parseStrings(classes, DEFAULT_CLASSES), parseLevels(levels), encounter, iterations, maxTurns);
    }

    @GetMapping(value = "/balance-model.csv", produces = "text/csv")
    public ResponseEntity<String> balanceModelCsv(
            @RequestParam(defaultValue = "warrior,rogue,mage,druid,guardian") String classes,
            @RequestParam(defaultValue = "1-50") String levels,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "50") int maxTurns) {
        String csv = BalanceModelReport.toCsv(balanceModel(classes, levels, encounter, iterations, maxTurns));
        return csv(csv);
    }

    @GetMapping("/balance-check")
    public List<BalanceCheckReport.Row> balanceCheck(
            @RequestParam(defaultValue = "warrior,rogue,mage,druid,guardian") String classes,
            @RequestParam(defaultValue = "1,5,10,20,50") String levels,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "50") int maxTurns) {
        return new BalanceCheckReport().run(balanceModel(classes, levels, encounter, iterations, maxTurns));
    }

    @GetMapping(value = "/balance-check.csv", produces = "text/csv")
    public ResponseEntity<String> balanceCheckCsv(
            @RequestParam(defaultValue = "warrior,rogue,mage,druid,guardian") String classes,
            @RequestParam(defaultValue = "1,5,10,20,50") String levels,
            @RequestParam(defaultValue = "forest_goblin") String encounter,
            @RequestParam(defaultValue = "3") int iterations,
            @RequestParam(defaultValue = "50") int maxTurns) {
        String csv = BalanceCheckReport.toCsv(balanceCheck(classes, levels, encounter, iterations, maxTurns));
        return csv(csv);
    }

    private CombatantBuilder builder() {
        return new CombatantBuilder(bus, store, sessions);
    }

    private static ResponseEntity<String> csv(String csv) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    private static List<String> parseStrings(String raw, List<String> fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        List<String> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values.isEmpty() ? fallback : values;
    }

    private static List<Integer> parseInts(String raw) {
        return parseStrings(raw, List.of()).stream().map(Integer::parseInt).toList();
    }

    private static List<Integer> parseLevels(String raw) {
        List<Integer> levels = new ArrayList<>();
        for (String part : parseStrings(raw, List.of())) {
            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                int start = Integer.parseInt(bounds[0].trim());
                int end = Integer.parseInt(bounds[1].trim());
                for (int level = start; level <= end; level++) levels.add(level);
            } else {
                levels.add(Integer.parseInt(part));
            }
        }
        return levels;
    }

    private static List<String> repeated(String skill, int count) {
        List<String> skills = new ArrayList<>();
        for (int i = 0; i < count; i++) skills.add(skill);
        return skills;
    }
}
