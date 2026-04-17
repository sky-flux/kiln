package com.skyflux.kiln;

import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * End-to-end smoke test for Phase 2 wiring: auto-wrapping of responses,
 * exception-to-R translation, MDC trace-id header echo, and Actuator bypass.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
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

    private UserId seedKnownUser() {
        User u = User.register("IntegrationTestUser", "it@example.com");
        repo.save(u);
        return u.id();
    }
}
