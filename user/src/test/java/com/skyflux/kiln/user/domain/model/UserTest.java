package com.skyflux.kiln.user.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void register_creates_user_with_generated_id_and_fields() {
        User u = User.register("Alice", "alice@example.com");

        assertThat(u.id()).isNotNull();
        assertThat(u.id().value()).isNotNull();
        assertThat(u.name()).isEqualTo("Alice");
        assertThat(u.email()).isEqualTo("alice@example.com");
    }

    @Test
    void register_rejects_blank_name() {
        assertThatThrownBy(() -> User.register("   ", "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void register_rejects_email_without_at_sign() {
        assertThatThrownBy(() -> User.register("Alice", "no-at-sign"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void reconstitute_accepts_existing_id_for_persistence_path() {
        UserId existing = UserId.newId();

        User u = User.reconstitute(existing, "Bob", "bob@example.com");

        assertThat(u.id()).isEqualTo(existing);
        assertThat(u.name()).isEqualTo("Bob");
        assertThat(u.email()).isEqualTo("bob@example.com");
    }

    @Test
    void registerLowercasesAndTrimsEmail() {
        User u = User.register("Alice", "  ALICE@EXAMPLE.COM  ");
        assertThat(u.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerTrimsName() {
        User u = User.register("  Alice  ", "alice@example.com");
        assertThat(u.name()).isEqualTo("Alice");
    }

    @Test
    void reconstituteDoesNotNormalize() {
        UserId existing = UserId.newId();
        User u = User.reconstitute(existing, "Alice", "Alice@EXAMPLE.com");
        assertThat(u.email()).isEqualTo("Alice@EXAMPLE.com");
        assertThat(u.name()).isEqualTo("Alice");
    }
}
