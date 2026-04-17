package com.skyflux.kiln.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppExceptionTest {

    @Test
    void singleArgConstructorUsesAppCodeMessageAndEmptyArgs() {
        AppException ex = new AppException(AppCode.NOT_FOUND);

        assertThat(ex.getMessage()).isEqualTo(AppCode.NOT_FOUND.message());
        assertThat(ex.appCode()).isEqualTo(AppCode.NOT_FOUND);
        assertThat(ex.args()).isEmpty();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void varargsConstructorPreservesArgs() {
        AppException ex = new AppException(AppCode.BAD_REQUEST, "fieldName", 42);

        assertThat(ex.getMessage()).isEqualTo(AppCode.BAD_REQUEST.message());
        assertThat(ex.appCode()).isEqualTo(AppCode.BAD_REQUEST);
        assertThat(ex.args()).containsExactly("fieldName", 42);
    }

    @Test
    void causeConstructorExposesCauseAndEmptyArgs() {
        Throwable cause = new IllegalStateException("boom");

        AppException ex = new AppException(AppCode.INTERNAL_ERROR, cause);

        assertThat(ex.getMessage()).isEqualTo(AppCode.INTERNAL_ERROR.message());
        assertThat(ex.appCode()).isEqualTo(AppCode.INTERNAL_ERROR);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.args()).isEmpty();
    }

    @Test
    void isRuntimeException() {
        assertThat(new AppException(AppCode.CONFLICT)).isInstanceOf(RuntimeException.class);
    }
}
