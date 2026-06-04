package com.epic.engine.sim.reports;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.session.SessionService;
import com.epic.engine.sim.BatchMetrics;
import com.epic.engine.sim.BatchRunner;
import com.epic.engine.sim.CombatantBuilder;
import com.epic.engine.sim.PolicyKind;
import com.epic.engine.sim.SimSetup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EarlyClassHealthCheck {
    private final EventBus bus;
    private final EntityStore store;
    private final SessionService sessions;

    public EarlyClassHealthCheck(EventBus bus, EntityStore store, SessionService sessions) {
        this.bus = bus;
        this.store = store;
        this.sessions = sessions;
    }

    public record Row(String playerClass, int level, String encounter,
                      double winRate, double ttkMedian,
                      double winHpRemaining, double lossEnemyHpRemaining) {}

    public List<Row> run(List<String> classes, List<Integer> levels,
                         List<String> encounters, int iterations) {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessions);
        BatchRunner batch = new BatchRunner();
        List<Row> rows = new ArrayList<>();
        for (String playerClass : classes) {
            for (int level : levels) {
                for (String encounter : encounters) {
                    BatchMetrics metrics = batch.runSimulation(iterations,
                            new SimSetup(playerClass, level, encounter, PolicyKind.HEURISTIC, List.of(), 50),
                            builder, store, bus);
                    rows.add(new Row(playerClass, level, encounter, metrics.winRate(), metrics.ttkMedian(),
                            metrics.winHpRemainingMedian(), metrics.lossEnemyHpRemainingMedian()));
                }
            }
        }
        return rows;
    }

    public static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder(
                "class,level,encounter,win_rate,ttk_median,win_hp_remaining,loss_enemy_hp_remaining\n");
        for (Row row : rows) {
            sb.append(String.format(Locale.US, "%s,%d,%s,%.3f,%.1f,%.3f,%.3f\n",
                    row.playerClass(), row.level(), row.encounter(), row.winRate(), row.ttkMedian(),
                    nz(row.winHpRemaining()), nz(row.lossEnemyHpRemaining())));
        }
        return sb.toString();
    }

    private static double nz(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }
}
