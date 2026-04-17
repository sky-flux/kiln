package com.skyflux.kiln.infra.openapi;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs the OpenAPI / Swagger UI URLs once the web server is ready.
 *
 * <p>Pulls the {@code springdoc.*.enabled} / {@code *.path} properties (falling
 * back to springdoc's own defaults) and the live Tomcat port, so the banner
 * reflects reality — dev profile prints three clickable URLs, prod profile
 * (where {@code application-prod.yml} disables springdoc) prints nothing.
 */
@Component
public class OpenApiStartupBanner {

    private static final Logger log = LoggerFactory.getLogger(OpenApiStartupBanner.class);

    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;
    private final String apiDocsPath;
    private final String swaggerUiPath;
    private final String contextPath;

    public OpenApiStartupBanner(
            @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled,
            @Value("${springdoc.api-docs.path:/v3/api-docs}") String apiDocsPath,
            @Value("${springdoc.swagger-ui.path:/swagger-ui.html}") String swaggerUiPath,
            @Value("${server.servlet.context-path:}") String contextPath) {
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerUiEnabled = swaggerUiEnabled;
        this.apiDocsPath = apiDocsPath;
        this.swaggerUiPath = swaggerUiPath;
        this.contextPath = contextPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext wsCtx)) {
            return;
        }
        int port = wsCtx.getWebServer().getPort();
        for (String line : buildBannerLines(port, contextPath,
                apiDocsEnabled, swaggerUiEnabled,
                apiDocsPath, swaggerUiPath)) {
            log.info(line);
        }
    }

    static List<String> buildBannerLines(
            int port, String contextPath,
            boolean apiDocsEnabled, boolean swaggerUiEnabled,
            String apiDocsPath, String swaggerUiPath) {
        if (!apiDocsEnabled && !swaggerUiEnabled) {
            return List.of();
        }
        String base = "http://localhost:" + port + contextPath;
        List<String> lines = new ArrayList<>();
        lines.add("OpenAPI endpoints exposed");
        if (swaggerUiEnabled) {
            lines.add("  Swagger UI  : " + base + swaggerUiPath);
        }
        if (apiDocsEnabled) {
            lines.add("  OpenAPI JSON: " + base + apiDocsPath);
            lines.add("  OpenAPI YAML: " + base + yamlPathFor(apiDocsPath));
        }
        return lines;
    }

    /**
     * Derives the user-facing YAML URL path from the configured JSON path.
     *
     * <p>If the JSON path carries a {@code .json} suffix (e.g. {@code /docs.json}),
     * swap it for {@code .yaml} ({@code /docs.yaml}) — the short path the user
     * actually hits. Otherwise append {@code .yaml} in the springdoc-native way
     * ({@code /v3/api-docs} → {@code /v3/api-docs.yaml}).
     */
    static String yamlPathFor(String apiDocsPath) {
        if (apiDocsPath.endsWith(".json")) {
            return apiDocsPath.substring(0, apiDocsPath.length() - ".json".length()) + ".yaml";
        }
        return apiDocsPath + ".yaml";
    }
}
