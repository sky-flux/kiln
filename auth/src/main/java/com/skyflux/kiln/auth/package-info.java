/**
 * RBAC data source — roles, permissions, role-permission linkage, and
 * user-role assignment.
 *
 * <p>Classified as a <em>Supporting Subdomain</em> per {@code docs/design.md}
 * Ch 19.17, so it uses the simplified {@code api / domain / repo / internal /
 * config} layout (Ch 3.1) rather than the full Hexagonal split reserved for
 * Core Domains.
 *
 * <p>The Sa-Token permission/role lookup ({@code StpInterface}) lives in
 * {@link com.skyflux.kiln.auth.internal} rather than {@code infra/security/}
 * because implementing it requires querying this module's repositories.
 * Keeping it alongside the data it reads avoids the infra → auth reverse
 * dependency that would otherwise break the Hexagonal layering.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Auth",
        // Narrow the import surface: auth only observes user via the `events`
        // named interface (UserRegistered etc.); user's implementation packages
        // are off-limits. When ModularityTest upgrades to `verify()` this pin
        // keeps the cross-module contract tight.
        allowedDependencies = {"common", "infra", "user :: events"})
package com.skyflux.kiln.auth;
