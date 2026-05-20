package com.epic.engine.save;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerStateRepository extends JpaRepository<PlayerState, String> {
    Optional<PlayerState> findByPlayerId(String playerId);
}
