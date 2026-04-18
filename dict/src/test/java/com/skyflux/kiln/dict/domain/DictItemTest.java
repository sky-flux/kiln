package com.skyflux.kiln.dict.domain;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class DictItemTest {
    @Test void shouldExposeFields() {
        var id = Ids.next(); var typeId = Ids.next();
        DictItem item = new DictItem(id, typeId, "MALE", "男", 1, true, null, Instant.now());
        assertThat(item.code()).isEqualTo("MALE");
        assertThat(item.label()).isEqualTo("男");
        assertThat(item.sortOrder()).isEqualTo(1);
        assertThat(item.isActive()).isTrue();
        assertThat(item.tenantId()).isNull();
    }
}
