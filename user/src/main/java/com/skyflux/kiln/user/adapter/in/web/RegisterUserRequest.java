package com.skyflux.kiln.user.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Wire DTO for {@code POST /api/v1/users}. Bean Validation rules applied on controller boundary. */
public record RegisterUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email
) {
}
