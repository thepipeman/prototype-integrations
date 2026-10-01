# testcontainers-testing — project conventions

New project — conventions to be established.

Known choices:
- JUnit Platform with `spring-boot-starter-webmvc-test`
- No database, so no Postgres container. Upstream HTTP dependencies are the thing to fake in tests (e.g. a stubbed HTTP server) once chosen.
