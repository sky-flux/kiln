package com.skyflux.kiln.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String as requiring a minimum password strength: 10-128 characters,
 * must contain at least one letter AND at least one non-letter (digit or
 * punctuation / symbol). A null value is considered valid - compose with
 * {@code @NotBlank} for non-null/non-blank enforcement.
 *
 * <p>Use on register DTO only. Login DTO does NOT validate strength (historical
 * passwords predate the rule and must still authenticate).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "password must be 10-128 chars with a letter and a non-letter";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
