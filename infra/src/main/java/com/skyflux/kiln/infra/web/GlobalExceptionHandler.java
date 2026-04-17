package com.skyflux.kiln.infra.web;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.result.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler that translates framework and application
 * exceptions into {@link R}-wrapped {@link ResponseEntity} payloads.
 *
 * <p>HTTP status mapping for {@link AppException} is derived from
 * {@link AppCode#httpStatus()} — one source of truth. See Ch 14.4.
 *
 * <p>Client-class framework exceptions (type-mismatch, malformed body,
 * missing param, no-resource) are mapped to the appropriate 4xx BEFORE
 * the {@code Exception} catch-all to avoid 500-ing client errors.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ──────────── Application-layer ────────────

    @ExceptionHandler(AppException.class)
    public ResponseEntity<R<Void>> handleAppException(AppException ex) {
        AppCode appCode = ex.appCode();
        log.warn("AppException [{}]: {}", appCode.code(), appCode.message());
        return ResponseEntity.status(HttpStatusCode.valueOf(appCode.httpStatus()))
                .body(R.fail(appCode.code(), appCode.message()));
    }

    // ──────────── Bean Validation ────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError firstFieldError = ex.getBindingResult().getFieldError();
        String message = (firstFieldError != null && firstFieldError.getDefaultMessage() != null)
                ? firstFieldError.getDefaultMessage()
                : AppCode.VALIDATION_FAILED.message();
        log.warn("Validation failed: {}", message);
        return fail(AppCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(AppCode.VALIDATION_FAILED.message());
        log.warn("Constraint violation: {}", message);
        return fail(AppCode.VALIDATION_FAILED, message);
    }

    // ──────────── HTTP protocol-level ────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod());
        return fail(AppCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType());
        return fail(AppCode.MEDIA_TYPE_NOT_SUPPORTED);
    }

    // ──────────── Client-input parsing (I1: used to fall to 500) ────────────

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数 '%s' 类型不正确".formatted(ex.getName());
        log.warn("Type mismatch: {}", message);
        return fail(AppCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgument: {}", ex.getMessage());
        return fail(AppCode.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : AppCode.BAD_REQUEST.message());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body not readable: {}", ex.getMostSpecificCause().getMessage());
        return fail(AppCode.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "缺少必要参数 '%s'".formatted(ex.getParameterName());
        log.warn(message);
        return fail(AppCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResource(NoResourceFoundException ex) {
        log.warn("No resource: {}", ex.getResourcePath());
        return fail(AppCode.NOT_FOUND);
    }

    // ──────────── Catch-all (L3: use AppCode message, not hand-rolled string) ────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return fail(AppCode.INTERNAL_ERROR);
    }

    // ──────────── helpers ────────────

    private static ResponseEntity<R<Void>> fail(AppCode code) {
        return fail(code, code.message());
    }

    private static ResponseEntity<R<Void>> fail(AppCode code, String message) {
        return ResponseEntity.status(HttpStatusCode.valueOf(code.httpStatus()))
                .body(R.fail(code.code(), message));
    }
}
