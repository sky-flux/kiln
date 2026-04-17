package com.skyflux.kiln.auth.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Record invariants for {@link Permission}.
 */
class PermissionTest {

    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void buildsWithValidFields() {
        Permission p = new Permission(ID, "user.admin", "Administer users");

        assertThat(p.id()).isEqualTo(ID);
        assertThat(p.code()).isEqualTo("user.admin");
        assertThat(p.name()).isEqualTo("Administer users");
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Permission(null, "user.admin", "Administer users"));
    }

    @Test
    void rejectsNullCode() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Permission(ID, null, "Administer users"));
    }

    @Test
    void rejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Permission(ID, "user.admin", null));
    }
}
