package com.skyflux.kiln.auth.domain;

/**
 * Stable role identifiers matched against Sa-Token's {@code @SaCheckRole}.
 *
 * <p>The enum name is the wire value (seed rows in V4 use the same text), so
 * {@link #value()} just returns {@link #name()} — having a dedicated accessor
 * makes call-sites read as intent ("give me the role code string") rather
 * than leaking the enum API.
 */
public enum RoleCode {
    ADMIN, USER;

    /** Wire value used against the {@code roles.code} column. */
    public String value() {
        return name();
    }
}
