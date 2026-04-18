package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.domain.MemberId;
import com.skyflux.kiln.member.domain.MemberLevel;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = MemberController.class)
    static class BootConfig {}

    @MockitoBean MemberService service;
    // UserRegisteredMemberListener (same package) requires this — provide mock so context loads
    @MockitoBean MemberJooqRepository memberJooqRepository;
    // OrderPaidMemberListener (same package) requires a Clock — provide mock so context loads
    @MockitoBean Clock clock;

    @Autowired MockMvc mvc;

    private static Member sampleMember() {
        UUID tenantId = Ids.next();
        UUID userId = Ids.next();
        return Member.reconstitute(
                MemberId.newId(), tenantId, userId, MemberLevel.BRONZE, 100, "ACTIVE");
    }

    @Test
    void get_my_profile_returns_200() throws Exception {
        Member m = sampleMember();
        when(service.getMyMember()).thenReturn(m);

        mvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value("BRONZE"))
                .andExpect(jsonPath("$.data.points").value(100));
    }

    @Test
    void admin_list_returns_200_with_paginated_members() throws Exception {
        Member m = sampleMember();
        PageQuery query = new PageQuery(1, 20, null);
        when(service.list(any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(m), 1L, query));

        mvc.perform(get("/api/v1/admin/members")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void award_points_returns_200_with_updated_member() throws Exception {
        Member m = sampleMember();
        UUID userId = m.userId();
        when(service.awardPoints(eq(userId), eq(500))).thenReturn(m);

        String body = """
                {"points":500}
                """;

        mvc.perform(post("/api/v1/admin/members/" + userId + "/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").value(100));
    }

    @Test
    void award_points_with_zero_returns_400() throws Exception {
        UUID userId = Ids.next();
        String body = """
                {"points":0}
                """;

        mvc.perform(post("/api/v1/admin/members/" + userId + "/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
