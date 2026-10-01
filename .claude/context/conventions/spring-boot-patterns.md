# spring-boot-patterns — project conventions

New project — conventions to be established.

Known choices:
- Java 25, Spring Boot 4.1.1, `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`
- No database, no persistence layer, no `@Transactional`
- Focus: resilient REST client integration patterns (outbound calls); prefer Spring's `RestClient` / `@HttpExchange` for clients
