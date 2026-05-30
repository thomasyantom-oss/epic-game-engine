# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

Single-player text RPG engine with classic fantasy setting. Web-based, click-to-interact. The engine contains no game content — all content is data-driven through a mod system (JS handlers + YAML data).

Language: Chinese (UI text, scene content, commit messages, comments).

## Commands

```bash
# Start everything (backend + frontend)
./start.sh          # Linux/Mac
./start.ps1         # Windows PowerShell

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
curl http://localhost:8080/api/debug/state/{sessionToken}
```

## Architecture

### Core Design

Event-driven microkernel. Java engine provides infrastructure (ECS, EventBus, script sandbox). All game rules live in GraalJS modules under `mods/`.

**Data flow**: Frontend `POST /api/action` → engine fires event → JS handlers process logic → engine builds snapshot → frontend renders.

**Two core APIs**: `POST /api/action` (execute) + `GET /api/snapshot` (full UI state).

### Backend (Java 21 + Spring Boot 3 + H2 + GraalJS)

Package `com.epic.engine.*`:
- `core/` — EventBus (fire/on/cancel/priority), Entity + Component (ECS), ModifierChain, TagIndex, EntityStore.
- `script/` — GraalJS sandbox (ScriptRuntime), exposes engine/store API to JS handlers. `synchronized` execution for thread safety.
- `module/` — ModuleLoader discovers mods, loads JS handlers/skills/buffs from `mods/` directories. HotReloader watches file changes for live reload.
- `buff/` — BuffService: applyBuff/removeBuff API, lifecycle events, 4 stacking strategies, per-turn tick.
- `generator/` — Schema-driven entity generator (SchemaRegistry loads main/sub schema YAML).
- `session/` — Session token management, character persistence across restarts.
- `snapshot/` — SnapshotService builds full UI state JSON (map, combat, status bars, colors, actions) for frontend.
- `persistence/` — H2 file DB (`backend/data/epic.mv.db`), persistent-tagged entities auto-saved.
- `debug/` — GameEventLog ring buffer + debug REST endpoints.
- `config/` — Spring Boot config, CORS, REST controller routing.

### Frontend (Vue 3 + Vite, no TypeScript)

Stateless snapshot renderer — one JSON snapshot drives the entire UI.

- `api/client.js` — thin fetch wrapper (action + snapshot endpoints).
- `components/` — SnapshotRenderer (top-level router), MapGrid, BattleGrid, AnimationLayer, StatusBars, ActionPanel, CharacterSelect/Create, SettingsPanel, TabPanel, TextRenderer, ActionLink.
- `composables/` — useAnimationPlayer (animation queue + sequencing), useSettings (font/theme prefs, localStorage).
- `styles/` — CSS variables, base styles. Colors driven by backend `colorMap` via dynamic CSS vars.

**UI layout**: 4-panel grid. Left-top: map. Right-top: func panel (character/settings tabs). Left-bottom: event/combat log. Right-bottom: action links. Combat replaces map panel content (not full-screen takeover).

### Mod System (`mods/base-rules/`)

All game rules and content live here. Engine loads JS handlers + YAML data at startup.

- `handlers/` — JS event handlers grouped by domain: `combat/` (flow, initiative, damage, death, events, log, start), `map/` (movement, pathfinding), `ui/` (actions, status_bars), `world/` (bootstrap), `character/` (select).
- `skills/` — Skill definitions (YAML metadata + animation sequences) + JS implementations (conditions, effects).
- `buffs/` — Buff definitions (YAML) + JS tick/apply/remove logic.
- `entities/` — YAML data: encounters, maps (world_map), terrains.
- `schemas/` — Main schemas (character) + sub schemas (class_warrior, class_mage). Drive character creation forms.
- `colors.yaml` — Semantic color definitions (player/enemy/damage/mana/highlight), loaded into snapshot for frontend CSS vars.
- `mod.yaml` — Mod descriptor (id, name, load-order).

### Key Systems

**Combat**: Turn-based, speed-ordered initiative. 3×3 grid positioning (front/back row). Skills with AOE (offset-based targeting), MP cost, animations. Buff lifecycle (apply/tick/remove). All commands generated dynamically from skills — no hardcoded actions.

**Animation**: 12 primitives (pulse, flash, projectile, beam, slash, impact, shake, damage_number, buff_up, debuff_down, mark_dead, indicator_add). Defined in skill YAML, played sequentially per combat event. HP/log updates sync with animation playback.

**Map**: 10×10 colored grid. Terrain tags control passability. WASD + click-to-pathfind (A*). POI interactions trigger encounters.

**Hot Reload**: HotReloader watches `mods/` for JS/YAML changes, reloads without restart.

## Tech Stack

- Java 21 (Amazon Corretto), Spring Boot 3.4, H2 (file mode), GraalJS (polyglot scripting)
- Vue 3.5, Vite 6 (dev proxy `/api` → `localhost:8080`)
- No TypeScript, no linter configured, no CSS framework
- SnakeYAML for data loading, GraalJS for game logic scripting
