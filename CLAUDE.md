# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Single-player text RPG engine with classic fantasy setting. Web-based, click-to-interact. All game content is data-driven through a mod system — the engine itself contains no game content.

Language: Chinese (UI text, scene content, commit messages, comments).

## Commands

```bash
# Start everything (backend + frontend)
./start.sh

# Backend only (port 8080)
cd backend && mvn spring-boot:run

# Frontend only (port 5173, proxies /api → localhost:8080)
cd frontend && npm run dev

# Run all backend tests
cd backend && mvn test

# Run a single test class
cd backend && mvn test -Dtest=CombatEngineTest

# Debug endpoints
curl http://localhost:8080/api/debug/health
curl http://localhost:8080/api/debug/log
curl http://localhost:8080/api/debug/state/player1
```

## Architecture

**Backend** (Java 21 + Spring Boot 3 + H2 + SnakeYAML):
- `mod/` — Mod discovery and content loading. `ModLoader` scans `mods/` for `mod.yaml` descriptors, `ModRegistry` loads scenes and encounters into memory at startup. Mods load by `load-order`; later mods override same-ID content.
- `scene/` — `SceneService` resolves raw YAML maps from `ModRegistry` into typed `Scene` objects (colored `TextSegment` descriptions + `SceneAction` list).
- `action/` — Command pattern. `ActionController` dispatches by `type` string to registered `ActionHandler` implementations (e.g., `MoveActionHandler`, `StartCombatActionHandler`). Handlers return `ActionResponse` with success flag and `refreshPanels` list telling the frontend which panels to update.
- `combat/` — Turn-based combat. `CombatService` holds active combats in-memory (`ConcurrentHashMap`). `CombatEngine` resolves rounds: speed-ordered initiative, front/back row positioning, damage calculation. Commands: ATTACK, DEFEND, FLEE.
- `save/` — JPA entity `PlayerState` persisted to H2 file DB (`backend/data/epic.mv.db`). Tracks current scene.
- `panel/` — `PanelRefresh` enum defines which UI panels to refresh after actions.
- `debug/` — `GameEventLog` ring buffer + debug REST endpoints.

**Frontend** (Vue 3 + Vite, no build-time type checking):
- 4-panel grid layout: main (map placeholder), func (status/settings), log (scrolling scene history), nav (action links).
- `composables/` — shared reactive state: `useGameState` (scene navigation), `useCombat` (combat flow), `usePanelRefresh` (selective panel updates), `useSettings` (font/theme prefs).
- `api/client.js` — thin fetch wrapper over REST endpoints.
- Combat mode (`CombatView`) replaces the normal grid when active.

**Data flow**: Frontend calls `POST /api/action` → backend dispatches to handler → handler mutates state, returns which panels changed → frontend re-fetches only those panels.

## Mod Content Format

Scenes go in `mods/<mod-id>/scenes/*.yaml`. Encounters in `mods/<mod-id>/encounters/*.yaml`. Each must have a unique `id` field. Scene descriptions use colored text segments:

```yaml
description:
  - text: "visible text"
    color: "#hexcolor"
```

## Tech Stack

- Java 21 (Amazon Corretto), Spring Boot 3.4, H2 (file mode), SnakeYAML
- Vue 3.5, Vite 6 (dev proxy `/api` → `localhost:8080`)
- No TypeScript, no linter configured, no CSS framework
