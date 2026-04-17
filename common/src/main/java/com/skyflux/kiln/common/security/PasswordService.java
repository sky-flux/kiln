package com.skyflux.kiln.common.security;

/**
 * Password hashing contract. Defined in {@code common/} so both the
 * application layer (use cases) and infrastructure layer (Argon2id impl
 * in {@code infra/security/}) can reference it without a framework
 * dependency — preserves the Hexagonal "domain has zero framework deps"
 * invariant (see {@code docs/design.md} Ch 19.6.3).
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Return self-contained encoded strings from {@link #hash(String)} —
 *       including algorithm id, parameters, salt and digest (so the stored
 *       format can roundtrip without a separate parameter column).</li>
 *   <li>Use a constant-time comparison in {@link #verify(String, String)} —
 *       never {@code String.equals}.</li>
 *   <li>Reject null / blank plaintext with {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <p>Reference implementation: Argon2id (OWASP 2024+ parameters).
 */
public interface PasswordService {

    /**
     * Hash a plaintext password. The returned string is self-contained and
     * suitable for direct storage in a {@code TEXT} column.
     *
     * @throws IllegalArgumentException if {@code plaintext} is null or blank.
     */
    String hash(String plaintext);

    /**
     * Constant-time verification of a plaintext against a previously-hashed
     * encoded string (as produced by {@link #hash}).
     *
     * @return {@code true} iff the plaintext matches the stored hash.
     * @throws IllegalArgumentException if either argument is null or blank.
     */
    boolean verify(String plaintext, String encodedHash);
}
