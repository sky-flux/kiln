/**
 * User domain events.
 *
 * <p>Exposed as the Modulith named interface {@code events} so sibling modules
 * (e.g. {@code auth}) can listen to events published here without importing
 * anything else from the user module's implementation packages. This is the
 * single authorised cross-module surface for user-originated events.
 */
@org.springframework.modulith.NamedInterface("events")
package com.skyflux.kiln.user.domain.event;
