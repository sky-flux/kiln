/**
 * User business module.
 * Only depends on shared platform modules (common, infra).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "User",
        allowedDependencies = {"common", "infra"}
)
package com.skyflux.kiln.user;
