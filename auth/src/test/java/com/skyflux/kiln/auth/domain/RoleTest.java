package com.skyflux.kiln.auth.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Record invariants for {@link Role} and {@link RoleCode}.
 *
 * <p>Pure value-object tests — no Spring, no DB. Exercises the compact
 * canonical-ctor guard clauses so a future edit to the record can't slip
 * through the Supporting-subdomain's single input-validation layer.
 */
class RoleTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void buildsWithValidFields() {
        Role r = new Role(ID, "ADMIN", "Administrator");

        assertThat(r.id()).isEqualTo(ID);
        assertThat(r.code()).isEqualTo("ADMIN");
        assertThat(r.name()).isEqualTo("Administrator");
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(null, "ADMIN", "Administrator"));
    }

    @Test
    void rejectsNullCode() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(ID, null, "Administrator"));
    }

    @Test
    void rejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(ID, "ADMIN", null));
    }

    @Test
    void rejectsBlankCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Role(ID, "   ", "Administrator"))
                .withMessageContaining("code blank");
    }

    @Test
    void roleCodeValueReturnsEnumName() {
        assertThat(RoleCode.ADMIN.value()).isEqualTo("ADMIN");
        assertThat(RoleCode.USER.value()).isEqualTo("USER");
    }
}
