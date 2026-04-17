# kiln

Spring Boot 4 multi-module project (`app`, `common`, `infra`, `user`) on Java 25 with Spring Modulith.

Design spec (authoritative): [`docs/design.md`](./docs/design.md) — stack choices, Modulith event conventions, Database First (Flyway + jOOQ), Virtual Threads, Sa-Token + Argon2id auth, JobRunr, Flowable, OpenTelemetry. Read it before making architectural decisions.

## Architecture

Modular monolith on Spring Modulith. Four Gradle modules:

| Module   | Role                                                                          | Depends on                |
|----------|-------------------------------------------------------------------------------|---------------------------|
| `app`    | Spring Boot entry point (`KilnApplication`), wires all modules together.      | `common`, `infra`, `user` |
| `common` | Shared pure-Java types: `R`, `PageQuery`, `PageResult`, `AppException`, `AppCode`, `MaskStrategy`, `@RawResponse`. No Spring. | —                         |
| `infra`  | Cross-cutting infrastructure sub-packages. **Phase 2 live**: `web` (`ResponseBodyWrapAdvice` + `GlobalExceptionHandler`), `jackson` (`KilnJackson3Customizer`), `mdc` (`MdcFilter`), `cors` (`CorsConfig` + `CorsProperties`), `openapi` (`SpringDocConfig`). **Phase-gated placeholders**: `jooq`, `redis`, `cache`, `security`, `health`, `http`, `task`, `mask`, `validation` — see comments in `infra/build.gradle`. | `common`                  |
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

When stepping on a new trap not listed here, **add it to this table** before marking the task complete. This file is load-bearing institutional memory — Phase 3+ will save hours from this list.
