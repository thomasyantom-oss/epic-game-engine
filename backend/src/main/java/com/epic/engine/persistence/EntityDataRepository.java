package com.epic.engine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EntityDataRepository extends JpaRepository<EntityData, String> {

    @Query("SELECT e FROM EntityData e WHERE e.tagsJson LIKE %:tag%")
    List<EntityData> findByTagContaining(@Param("tag") String tag);
}
