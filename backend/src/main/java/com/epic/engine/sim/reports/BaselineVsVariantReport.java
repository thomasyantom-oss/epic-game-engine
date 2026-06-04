package com.epic.engine.sim.reports;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.sim.BaselineIndex;
import com.epic.engine.sim.CombatantBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BaselineVsVariantReport {
    private final CombatantBuilder builder;
    private final EntityStore store;
    private final EventBus bus;

    public BaselineVsVariantReport(CombatantBuilder builder, EntityStore store, EventBus bus) {
        this.builder = builder;
        this.store = store;
        this.bus = bus;
    }

    public String run(String classId, int level, List<Map<String, Object>> variantSources, int iterations) {
        BaselineIndex.Result result = new BaselineIndex(builder, store, bus)
                .compare(classId, level, variantSources, iterations);
        int offensePct = (int) Math.round((result.offenseIndex() - 1.0) * 100);
        int defensePct = (int) Math.round((result.defenseIndex() - 1.0) * 100);
        return String.format(Locale.US,
                "baseline_vs_variant[%s L%d]\noffense_index=%.2f (输出 %+d%%)\ndefense_index=%.2f (生存 %+d%%)\n",
                classId, level, result.offenseIndex(), offensePct, result.defenseIndex(), defensePct);
    }
}
