package com.skyflux.kiln.audit.internal.web;

import com.skyflux.kiln.audit.api.AuditQueryService;
import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import com.skyflux.kiln.infra.web.GlobalExceptionHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuditQueryController}.
 *
 * <p>Same caveat as {@code AdminControllerTest}: {@code @SaCheckRole("ADMIN")}
 * is Sa-Token AOP, NOT wired into {@code @WebMvcTest}. End-to-end role enforcement
 * is covered in {@code KilnIntegrationTest}. This slice verifies:
 * <ul>
 *   <li>Happy path — 200 + JSON body shape.</li>
 *   <li>Filter query params thread through to the service.</li>
 *   <li>{@code size} above 200 is rejected (Bean Validation 400).</li>
 * </ul>
 */
@WebMvcTest(AuditQueryController.class)
class AuditQueryControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = AuditQueryController.class)
    @Import(GlobalExceptionHandler.class)
    static class BootConfig {
    }

    @MockitoBean
    AuditQueryService queryService;

    @Autowired
    MockMvc mvc;

    @Test
    void list_returns_page_ok() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Audit e = new Audit(
                id,
                Instant.parse("2026-04-18T10:00:00Z"),
                AuditResource.USER,
                AuditAction.LOGIN,
                null, null, null, null);
        when(queryService.list(any(), any(), any(), any(), any()))
                .thenReturn(PageResult.of(List.of(e), 1L, new PageQuery(1, 20, null)));

        mvc.perform(get("/api/v1/admin/audit-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].resource").value("USER"))
                .andExpect(jsonPath("$.items[0].action").value("LOGIN"));
    }

    @Test
    void list_threads_filters_to_service() throws Exception {
        UUID actor = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(queryService.list(any(), any(), any(), any(), any()))
                .thenReturn(PageResult.empty(new PageQuery(2, 50, null)));

        mvc.perform(get("/api/v1/admin/audit-events")
                        .param("page", "2")
                        .param("size", "50")
                        .param("resource", "ROLE")
                        .param("action", "ASSIGN")
                        .param("actorUserId", actor.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<PageQuery> pageCaptor = ArgumentCaptor.forClass(PageQuery.class);
        verify(queryService).list(pageCaptor.capture(),
                eq(AuditResource.ROLE), eq(AuditAction.ASSIGN), eq(actor), isNull());
        PageQuery captured = pageCaptor.getValue();
        assertThat(captured.page()).isEqualTo(2);
        assertThat(captured.size()).isEqualTo(50);
    }

    @Test
    void list_rejects_size_above_max() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-events").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_rejects_page_below_one() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-events").param("page", "0"))
                .andExpect(status().isBadRequest());
    }
}
