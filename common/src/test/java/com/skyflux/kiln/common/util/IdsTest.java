package com.skyflux.kiln.common.util;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class IdsTest {
    @Test void shouldGenerateUuidV7() {
        assertThat(Ids.next().version()).isEqualTo(7);
    }
    @Test void shouldGenerateUniqueIds() {
        assertThat(Ids.next()).isNotEqualTo(Ids.next());
    }
    @Test void shouldBeTimeOrdered() {
        UUID a = Ids.next(); UUID b = Ids.next();
        assertThat(a.compareTo(b)).isLessThan(0);
    }
}
