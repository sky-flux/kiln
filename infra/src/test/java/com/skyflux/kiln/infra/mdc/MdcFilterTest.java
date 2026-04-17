package com.skyflux.kiln.infra.mdc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesUuidWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> inChainTraceId = new AtomicReference<>();
        FilterChain chain = (req, res) -> inChainTraceId.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        String headerTraceId = response.getHeader("X-Request-Id");
        assertThat(headerTraceId).isNotBlank();
        assertThat(UUID.fromString(headerTraceId)).isNotNull();
        assertThat(inChainTraceId.get()).isEqualTo(headerTraceId);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void usesHeaderTraceIdWhenPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-Id", "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> inChainTraceId = new AtomicReference<>();
        FilterChain chain = (req, res) -> inChainTraceId.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertThat(inChainTraceId.get()).isEqualTo("abc-123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void clearsMdcEvenIfChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                throw new RuntimeException("boom");
            }
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get("traceId")).isNull();
    }

    // ──────────── I8: validate / sanitize untrusted X-Request-Id ────────────

    @Test
    void oversizeHeaderIsReplacedWithUuid() throws ServletException, IOException {
        String oversize = "a".repeat(129);   // cap is 128
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-Id", oversize);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> inChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> inChain.set(MDC.get("traceId")));

        String used = response.getHeader("X-Request-Id");
        assertThat(used).isNotEqualTo(oversize);
        assertThat(UUID.fromString(used)).isNotNull();   // replaced with a UUID
        assertThat(inChain.get()).isEqualTo(used);
    }

    @Test
    void headerWithCrLfIsReplacedWithUuid() throws ServletException, IOException {
        String injection = "abc\r\nSet-Cookie: evil=1";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-Id", injection);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String used = response.getHeader("X-Request-Id");
        assertThat(used).doesNotContain("\r", "\n");
        assertThat(UUID.fromString(used)).isNotNull();
    }

    @Test
    void headerWithDisallowedCharsIsReplacedWithUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-Id", "trace id with spaces and 中文");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String used = response.getHeader("X-Request-Id");
        assertThat(used).doesNotContain(" ", "中", "文");
        assertThat(UUID.fromString(used)).isNotNull();
    }

    @Test
    void maxAllowedCharactersAreAccepted() throws ServletException, IOException {
        // 128 chars, all in [A-Za-z0-9._-]
        String maxOk = "a".repeat(128);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-Id", maxOk);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(maxOk);
    }

    @Test
    void underscoresDotsAndDashesAreAccepted() throws ServletException, IOException {
        String ok = "trace.id_01-abc";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        request.addHeader("X-Request-Id", ok);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(ok);
    }
}
