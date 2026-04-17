# Kiln

> A production-ready **Modular Monolith** scaffold built on **Spring Boot 4** and **Spring Modulith**.

[中文文档 / Chinese README](./README.zh-CN.md)

## Overview

Kiln is a starting point for Java backend services that follow the *modular monolith* pattern: retaining the deployment and development simplicity of a single application while using Spring Modulith's boundary verification to prevent package-level coupling from rotting over time. This leaves clear seams for later extraction into separate services if — and only if — the business actually needs it.

- **Group**: `com.skyflux`
- **Artifact**: `kiln`
- **Version**: `0.0.1-SNAPSHOT`

## Tech Stack

| Category | Dependencies |
| --- | --- |
| Runtime | Java 25 (Gradle Toolchain), Spring Boot 4.0.5 |
| Architecture | Spring Modulith 2.0.5 (Core / JDBC / Runtime / Actuator / Observability) |
| Web | Spring Web MVC, Spring Security, SpringDoc OpenAPI (Swagger UI) |
| Data | Spring Data JDBC, jOOQ, Flyway migrations, Spring Data Redis |
| View | Apache FreeMarker |
| HTTP Client | Spring `RestClient` |
| Observability | Spring Boot Actuator, OpenTelemetry, Micrometer (OTLP & Prometheus registries) |
| Dev Tooling | Spring Boot DevTools (development only) |
| Testing | JUnit 5, Spring Boot Test, Modulith Test, REST Docs, Security Test, data slice tests |
| Build | Gradle (Groovy DSL), Asciidoctor (for REST Docs rendering) |

## Requirements

- **JDK 25** — resolved automatically via Gradle Toolchain, but a local install is recommended for IDE support.
- **Gradle** — use the bundled Wrapper (`./gradlew`); no separate install required.
- **Redis** — a reachable Redis instance is needed at runtime.
- **Relational database** — schema is managed by Flyway; configure `spring.datasource.*` in `application.yaml`.

## Getting Started

### 1. Clone

```bash
git clone <your-repo-url> kiln
cd kiln
```

### 2. Run the application

```bash
./gradlew bootRun
```

Defaults to Spring Boot's port `8080`. Override with `--args='--server.port=9000'`.

### 3. Run tests

```bash
./gradlew test
```

On success, Spring REST Docs snippets are written to `build/generated-snippets`.

### 4. Render REST Docs

```bash
./gradlew asciidoctor
```

### 5. Build an executable jar

```bash
./gradlew bootJar
java -jar build/libs/kiln-0.0.1-SNAPSHOT.jar
```

### 6. Build an OCI image (optional)

The Spring Boot Gradle plugin ships an OCI image builder — no Dockerfile required:

```bash
./gradlew bootBuildImage
```

## Project Layout

```
kiln/
├── build.gradle                         # Build script and dependencies
├── settings.gradle
├── gradle/wrapper/                      # Gradle Wrapper
├── src/
│   ├── main/
│   │   ├── java/com/skyflux/kiln/
│   │   │   └── KilnApplication.java     # Spring Boot entry point
│   │   └── resources/
│   │       └── application.yaml         # Application configuration
│   └── test/
│       └── java/com/skyflux/kiln/
│           └── KilnApplicationTests.java
└── HELP.md                              # Spring Initializr reference links
```

> **Modulith tip**: place each business domain in its own sub-package under `com.skyflux.kiln` (e.g. `order/`, `catalog/`, `billing/`). Spring Modulith treats every such sub-package as a module boundary — cross-module calls are only allowed through types exposed in the module's `api` sub-package. This is verified by `ApplicationModules.verify()` in tests and is the whole reason to use Modulith.

## Configuration

Current `application.yaml` only declares the application name:

```yaml
spring:
  application:
    name: kiln
```

When wiring in a database, Redis, and OpenTelemetry, extend it like so:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kiln
    username: kiln
    password: change-me
  data:
    redis:
      host: localhost
      port: 6379
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
```

## Observability Endpoints

Once Actuator is enabled the following paths become available:

- Health check: `GET /actuator/health`
- Prometheus metrics: `GET /actuator/prometheus`
- Modulith module graph: `GET /actuator/modulith`
- OpenAPI document (SpringDoc): `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

## References

External documentation links (Spring Boot 4, Spring Modulith, jOOQ, Flyway, OpenTelemetry, REST Docs, etc.) are collected in [`HELP.md`](./HELP.md).

## License

TBD.
