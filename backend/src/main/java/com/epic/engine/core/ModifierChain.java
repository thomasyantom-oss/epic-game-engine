package com.epic.engine.core;

import java.util.*;

public class ModifierChain {

    private final Entity entity;
    private final List<Modifier> modifiers = new ArrayList<>();
    private final Map<String, Component> baseState = new LinkedHashMap<>();
    private final Set<String> baseTags = new LinkedHashSet<>();

    public ModifierChain(Entity entity) {
        this.entity = entity;
        snapshotAll();
    }

    ModifierChain(Entity entity, boolean autoSnapshot) {
        this.entity = entity;
        if (autoSnapshot) snapshotAll();
    }

    private void snapshotAll() {
        baseState.clear();
        for (Component c : entity.getAllComponents()) {
            baseState.put(c.getType(), c.copy());
        }
        baseTags.clear();
        baseTags.addAll(entity.getTags());
    }

    public void setBase() {
        snapshotAll();
    }

    /**
     * Replaces the entire base snapshot with only the listed component types.
     *
     * This is intentionally not an incremental update API. Calling it with a
     * single structural component such as Position or EquipmentSlots drops every
     * other base component and can make additive modifiers inflate on future
     * recalculations. Use {@link #updateBase(String)} to refresh one component.
     */
    public void setBaseSelective(List<String> componentTypes) {
        baseState.clear();
        baseTags.clear();
        baseTags.addAll(entity.getTags());
        for (String type : componentTypes) {
            Component comp = entity.getComponent(type);
            if (comp != null) baseState.put(type, comp.copy());
        }
    }

    // 把单个组件的当前实时状态重新快照进 base(不动其它组件)。
    // 用于结构性状态(如 EquipmentSlots)被直接改写后,需让其在后续 recalculate 的
    // restoreBaseState 中存活,而非被旧 base 覆盖回去。
    public void updateBase(String componentType) {
        Component comp = entity.getComponent(componentType);
        if (comp != null) baseState.put(componentType, comp.copy());
        else baseState.remove(componentType);
    }

    public Map<String, Component> getBaseState() {
        return Collections.unmodifiableMap(baseState);
    }

    public void addModifier(Modifier modifier) {
        // id 唯一:同 id 重复注册视为替换(防 entity.loaded 重载/重复注册导致累加型 modifier 叠加膨胀)。
        modifiers.removeIf(m -> m.id().equals(modifier.id()));
        modifiers.add(modifier);
        modifiers.sort(Modifier::compareTo);
    }

    public void removeBySource(String source) {
        modifiers.removeIf(m -> m.source().equals(source));
    }

    public void removeById(String id) {
        modifiers.removeIf(m -> m.id().equals(id));
    }

    public void removeByTypeId(String typeId) {
        modifiers.removeIf(m -> typeId.equals(m.typeId()));
    }

    private void restoreBaseState() {
        // Only remove/restore components that are in base state
        for (String type : baseState.keySet()) {
            entity.removeComponent(type);
        }
        for (Map.Entry<String, Component> entry : baseState.entrySet()) {
            entity.addComponent(entry.getValue().copy());
        }
        // 注意:不复位标签。标签是运行时结构性状态(如战斗中的 combat:xxx),
        // modifier 只改组件字段、从不动标签。若在此复位会把进入战斗后才加的 combat 标签
        // 抹掉——任何战斗中触发 recalc 的技能(如战吼施加 buff)都会让单位"退出战斗"。
    }

    public void recalculate() {
        restoreBaseState();
        for (Modifier mod : modifiers) {
            mod.apply().accept(entity);
        }
    }

    public List<ModifierDiff> recalculateWithTracking() {
        restoreBaseState();
        List<ModifierDiff> diffs = new ArrayList<>();
        for (Modifier mod : modifiers) {
            Map<String, Map<String, Long>> before = snapshotLongs();
            mod.apply().accept(entity);
            Map<String, Map<String, Long>> after = snapshotLongs();
            diffs.add(new ModifierDiff(mod.id(), mod.typeId(), mod.label(), computeDelta(before, after)));
        }
        return diffs;
    }

    private Map<String, Map<String, Long>> snapshotLongs() {
        Map<String, Map<String, Long>> snap = new LinkedHashMap<>();
        for (Component c : entity.getAllComponents()) {
            Map<String, Long> fields = new LinkedHashMap<>();
            c.getAll().forEach((k, v) -> {
                if (v instanceof Number n) fields.put(k, n.longValue());
            });
            if (!fields.isEmpty()) snap.put(c.getType(), fields);
        }
        return snap;
    }

    private Map<String, Map<String, Long>> computeDelta(
            Map<String, Map<String, Long>> before,
            Map<String, Map<String, Long>> after) {
        Map<String, Map<String, Long>> delta = new LinkedHashMap<>();

        after.forEach((comp, fields) -> {
            Map<String, Long> compDelta = new LinkedHashMap<>();
            fields.forEach((field, afterVal) -> {
                long beforeVal = before.getOrDefault(comp, Map.of()).getOrDefault(field, 0L);
                long diff = afterVal - beforeVal;
                if (diff != 0) compDelta.put(field, diff);
            });
            if (!compDelta.isEmpty()) delta.put(comp, Collections.unmodifiableMap(compDelta));
        });

        // Detect removals: components/fields present in before but absent in after
        before.forEach((comp, fields) -> {
            Map<String, Long> afterFields = after.getOrDefault(comp, Map.of());
            Map<String, Long> compDelta = new LinkedHashMap<>(
                    delta.containsKey(comp) ? new LinkedHashMap<>(delta.get(comp)) : Map.of());
            fields.forEach((field, beforeVal) -> {
                if (!afterFields.containsKey(field) && beforeVal != 0) {
                    compDelta.put(field, 0L - beforeVal);
                }
            });
            if (!compDelta.isEmpty()) delta.put(comp, Collections.unmodifiableMap(compDelta));
        });

        return Collections.unmodifiableMap(delta);
    }

    public List<Modifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }
}
