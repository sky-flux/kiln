package com.skyflux.kiln.infra.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link RedisConfig}. Deliberately does NOT spin up Redis —
 * the goal is to assert the bean wires key/value serializers correctly.
 * Integration smoke against a live Redis runs via the shared Testcontainers
 * harness in the app module.
 */
class RedisConfigTest {

    private final RedisConfig config = new RedisConfig();

    @Test
    void redisTemplateUsesStringKeyAndJsonValueSerializers() {
        RedisConnectionFactory cf = mock(RedisConnectionFactory.class);
        RedisTemplate<String, Object> template = config.redisTemplate(cf);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isSameAs(cf);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
    }
}
