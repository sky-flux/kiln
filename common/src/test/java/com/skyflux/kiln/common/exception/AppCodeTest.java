package com.skyflux.kiln.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppCodeTest {

    @Test
    void existingLowCommonCodesArePreserved() {
        assertThat(AppCode.BAD_REQUEST.code()).isEqualTo(1000);
        assertThat(AppCode.UNAUTHORIZED.code()).isEqualTo(1001);
        assertThat(AppCode.FORBIDDEN.code()).isEqualTo(1002);
        assertThat(AppCode.NOT_FOUND.code()).isEqualTo(1003);
        assertThat(AppCode.CONFLICT.code()).isEqualTo(1004);
        assertThat(AppCode.TOO_MANY_REQUESTS.code()).isEqualTo(1005);
    }

    @Test
    void newValidationCodeIs1006() {
        assertThat(AppCode.VALIDATION_FAILED.code()).isEqualTo(1006);
        assertThat(AppCode.VALIDATION_FAILED.message()).isEqualTo("参数校验失败");
    }

    @Test
    void newMethodNotAllowedCodeIs1007() {
        assertThat(AppCode.METHOD_NOT_ALLOWED.code()).isEqualTo(1007);
        assertThat(AppCode.METHOD_NOT_ALLOWED.message()).isEqualTo("方法不被允许");
    }

    @Test
    void newMediaTypeCodeIs1008() {
        assertThat(AppCode.MEDIA_TYPE_NOT_SUPPORTED.code()).isEqualTo(1008);
        assertThat(AppCode.MEDIA_TYPE_NOT_SUPPORTED.message()).isEqualTo("媒体类型不受支持");
    }

    @Test
    void internalErrorStillAt1999() {
        assertThat(AppCode.INTERNAL_ERROR.code()).isEqualTo(1999);
    }

    @Test
    void eachAppCodeCarriesItsHttpStatus() {
        // 1xxx common → mapped per RFC 7231 / 6585
        assertThat(AppCode.BAD_REQUEST.httpStatus()).isEqualTo(400);
        assertThat(AppCode.UNAUTHORIZED.httpStatus()).isEqualTo(401);
        assertThat(AppCode.FORBIDDEN.httpStatus()).isEqualTo(403);
        assertThat(AppCode.NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(AppCode.CONFLICT.httpStatus()).isEqualTo(409);
        assertThat(AppCode.TOO_MANY_REQUESTS.httpStatus()).isEqualTo(429);
        assertThat(AppCode.VALIDATION_FAILED.httpStatus()).isEqualTo(400);
        assertThat(AppCode.METHOD_NOT_ALLOWED.httpStatus()).isEqualTo(405);
        assertThat(AppCode.MEDIA_TYPE_NOT_SUPPORTED.httpStatus()).isEqualTo(415);
        assertThat(AppCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
        // 2xxx auth → 401 semantics (not 500 as the old fallback)
        assertThat(AppCode.LOGIN_FAILED.httpStatus()).isEqualTo(401);
        assertThat(AppCode.TOKEN_EXPIRED.httpStatus()).isEqualTo(401);
        // 3xxx business → treated as client-visible business failure, 400
        assertThat(AppCode.BUSINESS_ERROR.httpStatus()).isEqualTo(400);
    }

    @Test
    void totalValuesCountMatchesSpec() {
        // 1xxx: BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT,
        //       TOO_MANY_REQUESTS, VALIDATION_FAILED, METHOD_NOT_ALLOWED,
        //       MEDIA_TYPE_NOT_SUPPORTED, INTERNAL_ERROR (10)
        // 2xxx: LOGIN_FAILED, TOKEN_EXPIRED (2)
        // 3xxx: BUSINESS_ERROR (1)
        assertThat(AppCode.values()).hasSize(13);
    }
}
