package com.epic.engine.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record BatchMetrics(
        int total,
        double winRate,
        double lossRate,
        double timeoutRate,
        double ttkMean,
        double ttkMedian,
        double ttkP10,
        double ttkP90,
        double winHpRemainingMedian,
        double lossEnemyHpRemainingMedian,
        double enemyTtkMedian,
        double playerSurvivalTurnsMedian) {

    public static BatchMetrics of(List<FightResult> results) {
        int total = results.size();
        if (total == 0) {
            return new BatchMetrics(0, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        long wins = results.stream().filter(r -> r.outcome() == FightOutcome.WIN).count();
        long losses = results.stream().filter(r -> r.outcome() == FightOutcome.LOSS).count();
        long timeouts = results.stream().filter(r -> r.outcome() == FightOutcome.TIMEOUT).count();
        List<Double> rounds = results.stream().map(r -> (double) r.rounds()).toList();
        double mean = rounds.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        List<Double> winHp = results.stream()
                .filter(r -> r.outcome() == FightOutcome.WIN)
                .map(FightResult::playerHpFrac)
                .toList();
        List<Double> lossEnemyHp = results.stream()
                .filter(r -> r.outcome() == FightOutcome.LOSS)
                .map(FightResult::enemyHpFrac)
                .toList();
        List<Double> lossRounds = results.stream()
                .filter(r -> r.outcome() == FightOutcome.LOSS)
                .map(r -> (double) r.rounds())
                .toList();
        List<Double> survivalRounds = results.stream()
                .filter(r -> r.outcome() == FightOutcome.LOSS || r.outcome() == FightOutcome.TIMEOUT)
                .map(r -> (double) r.rounds())
                .toList();
        return new BatchMetrics(
                total,
                (double) wins / total,
                (double) losses / total,
                (double) timeouts / total,
                mean,
                percentile(rounds, 0.50),
                percentile(rounds, 0.10),
                percentile(rounds, 0.90),
                percentile(winHp, 0.50),
                percentile(lossEnemyHp, 0.50),
                percentile(lossRounds, 0.50),
                survivalRounds.isEmpty() ? percentile(rounds, 0.50) : percentile(survivalRounds, 0.50));
    }

    private static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
