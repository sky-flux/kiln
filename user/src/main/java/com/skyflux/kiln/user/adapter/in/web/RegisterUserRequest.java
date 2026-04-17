package com.skyflux.kiln.user.adapter.in.web;

import com.skyflux.kiln.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire DTO for {@code POST /api/v1/users}. Bean Validation rules applied on
 * controller boundary.
 *
 * <p>Size constraints mirror the DB column widths and OWASP password input
 * caps (review finding C1): {@code @Size(max=128)} on password is a belt-and-
 * suspenders first-line DoS guard against 1MB password bombs that would
 * otherwise reach Argon2id. {@link StrongPassword} then enforces the real
 * strength rule (10-128 chars, letter + non-letter). The two constraints are
 * redundant on the upper bound but harmless; keep @Size for defence-in-depth.
 *
 * <p>{@link StrongPassword} intentionally applies only to registration — login
 * DTO does not validate strength so historical weak passwords can still
 * authenticate until a forced rotation.
 */
public record RegisterUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) @StrongPassword String password
) {
}
