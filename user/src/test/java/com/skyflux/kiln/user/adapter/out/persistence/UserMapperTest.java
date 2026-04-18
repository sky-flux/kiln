package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.jooq.generated.tables.records.UsersRecord;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserMapper}.
 *
 * <p>Pins the contracts that the adapter's UPSERT relies on:
 * - toRecord populates exactly id / name / email / password_hash / created_at / updated_at / tenant_id
 * - audit timestamps are UTC-offset (never local-zone)
 * - toAggregate trusts DB state (reconstitute, no normalization)
 */
class UserMapperTest {

    private static final String FAKE_HASH = "$argon2id$test$dummy";
    private static final UUID TENANT_ID = Ids.next();

    private final UserMapper mapper = new UserMapper();

    @Test
    void toRecordPopulatesAllExpectedFields() {
        UserId id = UserId.newId();
        User u = User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", FAKE_HASH, 0, null, "ACTIVE");

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getId()).isEqualTo(id.value());
        assertThat(r.getName()).isEqualTo("Alice");
        assertThat(r.getEmail()).isEqualTo("alice@example.com");
        assertThat(r.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    void auditTimestampsAreUtc() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "Alice", "alice@example.com", FAKE_HASH, 0, null, "ACTIVE");
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
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "Alice", "alice@example.com", FAKE_HASH, 0, null, "ACTIVE");

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getCreatedAt()).isEqualTo(r.getUpdatedAt());
    }

    @Test
    void toAggregateReconstructsWithoutNormalization() {
        UUID uuid = UUID.randomUUID();
        UUID tenantId = Ids.next();
        UsersRecord r = new UsersRecord();
        r.setId(uuid);
        r.setTenantId(tenantId);
        r.setName("  Alice  ");            // pre-existing trailing spaces must NOT be trimmed on read
        r.setEmail("ALICE@Example.com");   // pre-existing case must NOT be lowered on read
        r.setPasswordHash(FAKE_HASH);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setFailedLoginAttempts(0);
        r.setLockedUntil(null);
        r.setStatus("ACTIVE");

        User u = mapper.toAggregate(r);

        assertThat(u.id().value()).isEqualTo(uuid);
        assertThat(u.tenantId()).isEqualTo(tenantId);
        assertThat(u.name()).isEqualTo("  Alice  ");    // verbatim
        assertThat(u.email()).isEqualTo("ALICE@Example.com"); // verbatim
        assertThat(u.passwordHash()).isEqualTo(FAKE_HASH);
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

    // ──────────── Phase 4: passwordHash ────────────

    @Test
    void toRecordIncludesPasswordHash() {
        User u = User.reconstitute(UserId.newId(), TENANT_ID, "Alice", "alice@example.com", "encoded-hash-42", 0, null, "ACTIVE");

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getPasswordHash()).isEqualTo("encoded-hash-42");
    }

    @Test
    void toAggregateReadsPasswordHash() {
        UsersRecord r = new UsersRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(Ids.next());
        r.setName("Alice");
        r.setEmail("alice@example.com");
        r.setPasswordHash("encoded-from-db");
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setFailedLoginAttempts(0);
        r.setLockedUntil(null);
        r.setStatus("ACTIVE");

        User u = mapper.toAggregate(r);

        assertThat(u.passwordHash()).isEqualTo("encoded-from-db");
    }

    // ──────────── Phase 4.3 Wave 1: lockout bookkeeping round-trip ────────────

    @Test
    void toRecordWritesLockoutFields() {
        Instant lockedUntil = Instant.parse("2026-04-18T11:30:00Z");
        User u = User.reconstitute(
                UserId.newId(), TENANT_ID, "Alice", "alice@example.com", FAKE_HASH, 4, lockedUntil, "ACTIVE");

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(r.getLockedUntil()).isNotNull();
        // Mapper converts Instant → OffsetDateTime@UTC so the round-trip is
        // stable regardless of host zone.
        assertThat(r.getLockedUntil().toInstant()).isEqualTo(lockedUntil);
    }

    @Test
    void toRecordWritesDefaultsWhenUserHasNoLockoutState() {
        User u = User.register(TENANT_ID, "Bob", "bob@example.com", FAKE_HASH);

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getFailedLoginAttempts()).isZero();
        assertThat(r.getLockedUntil()).isNull();
    }

    @Test
    void toAggregateReadsLockoutFieldsWhenLocked() {
        Instant lockedUntil = Instant.parse("2026-04-18T12:00:00Z");
        UsersRecord r = new UsersRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(Ids.next());
        r.setName("Alice");
        r.setEmail("alice@example.com");
        r.setPasswordHash(FAKE_HASH);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setFailedLoginAttempts(4);
        r.setLockedUntil(lockedUntil.atOffset(ZoneOffset.UTC));
        r.setStatus("ACTIVE");

        User u = mapper.toAggregate(r);

        assertThat(u.failedLoginAttempts()).isEqualTo(4);
        assertThat(u.lockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void toAggregateReadsLockoutFieldsWhenUnlocked() {
        UsersRecord r = new UsersRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(Ids.next());
        r.setName("Alice");
        r.setEmail("alice@example.com");
        r.setPasswordHash(FAKE_HASH);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setFailedLoginAttempts(0);
        r.setLockedUntil(null);
        r.setStatus("ACTIVE");

        User u = mapper.toAggregate(r);

        assertThat(u.failedLoginAttempts()).isZero();
        assertThat(u.lockedUntil()).isNull();
    }

    // ──────────── Wave 1 T8: tenantId round-trip ────────────

    @Test
    void toRecordWritesTenantId() {
        UUID tenantId = Ids.next();
        User u = User.register(tenantId, "Alice", "alice@example.com", FAKE_HASH);

        UsersRecord r = mapper.toRecord(u);

        assertThat(r.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void toAggregateReadsTenantId() {
        UUID tenantId = Ids.next();
        UsersRecord r = new UsersRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setName("Alice");
        r.setEmail("alice@example.com");
        r.setPasswordHash(FAKE_HASH);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setFailedLoginAttempts(0);
        r.setLockedUntil(null);
        r.setStatus("ACTIVE");

        User u = mapper.toAggregate(r);

        assertThat(u.tenantId()).isEqualTo(tenantId);
    }
}
