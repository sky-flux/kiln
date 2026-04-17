package com.skyflux.kiln.user.adapter.in.web;

/** Response for {@code POST /api/v1/auth/login}. Exposes only the opaque Sa-Token value. */
public record TokenResponse(String token) {
}
