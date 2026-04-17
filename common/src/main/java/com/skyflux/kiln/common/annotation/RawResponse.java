package com.skyflux.kiln.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller methods annotated with {@code @RawResponse} skip the global
 * {@code R<T>} response wrapper and return the raw body (e.g. payment
 * callback endpoints that must respond with a vendor-specified format).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RawResponse {
}
