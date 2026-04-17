package com.skyflux.kiln.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces {@link StrongPassword} semantics:
 * <ul>
 *   <li>{@code null} is accepted (delegate null/blank enforcement to {@code @NotBlank}).</li>
 *   <li>Length must be in {@code [10, 128]} inclusive.</li>
 *   <li>Must contain at least one letter AND at least one non-letter character.</li>
 * </ul>
 *
 * <p>Stateless; safe to share a single instance across threads.
 */
public final class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 128;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasNonLetter = false;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else {
                hasNonLetter = true;
            }
            if (hasLetter && hasNonLetter) {
                return true;
            }
        }
        return false;
    }
}
