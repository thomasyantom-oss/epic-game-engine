package com.epic.engine.sim.reports;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.sim.BatchMetrics;
import com.epic.engine.sim.CombatTuning;
import com.epic.engine.sim.CombatantBuilder;
import com.epic.engine.sim.SimSetup;
import com.epic.engine.sim.Sweep;

import java.util.List;
import java.util.Locale;

public class ResistCapSweepReport {
    private final CombatantBuilder builder;
    private final EntityStore store;
    private final EventBus bus;
    private final CombatTuning tuning;

    public ResistCapSweepReport(CombatantBuilder builder, EntityStore store, EventBus bus, CombatTuning tuning) {
        this.builder = builder;
        this.store = store;
        this.bus = bus;
        this.tuning = tuning;
    }

    public String runCsv(List<Integer> caps, int floor, SimSetup setup, int iterations) {
        try {
            Sweep sweep = new Sweep();
            List<Sweep.Point> points = sweep.run(caps, cap -> tuning.setResistBounds(cap, floor),
                    Integer::toString, iterations, setup, builder, store, bus);
            StringBuilder sb = new StringBuilder("resist_cap,win_rate,ttk_median,win_hp_remaining\n");
            for (Sweep.Point point : points) {
                BatchMetrics metrics = point.metrics();
                sb.append(String.format(Locale.US, "%d,%.3f,%.1f,%.3f\n",
                        point.value(), metrics.winRate(), metrics.ttkMedian(), nz(metrics.winHpRemainingMedian())));
            }
            return sb.toString();
        } finally {
            tuning.setResistBounds(75, -50);
        }
    }

    private static double nz(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }
}
