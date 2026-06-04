package com.epic.engine.sim.reports;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
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

public class BalanceModelReport {
    private final EventBus bus;
    private final EntityStore store;
    private final SessionService sessions;

    public BalanceModelReport(EventBus bus, EntityStore store, SessionService sessions) {
        this.bus = bus;
        this.store = store;
        this.sessions = sessions;
    }

    public record Row(
            String playerClass,
            int level,
            String encounter,
            int playerHp,
            int enemyTotalHp,
            int playerRawAttack,
            double enemyRawAttackSum,
            int playerArmor,
            double enemyArmorAvg,
            double playerDamagePerRound,
            double enemyDamagePerRound,
            double estimatedPlayerTtk,
            double estimatedEnemyTtk,
            String estimatedOutcome,
            double simWinRate,
            double simLossRate,
            double simTimeoutRate,
            double simTtkMedian,
            double simEnemyTtkMedian,
            String gapReason) {}

    public List<Row> run(List<String> classes, List<Integer> levels, String encounter,
                         int iterations, int maxTurns) {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessions);
        BatchRunner batch = new BatchRunner();
        List<Row> rows = new ArrayList<>();
        for (String playerClass : classes) {
            for (int level : levels) {
                rows.add(runOne(builder, batch, playerClass, level, encounter, iterations, maxTurns));
            }
        }
        return rows;
    }

    private Row runOne(CombatantBuilder builder, BatchRunner batch, String playerClass, int level,
                       String encounter, int iterations, int maxTurns) {
        String playerId = builder.buildPlayer(playerClass, level, List.of());
        CombatantBuilder.Spawn spawn = builder.spawnEncounter(playerId, encounter);
        Entity player = store.get(playerId);

        int playerHp = hp(player);
        int playerArmor = defense(player);
        int playerRaw = attack(player);

        int enemyTotalHp = 0;
        int enemyArmorSum = 0;
        int enemyRawSum = 0;
        double playerDamageNumerator = 0.0;
        double enemyDamagePerRound = 0.0;
        int livingEnemies = 0;
        for (String enemyId : spawn.enemyIds()) {
            Entity enemy = store.get(enemyId);
            int enemyHp = hp(enemy);
            int enemyArmor = defense(enemy);
            int enemyRaw = attack(enemy);
            enemyTotalHp += enemyHp;
            enemyArmorSum += enemyArmor;
            enemyRawSum += enemyRaw;
            playerDamageNumerator += damageAfterArmor(playerRaw, enemyArmor);
            enemyDamagePerRound += damageAfterArmor(enemyRaw, playerArmor);
            livingEnemies++;
        }
        double playerDamagePerRound = livingEnemies == 0 ? 0.0 : playerDamageNumerator / livingEnemies;
        double enemyArmorAvg = livingEnemies == 0 ? 0.0 : (double) enemyArmorSum / livingEnemies;
        double estimatedPlayerTtk = playerDamagePerRound <= 0.0
                ? Double.POSITIVE_INFINITY
                : enemyTotalHp / playerDamagePerRound;
        double estimatedEnemyTtk = enemyDamagePerRound <= 0.0
                ? Double.POSITIVE_INFINITY
                : (double) playerHp / enemyDamagePerRound;
        String estimatedOutcome = estimatedPlayerTtk < estimatedEnemyTtk ? "win" : "lose";

        builder.cleanup(playerId, spawn);
        store.remove(playerId);

        BatchMetrics metrics = batch.runSimulation(iterations,
                new SimSetup(playerClass, level, encounter, PolicyKind.HEURISTIC, List.of(), maxTurns),
                builder, store, bus);

        return new Row(playerClass, level, encounter, playerHp, enemyTotalHp, playerRaw,
                enemyRawSum, playerArmor, enemyArmorAvg, playerDamagePerRound, enemyDamagePerRound,
                estimatedPlayerTtk, estimatedEnemyTtk, estimatedOutcome, metrics.winRate(),
                metrics.lossRate(), metrics.timeoutRate(), metrics.ttkMedian(),
                nz(metrics.enemyTtkMedian()), gapReason(estimatedOutcome, metrics));
    }

    public static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder("class,level,encounter,player_hp,enemy_total_hp,"
                + "player_raw_attack,enemy_raw_attack_sum,player_armor,enemy_armor_avg,"
                + "player_damage_per_round,enemy_damage_per_round,estimated_player_ttk,"
                + "estimated_enemy_ttk,estimated_outcome,sim_win_rate,sim_loss_rate,"
                + "sim_timeout_rate,sim_ttk_median,sim_enemy_ttk_median,gap_reason\n");
        for (Row row : rows) {
            sb.append(String.format(Locale.US,
                    "%s,%d,%s,%d,%d,%d,%.1f,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%s,%.3f,%.3f,%.3f,%.1f,%.1f,%s\n",
                    row.playerClass(), row.level(), row.encounter(), row.playerHp(), row.enemyTotalHp(),
                    row.playerRawAttack(), row.enemyRawAttackSum(), row.playerArmor(), row.enemyArmorAvg(),
                    row.playerDamagePerRound(), row.enemyDamagePerRound(), row.estimatedPlayerTtk(),
                    row.estimatedEnemyTtk(), row.estimatedOutcome(), row.simWinRate(), row.simLossRate(),
                    row.simTimeoutRate(), row.simTtkMedian(), row.simEnemyTtkMedian(), row.gapReason()));
        }
        return sb.toString();
    }

    private static int hp(Entity entity) {
        Component health = entity.getComponent("Health");
        return health.getInt("maxHp");
    }

    private static int attack(Entity entity) {
        return entity.getComponent("CombatStats").getInt("attack");
    }

    private static int defense(Entity entity) {
        return entity.getComponent("CombatStats").getInt("defense");
    }

    private static int damageAfterArmor(int raw, int armor) {
        return Math.max(1, (int) Math.ceil((double) raw * raw / (armor + raw)));
    }

    private static String gapReason(String estimatedOutcome, BatchMetrics metrics) {
        String simOutcome = metrics.winRate() >= 0.5 ? "win" : "lose";
        if (metrics.timeoutRate() >= 0.5) return "sim_timeout";
        if (estimatedOutcome.equals(simOutcome)) return "matched";
        return "model_sim_diverged";
    }

    private static double nz(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }
}
