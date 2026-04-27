package com.mockwise.ttsstt.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.ttsstt.tts.service.TtsCacheEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Class-bound serializer for the TTS cache. Earlier this used
     * {@code GenericJackson2JsonRedisSerializer} with the default ObjectMapper, but
     * that serializer relies on a {@code @class} hint inside the JSON to pick the
     * deserialization target — and our ObjectMapper has no default typing, so writes
     * went out as plain JSON without the hint and reads always came back as
     * {@code LinkedHashMap}, missing the {@code instanceof TtsCacheEntry} check in
     * {@link com.mockwise.ttsstt.tts.service.TtsCacheService#get(String)}. Result:
     * cache write succeeded, cache read always returned null, and ElevenLabs got
     * billed for every duplicate request.
     *
     * Pinning the serializer to {@code TtsCacheEntry.class} makes it parse the same
     * existing JSON correctly (no need to flush keys) and removes the dependency on
     * default typing, which carries Jackson polymorphic-deserialization risk anyway.
     */
    @Bean
    public RedisTemplate<String, TtsCacheEntry> ttsCacheRedisTemplate(
            RedisConnectionFactory cf, ObjectMapper objectMapper) {
        RedisTemplate<String, TtsCacheEntry> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        Jackson2JsonRedisSerializer<TtsCacheEntry> json =
                new Jackson2JsonRedisSerializer<>(objectMapper, TtsCacheEntry.class);
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
        return template;
    }
}
