package com.skyflux.kiln.user.application.port.in;

/**
 * Inbound port: total registered users.
 *
 * <p>Phase 4.2 Gate 3 I3: the admin-facing user count must go through an
 * inbound use case rather than letting the web adapter reach into the
 * outbound {@code UserRepository} port directly. The service is trivial
 * but the indirection is load-bearing: it keeps the hexagonal boundary
 * intact and leaves a seam to add authorization, auditing, or caching
 * without editing the controller.
 */
public interface CountUsersUseCase {
    long execute();
}
