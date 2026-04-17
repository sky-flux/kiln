package com.skyflux.kiln.user.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final String FAKE_HASH = "fake-hash-for-test";

    @Test
    void register_creates_user_with_generated_id_and_fields() {
        User u = User.register("Alice", "alice@example.com", FAKE_HASH);

        assertThat(u.id()).isNotNull();
        assertThat(u.id().value()).isNotNull();
        assertThat(u.name()).isEqualTo("Alice");
        assertThat(u.email()).isEqualTo("alice@example.com");
        assertThat(u.passwordHash()).isEqualTo(FAKE_HASH);
    }

    @Test
    void register_rejects_blank_name() {
        assertThatThrownBy(() -> User.register("   ", "a@b.com", FAKE_HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void register_rejects_email_without_at_sign() {
        assertThatThrownBy(() -> User.register("Alice", "no-at-sign", FAKE_HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void reconstitute_accepts_existing_id_for_persistence_path() {
        UserId existing = UserId.newId();

        User u = User.reconstitute(existing, "Bob", "bob@example.com", FAKE_HASH);

        assertThat(u.id()).isEqualTo(existing);
        assertThat(u.name()).isEqualTo("Bob");
        assertThat(u.email()).isEqualTo("bob@example.com");
        assertThat(u.passwordHash()).isEqualTo(FAKE_HASH);
    }

    @Test
    void registerLowercasesAndTrimsEmail() {
        User u = User.register("Alice", "  ALICE@EXAMPLE.COM  ", FAKE_HASH);
        assertThat(u.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerTrimsName() {
        User u = User.register("  Alice  ", "alice@example.com", FAKE_HASH);
        assertThat(u.name()).isEqualTo("Alice");
    }

    @Test
    void reconstituteDoesNotNormalize() {
        UserId existing = UserId.newId();
        User u = User.reconstitute(existing, "Alice", "Alice@EXAMPLE.com", FAKE_HASH);
        assertThat(u.email()).isEqualTo("Alice@EXAMPLE.com");
        assertThat(u.name()).isEqualTo("Alice");
    }

    // ──────────── Phase 4: passwordHash ────────────

    @Test
    void registerRequiresPasswordHash_null() {
        assertThatThrownBy(() -> User.register("Alice", "alice@example.com", null))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    void registerRequiresPasswordHash_blank() {
        assertThatThrownBy(() -> User.register("Alice", "alice@example.com", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    void passwordHashIsNotMutated() {
        // Even though email/name are trimmed + lowercased, the hash must be stored verbatim —
        // a hash contains significant characters (including leading `$` and case) that would
        // be corrupted by any normalization pass.
        String hash = "$argon2id$v=19$m=65536,t=3,p=4$U29tZVNhbHQ$Somedigestbytes";
        User u = User.register("  Alice  ", "  ALICE@EXAMPLE.COM  ", hash);

        assertThat(u.passwordHash()).isEqualTo(hash);  // verbatim, not lowercased or trimmed
    }

    @Test
    void passwordHashAccessorReturnsStoredValue() {
        String hash = "stored-hash-value-42";
        User u = User.reconstitute(UserId.newId(), "Alice", "a@b.com", hash);

        assertThat(u.passwordHash()).isEqualTo(hash);
    }
}
