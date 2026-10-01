# CLAUDE.md

This project uses the saga plugin. See `.claude/workflow/state.json` for current phase.

## Context

Project context lives in `.claude/context/ARCHITECTURE.md`, `PATTERNS.md`,
`DOMAIN.md`, and `TESTING.md`. `/design`, `/implement`, and
`/document-service` already read these directly when they need them — read
them yourself only if you're working on something ad hoc that needs that
context too. They're deliberately not `@`-imported here so they don't load
into every session regardless of whether the task needs them.

## Skill precedence

The saga plugin provides five skills with generic best-practice defaults:
`spring-boot-patterns`, `spring-data-jpa`, `jooq-conventions`,
`postgres-migrations`, `testcontainers-testing`. They assume **Java 25 and
Spring Boot 4.x** — records, sealed types, pattern matching, text blocks, and
virtual threads are the default, not an aspiration. No preview APIs.

`spring-data-jpa` and `jooq-conventions` are mutually exclusive — this project's
choice is recorded under `## Persistence stack` in
`.claude/context/PATTERNS.md`. Load that one, not both.

Each skill's `## Codebase conventions` section instructs Claude to load a
project-specific override file from `.claude/context/conventions/<skill-name>.md`.
**When that file exists, it wins — it takes precedence over everything in the
plugin's skill file.** Generic skill guidance only applies when no override file
is present for that skill.

If you see Claude follow the generic skill pattern instead of a project-specific
one, check that `/init-context` has been run and that the relevant file exists
under `.claude/context/conventions/`.

## Workflow

1. `/design <spec.md>` — produces a design doc, stops for human review
2. `/implement` — implements the design; asks test scope (none / unit / unit+integration) up front
3. `/mark-reviewed` — human gate, unlocks finishing
4. `/finish` — changelog entry, commit, optional push/PR

Reading the design doc between steps 1 and 2 is on you — there is no enforced
gate there, and implementation writes are reversible and visible in the diff.
The one hard block is commits/pushes, which a hook refuses until
`/mark-reviewed` has set `code_reviewed` in `.claude/workflow/state.json`. See
`hooks/hooks.json` in the plugin if you need to debug why something was
blocked.

Each step above reads what it needs from `state.json` and files on disk, not
from conversation history — it's safe (and cheap) to `/clear` between steps.

## Common commands

- Compile: `./gradlew compileJava`
- Test (quiet): `./gradlew test --console=plain -q`

## AI instructions

Design-time instructions (for `/design` and any design work) live under `.claude/ai-instructions/`. Read them before producing a design doc; where they conflict with generic skill guidance, they win.
