package com.epic.engine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityDataRepository extends JpaRepository<EntityData, String> {
}
