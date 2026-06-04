package com.epic.engine.sim.reports;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.sim.BatchMetrics;
import com.epic.engine.sim.BatchRunner;
import com.epic.engine.sim.BatchTrace;
import com.epic.engine.sim.CombatantBuilder;
import com.epic.engine.sim.SimSetup;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExplainReport {
    private final CombatantBuilder builder;
    private final EntityStore store;
    private final EventBus bus;

    public ExplainReport(CombatantBuilder builder, EntityStore store, EventBus bus) {
        this.builder = builder;
        this.store = store;
        this.bus = bus;
    }

    public record Summary(BatchMetrics metrics, BatchTrace trace) {}

    public Summary run(SimSetup setup, int iterations) {
        BatchRunner.Aggregated aggregated = new BatchRunner()
                .runSimulationWithTrace(iterations, setup, builder, store, bus);
        return new Summary(aggregated.metrics(), aggregated.trace());
    }

    public static String toCsv(Summary summary) {
        StringBuilder sb = new StringBuilder();
        BatchMetrics m = summary.metrics();
        sb.append("section,key,value\n");
        append(sb, "summary", "win_rate", "%.3f", m.winRate());
        append(sb, "summary", "loss_rate", "%.3f", m.lossRate());
        append(sb, "summary", "timeout_rate", "%.3f", m.timeoutRate());
        append(sb, "summary", "player_ttk_median", "%.1f", m.ttkMedian());
        append(sb, "summary", "enemy_ttk_median", "%.1f", nz(m.enemyTtkMedian()));
        append(sb, "summary", "avg_death_round", "%.1f", summary.trace().avgDeathRound());
        appendMap(sb, "damage_by_skill", summary.trace().totalDamageBySkill());
        appendMap(sb, "damage_by_type", summary.trace().totalDamageByType());
        appendMap(sb, "mitigation_saved", summary.trace().totalMitigationSaved());
        appendMap(sb, "kill_source", summary.trace().totalKillSource());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String section, String key, String format, double value) {
        sb.append(String.format(Locale.US, "%s,%s," + format + "\n", section, key, value));
    }

    private static <N extends Number> void appendMap(StringBuilder sb, String section, Map<String, N> values) {
        List<Map.Entry<String, N>> entries = values.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingDouble(e -> -e.doubleValue())))
                .toList();
        for (Map.Entry<String, N> entry : entries) {
            sb.append(String.format(Locale.US, "%s,%s,%s\n", section, entry.getKey(), entry.getValue()));
        }
    }

    private static double nz(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }
}
