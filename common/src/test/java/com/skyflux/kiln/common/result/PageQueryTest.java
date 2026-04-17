package com.skyflux.kiln.common.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    @ParameterizedTest(name = "({0},{1}) -> page={2} size={3}")
    @CsvSource({
            // input page, input size, expected page, expected size
            "2, 50, 2, 50",     // valid values pass through
            "0, 20, 1, 20",     // page < 1 clamped to 1
            "-5, 20, 1, 20",    // negative page clamped to 1
            "1, 0, 1, 20",      // size < 1 defaults to 20
            "1, 500, 1, 200",   // size > 200 clamped to 200
            "1, 200, 1, 200",   // size exactly 200 retained
            "1, 1, 1, 1"        // size exactly 1 retained
    })
    void compactConstructorEnforcesBoundaries(int inPage, int inSize, int expPage, int expSize) {
        PageQuery q = new PageQuery(inPage, inSize, null);

        assertThat(q.page()).isEqualTo(expPage);
        assertThat(q.size()).isEqualTo(expSize);
    }

    @Test
    void sortFieldIsPreserved() {
        PageQuery q = new PageQuery(2, 50, "name asc");

        assertThat(q.sort()).isEqualTo("name asc");
    }

    @Test
    void offsetComputesFromPageAndSize() {
        assertThat(new PageQuery(3, 20, null).offset()).isEqualTo(40);
    }

    @Test
    void offsetIsZeroOnFirstPage() {
        assertThat(new PageQuery(1, 20, null).offset()).isZero();
    }

    @Test
    void offsetUsesClampedValues() {
        // page 0 -> 1, size 500 -> 200, offset = (1-1)*200 = 0
        assertThat(new PageQuery(0, 500, null).offset()).isZero();
    }
}
