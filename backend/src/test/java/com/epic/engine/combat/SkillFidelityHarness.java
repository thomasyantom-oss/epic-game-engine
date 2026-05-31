package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Deterministic single-skill capture for fidelity testing. */
public class SkillFidelityHarness implements AutoCloseable {
    final EventBus bus = new EventBus();
    final EntityStore store = new EntityStore();
    final ScriptRuntime runtime;
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public SkillFidelityHarness() throws Exception {
        runtime = new ScriptRuntime(bus, store);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        runtime.bindService("buffs", new BuffService(bus, store));
        Path h = Path.of("../mods/base-rules/handlers/combat");
        for (String f : new String[]{"initiative.js", "damage_calc.js", "death_check.js",
                "combat_flow.js", "combat_events.js", "combat_log.js"}) {
            runtime.execute(Files.readString(h.resolve(f)), f);
        }
        Path skl = Path.of("../mods/base-rules/handlers/skill");
        if (Files.isDirectory(skl)) {
            for (String f : new String[]{"00_skill_lib.js", "01_effects.js", "02_dispatch.js"}) {
                if (Files.exists(skl.resolve(f)))
                    runtime.execute(Files.readString(skl.resolve(f)), f);
            }
        }
        try (var files = Files.list(Path.of("../mods/base-rules/skills"))) {
            files.filter(p -> p.toString().endsWith(".js")).sorted().forEach(p -> {
                try { runtime.execute(Files.readString(p), p.getFileName().toString()); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        }
    }

    public void scene() {
        store.clear();
        addUnit("mage",    "法师",     100, 10, 5, 6, true,  "FRONT", 0);
        addUnit("goblin1", "哥布林甲", 200,  6, 2, 3, false, "FRONT", 0);
        addUnit("goblin2", "哥布林乙", 200,  6, 2, 3, false, "MID",   0);
        addUnit("goblin3", "哥布林丙", 200,  6, 2, 3, false, "BACK",  0);

        Entity combat = new Entity("battle1");
        Component st = new Component("CombatState");
        st.set("round", 1);
        st.set("phase", "RESOLVE");
        combat.addComponent(st);
        Component ce = new Component("CombatEvents");
        ce.set("queue", new ArrayList<>());
        combat.addComponent(ce);
        Component cl = new Component("CombatLog");
        cl.set("entries", new ArrayList<>());
        combat.addComponent(cl);
        store.add(combat);
    }

    private void addUnit(String id, String name, int hp, int atk, int def, int spd,
                         boolean player, String row, int slot) {
        Entity e = new Entity(id);
        Component hh = new Component("Health");
        hh.set("hp", hp);
        hh.set("maxHp", hp);
        e.addComponent(hh);
        Component s = new Component("CombatStats");
        s.set("attack", atk);
        s.set("defense", def);
        s.set("speed", spd);
        e.addComponent(s);
        Component n = new Component("Name");
        n.set("value", name);
        e.addComponent(n);
        Component p = new Component("CombatPosition");
        p.set("row", row);
        p.set("slot", slot);
        e.addComponent(p);
        e.addTag(player ? "player" : "enemy");
        e.addTag("combat:battle1");
        store.add(e);
    }

    public String fire(String type, String targetId) throws Exception {
        scene();
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("type", type);
        if (targetId != null) cmd.put("targetId", targetId);
        GameEvent e = new GameEvent("combat.unit_action");
        e.set("combatId", "battle1");
        e.set("actorId", "mage");
        e.set("command", cmd);
        bus.fire("combat.unit_action", e);
        Object queue = store.get("battle1").getComponent("CombatEvents").get("queue");
        // Canonicalize: sort map keys (done by MAPPER) and sort list elements by their
        // serialized form so that AOE effects iterating over unordered sets are stable.
        String raw = MAPPER.writeValueAsString(queue);
        JsonNode node = MAPPER.readTree(raw);
        JsonNode canonical = canonicalize(node);
        return MAPPER.writeValueAsString(canonical);
    }

    /**
     * Recursively canonicalize a JSON node:
     * - ObjectNode: keys are already sorted by ORDER_MAP_ENTRIES_BY_KEYS
     * - ArrayNode: sort child elements by their own serialized string, then recurse
     */
    private JsonNode canonicalize(JsonNode node) throws Exception {
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            List<JsonNode> children = new ArrayList<>();
            for (JsonNode child : arr) {
                children.add(canonicalize(child));
            }
            // Sort by serialized string for determinism (handles unordered set iteration)
            children.sort(Comparator.comparing(JsonNode::toString));
            ArrayNode result = MAPPER.createArrayNode();
            for (JsonNode child : children) {
                result.add(child);
            }
            return result;
        } else if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            ObjectNode result = MAPPER.createObjectNode();
            // Iterate in field order (already sorted by Jackson's ORDER_MAP_ENTRIES_BY_KEYS)
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                result.set(entry.getKey(), canonicalize(entry.getValue()));
            }
            return result;
        }
        return node;
    }

    public String goldenPath(String skill) {
        return "src/test/resources/golden/" + skill + ".json";
    }

    @Override
    public void close() {
        runtime.close();
    }
}
