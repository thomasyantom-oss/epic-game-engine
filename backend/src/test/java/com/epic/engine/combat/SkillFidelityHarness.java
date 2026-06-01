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
        // Pre-load the future skill engine if present. The handlers/skill/ dir does not
        // exist before the refactor (Tasks 2-6 create it); absent files are safely skipped.
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

    // Private: callers must use fire(), which resets the scene each call. The harness is
    // single-use per fire (the test builds a fresh instance per skill) — reusing one
    // instance across multiple fire() calls is not supported (TagIndex retains stale refs).
    private void scene() {
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
        Component ds = new Component("DerivedStats");
        ds.set("物理强度", 0);
        ds.set("法术强度", atk);   // 复用 atk 参数当法强:mage(atk=10) → 火球 8+⌈10×0.5⌉=13
        ds.set("精神强度", 0);
        e.addComponent(ds);
        Component prim = new Component("PrimaryStats");
        // 确定性测试值：让各属性区分明显，便于核对公式
        prim.set("力量", 12); prim.set("敏捷", 7); prim.set("智力", 10);
        prim.set("体质", 8);  prim.set("意志", 5); prim.set("weaponAttr", "力量");
        e.addComponent(prim);
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
        // Canonicalize: sort map keys (done by MAPPER) and neutralize only target
        // iteration order in per-combat-event effects/animation arrays.
        String raw = MAPPER.writeValueAsString(queue);
        JsonNode node = MAPPER.readTree(raw);
        // Build a name→id lookup from the scene entities for light_field segments fix.
        Map<String, String> nameToId = buildNameToIdMap();
        JsonNode canonical = canonicalizeQueue(node, nameToId);
        return MAPPER.writeValueAsString(canonical);
    }

    /**
     * Build a map from entity display name → entity id, for all entities in the store
     * that have a Name component. Used to canonicalize per-target segments.
     */
    private Map<String, String> buildNameToIdMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Entity entity : store.all()) {
            if (entity.hasComponent("Name")) {
                String name = (String) entity.getComponent("Name").get("value");
                if (name != null) map.put(name, entity.getId());
            }
        }
        return map;
    }

    /**
     * Canonicalize the CombatEvents queue (a JSON array of combat event objects).
     *
     * Design principle: the ONLY source of non-determinism is that
     * EntityStore.getByTagAsList() is backed by ConcurrentHashMap whose iteration
     * order is arbitrary, so the ORDER OF TARGETS within per-target arrays varies
     * between runs. Everything else (animation step sequence for a given target,
     * text-segment order within a log sentence, leading delivery steps) is authored
     * and meaningful — it must be preserved verbatim.
     *
     * Canonicalization rules applied per combat-event object in the queue:
     *   1. Jackson's ORDER_MAP_ENTRIES_BY_KEYS already sorts object keys — no action.
     *   2. The "effects" array and the "animation" array are STABLE-sorted by each
     *      element's "target" field (string). Elements without a "target" field
     *      (delivery steps like slash/beam, or actor-targeted steps) are assigned the
     *      sentinel key "" so they sort before any target id and keep their relative
     *      order among themselves. This neutralises arbitrary entity-iteration order
     *      while preserving the per-target step sequence (impact→shake→damage_number).
     *   3. "segments" is NOT reordered — sentence structure is authored and protected.
     *      EXCEPTION: events with logCount > 1 have per-target log sentences; which
     *      target's sentence lands in "segments" is non-deterministic. For those events
     *      we normalise by finding the canonical first target (effects[0].target after
     *      step 2) and, if the current segments belong to a different target, we
     *      replace only the target-name text segment (the "enemy"-coloured one) with
     *      the canonical target's display name. Sentence structure is untouched.
     *   4. No other array is sorted; no recursive descent into nested structures
     *      beyond the top-level queue entries.
     */
    private JsonNode canonicalizeQueue(JsonNode queue, Map<String, String> nameToId) {
        if (!queue.isArray()) return queue;

        // Build reverse lookup: id → display name, for per-target segments fix.
        Map<String, String> idToName = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : nameToId.entrySet()) {
            idToName.put(entry.getValue(), entry.getKey());
        }

        ArrayNode result = MAPPER.createArrayNode();
        for (JsonNode eventNode : queue) {
            if (!eventNode.isObject()) { result.add(eventNode); continue; }

            ObjectNode event = (ObjectNode) eventNode;

            // Step 2a: stable-sort "effects" by target field.
            if (event.has("effects")) {
                event.set("effects", stableSortByTarget(event.get("effects")));
            }

            // Step 2b: stable-sort "animation" by target field.
            if (event.has("animation")) {
                event.set("animation", stableSortByTarget(event.get("animation")));
            }

            // Step 3 (exception): for logCount > 1, canonicalise which target's
            // sentence is stored in "segments" by using the canonical first-target
            // (effects[0].target after sorting) and replacing the "enemy"-colored
            // text segment if it belongs to a different target.
            int logCount = event.has("logCount") ? event.get("logCount").asInt(0) : 0;
            if (logCount > 1 && event.has("effects") && event.has("segments")) {
                JsonNode effects = event.get("effects");
                if (effects.isArray() && effects.size() > 0) {
                    JsonNode firstEffect = effects.get(0);
                    String canonicalTargetId = firstEffect.has("target")
                            ? firstEffect.get("target").asText() : null;
                    String canonicalTargetName = canonicalTargetId != null
                            ? idToName.get(canonicalTargetId) : null;

                    if (canonicalTargetName != null && event.get("segments").isArray()) {
                        // Replace the "enemy"-coloured text segment with canonical name.
                        ArrayNode segments = (ArrayNode) event.get("segments");
                        ArrayNode fixedSegments = MAPPER.createArrayNode();
                        for (JsonNode seg : segments) {
                            if (seg.isObject() && seg.has("color")
                                    && "enemy".equals(seg.get("color").asText())
                                    && seg.has("text")) {
                                // Replace target name with canonical one.
                                ObjectNode fixedSeg = MAPPER.createObjectNode();
                                Iterator<Map.Entry<String, JsonNode>> fields = seg.fields();
                                while (fields.hasNext()) {
                                    Map.Entry<String, JsonNode> f = fields.next();
                                    if ("text".equals(f.getKey())) {
                                        fixedSeg.put("text", canonicalTargetName);
                                    } else {
                                        fixedSeg.set(f.getKey(), f.getValue());
                                    }
                                }
                                fixedSegments.add(fixedSeg);
                            } else {
                                fixedSegments.add(seg);
                            }
                        }
                        event.set("segments", fixedSegments);
                    }
                }
            }

            result.add(event);
        }
        return result;
    }

    /**
     * Stable-sort an array node by each element's "target" field (string comparison).
     * Elements without a "target" field get the sentinel key "" (empty string), which
     * sorts before any entity id, so delivery steps (slash, beam, actor pulse) float
     * to the front in stable relative order.
     *
     * A stable sort (List.sort with Comparator) preserves the relative order of
     * elements with the same target key, which is exactly what we want:
     *   - Multiple steps for the same target (impact → shake → damage_number) stay
     *     in their authored order.
     *   - Multiple no-target delivery steps stay in their authored order.
     */
    private ArrayNode stableSortByTarget(JsonNode array) {
        if (!array.isArray()) return MAPPER.createArrayNode();
        List<JsonNode> elements = new ArrayList<>();
        for (JsonNode el : array) elements.add(el);
        // Stable sort: List.sort is guaranteed stable in Java.
        elements.sort(Comparator.comparing(el ->
                (el.isObject() && el.has("target")) ? el.get("target").asText() : ""));
        ArrayNode sorted = MAPPER.createArrayNode();
        for (JsonNode el : elements) sorted.add(el);
        return sorted;
    }

    public String goldenPath(String skill) {
        return "src/test/resources/golden/" + skill + ".json";
    }

    @Override
    public void close() {
        runtime.close();
    }
}
