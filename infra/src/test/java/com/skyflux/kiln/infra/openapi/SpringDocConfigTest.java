package com.skyflux.kiln.infra.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDocConfigTest {

    private final SpringDocConfig config = new SpringDocConfig();

    @Test
    void openApiInfoIsPopulated() {
        OpenAPI api = config.openAPI();

        assertThat(api).isNotNull();
        assertThat(api.getInfo()).isNotNull();
        assertThat(api.getInfo().getTitle()).isEqualTo(SpringDocConfig.API_TITLE);
        assertThat(api.getInfo().getVersion()).isEqualTo(SpringDocConfig.API_VERSION);
        assertThat(api.getInfo().getDescription()).isEqualTo(SpringDocConfig.API_DESCRIPTION);
        assertThat(api.getInfo().getContact()).isNotNull();
        assertThat(api.getInfo().getContact().getName()).isEqualTo(SpringDocConfig.CONTACT_NAME);
        assertThat(api.getInfo().getContact().getEmail()).isEqualTo(SpringDocConfig.CONTACT_EMAIL);
        assertThat(api.getInfo().getLicense()).isNotNull();
        assertThat(api.getInfo().getLicense().getName()).isEqualTo(SpringDocConfig.LICENSE_NAME);
    }

    @Test
    void bearerAuthSecuritySchemeRegistered() {
        OpenAPI api = config.openAPI();

        assertThat(api.getComponents()).isNotNull();
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }
}
