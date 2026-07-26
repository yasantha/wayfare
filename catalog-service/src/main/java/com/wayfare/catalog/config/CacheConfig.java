package com.wayfare.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis-backed caching for near-static catalog reads (design §4.1, §9.1).
 * Destinations and activities are cached separately with distinct TTLs; a
 * catalog edit is rare, so a short TTL bounds staleness without needing
 * explicit invalidation wiring in the MVP.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer catalogCacheCustomizer() {
        ObjectMapper mapper = new ObjectMapper()
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL);
        var serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        return builder -> builder
                .withCacheConfiguration("destination", base.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration("activity", base.entryTtl(Duration.ofHours(6)));
    }
}
