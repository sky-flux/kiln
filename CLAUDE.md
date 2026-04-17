# kiln

Spring Boot 4 multi-module project (`app`, `common`, `infra`, `user`) on Java 25 with Spring Modulith.

Design spec (authoritative): [`docs/design.md`](./docs/design.md) — stack choices, Modulith event conventions, Database First (Flyway + jOOQ), Virtual Threads, Sa-Token + Argon2id auth, JobRunr, Flowable, OpenTelemetry. Read it before making architectural decisions.

## Architecture

Modular monolith on Spring Modulith. Four Gradle modules:

| Module   | Role                                                                          | Depends on                |
|----------|-------------------------------------------------------------------------------|---------------------------|
| `app`    | Spring Boot entry point (`KilnApplication`), wires all modules together.      | `common`, `infra`, `user` |
| `common` | Shared pure-Java types: `R`, `PageQuery`, `PageResult`, `AppException`, `AppCode`, `MaskStrategy`, `@RawResponse`. No Spring. | —                         |
| `infra`  | Cross-cutting infrastructure sub-packages. **Phase 2-3 live**: `web` (`ResponseBodyWrapAdvice` + `GlobalExceptionHandler`), `jackson` (`KilnJackson3Customizer`), `mdc` (`MdcFilter`), `cors` (`CorsConfig` + `CorsProperties`), `openapi` (`SpringDocConfig`), `jooq` (generated code + runtime config; DDLDatabase codegen from Flyway scripts under `infra/src/main/resources/db/migration/`). **Phase-gated placeholders**: `redis`, `cache`, `security`, `health`, `http`, `task`, `mask`, `validation` — see comments in `infra/build.gradle`. | `common`                  |
| `user`   | **Reference Hexagonal module** — `domain/application/adapter/config` layout; Ports & Adapters; ArchUnit-enforced dependency rule (`user/src/test/.../ArchitectureTest.java`). Real business modules emulate this shape. | `common`, `infra`          |

### Module layout — two shapes depending on domain classification

**Core Domains** (e.g., `order`, `payment`) use the **Hexagonal (Ports & Adapters) layout** — see `docs/design.md` Ch 19 and the reference implementation in `user/`:

```
com.skyflux.kiln.<module>/
├── domain/                  ← pure domain, ZERO framework imports (ArchUnit enforced)
│   ├── model/               ← Aggregates, Entities, Value Objects
│   ├── event/               ← Domain events (sealed interface + records)
│   └── exception/           ← Domain exceptions
├── application/
│   ├── port/in/             ← Inbound ports (UseCase interfaces)
│   ├── port/out/            ← Outbound ports (Repository / Gateway interfaces)
│   └── usecase/             ← UseCase impls (transaction boundary lives here)
├── adapter/
│   ├── in/web/              ← REST Controllers + Request/Response DTOs (flat; open `dto/` sub-pkg only when DTO count ≥ 5)
│   ├── in/event/            ← Cross-BC event listeners
│   ├── out/persistence/     ← jOOQ Repository impls
│   └── out/http/            ← External HTTP clients
└── config/                  ← Spring wiring (@Configuration, @ConfigurationProperties)
```

**Supporting / Generic Subdomains** (e.g., `dict`, `audit`) may use the **simplified layout** — `docs/design.md` Ch 3.1:

```
com.skyflux.kiln.<module>/
├── api/         ← public types visible to other modules
├── domain/      ← domain model
├── repo/        ← repositories
├── task/        ← scheduled / async tasks
├── config/      ← module-level Spring config
└── internal/    ← impl details; other modules must NOT import
```

Pick based on **Ch 19.17's Kiln module classification table** (Core / Supporting / Generic). When in doubt, lean Hexagonal — it scales both up and down.

**Cross-module communication**: Spring Modulith events (`ApplicationEventPublisher` + `@ApplicationModuleListener`) are preferred over direct `@Autowired`. Events give asynchronous, transactional, decoupled integration. See `docs/design.md` Ch 19.9.

**Enforcement**:
- `app/src/test/java/com/skyflux/kiln/ModularityTest.java` — Spring Modulith boundary check (currently uses `listModules()`; upgrade to `verify()` once business modules stabilize).
- `user/src/test/java/com/skyflux/kiln/user/ArchitectureTest.java` — ArchUnit rules for Hexagonal layers (`domain → no framework`, `domain → no application`, `application → no adapter`). Copy this test into every Core Domain module.

### Load-bearing design decisions

