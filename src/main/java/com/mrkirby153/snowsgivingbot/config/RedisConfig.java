package com.mrkirby153.snowsgivingbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    private final String host;
    private final int port;
    private final int db;

    public RedisConfig(@Value("${redis.host}") String host, @Value("${redis.port}") int port,
        @Value("${redis.db:0}") int db) {
        this.host = host;
        this.port = port;
        this.db = db;
    }

    @Bean
    public JedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(db);
        return new JedisConnectionFactory(config);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.builder(factory).cacheDefaults(redisCacheConfiguration())
            .transactionAware()
            .build();
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(120))
            .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()))
            .computePrefixWith(cacheName -> "giveaways:" + cacheName + ":");
    }
}
