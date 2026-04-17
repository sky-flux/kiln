package com.skyflux.kiln.infra.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocsPathAliasConfigTest {

    @Test
    void registersShortYamlAliasForwardingToSpringdocYamlPathWhenJsonSuffixed() {
        DocsPathAliasConfig config = new DocsPathAliasConfig("/docs.json");
        ViewControllerRegistry registry = mock(ViewControllerRegistry.class);
        ViewControllerRegistration registration = mock(ViewControllerRegistration.class);
        when(registry.addViewController("/docs.yaml")).thenReturn(registration);

        config.addViewControllers(registry);

        verify(registry).addViewController("/docs.yaml");
        verify(registration).setViewName("forward:/docs.json.yaml");
    }

    @Test
    void skipsAliasWhenApiDocsPathHasNoJsonSuffix() {
        DocsPathAliasConfig config = new DocsPathAliasConfig("/v3/api-docs");
        ViewControllerRegistry registry = mock(ViewControllerRegistry.class);

        config.addViewControllers(registry);

        verify(registry, never()).addViewController(anyString());
    }
}
