package com.skyflux.kiln.infra.jackson;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Project-wide Jackson 3 customizations.
 *
 * <ul>
 *   <li>Do not fail on unknown JSON properties during deserialization — keep the
 *       API forward-compatible when new fields are added by clients.</li>
 *   <li>Do not fail on empty beans during serialization — permit result
 *       envelopes / commands with zero exposed getters.</li>
 *   <li>Emit {@code java.time} values as ISO strings that preserve the zone
 *       offset rather than numeric timestamps.</li>
 * </ul>
 *
 * <p>{@code JavaTimeModule} is auto-registered by Spring Boot 4 and therefore
 * not added explicitly here.
 */
@Component
public class KilnJackson3Customizer implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID, true);
    }
}
