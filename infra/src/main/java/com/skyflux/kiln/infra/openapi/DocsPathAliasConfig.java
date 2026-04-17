package com.skyflux.kiln.infra.openapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exposes a short {@code .yaml} alias for the OpenAPI spec when the configured
 * {@code springdoc.api-docs.path} carries a {@code .json} suffix.
 *
 * <p>Example: with {@code springdoc.api-docs.path=/docs.json}, springdoc serves
 * the YAML natively at {@code /docs.json.yaml}. This config registers
 * {@code /docs.yaml} as an internal-forward alias so the path triplet the user
 * sees is {@code /docs} / {@code /docs.json} / {@code /docs.yaml} — no
 * {@code .json.yaml} tail to explain.
 *
 * <p>If the configured path has no {@code .json} suffix (the springdoc default
 * {@code /v3/api-docs}, for instance), no alias is registered — the native
 * {@code /v3/api-docs.yaml} is already the "clean" path.
 */
@Configuration
public class DocsPathAliasConfig implements WebMvcConfigurer {

    private final String apiDocsPath;

    public DocsPathAliasConfig(
            @Value("${springdoc.api-docs.path:/v3/api-docs}") String apiDocsPath) {
        this.apiDocsPath = apiDocsPath;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (!apiDocsPath.endsWith(".json")) {
            return;
        }
        String shortYamlPath = OpenApiStartupBanner.yamlPathFor(apiDocsPath);
        registry.addViewController(shortYamlPath).setViewName("forward:" + apiDocsPath + ".yaml");
    }
}
