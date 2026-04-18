package com.skyflux.kiln.common.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void ofCopiesItemsTotalAndPageMetadataFromQuery() {
        PageQuery query = new PageQuery(2, 50, "id desc");
        List<String> items = List.of("a", "b", "c");

        PageResult<String> result = PageResult.of(items, 123L, query);

        assertThat(result.items()).isEqualTo(items);
        assertThat(result.total()).isEqualTo(123L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(50);
    }

    @Test
    void emptyReturnsEmptyListZeroTotalAndQueryPageMetadata() {
        PageQuery query = new PageQuery(4, 25, null);

        PageResult<String> result = PageResult.empty(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.page()).isEqualTo(4);
        assertThat(result.size()).isEqualTo(25);
    }

    @Test
    void recordEqualityIsByValue() {
        PageResult<String> a = new PageResult<>(List.of("x"), 1L, 1, 20);
        PageResult<String> b = new PageResult<>(List.of("x"), 1L, 1, 20);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void mapTransformsItemsPreservingPaginationMetadata() {
        PageQuery query = new PageQuery(2, 10, null);
        PageResult<String> source = PageResult.of(List.of("a", "bb"), 2L, query);

        PageResult<Integer> mapped = source.map(String::length);

        assertThat(mapped.items()).containsExactly(1, 2);
        assertThat(mapped.total()).isEqualTo(2L);
        assertThat(mapped.page()).isEqualTo(2);
        assertThat(mapped.size()).isEqualTo(10);
    }
}
