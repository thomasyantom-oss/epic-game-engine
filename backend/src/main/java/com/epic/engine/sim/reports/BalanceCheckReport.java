package com.epic.engine.sim.reports;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BalanceCheckReport {
    public enum Status {
        PASS,
        OBSERVE,
        FAIL_TOO_HARD,
        FAIL_TOO_EASY
    }

    public record Row(
            String playerClass,
            int level,
            String encounter,
            String metric,
            double current,
            String target,
            Status status,
            String suggestion) {}

    public List<Row> run(List<BalanceModelReport.Row> modelRows) {
        List<Row> rows = new ArrayList<>();
        for (BalanceModelReport.Row row : modelRows) {
            addLevelChecks(rows, row);
        }
        return rows;
    }

    public static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder(
                "class,level,encounter,metric,current,target,status,suggestion\n");
        for (Row row : rows) {
            sb.append(String.format(Locale.US, "%s,%d,%s,%s,%.3f,%s,%s,%s\n",
                    row.playerClass(), row.level(), row.encounter(), row.metric(),
                    row.current(), row.target(), row.status(), row.suggestion()));
        }
        return sb.toString();
    }

    private static void addLevelChecks(List<Row> rows, BalanceModelReport.Row row) {
        switch (row.level()) {
            case 1 -> checkRange(rows, row, "estimated_player_ttk",
                    row.estimatedPlayerTtk(), 1.0, 2.0,
                    Status.FAIL_TOO_EASY, "lower_player_weapon_base_or_raise_enemy_hp",
                    Status.FAIL_TOO_HARD, "raise_player_weapon_base_or_lower_enemy_hp");
            case 5 -> checkMin(rows, row, "sim_win_rate",
                    row.simWinRate(), 0.90,
                    "reduce_enemy_early_weapon_or_enemy_hp");
            case 10 -> checkMin(rows, row, "sim_win_rate",
                    row.simWinRate(), 0.70,
                    "reduce_enemy_mid_weapon_or_raise_player_damage");
            case 20 -> checkRange(rows, row, "sim_win_rate",
                    row.simWinRate(), 0.30, 0.70,
                    Status.FAIL_TOO_HARD, "reduce_enemy_mid_weapon_or_raise_player_survivability",
                    Status.FAIL_TOO_EASY, "raise_enemy_mid_weapon_or_enemy_hp");
            case 50 -> {
                checkMax(rows, row, "sim_win_rate",
                        row.simWinRate(), 0.00,
                        "raise_enemy_late_weapon_or_enemy_hp");
                observe(rows, row, "estimated_enemy_ttk",
                        row.estimatedEnemyTtk(), "naked_player_pressure_baseline",
                        "compare_equipped_builds_against_this_floor");
            }
            default -> {
            }
        }
    }

    private static void checkMin(List<Row> rows, BalanceModelReport.Row source, String metric,
                                 double current, double min, String tooHardSuggestion) {
        Status status = current >= min ? Status.PASS : Status.FAIL_TOO_HARD;
        rows.add(new Row(source.playerClass(), source.level(), source.encounter(), metric,
                current, ">=" + fmt(min), status, status == Status.PASS ? "" : tooHardSuggestion));
    }

    private static void checkMax(List<Row> rows, BalanceModelReport.Row source, String metric,
                                 double current, double max, String tooEasySuggestion) {
        Status status = current <= max ? Status.PASS : Status.FAIL_TOO_EASY;
        rows.add(new Row(source.playerClass(), source.level(), source.encounter(), metric,
                current, "<=" + fmt(max), status, status == Status.PASS ? "" : tooEasySuggestion));
    }

    private static void checkRange(List<Row> rows, BalanceModelReport.Row source, String metric,
                                   double current, double min, double max,
                                   Status lowStatus, String lowSuggestion,
                                   Status highStatus, String highSuggestion) {
        Status status;
        String suggestion;
        if (current < min) {
            status = lowStatus;
            suggestion = lowSuggestion;
        } else if (current > max) {
            status = highStatus;
            suggestion = highSuggestion;
        } else {
            status = Status.PASS;
            suggestion = "";
        }
        rows.add(new Row(source.playerClass(), source.level(), source.encounter(), metric,
                current, fmt(min) + "-" + fmt(max), status, suggestion));
    }

    private static void observe(List<Row> rows, BalanceModelReport.Row source, String metric,
                                double current, String target, String suggestion) {
        rows.add(new Row(source.playerClass(), source.level(), source.encounter(), metric,
                current, target, Status.OBSERVE, suggestion));
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
