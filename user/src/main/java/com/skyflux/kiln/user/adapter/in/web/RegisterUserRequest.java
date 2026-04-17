package com.skyflux.kiln.user.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire DTO for {@code POST /api/v1/users}. Bean Validation rules applied on
 * controller boundary.
 *
 * <p>Size constraints mirror the DB column widths and OWASP password input
 * caps (review finding C1): {@code password} max 128 chars prevents
 * Argon2id-DoS via oversized input.
 */
public record RegisterUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
