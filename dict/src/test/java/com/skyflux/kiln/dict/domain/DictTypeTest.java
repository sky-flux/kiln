package com.skyflux.kiln.dict.domain;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class DictTypeTest {
    @Test void shouldCreateSystemType() {
        DictType t = new DictType(Ids.next(), "GENDER", "性别", true, null, Instant.now());
        assertThat(t.isSystem()).isTrue();
        assertThat(t.tenantId()).isNull();
    }
    @Test void shouldCreateTenantType() {
        var tenantId = Ids.next();
        DictType t = new DictType(Ids.next(), "MY_CATEGORY", "自定义", false, tenantId, Instant.now());
        assertThat(t.isSystem()).isFalse();
        assertThat(t.tenantId()).isEqualTo(tenantId);
    }
}