- **Database First**: Flyway SQL migrations are the schema source of truth; jOOQ generates the Java access layer from them. Do not hand-write entity annotations.
- **Virtual Threads**: `spring.threads.virtual.enabled=true`. Write blocking-style I/O; Tomcat handler / `@Async` / `@Scheduled` all run on VTs.
- **`java.time` only**: `Instant` / `LocalDateTime` / `ZonedDateTime`. No `Date` / `Calendar` / Joda-Time.
- **`record` first**: DTOs, value objects, config properties. Lombok is optional, not assumed.

### Database First workflow

1. **Schema first** — write the Flyway SQL migration (`infra/src/main/resources/db/migration/VN__description.sql`). Name: Flyway `V<version>__<description>.sql` pattern.
2. **Generate jOOQ** — `./gradlew :infra:generateJooq`. Produces `Tables`, `Records`, `Pojos` under `com.skyflux.kiln.infra.jooq.generated`.
3. **Write the adapter** — `UserJooqRepositoryAdapter` etc. in each business module's `adapter/out/persistence/`. The generated `Record` type is persistence-layer only; map to/from the domain aggregate in a `<Entity>Mapper` class.
4. **Migrate at startup** — Spring Boot's Flyway auto-config runs migrations on context load. For tests, Flyway runs against the Testcontainers-provided PG instance.

**Never hand-edit the jOOQ generated dir** (`infra/build/generated-src/jooq/main`) — it's rebuilt on every `./gradlew build`. The dir is already gitignored.

**Schema changes at runtime** — add `V<N+1>__<description>.sql`. Never modify an applied migration (Flyway will refuse to start).

## Test-Driven Development

Strict Red → Green → Refactor. **No production code is written without a failing test already in place.** If code was written before the test, delete the code and start over from the test — "keep as reference" counts as cheating.

### The cycle

1. **RED — write one failing test**
   - One behavior per test. The test name describes the behavior (`shouldRejectBlankEmail`, not `test1`).
   - Prefer real collaborators over mocks; mock only at external boundaries (HTTP, time, randomness).
   - Run the test and **watch it fail**:
     ```bash
     ./gradlew :common:test --tests 'com.skyflux.kiln.common.result.RTest.shouldBuildSuccessResult'
     ```
   - The failure reason must match what you expect (missing feature or wrong return). A compile error or typo is not a valid RED — fix it and re-run until the test fails for the right reason.
   - If the test passes on first run, it is testing something that already exists. Delete or fix it.

2. **GREEN — minimal code to pass**
   - Simplest implementation that makes this one test pass. No extra fields, no speculative options, no "while I'm here" refactors.
   - Run the same test, confirm pass. Then run `./gradlew test` and confirm no regressions.

3. **REFACTOR — clean on green**
   - Remove duplication, rename, extract helpers. Do not add behavior.
   - All tests stay green throughout.

4. Repeat for the next behavior.

### Rules

- Every new function/method with **behavior** must have a test that failed first.
- **Skeletons are exempt**: empty `package-info.java`, marker annotations, `@SpringBootApplication` entry points, and pure structural scaffolding. The rule activates the moment a class gains branching, a boundary check, factory logic, or any assertable behavior.
- Bug fixes: first write a test that reproduces the bug, then fix.
- The Modulith boundary test `ModularityTest` and the Hexagonal `ArchitectureTest` (ArchUnit) count as gating tests — every GREEN step must leave them passing.
- No `@Disabled`, no `// TODO test later`. Unfinished tests do not merge.
- Hard to test = bad design. Fix the design, don't loosen the test.
- Output must be pristine — no stacktraces, warnings, or `println` left in passing tests.

### Dispatching subagents — verify their TDD claims, don't trust them

**Subagents lie about TDD** (not maliciously — their self-reports are opaque to the dispatcher). Phase 2 evidence: 3 of 7 key classes had `impl_ctime < test_ctime` despite agents claiming "Red → Green cycles completed".

Rules when delegating:

1. **Ctime check after the agent returns** — before marking the task complete:
   ```bash
   stat -f '%B' src/test/.../FooTest.java src/main/.../Foo.java
   # test ctime MUST be ≤ impl ctime. Otherwise the agent wrote impl first.
   ```
   One-liner for multiple pairs:
   ```bash
   for pair in "impl1 test1" "impl2 test2"; do
     i=$(echo $pair|cut -d' ' -f1); t=$(echo $pair|cut -d' ' -f2)
     [ "$(stat -f %B $t)" -le "$(stat -f %B $i)" ] && echo "✓ $t" || echo "✗ $i IMPL-FIRST"
   done
   ```

