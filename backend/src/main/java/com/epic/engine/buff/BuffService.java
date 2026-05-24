package com.epic.engine.buff;

import com.epic.engine.core.*;
import org.graalvm.polyglot.HostAccess;
import java.util.Map;

public class BuffService {

    private final EventBus bus;
    private final EntityStore store;

    public BuffService(EventBus bus, EntityStore store) {
        this.bus = bus;
        this.store = store;
    }

    @HostAccess.Export
    public boolean applyBuff(String targetId, String buffId, Map<String, Object> data) {
        Entity target = store.get(targetId);
        if (target == null) return false;

        GameEvent applyEvent = new GameEvent("buff.apply");
        applyEvent.set("targetId", targetId);
        applyEvent.set("buffId", buffId);
        applyEvent.set("data", data);
        bus.fire("buff.apply", applyEvent);

        if (applyEvent.isCancelled()) return false;

        String compName = "Buff_" + buffId;
        Component existing = target.getComponent(compName);

        String stacking = data != null && data.containsKey("stacking")
                ? (String) data.get("stacking") : "replace";

        if (existing != null) {
            switch (stacking) {
                case "stack" -> {
                    int stacks = existing.has("stacks") ? existing.getInt("stacks") : 1;
                    int maxStacks = existing.has("maxStacks") ? existing.getInt("maxStacks") : 99;
                    existing.set("stacks", Math.min(stacks + 1, maxStacks));
                    if (data != null) data.forEach((k, v) -> {
                        if (!k.equals("stacking") && !k.equals("stacks")) existing.set(k, v);
                    });
                }
                case "refresh" -> {
                    if (data != null) data.forEach((k, v) -> {
                        if (!k.equals("stacking")) existing.set(k, v);
                    });
                }
                case "independent" -> {
                    String indepName = compName + "_" + System.currentTimeMillis();
                    Component comp = new Component(indepName);
                    comp.set("buffId", buffId);
                    if (data != null) data.forEach(comp::set);
                    target.addComponent(comp);
                }
                default -> {
                    target.removeComponent(compName);
                    Component comp = new Component(compName);
                    if (data != null) data.forEach(comp::set);
                    target.addComponent(comp);
                }
            }
        } else {
            Component comp = new Component(compName);
            if (data != null) data.forEach(comp::set);
            if (!comp.has("stacks")) comp.set("stacks", 1);
            target.addComponent(comp);
        }

        GameEvent appliedEvent = new GameEvent("buff.applied");
        appliedEvent.set("targetId", targetId);
        appliedEvent.set("buffId", buffId);
        bus.fire("buff.applied", appliedEvent);

        return true;
    }

    @HostAccess.Export
    public boolean removeBuff(String targetId, String buffId) {
        Entity target = store.get(targetId);
        if (target == null) return false;

        String compName = "Buff_" + buffId;
        if (!target.hasComponent(compName)) return false;

        GameEvent removeEvent = new GameEvent("buff.remove");
        removeEvent.set("targetId", targetId);
        removeEvent.set("buffId", buffId);
        bus.fire("buff.remove", removeEvent);

        if (removeEvent.isCancelled()) return false;

        target.removeComponent(compName);

        GameEvent removedEvent = new GameEvent("buff.removed");
        removedEvent.set("targetId", targetId);
        removedEvent.set("buffId", buffId);
        bus.fire("buff.removed", removedEvent);

        return true;
    }

    @HostAccess.Export
    public boolean hasBuff(String targetId, String buffId) {
        Entity target = store.get(targetId);
        if (target == null) return false;
        return target.hasComponent("Buff_" + buffId);
    }

    @HostAccess.Export
    public Component getBuffData(String targetId, String buffId) {
        Entity target = store.get(targetId);
        if (target == null) return null;
        return target.getComponent("Buff_" + buffId);
    }
}
