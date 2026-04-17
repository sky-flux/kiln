package com.skyflux.kiln.infra.jackson;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class KilnJackson3CustomizerTest {

    public record SampleRecord(String name) { }

    public static class EmptyBean { }

    @Test
    void customizerDisablesFailOnUnknownProperties() {
        ObjectMapper mapper = build();

        SampleRecord result = mapper.readValue(
                "{\"name\":\"x\",\"unknown\":1}", SampleRecord.class);

        assertThat(result).isEqualTo(new SampleRecord("x"));
    }

    @Test
    void customizerDisablesFailOnEmptyBeans() {
        ObjectMapper mapper = build();

        String json = mapper.writeValueAsString(new EmptyBean());

        assertThat(json).isEqualTo("{}");
    }

    @Test
    void customizerSetsDateTimeFeatures() {
        KilnJackson3Customizer customizer = new KilnJackson3Customizer();
        JsonMapper.Builder builder = JsonMapper.builder();
        customizer.customize(builder);

        assertThat(builder.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(builder.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isFalse();
        assertThat(builder.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
        assertThat(builder.isEnabled(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)).isFalse();
        assertThat(builder.isEnabled(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)).isTrue();
    }

    private static ObjectMapper build() {
        KilnJackson3Customizer customizer = new KilnJackson3Customizer();
        JsonMapper.Builder builder = JsonMapper.builder();
        customizer.customize(builder);
        return builder.build();
    }
}