2. **Require commit-per-step** when possible: subagent should commit the failing test first (`✅ red: …`), then the implementation (`✨ green: …`). Git log becomes the audit trail.

3. **If ctime shows IMPL-FIRST**: do not accept the agent's work. Either have the agent redo it properly, or delete impl + test and rewrite in the main conversation with the `superpowers:test-driven-development` skill invoked.

4. **Ambiguity (SAME TIME)**: treat as suspect. Check the test content — a test that just exercises every method once without edge cases is the fingerprint of retroactive writing.

### Useful Gradle commands

```bash
./gradlew test                              # all modules
./gradlew :common:test                      # one module
./gradlew :common:test --tests '*RTest*'
./gradlew :app:test --tests '*ModularityTest*'   # Modulith boundary check
./gradlew check                             # test + verification tasks
```

## Local Development

```bash
# Start infrastructure (postgres:18 on 5432, redis:8 on 6379)
docker compose up -d

# Create your local overrides from the template (gitignored)
cp app/src/main/resources/application-local.yml.template \
   app/src/main/resources/application-local.yml

# Run the app (Spring Boot picks up application-local.yml via profile)
./gradlew :app:bootRun

# Build an executable jar
./gradlew :app:bootJar
java -jar app/build/libs/app.jar

# Build an OCI image (no Dockerfile needed)
./gradlew :app:bootBuildImage
```

`infra/build.gradle` has commented phase markers (`// Phase 3+: jOOQ, Flyway, Postgres`, `// Phase 4+: Redis, Redisson, Spring Security, Sa-Token`, etc.). Those dependencies are intentionally off until their phase ships — do not silently uncomment to unblock a task. If you need a phase-gated dependency earlier, discuss the phase bump first.

## Commit Convention

Format: `<gitmoji> <short description>`

