package com.skyflux.kiln.user.domain.model;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final String FAKE_HASH = "fake-hash-for-test";
    private static final UUID TENANT_ID = Ids.next();

    @Test
    void register_creates_user_with_generated_id_and_fields() {
        User u = User.register(TENANT_ID, "Alice", "alice@example.com", FAKE_HASH);

        assertThat(u.id()).isNotNull();
        assertThat(u.id().value()).isNotNull();
        assertThat(u.tenantId()).isEqualTo(TENANT_ID);
        assertThat(u.name()).isEqualTo("Alice");
        assertThat(u.email()).isEqualTo("alice@example.com");
        assertThat(u.passwordHash()).isEqualTo(FAKE_HASH);
    }

    @Test
    void register_rejects_blank_name() {
        assertThatThrownBy(() -> User.register(TENANT_ID, "   ", "a@b.com", FAKE_HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void register_rejects_email_without_at_sign() {
        assertThatThrownBy(() -> User.register(TENANT_ID, "Alice", "no-at-sign", FAKE_HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void reconstitute_accepts_existing_id_for_persistence_path() {
        UserId existing = UserId.newId();
        UUID tenantId = Ids.next();

        User u = User.reconstitute(existing, tenantId, "Bob", "bob@example.com", FAKE_HASH, 0, null, "ACTIVE");

        assertThat(u.id()).isEqualTo(existing);
        assertThat(u.tenantId()).isEqualTo(tenantId);
        assertThat(u.name()).isEqualTo("Bob");
        assertThat(u.email()).isEqualTo("bob@example.com");
        assertThat(u.passwordHash()).isEqualTo(FAKE_HASH);
    }

    @Test
    void registerLowercasesAndTrimsEmail() {
        User u = User.register(TENANT_ID, "Alice", "  ALICE@EXAMPLE.COM  ", FAKE_HASH);
        assertThat(u.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerTrimsName() {
        User u = User.register(TENANT_ID, "  Alice  ", "alice@example.com", FAKE_HASH);
        assertThat(u.name()).isEqualTo("Alice");
    }

    @Test
    void reconstituteDoesNotNormalize() {
        UserId existing = UserId.newId();
        UUID tenantId = Ids.next();
        User u = User.reconstitute(existing, tenantId, "Alice", "Alice@EXAMPLE.com", FAKE_HASH, 0, null, "ACTIVE");
        assertThat(u.email()).isEqualTo("Alice@EXAMPLE.com");
        assertThat(u.name()).isEqualTo("Alice");
    }

    // ──────────── Phase 4: passwordHash ────────────

    @Test
    void registerRequiresPasswordHash_null() {
        assertThatThrownBy(() -> User.register(TENANT_ID, "Alice", "alice@example.com", null))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    void registerRequiresPasswordHash_blank() {
        assertThatThrownBy(() -> User.register(TENANT_ID, "Alice", "alice@example.com", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    void passwordHashIsNotMutated() {
        // Even though email/name are trimmed + lowercased, the hash must be stored verbatim —
        // a hash contains significant characters (including leading `$` and case) that would
        // be corrupted by any normalization pass.
        String hash = "$argon2id$v=19$m=65536,t=3,p=4$U29tZVNhbHQ$Somedigestbytes";
        User u = User.register(TENANT_ID, "  Alice  ", "  ALICE@EXAMPLE.COM  ", hash);

        assertThat(u.passwordHash()).isEqualTo(hash);  // verbatim, not lowercased or trimmed
    }

    @Test
    void passwordHashAccessorReturnsStoredValue() {
        String hash = "stored-hash-value-42";
        UUID tenantId = Ids.next();
        User u = User.reconstitute(UserId.newId(), tenantId, "Alice", "a@b.com", hash, 0, null, "ACTIVE");

        assertThat(u.passwordHash()).isEqualTo(hash);
    }

    // ──────────── Phase 4.3 Wave 1: lockout bookkeeping (passive carriers) ────────────

    @Test
    void registerDefaultsLockoutBookkeepingToZeroAndNull() {
        // Newly registered users start with a clean slate: no failed attempts,
        // no lock. Wave 2 will mutate these fields on auth failures/successes.
        User u = User.register(TENANT_ID, "x", "x@y", FAKE_HASH);

        assertThat(u.failedLoginAttempts()).isZero();
        assertThat(u.lockedUntil()).isNull();
    }

    @Test
    void reconstitutePropagatesLockoutFields() {
        // Reconstitute trusts DB state — the loaded counter + timestamp round-trip verbatim.
        UserId id = UserId.newId();
        UUID tenantId = Ids.next();
        Instant lockedUntil = Instant.parse("2026-04-18T10:00:00Z");

        User u = User.reconstitute(id, tenantId, "x", "x@y", "h", 3, lockedUntil, "ACTIVE");

        assertThat(u.failedLoginAttempts()).isEqualTo(3);
        assertThat(u.lockedUntil()).isEqualTo(lockedUntil);
    }

    // ──────────── Phase 4.3 Wave 2: lockout business rules ────────────

    private static final Instant NOW = Instant.parse("2026-04-18T10:00:00Z");

    @Test
    void isLockedFalseWhenLockedUntilNull() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");
        assertThat(u.isLocked(NOW)).isFalse();
    }

    @Test
    void isLockedFalseWhenLockedUntilInPast() {
        Instant past = NOW.minusSeconds(1);
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, past, "ACTIVE");
        assertThat(u.isLocked(NOW)).isFalse();
    }

    @Test
    void isLockedTrueWhenLockedUntilInFuture() {
        Instant future = NOW.plusSeconds(60);
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, future, "ACTIVE");
        assertThat(u.isLocked(NOW)).isTrue();
    }

    @Test
    void isLockedRejectsNullNow() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");
        assertThatThrownBy(() -> u.isLocked(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerLoginFailureIncrementsCounter() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");

        User updated = u.registerLoginFailure(NOW, 5, Duration.ofMinutes(15));

        assertThat(updated.failedLoginAttempts()).isEqualTo(1);
        assertThat(updated.lockedUntil()).isNull();
        assertThat(updated.isLocked(NOW)).isFalse();
    }

    @Test
    void registerLoginFailureLocksWhenThresholdReached() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 4, null, "ACTIVE");
        Duration lockDuration = Duration.ofMinutes(15);

        User updated = u.registerLoginFailure(NOW, 5, lockDuration);

        assertThat(updated.lockedUntil()).isEqualTo(NOW.plus(lockDuration));
        assertThat(updated.isLocked(NOW)).isTrue();
    }

    @Test
    void registerLoginFailureResetsCounterWhenLocking() {
        // When the failure trips the lock, the counter is consumed — after the
        // lock expires the user gets a fresh N attempts.
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 4, null, "ACTIVE");

        User updated = u.registerLoginFailure(NOW, 5, Duration.ofMinutes(15));

        assertThat(updated.failedLoginAttempts()).isZero();
    }

    @Test
    void registerLoginFailureRejectsZeroThreshold() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");
        assertThatThrownBy(() -> u.registerLoginFailure(NOW, 0, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockThreshold");
    }

    @Test
    void registerLoginFailureRejectsNullDuration() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");
        assertThatThrownBy(() -> u.registerLoginFailure(NOW, 5, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerLoginFailureRejectsNullNow() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");
        assertThatThrownBy(() -> u.registerLoginFailure(null, 5, Duration.ofMinutes(15)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerLoginSuccessClearsCounterAndLock() {
        Instant locked = NOW.plusSeconds(600);
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 3, locked, "ACTIVE");

        User updated = u.registerLoginSuccess();

        assertThat(updated.failedLoginAttempts()).isZero();
        assertThat(updated.lockedUntil()).isNull();
    }

    @Test
    void registerLoginFailurePreservesTenantId() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 0, null, "ACTIVE");

        User updated = u.registerLoginFailure(NOW, 5, Duration.ofMinutes(15));

        assertThat(updated.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void registerLoginSuccessPreservesTenantId() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "x", "x@y", FAKE_HASH, 3, NOW.plusSeconds(60), "ACTIVE");

        User updated = u.registerLoginSuccess();

        assertThat(updated.tenantId()).isEqualTo(TENANT_ID);
    }

    // ──────────── Wave 2a: status field ────────────

    @Test
    void newUserShouldHaveActiveStatus() {
        User u = User.register(TENANT_ID, "Alice", "alice@example.com", FAKE_HASH);
        assertThat(u.status()).isEqualTo("ACTIVE");
    }

    @Test
    void deactivateShouldReturnInactiveUser() {
        User u = User.register(TENANT_ID, "Alice", "alice@example.com", FAKE_HASH);
        assertThat(u.deactivate().status()).isEqualTo("INACTIVE");
    }

    @Test
    void deactivatingInactiveUserShouldThrow() {
        User u = User.register(TENANT_ID, "Alice", "alice@example.com", FAKE_HASH).deactivate();
        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(u::deactivate);
    }

    @Test
    void withNameShouldUpdateNamePreservingOtherFields() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "Old", "u@e.com", FAKE_HASH, 0, null, "ACTIVE");
        User updated = u.withName("New Name");
        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.email()).isEqualTo("u@e.com");
        assertThat(updated.status()).isEqualTo("ACTIVE");
    }

    @Test
    void withNameShouldRejectBlankName() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "Old", "u@e.com", FAKE_HASH, 0, null, "ACTIVE");
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> u.withName("   "));
    }
}
