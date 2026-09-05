package com.ipuuuuu.agentops.tools;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Enables durable tool idempotency when AGENTOPS_REDIS_URL is configured. */
@Configuration
@ConditionalOnProperty(name = "agentops.redis.url")
final class RedisConfiguration {
    @Bean
    RedisConnectionFactory redisConnectionFactory() {
        java.net.URI uri = java.net.URI.create(System.getenv().get("AGENTOPS_REDIS_URL"));
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 6379);
        if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
            String[] credentials = uri.getUserInfo().split(":", 2);
            configuration.setUsername(credentials[0]);
            configuration.setPassword(credentials[1]);
        }
        if (uri.getPath() != null && uri.getPath().length() > 1) {
            configuration.setDatabase(Integer.parseInt(uri.getPath().substring(1)));
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    IdempotencyStore redisIdempotencyStore(StringRedisTemplate redis, AgentOpsRedisProperties properties) {
        return new RedisIdempotencyStore(new RedisIdempotencyStore.Commands() {
            @Override public String get(String key) { return redis.opsForValue().get(key); }
            @Override public boolean setIfAbsent(String key, String value) {
                return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value));
            }
            @Override public boolean setIfAbsent(String key, String value, Duration ttl) {
                return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
            }
        }, properties.ttl());
    }

    @Bean
    AgentOpsRedisProperties agentOpsRedisProperties() {
        return AgentOpsRedisProperties.fromEnvironment();
    }
}