Uses the [gitmoji](https://gitmoji.dev/) convention — the emoji **is** the type tag (analogous to Conventional Commits' `feat:`/`fix:`, but visual).

### Rules

- Every commit starts with a gitmoji emoji.
- Subject line ≤ 72 characters.
- Body optional; when used, a short paragraph or `-` bullets explaining what/why.
- **No AI attribution** — never add `Co-Authored-By: Claude`, `Generated with …`, or any AI-tool footer.
- **Linear history** — rebase, never merge commits. PRs rebase or squash on merge.
- Commit at each successful GREEN (before refactor), and again after refactor if anything changed. This gives a safe rollback point.

### Common emojis

| Emoji | When to use                              |
|-------|------------------------------------------|
| 🎉    | Begin a project / initial commit         |
| ✨    | Introduce new features                   |
| 🐛    | Fix a bug                                |
| 🚑    | Critical hotfix                          |
| ⚡    | Improve performance                      |
| ♻️    | Refactor code                            |
| ✅    | Add, update, or pass tests               |
| 📝    | Add or update documentation              |
| 🔧    | Add or update configuration files        |
| 🔨    | Add or update development scripts        |
| ⬆️    | Upgrade dependencies                     |
| 👷    | Add or update CI build system            |
| 💚    | Fix CI build                             |
| 🚨    | Fix compiler / linter warnings           |
| 🔖    | Release / version tags                   |
| 🔒    | Fix security or privacy issues           |
| 🚚    | Move or rename resources                 |
| 🔥    | Remove code or files                     |
| 🩹    | Simple fix for a non-critical issue      |
| 🎨    | Improve structure / format of the code   |
| 💡    | Add or update comments in source code    |
| 🏷️    | Add or update types                      |
| 💥    | Introduce breaking changes               |
| 🌐    | Internationalization / localization      |

Full reference: https://gitmoji.dev/.

### Examples

```
🎉 initial commit
🚚 restructure into multi-module gradle layout
✨ add R/PageResult response helpers in common
✅ add RTest covering success and error factories
🐛 fix NPE on null Authentication principal
♻️ extract JwtClaims into common module
⚡ cache JWK fetch across requests
🔧 add spring-modulith verification task
⬆️ upgrade spring-boot 4.0.5 → 4.0.6
👷 add GitHub Actions CI workflow
🩹 correct package-info typo in user/api
```

## Spring Boot 4 / Jackson 3 known traps

These are traps we have already stepped on in Phase 1 / Phase 2. Check here first before debugging a strange Boot 4 error.

| Symptom | Cause | Fix |
|---|---|---|
| App startup crashes with `Failed to bind properties under 'spring.jackson.serialization' … No enum constant … write-dates-as-timestamps` | Jackson 3 relocated some feature enums; YAML relaxed-binding no longer resolves | Remove `spring.jackson.serialization.*` and `spring.jackson.deserialization.*` from `application.yml`; configure programmatically via a `JsonMapperBuilderCustomizer` bean (see `infra/jackson/KilnJackson3Customizer`) |
| `Jackson3ObjectMapperBuilderCustomizer` does not resolve | Wrong class name (hallucinated/spec-outdated) | Real API: `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer` |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` compile error | Moved in Jackson 3 | Now in `tools.jackson.databind.cfg.DateTimeFeature` (implements `DatatypeFeature`) |
| `@WebMvcTest` annotation not found | Moved package in Boot 4 + no longer transitive through `starter-test` | Use `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`; add `testImplementation 'org.springframework.boot:spring-boot-webmvc-test'` |
| `@Autowired RestTestClient` fails with "no such bean" inside `@SpringBootTest(webEnvironment = RANDOM_PORT)` | `spring-boot-resttestclient` is a separate artifact; autoconfig doesn't register it unless the annotation is present | Add `@AutoConfigureRestTestClient` to the test class AND use `testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'` (the starter pulls `spring-boot-resttestclient` transitively) |
| `RestTestClient` import cannot resolve | Wrong package | `org.springframework.test.web.servlet.client.RestTestClient` (Spring Framework package, not Boot) |
| Gradle 9 rejects `settings.gradle` with "`pluginManagement {}` must appear before any other statements" | `pluginManagement {}` was placed after `include 'xxx'` lines | Move `pluginManagement {}` **and** `dependencyResolutionManagement {}` **above** the first `include`; `rootProject.name = 'kiln'` can stay anywhere |
| "Could not find method `api()`" in a module `build.gradle` | Using `api` config requires the `java-library` plugin | In root `build.gradle`, `subprojects { apply plugin: 'java-library' }` (not `'java'`) |
| `BigDecimal.setScale(int)` deprecation | Missing RoundingMode | Use `setScale(scale, RoundingMode.X)` explicitly; for `Money.zero(currency)` use `RoundingMode.UNNECESSARY` |
| Swagger UI runtime errors despite springdoc compiling | springdoc-openapi 2.8.x pulls swagger-core-jakarta (Jackson 2) while Boot 4 runs Jackson 3 | Known compatibility gap as of 2026-04; watch for springdoc 3.x. Workaround: disable springdoc in `application-prod.yml` (`springdoc.api-docs.enabled=false`). |
| `./gradlew :infra:generateJooq` fails with "could not parse SQL" — PG-specific DDL statement rejected by jOOQ's DDLDatabase embedded H2 parser | Historically some PG DDL forms (functional indexes, certain `ALTER TABLE` variants) aren't recognized by H2. **Note**: `COMMENT ON TABLE/COLUMN` **does parse** in jOOQ 3.20 + PG mode (verified by Phase 3 V1 migration) — JavaDoc is preserved in generated classes. | Check the specific DDL error first. Workarounds: (1) simplify the SQL, (2) move the offending statement to a Flyway repeatable `R__*.sql` whose path is excluded from the jOOQ `scripts` property, (3) add a second version migration that only PG runs. |
| Tests fail with `Driver claims to not accept jdbcUrl, jdbc:tc:postgresql://...` or similar | Testcontainers URL resolver needs the `junit-jupiter` + `postgresql` testcontainers artifacts both present AND the JUnit extension registered | Add `testImplementation 'org.testcontainers:junit-jupiter:1.21.3'` AND `testImplementation 'org.testcontainers:postgresql:1.21.3'`; annotate the test class with `@Testcontainers` or use `@ServiceConnection` on the container field |
| `@ServiceConnection` doesn't override datasource | Spring Boot 4 requires `spring-boot-testcontainers` artifact explicitly — not transitive through `starter-test` | Add `testImplementation 'org.springframework.boot:spring-boot-testcontainers'` and annotate the container bean with `@ServiceConnection` |
| Flyway refuses to run a 2nd time with `ValidateResult: migration mismatch` | An already-applied `V<n>__*.sql` was edited in place | **Never edit applied migrations**. Add a new `V<n+1>__*.sql` to evolve schema. For local dev, `./gradlew flywayClean` (only possible if `clean-disabled: false` in dev yaml) |
| jOOQ generated code not found on compile — `Users` or `UsersRecord` symbol unresolved | `generateSchemaSourceOnCompilation = true` missing OR generated dir not on source path | Confirm `generateSchemaSourceOnCompilation = true` in `jooq.configurations.main`; this adds `build/generated-src/jooq/main` to the Java source set automatically |
| `@DataJooqTest` slice test fails to start | The `@DataJooqTest` slice disables Flyway autoconfig by default | Annotate with `@ImportAutoConfiguration({FlywayAutoConfiguration.class, DataSourceAutoConfiguration.class})` OR use full `@SpringBootTest` for jOOQ integration tests |
| Flyway silently does NOT run at startup — no logs, no error, table missing, tests fail with `relation "users" does not exist` | Boot 4 split Flyway autoconfig into the separate `spring-boot-flyway` module. `flyway-core` on classpath is no longer enough — autoconfig class `FlywayAutoConfiguration` is NOT in `spring-boot-autoconfigure` anymore. | Use `api 'org.springframework.boot:spring-boot-starter-flyway'` (NOT just `flyway-core`). The starter pulls both the runtime and the autoconfig module. Same applies to JDBC (`spring-boot-starter-jdbc`) and jOOQ (`spring-boot-starter-jooq`). |
| jOOQ codegen (`./gradlew :infra:generateJooq`) fails with `NoSuchMethodError: org.jooq.meta.jaxb.Jdbc.getUrlProperty()` | Spring Boot 4.0.5 BOM pins `org.jooq:jooq:3.19.31`. When `jooqGenerator` declares `jooq-meta-extensions:3.20.0`, Gradle downgrades `jooq` to the BOM pin, causing a cross-version NoSuchMethodError. | Explicitly pin the entire jOOQ codegen stack in the `jooqGenerator` configuration: `jooqGenerator 'org.jooq:jooq:3.20.0'`, `'org.jooq:jooq-meta:3.20.0'`, `'org.jooq:jooq-codegen:3.20.0'`, `'org.jooq:jooq-meta-extensions:3.20.0'` — NOT just meta-extensions. |
| DDLDatabase (`nu.studer.jooq` DDL-mode codegen) produces 0 tables with no warning | jOOQ's `DDLDatabase` uses embedded H2 to parse SQL. (a) Ant-glob `**/*.sql` silently matches nothing from codegen working dir. (b) H2's DDL parser rejects functional indexes like `CREATE UNIQUE INDEX ... ON t(lower(col))`. | (a) Use explicit directory form: `"${project.projectDir}/src/main/resources/db/migration/"` (trailing slash, no glob). (b) Prefer plain `UNIQUE(col)` + domain-layer normalization over functional indexes. If case-insensitive uniqueness is required, `CHECK (col = lower(col))` is parseable. |
| Testcontainers throws `Could not find a valid Docker environment` on Colima (or other alt Docker runtime without `/var/run/docker.sock`) | Testcontainers probes `/var/run/docker.sock` by default. Colima's socket lives at `~/.colima/default/docker.sock`. | Create `~/.testcontainers.properties` (NOT in repo — user-local) with `docker.host=unix:///Users/<you>/.colima/default/docker.sock`. Alternatively `sudo ln -s ~/.colima/default/docker.sock /var/run/docker.sock`. |
| Testcontainers's Ryuk sidecar fails to start on Colima: `Container startup failed for image testcontainers/ryuk:0.14.0` | Ryuk (cleanup helper) has compat issues with some Colima configurations (cgroup/namespace). | Disable Ryuk on dev machines: `~/.testcontainers.properties` → `ryuk.disabled=true`, OR env var `TESTCONTAINERS_RYUK_DISABLED=true`. Trade-off: orphaned test containers aren't auto-killed; clean manually with `docker ps -a \| grep testcontainers \| awk '{print $1}' \| xargs docker rm -f`. |
| Test fails with `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'` | Spring Boot 4 provides Jackson 3's `tools.jackson.databind.JsonMapper`, NOT the Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`. Test code that `@Autowired ObjectMapper` from the classic package fails to resolve. | Don't auto-wire ObjectMapper in tests. Build JSON bodies as raw String literals (text blocks work great) or use `tools.jackson.databind.JsonMapper` if really needed. |
| `@SpringBootTest` with `classes = SomeTestConfig.class` doesn't load `application.yml` even though `SomeTestConfig` is `@SpringBootApplication` | The test classpath may not include the module that owns `application.yml`. In this project, `application.yml` lives in `app/`, so `user:test` doesn't see it — datasource defaults come from elsewhere. | Either add `user/src/test/resources/application.yml` for module-scoped config, or rely on `@ServiceConnection` Testcontainers (which overrides datasource regardless of yaml). |

When stepping on a new trap not listed here, **add it to this table** before marking the task complete. This file is load-bearing institutional memory — Phase 3+ will save hours from this list.
