package com.epic.engine.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class EntityData {

    @Id
    private String entityId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String componentsJson;

    @Column
    private String tagsJson;

    public EntityData() {}

    public EntityData(String entityId, String componentsJson, String tagsJson) {
        this.entityId = entityId;
        this.componentsJson = componentsJson;
        this.tagsJson = tagsJson;
    }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getComponentsJson() { return componentsJson; }
    public void setComponentsJson(String componentsJson) { this.componentsJson = componentsJson; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
}
