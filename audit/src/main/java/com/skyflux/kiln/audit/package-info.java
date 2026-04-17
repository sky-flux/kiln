/**
 * Append-only audit log for security- and identity-relevant events.
 *
 * <p>Classified as a <em>Supporting Subdomain</em> per {@code docs/design.md}
 * Ch 19.17, so it uses the simplified {@code api / domain / repo / internal /
 * config} layout (Ch 3.1) rather than the full Hexagonal split reserved for
 * Core Domains.
 *
 * <p>Wave 3 C4 tightens {@code allowedDependencies} to include the
 * {@code events} named interfaces exposed by {@code user} and {@code auth}:
 * the audit module's listeners ({@code UserLifecycleAuditListener},
 * {@code LoginAuditListener}, {@code RoleAuditListener}) subscribe to those
 * event families via plain Spring {@code @TransactionalEventListener}. No
 * other packages from those modules are reachable.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Audit",
        allowedDependencies = {"common", "infra", "user :: events", "auth :: events"})
package com.skyflux.kiln.audit;
