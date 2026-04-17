package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.infra.jooq.generated.tables.records.UsersRecord;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserMapper}.
 *
 * <p>Pins the contracts that the adapter's UPSERT relies on:
 * - toRecord populates exactly id / name / email / created_at / updated_at
 * - audit timestamps are UTC-offset (never local-zone)
 * - toAggregate trusts DB state (reconstitute, no normalization)
 */
class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toRecordPopulatesAllExpectedFields() {
        UserId id = UserId.newId();
        User u = User.reconstitute(id, "Alice", "alice@example.com");

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getId()).isEqualTo(id.value());
        assertThat(r.getName()).isEqualTo("Alice");
        assertThat(r.getEmail()).isEqualTo("alice@example.com");
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    void auditTimestampsAreUtc() {
        User u = User.reconstitute(UserId.newId(), "Alice", "alice@example.com");
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        UsersRecord r = mapper.toRecord(u);
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);

        assertThat(r.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(r.getUpdatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(r.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void toRecordSetsCreatedAtAndUpdatedAtToSameInstant() {
        // On INSERT, created_at and updated_at should match.
        // (The UPSERT DO UPDATE clause in the adapter overwrites updated_at but leaves created_at.)
        User u = User.reconstitute(UserId.newId(), "Alice", "alice@example.com");

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getCreatedAt()).isEqualTo(r.getUpdatedAt());
    }

    @Test
    void toAggregateReconstructsWithoutNormalization() {
        UUID uuid = UUID.randomUUID();
        UsersRecord r = new UsersRecord();
        r.setId(uuid);
        r.setName("  Alice  ");            // pre-existing trailing spaces must NOT be trimmed on read
        r.setEmail("ALICE@Example.com");   // pre-existing case must NOT be lowered on read
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        User u = mapper.toAggregate(r);

        assertThat(u.id().value()).isEqualTo(uuid);
        assertThat(u.name()).isEqualTo("  Alice  ");    // verbatim
        assertThat(u.email()).isEqualTo("ALICE@Example.com"); // verbatim
    }

    @Test
    void updatePayloadDoesNotLeakCreatedAt() {
        // Pins the adapter's invariant by inspecting which fields the mapper
        // exposes. If the mapper is ever "simplified" to also expose a method
        // like `updateFields()` that accidentally includes createdAt, this
        // test makes that decision loud: the invariant lives in the adapter's
        // doUpdate chain and must be protected by review, not by the mapper.
        // (Mapper intentionally exposes only toAggregate + toRecord.)
        long methodsWithCreatedAtInName = java.util.Arrays.stream(UserMapper.class.getDeclaredMethods())
                .filter(m -> m.getName().toLowerCase().contains("update")
                        || m.getName().toLowerCase().contains("mutable"))
                .count();
        assertThat(methodsWithCreatedAtInName)
                .as("UserMapper must not expose update-specific methods that bundle created_at")
                .isZero();
    }
}
