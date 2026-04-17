package com.skyflux.kiln.infra.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI / Swagger UI metadata.
 *
 * <p>Configures the top-level {@link OpenAPI} bean picked up by
 * {@code springdoc-openapi-starter-webmvc-ui}. See design doc
 * {@code Ch.15.1 - 15.2}.
 *
 * <p>A {@code bearerAuth} security scheme placeholder is registered for
 * future JWT / Sa-Token integration (Phase 4). It is declared on the
 * components list only — individual operations do not yet require it, so
 * existing Phase 2 endpoints remain callable in Swagger UI without a token.
 */
@Configuration
public class SpringDocConfig {

    /** Title surfaced by Swagger UI. */
    public static final String API_TITLE = "Kiln API";

    /** Published version string — keep aligned with {@code build.gradle}. */
    public static final String API_VERSION = "0.0.1-SNAPSHOT";

    /** Contact e-mail. Placeholder until a real address is allocated. */
    public static final String CONTACT_EMAIL = "dev@skyflux.com";

    /** Contact name. */
    public static final String CONTACT_NAME = "Kiln Team";

    /** License identifier. */
    public static final String LICENSE_NAME = "Apache-2.0";

    /** License URL. */
    public static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";

    /** Human readable description (zh-CN, matches README). */
    public static final String API_DESCRIPTION =
            "基于 Spring Boot 4 + Spring Modulith 的企业级后端脚手架";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .version(API_VERSION)
                        .description(API_DESCRIPTION)
                        .contact(new Contact()
                                .name(CONTACT_NAME)
                                .email(CONTACT_EMAIL))
                        .license(new License()
                                .name(LICENSE_NAME)
                                .url(LICENSE_URL)))
                // TODO Phase 4: once Sa-Token / JWT is wired up, add a
                // SecurityRequirement globally so protected endpoints show a
                // lock icon in Swagger UI. For now we only declare the scheme.
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")));
    }
}
