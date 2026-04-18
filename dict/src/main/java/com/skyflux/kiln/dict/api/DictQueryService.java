package com.skyflux.kiln.dict.api;

import com.skyflux.kiln.dict.domain.DictItem;
import java.util.List;

/**
 * Public API for dictionary item lookup.
 * Results are Redis-cached per tenant+typeCode for 1 hour.
 * Other modules may depend on this interface.
 */
public interface DictQueryService {
    /** Returns active items for the given type code, ordered by sort_order. */
    List<DictItem> getItems(String typeCode);
}
