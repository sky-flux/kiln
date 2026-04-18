package com.skyflux.kiln.tenant.config;

import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TenantModuleConfig {

    @Bean
    TenantFilter tenantFilter(TenantJooqRepository repo) {
        return new TenantFilter(repo);
    }

    @Bean
    ExecuteListenerProvider tenantRlsListenerProvider() {
        return new DefaultExecuteListenerProvider(new TenantRlsListener());
    }
}
