package com.skyflux.kiln.auth.domain;

import java.util.List;
import java.util.UUID;

/**
 * Query-side surface exposed by the auth module for Sa-Token's
 * {@code StpInterface}.
 *
 * <p>Intentionally narrow — it only reads, never writes. Callers inside the
 * auth module ({@code StpInterfaceImpl}) use it to answer "what roles /
 * permissions does this user hold?" on every request. Heavy callers should
 * cache externally; this interface makes no caching promise.
 */
public interface PermissionLookupService {

    /** All permission codes granted (transitively via roles) to the given user. */
    List<String> permissionsFor(UUID userId);

    /** All role codes held by the given user. */
    List<String> rolesFor(UUID userId);
}
