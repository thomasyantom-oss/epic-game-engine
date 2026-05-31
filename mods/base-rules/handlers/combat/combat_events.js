// Combat event queue for front-end playback
// Events are per-round — cleared at start of each resolve, consumed by frontend

// Initialize/clear event queue at round start
engine.on("combat.resolve_round", 99, function(event) {
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null) return;

    if (!combat.hasComponent("CombatEvents")) {
        combat.addComponent(engine.newComponent("CombatEvents"));
    }
    combat.getComponent("CombatEvents").set("queue", engine.newList());
});

// Resolve animation from skill YAML — replace actor/target placeholders with real IDs
function resolveSkillAnimation(skillId, actorId, targetId) {
    var skillDef = engine.loadYaml("skills/" + skillId + ".yaml");
    if (skillDef === null) return null;
    var animDef = skillDef.get("animation");
    if (animDef === null || animDef.size() === 0) return null;

    var actor = store.get(actorId);
    var actorSide = actor !== null && actor.hasTag("player") ? "player" : "enemy";

    var resolved = engine.newList();
    for (var i = 0; i < animDef.size(); i++) {
        var step = animDef.get(i);
        var anim = engine.newMap();
        var keys = step.keySet().iterator();
        while (keys.hasNext()) {
            var key = keys.next();
            var val = step.get(key);
            if (val === "actor") val = actorId;
            else if (val === "target") val = targetId;
            else if (val === "actor_side") val = actorSide;
            anim.put(key, val);
        }
        resolved.add(anim);
    }
    return resolved;
}

// Record attack events
engine.on("combat.damage_dealt", 60, function(event) {
    if (event.has("skipLog")) return;
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null || !combat.hasComponent("CombatEvents")) return;

    var attackerId = event.get("attackerId");
    var targetId = event.get("targetId");
    var damage = event.get("damage");
    var skillId = event.has("skillId") ? event.get("skillId") : null;

    var attacker = store.get(attackerId);
    var target = store.get(targetId);
    var attackerName = attacker.hasComponent("Name") ? attacker.getComponent("Name").getString("value") : attackerId;
    var targetName = target.hasComponent("Name") ? target.getComponent("Name").getString("value") : targetId;
    var attackerSide = attacker.hasTag("player") ? "player" : "enemy";
    var targetSide = target.hasTag("player") ? "player" : "enemy";

    var evt = engine.newMap();

    // Segments (display text)
    var segments = engine.newList();
    var s1 = engine.newMap(); s1.put("text", attackerName); s1.put("color", attackerSide); segments.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 对 "); s2.put("color", "text"); segments.add(s2);
    var s3 = engine.newMap(); s3.put("text", targetName); s3.put("color", targetSide); segments.add(s3);
    var s4 = engine.newMap(); s4.put("text", " 造成 "); s4.put("color", "text"); segments.add(s4);
    var s5 = engine.newMap(); s5.put("text", "" + damage); s5.put("color", "damage"); segments.add(s5);
    var s6 = engine.newMap(); s6.put("text", " 点伤害"); s6.put("color", "text"); segments.add(s6);
    evt.put("segments", segments);

    // Effects (for state tracking)
    var effects = engine.newList();
    var hpEffect = engine.newMap();
    hpEffect.put("target", targetId);
    hpEffect.put("type", "hp_change");
    hpEffect.put("amount", -damage);
    hpEffect.put("hp", target.getComponent("Health").getInt("hp"));
    hpEffect.put("maxHp", target.getComponent("Health").getInt("maxHp"));
    effects.add(hpEffect);
    evt.put("effects", effects);

    // Animation — read from skill YAML, fallback to default
    var animation = null;
    if (skillId !== null) {
        animation = resolveSkillAnimation(skillId, attackerId, targetId);
    }
    if (animation === null) {
        // Default fallback: lunge + impact + shake + damage_number
        animation = engine.newList();
        var lungeAnim = engine.newMap();
        lungeAnim.put("type", "lunge");
        lungeAnim.put("target", attackerId);
        lungeAnim.put("side", attackerSide);
        animation.add(lungeAnim);
        var impactAnim = engine.newMap();
        impactAnim.put("type", "impact");
        impactAnim.put("target", targetId);
        animation.add(impactAnim);
        var shakeAnim = engine.newMap();
        shakeAnim.put("type", "shake");
        shakeAnim.put("target", targetId);
        shakeAnim.put("intensity", "normal");
        animation.add(shakeAnim);
    }
    // Append damage_number only if not already in animation
    var hasDmgNum = false;
    for (var a = 0; a < animation.size(); a++) {
        if (animation.get(a).get("type") === "damage_number") { hasDmgNum = true; break; }
    }
    if (!hasDmgNum) {
        var dmgAnim = engine.newMap();
        dmgAnim.put("type", "damage_number");
        dmgAnim.put("target", targetId);
        dmgAnim.put("value", -damage);
        dmgAnim.put("color", "damage");
        animation.add(dmgAnim);
    } else {
        // Fill in value for existing damage_number entries
        for (var a = 0; a < animation.size(); a++) {
            var an = animation.get(a);
            if (an.get("type") === "damage_number" && !an.containsKey("value")) {
                an.put("value", -damage);
            }
        }
    }

    evt.put("animation", animation);

    combat.getComponent("CombatEvents").get("queue").add(evt);
});

// NOTE: defend presentation (log + buff_up animation + buff_applied effect) is owned solely by
// the bespoke skills/defend.js handler. A second recorder used to live here (prio 200) and
// produced a duplicate "进行防御" log line + a second buff_up animation — removed.

// Record death events
engine.on("combat.unit_death", 60, function(event) {
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null || !combat.hasComponent("CombatEvents")) return;

    var deadId = event.get("deadId");
    var dead = store.get(deadId);
    var deadName = dead.hasComponent("Name") ? dead.getComponent("Name").getString("value") : deadId;
    var deadSide = dead.hasTag("player") ? "player" : "enemy";

    var evt = engine.newMap();
    var segments = engine.newList();
    var s1 = engine.newMap(); s1.put("text", deadName); s1.put("color", deadSide); segments.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 被击败了！"); s2.put("color", "enemy"); segments.add(s2);
    evt.put("segments", segments);

    var effects = engine.newList();
    var deathEffect = engine.newMap();
    deathEffect.put("target", deadId);
    deathEffect.put("type", "death");
    effects.add(deathEffect);
    evt.put("effects", effects);

    var animation = engine.newList();
    var shakeAnim = engine.newMap();
    shakeAnim.put("type", "shake");
    shakeAnim.put("target", deadId);
    shakeAnim.put("intensity", "heavy");
    animation.add(shakeAnim);
    evt.put("animation", animation);

    combat.getComponent("CombatEvents").get("queue").add(evt);
});
