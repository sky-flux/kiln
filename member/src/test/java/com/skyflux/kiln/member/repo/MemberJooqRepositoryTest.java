package com.skyflux.kiln.member.repo;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.domain.MemberLevel;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MemberJooqRepositoryTest.TestApp.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberJooqRepositoryTest {

    @SpringBootApplication(exclude = {
            org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class,
            cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate.class
    })
    static class TestApp {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:18.3-alpine");
        }
    }

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private UUID userId;

    @Autowired MemberJooqRepository repo;
    @Autowired DSLContext dsl;

    @BeforeAll
    void seedUser() {
        userId = Ids.next();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.USERS)
                .set(Tables.USERS.ID, userId)
                .set(Tables.USERS.TENANT_ID, TENANT_ID)
                .set(Tables.USERS.NAME, "Test User")
                .set(Tables.USERS.EMAIL, "test+" + userId + "@example.com")
                .set(Tables.USERS.PASSWORD_HASH, "$argon2id$v=19$test")
                .set(Tables.USERS.STATUS, "ACTIVE")
                .set(Tables.USERS.CREATED_AT, now)
                .set(Tables.USERS.UPDATED_AT, now)
                .execute();
    }

    @Test void shouldSaveAndFindByUserId() {
        Member m = Member.create(TENANT_ID, userId);
        repo.save(m);

        Optional<Member> found = repo.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().tenantId()).isEqualTo(TENANT_ID);
        assertThat(found.get().level()).isEqualTo(MemberLevel.BRONZE);
        assertThat(found.get().points()).isZero();
    }

    @Test void shouldAccumulatePoints() {
        Member m = Member.create(TENANT_ID, userId);
        repo.save(m);

        Member updated = m.awardPoints(1000);
        repo.save(updated);

        Optional<Member> found = repo.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().points()).isEqualTo(1000);
        assertThat(found.get().level()).isEqualTo(MemberLevel.SILVER);
    }

    @Test void shouldListByTenant() {
        Member m = Member.create(TENANT_ID, userId);
        repo.save(m);

        PageQuery query = new PageQuery(1, 20, null);
        PageResult<Member> result = repo.listByTenant(TENANT_ID, query);
        assertThat(result.items()).isNotEmpty();
        assertThat(result.items()).allMatch(r -> r.tenantId().equals(TENANT_ID));
    }

    @Test void shouldReturnEmptyForUnknownUser() {
        assertThat(repo.findByUserId(UUID.randomUUID())).isEmpty();
    }
}
