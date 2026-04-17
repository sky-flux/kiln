package com.skyflux.kiln.infra.mdc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servlet filter that populates SLF4J {@link MDC} with a per-request
 * {@code traceId} for log correlation.
 *
 * <p>The {@code X-Request-Id} header is accepted only when it matches the
 * conservative whitelist {@code [A-Za-z0-9._-]{1,128}}; anything else
 * (oversize, control chars like CR/LF, non-ASCII) is discarded and a fresh
 * UUID is generated. This protects against:
 * <ul>
 *   <li>Log injection (attacker-supplied value landing in every log line).</li>
 *   <li>HTTP response splitting via CR/LF in echoed header.</li>
 *   <li>Flooding the log pipeline with arbitrarily long values.</li>
 * </ul>
 *
 * <p>See design doc {@code Ch.13.4 MDC 上下文}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Max accepted length for inbound X-Request-Id. */
    static final int MAX_TRACE_ID_LEN = 128;

    /** Allowed character class for inbound X-Request-Id. */
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(REQUEST_ID_HEADER));
        try {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            response.setHeader(REQUEST_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    /** Return the header if it passes the whitelist; otherwise a fresh UUID. */
    private static String sanitize(String candidate) {
        if (candidate == null
                || candidate.isBlank()
                || candidate.length() > MAX_TRACE_ID_LEN
                || !ALLOWED.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }
}
