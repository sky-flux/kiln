package com.skyflux.kiln.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Wires a JSON-serializing {@link RedisTemplate} alongside Spring Boot's
 * default {@code StringRedisTemplate}.
 *
 * <p>Keys use {@link StringRedisSerializer} so they stay inspectable with
 * {@code redis-cli}; values use {@link GenericJacksonJsonRedisSerializer}
 * (Spring Data Redis 4 / Jackson 3 replacement for the deprecated
 * {@code GenericJackson2JsonRedisSerializer}).
 */
@Configuration
class RedisConfig {

    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        RedisSerializer<Object> valueSerializer = GenericJacksonJsonRedisSerializer.builder().build();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
