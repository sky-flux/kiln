/**
 * Tenant module — Generic subdomain.
 * Provides multi-tenant isolation via PostgreSQL RLS + Java 25 ScopedValue context propagation.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Tenant",
    allowedDependencies = {"common", "infra"}
)
package com.skyflux.kiln.tenant;
