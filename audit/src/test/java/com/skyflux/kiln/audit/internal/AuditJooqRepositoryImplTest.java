package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.audit.repo.AuditRepository;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AuditJooqRepositoryImpl}. Full Postgres
 * round-trip via Testcontainers — {@code JSONB} write/read and
 * {@code ORDER BY ... DESC, LIMIT/OFFSET} semantics need the real PG parser.
 *
 * <p>No FK on {@code audits} columns (by design) so tests do not need to seed
 * {@code users} first.
 */
@SpringBootTest(classes = AuditJooqRepositoryImplTest.TestApp.class)
class AuditJooqRepositoryImplTest {

    @SpringBootApplication(exclude = {
            org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class,
            cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate.class
    })
    @ComponentScan(basePackages = "com.skyflux.kiln.audit")
    static class TestApp {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:18.3-alpine");
        }
    }

    @Autowired
    AuditRepository repo;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void clean() {
        dsl.deleteFrom(Tables.AUDITS).execute();
    }

    @Test
    void saveThenListRoundTrips() {
        UUID actor = UUID.randomUUID();
        Audit e = new Audit(
                UUID.randomUUID(),
                Instant.parse("2026-04-18T10:00:00Z"),
                AuditResource.USER,
                AuditAction.LOGIN,
                actor,
                null,
                "{\"ip\":\"10.0.0.1\"}",
                "req-42");

        repo.save(e);

        PageResult<Audit> page = repo.list(new PageQuery(1, 20, null), null, null, null, null);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        Audit read = page.items().get(0);
        assertThat(read.id()).isEqualTo(e.id());
        assertThat(read.resource()).isEqualTo(AuditResource.USER);
        assertThat(read.action()).isEqualTo(AuditAction.LOGIN);
        assertThat(read.actorUserId()).isEqualTo(actor);
        assertThat(read.targetUserId()).isNull();
        assertThat(read.details()).contains("10.0.0.1"); // JSONB canonicalisation may reformat whitespace
        assertThat(read.requestId()).isEqualTo("req-42");
        assertThat(read.occurredAt()).isEqualTo(e.occurredAt());
    }

    @Test
    void listOrderedByOccurredAtDesc() {
        Instant t1 = Instant.parse("2026-04-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-04-18T09:00:00Z");
        Instant t3 = Instant.parse("2026-04-18T10:00:00Z");
        repo.save(event(t1, AuditResource.USER, AuditAction.LOGIN, null));
        repo.save(event(t3, AuditResource.USER, AuditAction.LOGIN, null));
        repo.save(event(t2, AuditResource.USER, AuditAction.LOGIN, null));

        PageResult<Audit> page = repo.list(new PageQuery(1, 20, null), null, null, null, null);

        assertThat(page.items()).extracting(Audit::occurredAt)
                .containsExactly(t3, t2, t1);
    }

    @Test
    void listFilterByResourceAndActionMatches() {
        repo.save(event(Instant.parse("2026-04-18T08:00:00Z"), AuditResource.USER, AuditAction.LOGIN, null));
        repo.save(event(Instant.parse("2026-04-18T09:00:00Z"), AuditResource.ROLE, AuditAction.ASSIGN, null));
        repo.save(event(Instant.parse("2026-04-18T10:00:00Z"), AuditResource.ROLE, AuditAction.ASSIGN, null));

        PageResult<Audit> page = repo.list(new PageQuery(1, 20, null), AuditResource.ROLE, AuditAction.ASSIGN, null, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(Audit::resource).containsOnly(AuditResource.ROLE);
        assertThat(page.items()).extracting(Audit::action).containsOnly(AuditAction.ASSIGN);
    }

    @Test
    void listFilterByActorMatches() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repo.save(event(Instant.parse("2026-04-18T08:00:00Z"), AuditResource.USER, AuditAction.LOGIN, a));
        repo.save(event(Instant.parse("2026-04-18T09:00:00Z"), AuditResource.USER, AuditAction.LOGIN, b));
        repo.save(event(Instant.parse("2026-04-18T10:00:00Z"), AuditResource.USER, AuditAction.LOGIN, a));

        PageResult<Audit> page = repo.list(new PageQuery(1, 20, null), null, null, a, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(Audit::actorUserId).containsOnly(a);
    }

    @Test
    void listPaginatesCorrectly() {
        for (int i = 0; i < 5; i++) {
            // Increasing timestamps so DESC order maps predictably to insertion reverse.
            repo.save(event(Instant.parse("2026-04-18T1" + i + ":00:00Z"), AuditResource.USER, AuditAction.LOGIN, null));
        }

        PageResult<Audit> p1 = repo.list(new PageQuery(1, 2, null), null, null, null, null);
        PageResult<Audit> p2 = repo.list(new PageQuery(2, 2, null), null, null, null, null);
        PageResult<Audit> p3 = repo.list(new PageQuery(3, 2, null), null, null, null, null);

        assertThat(p1.total()).isEqualTo(5);
        assertThat(p1.items()).hasSize(2);
        assertThat(p2.items()).hasSize(2);
        assertThat(p3.items()).hasSize(1);

        // No overlap across pages
        List<UUID> ids1 = p1.items().stream().map(Audit::id).toList();
        List<UUID> ids2 = p2.items().stream().map(Audit::id).toList();
        assertThat(ids1).doesNotContainAnyElementsOf(ids2);
    }

    @Test
    void countIgnoresPagination() {
        for (int i = 0; i < 7; i++) {
            repo.save(event(Instant.parse("2026-04-18T10:0" + i + ":00Z"), AuditResource.USER, AuditAction.LOGIN, null));
        }

        assertThat(repo.count(null, null, null, null)).isEqualTo(7);
        // Small page — total must still reflect all 7 rows.
        PageResult<Audit> p = repo.list(new PageQuery(1, 2, null), null, null, null, null);
        assertThat(p.total()).isEqualTo(7);
        assertThat(p.items()).hasSize(2);
    }

    @Test
    void saveHandlesNullDetailsAndActor() {
        Audit e = new Audit(
                UUID.randomUUID(),
                Instant.parse("2026-04-18T10:00:00Z"),
                AuditResource.USER,
                AuditAction.LOGIN,
                null, null, null, null);

        repo.save(e);

        PageResult<Audit> page = repo.list(new PageQuery(1, 20, null), null, null, null, null);
        assertThat(page.items()).hasSize(1);
        Audit read = page.items().get(0);
        assertThat(read.actorUserId()).isNull();
        assertThat(read.details()).isNull();
        assertThat(read.requestId()).isNull();
    }

    private static Audit event(Instant at, AuditResource resource, AuditAction action, UUID actor) {
        return new Audit(
                UUID.randomUUID(), at, resource, action, actor, null, null, null);
    }
}
