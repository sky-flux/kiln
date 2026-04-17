package com.skyflux.kiln.infra.web;

import com.skyflux.kiln.common.annotation.RawResponse;
import com.skyflux.kiln.common.result.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Global {@link ResponseBodyAdvice} that wraps controller return values in
 * {@link R} so that all JSON responses share a consistent envelope.
 *
 * <p>Pass-through rules:
 * <ul>
 *   <li>body is already an {@link R}</li>
 *   <li>body is a {@link ResponseEntity} (status already explicit)</li>
 *   <li>controller or method is annotated {@link RawResponse}</li>
 *   <li>return type is {@link String} (avoids ClassCastException from the
 *       {@code StringHttpMessageConverter})</li>
 *   <li>request path starts with {@code /actuator}, {@code /v3/api-docs},
 *       or {@code /swagger-ui}</li>
 * </ul>
 */
@RestControllerAdvice
public class ResponseBodyWrapAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof R<?>) {
            return body;
        }
        if (body instanceof ResponseEntity<?>) {
            return body;
        }
        if (isRawResponse(returnType)) {
            return body;
        }
        if (isStringReturnType(returnType)) {
            return body;
        }
        String path = request.getURI().getPath();
        if (isSkippedPath(path)) {
            return body;
        }
        return R.ok(body);
    }

    private static boolean isRawResponse(MethodParameter returnType) {
        if (returnType.getMethod() != null
                && returnType.getMethod().isAnnotationPresent(RawResponse.class)) {
            return true;
        }
        Class<?> declaring = returnType.getContainingClass();
        return declaring != null && declaring.isAnnotationPresent(RawResponse.class);
    }

    private static boolean isStringReturnType(MethodParameter returnType) {
        return String.class.equals(returnType.getParameterType());
    }

    private static boolean isSkippedPath(String path) {
        if (path == null) {
            return false;
        }
        return matchesPrefix(path, "/actuator")
                || matchesPrefix(path, "/v3/api-docs")
                || matchesPrefix(path, "/swagger-ui");
    }

    /** Anchor prefix: match exact base path or base followed by "/". */
    private static boolean matchesPrefix(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }
}
