package com.epic.engine.sim;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.session.SessionService;
import com.epic.engine.sim.reports.ArmorKSweepReport;
import com.epic.engine.sim.reports.BalanceCheckReport;
import com.epic.engine.sim.reports.BalanceModelReport;
import com.epic.engine.sim.reports.BaselineVsVariantReport;
import com.epic.engine.sim.reports.EarlyClassHealthCheck;
import com.epic.engine.sim.reports.ExplainReport;
import com.epic.engine.sim.reports.ResistCapSweepReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimIntegrationTest {
    @Autowired EventBus bus;
    @Autowired EntityStore store;
    @Autowired SessionService sessionService;
    @Autowired ScriptRuntime runtime;
    @Autowired MockMvc mockMvc;

    @Test
    void combatantBuilderUsesRealLevelPathAndSourceModifiers() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        String base = builder.buildPlayer("warrior", 10, List.of());
        int baseAttack = store.get(base).getComponent("CombatStats").getInt("attack");

        Map<String, Object> source = new HashMap<>();
        source.put("kind", "modifier");
        source.put("field", "PrimaryStats.力量");
        source.put("value", "+40");
        String variant = builder.buildPlayer("warrior", 10, List.of(source));
        int variantAttack = store.get(variant).getComponent("CombatStats").getInt("attack");

        assertThat(store.get(base).getComponent("Experience").getInt("level")).isEqualTo(10);
        assertThat(variantAttack).isGreaterThan(baseAttack);
        store.remove(base);
        store.remove(variant);
    }

    @Test
    void reportsProducePmFacingCsvAndSummary() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        List<EarlyClassHealthCheck.Row> rows = new EarlyClassHealthCheck(bus, store, sessionService)
                .run(List.of("warrior", "mage"), List.of(1, 5), List.of("forest_goblin"), 2);
        assertThat(rows).hasSize(4);
        assertThat(EarlyClassHealthCheck.toCsv(rows)).startsWith(
                "class,level,encounter,win_rate,ttk_median,win_hp_remaining,loss_enemy_hp_remaining\n");

        CombatTuning tuning = new CombatTuning();
        runtime.bindService("tuning", tuning);
        String armorCsv = new ArmorKSweepReport(builder, store, bus, tuning).runCsv(
                List.of(5, 10), new SimSetup("warrior", 5, "forest_goblin",
                        PolicyKind.HEURISTIC, List.of(), 50), 2);
        assertThat(armorCsv).startsWith(
                "armor_K,win_rate,player_survival_turns,enemy_ttk,player_ttk_median,win_hp_remaining\n");
        assertThat(armorCsv.split("\n")).hasSize(3);

        String resistCsv = new ResistCapSweepReport(builder, store, bus, tuning).runCsv(
                List.of(25, 75), -50, new SimSetup("mage", 10, "mitigation_high_resist_test",
                        PolicyKind.SCRIPTED, List.of("fireball", "fireball", "fireball"), 3), 2);
        assertThat(resistCsv).startsWith("resist_cap,win_rate,ttk_median,win_hp_remaining\n");
        assertThat(resistCsv.split("\n")).hasSize(3);

        Map<String, Object> source = new HashMap<>();
        source.put("kind", "modifier");
        source.put("field", "PrimaryStats.力量");
        source.put("value", "+40");
        String summary = new BaselineVsVariantReport(builder, store, bus)
                .run("warrior", 10, List.of(source), 2);
        assertThat(summary).contains("offense_index").contains("defense_index");

        ExplainReport.Summary explain = new ExplainReport(builder, store, bus).run(
                new SimSetup("warrior", 1, "forest_goblin", PolicyKind.HEURISTIC, List.of(), 50), 2);
        assertThat(ExplainReport.toCsv(explain)).contains("damage_by_skill").contains("kill_source");

        List<BalanceModelReport.Row> modelRows = new BalanceModelReport(bus, store, sessionService)
                .run(List.of("warrior"), List.of(1), "forest_goblin", 1, 50);
        assertThat(BalanceModelReport.toCsv(modelRows))
                .contains("estimated_player_ttk")
                .contains("sim_win_rate");

        String checkCsv = BalanceCheckReport.toCsv(new BalanceCheckReport().run(modelRows));
        assertThat(checkCsv)
                .contains("status")
                .contains("suggestion")
                .contains("estimated_player_ttk");
    }

    @Test
    void simReportsAreAvailableThroughApi() throws Exception {
        mockMvc.perform(get("/api/sim/reports/early-health.csv")
                        .param("classes", "warrior")
                        .param("levels", "1-2")
                        .param("iterations", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith(
                        "class,level,encounter,win_rate,ttk_median,win_hp_remaining,loss_enemy_hp_remaining\n")));

        mockMvc.perform(get("/api/sim/reports/explain.csv")
                        .param("playerClass", "warrior")
                        .param("level", "1")
                        .param("iterations", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("damage_by_skill")));

        mockMvc.perform(get("/api/sim/reports/balance-model.csv")
                        .param("classes", "warrior")
                        .param("levels", "1")
                        .param("iterations", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("estimated_player_ttk")));

        mockMvc.perform(get("/api/sim/reports/balance-check.csv")
                        .param("classes", "warrior")
                        .param("levels", "1")
                        .param("iterations", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("suggestion")));
    }

    @Test
    void encounterScalingRaisesGoblinAgilityAndConstitutionWithHeroLevel() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);

        String level1Player = builder.buildPlayer("warrior", 1, List.of());
        CombatantBuilder.Spawn level1Spawn = builder.spawnEncounter(level1Player, "forest_goblin");
        int level1Hp = store.get(level1Spawn.enemyIds().get(0)).getComponent("Health").getInt("maxHp");
        int level1Attack = store.get(level1Spawn.enemyIds().get(0)).getComponent("CombatStats").getInt("attack");
        int level1Defense = store.get(level1Spawn.enemyIds().get(0)).getComponent("CombatStats").getInt("defense");
        builder.cleanup(level1Player, level1Spawn);
        store.remove(level1Player);

        String level10Player = builder.buildPlayer("warrior", 10, List.of());
        CombatantBuilder.Spawn level10Spawn = builder.spawnEncounter(level10Player, "forest_goblin");
        int level10Hp = store.get(level10Spawn.enemyIds().get(0)).getComponent("Health").getInt("maxHp");
        int level10CurrentHp = store.get(level10Spawn.enemyIds().get(0)).getComponent("Health").getInt("hp");
        int level10Attack = store.get(level10Spawn.enemyIds().get(0)).getComponent("CombatStats").getInt("attack");
        int level10Defense = store.get(level10Spawn.enemyIds().get(0)).getComponent("CombatStats").getInt("defense");
        builder.cleanup(level10Player, level10Spawn);
        store.remove(level10Player);

        assertThat(level10Hp).isGreaterThan(level1Hp);
        assertThat(level10CurrentHp).isEqualTo(level10Hp);
        assertThat(level10Attack).isGreaterThan(level1Attack);
        assertThat(level10Defense).isGreaterThan(level1Defense);
    }

    @Test
    void simulatorBaselineWeaponAndArmorScaleWithHeroLevel() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);

        String level1Player = builder.buildPlayer("warrior", 1, List.of());
        int level1Attack = store.get(level1Player).getComponent("CombatStats").getInt("attack");
        int level1Defense = store.get(level1Player).getComponent("CombatStats").getInt("defense");
        store.remove(level1Player);

        String level50Player = builder.buildPlayer("warrior", 50, List.of());
        int level50Attack = store.get(level50Player).getComponent("CombatStats").getInt("attack");
        int level50Defense = store.get(level50Player).getComponent("CombatStats").getInt("defense");
        store.remove(level50Player);

        assertThat(level50Attack).isGreaterThan(level1Attack);
        assertThat(level50Attack).isGreaterThanOrEqualTo(54);
        assertThat(level50Defense).isEqualTo(level1Defense + 49);
    }
}
