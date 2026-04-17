package com.skyflux.kiln;

import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * End-to-end smoke test: auto-wrapping of responses, exception-to-R
 * translation, MDC trace-id header echo, Actuator bypass, jOOQ-backed
 * persistence via Flyway-migrated Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class KilnIntegrationTest {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository repo;

    @Test
    void getExistingUserReturnsWrappedResponse() {
        UserId knownId = seedKnownUser();

        client.get().uri("/api/v1/users/{id}", knownId.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.message").isEqualTo("ok")
                .jsonPath("$.data.id").isEqualTo(knownId.value().toString())
                .jsonPath("$.data.name").isEqualTo("IntegrationTestUser");
    }

    @Test
    void getMissingUserReturns404WithAppCodeNotFound() {
        UUID ghost = UUID.randomUUID();
        client.get().uri("/api/v1/users/{id}", ghost)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1003);
    }

    @Test
    void traceIdHeaderIsEchoedBack() {
        client.get().uri("/api/v1/users/{id}", UUID.randomUUID())
                .header("X-Request-Id", "test-trace-123")
                .exchange()
                .expectHeader().valueEquals("X-Request-Id", "test-trace-123");
    }

    @Test
    void malformedUuidReturns400NotFound500() {
        // I1 fix: bad UUID on path → IllegalArgumentException → GlobalExceptionHandler → 400
        client.get().uri("/api/v1/users/{id}", "not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1000);   // AppCode.BAD_REQUEST
    }

    @Test
    void oversizedTraceIdIsReplacedWithGeneratedUuid() {
        // I8 fix: inbound X-Request-Id longer than 128 chars is dropped → UUID generated
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

    // ──────────── Gate 3 fixes — POST write path e2e ────────────

    @Test
    void registerThenGetRoundTrips() {
        // C2 + I1 e2e: POST creates user → GET by id returns same user,
        // R-wrapped. Also verifies Flyway migrations ran against Testcontainers.
        String created = client.post().uri("/api/v1/users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"RT","email":"rt@example.com"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        // The 201 body is R-wrapped by ResponseBodyWrapAdvice → { code:0, data:{ id:"..." } }
        org.assertj.core.api.Assertions.assertThat(created).contains("\"code\":0", "\"data\"");

        // Extract id from response
        String idToken = created.replaceAll(".*\"id\"\\s*:\\s*\"", "").replaceAll("\".*", "");

        client.get().uri("/api/v1/users/{id}", idToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.name").isEqualTo("RT")
                .jsonPath("$.data.email").isEqualTo("rt@example.com");
    }

    @Test
    void postBlankNameReturns400WithValidationCode() {
        // C2: end-to-end assert R-wrapped VALIDATION_FAILED on Bean Validation reject
        client.post().uri("/api/v1/users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"","email":"ok@example.com"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(1006)    // AppCode.VALIDATION_FAILED
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    void duplicateEmailReturns409Conflict() {
        // I1 e2e: second POST with same email (case-variant) triggers CONFLICT
        String body1 = """
                {"name":"Dup1","email":"dup-e2e@example.com"}
                """;
        client.post().uri("/api/v1/users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body1)
                .exchange()
                .expectStatus().isCreated();

        // Same email but different casing — domain normalization should result in conflict
        String body2 = """
                {"name":"Dup2","email":"DUP-e2e@Example.com"}
                """;
        client.post().uri("/api/v1/users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body2)
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo(1004);  // AppCode.CONFLICT
    }

    private UserId seedKnownUser() {
        User u = User.register("IntegrationTestUser", "it@example.com");
        repo.save(u);
        return u.id();
    }
}
