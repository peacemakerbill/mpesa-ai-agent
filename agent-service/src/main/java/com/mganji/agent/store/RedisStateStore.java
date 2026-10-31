package com.mganji.agent.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "agent.store", havingValue = "redis", matchIfMissing = true)
public class RedisStateStore implements StateStore {

    private static final DefaultRedisScript<Long> DELETE_IF_EQUALS = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public Optional<String> getAndDelete(String key) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key));
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public boolean deleteIfEquals(String key, String expected) {
        Long removed = redis.execute(DELETE_IF_EQUALS, List.of(key), expected);
        return removed != null && removed > 0;
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, ttlOnCreate);
        }
        return value == null ? 0L : value;
    }

    @Override
    public void appendToList(String key, String value, int maxLength, Duration ttl) {
        redis.opsForList().rightPush(key, value);
        redis.opsForList().trim(key, -maxLength, -1);
        redis.expire(key, ttl);
    }

    @Override
    public List<String> readList(String key) {
        List<String> values = redis.opsForList().range(key, 0, -1);
        return values == null ? List.of() : values;
    }
}
