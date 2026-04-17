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
        client.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"","email":"ok@example.com","password":"S3cret-pw"}
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
                {"name":"Dup1","email":"dup-e2e@example.com","password":"pw-one-1"}
                """;
        client.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body1)
                .exchange()
                .expectStatus().isCreated();

        String body2 = """
                {"name":"Dup2","email":"DUP-e2e@Example.com","password":"pw-two-2"}
                """;
        client.post().uri("/api/v1/users")
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
        String password = "S3cret-pw";

        // 1. Register
        String registerBody = """
                {"name":"AuthE2E","email":"%s","password":"%s"}
                """.formatted(email, password);
        String registerResp = client.post().uri("/api/v1/users")
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
                .contentType(MediaType.APPLICATION_JSON).body(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(loginResp).contains("\"code\":0", "\"token\"");
        String token = extractJsonString(loginResp, "token");

        // 3. Authenticated GET — requires Sa-Token bearer
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
        // Seed a user.
        client.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"name":"WrongPw","email":"wrong-pw@example.com","password":"correct-pw"}
                        """)
                .exchange().expectStatus().isCreated();

        // Login with wrong password.
        client.post().uri("/api/v1/auth/login")
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
        String password = "S3cret-pw";

        // Register + login
        String registerResp = client.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"LogoutE2E","email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange().expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        String userId = extractJsonString(registerResp, "id");

        String loginResp = client.post().uri("/api/v1/auth/login")
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
        String userId = register("RbacRolesVisible", email, "S3cret-pw");

        java.util.List<String> roles = permissionLookup.rolesFor(UUID.fromString(userId));

        org.assertj.core.api.Assertions.assertThat(roles).containsExactly("USER");
    }

    @Test
    void freshlyRegisteredUserIsForbiddenFromAdminEndpoint() {
        // UserRegisteredListener auto-assigns the USER role AFTER_COMMIT of
        // registration. USER does NOT carry "ADMIN" → @SaCheckRole rejects with
        // NotRoleException → GlobalExceptionHandler maps to 403 / AppCode.FORBIDDEN.
        String email = "rbac-user-only@example.com";
        String token = registerAndLogin("RbacUser", email, "S3cret-pw");

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
        String userId = register("RbacAdmin", email, "S3cret-pw");
        roleAssignment.assign(UUID.fromString(userId), RoleCode.ADMIN);
        String token = login(email, "S3cret-pw");

        long baseline = readAdminCount(token);
        register("RbacDelta1", "rbac-delta-1@example.com", "S3cret-pw");
        register("RbacDelta2", "rbac-delta-2@example.com", "S3cret-pw");

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

    // ──────────── helpers ────────────

    private String register(String name, String email, String password) {
        String resp = client.post().uri("/api/v1/users")
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
        String resp = client.post().uri("/api/v1/auth/login")
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
