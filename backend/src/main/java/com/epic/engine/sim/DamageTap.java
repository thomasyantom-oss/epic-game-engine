package com.epic.engine.sim;

import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;

import java.util.function.IntSupplier;

/** One-time event tap with a per-fight active trace. Never removes shared handlers. */
public class DamageTap {
    private final IntSupplier roundSupplier;
    private FightTrace active;

    public DamageTap(EventBus bus, IntSupplier roundSupplier) {
        this.roundSupplier = roundSupplier;
        bus.on("combat.damage_dealt", 200, this::onDamage);
        bus.on("combat.mitigation", 200, this::onMitigation);
        bus.on("combat.unit_death", 200, this::onDeath);
    }

    public FightTrace begin() {
        active = new FightTrace();
        return active;
    }

    public FightTrace end() {
        FightTrace trace = active;
        active = null;
        return trace;
    }

    private void onDamage(GameEvent event) {
        if (active == null) return;
        active.addDamage(str(event.get("skillId")), str(event.get("targetId")), num(event.get("damage")));
    }

    private void onMitigation(GameEvent event) {
        if (active == null) return;
        active.addMitigation(str(event.get("targetId")), str(event.get("type")),
                num(event.get("raw")), num(event.get("final")));
    }

    private void onDeath(GameEvent event) {
        if (active == null) return;
        active.addDeath(str(event.get("killerId")), roundSupplier.getAsInt());
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
