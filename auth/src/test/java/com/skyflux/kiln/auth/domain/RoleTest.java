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
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Test
    void roleShouldCarryTenantId() {
        Role r = new Role(ID, "MANAGER", "Manager", TENANT_ID);
        assertThat(r.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void buildsWithValidFields() {
        Role r = new Role(ID, "ADMIN", "Administrator", TENANT_ID);

        assertThat(r.id()).isEqualTo(ID);
        assertThat(r.code()).isEqualTo("ADMIN");
        assertThat(r.name()).isEqualTo("Administrator");
        assertThat(r.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(null, "ADMIN", "Administrator", TENANT_ID));
    }

    @Test
    void rejectsNullCode() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(ID, null, "Administrator", TENANT_ID));
    }

    @Test
    void rejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(ID, "ADMIN", null, TENANT_ID));
    }

    @Test
    void rejectsNullTenantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Role(ID, "ADMIN", "Administrator", null));
    }

    @Test
    void rejectsBlankCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Role(ID, "   ", "Administrator", TENANT_ID))
                .withMessageContaining("code blank");
    }

    @Test
    void roleCodeValueReturnsEnumName() {
        assertThat(RoleCode.ADMIN.value()).isEqualTo("ADMIN");
        assertThat(RoleCode.USER.value()).isEqualTo("USER");
    }
}
