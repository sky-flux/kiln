package com.skyflux.kiln;

import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.PermissionLookupService;
import com.skyflux.kiln.auth.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * End-to-end smoke test covering:
 * <ul>
 *   <li>R-wrapping of responses, {@code AppException} → HTTP status via {@code GlobalExceptionHandler}</li>
 *   <li>{@code X-Request-Id} MDC filter behaviour (echo + oversize-drop)</li>
 *   <li>Actuator bypass of R-wrapping</li>
 *   <li>jOOQ-backed persistence through Flyway-migrated Testcontainers PostgreSQL</li>
 *   <li>Phase 4 auth flow: register → login → {@code Bearer} token on a protected GET</li>
 *   <li>Sa-Token {@code NotLoginException} → 401 {@code UNAUTHORIZED} via exception handler</li>
 *   <li>Login failure uniformity — wrong password and unknown email both yield
 *       {@code LOGIN_FAILED} (no enumeration leak)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class KilnIntegrationTest {

    @Autowired
    RestTestClient client;

    @Autowired
    RoleAssignmentService roleAssignment;

    @Autowired
    PermissionLookupService permissionLookup;

    /**
     * Phase 4.3 Wave 3 C5 — override security knobs so the e2e lockout test
     * completes in 3 failed logins instead of the production default of 5,
     * while keeping rate-limit headroom comfortably above the lockout
     * threshold so unrelated tests (admin bootstrap, audit queries, etc.) do
     * not accidentally trip the per-IP 429 sentinel.
     *
     * <p>Rate-limit e2e is intentionally NOT exercised here. The interceptor's
     * sliding-window semantics are exhaustively covered by
     * {@code LoginRateLimitInterceptorTest}; adding an e2e would require
     * either a context rebuild (new Postgres + Redis containers) or a wall-
     * clock wait for the window to expire, neither of which earns its keep.
     */
    @DynamicPropertySource
    static void overrideSecurityForTests(DynamicPropertyRegistry r) {
        r.add("kiln.security.login.lock-threshold", () -> "3");
        r.add("kiln.security.login.lock-duration", () -> "PT1H");
        r.add("kiln.security.login.rate-limit.max-attempts", () -> "50");
        r.add("kiln.security.login.rate-limit.window", () -> "PT1M");
    }

    // ──────────── Phase 2 / 3 — infra bypass & error mapping ────────────

    @Test
    void traceIdHeaderIsEchoedBack() {
        // MDC filter runs ahead of Sa-Token's interceptor, so 401 responses still carry X-Request-Id.
        client.get().uri("/api/v1/users/{id}", UUID.randomUUID())
                .header("X-Request-Id", "test-trace-123")
                .exchange()
                .expectHeader().valueEquals("X-Request-Id", "test-trace-123");
    }

    @Test
    void oversizedTraceIdIsReplacedWithGeneratedUuid() {
        // I8 fix: inbound X-Request-Id longer than 128 chars is dropped → UUID generated.
        String oversized = "a".repeat(200);
        String echoed = client.get().uri("/api/v1/users/{id}", UUID.randomUUID())
                .header("X-Request-Id", oversized)
                .exchange()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("X-Request-Id");

        org.assertj.core.api.Assertions.assertThat(echoed)
                .isNotEqualTo(oversized)
                .matches(s -> { UUID.fromString(s); return true; });
    }

    @Test
    void actuatorHealthIsNotWrappedInR() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.code").doesNotExist();
    }

    // ──────────── Phase 3 — public POST /api/v1/users (register) ────────────

    @Test
    void postBlankNameReturns400WithValidationCode() {
        // Bean Validation fires before the use case; TenantContext is never read.
        // No X-Tenant-Code needed — the 400 is produced before tenant resolution.
        client.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"","email":"ok@example.com","password":"S3cret-pass"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1006)    // AppCode.VALIDATION_FAILED
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    void duplicateEmailReturns409Conflict() {
        // I1 e2e: second POST with same email (case-variant) triggers CONFLICT.
        String body1 = """
                {"name":"Dup1","email":"dup-e2e@example.com","password":"pw-one-1111"}
                """;
        client.post().uri("/api/v1/users")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body1)
                .exchange()
                .expectStatus().isCreated();

        String body2 = """
                {"name":"Dup2","email":"DUP-e2e@Example.com","password":"pw-two-2222"}
                """;
        client.post().uri("/api/v1/users")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body2)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo(1004);  // AppCode.CONFLICT
    }

    // ──────────── Phase 4 — auth flow: register → login → authenticated GET ────────────

    @Test
    void registerLoginThenAuthenticatedGetRoundTrips() {
        String email = "auth-e2e@example.com";
        String password = "S3cret-pass";

        // 1. Register in the system tenant
        String registerBody = """
                {"name":"AuthE2E","email":"%s","password":"%s"}
                """.formatted(email, password);
        String registerResp = client.post().uri("/api/v1/users")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON).body(registerBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        String userId = extractJsonString(registerResp, "id");

        // 2. Login — returns R-wrapped { token: "..." }
        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        String loginResp = client.post().uri("/api/v1/auth/login")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON).body(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(loginResp).contains("\"code\":0", "\"token\"");
        String token = extractJsonString(loginResp, "token");

        // 3. Authenticated GET — token carries tenantId; no header needed
        client.get().uri("/api/v1/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(userId)
                .jsonPath("$.data.email").isEqualTo(email)
                .jsonPath("$.data.name").isEqualTo("AuthE2E");
    }

    @Test
    void getWithoutTokenReturns401Unauthorized() {
        UUID someId = UUID.randomUUID();
        client.get().uri("/api/v1/users/{id}", someId)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1001);   // AppCode.UNAUTHORIZED
    }

    @Test
    void loginWithWrongPasswordReturns401WithLoginFailedCode() {
        // Seed a user in the system tenant.
        client.post().uri("/api/v1/users")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"name":"WrongPw","email":"wrong-pw@example.com","password":"correct-pw"}
                        """)
                .exchange().expectStatus().isCreated();

        // Login with wrong password.
        client.post().uri("/api/v1/auth/login")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"email":"wrong-pw@example.com","password":"INCORRECT"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(2001);   // AppCode.LOGIN_FAILED
    }

    @Test
    void loginWithUnknownEmailReturns401WithSameLoginFailedCode() {
        // No-enumeration invariant: unknown email must NOT distinguish itself
        // from wrong-password in the response (see AuthenticateUserServiceTest).
        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"email":"ghost-never-seen@example.com","password":"whatever"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(2001);   // AppCode.LOGIN_FAILED
    }

    @Test
    void logoutInvalidatesTokenForSubsequentAuthenticatedGet() {
        // C2 Gate-3 fix: end-to-end logout coverage — after /logout, the same
        // bearer token must no longer grant access to protected endpoints.
        String email = "logout-e2e@example.com";
        String password = "S3cret-pass";

        // Register + login in the system tenant
        String registerResp = client.post().uri("/api/v1/users")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"LogoutE2E","email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange().expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        String userId = extractJsonString(registerResp, "id");

        String loginResp = client.post().uri("/api/v1/auth/login")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange().expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        String token = extractJsonString(loginResp, "token");

        // Authenticated GET works with token
        client.get().uri("/api/v1/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();

        // Logout invalidates the Sa-Token session
        client.post().uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();

        // Same token now fails the Sa-Token gate → 401
        client.get().uri("/api/v1/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1001);   // AppCode.UNAUTHORIZED
    }

    // ──────────── Phase 4.2 — RBAC: @SaCheckRole("ADMIN") on AdminController ────────────

    @Test
    void registrationAssignsUserRoleViaEventListener() {
        // C1 guard: UserRegisteredListener (sync AFTER_COMMIT via @TransactionalEventListener)
        // must write the USER row to user_roles for every freshly registered user. Without this
        // assertion, a subtle regression (e.g. someone re-adds @Async without @EnableAsync, or
        // the listener throws silently) would degrade permissions but pass the slice / e2e
        // positive-path tests. We assert the lookup SERVICE returns "USER" — which exercises
        // both the INSERT and the jOOQ read path used by StpInterfaceImpl.
        String email = "rbac-roles-visible@example.com";
        String userId = register("RbacRolesVisible", email, "S3cret-pass");

        java.util.List<String> roles = permissionLookup.rolesFor(UUID.fromString(userId));

        org.assertj.core.api.Assertions.assertThat(roles).containsExactly("USER");
    }

    @Test
    void freshlyRegisteredUserIsForbiddenFromAdminEndpoint() {
        // UserRegisteredListener auto-assigns the USER role AFTER_COMMIT of
        // registration. USER does NOT carry "ADMIN" → @SaCheckRole rejects with
        // NotRoleException → GlobalExceptionHandler maps to 403 / AppCode.FORBIDDEN.
        String email = "rbac-user-only@example.com";
        String token = registerAndLogin("RbacUser", email, "S3cret-pass");

        client.get().uri("/api/v1/admin/users/count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1002);   // AppCode.FORBIDDEN
    }

    @Test
    void adminRoleGrantsAccessToAdminEndpoint() {
        // Register an ADMIN, capture the count, register two more regular users,
        // then re-read the count and assert it grew by EXACTLY +2. A "greater than
        // zero" check here would silently pass if Sa-Token's AOP degraded and
        // anyone could hit the endpoint — the delta equality forces the assertion
        // to travel through the full chain: role_permissions seed →
        // UserRoleJooqRepository.assign → StpInterfaceImpl → @SaCheckRole("ADMIN")
        // pass → CountUsersUseCase → AdminController body → R-wrap.
        String email = "rbac-admin@example.com";
        String userId = register("RbacAdmin", email, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(userId), RoleCode.ADMIN);
        String token = login(email, "S3cret-pass");

        long baseline = readAdminCount(token);
        register("RbacDelta1", "rbac-delta-1@example.com", "S3cret-pass");
        register("RbacDelta2", "rbac-delta-2@example.com", "S3cret-pass");

        long after = readAdminCount(token);
        org.assertj.core.api.Assertions.assertThat(after - baseline).isEqualTo(2L);
    }

    private long readAdminCount(String token) {
        String resp = client.get().uri("/api/v1/admin/users/count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        Matcher m = Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(resp);
        if (!m.find()) {
            throw new AssertionError("count not found in admin response: " + resp);
        }
        return Long.parseLong(m.group(1));
    }

    @Test
    void adminEndpointWithoutTokenReturns401() {
        // NotLoginException comes BEFORE NotRoleException because Sa-Token's
        // interceptor short-circuits on an absent token. Confirms the @SaCheckRole
        // gate does not accidentally leak admin-endpoint existence to anonymous callers.
        client.get().uri("/api/v1/admin/users/count")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1001);   // AppCode.UNAUTHORIZED
    }

    // ──────────── Phase 4.3 Wave 2 — strong-password registration ────────────

    @Test
    void registerRejectsWeakPassword() {
        // @StrongPassword on RegisterUserRequest rejects < 10 chars / missing
        // letter+non-letter mix. "abc" fails on length first; controller
        // surfaces a Bean Validation error which GlobalExceptionHandler maps
        // to VALIDATION_FAILED.
        client.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"x","email":"weak-pw@example.com","password":"abc"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1006);   // AppCode.VALIDATION_FAILED
    }

    // ──────────── Phase 4.3 Wave 2 — account lockout ────────────

    @Test
    void accountLocksAfterThresholdFailedAttempts() {
        // With lockThreshold=3 (see @DynamicPropertySource), the 3 wrong-
        // password attempts each return 401/LOGIN_FAILED. The 4th attempt —
        // even with the CORRECT password — finds the account locked and
        // returns 423/ACCOUNT_LOCKED per AppCode.
        String email = "lockout-e2e@example.com";
        String password = "S3cret-pass";
        register("LockoutE2E", email, password);

        for (int i = 0; i < 3; i++) {
            client.post().uri("/api/v1/auth/login")
                    .header("X-Tenant-Code", "system")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"email":"%s","password":"WRONG-pass-%d"}
                            """.formatted(email, i))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(2001);   // AppCode.LOGIN_FAILED
        }

        // 4th attempt — correct password, but the account is locked.
        client.post().uri("/api/v1/auth/login")
                .header("X-Tenant-Code", "system")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.LOCKED)
                .expectBody()
                .jsonPath("$.code").isEqualTo(2003);   // AppCode.ACCOUNT_LOCKED
    }

    // ──────────── Phase 4.3 Wave 2 — admin role assign / revoke ────────────

    @Test
    void adminCanAssignAdminRoleToUserViaHttp() {
        // Bootstrap: register A + B, promote A to ADMIN via the service
        // (Phase 4.2 pattern — no admin exists yet to self-promote). A then
        // assigns ADMIN to B over HTTP; B subsequently passes @SaCheckRole.
        String aEmail = "admin-assign-a@example.com";
        String bEmail = "admin-assign-b@example.com";
        String aId = register("AdminAssignA", aEmail, "S3cret-pass");
        String bId = register("AdminAssignB", bEmail, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(aId), RoleCode.ADMIN);
        String aToken = login(aEmail, "S3cret-pass");

        client.post().uri("/api/v1/admin/users/{uid}/roles/{rc}", bId, "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aToken)
                .exchange()
                .expectStatus().isNoContent();

        String bToken = login(bEmail, "S3cret-pass");
        client.get().uri("/api/v1/admin/users/count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void adminCanRevokeRoleFromUserViaHttp() {
        String aEmail = "admin-revoke-a@example.com";
        String bEmail = "admin-revoke-b@example.com";
        String aId = register("AdminRevokeA", aEmail, "S3cret-pass");
        String bId = register("AdminRevokeB", bEmail, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(aId), RoleCode.ADMIN);
        roleAssignment.assign(UUID.fromString(bId), RoleCode.ADMIN);
        String aToken = login(aEmail, "S3cret-pass");
        String bToken = login(bEmail, "S3cret-pass");

        // Sanity: B starts as ADMIN.
        client.get().uri("/api/v1/admin/users/count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bToken)
                .exchange().expectStatus().isOk();

        // A revokes B's ADMIN role.
        client.delete().uri("/api/v1/admin/users/{uid}/roles/{rc}", bId, "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aToken)
                .exchange()
                .expectStatus().isNoContent();

        // B can no longer reach the admin surface.
        client.get().uri("/api/v1/admin/users/count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1002);   // AppCode.FORBIDDEN
    }

    @Test
    void revokeIsIdempotent() {
        String aEmail = "admin-idem-a@example.com";
        String bEmail = "admin-idem-b@example.com";
        String aId = register("AdminIdemA", aEmail, "S3cret-pass");
        String bId = register("AdminIdemB", bEmail, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(aId), RoleCode.ADMIN);
        String aToken = login(aEmail, "S3cret-pass");

        // Even though B was never assigned ADMIN, both DELETEs succeed.
        client.delete().uri("/api/v1/admin/users/{uid}/roles/{rc}", bId, "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aToken)
                .exchange().expectStatus().isNoContent();
        client.delete().uri("/api/v1/admin/users/{uid}/roles/{rc}", bId, "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aToken)
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void assignUnknownRoleCodeReturns400() {
        String aEmail = "admin-unknown-a@example.com";
        String bEmail = "admin-unknown-b@example.com";
        String aId = register("AdminUnknownA", aEmail, "S3cret-pass");
        String bId = register("AdminUnknownB", bEmail, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(aId), RoleCode.ADMIN);
        String aToken = login(aEmail, "S3cret-pass");

        client.post().uri("/api/v1/admin/users/{uid}/roles/{rc}", bId, "GHOST")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aToken)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1006);   // AppCode.VALIDATION_FAILED
    }

    // ──────────── Phase 4.3 Wave 3 — audit events query ────────────

    @Test
    void adminCanQueryAuditEventsViaHttp() {
        // Registration publishes USER_REGISTERED; successful login publishes
        // LOGIN_SUCCESS. Cross-BC listeners in the audit module translate both
        // into audit_events rows that the admin query endpoint exposes.
        String adminEmail = "audit-admin@example.com";
        String adminId = register("AuditAdmin", adminEmail, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(adminId), RoleCode.ADMIN);

        String userEmail = "audit-user@example.com";
        register("AuditUser", userEmail, "S3cret-pass");
        login(userEmail, "S3cret-pass");   // publishes LOGIN_SUCCESS

        String adminToken = login(adminEmail, "S3cret-pass");

        String all = client.get().uri("/api/v1/admin/audit-events?page=1&size=20")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(all)
                .contains("\"code\":0")
                .contains("\"items\"");

        String filtered = client.get().uri("/api/v1/admin/audit-events?page=1&size=20&type=LOGIN_SUCCESS")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        // Every item in the filtered page must carry type=LOGIN_SUCCESS and
        // at least one must exist (the user-login + admin-login above).
        org.assertj.core.api.Assertions.assertThat(filtered).contains("\"LOGIN_SUCCESS\"");
        org.assertj.core.api.Assertions.assertThat(filtered)
                .doesNotContain("\"USER_REGISTERED\"")
                .doesNotContain("\"LOGIN_FAILED\"");
    }

    // ──────────── Wave 1 — RLS tenant-isolation ────────────

    @Test
    void rlsIsolatesUserAcrossTenants() {
        // Setup: promote a system-tenant admin (in-process, no HTTP bootstrap needed)
        String adminEmail = "rls-admin@example.com";
        String adminId = register("RlsAdmin", adminEmail, "S3cret-pass");
        roleAssignment.assign(UUID.fromString(adminId), RoleCode.ADMIN);
        String adminToken = login(adminEmail, "S3cret-pass");

        // 1. Create two tenants via the admin endpoint (authenticated as system-tenant admin)
        String alphaCode = "alpha-tenant";
        String betaCode  = "beta-tenant";
        client.post().uri("/api/v1/admin/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"code":"%s","name":"Alpha Tenant"}
                        """.formatted(alphaCode))
                .exchange()
                .expectStatus().isCreated();
        client.post().uri("/api/v1/admin/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"code":"%s","name":"Beta Tenant"}
                        """.formatted(betaCode))
                .exchange()
                .expectStatus().isCreated();

        // 2. Register a user in each tenant
        String userAId = registerInTenant("UserAlpha", "user-alpha@example.com", "S3cret-pass", alphaCode);
        String userBId = registerInTenant("UserBeta",  "user-beta@example.com",  "S3cret-pass", betaCode);

        // 3. Login as each user (session carries tenantId as Sa-Token extra)
        String tokenA = loginInTenant("user-alpha@example.com", "S3cret-pass", alphaCode);
        String tokenB = loginInTenant("user-beta@example.com",  "S3cret-pass", betaCode);

        // 4. Each user can fetch their own profile
        client.get().uri("/api/v1/users/{id}", userAId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(userAId);

        client.get().uri("/api/v1/users/{id}", userBId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(userBId);

        // 5. RLS cross-tenant isolation: user-a CANNOT see user-b's profile.
        // PostgreSQL's tenant_isolation policy filters by app.tenant_id, so
        // userB's row is invisible to the alpha session → repository returns
        // empty → GetUserService throws NOT_FOUND (404).
        client.get().uri("/api/v1/users/{id}", userBId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1003);   // AppCode.NOT_FOUND
    }

    // ──────────── helpers ────────────

    private String register(String name, String email, String password) {
        return registerInTenant(name, email, password, "system");
    }

    private String registerInTenant(String name, String email, String password, String tenantCode) {
        String resp = client.post().uri("/api/v1/users")
                .header("X-Tenant-Code", tenantCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"%s","email":"%s","password":"%s"}
                        """.formatted(name, email, password))
                .exchange().expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        return extractJsonString(resp, "id");
    }

    private String login(String email, String password) {
        return loginInTenant(email, password, "system");
    }

    private String loginInTenant(String email, String password, String tenantCode) {
        String resp = client.post().uri("/api/v1/auth/login")
                .header("X-Tenant-Code", tenantCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange().expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        return extractJsonString(resp, "token");
    }

    private String registerAndLogin(String name, String email, String password) {
        register(name, email, password);
        return login(email, password);
    }

    /**
     * Crude JSON string extractor for {@code "<key>":"<value>"} patterns — the
     * test suite deliberately avoids wiring a Jackson ObjectMapper (Boot 4 ships
     * Jackson 3 under {@code tools.jackson.databind.JsonMapper}; the classic
     * {@code com.fasterxml.jackson.databind.ObjectMapper} bean does not exist).
     */
    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new AssertionError("key '" + key + "' not found in JSON: " + json);
        }
        return m.group(1);
    }
}
