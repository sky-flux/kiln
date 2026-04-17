package com.skyflux.kiln.infra.web;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.result.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ──────────── AppException → HTTP status mapping (all AppCodes) ────────────

    @Test
    void appExceptionBadRequestMapsTo400() {
        assertThat(handleApp(AppCode.BAD_REQUEST).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void appExceptionUnauthorizedMapsTo401() {
        ResponseEntity<R<Void>> resp = handleApp(AppCode.UNAUTHORIZED);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(AppCode.UNAUTHORIZED.code());
        assertThat(resp.getBody().message()).isEqualTo(AppCode.UNAUTHORIZED.message());
    }

    @Test
    void appExceptionForbiddenMapsTo403() {
        assertThat(handleApp(AppCode.FORBIDDEN).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void appExceptionNotFoundMapsTo404() {
        assertThat(handleApp(AppCode.NOT_FOUND).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void appExceptionMethodNotAllowedMapsTo405() {
        assertThat(handleApp(AppCode.METHOD_NOT_ALLOWED).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void appExceptionConflictMapsTo409() {
        assertThat(handleApp(AppCode.CONFLICT).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void appExceptionMediaTypeNotSupportedMapsTo415() {
        assertThat(handleApp(AppCode.MEDIA_TYPE_NOT_SUPPORTED).getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void appExceptionTooManyRequestsMapsTo429() {
        assertThat(handleApp(AppCode.TOO_MANY_REQUESTS).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void appExceptionValidationFailedMapsTo400() {
        assertThat(handleApp(AppCode.VALIDATION_FAILED).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void appExceptionInternalErrorMapsTo500() {
        assertThat(handleApp(AppCode.INTERNAL_ERROR).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void appExceptionLoginFailedMapsTo401() {
        assertThat(handleApp(AppCode.LOGIN_FAILED).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void appExceptionTokenExpiredMapsTo401() {
        assertThat(handleApp(AppCode.TOKEN_EXPIRED).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void appExceptionBusinessErrorMapsTo400() {
        assertThat(handleApp(AppCode.BUSINESS_ERROR).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ──────────── Framework exceptions ────────────

    @Test
    void methodArgumentNotValidReturns400WithFirstFieldError() {
        MethodParameter mp = mock(MethodParameter.class);
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "target");
        binding.addError(new FieldError("target", "email", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, binding);

        ResponseEntity<R<Void>> resp = handler.handleMethodArgumentNotValid(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(AppCode.VALIDATION_FAILED.code());
        assertThat(resp.getBody().message()).isEqualTo("must not be blank");
    }

    @Test
    void methodArgumentNotValidWithNoFieldErrorFallsBackToAppCodeMessage() {
        MethodParameter mp = mock(MethodParameter.class);
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "target");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, binding);

        ResponseEntity<R<Void>> resp = handler.handleMethodArgumentNotValid(ex);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).isEqualTo(AppCode.VALIDATION_FAILED.message());
    }

    @Test
    @SuppressWarnings("unchecked")
    void constraintViolationReturns400WithFirstViolationMessage() {
        ConstraintViolation<Object> v1 = mock(ConstraintViolation.class);
        when(v1.getMessage()).thenReturn("must be greater than 0");
        ConstraintViolationException ex = new ConstraintViolationException(Set.<ConstraintViolation<?>>of(v1));

        ResponseEntity<R<Void>> resp = handler.handleConstraintViolation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("must be greater than 0");
    }

    @Test
    void constraintViolationWithEmptySetFallsBackToAppCodeMessage() {
        ResponseEntity<R<Void>> resp = handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));
        assertThat(resp.getBody().message()).isEqualTo(AppCode.VALIDATION_FAILED.message());
    }

    @Test
    void methodNotSupportedReturns405() {
        ResponseEntity<R<Void>> resp = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody().code()).isEqualTo(AppCode.METHOD_NOT_ALLOWED.code());
    }

    @Test
    void mediaTypeNotSupportedReturns415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, java.util.List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<R<Void>> resp = handler.handleMediaTypeNotSupported(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ──────────── New (I1): client-class exceptions that used to reach Exception → 500 ────────────

    @Test
    void methodArgumentTypeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", UUID_CLASS, "id", mock(MethodParameter.class), new IllegalArgumentException("bad uuid"));
        ResponseEntity<R<Void>> resp = handler.handleTypeMismatch(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo(AppCode.BAD_REQUEST.code());
    }

    @Test
    void illegalArgumentReturns400() {
        ResponseEntity<R<Void>> resp = handler.handleIllegalArgument(new IllegalArgumentException("bad"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo(AppCode.BAD_REQUEST.code());
    }

    @Test
    void httpMessageNotReadableReturns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "malformed", new RuntimeException("root cause"),
                new org.springframework.mock.http.MockHttpInputMessage(new byte[0]));
        ResponseEntity<R<Void>> resp = handler.handleNotReadable(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingParameterReturns400() throws Exception {
        ResponseEntity<R<Void>> resp = handler.handleMissingParameter(new MissingServletRequestParameterException("name", "String"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noResourceFoundReturns404() {
        ResponseEntity<R<Void>> resp = handler.handleNoResource(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing", "Not Found"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo(AppCode.NOT_FOUND.code());
    }

    // ──────────── Catch-all (L3: no hard-coded message) ────────────

    @Test
    void catchAllReturns500WithAppCodeMessage() {
        ResponseEntity<R<Void>> resp = handler.handleAny(new IllegalStateException("boom"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(AppCode.INTERNAL_ERROR.code());
        assertThat(resp.getBody().message()).isEqualTo(AppCode.INTERNAL_ERROR.message());
    }

    // ──────────── helpers ────────────

    private static final Class<?> UUID_CLASS = java.util.UUID.class;

    private ResponseEntity<R<Void>> handleApp(AppCode code) {
        return handler.handleAppException(new AppException(code));
    }
}
