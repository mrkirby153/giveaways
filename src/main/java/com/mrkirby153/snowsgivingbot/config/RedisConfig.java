package com.mrkirby153.snowsgivingbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

@Configuration
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
}
