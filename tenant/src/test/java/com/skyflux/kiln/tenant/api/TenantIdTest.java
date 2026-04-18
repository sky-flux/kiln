package com.skyflux.kiln.tenant.api;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TenantIdTest {
    @Test void shouldRejectNull() {
        assertThatNullPointerException().isThrownBy(() -> new TenantId(null));
    }
    @Test void shouldExposeValue() {
        UUID uuid = Ids.next();
        assertThat(new TenantId(uuid).value()).isEqualTo(uuid);
    }
    @Test void newIdShouldGenerateV7() {
        assertThat(TenantId.newId().value().version()).isEqualTo(7);
    }
    @Test void ofStringShouldParse() {
        UUID uuid = Ids.next();
        assertThat(TenantId.of(uuid.toString()).value()).isEqualTo(uuid);
    }
}
