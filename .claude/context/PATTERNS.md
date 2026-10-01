# Patterns

## Platform

- Java 25 (Gradle toolchain in `build.gradle.kts`)
- Spring Boot 4.1.1, Gradle Kotlin DSL
- Starters: `webmvc`, `actuator`

## Persistence stack

None. The project has no database; neither `spring-data-jpa` nor `jooq-conventions` applies, and there are no schema migrations.

## Layering

Purpose: a prototype showcasing resilient REST client integration and good integration patterns. Conventions are yet to be established beyond that focus.

Design instructions live under `.claude/ai-instructions/`; consult them when designing.

## Transaction boundaries

None — no database.

## Error handling

Conventions yet to be established. Expected focus: mapping upstream failures (timeouts, 4xx/5xx) to explicit domain-level errors.

## REST clients

Conventions yet to be established. This is the core subject of the project: resilient outbound REST integration (timeouts, retries, error translation, observability).

## Security

None yet.

## Naming conventions

Base package `io.pipecrafts.prototype.integration`.
