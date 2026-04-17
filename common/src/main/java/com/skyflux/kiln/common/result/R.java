package com.skyflux.kiln.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record R<T>(
        int code,
        String message,
        T data,
        Instant timestamp
) implements Serializable {

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data, Instant.now());
    }

    public static <T> R<T> ok() {
        return new R<>(0, "ok", null, Instant.now());
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, Instant.now());
    }
}
