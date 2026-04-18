package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.dict.api.DictQueryService;
import com.skyflux.kiln.dict.domain.DictItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link DictItemController}.
 *
 * <p>Sa-Token's {@code @SaCheckRole} AOP is not loaded in the WebMvcTest slice,
 * so these tests exercise the controller's request mapping, validation, and
 * service delegation without the auth gate.
 */
@WebMvcTest(DictItemController.class)
class DictItemControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
        basePackageClasses = DictItemController.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = DictItemController.class),
        useDefaultFilters = false
    )
    static class BootConfig {}

    @MockitoBean
    DictQueryService queryService;

    @MockitoBean
    DictService service;

    @Autowired
    MockMvc mvc;

    private static DictItem sampleItem(String code) {
        return new DictItem(UUID.randomUUID(), UUID.randomUUID(), code,
                "Label " + code, 0, true, UUID.randomUUID(), null);
    }

    @Test
    void get_items_by_type_code_returns_200_with_items() throws Exception {
        when(queryService.getItems("GENDER")).thenReturn(
                List.of(sampleItem("MALE"), sampleItem("FEMALE")));

        mvc.perform(get("/api/v1/dict/GENDER/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void post_add_item_returns_201() throws Exception {
        when(service.addItem(eq("GENDER"), eq("MALE"), eq("Male"), eq(0)))
                .thenReturn(sampleItem("MALE"));

        mvc.perform(post("/api/v1/admin/dict/types/GENDER/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"MALE","label":"Male","sortOrder":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("MALE"));
    }

    @Test
    void delete_item_returns_204() throws Exception {
        UUID itemId = UUID.randomUUID();
        doNothing().when(service).deleteItem(any(UUID.class), eq("GENDER"));

        mvc.perform(delete("/api/v1/admin/dict/items/{id}", itemId)
                        .param("typeCode", "GENDER"))
                .andExpect(status().isNoContent());
    }
}
