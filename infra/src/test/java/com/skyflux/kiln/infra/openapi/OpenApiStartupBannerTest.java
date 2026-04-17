package com.skyflux.kiln.infra.openapi;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class OpenApiStartupBannerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(OpenApiStartupBanner.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void buildBannerLinesIncludesAllThreeUrlsWhenBothEnabled() {
        List<String> lines = OpenApiStartupBanner.buildBannerLines(
                8080, "",
                true, true,
                "/v3/api-docs", "/swagger-ui.html");

        assertThat(lines).anyMatch(l -> l.contains("http://localhost:8080/swagger-ui.html"));
        assertThat(lines).anyMatch(l -> l.contains("http://localhost:8080/v3/api-docs"));
        assertThat(lines).anyMatch(l -> l.contains("http://localhost:8080/v3/api-docs.yaml"));
    }

    @Test
    void buildBannerLinesReturnsEmptyWhenBothDisabled() {
        List<String> lines = OpenApiStartupBanner.buildBannerLines(
                8080, "",
                false, false,
                "/v3/api-docs", "/swagger-ui.html");

        assertThat(lines).isEmpty();
    }

    @Test
    void buildBannerLinesOmitsSwaggerUiWhenDisabled() {
        List<String> lines = OpenApiStartupBanner.buildBannerLines(
                8080, "",
                true, false,
                "/v3/api-docs", "/swagger-ui.html");

        assertThat(lines).noneMatch(l -> l.contains("swagger-ui.html"));
        assertThat(lines).anyMatch(l -> l.contains("/v3/api-docs"));
    }

    @Test
    void buildBannerLinesOmitsApiDocsWhenDisabled() {
        List<String> lines = OpenApiStartupBanner.buildBannerLines(
                8080, "",
                false, true,
                "/v3/api-docs", "/swagger-ui.html");

        assertThat(lines).anyMatch(l -> l.contains("swagger-ui.html"));
        assertThat(lines).noneMatch(l -> l.contains("/v3/api-docs"));
    }

    @Test
    void buildBannerLinesPrependsContextPath() {
        List<String> lines = OpenApiStartupBanner.buildBannerLines(
                9090, "/kiln",
                true, true,
                "/v3/api-docs", "/swagger-ui.html");

        assertThat(lines).anyMatch(l -> l.contains("http://localhost:9090/kiln/swagger-ui.html"));
        assertThat(lines).anyMatch(l -> l.contains("http://localhost:9090/kiln/v3/api-docs"));
        assertThat(lines).anyMatch(l -> l.contains("http://localhost:9090/kiln/v3/api-docs.yaml"));
    }

    @Test
    void yamlPathForStripsJsonSuffixWhenPresent() {
        assertThat(OpenApiStartupBanner.yamlPathFor("/docs.json")).isEqualTo("/docs.yaml");
    }

    @Test
    void yamlPathForAppendsYamlWhenNoJsonSuffix() {
        assertThat(OpenApiStartupBanner.yamlPathFor("/v3/api-docs")).isEqualTo("/v3/api-docs.yaml");
        assertThat(OpenApiStartupBanner.yamlPathFor("/docs")).isEqualTo("/docs.yaml");
    }

    @Test
    void buildBannerLinesStripsJsonSuffixWhenDerivingYamlUrl() {
        List<String> lines = OpenApiStartupBanner.buildBannerLines(
                8080, "",
                true, true,
                "/docs.json", "/docs");

        assertThat(lines).anyMatch(l -> l.contains("http://localhost:8080/docs.json"));
        assertThat(lines).anyMatch(l -> l.contains("http://localhost:8080/docs.yaml"));
        assertThat(lines).noneMatch(l -> l.contains("/docs.json.yaml"));
    }

    @Test
    void onReadyLogsAtInfoAndIncludesEveryEndpointUrlWhenWebContextAndBothEnabled() {
        OpenApiStartupBanner banner = new OpenApiStartupBanner(
                true, true,
                "/v3/api-docs", "/swagger-ui.html",
                "");

        banner.onReady(webReadyEvent(8080));

        assertThat(appender.list)
                .extracting(ILoggingEvent::getLevel)
                .containsOnly(Level.INFO);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("http://localhost:8080/swagger-ui.html"))
                .anyMatch(m -> m.contains("http://localhost:8080/v3/api-docs"))
                .anyMatch(m -> m.contains("http://localhost:8080/v3/api-docs.yaml"));
    }

    @Test
    void onReadyEmitsNothingWhenContextIsNotWebServer() {
        OpenApiStartupBanner banner = new OpenApiStartupBanner(
                true, true,
                "/v3/api-docs", "/swagger-ui.html",
                "");
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getApplicationContext()).thenReturn(mock(ConfigurableApplicationContext.class));

        banner.onReady(event);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void onReadyEmitsNothingWhenBothEndpointsDisabled() {
        OpenApiStartupBanner banner = new OpenApiStartupBanner(
                false, false,
                "/v3/api-docs", "/swagger-ui.html",
                "");

        banner.onReady(webReadyEvent(8080));

        assertThat(appender.list).isEmpty();
    }

    private ApplicationReadyEvent webReadyEvent(int port) {
        WebServer webServer = mock(WebServer.class);
        when(webServer.getPort()).thenReturn(port);
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class,
                withSettings().extraInterfaces(WebServerApplicationContext.class));
        when(((WebServerApplicationContext) ctx).getWebServer()).thenReturn(webServer);
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getApplicationContext()).thenReturn(ctx);
        return event;
    }
}
