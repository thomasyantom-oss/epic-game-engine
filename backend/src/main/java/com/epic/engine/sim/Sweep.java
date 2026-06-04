package com.epic.engine.sim;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class Sweep {
    public record Point(String label, int value, BatchMetrics metrics) {}

    public List<Point> run(List<Integer> values,
                           IntConsumer knobSetter,
                           IntFunction<String> labeler,
                           int iterations,
                           SimSetup setup,
                           CombatantBuilder builder,
                           EntityStore store,
                           EventBus bus) {
        BatchRunner batch = new BatchRunner();
        List<Point> points = new ArrayList<>();
        for (int value : values) {
            knobSetter.accept(value);
            points.add(new Point(labeler.apply(value), value,
                    batch.runSimulation(iterations, setup, builder, store, bus)));
        }
        return points;
    }
}
