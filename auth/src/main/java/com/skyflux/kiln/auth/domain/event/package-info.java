/**
 * Auth domain events.
 *
 * <p>Exposed as the Modulith named interface {@code events} so sibling modules
 * (e.g. {@code audit}) can listen to events published here without importing
 * anything else from the auth module's implementation packages. This is the
 * single authorised cross-module surface for auth-originated events. Mirrors
 * the {@code user :: events} named interface established in Phase 4.2.
 */
@org.springframework.modulith.NamedInterface("events")
package com.skyflux.kiln.auth.domain.event;
