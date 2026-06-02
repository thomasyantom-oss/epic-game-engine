# CODEX.md

## Role

Codex is the implementation SDE for this project.

Claude is the senior SDE for design, planning, architecture review, and code review when the project owner chooses to use Claude. Claude's output should be treated as senior technical guidance, not as automatically executable truth. Codex should still verify plans against the repository before implementing.

The project owner is the PM / stakeholder / tester. They set priorities, define acceptance, provide manual test feedback, and decide when a change is good enough.

## Responsibility Split

Codex owns:

- concrete implementation work
- local code inspection
- small refactors
- test and fixture creation
- regression fixes
- running relevant build/test commands
- documenting what changed and what remains risky
- maintaining this `CODEX.md` when the owner updates Codex's working rules

Claude owns, when involved:

- design direction
- implementation plan review
- architecture critique
- higher-level tradeoff analysis
- senior code review

The owner owns:

- requirements and priority calls
- acceptance decisions
- gameplay/product direction
- manual playtesting feedback

## Working Rules

- Read `AGENT.md` first when starting code-side work.
- Read this `CODEX.md` after `AGENT.md`.
- Treat `refactor-v1.md` as the current implementation queue for the safe skill/equipment authoring pass unless the owner gives newer direction.
- Prefer small, reviewable changes with behavior preserved.
- Do not tighten engine extensibility just because something is powerful or risky. This project intentionally keeps custom skill/equipment behavior possible.
- Fix or test problems that are accidental sources of duplication, lifecycle bugs, invalid generated content, or unclear authoring patterns.
- Put large refactors, low-priority performance work, and design-dependent items into `docs/backlog.md` instead of implementing them immediately.
- Do not edit roadmap, chapter planning, product direction, or story/design docs unless explicitly asked.

## Current Refactor Boundary

For the V1 safe authoring pass:

- Keep bespoke skill JS allowed.
- Keep `ScriptRuntime` host exposure as-is.
- Do not force all skills into pure YAML.
- Do not optimize small-map pathfinding.
- Treat per-buff combatant scans as low priority for now.
- Prioritize content validation tests, lifecycle tests, and small helper extraction.

## Communication

When implementing:

- state the files/areas being touched before editing
- run the most relevant tests after changes
- report behavior preserved or changed
- call out anything not tested
- after each completed code change, provide a ready-to-send Claude review prompt that summarizes the goal, files changed, tests run, and the specific review focus

When reviewing or planning:

- separate immediate implementation candidates from backlog items
- distinguish confirmed bugs from intentional extensibility risks
- do not over-escalate low-priority performance concerns without evidence
