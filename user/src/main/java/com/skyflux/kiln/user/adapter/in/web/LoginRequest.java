package com.skyflux.kiln.user.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire DTO for {@code POST /api/v1/auth/login}. Bean Validation applied on
 * controller boundary.
 *
 * <p>{@code password} is capped at 128 chars — OWASP Password Storage
 * Cheat Sheet recommendation. Without this cap, an attacker can POST a
 * megabyte-sized password field and force Argon2id to consume ~19 MiB
 * per hash verification, saturating CPU and memory (review finding C1).
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
