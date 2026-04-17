package com.skyflux.kiln.user.adapter.in.web;

/**
 * Response DTO for {@link AdminController#userCount()} — the Phase 4.2 admin
 * demo surface. Kept intentionally minimal: only the {@code count} field is
 * exposed, so consumer contracts cannot start depending on incidental fields.
 */
public record UserCountResponse(long count) {
}
